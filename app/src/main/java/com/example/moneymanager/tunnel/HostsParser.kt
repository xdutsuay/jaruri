package com.example.moneymanager.tunnel

import java.io.BufferedReader

/**
 * Reads hosts-format ("0.0.0.0 ads.example.com") and plain one-domain-per-line
 * lists. Comments, localhost-style entries and anything that isn't a domain
 * name are skipped. Hosts entries block that exact name (hosts semantics).
 */
object HostsParser {
    private val SINK_IPS = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "0", "::0")
    private val RESERVED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts"
    )
    private val LABEL = Regex("[a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?")

    fun parse(reader: BufferedReader, maxEntries: Int = 2_000_000): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        reader.useLines { lines ->
            for (line in lines) {
                parseLine(line)?.let { out += it }
                if (out.size >= maxEntries) break
            }
        }
        return out
    }

    fun parseLine(line: String): String? {
        val body = line.substringBefore('#').trim()
        if (body.isEmpty()) return null
        val parts = body.split(' ', '\t').filter { it.isNotEmpty() }
        val host = when {
            parts.size >= 2 && parts[0] in SINK_IPS -> parts[1]
            parts.size == 1 -> parts[0]
            else -> return null
        }.lowercase().trimEnd('.')
        if (host in RESERVED || !isDomain(host)) return null
        return host
    }

    fun isDomain(s: String): Boolean {
        if (s.length !in 3..253 || '.' !in s) return false
        return s.split('.').all { LABEL.matches(it) } && !s.all { it.isDigit() || it == '.' }
    }
}
