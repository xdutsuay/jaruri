package com.example.moneymanager.listener.transcribe

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Cancel" on the transcription notification. Not exported; reachable only through our PendingIntent. */
class TranscriptionCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL) return
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1)
        if (sessionId <= 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TranscriptionScheduler.cancel(app, sessionId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.example.moneymanager.listener.CANCEL_TRANSCRIPTION"
        const val EXTRA_SESSION_ID = "sessionId"

        fun intent(context: Context, sessionId: Long): Intent =
            Intent(context, TranscriptionCancelReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_SESSION_ID, sessionId)

        fun pendingIntent(context: Context, sessionId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context, (sessionId % 100_000).toInt(), intent(context, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
