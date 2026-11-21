package com.pinned.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuickPinTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            val count = Store.load(this@QuickPinTileService).size
            state = if (count > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Pinned"
            if (Build.VERSION.SDK_INT >= 29) {
                subtitle = when (count) {
                    0 -> "Add pin"
                    1 -> "1 active"
                    else -> "$count active"
                }
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_COMPOSER, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this,
                9001,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
