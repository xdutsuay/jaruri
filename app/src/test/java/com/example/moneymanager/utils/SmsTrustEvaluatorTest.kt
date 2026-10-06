package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTrustEvaluatorTest {

    @Test
    fun promotionalSenderRejected() {
        val body = "Rs.100 debited from A/c XX11 at STORE. Avl Bal Rs.500"
        val r = SmsTrustEvaluator.evaluate(body, address = "JD-SBICRD-P")
        assertTrue(r.isPromotional)
        assertFalse(r.shouldAutoImport)
    }

    @Test
    fun scamPhrasesLowerScore() {
        val body =
            "Your account will be blocked. Click here bit.ly/x to update kyc. Share OTP now. Rs.1 debited"
        val r = SmsTrustEvaluator.evaluate(body, address = "XX-FAKE-T")
        assertTrue(r.isLikelyScam || r.score < 0.45f)
        assertFalse(r.shouldAutoImport)
    }

    @Test
    fun knownInstrumentBoostsTrust() {
        val body =
            "Rs.1,250.00 debited from A/c XX4521 on 08-09-2026 at AMAZON. Avl Bal Rs.12,340.50"
        val parsed = SmsParser.parse(body)!!
        val known = listOf(
            SmsTrustEvaluator.KnownInstrument("4521", "BANK", "IDFC", observationCount = 3)
        )
        val r = SmsTrustEvaluator.evaluate(body, "CP-IDFCFB-S", parsed, known)
        assertTrue(r.reasons.contains("known_instrument"))
        assertTrue(r.shouldAutoImport)
        assertTrue(r.score > 0.6f)
    }

    @Test
    fun senderClassFromDltSuffix() {
        assertEquals(
            SmsTrustEvaluator.SenderClass.PROMOTIONAL,
            SmsTrustEvaluator.senderClass("AB-BANK-P")
        )
        assertEquals(
            SmsTrustEvaluator.SenderClass.TRANSACTIONAL,
            SmsTrustEvaluator.senderClass("AB-BANK-T")
        )
        assertEquals(
            SmsTrustEvaluator.SenderClass.SERVICE,
            SmsTrustEvaluator.senderClass("CP-IDFCFB-S")
        )
    }
}
