package com.example.moneymanager.tunnel

/**
 * Exact names plus "*.suffix" patterns. A suffix pattern matches the domain
 * itself and every subdomain. Lookup walks the name's parent domains, so it is
 * O(labels) hash probes, independent of the list size.
 */
class DomainSet(exact: Collection<String>, suffixes: Collection<String> = emptyList()) {
    private val exact: Set<String> = exact.toHashSet()
    private val suffixes: Set<String> = suffixes.toHashSet()

    val size: Int get() = exact.size + suffixes.size

    /** The pattern that matched ("x.com" or "*.x.com"), or null. */
    fun match(name: String): String? {
        if (name in exact) return name
        if (suffixes.isEmpty()) return null
        var i = 0
        var cur = name
        while (true) {
            if (cur in suffixes) return "*.$cur"
            i = cur.indexOf('.')
            if (i < 0) return null
            cur = cur.substring(i + 1)
        }
    }

    companion object {
        val EMPTY = DomainSet(emptyList())

        /** Splits user patterns ("x.com", "*.x.com", ".x.com") into a set. Invalid ones are skipped. */
        fun ofPatterns(patterns: Collection<String>): DomainSet {
            val e = ArrayList<String>()
            val s = ArrayList<String>()
            for (p in patterns) {
                val n = normalizePattern(p) ?: continue
                if (n.startsWith("*.")) s += n.substring(2) else e += n
            }
            return DomainSet(e, s)
        }

        /** "x.com" / "*.x.com" in canonical lowercase form, or null when not a usable pattern. */
        fun normalizePattern(raw: String): String? {
            var p = raw.trim().lowercase().trimEnd('.')
            val wildcard = p.startsWith("*.") || p.startsWith(".")
            p = p.removePrefix("*").removePrefix(".")
            if (!HostsParser.isDomain(p)) return null
            return if (wildcard) "*.$p" else p
        }
    }
}
