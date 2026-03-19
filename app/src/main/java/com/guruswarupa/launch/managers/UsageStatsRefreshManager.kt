package com.guruswarupa.launch.managers

import android.util.Log
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import java.util.concurrent.Executor

import com.guruswarupa.launch.managers.AppUsageStatsManager





class UsageStatsRefreshManager(
    private val activity: FragmentActivity,
    private val backgroundExecutor: Executor,
    private val usageStatsManager: AppUsageStatsManager
) {
    


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

    


    fun updateBatteryInBackground() {
        safeExecute {
            val batteryManager = BatteryManager(activity)
            activity.runOnUiThread {
                val batteryPercentageTextView = activity.findViewById<TextView>(R.id.battery_percentage)
                batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }
            }
        }
    }

    



    fun updateUsageInBackground() {
        
    }
}
