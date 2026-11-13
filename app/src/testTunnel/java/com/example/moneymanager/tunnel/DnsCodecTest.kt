package com.example.moneymanager.tunnel

import com.example.moneymanager.tunnel.dns.BlockMode
import com.example.moneymanager.tunnel.dns.DnsMessage
import com.example.moneymanager.tunnel.dns.IpPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DnsCodecTest {
    private fun blockedFor(name: String, qtype: Int, mode: BlockMode): ByteArray {
        val m = TestPackets.dnsQuery(name, qtype)
        return DnsMessage.blocked(m, DnsMessage.parseQuery(m)!!, mode)
    }

    @Test
    fun parsesIpv4AndIpv6DnsQueries() {
        for (v6 in listOf(false, true)) {
            val pkt = TestPackets.udp(v6, 40000, 53, TestPackets.dnsQuery("Ads.Example.COM", 28, id = 0xBEEF))
            val ip = IpPacket.parse(pkt, pkt.size)!!
            assertEquals(if (v6) 6 else 4, ip.version)
            assertEquals(17, ip.protocol)
            assertEquals(40000, ip.srcPort); assertEquals(53, ip.dstPort)
            val q = DnsMessage.parseQuery(ip.payload())!!
            assertEquals("ads.example.com", q.name)
            assertEquals(28, q.qtype); assertEquals(0xBEEF, q.id)
        }
    }

    @Test
    fun nxdomainReplySwapsAddressesAndHasValidChecksums() {
        for (v6 in listOf(false, true)) {
            val query = TestPackets.dnsQuery("bad.example", edns = true)
            val raw = TestPackets.udp(v6, 5555, 53, query)
            val req = IpPacket.parse(raw, raw.size)!!
            val q = DnsMessage.parseQuery(req.payload())!!
            val replyPkt = IpPacket.udpReply(req, DnsMessage.blocked(req.payload(), q, BlockMode.NXDOMAIN))
            val r = IpPacket.parse(replyPkt, replyPkt.size)!!
            assertArrayEquals(req.dst, r.src); assertArrayEquals(req.src, r.dst)
            assertEquals(53, r.srcPort); assertEquals(5555, r.dstPort)
            assertTrue(TestPackets.transportChecksumOk(r))
            if (!v6) assertEquals(0, IpPacket.checksum(replyPkt, 0, 20, 0))
            val m = r.payload()
            assertEquals(0x1234, IpPacket.u16(m, 0))
            assertEquals(DnsMessage.RCODE_NXDOMAIN, DnsMessage.rcode(m))
            assertTrue("QR set", m[2].toInt() and 0x80 != 0)
            assertEquals(1, IpPacket.u16(m, 4)); assertEquals(0, IpPacket.u16(m, 6))
            assertEquals("EDNS dropped", 0, IpPacket.u16(m, 10))
        }
    }

    @Test
    fun zeroIpAnswersAAndAaaa_andEmptyForOtherTypes() {
        val a = blockedFor("x.test", 1, BlockMode.ZERO_IP)
        assertEquals(1, IpPacket.u16(a, 6))
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), a.copyOfRange(a.size - 4, a.size))
        assertEquals(4, IpPacket.u16(a, a.size - 6))
        val aaaa = blockedFor("x.test", 28, BlockMode.ZERO_IP)
        assertEquals(16, IpPacket.u16(aaaa, aaaa.size - 18))
        val mx = blockedFor("x.test", 15, BlockMode.ZERO_IP)
        assertEquals(0, IpPacket.u16(mx, 6)); assertEquals(DnsMessage.RCODE_NOERROR, DnsMessage.rcode(mx))
    }

    @Test
    fun servfailKeepsIdAndQuestion() {
        val query = TestPackets.dnsQuery("slow.test", id = 7)
        val q = DnsMessage.parseQuery(query)!!
        val r = DnsMessage.servfail(query, q)
        assertEquals(7, IpPacket.u16(r, 0)); assertEquals(DnsMessage.RCODE_SERVFAIL, DnsMessage.rcode(r))
        assertEquals("slow.test", DnsMessage.parseQuery(r.copyOf().also { it[2] = 1 })!!.name)
    }

    @Test
    fun tcpResetFollowsRfc793() {
        val syn = IpPacket.parse(TestPackets.tcp(false, 4444, 853, seq = 1000, ack = 0, flags = IpPacket.TCP_SYN), 40)!!
        val rst = IpPacket.parse(IpPacket.tcpReset(syn)!!, 40)!!
        assertEquals(IpPacket.TCP_RST or IpPacket.TCP_ACK, rst.tcpFlags)
        assertEquals(1001L, rst.tcpAck)
        assertTrue(TestPackets.transportChecksumOk(rst))

        val ackPkt = TestPackets.tcp(true, 4444, 53, seq = 5, ack = 0xFFFFFFF0L, flags = IpPacket.TCP_ACK)
        val withAck = IpPacket.parse(ackPkt, ackPkt.size)!!
        val rst2 = IpPacket.parse(IpPacket.tcpReset(withAck)!!, 60)!!
        assertEquals(IpPacket.TCP_RST, rst2.tcpFlags); assertEquals(0xFFFFFFF0L, rst2.tcpSeq)
        assertTrue(TestPackets.transportChecksumOk(rst2))

        val rstIn = IpPacket.parse(TestPackets.tcp(false, 1, 53, 0, 0, IpPacket.TCP_RST), 40)!!
        assertNull("never answer a RST", IpPacket.tcpReset(rstIn))
    }

    @Test
    fun rejectsWhatItShouldNotGuessAt() {
        assertNull(IpPacket.parse(ByteArray(10), 10))
        // IPv4 fragment
        val frag = TestPackets.udp(false, 1, 53, TestPackets.dnsQuery("a.b")).also { it[6] = 0x20 }
        assertNull(IpPacket.parse(frag, frag.size))
        // compressed name in question, response bit, two questions
        val q = TestPackets.dnsQuery("a.b")
        assertNull(DnsMessage.parseQuery(q.copyOf().also { it[12] = 0xC0.toByte() }))
        assertNull(DnsMessage.parseQuery(q.copyOf().also { it[2] = 0x81.toByte() }))
        assertNull(DnsMessage.parseQuery(q.copyOf().also { it[5] = 2 }))
        assertNull(DnsMessage.parseQuery(q.copyOf(14)))
    }

    @Test
    fun randomGarbageNeverThrows() {
        val rnd = Random(42)
        repeat(20_000) {
            val len = rnd.nextInt(0, 120)
            val b = rnd.nextBytes(len)
            if (len > 0 && rnd.nextBoolean()) b[0] = (if (rnd.nextBoolean()) 0x45 else 0x60).toByte()
            val ip = IpPacket.parse(b, len)
            if (ip != null && ip.protocol == 17) DnsMessage.parseQuery(ip.payload())
            if (ip != null) IpPacket.tcpReset(ip)
        }
        // truncated real packets too
        val full = TestPackets.udp(true, 9, 53, TestPackets.dnsQuery("trunc.example"))
        for (n in 0..full.size) {
            val ip = IpPacket.parse(full.copyOf(n), n)
            if (ip != null) assertNotNull(ip.payload())
        }
    }
}
