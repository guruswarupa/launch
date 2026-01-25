package com.guruswarupa.launch

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import java.util.concurrent.Executor

/**
 * Handles battery and usage stats refresh operations.
 * Extracted from MainActivity to reduce complexity.
 */
class UsageStatsRefreshManager(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val handler: Handler,
    private val usageStatsManager: AppUsageStatsManager,
    private val adapter: AppAdapter?,
    private val recyclerView: RecyclerView,
    private val weeklyUsageGraph: WeeklyUsageGraphView?,
    private val usageStatsDisplayManager: UsageStatsDisplayManager?
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
                screenTimeTextView?.text = "Screen Time: $formattedTime"
            }
        }
    }

    /**
     * Refresh all usage data in background without blocking UI.
     * Updates app list usage times and weekly graph.
     */
    fun refreshUsageDataInBackground(deferExpensive: Boolean = false) {
        safeExecute {
            try {
                // Clear adapter cache first to ensure fresh data
                adapter?.clearUsageCache()

                // Only refresh visible items first for fast UI response
                // Defer expensive weekly usage graph data until after UI is shown
                if (!deferExpensive && usageStatsManager.hasUsageStatsPermission()) {
                    // Load weekly data only if not deferring (e.g., user interaction)
                    val weeklyData = usageStatsManager.getWeeklyUsageData()
                    val appUsageData = usageStatsManager.getWeeklyAppUsageData()

                    activity.runOnUiThread {
                        weeklyUsageGraph?.let {
                            it.setUsageData(weeklyData)
                            it.setAppUsageData(appUsageData)
                        }
                    }
                }

                // Refresh only visible items in adapter (much faster than full refresh)
                // Use handler.postDelayed to allow UI to render first
                handler.postDelayed({
                    // Only refresh visible items, not entire list
                    val layoutManager = recyclerView.layoutManager
                    if (layoutManager is LinearLayoutManager) {
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                            adapter?.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                        } else {
                            adapter?.notifyDataSetChanged()
                        }
                    } else {
                        adapter?.notifyDataSetChanged()
                    }
                }, 100) // Small delay to let UI render first
            } catch (e: Exception) {
                // Silently handle errors to prevent crashes
                e.printStackTrace()
            }
        }
    }

    /**
     * Load expensive weekly usage graph data (called lazily when needed).
     */
    fun loadWeeklyUsageGraphData() {
        safeExecute {
            try {
                usageStatsDisplayManager?.loadWeeklyUsageData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
