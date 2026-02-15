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
        private const val CACHE_DURATION = 60000L // 1 minute (reduced for more frequent updates)
    }
    
    /**
     * Invalidate all caches to force fresh data retrieval
     * Call this when app resumes to ensure up-to-date usage data
     */
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
        // Set to start of current day
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Try using queryEvents for the most accurate real-time usage tracking
        // This gives us actual foreground/background events, allowing precise calculation
        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            var totalUsage = 0L
            var lastForegroundTime: Long? = null

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            // App moved to foreground - record the start time
                            lastForegroundTime = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            // App moved to background - calculate duration
                            if (lastForegroundTime != null) {
                                // Only count time that falls within today's range
                                val sessionStart = maxOf(lastForegroundTime, startTime)
                                val sessionEnd = minOf(event.timeStamp, endTime)
                                if (sessionEnd > sessionStart) {
                                    totalUsage += (sessionEnd - sessionStart)
                                }
                                lastForegroundTime = null
                            }
                        }
                    }
                }
            }
            
            // If app is currently in foreground (lastForegroundTime is set), count time until now
            if (lastForegroundTime != null) {
                val sessionStart = maxOf(lastForegroundTime, startTime)
                if (endTime > sessionStart) {
                    totalUsage += (endTime - sessionStart)
                }
            }
            
            // Cache the result
            usageCache[packageName] = Pair(totalUsage, currentTime)
            return totalUsage
        } catch (_: Exception) {
            // Fallback to INTERVAL_DAILY if queryEvents fails
            // Query from a few days back to ensure we get today's daily aggregate
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val queryStartTime = calendar.timeInMillis
            
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                queryStartTime,
                endTime
            )

            // Find today's usage entry for this package
            val todayUsage = usageStatsList
                .filter { 
                    it.packageName == packageName && 
                    it.totalTimeInForeground > 0
                }
                .firstOrNull { stats ->
                    // Check if this entry is for today by verifying lastTimeUsed is within today's range
                    stats.lastTimeUsed in startTime..endTime
                }
                ?.totalTimeInForeground ?: 0L
            
            // Cache the result
            usageCache[packageName] = Pair(todayUsage, currentTime)
            return todayUsage
        }
    }

    fun getTotalUsageForPeriod(startTime: Long, endTime: Long): Long {
        if (!hasUsageStatsPermission()) return 0L
        
        return try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val packageForegroundTime = mutableMapOf<String, Long>()
            var totalUsage = 0L

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                
                // Filter out system apps and launcher
                if (event.packageName.startsWith("com.android") ||
                    event.packageName.startsWith("android") ||
                    event.packageName == "com.guruswarupa.launch") {
                    continue
                }
                
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        packageForegroundTime[event.packageName] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val foregroundStart = packageForegroundTime.remove(event.packageName)
                        if (foregroundStart != null) {
                            val sessionStart = maxOf(foregroundStart, startTime)
                            val sessionEnd = minOf(event.timeStamp, endTime)
                            if (sessionEnd > sessionStart) {
                                totalUsage += (sessionEnd - sessionStart)
                            }
                        }
                    }
                }
            }
            
            // Count any apps still in foreground at the end time
            for (foregroundStart in packageForegroundTime.values) {
                val sessionStart = maxOf(foregroundStart, startTime)
                if (endTime > sessionStart) {
                    totalUsage += (endTime - sessionStart)
                }
            }
            
            totalUsage
        } catch (_: Exception) {
            // Fallback to queryUsageStats
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            usageStatsList
                .filter { stats ->
                    stats.totalTimeInForeground > 0 &&
                            stats.lastTimeUsed in startTime..endTime &&
                            !stats.packageName.startsWith("com.android") &&
                            !stats.packageName.startsWith("android") &&
                            stats.packageName != "com.guruswarupa.launch"
                }
                .sumOf { it.totalTimeInForeground }
        }
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

            // Set to end of day (or current time for today)
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

            // Use queryEvents for accurate daily usage calculation
            val totalDayUsage = try {
                val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                val packageForegroundTime = mutableMapOf<String, Long>()
                var dayTotalUsage = 0L

                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    
                    // Filter out system apps and launcher
                    if (event.packageName.startsWith("com.android") ||
                        event.packageName.startsWith("android") ||
                        event.packageName == "com.guruswarupa.launch") {
                        continue
                    }
                    
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            packageForegroundTime[event.packageName] = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            val foregroundStart = packageForegroundTime.remove(event.packageName)
                            if (foregroundStart != null) {
                                val sessionStart = maxOf(foregroundStart, startTime)
                                val sessionEnd = minOf(event.timeStamp, endTime)
                                if (sessionEnd > sessionStart) {
                                    dayTotalUsage += (sessionEnd - sessionStart)
                                }
                            }
                        }
                    }
                }
                
                // Count any apps still in foreground (for today)
                if (isToday) {
                    val now = System.currentTimeMillis()
                    for (foregroundStart in packageForegroundTime.values) {
                        val sessionStart = maxOf(foregroundStart, startTime)
                        if (now > sessionStart) {
                            dayTotalUsage += (now - sessionStart)
                        }
                    }
                }
                
                dayTotalUsage
            } catch (_: Exception) {
                // Fallback to INTERVAL_DAILY
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                usageStatsList
                    .filter { stats ->
                        stats.totalTimeInForeground > 0 &&
                                stats.lastTimeUsed in startTime..endTime &&
                                !stats.packageName.startsWith("com.android") &&
                                !stats.packageName.startsWith("android") &&
                                stats.packageName != "com.guruswarupa.launch"
                    }
                    .sumOf { it.totalTimeInForeground }
            }

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

            // Set to end of day (or current time for today)
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

            // Use queryEvents for accurate app-specific daily usage
            val appUsageMap = mutableMapOf<String, Long>()
            try {
                val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
                val packageForegroundTime = mutableMapOf<String, Long>() // packageName to last foreground timestamp

                val event = UsageEvents.Event()
                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event)
                    
                    // Filter out system apps and launcher
                    if (event.packageName.startsWith("com.android") ||
                        event.packageName.startsWith("android") ||
                        event.packageName == "com.guruswarupa.launch") {
                        continue
                    }
                    
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            packageForegroundTime[event.packageName] = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            val foregroundStart = packageForegroundTime.remove(event.packageName)
                            if (foregroundStart != null) {
                                val sessionStart = maxOf(foregroundStart, startTime)
                                val sessionEnd = minOf(event.timeStamp, endTime)
                                if (sessionEnd > sessionStart) {
                                    val duration = sessionEnd - sessionStart
                                    val appName = try {
                                        context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(event.packageName, 0)
                                        ).toString()
                                    } catch (_: Exception) {
                                        event.packageName.substringAfterLast(".")
                                    }
                                    appUsageMap[appName] = (appUsageMap[appName] ?: 0) + duration
                                }
                            }
                        }
                    }
                }
                
                // Count any apps still in foreground (for today)
                if (isToday) {
                    val now = System.currentTimeMillis()
                    for ((pName, foregroundStart) in packageForegroundTime) {
                        val sessionStart = maxOf(foregroundStart, startTime)
                        if (now > sessionStart) {
                            val duration = now - sessionStart
                            val appName = try {
                                context.packageManager.getApplicationLabel(
                                    context.packageManager.getApplicationInfo(pName, 0)
                                ).toString()
                            } catch (_: Exception) {
                                pName.substringAfterLast(".")
                            }
                            appUsageMap[appName] = (appUsageMap[appName] ?: 0) + duration
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to INTERVAL_DAILY
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                usageStatsList
                    .filter { stats ->
                        stats.totalTimeInForeground > 0 &&
                                stats.lastTimeUsed in startTime..endTime &&
                                !stats.packageName.startsWith("com.android") &&
                                !stats.packageName.startsWith("android") &&
                                stats.packageName != "com.guruswarupa.launch"
                    }
                    .forEach { stats ->
                        val appName = try {
                            context.packageManager.getApplicationLabel(
                                context.packageManager.getApplicationInfo(stats.packageName, 0)
                            ).toString()
                        } catch (_: Exception) {
                            stats.packageName.substringAfterLast(".")
                        }
                        appUsageMap[appName] = (appUsageMap[appName] ?: 0) + stats.totalTimeInForeground
                    }
            }

            // Sort by usage and return top 15 apps
            val sortedApps = appUsageMap.toList()
                .sortedByDescending { it.second }
                .take(15)
                .toMap()
            
            // Calculate "Others" usage
            val othersUsage = appUsageMap.values.sum() - sortedApps.values.sum()
            val finalApps = sortedApps.toMutableMap()
            if (othersUsage > 0) {
                finalApps["Others"] = othersUsage
            }

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

            weeklyData.add(dayFormat to finalApps)
        }

        return weeklyData
    }
}
