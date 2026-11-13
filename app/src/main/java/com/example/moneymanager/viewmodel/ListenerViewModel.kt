package com.example.moneymanager.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerState
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.transcribe.FtsQuery
import com.example.moneymanager.listener.transcribe.SttModelStore
import com.example.moneymanager.listener.transcribe.TranscriptHit
import com.example.moneymanager.listener.transcribe.TranscriptionScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ListenerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = ListenerRepository(application)
    private val transcripts = AppDatabase.getDatabase(application).transcriptDao()
    private val modelStore = SttModelStore(application)

    /** Search results, or null while the query is blank (show sessions instead). */
    data class Search(val query: String, val hits: List<TranscriptHit>)

    /** Search date filter: only sessions started in the last N days (null = any time). */
    enum class Range(val days: Int?) { ALL(null), WEEK(7), MONTH(30) }

    private val query = MutableStateFlow("")
    private val range = MutableStateFlow(Range.ALL)
    val search: LiveData<Search?> = combine(query.debounce(SEARCH_DEBOUNCE_MS), range) { q, r -> q to r }
        .distinctUntilChanged()
        .mapLatest { (q, r) ->
            val match = FtsQuery.build(q)
            val since = r.days?.let { System.currentTimeMillis() - it * 86_400_000L } ?: 0L
            when {
                q.isBlank() -> null
                match == null -> Search(q, emptyList())
                else -> Search(q, withContext(Dispatchers.IO) { transcripts.search(match, sinceMs = since) })
            }
        }
        .asLiveData()

    fun setQuery(q: String) {
        query.value = q
    }

    fun setRange(r: Range) {
        range.value = r
    }

    /** Bytes of audio kept by Listener (all sessions). */
    val totalBytes: LiveData<Long> = repo.totalBytes.asLiveData()
    val markCount: LiveData<Int> = ListenerState.markCount.asLiveData()

    fun deleteAudio(id: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.deleteAudioKeepTranscript(id, System.currentTimeMillis()) }
            onResult(ok)
        }
    }

    /** Installed model name, or null. */
    val modelName = MutableLiveData<String?>(modelStore.installedName)
    val modelBusy = MutableLiveData(false)

    fun importModel(uri: Uri, onResult: (Result<String>) -> Unit) {
        if (modelBusy.value == true) return
        modelBusy.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }?.removeSuffix(".zip") ?: "speech-model"
                    val input = resolver.openInputStream(uri) ?: throw java.io.IOException("Can't open file")
                    input.use { modelStore.import(it, name) }
                }
            }
            modelBusy.value = false
            modelName.value = modelStore.installedName
            onResult(result)
        }
    }

    fun removeModel() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modelStore.remove() }
            modelName.value = modelStore.installedName
        }
    }
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
            val ok = withContext(Dispatchers.IO) {
                TranscriptionScheduler.cancel(getApplication(), id)
                repo.delete(id)
            }
            onResult(ok)
        }
    }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
