package com.example.moneymanager.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class DomainRulesTest {

    @Test
    fun hostsLinesAndPlainLines() {
        assertEquals("ads.example.com", HostsParser.parseLine("0.0.0.0 ads.example.com # tracker"))
        assertEquals("ads.example.com", HostsParser.parseLine("127.0.0.1\tADS.Example.com."))
        assertEquals("plain.example.org", HostsParser.parseLine("plain.example.org"))
        assertEquals("x_y.example.org", HostsParser.parseLine(":: x_y.example.org"))
        for (skip in listOf("", "# comment", "127.0.0.1 localhost", "0.0.0.0 0.0.0.0", "255.255.255.255 broadcasthost",
            "::1 ip6-localhost", "192.168.1.5 router.lan", "0.0.0.0 nodot", "0.0.0.0 bad..name", "0.0.0.0 -bad.com",
            "0.0.0.0 1.2.3.4", "a b c")) {
            assertNull(skip, HostsParser.parseLine(skip))
        }
    }

    @Test
    fun patternsExactAndWildcard() {
        val set = DomainSet.ofPatterns(listOf("tracker.com", "*.ads.net", ".cdn.io", "BAD..", "  Mixed.Case.ORG. "))
        assertEquals("tracker.com", set.match("tracker.com"))
        assertNull("exact does not cover subdomains", set.match("x.tracker.com"))
        assertEquals("*.ads.net", set.match("ads.net"))
        assertEquals("*.ads.net", set.match("a.b.ads.net"))
        assertNull(set.match("badads.net"))
        assertEquals("*.cdn.io", set.match("img.cdn.io"))
        assertEquals("mixed.case.org", set.match("mixed.case.org"))
        assertEquals(4, set.size)
        assertNull(DomainSet.normalizePattern("*"))
        assertEquals("*.x.com", DomainSet.normalizePattern("*.X.com"))
    }

    @Test
    fun policyOrder() {
        val p = TunnelPolicy(
            blockedUids = setOf(10123),
            allow = DomainSet.ofPatterns(listOf("ok.ads.net")),
            block = DomainSet.ofPatterns(listOf("*.ads.net")),
            lists = listOf(NamedList("StevenBlack", DomainSet(listOf("listed.com"))))
        )
        assertEquals(Verdict(DnsDecision.BLOCKED_APP), p.decide("anything.com", 10123))
        assertEquals(Verdict(DnsDecision.ALLOWED_RULE, "ok.ads.net"), p.decide("ok.ads.net", 10001))
        assertEquals(Verdict(DnsDecision.BLOCKED_DOMAIN, "*.ads.net"), p.decide("x.ads.net", -1))
        assertEquals(Verdict(DnsDecision.BLOCKED_DOMAIN, "list:StevenBlack"), p.decide("listed.com", -1))
        assertEquals(Verdict(DnsDecision.ALLOWED), p.decide("fine.org", -1))
        assertEquals("unknown uid is never app-blocked", DnsDecision.ALLOWED, p.decide("fine.org", TunnelPolicy.UNKNOWN_UID).decision)
        assertEquals(DnsDecision.PAUSED, p.copy(paused = true).decide("listed.com", 10123).decision)
        assertTrue(Verdict(DnsDecision.BLOCKED_APP).blocks)
        assertFalse(Verdict(DnsDecision.PAUSED).blocks)
    }

    /** The real bundled asset parses to the advertised size, off the main thread budget. */
    @Test
    fun bundledStevenBlackListParses() {
        val f = File("src/tunnelUnified/assets/blocklists/stevenblack-hosts.gzip")
        assertTrue("asset present at ${f.absolutePath}", f.exists())
        val t0 = System.nanoTime()
        val domains = GZIPInputStream(f.inputStream()).bufferedReader().let { HostsParser.parse(it) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("about 78k domains, got ${domains.size}", domains.size in 70_000..90_000)
        assertFalse(domains.contains("localhost"))
        assertFalse(domains.contains("0.0.0.0"))
        assertTrue("parse took ${ms} ms", ms < 10_000)
    }

    /** The permissive list: exact size, provenance header, and a subset of the unified list it was cut from. */
    @Test
    fun bundledPermissiveListParses_andIsASubsetOfUnified() {
        val f = File("src/main/assets/blocklists/permissive-hosts.gzip")
        val text = GZIPInputStream(f.inputStream()).bufferedReader().readText()
        assertTrue(text.contains("6866cc66859a4ba99d945f076a09bca74a496d4c"))
        assertTrue(text.contains("Excluded on purpose: MVPS"))
        val permissive = HostsParser.parse(text.reader().buffered())
        assertEquals(18_902, permissive.size)
        val unified = GZIPInputStream(File("src/tunnelUnified/assets/blocklists/stevenblack-hosts.gzip").inputStream())
            .bufferedReader().let { HostsParser.parse(it) }
        val outside = permissive.count { it !in unified }
        // StevenBlack applies a whitelist when unifying, so a few may differ; the bulk must overlap.
        assertTrue("$outside of ${permissive.size} not in unified", outside < permissive.size / 20)
        val notice = File("src/main/assets/blocklists/PERMISSIVE-NOTICE.txt").readText()
        for (src in listOf("AdAway", "CC BY 3.0", "Tiuxo", "CC BY 4.0", "MIT License")) assertTrue(src, notice.contains(src))
    }

    @Test
    fun matcherPerformanceAt200kDomains() {
        val exact = (0 until 200_000).map { "host$it.tracker${it % 997}.example" }
        val suffixes = (0 until 2_000).map { "zone$it.net" }
        val t0 = System.nanoTime()
        val set = DomainSet(exact, suffixes)
        val buildMs = (System.nanoTime() - t0) / 1_000_000
        val queries = (0 until 200_000).map {
            when (it % 3) {
                0 -> "host$it.tracker${it % 997}.example"
                1 -> "a.b.c.zone${it % 4000}.net"
                else -> "www.some-site$it.co.in"
            }
        }
        repeat(2) { queries.forEach { set.match(it) } } // warm-up
        val t1 = System.nanoTime()
        var hits = 0
        repeat(5) { queries.forEach { if (set.match(it) != null) hits++ } }
        val perLookupNs = (System.nanoTime() - t1) / (5L * queries.size)
        assertTrue("hits=$hits", hits > 0)
        assertTrue("build ${buildMs} ms", buildMs < 5_000)
        // Plan target: < 5 µs per lookup (generous here so slow CI boxes don't flake).
        assertTrue("lookup ${perLookupNs} ns", perLookupNs < 5_000)
    }
}
