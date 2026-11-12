package com.example.moneymanager.listener

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.moneymanager.MainActivity
import com.example.moneymanager.R

/**
 * Quick Settings toggle for the Listener.
 *
 * Stop: sent straight to the running service (we already hold a foreground
 * service, so this is not a background start).
 *
 * Start: always goes through the app. Since Android 11 a foreground service
 * started from the background cannot use the microphone ("while-in-use"
 * restriction), and on Android 14 a microphone-type FGS needs the app to be
 * eligible to use the mic at start time. So the tile opens MainActivity with
 * ACTION_START_FROM_TILE (startActivityAndCollapse with a PendingIntent on
 * API 34+, an Intent below), and the visible Listener screen runs the same
 * consent → permission → free-space → start flow as the on-screen button.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ListenerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (ListenerState.isBusy) {
            ListenerService.stop(this)
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                updateTile()
            }
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ListenerService.ACTION_START_FROM_TILE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val launch = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, 3, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
            } else {
                launchLegacy(intent)
            }
        }
        if (isLocked) unlockAndRun(launch) else launch()
    }

    /** The PendingIntent overload only exists on API 34+; below that the Intent one is the API. */
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val on = ListenerState.isBusy
        tile.label = getString(R.string.listener_title)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.listener_tile_on else R.string.listener_tile_off)
        }
        tile.updateTile()
    }
}
