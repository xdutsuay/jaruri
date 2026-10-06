package com.example.moneymanager.utils

import java.util.Locale
import kotlin.math.min

/**
 * Bank-agnostic UPI / merchant payee identity: normalize, extract VPA/aliases,
 * and conservatively fuzzy-match near-duplicate merchant strings.
 */
object PayeeResolver {

    /**
     * VPA-like handles: local@psp. Pattern-based (not an issuer allow-list).
     * Matches *@ok*, *@ybl, *@apl, *@paytm, *@ibl, *@axl, etc. via generic @psp.
     */
    private val VPA_PATTERN = Regex(
        """\b([a-zA-Z0-9][a-zA-Z0-9.\-_]{1,48}@[a-zA-Z][a-zA-Z0-9.\-_]{1,32})\b"""
    )

    data class Extracted(
        val vpa: String?,
        val merchantAlias: String?,
        /** Preferred display seed (merchant if useful, else VPA local part). */
        val displaySeed: String?
    )

    fun normalizeKey(raw: String): String {
        val s = raw.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return ""
        // Keep @ for VPAs; strip other punctuation / collapse space.
        val cleaned = buildString(s.length) {
            var prevSpace = false
            for (ch in s) {
                when {
                    ch.isLetterOrDigit() || ch == '@' || ch == '.' || ch == '_' || ch == '-' -> {
                        append(ch)
                        prevSpace = false
                    }
                    ch.isWhitespace() -> {
                        if (!prevSpace && isNotEmpty()) {
                            append(' ')
                            prevSpace = true
                        }
                    }
                }
            }
        }.trim()
        return cleaned
    }

    fun extractVpa(text: String): String? {
        val m = VPA_PATTERN.find(text) ?: return null
        val vpa = m.groupValues[1].lowercase(Locale.ROOT)
        // Prefer handles that look like PSP handles (contain a letter after @).
        return if (vpa.contains('@')) vpa else null
    }

    fun extractFromSms(text: String, description: String? = null): Extracted {
        val vpa = extractVpa(text)
        val merchant = description?.takeIf { isUsefulMerchant(it) }
            ?: extractMerchantFallback(text)
        val displaySeed = when {
            merchant != null && isUsefulMerchant(merchant) -> merchant
            vpa != null -> vpa.substringBefore('@').replace('.', ' ')
            else -> null
        }
        return Extracted(
            vpa = vpa?.let { normalizeKey(it) },
            merchantAlias = merchant?.let { normalizeKey(it) }?.takeIf { it.isNotBlank() },
            displaySeed = displaySeed?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Conservative near-duplicate check: exact, contains (longer ≥ 5), or
     * Levenshtein distance ≤ 2 for strings of length ≥ 5.
     */
    fun isNearDuplicate(a: String, b: String): Boolean {
        val na = normalizeKey(a)
        val nb = normalizeKey(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val shorter = if (na.length <= nb.length) na else nb
        val longer = if (na.length <= nb.length) nb else na
        if (shorter.length >= 5 && longer.contains(shorter)) return true
        if (na.length >= 5 && nb.length >= 5 &&
            kotlin.math.abs(na.length - nb.length) <= 2 &&
            levenshtein(na, nb) <= 2
        ) {
            return true
        }
        return false
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in a.indices) {
            cur[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                cur[j + 1] = min(
                    min(cur[j] + 1, prev[j + 1] + 1),
                    prev[j] + cost
                )
            }
            for (j in prev.indices) prev[j] = cur[j]
        }
        return prev[b.length]
    }

    fun aliasKeys(extracted: Extracted): List<String> {
        return listOfNotNull(extracted.vpa, extracted.merchantAlias)
            .map { normalizeKey(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun extractMerchantFallback(text: String): String? {
        val toMatch = Regex(
            """\b(?:to|towards|paid to|sent to)\s+([A-Za-z0-9 &._@-]{2,40})""",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null
        val candidate = toMatch.groupValues[1].trim()
            .replace(Regex("""[,.;:].*$"""), "")
            .replace(Regex("""\s+(using|via|on|ref|upi|for|info).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return candidate.takeIf { isUsefulMerchant(it) }
    }

    private fun isUsefulMerchant(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val c = candidate.lowercase(Locale.ROOT)
        if (c == "your" || c.startsWith("credit")) return false
        if (c == "rs" || c == "rs." || c == "inr" || c.startsWith("rs.") || c.startsWith("₹")) return false
        if (c.all { it.isDigit() || it == '.' || it == ',' }) return false
        if (c == "upi transaction" || c == "sms transaction") return false
        return true
    }
}
