
package com.guruswarupa.launch

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AppUsageStatsManager(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    fun getAppUsageTime(packageName: String): Long {
        if (!hasUsageStatsPermission()) return 0L

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1) // Get usage for last 24 hours only
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Use INTERVAL_BEST to get more accurate granular data
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startTime,
            endTime
        )

        // Filter and sum only foreground time for the specific package
        return usageStatsList
            .filter { it.packageName == packageName && it.totalTimeInForeground > 0 }
            .sumOf { it.totalTimeInForeground }
    }

    fun formatUsageTime(timeInMillis: Long): String {
        if (timeInMillis <= 0) return ""

        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(timeInMillis)

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    fun getWeeklyUsageData(): List<Pair<String, Long>> {
        if (!hasUsageStatsPermission()) return emptyList()

        val weeklyData = mutableListOf<Pair<String, Long>>()
        val calendar = Calendar.getInstance()

        // Get data for the last 7 days
        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)

            // Set to start of day
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            // Set to end of day
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis

            // Use INTERVAL_BEST for more accurate granular data
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )

            // Filter for only meaningful foreground usage
            val totalDayUsage = usageStatsList
                .filter { stats ->
                    stats.totalTimeInForeground > 0 &&
                            stats.lastTimeUsed >= startTime &&
                            stats.lastTimeUsed <= endTime &&
                            // Filter out system apps and this launcher
                            !stats.packageName.startsWith("com.android") &&
                            !stats.packageName.startsWith("android") &&
                            stats.packageName != "com.guruswarupa.launch"
                }
                .sumOf { it.totalTimeInForeground }

            // Format day label
            val dayFormat = when (i) {
                0 -> "Today"
                1 -> "Yesterday"
                else -> {
                    calendar.time = Date()
                    calendar.add(Calendar.DAY_OF_YEAR, -i)
                    SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time)
                }
            }

            weeklyData.add(dayFormat to totalDayUsage)
        }

        return weeklyData
    }
}
