package com.example.moneymanager.tunnel.dns

/** The parts of a DNS query the filter needs, plus builders for the answers it makes itself. */
data class DnsQuery(val id: Int, val name: String, val qtype: Int, val questionEnd: Int)

enum class BlockMode { NXDOMAIN, ZERO_IP }

object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    private const val BLOCK_TTL = 60

    /** Standard query with exactly one question; null otherwise (we never guess). */
    fun parseQuery(m: ByteArray): DnsQuery? {
        if (m.size < 12) return null
        val flags = IpPacket.u16(m, 2)
        if (flags and 0x8000 != 0) return null // a response, not a query
        if ((flags ushr 11) and 0xF != 0) return null // opcode != QUERY
        if (IpPacket.u16(m, 4) != 1) return null
        val sb = StringBuilder()
        var i = 12
        while (true) {
            if (i >= m.size) return null
            val len = m[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if (len and 0xC0 != 0 || len > 63) return null // no compression in a question
            if (i + 1 + len > m.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append((m[i + k].toInt() and 0xFF).toChar().lowercaseChar())
            i += 1 + len
            if (sb.length > 253) return null
        }
        if (i + 4 > m.size) return null
        return DnsQuery(IpPacket.u16(m, 0), sb.toString(), IpPacket.u16(m, i), i + 4)
    }

    /** Answer for a blocked name: NXDOMAIN, or 0.0.0.0 / :: (other qtypes get an empty NOERROR). */
    fun blocked(query: ByteArray, q: DnsQuery, mode: BlockMode): ByteArray {
        if (mode == BlockMode.NXDOMAIN) return header(query, q, RCODE_NXDOMAIN, 0)
        val rdata = when (q.qtype) {
            TYPE_A -> ByteArray(4)
            TYPE_AAAA -> ByteArray(16)
            else -> return header(query, q, RCODE_NOERROR, 0)
        }
        val head = header(query, q, RCODE_NOERROR, 1)
        val rr = ByteArray(12 + rdata.size)
        rr[0] = 0xC0.toByte(); rr[1] = 12 // pointer to the question name
        IpPacket.put16(rr, 2, q.qtype)
        IpPacket.put16(rr, 4, 1) // IN
        IpPacket.put32(rr, 6, BLOCK_TTL.toLong())
        IpPacket.put16(rr, 10, rdata.size)
        return head + rr
    }

    fun servfail(query: ByteArray, q: DnsQuery): ByteArray = header(query, q, RCODE_SERVFAIL, 0)

    fun rcode(response: ByteArray): Int = if (response.size < 4) -1 else response[3].toInt() and 0xF

    /** Copies header + question, drops EDNS/other sections. */
    private fun header(query: ByteArray, q: DnsQuery, rcode: Int, answers: Int): ByteArray {
        val out = query.copyOf(q.questionEnd)
        val rd = query[2].toInt() and 0x01
        out[2] = (0x80 or rd).toByte() // QR=1, opcode 0, AA=0, TC=0, RD copied
        out[3] = (0x80 or rcode).toByte() // RA=1
        IpPacket.put16(out, 4, 1)
        IpPacket.put16(out, 6, answers)
        IpPacket.put16(out, 8, 0)
        IpPacket.put16(out, 10, 0)
        return out
    }
}
