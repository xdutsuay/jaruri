package com.example.moneymanager.listener.transcribe

import kotlin.math.sqrt

/** A line pulled out of the transcript, with where to seek to. */
data class NoteLine(val startMs: Long, val text: String)

data class SessionNotes(val actionItems: List<NoteLine>, val keyLines: List<NoteLine>) {
    val isEmpty get() = actionItems.isEmpty() && keyLines.isEmpty()
}

/**
 * Extension point for session notes. Today only [HeuristicSummarizer] exists.
 * A real (LLM) summary is deliberately deferred: nothing small enough to ship
 * in this APK summarises credibly on-device, and Listener never uses the network.
 */
interface SessionSummarizer {
    /** Shown to the user next to the output; must say plainly what produced it. */
    val isAi: Boolean
    fun summarize(segments: List<TranscriptSegmentEntity>): SessionNotes
}

/**
 * Keyword rules, not AI: lines that look like to-dos (English + common Hinglish
 * phrasing) and lines that repeat the meeting's most frequent words.
 */
class HeuristicSummarizer(
    private val maxActions: Int = 10,
    private val maxKeyLines: Int = 5
) : SessionSummarizer {
    override val isAi = false

    override fun summarize(segments: List<TranscriptSegmentEntity>): SessionNotes {
        val lines = segments.filter { it.text.isNotBlank() }.sortedBy { it.startMs }
        val actions = lines.filter { isActionLike(it.text) }
            .distinctBy { it.text.trim().lowercase() }
            .take(maxActions)
            .map { NoteLine(it.startMs, it.text.trim()) }
        val actionStarts = actions.map { it.startMs }.toSet()

        val tokenized = lines.map { it to tokens(it.text) }
        val freq = HashMap<String, Int>()
        tokenized.forEach { (_, t) -> t.filter(::isContentWord).distinct().forEach { freq[it] = (freq[it] ?: 0) + 1 } }
        val key = tokenized
            .filter { (seg, t) -> seg.startMs !in actionStarts && t.size >= MIN_WORDS }
            .map { (seg, t) ->
                val score = t.filter(::isContentWord).distinct()
                    .sumOf { (freq[it] ?: 0).let { f -> if (f >= 2) f else 0 } }.toDouble() / sqrt(t.size.toDouble())
                seg to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(maxKeyLines)
            .map { it.first }
            .sortedBy { it.startMs }
            .map { NoteLine(it.startMs, it.text.trim()) }
        return SessionNotes(actions, key)
    }

    companion object {
        private const val MIN_WORDS = 4

        private val ACTION = Regex(
            "\\b(" + listOf(
                "to ?do", "action items?", "follow ?up", "need to", "needs to", "have to", "has to",
                "we should", "you should", "i will", "i'll", "we will", "we'll", "let's", "lets",
                "deadline", "due (by|on|date)", "by (tomorrow|tonight|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next week|end of)",
                "remind", "assign(ed)?", "send (the|a|it|him|her|them)", "make sure",
                // Hinglish
                "karna hai", "karna hoga", "karna padega", "karenge", "kar dena", "kar do", "bhej (do|dena|denge)",
                "kal tak", "yaad dila"
            ).joinToString("|") + ")\\b",
            RegexOption.IGNORE_CASE
        )

        fun isActionLike(text: String) = ACTION.containsMatchIn(text)

        private val WORD = Regex("[\\p{L}\\p{N}']+")
        fun tokens(text: String) = WORD.findAll(text.lowercase()).map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()

        private val STOP = (
            "a an the and or but if then so of to in on at by for with from up down out over about into is are was were be been " +
                "being am do does did have has had having i you he she it we they me him her us them my your his its our their " +
                "this that these those there here what which who whom when where why how all any both each few more most other " +
                "some such no not only own same than too very can will just should now also yeah yes okay ok like um uh hmm " +
                "know think going get got right well really one two would could one's it's that's i'm you're we're they're don't " +
                "hai hain ki ka ke ko se main mein aur toh to ye yeh wo woh kya nahi na bhi tha thi the ho hoga" 
            ).split(' ').filter { it.isNotBlank() }.toSet()

        fun isContentWord(w: String) = w.length >= 3 && w !in STOP && !w.all { it.isDigit() }
    }
}
