
package com.guruswarupa.launch

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.app.usage.UsageStatsManager
import android.content.Context
import android.app.ActivityManager

class AppUsageMonitor : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private var lastForegroundApp: String? = null
    private lateinit var appTimerManager: AppTimerManager

    override fun onCreate() {
        super.onCreate()
        appTimerManager = AppTimerManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitoringRunnable = object : Runnable {
            override fun run() {
                checkForegroundApp()
                handler.postDelayed(this, 1000) // Check every second
            }
        }
        handler.post(monitoringRunnable!!)
    }

    private fun checkForegroundApp() {
        val currentApp = getForegroundApp()

        if (currentApp != null && currentApp != lastForegroundApp) {
            if (currentApp == packageName) {
                // User returned to launcher, cancel any active timer
                appTimerManager.cancelTimer()
            }
            lastForegroundApp = currentApp
        }
    }

    private fun getForegroundApp(): String? {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = activityManager.getRunningTasks(1)

        return if (tasks.isNotEmpty()) {
            tasks[0].topActivity?.packageName
        } else {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitoringRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
