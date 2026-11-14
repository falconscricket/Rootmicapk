package com.example.alpha.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.alpha.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Safely restores ALPHA Monitor readiness after device reboot without violating
 * Android 14+ background foreground-service start restrictions.
 */
class AlphaBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsManager(context).getGlobalSettings()
                if (settings.monitoringEnabled) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        nm.createNotificationChannel(
                            NotificationChannel(
                                context.getString(R.string.notification_channel_id),
                                context.getString(R.string.notification_channel_name),
                                NotificationManager.IMPORTANCE_LOW
                            )
                        )
                    }
                    val openIntent = PendingIntent.getActivity(
                        context,
                        10,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    val note = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setContentTitle("ALPHA Mic Monitor Ready")
                        .setContentText("Saved apps restored. Tap to resume automatic popup detection.")
                        .setAutoCancel(true)
                        .setContentIntent(openIntent)
                        .build()
                    nm.notify(3231, note)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
