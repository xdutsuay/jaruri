package com.example.moneymanager.tunnel

import android.content.Context
import com.example.moneymanager.tunnel.dns.BlockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small synchronous settings the VPN thread reads; [changes] ticks on every write. */
class TunnelPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("tunnel_prefs", Context.MODE_PRIVATE)

    var consentAccepted: Boolean
        get() = p.getBoolean(KEY_CONSENT, false)
        set(v) = edit { putBoolean(KEY_CONSENT, v) }

    var bundledListEnabled: Boolean
        get() = p.getBoolean(KEY_BUNDLED, true)
        set(v) = edit { putBoolean(KEY_BUNDLED, v) }

    var blockMode: BlockMode
        get() = if (p.getString(KEY_MODE, null) == BlockMode.ZERO_IP.name) BlockMode.ZERO_IP else BlockMode.NXDOMAIN
        set(v) = edit { putString(KEY_MODE, v.name) }

    /** Filtering paused until this wall-clock time (0 = not paused). */
    var pausedUntil: Long
        get() = p.getLong(KEY_PAUSED, 0)
        set(v) = edit { putLong(KEY_PAUSED, v) }

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        tick.value = tick.value + 1
    }

    val changes: StateFlow<Long> get() = tick

    companion object {
        private const val KEY_CONSENT = "consent"
        private const val KEY_BUNDLED = "bundled_list"
        private const val KEY_MODE = "block_mode"
        private const val KEY_PAUSED = "paused_until"
        private val tick = MutableStateFlow(0L)
    }
}
