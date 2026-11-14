package com.example.moneymanager.tunnel

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.moneymanager.R
import com.example.moneymanager.tunnel.ui.TunnelActivity
import java.text.DateFormat
import java.util.Date

object TunnelNotifications {
    private const val CHANNEL = "tunnel"
    private const val ID = 4300

    fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 40, Intent(context, TunnelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, TunnelActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is optional; areNotificationsEnabled() guards it.
    fun update(context: Context, pausedUntil: Long) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(context)
        val paused = pausedUntil > System.currentTimeMillis()
        val text = if (paused) {
            context.getString(R.string.tunnel_notif_paused, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pausedUntil)))
        } else context.getString(R.string.tunnel_notif_on)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tunnel)
            .setContentTitle(context.getString(R.string.tunnel_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent(context))
        if (paused) b.addAction(R.drawable.ic_tunnel, context.getString(R.string.tunnel_resume), action(context, TunnelVpnService.ACTION_RESUME, 41))
        else b.addAction(R.drawable.ic_tunnel, context.getString(R.string.tunnel_pause_10), action(context, TunnelVpnService.ACTION_PAUSE, 42))
        b.addAction(R.drawable.ic_tunnel, context.getString(R.string.tunnel_stop), action(context, TunnelVpnService.ACTION_STOP, 43))
        nm.notify(ID, b.build())
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Flip the notification back to "On" when a pause ends (filtering itself resumes on its own). */
    fun scheduleResumeRefresh(context: Context, until: Long) {
        val app = context.applicationContext
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (TunnelState.isOn) update(app, 0) }, (until - System.currentTimeMillis()).coerceAtLeast(0) + 500)
    }

    fun cancel(context: Context) {
        handler.removeCallbacksAndMessages(null)
        NotificationManagerCompat.from(context).cancel(ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.tunnel_title), NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}

/** Notification actions; runs in our process, so it talks to the live service directly. */
class TunnelActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TunnelVpnService.ACTION_PAUSE -> TunnelVpnService.pause(context, 10)
            TunnelVpnService.ACTION_RESUME -> TunnelVpnService.resume(context)
            TunnelVpnService.ACTION_STOP -> TunnelVpnService.stop(context)
        }
    }
}
