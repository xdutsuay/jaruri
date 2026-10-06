package com.example.moneymanager.utils

import java.util.Locale

/**
 * Agnostic trust scoring for financial SMS.
 *
 * Combines body heuristics, DLT sender class, and whether the masked instrument
 * (card/account last-4) has been seen before. No hardcoded bank allow-lists.
 */
object SmsTrustEvaluator {

    data class Result(
        /** 0.0 (reject / scam) … 1.0 (high confidence ledger event). */
        val score: Float,
        val reasons: List<String>,
        val isLikelyScam: Boolean,
        val isPromotional: Boolean,
        /** False when score is too low to auto-import. */
        val shouldAutoImport: Boolean
    )

    data class KnownInstrument(
        val last4: String,
        val kind: String,
        val bankHint: String = "",
        val observationCount: Int = 0
    )

    private val SCAM_PHRASES = listOf(
        "click here", "click the link", "click link", "bit.ly", "tinyurl",
        "update kyc", "kyc expired", "kyc pending", "complete your kyc",
        "account will be blocked", "account blocked", "suspended",
        "urgent action", "immediate action", "verify now", "confirm asap",
        "share otp", "send otp", "do not share otp", // "do not share" alone is often legit
        "your cvv", "enter cvv", "share pin", "send pin",
        "won a lottery", "you have won", "claim prize", "lucky winner",
        "whatsapp.com", "wa.me/", "telegram.me", "t.me/"
    )

    /** Phrases that are common in real bank SMS and should not alone mark scam. */
    private val LEGIT_DISCLAIMERS = listOf(
        "if not done by you", "not you?", "call 1800", "customer care"
    )

    private const val AUTO_IMPORT_THRESHOLD = 0.45f

    fun evaluate(
        body: String,
        address: String? = null,
        parsed: ParsedSms? = null,
        knownInstruments: List<KnownInstrument> = emptyList()
    ): Result {
        val reasons = mutableListOf<String>()
        var score = 0.55f
        val lower = body.lowercase(Locale.ROOT)

        val promotional = SmsParser.isPromotional(body) ||
            (!address.isNullOrBlank() && SmsParser.isPromotionalSender(address))
        if (promotional) {
            reasons += "promotional"
            return Result(0.05f, reasons, isLikelyScam = false, isPromotional = true, shouldAutoImport = false)
        }
        if (SmsParser.isNonLedgerNotice(body)) {
            reasons += "mandate_or_schedule"
            return Result(0.05f, reasons, isLikelyScam = false, isPromotional = false, shouldAutoImport = false)
        }

        val scamHits = SCAM_PHRASES.count { lower.contains(it) }
        // "do not share otp" in real banks is a disclaimer — discount if paired with legit text
        val hasLegitDisclaimer = LEGIT_DISCLAIMERS.any { lower.contains(it) }
        val adjustedScam = if (hasLegitDisclaimer && scamHits <= 1) 0 else scamHits
        if (adjustedScam >= 2) {
            reasons += "scam_phrases"
            score -= 0.45f
        } else if (adjustedScam == 1) {
            reasons += "suspicious_phrase"
            score -= 0.2f
        }

        val senderClass = senderClass(address)
        when (senderClass) {
            SenderClass.TRANSACTIONAL, SenderClass.SERVICE -> {
                reasons += "dlt_$senderClass"
                score += 0.15f
            }
            SenderClass.PROMOTIONAL -> {
                reasons += "dlt_promo"
                score -= 0.4f
            }
            SenderClass.UNKNOWN -> Unit
        }

        if (SmsParser.looksLikeTransaction(body)) {
            reasons += "txn_shape"
            score += 0.15f
        } else if (parsed?.isComplete == true) {
            reasons += "parsed_complete"
            score += 0.1f
        } else {
            reasons += "weak_txn_shape"
            score -= 0.15f
        }

        val last4 = parsed?.instrumentLast4
        if (!last4.isNullOrBlank() && knownInstruments.isNotEmpty()) {
            val match = knownInstruments.firstOrNull { it.last4 == last4 }
            if (match != null) {
                reasons += "known_instrument"
                score += 0.2f + (0.05f * match.observationCount.coerceAtMost(4))
                val hint = parsed.bankHint?.lowercase(Locale.ROOT).orEmpty()
                val knownHint = match.bankHint.lowercase(Locale.ROOT)
                if (hint.isNotBlank() && knownHint.isNotBlank() &&
                    (hint.contains(knownHint) || knownHint.contains(hint))
                ) {
                    reasons += "bank_hint_match"
                    score += 0.05f
                }
            } else {
                reasons += "unknown_instrument"
                // New cards/accounts are normal — mild penalty only when many known ones exist
                if (knownInstruments.size >= 2) score -= 0.08f
            }
        } else if (!last4.isNullOrBlank()) {
            reasons += "first_instrument"
            score += 0.05f
        }

        if (parsed?.reportedBalance != null) {
            reasons += "has_balance"
            score += 0.05f
        }

        score = score.coerceIn(0f, 1f)
        val likelyScam = adjustedScam >= 2 || (adjustedScam >= 1 && score < 0.35f)
        val shouldImport = !likelyScam && !promotional && score >= AUTO_IMPORT_THRESHOLD &&
            (parsed?.isComplete == true)

        return Result(
            score = score,
            reasons = reasons,
            isLikelyScam = likelyScam,
            isPromotional = false,
            shouldAutoImport = shouldImport
        )
    }

    enum class SenderClass { TRANSACTIONAL, SERVICE, PROMOTIONAL, UNKNOWN }

    /**
     * Indian DLT sender suffixes: -T transactional, -S service, -P promotional.
     * Works on any prefix (bank-agnostic).
     */
    fun senderClass(address: String?): SenderClass {
        if (address.isNullOrBlank()) return SenderClass.UNKNOWN
        val a = address.trim().uppercase(Locale.ROOT)
        return when {
            a.endsWith("-P") || a.contains("-P,") -> SenderClass.PROMOTIONAL
            a.endsWith("-T") || a.contains("-T,") -> SenderClass.TRANSACTIONAL
            a.endsWith("-S") || a.contains("-S,") -> SenderClass.SERVICE
            else -> SenderClass.UNKNOWN
        }
    }
}
