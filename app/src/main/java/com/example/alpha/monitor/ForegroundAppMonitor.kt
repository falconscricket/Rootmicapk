package com.example.alpha.monitor

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Monitors foreground application changes via Android's UsageStatsManager API.
 */
class ForegroundAppMonitor(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private var lastKnownForegroundPackage: String? = null

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Queries recent UsageEvents to determine the current foreground package name.
     * Returns null only if Usage Access is not granted or no events exist yet.
     */
    fun getCurrentForegroundPackage(lookbackWindowMs: Long = 15_000L): String? {
        val usm = usageStatsManager ?: return null
        if (!hasUsageStatsPermission()) return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - lookbackWindowMs
        val events = runCatching { usm.queryEvents(startTime, endTime) }.getOrNull()
            ?: return lastKnownForegroundPackage

        val event = UsageEvents.Event()
        var latestPackage = lastKnownForegroundPackage
        var latestTimestamp = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isForegroundEvent && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestPackage = event.packageName
            }
        }

        if (latestPackage != null) {
            lastKnownForegroundPackage = latestPackage
        }
        return latestPackage
    }
}
