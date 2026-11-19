package io.github.sachin102492.floatnote.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.sachin102492.floatnote.R
import io.github.sachin102492.floatnote.ui.MainActivity

/** Keeps the floating bubble on screen. Runs only while the user has the bubble enabled. */
class FloatingService : Service() {

    private var overlay: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Settings.canDrawOverlays(this)) {
            // Show the overlay before going foreground: on Android 15+ a visible overlay
            // window is what allows the service to start from the background (e.g. the tile).
            overlay = OverlayController(this) { stopSelf() }.also { it.show() }
        }
        goForeground()
        if (overlay == null) {
            stopSelf()
            return
        }
        running = true
        refreshTile(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EXPAND -> overlay?.expand()
            ACTION_REFRESH_STYLE -> overlay?.applyStyle()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }

    override fun onDestroy() {
        overlay?.destroy()
        overlay = null
        running = false
        refreshTile(this)
        super.onDestroy()
    }

    private fun goForeground() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not enter the foreground", e)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            },
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val expand = PendingIntent.getService(
            this, 1, Intent(this, FloatingService::class.java).setAction(ACTION_EXPAND), flags,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, FloatingService::class.java).setAction(ACTION_STOP), flags,
        )
        val openApp = PendingIntent.getActivity(
            this, 3, Intent(this, MainActivity::class.java), flags,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edit)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(expand)
            .addAction(0, getString(R.string.notification_open_app), openApp)
            .addAction(0, getString(R.string.notification_hide), stop)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_EXPAND = "io.github.sachin102492.floatnote.action.EXPAND"
        const val ACTION_STOP = "io.github.sachin102492.floatnote.action.STOP"
        private const val ACTION_REFRESH_STYLE = "io.github.sachin102492.floatnote.action.REFRESH_STYLE"
        private const val CHANNEL_ID = "bubble"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "FloatingService"

        @Volatile
        var running = false
            private set

        /** Shows the bubble, optionally opening the notepad right away. */
        fun start(context: Context, expand: Boolean = false) {
            val intent = Intent(context, FloatingService::class.java)
            if (expand) intent.action = ACTION_EXPAND
            ContextCompat.startForegroundService(context, intent)
        }

        /** Re-applies the bubble colour if the bubble is on screen. */
        fun refreshStyle(context: Context) {
            if (!running) return
            context.startService(
                Intent(context, FloatingService::class.java).setAction(ACTION_REFRESH_STYLE),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingService::class.java))
        }

        private fun refreshTile(context: Context) {
            try {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, BubbleTileService::class.java),
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not refresh the tile", e)
            }
        }
    }
}
