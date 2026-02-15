package com.guruswarupa.launch

import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import java.util.Calendar
import java.util.concurrent.Executor

/**
 * Handles battery and usage stats refresh operations.
 * Extracted from MainActivity to reduce complexity.
 */
class UsageStatsRefreshManager(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val usageStatsManager: AppUsageStatsManager
) {
    /**
     * Safely execute a task on the background executor.
     */
    private fun safeExecute(task: Runnable): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        try {
            backgroundExecutor.execute(task)
            return true
        } catch (e: Exception) {
            Log.w("UsageStatsRefreshManager", "Task rejected by executor", e)
            return false
        }
    }

    /**
     * Updates battery information in background.
     */
    fun updateBatteryInBackground() {
        safeExecute {
            val batteryManager = BatteryManager(activity)
            activity.runOnUiThread {
                val batteryPercentageTextView = activity.findViewById<TextView>(R.id.battery_percentage)
                batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }
            }
        }
    }

    /**
     * Updates usage information in background.
     */
    fun updateUsageInBackground() {
        safeExecute {
            // Get screen time usage for today
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val screenTimeMillis = usageStatsManager.getTotalUsageForPeriod(startTime, endTime)
            val formattedTime = usageStatsManager.formatUsageTime(screenTimeMillis)

            activity.runOnUiThread {
                val screenTimeTextView = activity.findViewById<TextView>(R.id.screen_time)
                screenTimeTextView?.text = activity.getString(R.string.screen_time_format, formattedTime)
            }
        }
    }
}
