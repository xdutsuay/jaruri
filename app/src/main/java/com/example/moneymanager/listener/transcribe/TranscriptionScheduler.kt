package com.example.moneymanager.listener.transcribe

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerSessionEntity

/** One unique WorkManager job per session. */
object TranscriptionScheduler {

    fun workName(sessionId: Long) = "listener-transcribe-$sessionId"

    /** Queue (or resume) transcription. A job already queued/running is kept. */
    suspend fun enqueue(context: Context, sessionId: Long) {
        val dao = AppDatabase.getDatabase(context).transcriptDao()
        dao.setState(sessionId, ListenerSessionEntity.TRANSCRIPT_QUEUED, null)
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(sessionId), ExistingWorkPolicy.KEEP, request(sessionId)
        )
    }

    /** Throw away the transcript and start over (e.g. after importing a better model). */
    suspend fun restart(context: Context, sessionId: Long) {
        val dao = AppDatabase.getDatabase(context).transcriptDao()
        WorkManager.getInstance(context).cancelUniqueWork(workName(sessionId))
        dao.deleteForSession(sessionId)
        dao.resetProgress(sessionId, ListenerSessionEntity.TRANSCRIPT_QUEUED)
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(sessionId), ExistingWorkPolicy.REPLACE, request(sessionId)
        )
    }

    /** User cancel: state first, so the worker can tell this from a system stop. Progress is kept. */
    suspend fun cancel(context: Context, sessionId: Long) {
        AppDatabase.getDatabase(context).transcriptDao()
            .setState(sessionId, ListenerSessionEntity.TRANSCRIPT_CANCELLED, null)
        WorkManager.getInstance(context).cancelUniqueWork(workName(sessionId))
    }

    fun observe(context: Context, sessionId: Long): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(workName(sessionId))

    private fun request(sessionId: Long) =
        OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf(TranscriptionWorker.KEY_SESSION_ID to sessionId))
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(TAG)
            .build()

    const val TAG = "listener-transcribe"
}
