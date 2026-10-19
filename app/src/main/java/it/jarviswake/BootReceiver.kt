package it.jarviswake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "boot", "Avvio JarvisWake", NotificationManager.IMPORTANCE_HIGH
        ))
        val open = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(10, Notification.Builder(context, "boot")
            .setSmallIcon(R.drawable.ic_jarvis)
            .setContentTitle("JarvisWake pronto dopo il riavvio")
            .setContentText("L’assistente Jarvis ascolta se è selezionato; tocca per aprire l’app.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}
