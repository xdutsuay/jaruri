package com.example.moneymanager.tunnel

import com.example.moneymanager.tunnel.dns.BlockMode
import com.example.moneymanager.tunnel.dns.DnsMessage
import com.example.moneymanager.tunnel.dns.IpPacket
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * End-to-end on the JVM: a fake tun (queues) → engine → real UDP to a local
 * stub DNS server on 127.0.0.1 → reply packet back into the tun.
 */
class DnsTunnelEngineTest {
    private val toTun = LinkedBlockingQueue<ByteArray>()   // what "apps" send
    private val fromTun = LinkedBlockingQueue<ByteArray>() // what the engine writes back
    private val events = CopyOnWriteArrayList<DnsEvent>()
    private lateinit var stub: DatagramSocket
    private lateinit var stubThread: Thread
    @Volatile private var stubSilent = false
    private val pool = Executors.newFixedThreadPool(4)
    private var policy = TunnelPolicy()
    private var engineThread: Thread? = null
    private val CLOSE = ByteArray(0)

    @Before
    fun setUp() {
        stub = DatagramSocket(0, InetAddress.getLoopbackAddress())
        stubThread = Thread {
            val buf = ByteArray(2048)
            while (!stub.isClosed) {
                val p = DatagramPacket(buf, buf.size)
                try { stub.receive(p) } catch (_: Exception) { break }
                if (stubSilent) continue
                val q = buf.copyOf(p.length)
                val parsed = DnsMessage.parseQuery(q) ?: continue
                // answer A 93.184.216.34 with TTL 300
                val resp = q.copyOf(parsed.questionEnd).also {
                    it[2] = 0x81.toByte(); it[3] = 0x80.toByte(); IpPacket.put16(it, 6, 1); IpPacket.put16(it, 10, 0)
                } + byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 1, 44, 0, 4, 93, 184.toByte(), 216.toByte(), 34)
                stub.send(DatagramPacket(resp, resp.size, p.socketAddress))
            }
        }.apply { isDaemon = true; start() }
    }

    @After
    fun tearDown() {
        toTun.put(CLOSE)
        engineThread?.join(2_000)
        stub.close()
        pool.shutdownNow()
    }

    private fun start(uid: Int = 10200, timeoutMs: Int = 2_500) {
        val engine = DnsTunnelEngine(
            policy = { policy },
            blockMode = { BlockMode.NXDOMAIN },
            upstream = UdpUpstream(
                servers = { listOf(InetAddress.getLoopbackAddress()) },
                socketFactory = { DatagramSocket() },
                port = stub.localPort,
                timeoutMs = timeoutMs
            ),
            uidOf = { uid },
            onEvent = { events += it },
            forwarders = pool
        )
        engineThread = Thread {
            engine.run(
                source = { buf ->
                    val p = toTun.take()
                    if (p === CLOSE) -1 else { System.arraycopy(p, 0, buf, 0, p.size); p.size }
                },
                sink = { fromTun.put(it) }
            )
        }.apply { isDaemon = true; start() }
    }

    private fun reply(): IpPacket {
        val r = fromTun.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("no reply from tunnel")
        return IpPacket.parse(r, r.size)!!
    }

    private fun waitEvents(n: Int) {
        val end = System.currentTimeMillis() + 5_000
        while (events.size < n && System.currentTimeMillis() < end) Thread.sleep(10)
    }

    @Test
    fun allowedQueryIsForwardedAndWrappedBack() {
        start()
        toTun.put(TestPackets.udp(false, 41000, 53, TestPackets.dnsQuery("example.com", id = 0x0A0B)))
        val r = reply()
        assertEquals(53, r.srcPort); assertEquals(41000, r.dstPort)
        assertArrayEquals(TestPackets.V4_DNS, r.src)
        assertTrue(TestPackets.transportChecksumOk(r))
        val m = r.payload()
        assertEquals(0x0A0B, IpPacket.u16(m, 0))
        assertEquals(1, IpPacket.u16(m, 6))
        assertArrayEquals(byteArrayOf(93, 184.toByte(), 216.toByte(), 34), m.copyOfRange(m.size - 4, m.size))
        waitEvents(1)
        assertEquals(DnsDecision.ALLOWED, events.single().decision)
        assertEquals(10200, events.single().uid)
        assertEquals("example.com", events.single().name)
    }

    @Test
    fun blockedDomainAnsweredLocallyOverIpv6() {
        policy = TunnelPolicy(lists = listOf(NamedList("SB", DomainSet(listOf("ads.example.com")))))
        start()
        toTun.put(TestPackets.udp(true, 41001, 53, TestPackets.dnsQuery("ads.example.com", qtype = 28)))
        val r = reply()
        assertEquals(6, r.version)
        assertTrue(TestPackets.transportChecksumOk(r))
        assertEquals(DnsMessage.RCODE_NXDOMAIN, DnsMessage.rcode(r.payload()))
        waitEvents(1)
        assertEquals(DnsDecision.BLOCKED_DOMAIN, events[0].decision)
        assertEquals("list:SB", events[0].ref)
    }

    @Test
    fun blockedAppGetsNxdomainButUnknownAppIsForwarded() {
        policy = TunnelPolicy(blockedUids = setOf(10200))
        start(uid = 10200)
        toTun.put(TestPackets.udp(false, 41002, 53, TestPackets.dnsQuery("social.example")))
        assertEquals(DnsMessage.RCODE_NXDOMAIN, DnsMessage.rcode(reply().payload()))
        waitEvents(1)
        assertEquals(DnsDecision.BLOCKED_APP, events[0].decision)
    }

    @Test
    fun silentUpstreamBecomesServfailWithinTimeout() {
        stubSilent = true
        start(timeoutMs = 600)
        val t0 = System.currentTimeMillis()
        toTun.put(TestPackets.udp(false, 41003, 53, TestPackets.dnsQuery("slow.example")))
        val r = reply()
        assertTrue(System.currentTimeMillis() - t0 < 3_000)
        assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(r.payload()))
        waitEvents(1)
        assertEquals(DnsDecision.SERVFAIL, events[0].decision)
    }

    @Test
    fun tcpToDnsAddressIsReset_otherTrafficDropped_garbageSurvived() {
        start()
        toTun.put(byteArrayOf(1, 2, 3))
        toTun.put(ByteArray(60) { 0x45 })
        toTun.put(TestPackets.udp(false, 41004, 123, byteArrayOf(1, 2, 3, 4))) // NTP to our address: dropped
        toTun.put(TestPackets.tcp(false, 41005, 853, seq = 77, ack = 0, flags = IpPacket.TCP_SYN))
        val rst = reply()
        assertEquals(IpPacket.PROTO_TCP, rst.protocol)
        assertEquals(78L, rst.tcpAck)
        // still alive afterwards
        toTun.put(TestPackets.udp(false, 41006, 53, TestPackets.dnsQuery("after.example")))
        assertEquals(DnsMessage.RCODE_NOERROR, DnsMessage.rcode(reply().payload()))
        assertNull(fromTun.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun pausedForwardsEverything() {
        policy = TunnelPolicy(paused = true, lists = listOf(NamedList("SB", DomainSet(listOf("ads.example.com")))))
        start()
        toTun.put(TestPackets.udp(false, 41007, 53, TestPackets.dnsQuery("ads.example.com")))
        assertEquals(DnsMessage.RCODE_NOERROR, DnsMessage.rcode(reply().payload()))
        waitEvents(1)
        assertEquals(DnsDecision.PAUSED, events[0].decision)
    }
}
