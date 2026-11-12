package com.example.moneymanager.listener.transcribe

/**
 * Turns free text into a safe FTS4 MATCH expression: word characters only
 * (letters, digits, combining marks so Devanagari words stay whole), each
 * term prefix-matched, all terms required. FTS operators and quotes in the
 * input are dropped, so user text can't produce a malformed query.
 */
object FtsQuery {
    const val MAX_TERMS = 8

    fun build(input: String): String? {
        val terms = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotEmpty()) {
                terms += cur.toString().lowercase()
                cur.setLength(0)
            }
        }
        for (ch in input) {
            if (isWordChar(ch)) cur.append(ch) else flush()
        }
        flush()
        val kept = terms.filter { it.isNotBlank() }.distinct().take(MAX_TERMS)
        if (kept.isEmpty()) return null
        return kept.joinToString(" ") { "$it*" }
    }

    private fun isWordChar(ch: Char): Boolean {
        if (Character.isLetterOrDigit(ch)) return true
        val t = Character.getType(ch)
        return t == Character.NON_SPACING_MARK.toInt() ||
            t == Character.COMBINING_SPACING_MARK.toInt()
    }
}
