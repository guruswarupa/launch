package com.guruswarupa.launch.services

import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.app.Service
import com.guruswarupa.launch.managers.PhysicalActivityManager
import com.guruswarupa.launch.managers.ServiceNotificationManager

class PhysicalActivityTrackingService : Service() {
    
    private var activityManager: PhysicalActivityManager? = null
    
    companion object {
        private const val TAG = "PhysicalActivityService"
        private const val SERVICE_NAME = "Activity Tracking"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 1. CALL STARTFOREGROUND IMMEDIATELY - ABSOLUTE PRIORITY
        try {
            val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call startForeground", e)
        }
        
        // 2. Perform initialization asynchronously to avoid blocking the main thread
        activityManager = PhysicalActivityManager(this)
        activityManager?.initializeAsync()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Safe to call multiple times, ensures foreground state is maintained
        try {
            val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-assert startForeground", e)
        }

        // Just ensure tracking is active
        activityManager?.let { manager ->
            if (manager.hasActivityRecognitionPermission()) {
                manager.startTracking()
            } else {
                Log.w(TAG, "Activity recognition permission not granted")
                stopSelf()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activityManager?.stopTracking()
        ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
