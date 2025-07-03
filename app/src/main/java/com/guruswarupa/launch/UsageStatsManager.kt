package com.guruswarupa.launch

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AppUsageStatsManager(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // Cache for expensive operations
    private var dailyUsageCache: Pair<String, Long>? = null // date to total usage
    private var weeklyDataCache: Pair<Long, List<Pair<String, Long>>>? = null // timestamp to data
    private val CACHE_DURATION = 300000L // 5 minutes

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
        // Set to start of current day
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
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

    fun getWeeklyUsageData(): List<Pair<String, Long>> {
        if (!hasUsageStatsPermission()) return emptyList()

        // Check cache first
        val currentTime = System.currentTimeMillis()
        weeklyDataCache?.let { (timestamp, data) ->
            if (currentTime - timestamp < CACHE_DURATION) {
                return data
            }
        }

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

        // Cache the result
        weeklyDataCache = Pair(currentTime, weeklyData)
        return weeklyData
    }

    fun getWeeklyAppUsageData(): List<Pair<String, Map<String, Long>>> {
        if (!hasUsageStatsPermission()) return emptyList()

        val weeklyData = mutableListOf<Pair<String, Map<String, Long>>>()
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

            // Get app-specific usage for this day
            val appUsageMap = mutableMapOf<String, Long>()
            usageStatsList
                .filter { stats ->
                    stats.totalTimeInForeground > 0 &&
                            stats.lastTimeUsed >= startTime &&
                            stats.lastTimeUsed <= endTime &&
                            // Filter out system apps and this launcher
                            !stats.packageName.startsWith("com.android") &&
                            !stats.packageName.startsWith("android") &&
                            stats.packageName != "com.guruswarupa.launch"
                }
                .forEach { stats ->
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(stats.packageName, 0)
                        ).toString()
                    } catch (e: Exception) {
                        stats.packageName.substringAfterLast(".")
                    }
                    appUsageMap[appName] = (appUsageMap[appName] ?: 0) + stats.totalTimeInForeground
                }

            // Sort by usage and take top 5 apps for each day
            val topApps = appUsageMap.toList()
                .sortedByDescending { it.second }
                .take(5)
                .toMap()

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

            weeklyData.add(dayFormat to topApps)
        }

        return weeklyData
    }

    fun formatUsageTime(timeInMillis: Long): String {
        if (timeInMillis == 0L) return ""

        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(timeInMillis)
        return when {
            totalMinutes < 1 -> "${TimeUnit.MILLISECONDS.toSeconds(timeInMillis)}s"
            totalMinutes < 60 -> "${totalMinutes}m"
            else -> {
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
        }
    }
}