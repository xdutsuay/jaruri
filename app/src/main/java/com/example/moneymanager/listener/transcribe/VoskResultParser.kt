package com.example.moneymanager.listener.transcribe

import org.json.JSONException
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * Parses a Vosk result JSON (with `setWords(true)`):
 * `{"result":[{"start":0.5,"end":1.0,"word":"hi"},…],"text":"hi …"}`.
 * Times are seconds from the start of the stream.
 */
object VoskResultParser {

    /**
     * @param fallbackMs stream position to use if the result carries no word
     * timings (shouldn't happen with setWords(true), but never drop text).
     * @return null for empty/blank/malformed results.
     */
    fun parse(json: String?, fallbackMs: Long): RawSegment? {
        if (json.isNullOrBlank()) return null
        return try {
            val o = JSONObject(json)
            val text = o.optString("text", "").trim().replace(Regex("\\s+"), " ")
            if (text.isEmpty()) return null
            val words = o.optJSONArray("result")
            if (words == null || words.length() == 0) {
                RawSegment(fallbackMs, fallbackMs, text)
            } else {
                val first = words.getJSONObject(0)
                val last = words.getJSONObject(words.length() - 1)
                val start = (first.optDouble("start", 0.0) * 1000).roundToLong()
                val end = (last.optDouble("end", first.optDouble("end", 0.0)) * 1000).roundToLong()
                RawSegment(start, maxOf(start, end), text)
            }
        } catch (_: JSONException) {
            null
        }
    }
}
