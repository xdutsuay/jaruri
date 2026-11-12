package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ListenerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ListenerRepository(application)
    private val settings = SettingsRepository(application)

    val sessions: LiveData<List<ListenerSessionEntity>> = repo.sessions.asLiveData()
    val status: LiveData<ListenerState.Status> = ListenerState.status.asLiveData()
    val lastError: LiveData<String?> = ListenerState.lastError.asLiveData()

    init {
        reconcile()
    }

    /** Close sessions a dead process left as RECORDING. Safe to call repeatedly. */
    fun reconcile() {
        viewModelScope.launch(Dispatchers.IO) { repo.reconcileInterrupted() }
    }

    suspend fun isConsentAccepted(): Boolean = settings.listenerConsentAccepted.first()

    fun acceptConsent() {
        viewModelScope.launch { settings.setListenerConsentAccepted(true) }
    }

    suspend fun freeBytes(): Long = withContext(Dispatchers.IO) { repo.storage.freeBytes() }

    suspend fun segments(session: ListenerSessionEntity): List<File> =
        withContext(Dispatchers.IO) { repo.storage.segments(session.dirName) }

    fun rename(id: Long, title: String) {
        viewModelScope.launch(Dispatchers.IO) { repo.rename(id, title) }
    }

    fun delete(id: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.delete(id) }
            onResult(ok)
        }
    }
}
