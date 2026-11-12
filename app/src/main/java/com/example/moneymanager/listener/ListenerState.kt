package com.example.moneymanager.listener

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process view of the live session. Resets to [Idle] when the process dies,
 * which is exactly when RECORDING rows must be reconciled to INTERRUPTED.
 */
object ListenerState {
    sealed class Status {
        object Idle : Status()
        /** Service accepted Start; the row for [startedAt] may not exist yet. */
        data class Starting(val startedAt: Long) : Status()
        data class Recording(
            val sessionId: Long,
            val startedAt: Long,
            val segmentIndex: Int
        ) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    /** Last start failure (e.g. FGS not allowed) for the UI to show once. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    val isRecording: Boolean get() = _status.value is Status.Recording

    /** Starting or Recording: a second Start must be ignored, Stop must be honoured. */
    val isBusy: Boolean get() = _status.value !is Status.Idle

    /**
     * True if the live service owns this row. Evaluated per row *after* rows are
     * read, so a session created mid-reconcile is never mistaken for a dead one.
     */
    fun ownsSession(row: ListenerSessionEntity): Boolean = when (val s = _status.value) {
        is Status.Recording -> s.sessionId == row.id
        is Status.Starting -> s.startedAt == row.startedAt
        Status.Idle -> false
    }

    internal fun activeSessionIdForTest(): Long? = (_status.value as? Status.Recording)?.sessionId

    internal fun set(status: Status) {
        _status.value = status
    }

    internal fun reportError(message: String?) {
        _lastError.value = message
    }

    fun consumeError(): String? {
        val e = _lastError.value
        _lastError.value = null
        return e
    }
}
