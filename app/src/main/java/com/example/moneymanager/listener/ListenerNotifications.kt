package com.example.moneymanager.listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.moneymanager.MainActivity
import com.example.moneymanager.R

object ListenerNotifications {
    const val CHANNEL_ONGOING = "listener_ongoing"
    const val CHANNEL_NOTICES = "listener_notices"
    const val ID_ONGOING = 4101
    private const val ID_NOTICE = 4102

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ONGOING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING,
                    context.getString(R.string.listener_channel_ongoing),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_NOTICES) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_NOTICES,
                    context.getString(R.string.listener_channel_notices),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    fun openListenerIntent(context: Context): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            action = ListenerService.ACTION_OPEN_LISTENER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ongoing(context: Context, title: String, startedAt: Long): android.app.Notification {
        ensureChannels(context)
        val stop = PendingIntent.getService(
            context, 2,
            Intent(context, ListenerService::class.java).setAction(ListenerService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(context.getString(R.string.listener_notif_recording))
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openListenerIntent(context))
            .addAction(R.drawable.ic_mic, context.getString(R.string.listener_stop), stop)
            .build()
    }

    /** Posted when a session ends without the user pressing Stop. Best effort. */
    fun notice(context: Context, text: String) {
        ensureChannels(context)
        try {
            val n = NotificationCompat.Builder(context, CHANNEL_NOTICES)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(context.getString(R.string.listener_notif_stopped))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openListenerIntent(context))
                .build()
            NotificationManagerCompat.from(context).notify(ID_NOTICE, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied on 33+: the session list still shows why it stopped.
        }
    }
}
