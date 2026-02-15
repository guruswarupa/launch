package com.guruswarupa.launch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class PhysicalActivityTrackingService : Service() {
    
    private var activityManager: PhysicalActivityManager? = null
    
    companion object {
        private const val TAG = "PhysicalActivityService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "physical_activity_tracking_channel"
        private const val CHANNEL_NAME = "Physical Activity Tracking"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 1. Establish notification channel first (very fast)
        createNotificationChannel()
        
        // 2. CALL STARTFOREGROUND IMMEDIATELY - ABSOLUTE PRIORITY
        // This satisfies the system requirement within milliseconds
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call startForeground", e)
        }
        
        // 3. Perform initialization asynchronously to avoid blocking the main thread
        activityManager = PhysicalActivityManager(this)
        activityManager?.initializeAsync()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Safe to call multiple times, ensures foreground state is maintained
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
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
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Tracks your steps and distance even when the screen is off"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Physical Activity")
            .setContentText("Tracking your activity in the background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }
}
