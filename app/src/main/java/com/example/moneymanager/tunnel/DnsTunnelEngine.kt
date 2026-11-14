package com.example.moneymanager.tunnel

import com.example.moneymanager.tunnel.dns.BlockMode
import com.example.moneymanager.tunnel.dns.DnsMessage
import com.example.moneymanager.tunnel.dns.IpPacket
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException

/** One tun read: returns the packet length, or -1 when the tunnel is closed. */
fun interface PacketSource { @Throws(IOException::class) fun read(buf: ByteArray): Int }
fun interface PacketSink { @Throws(IOException::class) fun write(packet: ByteArray) }

/** Resolves one raw DNS message upstream; null on timeout/failure. */
fun interface Upstream { fun resolve(query: ByteArray): ByteArray? }

data class DnsEvent(
    val timeMs: Long,
    val uid: Int,
    val name: String,
    val qtype: Int,
    val decision: DnsDecision,
    val ref: String?,
    val rcode: Int,
    val latencyMs: Long
)

/**
 * The tun only carries packets for our virtual DNS addresses. UDP/53 is
 * filtered (answered locally or forwarded); TCP to the DNS address gets a RST
 * so resolvers fall back to UDP quickly (this also covers DoT/853 probes);
 * everything else is dropped. Platform-free so it runs in JVM tests.
 */
class DnsTunnelEngine(
    private val policy: () -> TunnelPolicy,
    private val blockMode: () -> BlockMode,
    private val upstream: Upstream,
    private val uidOf: (IpPacket) -> Int,
    private val onEvent: (DnsEvent) -> Unit,
    private val forwarders: ExecutorService,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Blocks until [source] reports the tunnel closed or throws. */
    fun run(source: PacketSource, sink: PacketSink) {
        val buf = ByteArray(MAX_PACKET)
        while (true) {
            val n = try { source.read(buf) } catch (_: IOException) { return }
            if (n < 0) return
            if (n == 0) continue
            try {
                handle(buf.copyOf(n), sink)
            } catch (_: RuntimeException) {
                // One bad packet must never take the tunnel down.
            }
        }
    }

    fun handle(packet: ByteArray, sink: PacketSink) {
        val ip = IpPacket.parse(packet, packet.size) ?: return
        if (ip.protocol == IpPacket.PROTO_TCP) {
            IpPacket.tcpReset(ip)?.let { safeWrite(sink, it) }
            return
        }
        if (ip.protocol != IpPacket.PROTO_UDP || ip.dstPort != 53) return
        val message = ip.payload()
        val q = DnsMessage.parseQuery(message) ?: return
        val started = clock()
        val uid = try { uidOf(ip) } catch (_: Exception) { TunnelPolicy.UNKNOWN_UID }
        val verdict = policy().decide(q.name, uid)
        if (verdict.blocks) {
            val reply = DnsMessage.blocked(message, q, blockMode())
            safeWrite(sink, IpPacket.udpReply(ip, reply))
            onEvent(DnsEvent(started, uid, q.name, q.qtype, verdict.decision, verdict.ref, DnsMessage.rcode(reply), 0))
            return
        }
        val task = Runnable {
            val answer = try { upstream.resolve(message) } catch (_: Exception) { null }
            val reply = answer ?: DnsMessage.servfail(message, q)
            safeWrite(sink, IpPacket.udpReply(ip, reply))
            val decision = if (answer == null) DnsDecision.SERVFAIL else verdict.decision
            onEvent(DnsEvent(started, uid, q.name, q.qtype, decision, verdict.ref, DnsMessage.rcode(reply), clock() - started))
        }
        try {
            forwarders.execute(task)
        } catch (_: RejectedExecutionException) {
            // Shutting down or overloaded: answer SERVFAIL rather than leave the app hanging.
            val reply = DnsMessage.servfail(message, q)
            safeWrite(sink, IpPacket.udpReply(ip, reply))
        }
    }

    private fun safeWrite(sink: PacketSink, p: ByteArray) {
        try {
            synchronized(sink) { sink.write(p) }
        } catch (_: IOException) {
        }
    }

    companion object {
        const val MAX_PACKET = 32767
    }
}
