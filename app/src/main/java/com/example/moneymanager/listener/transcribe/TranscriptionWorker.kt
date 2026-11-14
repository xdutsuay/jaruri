package com.example.moneymanager.listener.transcribe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerNotifications
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * WorkManager job around [TranscriptionRunner]. Asks to run in the foreground
 * (dataSync) so long sessions aren't cut at WorkManager's 10-minute limit; if
 * Android refuses (background start limits), it carries on in the background,
 * and because progress is committed per chunk a stop just resumes later.
 *
 * Stop handling: cancelled by the app (user pressed Cancel) → CANCELLED;
 * stopped by the system → QUEUED, and WorkManager re-runs it.
 */
class TranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1)
        if (sessionId <= 0) return Result.failure()
        val db = AppDatabase.getDatabase(applicationContext)
        val row = db.listenerSessionDao().getById(sessionId) ?: return Result.success()
        if (row.transcriptState == ListenerSessionEntity.TRANSCRIPT_CANCELLED) return Result.success()

        tryForeground(row.title, 0)

        val store = SttModelStore(applicationContext)
        val runner = TranscriptionRunner(
            sessions = db.listenerSessionDao(),
            transcripts = db.transcriptDao(),
            storage = ListenerStorage(applicationContext),
            engineFactory = {
                val name = BundledSttModels.ensureInstalled(applicationContext, store)
                    ?: throw EngineUnavailableException(applicationContext.getString(R.string.listener_stt_no_model))
                VoskSpeechEngine.load(store.currentDir, name)
            },
            decoder = MediaCodecPcmDecoder(),
            durationsOf = ChunkDurations::read
        )

        var lastPct = -1
        val outcome = try {
            runner.run(sessionId, isCancelled = { isStopped }) { p ->
                val pct = (p.fraction * 100).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    setProgressAsync(workDataOf(KEY_PROGRESS to pct))
                    if (pct % 5 == 0) tryForegroundAsync(row.title, pct)
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                recordStop(db, sessionId)
                dropIfSessionDeleted(db, sessionId)
            }
            throw e
        }
        dropIfSessionDeleted(db, sessionId)
        return when (outcome) {
            TranscriptionRunner.Outcome.Done -> Result.success()
            is TranscriptionRunner.Outcome.Failed -> Result.failure()
            TranscriptionRunner.Outcome.Cancelled -> {
                recordStop(db, sessionId)
                if (isStopped) Result.retry() else Result.success()
            }
        }
    }

    /** The session was deleted while we worked: remove anything we just wrote. */
    private suspend fun dropIfSessionDeleted(db: AppDatabase, sessionId: Long) {
        if (db.listenerSessionDao().getById(sessionId) == null) db.transcriptDao().deleteForSession(sessionId)
    }

    private suspend fun recordStop(db: AppDatabase, sessionId: Long) {
        val current = db.listenerSessionDao().getById(sessionId)?.transcriptState
        if (current == ListenerSessionEntity.TRANSCRIPT_CANCELLED) return
        val byUser = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP
        db.transcriptDao().setState(
            sessionId,
            if (byUser) ListenerSessionEntity.TRANSCRIPT_CANCELLED else ListenerSessionEntity.TRANSCRIPT_QUEUED,
            null
        )
    }

    private suspend fun tryForeground(title: String, pct: Int) {
        try {
            setForeground(foregroundInfo(title, pct))
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException / IllegalStateException:
            // keep going in the background; per-chunk commits make a stop harmless.
        }
    }

    private fun tryForegroundAsync(title: String, pct: Int) {
        try {
            setForegroundAsync(foregroundInfo(title, pct))
        } catch (_: Exception) {
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("", 0)

    private fun foregroundInfo(title: String, pct: Int): ForegroundInfo {
        val ctx = applicationContext
        ensureChannel(ctx)
        // Our own receiver (not WorkManager's cancel intent): it records CANCELLED before
        // cancelling, so a notification Cancel is a user cancel on every Android version
        // (stopReason only exists on 12+), and the session never sits "paused".
        val cancel = TranscriptionCancelReceiver.pendingIntent(ctx, inputData.getLong(KEY_SESSION_ID, 0))
        val n: Notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(ctx.getString(R.string.listener_stt_notif_title))
            .setContentText(title)
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(ListenerNotifications.openListenerIntent(ctx))
            .addAction(R.drawable.ic_mic, ctx.getString(R.string.cancel), cancel)
            .build()
        val nid = NOTIFICATION_ID_BASE + (inputData.getLong(KEY_SESSION_ID, 0) % 1000).toInt()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(nid, n)
        }
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.listener_stt_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_PROGRESS = "progress"
        private const val CHANNEL = "listener_transcribe"
        private const val NOTIFICATION_ID_BASE = 4200
    }
}
