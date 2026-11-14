package com.example.moneymanager.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-wide tunnel status for the UI and the tile. */
object TunnelState {
    sealed class Status {
        object Off : Status()
        object Starting : Status()
        data class On(val since: Long, val bypassCount: Int) : Status()
        /** Another VPN took over, or the user revoked us in Settings. */
        object Revoked : Status()
        data class Failed(val message: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status

    val isOn: Boolean get() = _status.value is Status.On || _status.value is Status.Starting

    internal fun set(s: Status) {
        _status.value = s
    }
}
