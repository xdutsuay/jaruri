package com.example.moneymanager.quickadd

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.moneymanager.R

/**
 * Quick Settings tile → the quick expense sheet (offline). On the lock screen it asks the
 * user to unlock first: the sheet is not shown over the keyguard.
 */
@RequiresApi(Build.VERSION_CODES.N)
class QuickAddTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.money_tile_quick_add)
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { open() } else open()
    }

    // Android 14 removed the Intent overload for apps targeting 34; older releases only have it.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun open() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(QuickExpenseActivity.intent(this))
        }
    }

    companion object {
        fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx,
            0,
            QuickExpenseActivity.intent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
