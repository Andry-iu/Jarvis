package it.jarviswake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper

/** User-enabled phone bridge. Owns neither the microphone nor a Voice session. */
class BridgeLinkService : Service() {
    companion object {
        private const val CHANNEL = "bridge_link"
        private const val NOTIFICATION = 12
        private const val STOP = "it.jarviswake.STOP_LINK"

        // Call from a visible Activity or a permitted system start (boot/package replacement).
        fun sync(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, BridgeLinkService::class.java)
            if (!BridgeSettings.enabled(app) || !BridgeSettings.configured(app)) {
                app.stopService(intent)
                return
            }
            runCatching { app.startForegroundService(intent) }.onFailure {
                BridgeLink.serviceUnavailable(app)
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val maintain = object : Runnable {
        override fun run() {
            if (!BridgeSettings.enabled(this@BridgeLinkService) || !BridgeSettings.configured(this@BridgeLinkService)) {
                stopSelf()
                return
            }
            // Idempotent while the worker is alive; recover a terminated worker without a UI owner.
            BridgeLink.owner(this@BridgeLinkService, "link_service", true)
            main.postDelayed(this, 10_000L)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            BridgeSettings.enable(this, false)
            BridgeLink.refresh(this)
        }
        if (!BridgeSettings.enabled(this) || !BridgeSettings.configured(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Collegamento ChatGPT", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 12, Intent(this, BridgeSetupActivity::class.java).putExtra("section", "pair"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 13, Intent(this, BridgeLinkService::class.java).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_jarvis)
                .setContentTitle("JarvisWake Link")
                .setContentText("Collegamento abilitato anche con la pagina chiusa.")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Disattiva collegamento", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION, notification)
            main.removeCallbacks(maintain)
            maintain.run()
            NativeRuntimeSnapshot.save(this, "-", "bridge_lifecycle", "event=service_started")
            return START_STICKY
        } catch (_: RuntimeException) {
            BridgeLink.serviceUnavailable(this)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(maintain)
        BridgeLink.owner(this, "link_service", false)
        NativeRuntimeSnapshot.save(this, "-", "bridge_lifecycle", "event=service_destroyed")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
