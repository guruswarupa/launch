package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

class DailyUsageManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("daily_usage_prefs", Context.MODE_PRIVATE)
    private val appUsageStatsManager = AppUsageStatsManager(context)

    companion object {
        private const val PREF_DAILY_LIMIT_PREFIX = "daily_limit_"
        private const val PREF_DAILY_USAGE_PREFIX = "daily_usage_"
        private const val PREF_LAST_RESET_DATE = "last_reset_date"
        private const val PREF_TIMER_ENABLED_PREFIX = "timer_enabled_"
    }

    /**
     * Check if daily usage timer is enabled for an app
     */
    fun isTimerEnabled(packageName: String): Boolean {
        return prefs.getBoolean("${PREF_TIMER_ENABLED_PREFIX}$packageName", false)
    }

    /**
     * Enable or disable daily usage timer for an app
     */
    fun setTimerEnabled(packageName: String, enabled: Boolean) {
        prefs.edit { putBoolean("${PREF_TIMER_ENABLED_PREFIX}$packageName", enabled) }
    }

    /**
     * Get daily usage limit for an app in milliseconds
     */
    fun getDailyLimit(packageName: String): Long {
        return prefs.getLong("${PREF_DAILY_LIMIT_PREFIX}$packageName", 0L)
    }

    /**
     * Set daily usage limit for an app in milliseconds
     */
    fun setDailyLimit(packageName: String, limitMs: Long) {
        prefs.edit { putLong("${PREF_DAILY_LIMIT_PREFIX}$packageName", limitMs) }
    }

    /**
     * Get today's usage for an app in milliseconds
     * Uses the same method as AppUsageStatsManager to ensure consistency with app list display
     */
    fun getTodayUsage(packageName: String): Long {
        resetIfNewDay()
        
        // Use AppUsageStatsManager.getAppUsageTime() to get the same value shown in app list
        // This uses queryEvents for accurate real-time tracking
        val usageFromStats = appUsageStatsManager.getAppUsageTime(packageName)
        
        // Store it for quick access (though we'll always use the manager for accuracy)
        if (usageFromStats > 0) {
            prefs.edit { putLong("${PREF_DAILY_USAGE_PREFIX}$packageName", usageFromStats) }
        }
        
        return usageFromStats
    }

    /**
     * Record app usage (called when app is launched or becomes foreground)
     */
    fun recordAppUsage(packageName: String) {
        resetIfNewDay()
        // Usage is tracked via UsageStatsManager, but we can store a timestamp
        // to track when the app was last used
        prefs.edit { putLong("last_used_$packageName", System.currentTimeMillis()) }
    }

    /**
     * Check if app can be launched (hasn't exceeded daily limit)
     */
    fun canLaunchApp(packageName: String): Boolean {
        if (!isTimerEnabled(packageName)) {
            return true // Timer not enabled, allow launch
        }

        val limit = getDailyLimit(packageName)
        if (limit == 0L) {
            return true // No limit set, allow launch
        }

        val usage = getTodayUsage(packageName)
        return usage < limit
    }

    /**
     * Get remaining time for today in milliseconds
     */
    fun getRemainingTime(packageName: String): Long {
        if (!isTimerEnabled(packageName)) {
            return Long.MAX_VALUE
        }

        val limit = getDailyLimit(packageName)
        if (limit == 0L) {
            return Long.MAX_VALUE
        }

        val usage = getTodayUsage(packageName)
        return maxOf(0L, limit - usage)
    }

    /**
     * Format time in milliseconds to readable string
     */
    fun formatTime(ms: Long): String {
        if (ms == Long.MAX_VALUE) {
            return "Unlimited"
        }

        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Show dialog to set daily limit for an app
     */
    fun showSetLimitDialog(packageName: String, appName: String, onLimitSet: (Long) -> Unit) {
        val currentLimit = getDailyLimit(packageName)
        val input = EditText(context).apply {
            hint = "Enter minutes (0 to disable)"
            setText(if (currentLimit > 0) (currentLimit / 60000).toString() else "")
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setHintTextColor(ContextCompat.getColor(context, android.R.color.white))
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Set Daily Limit for $appName")
            .setMessage("Enter daily usage limit in minutes:")
            .setView(input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutes = input.text.toString().toLongOrNull() ?: 0L
                    val limitMs = minutes * 60000L
                    setDailyLimit(packageName, limitMs)
                    setTimerEnabled(packageName, limitMs > 0)
                    onLimitSet(limitMs)
                    Toast.makeText(
                        context,
                        if (limitMs > 0) "Daily limit set to ${formatTime(limitMs)}" else "Daily limit disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: NumberFormatException) {
                    Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Reset usage data if it's a new day
     */
    private fun resetIfNewDay() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lastResetDate = prefs.getLong(PREF_LAST_RESET_DATE, 0L)
        
        if (lastResetDate != today) {
            // New day - clear all usage data
            prefs.edit {
                val allPrefs = prefs.all
                for (key in allPrefs.keys) {
                    if (key.startsWith(PREF_DAILY_USAGE_PREFIX)) {
                        remove(key)
                    }
                }
                putLong(PREF_LAST_RESET_DATE, today)
            }
        }
    }

    /**
     * Reset usage for a specific app (for testing or manual reset)
     */
    fun resetUsage(packageName: String) {
        prefs.edit { remove("${PREF_DAILY_USAGE_PREFIX}$packageName") }
    }

    /**
     * Get all apps with timers enabled
     */
    fun getAppsWithTimers(): Set<String> {
        val apps = mutableSetOf<String>()
        val allPrefs = prefs.all
        for (key in allPrefs.keys) {
            if (key.startsWith(PREF_TIMER_ENABLED_PREFIX) && prefs.getBoolean(key, false)) {
                val packageName = key.removePrefix(PREF_TIMER_ENABLED_PREFIX)
                apps.add(packageName)
            }
        }
        return apps
    }
}
