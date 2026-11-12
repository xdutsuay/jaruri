package com.example.moneymanager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStorage
import com.example.moneymanager.listener.transcribe.ChunkDurations
import com.example.moneymanager.listener.transcribe.HeuristicSummarizer
import com.example.moneymanager.listener.transcribe.SessionNotes
import com.example.moneymanager.listener.transcribe.SessionSummarizer
import com.example.moneymanager.listener.transcribe.SttModelStore
import com.example.moneymanager.listener.transcribe.TranscriptExporter
import com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity
import com.example.moneymanager.listener.transcribe.TranscriptionScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One recorded session: transcript, transcription job, notes, export. */
class ListenerSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val storage = ListenerStorage(application)
    private val modelStore = SttModelStore(application)
    private val summarizer: SessionSummarizer = HeuristicSummarizer()

    var sessionId: Long = -1
        private set
    lateinit var session: LiveData<ListenerSessionEntity?>
        private set
    lateinit var segments: LiveData<List<TranscriptSegmentEntity>>
        private set
    lateinit var notes: LiveData<SessionNotes>
        private set
    lateinit var work: LiveData<List<WorkInfo>>
        private set

    /** Call once from the fragment; later calls are ignored. */
    fun bind(id: Long) {
        if (sessionId != -1L) return
        sessionId = id
        session = db.listenerSessionDao().observeById(id).asLiveData()
        val segs = if (id > 0) db.transcriptDao().observeForSession(id) else flowOf(emptyList())
        segments = segs.asLiveData()
        notes = segs.map { summarizer.summarize(it) }.asLiveData(Dispatchers.Default)
        work = TranscriptionScheduler.observe(getApplication(), id)
    }

    val hasModel: Boolean get() = modelStore.isInstalled

    fun transcribe() = viewModelScope.launch { TranscriptionScheduler.enqueue(getApplication(), sessionId) }
    fun cancel() = viewModelScope.launch { TranscriptionScheduler.cancel(getApplication(), sessionId) }
    fun restart() = viewModelScope.launch { TranscriptionScheduler.restart(getApplication(), sessionId) }

    suspend fun audio(session: ListenerSessionEntity): Pair<List<File>, List<Long>> = withContext(Dispatchers.IO) {
        val files = storage.segments(session.dirName)
        files to ChunkDurations.read(files)
    }

    /** Writes the export to cache/exports and returns the file, or null if there's no transcript. */
    suspend fun export(format: TranscriptExporter.Format): File? = withContext(Dispatchers.IO) {
        val s = db.listenerSessionDao().getById(sessionId) ?: return@withContext null
        val segs = db.transcriptDao().forSession(sessionId)
        if (segs.isEmpty()) return@withContext null
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
        val file = File(dir, TranscriptExporter.fileName(s.title, s.startedAt, format))
        file.writeText(TranscriptExporter.export(format, s.title, s.startedAt, s.durationMs, segs))
        file
    }
}
