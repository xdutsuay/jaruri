package com.example.moneymanager.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayeeResolverTest {

    @Test
    fun extractVpaFromCommonPspHandles() {
        assertEquals(
            "merchant@oksbi",
            PayeeResolver.extractVpa("Paid to merchant@oksbi via UPI")
        )
        assertEquals(
            "shop@ybl",
            PayeeResolver.extractVpa("UPI to shop@ybl Ref 123")
        )
        assertEquals(
            "cafe@apl",
            PayeeResolver.extractVpa("sent to cafe@apl")
        )
        assertEquals(
            "store@paytm",
            PayeeResolver.extractVpa("to store@paytm")
        )
    }

    @Test
    fun normalizeKeyStripsNoise() {
        assertEquals("swiggy", PayeeResolver.normalizeKey("  SWIGGY!! "))
        assertEquals("foo@oksbi", PayeeResolver.normalizeKey("Foo@OkSBI"))
    }

    @Test
    fun nearDuplicateConservative() {
        assertTrue(PayeeResolver.isNearDuplicate("SWIGGY", "swiggy"))
        assertTrue(PayeeResolver.isNearDuplicate("Amazon Pay", "amazon"))
        assertTrue(PayeeResolver.isNearDuplicate("Flipkart", "Flipkartt"))
        assertFalse(PayeeResolver.isNearDuplicate("Swiggy", "Zomato"))
        assertFalse(PayeeResolver.isNearDuplicate("ab", "abc")) // too short
    }

    @Test
    fun extractFromSmsPrefersMerchantAndVpa() {
        val ex = PayeeResolver.extractFromSms(
            "Rs.100 paid to SWIGGY using PhonePe. VPA swiggy@ybl",
            description = "SWIGGY"
        )
        assertEquals("swiggy@ybl", ex.vpa)
        assertEquals("swiggy", ex.merchantAlias)
        assertNotNull(ex.displaySeed)
    }
}
