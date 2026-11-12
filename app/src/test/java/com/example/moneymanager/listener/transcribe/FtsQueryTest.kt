package com.example.moneymanager.listener.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test
    fun prefixAndAllTerms() {
        assertEquals("budget* review*", FtsQuery.build("Budget review"))
    }

    @Test
    fun operatorsAndQuotesAreStripped() {
        // Upper-case FTS operators become plain lower-case terms.
        assertEquals("a* or* b* near* 3* c*", FtsQuery.build("\"a\" OR b NEAR/3 c*"))
        assertEquals("x*", FtsQuery.build("x -- ;"))
        assertNull(FtsQuery.build("  \"*()- "))
        assertNull(FtsQuery.build(""))
    }

    @Test
    fun devanagariWordsStayWhole() {
        // "काम" has a vowel sign (combining mark) that must not split the word.
        assertEquals("काम* करना*", FtsQuery.build("काम करना"))
    }

    @Test
    fun duplicatesDroppedAndTermsCapped() {
        assertEquals("a*", FtsQuery.build("a A a"))
        val many = (1..20).joinToString(" ") { "w$it" }
        assertEquals(FtsQuery.MAX_TERMS, FtsQuery.build(many)!!.split(" ").size)
    }
}
