package com.example.moneymanager.tunnel

import com.example.moneymanager.tunnel.dns.IpPacket
import java.io.ByteArrayOutputStream

/** Builds raw IP packets the way the kernel hands them to the tun. */
object TestPackets {
    val V4_CLIENT = byteArrayOf(10, 111, 222.toByte(), 2)
    val V4_DNS = byteArrayOf(10, 111, 222.toByte(), 1)
    val V6_CLIENT = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 2 }
    val V6_DNS = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 }

    fun dnsQuery(name: String, qtype: Int = 1, id: Int = 0x1234, edns: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v ushr 8); out.write(v and 0xFF) }
        u16(id); u16(0x0100); u16(1); u16(0); u16(0); u16(if (edns) 1 else 0)
        for (label in name.split('.')) { out.write(label.length); out.write(label.toByteArray()) }
        out.write(0); u16(qtype); u16(1)
        if (edns) { out.write(0); u16(41); u16(4096); out.write(byteArrayOf(0, 0, 0, 0)); u16(0) }
        return out.toByteArray()
    }

    fun udp(v6: Boolean, sport: Int, dport: Int, payload: ByteArray,
            src: ByteArray = if (v6) V6_CLIENT else V4_CLIENT, dst: ByteArray = if (v6) V6_DNS else V4_DNS): ByteArray {
        val udp = ByteArray(8 + payload.size)
        IpPacket.put16(udp, 0, sport); IpPacket.put16(udp, 2, dport); IpPacket.put16(udp, 4, udp.size)
        System.arraycopy(payload, 0, udp, 8, payload.size)
        IpPacket.put16(udp, 6, IpPacket.transportChecksum(src, dst, 17, udp))
        return ip(v6, 17, src, dst, udp)
    }

    fun tcp(v6: Boolean, sport: Int, dport: Int, seq: Long, ack: Long, flags: Int, payload: Int = 0): ByteArray {
        val src = if (v6) V6_CLIENT else V4_CLIENT
        val dst = if (v6) V6_DNS else V4_DNS
        val t = ByteArray(20 + payload)
        IpPacket.put16(t, 0, sport); IpPacket.put16(t, 2, dport)
        IpPacket.put32(t, 4, seq); IpPacket.put32(t, 8, ack)
        t[12] = (5 shl 4).toByte(); t[13] = flags.toByte()
        return ip(v6, 6, src, dst, t)
    }

    private fun ip(v6: Boolean, proto: Int, src: ByteArray, dst: ByteArray, transport: ByteArray): ByteArray =
        if (!v6) {
            val o = ByteArray(20 + transport.size)
            o[0] = 0x45; IpPacket.put16(o, 2, o.size); o[8] = 64; o[9] = proto.toByte()
            System.arraycopy(src, 0, o, 12, 4); System.arraycopy(dst, 0, o, 16, 4)
            IpPacket.put16(o, 10, IpPacket.checksum(o, 0, 20, 0))
            System.arraycopy(transport, 0, o, 20, transport.size); o
        } else {
            val o = ByteArray(40 + transport.size)
            o[0] = 0x60; IpPacket.put16(o, 4, transport.size); o[6] = proto.toByte(); o[7] = 64
            System.arraycopy(src, 0, o, 8, 16); System.arraycopy(dst, 0, o, 24, 16)
            System.arraycopy(transport, 0, o, 40, transport.size); o
        }

    /** A transport checksum over a reply verifies to 0 when it is correct. */
    fun transportChecksumOk(p: IpPacket): Boolean {
        val start = if (p.version == 4) 20 else 40
        val seg = p.raw.copyOfRange(start, p.raw.size)
        return IpPacket.transportChecksum(p.src, p.dst, p.protocol, seg) == 0
    }
}
