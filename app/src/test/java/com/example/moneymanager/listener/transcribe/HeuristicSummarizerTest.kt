package com.example.moneymanager.listener.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicSummarizerTest {
    private fun seg(ms: Long, text: String) =
        TranscriptSegmentEntity(sessionId = 1, chunkIndex = 0, startMs = ms, endMs = ms + 1000, text = text)

    @Test
    fun isLabelledAsNotAi() = assertFalse(HeuristicSummarizer().isAi)

    @Test
    fun picksActionLinesInTimeOrder_englishAndHinglish() {
        val notes = HeuristicSummarizer().summarize(listOf(
            seg(9_000, "rahul will send the invoice by friday"),
            seg(1_000, "good morning everyone"),
            seg(5_000, "we need to fix the login bug"),
            seg(7_000, "report kal tak bhej dena"),
            seg(8_000, "the weather is nice"),
            seg(10_000, "WE NEED TO FIX THE LOGIN BUG")
        ))
        assertEquals(listOf(5_000L, 7_000L, 9_000L), notes.actionItems.map { it.startMs })
    }

    @Test
    fun keyLinesRepeatFrequentTopics_andSkipActions() {
        val notes = HeuristicSummarizer().summarize(listOf(
            seg(1_000, "the budget for marketing looks tight this quarter"),
            seg(2_000, "hello hello can you hear me"),
            seg(3_000, "marketing budget needs a second review"),
            seg(4_000, "random unrelated sentence about lunch today"),
            seg(5_000, "we need to cut the marketing budget")
        ))
        assertEquals(listOf(5_000L), notes.actionItems.map { it.startMs })
        assertEquals(listOf(1_000L, 3_000L), notes.keyLines.map { it.startMs })
    }

    @Test
    fun emptyAndNoiseGiveNothing() {
        assertTrue(HeuristicSummarizer().summarize(emptyList()).isEmpty)
        assertTrue(HeuristicSummarizer().summarize(listOf(seg(0, "uh"), seg(1, "  "))).isEmpty)
    }

    @Test
    fun capsAreRespected() {
        val many = (1..30).map { seg(it * 1000L, "item $it we need to do this") }
        assertEquals(3, HeuristicSummarizer(maxActions = 3).summarize(many).actionItems.size)
    }

    @Test
    fun keywordMatchingUsesWordBoundaries() {
        assertFalse(HeuristicSummarizer.isActionLike("the remainder was fine"))
        assertTrue(HeuristicSummarizer.isActionLike("please remind me"))
        assertTrue(HeuristicSummarizer.isActionLike("Let's ship it"))
        assertFalse(HeuristicSummarizer.isActionLike("a todolist app"))
    }
}
