package com.example.moneymanager.utils

import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Colored circular letter icons per category (Money Manager–style cues).
 */
object CategoryIcons {

    private val PALETTE = intArrayOf(
        0xFFE53935.toInt(), // red
        0xFF1E88E5.toInt(), // blue
        0xFF43A047.toInt(), // green
        0xFFFB8C00.toInt(), // orange
        0xFF8E24AA.toInt(), // purple
        0xFF00897B.toInt(), // teal
        0xFFFDD835.toInt(), // yellow
        0xFF6D4C41.toInt(), // brown
        0xFF546E7A.toInt(), // blue-grey
        0xFFEC407A.toInt()  // pink
    )

    private val KNOWN = mapOf(
        "food" to 0xFFF9A825.toInt(),
        "bills" to 0xFF29B6F6.toInt(),
        "transportation" to 0xFF26A69A.toInt(),
        "home" to 0xFFFF7043.toInt(),
        "car" to 0xFF7E57C2.toInt(),
        "entertainment" to 0xFFAB47BC.toInt(),
        "shopping" to 0xFFEC407A.toInt(),
        "clothing" to 0xFF5C6BC0.toInt(),
        "health" to 0xFFEF5350.toInt(),
        "baby" to 0xFFEF5350.toInt(),
        "electronics" to 0xFF78909C.toInt(),
        "investment" to 0xFFFDD835.toInt(),
        "papa" to 0xFFEF5350.toInt(),
        "others" to 0xFFE53935.toInt(),
        "salary" to 0xFF43A047.toInt(),
        "transfer" to 0xFF546E7A.toInt()
    )

    fun colorFor(category: String): Int {
        val key = category.trim().lowercase()
        KNOWN[key]?.let { return it }
        if (key.isEmpty()) return PALETTE[0]
        var h = 0
        for (ch in key) h = 31 * h + ch.code
        return PALETTE[kotlin.math.abs(h) % PALETTE.size]
    }

    fun bind(bg: View, letterView: TextView, category: String) {
        val color = colorFor(category)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        bg.background = drawable
        letterView.text = category.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        // Dark letter on bright yellows
        val lum = ColorUtils.calculateLuminance(color)
        letterView.setTextColor(if (lum > 0.6) 0xFF212121.toInt() else 0xFFFFFFFF.toInt())
    }
}
