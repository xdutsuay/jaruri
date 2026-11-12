package com.example.moneymanager.listener

import android.content.Context
import com.example.moneymanager.data.AppDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Session rows + their files. Keeps the two in step: delete removes files
 * first (row survives a failed delete so the user can retry), and stale
 * RECORDING rows are closed as INTERRUPTED rather than resumed.
 */
class ListenerRepository(
    private val dao: ListenerSessionDao,
    val storage: ListenerStorage
) {
    constructor(context: Context) : this(
        AppDatabase.getDatabase(context).listenerSessionDao(),
        ListenerStorage(context)
    )

    val sessions: Flow<List<ListenerSessionEntity>> = dao.getAll()

    suspend fun get(id: Long): ListenerSessionEntity? = dao.getById(id)

    suspend fun create(title: String, startedAt: Long): ListenerSessionEntity {
        val dirName = ListenerPolicy.sessionDirName(startedAt)
        storage.ensureSessionDir(dirName)
        val row = ListenerSessionEntity(
            title = title,
            startedAt = startedAt,
            dirName = dirName,
            state = ListenerSessionEntity.STATE_RECORDING
        )
        val id = dao.insert(row)
        return row.copy(id = id)
    }

    /** Refresh size/chunk counters while recording. */
    suspend fun updateProgress(id: Long, now: Long) {
        val row = dao.getById(id) ?: return
        val segs = storage.segments(row.dirName)
        dao.update(
            row.copy(
                segmentCount = segs.size,
                bytes = segs.sumOf { it.length() },
                durationMs = (now - row.startedAt).coerceAtLeast(0)
            )
        )
    }

    suspend fun finish(id: Long, endedAt: Long, reason: ListenerStopReason) {
        val row = dao.getById(id) ?: return
        val segs = storage.segments(row.dirName)
        val state = when (reason) {
            ListenerStopReason.RECORDER_ERROR,
            ListenerStopReason.PROCESS_DIED -> ListenerSessionEntity.STATE_INTERRUPTED
            else -> ListenerSessionEntity.STATE_DONE
        }
        dao.update(
            row.copy(
                endedAt = endedAt,
                durationMs = (endedAt - row.startedAt).coerceAtLeast(0),
                segmentCount = segs.size,
                bytes = segs.sumOf { it.length() },
                state = state,
                notes = reason.name
            )
        )
    }

    /**
     * Close RECORDING rows that no live service owns (process was killed,
     * force-stopped, or crashed). Duration is estimated from the last chunk's
     * mtime. Never restarts recording.
     *
     * @return number of rows marked INTERRUPTED.
     */
    suspend fun reconcileInterrupted(activeSessionId: Long?): Int {
        var n = 0
        for (row in dao.getByState(ListenerSessionEntity.STATE_RECORDING)) {
            if (row.id == activeSessionId) continue
            val segs = storage.segments(row.dirName)
            val lastWrite = segs.maxOfOrNull { it.lastModified() } ?: row.startedAt
            val endedAt = maxOf(lastWrite, row.startedAt)
            dao.update(
                row.copy(
                    endedAt = endedAt,
                    durationMs = endedAt - row.startedAt,
                    segmentCount = segs.size,
                    bytes = segs.sumOf { it.length() },
                    state = ListenerSessionEntity.STATE_INTERRUPTED,
                    notes = ListenerStopReason.PROCESS_DIED.name
                )
            )
            n++
        }
        return n
    }

    suspend fun rename(id: Long, title: String) {
        val t = title.trim()
        if (t.isNotEmpty()) dao.rename(id, t)
    }

    /** Deletes audio files then the row. Returns false (row kept) if files remain. */
    suspend fun delete(id: Long): Boolean {
        val row = dao.getById(id) ?: return true
        if (!storage.deleteSession(row.dirName)) return false
        dao.deleteById(id)
        return true
    }
}
