package com.example.moneymanager.tunnel.dns

/**
 * Minimal IPv4/IPv6 + UDP/TCP parsing and building for the DNS-only tunnel.
 * Only what the tunnel needs: no fragments, no IPv6 extension headers (such
 * packets are ignored, never mis-parsed).
 */
data class IpPacket(
    val version: Int,
    val protocol: Int,
    val src: ByteArray,
    val dst: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    /** Offset/length of the transport payload (DNS message for UDP) within [raw]. */
    val payloadOffset: Int,
    val payloadLength: Int,
    val tcpSeq: Long = 0,
    val tcpAck: Long = 0,
    val tcpFlags: Int = 0,
    val raw: ByteArray
) {
    fun payload(): ByteArray = raw.copyOfRange(payloadOffset, payloadOffset + payloadLength)

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)

    companion object {
        const val PROTO_TCP = 6
        const val PROTO_UDP = 17
        const val TCP_FIN = 0x01
        const val TCP_SYN = 0x02
        const val TCP_RST = 0x04
        const val TCP_ACK = 0x10

        /** Returns null for anything we don't handle (bad lengths, fragments, other protocols). */
        fun parse(buf: ByteArray, len: Int): IpPacket? {
            if (len < 20) return null
            val raw = if (buf.size == len) buf else buf.copyOf(len)
            return when ((raw[0].toInt() ushr 4) and 0xF) {
                4 -> parseV4(raw)
                6 -> parseV6(raw)
                else -> null
            }
        }

        private fun parseV4(b: ByteArray): IpPacket? {
            val ihl = (b[0].toInt() and 0xF) * 4
            val total = u16(b, 2)
            if (ihl < 20 || total > b.size || total < ihl) return null
            val fragment = u16(b, 6) and 0x3FFF // MF flag or offset
            if (fragment != 0) return null
            val proto = b[9].toInt() and 0xFF
            return transport(4, proto, b.copyOfRange(12, 16), b.copyOfRange(16, 20), b, ihl, total)
        }

        private fun parseV6(b: ByteArray): IpPacket? {
            if (b.size < 40) return null
            val payloadLen = u16(b, 4)
            if (40 + payloadLen > b.size) return null
            val next = b[6].toInt() and 0xFF
            return transport(6, next, b.copyOfRange(8, 24), b.copyOfRange(24, 40), b, 40, 40 + payloadLen)
        }

        private fun transport(v: Int, proto: Int, src: ByteArray, dst: ByteArray, b: ByteArray, off: Int, end: Int): IpPacket? =
            when (proto) {
                PROTO_UDP -> {
                    if (end - off < 8) null else {
                        val udpLen = u16(b, off + 4)
                        if (udpLen < 8 || off + udpLen > end) null
                        else IpPacket(v, proto, src, dst, u16(b, off), u16(b, off + 2), off + 8, udpLen - 8, raw = b)
                    }
                }
                PROTO_TCP -> {
                    if (end - off < 20) null else {
                        val dataOff = ((b[off + 12].toInt() ushr 4) and 0xF) * 4
                        if (dataOff < 20 || off + dataOff > end) null
                        else IpPacket(
                            v, proto, src, dst, u16(b, off), u16(b, off + 2),
                            off + dataOff, end - off - dataOff,
                            tcpSeq = u32(b, off + 4), tcpAck = u32(b, off + 8),
                            tcpFlags = b[off + 13].toInt() and 0x3F, raw = b
                        )
                    }
                }
                else -> null
            }

        /** A UDP reply to [request] (addresses and ports swapped) carrying [payload]. */
        fun udpReply(request: IpPacket, payload: ByteArray): ByteArray {
            val udp = ByteArray(8 + payload.size)
            put16(udp, 0, request.dstPort)
            put16(udp, 2, request.srcPort)
            put16(udp, 4, udp.size)
            System.arraycopy(payload, 0, udp, 8, payload.size)
            val sum = transportChecksum(request.dst, request.src, PROTO_UDP, udp)
            put16(udp, 6, if (sum == 0) 0xFFFF else sum)
            return wrap(request, udp, PROTO_UDP)
        }

        /** A TCP RST answering [request] (RFC 793 reset generation). */
        fun tcpReset(request: IpPacket): ByteArray? {
            if (request.protocol != PROTO_TCP || request.tcpFlags and TCP_RST != 0) return null
            val tcp = ByteArray(20)
            put16(tcp, 0, request.dstPort)
            put16(tcp, 2, request.srcPort)
            if (request.tcpFlags and TCP_ACK != 0) {
                put32(tcp, 4, request.tcpAck)
                tcp[13] = TCP_RST.toByte()
            } else {
                var segLen = request.payloadLength.toLong()
                if (request.tcpFlags and TCP_SYN != 0) segLen++
                if (request.tcpFlags and TCP_FIN != 0) segLen++
                put32(tcp, 8, (request.tcpSeq + segLen) and 0xFFFFFFFFL)
                tcp[13] = (TCP_RST or TCP_ACK).toByte()
            }
            tcp[12] = (5 shl 4).toByte()
            put16(tcp, 16, transportChecksum(request.dst, request.src, PROTO_TCP, tcp))
            return wrap(request, tcp, PROTO_TCP)
        }

        private fun wrap(request: IpPacket, transport: ByteArray, proto: Int): ByteArray =
            if (request.version == 4) {
                val out = ByteArray(20 + transport.size)
                out[0] = 0x45
                put16(out, 2, out.size)
                out[8] = 64
                out[9] = proto.toByte()
                System.arraycopy(request.dst, 0, out, 12, 4)
                System.arraycopy(request.src, 0, out, 16, 4)
                put16(out, 10, checksum(out, 0, 20, 0))
                System.arraycopy(transport, 0, out, 20, transport.size)
                out
            } else {
                val out = ByteArray(40 + transport.size)
                out[0] = 0x60
                put16(out, 4, transport.size)
                out[6] = proto.toByte()
                out[7] = 64
                System.arraycopy(request.dst, 0, out, 8, 16)
                System.arraycopy(request.src, 0, out, 24, 16)
                System.arraycopy(transport, 0, out, 40, transport.size)
                out
            }

        fun transportChecksum(src: ByteArray, dst: ByteArray, proto: Int, segment: ByteArray): Int {
            var sum = 0L
            sum = add(sum, src, 0, src.size)
            sum = add(sum, dst, 0, dst.size)
            sum += proto
            sum += segment.size
            return checksum(segment, 0, segment.size, sum)
        }

        fun checksum(b: ByteArray, off: Int, len: Int, initial: Long): Int {
            var sum = add(initial, b, off, len)
            while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
            return (sum.inv() and 0xFFFF).toInt()
        }

        private fun add(initial: Long, b: ByteArray, off: Int, len: Int): Long {
            var sum = initial
            var i = off
            while (i + 1 < off + len) {
                sum += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
                i += 2
            }
            if (i < off + len) sum += (b[i].toInt() and 0xFF) shl 8
            return sum
        }

        fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun u32(b: ByteArray, i: Int): Long = (u16(b, i).toLong() shl 16) or u16(b, i + 2).toLong()
        fun put16(b: ByteArray, i: Int, v: Int) {
            b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte()
        }
        fun put32(b: ByteArray, i: Int, v: Long) {
            put16(b, i, (v ushr 16).toInt() and 0xFFFF); put16(b, i + 2, (v and 0xFFFF).toInt())
        }
    }
}
