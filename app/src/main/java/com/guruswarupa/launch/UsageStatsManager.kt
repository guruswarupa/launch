package com.guruswarupa.launch

import android.app.AppOpsManager
import android.app.usage.UsageEvents
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
    private val usageCache = mutableMapOf<String, Pair<Long, Long>>() // packageName to (usage, timestamp)
    private val CACHE_DURATION = 60000L // 1 minute (reduced for more frequent updates)
    
    /**
     * Invalidate all caches to force fresh data retrieval
     * Call this when app resumes to ensure up-to-date usage data
     */
    fun invalidateCache() {
        usageCache.clear()
        dailyUsageCache = null
        weeklyDataCache = null
    }
    
    /**
     * Invalidate cache for a specific package
     */
    fun invalidatePackageCache(packageName: String) {
        usageCache.remove(packageName)
    }

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
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            // App moved to foreground - record the start time
                            lastForegroundTime = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
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
        } catch (e: Exception) {
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
                    stats.lastTimeUsed >= startTime && stats.lastTimeUsed <= endTime
                }
                ?.totalTimeInForeground ?: 0L
            
            // Cache the result
            usageCache[packageName] = Pair(todayUsage, currentTime)
            return todayUsage
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
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            packageForegroundTime[event.packageName] = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
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
                    val currentTime = System.currentTimeMillis()
                    for ((_, foregroundStart) in packageForegroundTime) {
                        val sessionStart = maxOf(foregroundStart, startTime)
                        if (currentTime > sessionStart) {
                            dayTotalUsage += (currentTime - sessionStart)
                        }
                    }
                }
                
                dayTotalUsage
            } catch (e: Exception) {
                // Fallback to INTERVAL_DAILY
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                usageStatsList
                    .filter { stats ->
                        stats.totalTimeInForeground > 0 &&
                                stats.lastTimeUsed >= startTime &&
                                stats.lastTimeUsed <= endTime &&
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
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            packageForegroundTime[event.packageName] = event.timeStamp
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
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
                                    } catch (e: Exception) {
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
                    val currentTime = System.currentTimeMillis()
                    for ((packageName, foregroundStart) in packageForegroundTime) {
                        val sessionStart = maxOf(foregroundStart, startTime)
                        if (currentTime > sessionStart) {
                            val duration = currentTime - sessionStart
                            val appName = try {
                                context.packageManager.getApplicationLabel(
                                    context.packageManager.getApplicationInfo(packageName, 0)
                                ).toString()
                            } catch (e: Exception) {
                                packageName.substringAfterLast(".")
                            }
                            appUsageMap[appName] = (appUsageMap[appName] ?: 0) + duration
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to INTERVAL_DAILY
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                
                usageStatsList
                    .filter { stats ->
                        stats.totalTimeInForeground > 0 &&
                                stats.lastTimeUsed >= startTime &&
                                stats.lastTimeUsed <= endTime &&
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
            }

            // Sort by usage and return all apps (or top 15 to avoid clutter)
            val sortedApps = appUsageMap.toList()
                .sortedByDescending { it.second }
                .take(15) // Show top 15 apps, group rest as "Others"
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

    fun getTotalUsageForPeriod(startTime: Long, endTime: Long): Long {
        if (!hasUsageStatsPermission()) return 0L

        // Use queryEvents for accurate calculation of total screen time
        try {
            val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
            val packageForegroundTime = mutableMapOf<String, Long>() // packageName to last foreground timestamp
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
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        // App moved to foreground - record the start time
                        packageForegroundTime[event.packageName] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        // App moved to background - calculate duration
                        val foregroundStart = packageForegroundTime.remove(event.packageName)
                        if (foregroundStart != null) {
                            // Only count time that falls within the query range
                            val sessionStart = maxOf(foregroundStart, startTime)
                            val sessionEnd = minOf(event.timeStamp, endTime)
                            if (sessionEnd > sessionStart) {
                                totalUsage += (sessionEnd - sessionStart)
                            }
                        }
                    }
                }
            }
            
            // Count any apps still in foreground
            val currentTime = System.currentTimeMillis()
            for ((packageName, foregroundStart) in packageForegroundTime) {
                val sessionStart = maxOf(foregroundStart, startTime)
                val sessionEnd = minOf(currentTime, endTime)
                if (sessionEnd > sessionStart) {
                    totalUsage += (sessionEnd - sessionStart)
                }
            }
            
            return totalUsage
        } catch (e: Exception) {
            // Fallback to INTERVAL_DAILY if queryEvents fails
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            return usageStatsList
                .filter { stats ->
                    stats.totalTimeInForeground > 0 &&
                            stats.lastTimeUsed >= startTime &&
                            stats.lastTimeUsed <= endTime &&
                            !stats.packageName.startsWith("com.android") &&
                            !stats.packageName.startsWith("android") &&
                            stats.packageName != "com.guruswarupa.launch"
                }
                .sumOf { it.totalTimeInForeground }
        }
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