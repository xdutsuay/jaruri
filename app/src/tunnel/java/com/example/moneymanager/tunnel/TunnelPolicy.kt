package com.example.moneymanager.tunnel

/** What happened to one lookup. Stored in the log as the enum name. */
enum class DnsDecision { ALLOWED, ALLOWED_RULE, PAUSED, BLOCKED_APP, BLOCKED_DOMAIN, SERVFAIL }

data class Verdict(val decision: DnsDecision, val ref: String? = null) {
    val blocks: Boolean get() = decision == DnsDecision.BLOCKED_APP || decision == DnsDecision.BLOCKED_DOMAIN
}

data class NamedList(val name: String, val domains: DomainSet)

/**
 * Immutable snapshot of the rules. Order:
 * paused → app blocked (best effort; needs the system to tell us the app) →
 * allowlist → manual blocklist → enabled lists → allowed.
 */
data class TunnelPolicy(
    val paused: Boolean = false,
    val blockedUids: Set<Int> = emptySet(),
    val allow: DomainSet = DomainSet.EMPTY,
    val block: DomainSet = DomainSet.EMPTY,
    val lists: List<NamedList> = emptyList()
) {
    fun decide(name: String, uid: Int): Verdict {
        if (paused) return Verdict(DnsDecision.PAUSED)
        if (uid >= 0 && uid in blockedUids) return Verdict(DnsDecision.BLOCKED_APP)
        allow.match(name)?.let { return Verdict(DnsDecision.ALLOWED_RULE, it) }
        block.match(name)?.let { return Verdict(DnsDecision.BLOCKED_DOMAIN, it) }
        for (l in lists) l.domains.match(name)?.let { return Verdict(DnsDecision.BLOCKED_DOMAIN, "list:${l.name}") }
        return Verdict(DnsDecision.ALLOWED)
    }

    companion object {
        const val UNKNOWN_UID = -1
    }
}
