package io.github.sachin102492.floatnote.overlay

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.sachin102492.floatnote.ui.MainActivity

/** Quick Settings tile that toggles the floating bubble from anywhere. */
class BubbleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render(FloatingService.running)
    }

    override fun onClick() {
        super.onClick()
        if (FloatingService.running) {
            FloatingService.stop(this)
            render(false)
            return
        }
        if (Settings.canDrawOverlays(this)) {
            try {
                FloatingService.start(this)
                render(true)
                return
            } catch (e: IllegalStateException) {
                // Background start not allowed on this device; let the app start it instead.
            }
        }
        openApp()
    }

    private fun render(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_START_BUBBLE, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        } else {
            startActivityAndCollapseLegacy(intent)
        }
    }

    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseLegacy(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
