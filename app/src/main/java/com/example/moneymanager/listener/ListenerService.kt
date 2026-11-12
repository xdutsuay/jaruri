package com.example.moneymanager.listener

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.moneymanager.R
import com.example.moneymanager.listener.transcribe.SttModelStore
import com.example.moneymanager.listener.transcribe.TranscriptionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manual meeting recorder. Runs only between an explicit Start (from the
 * visible Listener screen) and Stop (screen, notification, or QS tile), or
 * until a safety limit fires. START_NOT_STICKY and no boot receiver: if the
 * OS kills us we do not come back; the row is reconciled to INTERRUPTED.
 */
class ListenerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repo: ListenerRepository
    private var recorder: ListenerRecorder? = null

    private var sessionId: Long = -1
    private var sessionDir: String = ""
    private var startedAt: Long = 0
    private var segmentIndex = 0
    private var segmentMs = ListenerPolicy.DEFAULT_SEGMENT_MS
    private var maxMs = ListenerPolicy.DEFAULT_MAX_SESSION_MS
    private var stopping = false

    private val rollTask = Runnable { rollSegment() }
    private val watchdog = object : Runnable {
        override fun run() {
            checkLimits()
            if (ListenerState.isRecording && !stopping) handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = ListenerRepository(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession(intent.getStringExtra(EXTRA_TITLE))
            ACTION_STOP -> stopSession(ListenerStopReason.USER)
            else -> if (!ListenerState.isBusy) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startSession(requestedTitle: String?) {
        if (ListenerState.isBusy || stopping) return
        ListenerState.reportError(null)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            failStart(getString(R.string.listener_err_mic_permission))
            return
        }
        val now = System.currentTimeMillis()
        val title = requestedTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: ListenerPolicy.defaultTitle(now)

        // Must reach startForeground() within a few seconds of startForegroundService().
        try {
            ServiceCompat.startForeground(
                this,
                ListenerNotifications.ID_ONGOING,
                ListenerNotifications.ongoing(this, title, now),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else 0
            )
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException / SecurityException on 12+/14+
            failStart(getString(R.string.listener_err_fgs, e.javaClass.simpleName))
            return
        }

        if (!ListenerPolicy.canStart(repo.storage.freeBytes())) {
            failStart(getString(R.string.listener_err_low_storage_start))
            return
        }

        segmentMs = ListenerPolicy.effectiveSegmentMs(isDebuggable(), readDebugKnob(DEBUG_SEGMENT_FILE))
        maxMs = debugMaxOverride() ?: ListenerPolicy.DEFAULT_MAX_SESSION_MS
        startedAt = now
        stopping = false
        ListenerState.set(ListenerState.Status.Starting(now))

        scope.launch {
            val row = withContext(Dispatchers.IO) { repo.create(title, now) }
            sessionId = row.id
            sessionDir = row.dirName
            if (stopping) {
                // Stop arrived while the row was being created.
                withContext(Dispatchers.IO) {
                    repo.finish(row.id, System.currentTimeMillis(), ListenerStopReason.USER)
                }
                endStartedButCancelled()
                return@launch
            }
            segmentIndex = 0
            recorder = ListenerRecorder(applicationContext) { msg ->
                handler.post { onRecorderError(msg) }
            }
            if (!startNextSegment()) {
                withContext(Dispatchers.IO) {
                    repo.finish(sessionId, System.currentTimeMillis(), ListenerStopReason.RECORDER_ERROR)
                }
                failStart(getString(R.string.listener_err_recorder))
                return@launch
            }
            handler.postDelayed(rollTask, segmentMs)
            handler.postDelayed(watchdog, WATCHDOG_MS)
            refreshTile()
        }
    }

    private fun startNextSegment(): Boolean {
        val rec = recorder ?: return false
        segmentIndex++
        val file = File(repo.storage.sessionDir(sessionDir), ListenerPolicy.segmentFileName(segmentIndex))
        return try {
            rec.start(file)
            ListenerState.set(ListenerState.Status.Recording(sessionId, startedAt, segmentIndex))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun rollSegment() {
        if (stopping || !ListenerState.isRecording) return
        recorder?.stop()
        if (!startNextSegment()) {
            stopSession(ListenerStopReason.RECORDER_ERROR)
            return
        }
        val id = sessionId
        scope.launch(Dispatchers.IO) { repo.updateProgress(id, System.currentTimeMillis()) }
        handler.postDelayed(rollTask, segmentMs)
    }

    private fun checkLimits() {
        if (stopping || !ListenerState.isRecording) return
        val elapsed = System.currentTimeMillis() - startedAt
        when {
            ListenerPolicy.shouldStopForDuration(elapsed, maxMs) ->
                stopSession(ListenerStopReason.MAX_DURATION)
            ListenerPolicy.shouldStopForStorage(repo.storage.freeBytes()) ->
                stopSession(ListenerStopReason.LOW_STORAGE)
        }
    }

    private fun onRecorderError(msg: String) {
        android.util.Log.w(TAG, msg)
        if (stopping) return
        stopSession(ListenerStopReason.RECORDER_ERROR)
    }

    private fun stopSession(reason: ListenerStopReason) {
        if (stopping) return
        if (ListenerState.status.value is ListenerState.Status.Starting) {
            stopping = true // the start coroutine sees this and closes the row
            return
        }
        if (!ListenerState.isRecording || sessionId <= 0) {
            stopSelf()
            return
        }
        stopping = true
        handler.removeCallbacks(rollTask)
        handler.removeCallbacks(watchdog)
        recorder?.stop()
        recorder = null
        val id = sessionId
        val endedAt = System.currentTimeMillis()
        ListenerState.set(ListenerState.Status.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        refreshTile()
        when (reason) {
            ListenerStopReason.MAX_DURATION ->
                ListenerNotifications.notice(this, getString(R.string.listener_notice_max_duration, maxMs / 60_000))
            ListenerStopReason.LOW_STORAGE ->
                ListenerNotifications.notice(this, getString(R.string.listener_notice_low_storage))
            ListenerStopReason.RECORDER_ERROR ->
                ListenerNotifications.notice(this, getString(R.string.listener_notice_recorder_error))
            else -> Unit
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                repo.finish(id, endedAt, reason)
                // v2: transcribe automatically when a speech model is installed.
                if (SttModelStore(applicationContext).isInstalled) {
                    try {
                        TranscriptionScheduler.enqueue(applicationContext, id)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "auto-transcribe not queued", e)
                    }
                }
            }
            sessionId = -1
            stopSelf()
        }
    }

    private fun endStartedButCancelled() {
        sessionId = -1
        ListenerState.set(ListenerState.Status.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        refreshTile()
        stopSelf()
    }

    private fun failStart(message: String) {
        ListenerState.reportError(message)
        ListenerState.set(ListenerState.Status.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        refreshTile()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (ListenerState.isRecording && !stopping) {
            // Destroyed without our Stop (rare: system/user stopped the FGS). Close the
            // chunk and record it as interrupted; do not restart.
            recorder?.stop()
            recorder = null
            val id = sessionId
            ListenerState.set(ListenerState.Status.Idle)
            if (id > 0) {
                val appRepo = repo
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    appRepo.finish(id, System.currentTimeMillis(), ListenerStopReason.PROCESS_DIED)
                }
            }
            refreshTile()
        } else if (ListenerState.isBusy) {
            ListenerState.set(ListenerState.Status.Idle)
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    this, ComponentName(this, ListenerTileService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Debug builds only; written via `adb shell run-as <pkg>` for evidence runs. */
    private fun readDebugKnob(name: String): String? {
        if (!isDebuggable()) return null
        val f = File(filesDir, name)
        return if (f.isFile) f.readText() else null
    }

    private fun debugMaxOverride(): Long? {
        val v = readDebugKnob(DEBUG_MAX_FILE)?.trim()?.toLongOrNull() ?: return null
        return if (v >= ListenerPolicy.MIN_DEBUG_SEGMENT_MS) v else null
    }

    companion object {
        const val ACTION_START = "com.example.moneymanager.listener.START"
        const val ACTION_STOP = "com.example.moneymanager.listener.STOP"
        /** Activity actions (tile / notification → app). */
        const val ACTION_OPEN_LISTENER = "com.example.moneymanager.action.OPEN_LISTENER"
        const val ACTION_START_FROM_TILE = "com.example.moneymanager.action.LISTENER_START"
        const val EXTRA_TITLE = "title"

        const val DEBUG_SEGMENT_FILE = "listener_debug_segment_ms"
        const val DEBUG_MAX_FILE = "listener_debug_max_ms"
        private const val WATCHDOG_MS = 15_000L
        private const val TAG = "ListenerService"

        fun start(context: Context, title: String?) {
            val i = Intent(context, ListenerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ListenerService::class.java).setAction(ACTION_STOP))
        }
    }
}
