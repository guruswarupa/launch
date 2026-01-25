package com.guruswarupa.launch

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

/**
 * Manages usage stats display: weekly graph, daily dialogs, and refresh logic
 */
class UsageStatsDisplayManager(
    private val activity: MainActivity,
    private val usageStatsManager: AppUsageStatsManager,
    private val weeklyUsageGraph: WeeklyUsageGraphView,
    private val adapter: AppAdapter,
    private val recyclerView: RecyclerView,
    private val handler: Handler
) {
    private var lastUpdateDate: String = ""
    
    init {
        // Setup weekly usage graph callback
        weeklyUsageGraph.onDaySelected = { day, appUsages ->
            showDailyUsageDialog(day, appUsages)
        }
    }
    
    fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }
    
    fun checkDateChangeAndRefreshUsage() {
        val currentDate = getCurrentDateString()
        if (currentDate != lastUpdateDate) {
            lastUpdateDate = currentDate
            refreshUsageStats()
        }
    }
    
    fun loadWeeklyUsageData() {
        // This function is already called from background threads, but ensure UI updates are on main thread
        if (usageStatsManager.hasUsageStatsPermission()) {
            val weeklyData = usageStatsManager.getWeeklyUsageData()
            val appUsageData = usageStatsManager.getWeeklyAppUsageData()
            
            activity.runOnUiThread {
                weeklyUsageGraph.setUsageData(weeklyData)
                weeklyUsageGraph.setAppUsageData(appUsageData)
            }
        }
    }
    
    fun refreshUsageStats() {
        // Clear adapter cache to force refresh
        adapter.clearUsageCache()
        // Only refresh visible items for better performance
        handler.postDelayed({
            val layoutManager = recyclerView.layoutManager
            if (layoutManager is LinearLayoutManager) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                    adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                } else {
                    adapter.notifyDataSetChanged()
                }
            } else {
                adapter.notifyDataSetChanged()
            }
        }, 100)
    }
    
    private fun showDailyUsageDialog(day: String, appUsages: Map<String, Long>) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_daily_usage, null)
        val dayTitle = dialogView.findViewById<TextView>(R.id.day_title)
        val totalTime = dialogView.findViewById<TextView>(R.id.total_time)
        val pieChart = dialogView.findViewById<DailyUsagePieView>(R.id.daily_pie_chart)
        val appUsageList = dialogView.findViewById<RecyclerView>(R.id.app_usage_list)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)
        
        // Set day title
        dayTitle.text = day
        
        // Calculate and display total time
        val totalUsage = appUsages.values.sum()
        val totalTimeText = formatUsageTimeForDialog(totalUsage)
        totalTime.text = "Total: $totalTimeText"
        
        // Set pie chart data
        pieChart.setAppUsageData(appUsages)
        
        // Setup RecyclerView with app usage list
        val sortedApps = appUsages.toList().sortedByDescending { it.second }
        val totalUsageFloat = totalUsage.toFloat()
        val appUsageItems = sortedApps.mapIndexed { index, (appName, usage) ->
            val percentage = if (totalUsageFloat > 0) (usage.toFloat() / totalUsageFloat * 100f) else 0f
            val color = pieChart.getColorForApp(index)
            AppUsageItem(appName, usage, percentage, color)
        }
        
        appUsageList.layoutManager = LinearLayoutManager(activity)
        appUsageList.adapter = AppUsageAdapter(appUsageItems)
        
        // Create and show dialog
        val dialog = android.app.AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun formatUsageTimeForDialog(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0m"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
