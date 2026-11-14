package com.example.moneymanager.tunnel

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.moneymanager.R
import com.example.moneymanager.tunnel.ui.TunnelActivity

/**
 * Quick Settings toggle. Stop is direct. Start is direct only when the VPN is
 * already prepared and consent was given; otherwise (first run, or the system
 * refuses a background start) it opens the Tunnel screen, which asks.
 */
@RequiresApi(Build.VERSION_CODES.N)
class TunnelTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (TunnelState.isOn) {
            TunnelVpnService.stop(this)
            render(false)
            return
        }
        val ready = VpnService.prepare(this) == null && TunnelPrefs(this).consentAccepted
        if (ready) {
            try {
                TunnelVpnService.start(this)
                render(true)
                return
            } catch (_: IllegalStateException) {
                // Background start refused: fall through and open the app.
            }
        }
        val intent = Intent(this, TunnelActivity::class.java).apply {
            action = TunnelActivity.ACTION_START
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val launch = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 44, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
            } else {
                launchLegacy(intent)
            }
        }
        if (isLocked) unlockAndRun(launch) else launch()
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }

    private fun render(on: Boolean = TunnelState.isOn) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tunnel_title)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.tunnel_tile_on else R.string.tunnel_tile_off)
        }
        tile.updateTile()
    }
}
