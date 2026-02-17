package com.guruswarupa.launch

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

class AppUsageStatsManager(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    // Cache for expensive operations
    private var dailyUsageCache: Pair<String, Long>? = null // date to total usage
    private var weeklyDataCache: Pair<Long, List<Pair<String, Long>>>? = null // timestamp to data
    private val usageCache = mutableMapOf<String, Pair<Long, Long>>() // packageName to (usage, timestamp)
    
    companion object {
        private const val CACHE_DURATION = 30000L // 30 seconds
    }
    
    fun invalidateCache() {
        usageCache.clear()
        dailyUsageCache = null
        weeklyDataCache = null
    }
    
    fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageStatsPermission(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    /**
     * Calculates foreground usage map using UsageEvents for higher accuracy.
     * This avoids counting background services as foreground time.
     */
    private fun getForegroundUsageMap(startTime: Long, endTime: Long): Map<String, Long> {
        val usageMap = mutableMapOf<String, Long>()
        if (!hasUsageStatsPermission()) return usageMap

        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            val appStartTimes = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        appStartTimes[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = appStartTimes.remove(pkg)
                        if (start != null) {
                            val duration = event.timeStamp - start
                            if (duration > 0) {
                                usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                            }
                        }
                    }
                }
            }
            
            // Handle app currently in foreground if we're querying up to "now"
            val now = System.currentTimeMillis()
            if (endTime >= now - 10000) {
                for ((pkg, start) in appStartTimes) {
                    val duration = now - start
                    if (duration > 0) {
                        usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to aggregate stats if events query fails
            val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            return statsMap.mapValues { it.value.totalTimeInForeground }
        }
        
        return usageMap
    }

    /**
     * Filters out system apps and the launcher itself to show only user-relevant apps.
     */
    private fun isReportableApp(packageName: String): Boolean {
        if (packageName == "com.guruswarupa.launch" || 
            packageName == "android" || 
            packageName.startsWith("com.android.systemui")) {
            return false
        }
        
        // Only count apps that have a launcher intent (actual apps the user interacts with)
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }

    fun getAppUsageTime(packageName: String): Long {
        if (!hasUsageStatsPermission()) return 0L

        // Check cache first
        val currentTime = System.currentTimeMillis()
        usageCache[packageName]?.let { (cachedUsage, timestamp) ->
            if (currentTime - timestamp < CACHE_DURATION) {
                return cachedUsage
            }
        }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageMap = getForegroundUsageMap(startTime, endTime)
        val usage = usageMap[packageName] ?: 0L
        
        usageCache[packageName] = Pair(usage, currentTime)
        return usage
    }

    fun getTotalUsageForPeriod(startTime: Long, endTime: Long): Long {
        if (!hasUsageStatsPermission()) return 0L
        
        val usageMap = getForegroundUsageMap(startTime, endTime)
        return usageMap
            .filter { (pkg, _) -> isReportableApp(pkg) }
            .values
            .sum()
    }

    fun formatUsageTime(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0m"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    fun getWeeklyUsageData(): List<Pair<String, Long>> {
        if (!hasUsageStatsPermission()) return emptyList()

        val currentTime = System.currentTimeMillis()
        weeklyDataCache?.let { (timestamp, data) ->
            if (currentTime - timestamp < CACHE_DURATION) {
                return data
            }
        }

        val weeklyData = mutableListOf<Pair<String, Long>>()
        val calendar = Calendar.getInstance()

        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            val isToday = (i == 0)
            val endTime = if (isToday) {
                System.currentTimeMillis()
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                calendar.timeInMillis
            }

            val usageMap = getForegroundUsageMap(startTime, endTime)
            val dayTotalUsage = usageMap
                .filter { (pkg, _) -> isReportableApp(pkg) }
                .values
                .sum()

            val dayFormat = when (i) {
                0 -> "Today"
                1 -> "Yesterday"
                else -> {
                    val labelCalendar = Calendar.getInstance()
                    labelCalendar.add(Calendar.DAY_OF_YEAR, -i)
                    SimpleDateFormat("EEE", Locale.getDefault()).format(labelCalendar.time)
                }
            }

            weeklyData.add(dayFormat to dayTotalUsage)
        }

        weeklyDataCache = Pair(currentTime, weeklyData)
        return weeklyData
    }

    fun getWeeklyAppUsageData(): List<Pair<String, Map<String, Long>>> {
        if (!hasUsageStatsPermission()) return emptyList()

        val weeklyData = mutableListOf<Pair<String, Map<String, Long>>>()
        val calendar = Calendar.getInstance()

        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            val isToday = (i == 0)
            val endTime = if (isToday) {
                System.currentTimeMillis()
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                calendar.timeInMillis
            }

            val usageMap = getForegroundUsageMap(startTime, endTime)
            val appUsageMap = mutableMapOf<String, Long>()
            
            usageMap
                .filter { (pkg, usage) -> usage > 0 && isReportableApp(pkg) }
                .forEach { (pkg, usage) ->
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0)
                        ).toString()
                    } catch (_: Exception) {
                        pkg.substringAfterLast(".")
                    }
                    appUsageMap[appName] = (appUsageMap[appName] ?: 0) + usage
                }

            val sortedApps = appUsageMap.toList()
                .sortedByDescending { it.second }
                .take(15)
                .toMap()
            
            val othersUsage = appUsageMap.values.sum() - sortedApps.values.sum()
            val finalApps = sortedApps.toMutableMap()
            if (othersUsage > 0) {
                finalApps["Others"] = othersUsage
            }

            val dayFormat = when (i) {
                0 -> "Today"
                1 -> "Yesterday"
                else -> {
                    val labelCalendar = Calendar.getInstance()
                    labelCalendar.add(Calendar.DAY_OF_YEAR, -i)
                    SimpleDateFormat("EEE", Locale.getDefault()).format(labelCalendar.time)
                }
            }

            weeklyData.add(dayFormat to finalApps)
        }

        return weeklyData
    }
}
