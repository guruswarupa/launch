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
    
    private lateinit var activityManager: PhysicalActivityManager
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    companion object {
        private const val TAG = "PhysicalActivityService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "physical_activity_tracking_channel"
        private const val CHANNEL_NAME = "Physical Activity Tracking"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        activityManager = PhysicalActivityManager(this)
        
        // Note: Step counter sensors are hardware-based and work in deep sleep mode
        // No wake lock needed - this saves significant battery
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start tracking
        if (activityManager.hasActivityRecognitionPermission()) {
            activityManager.startTracking()
            // Don't update notification periodically - keep it minimal
        } else {
            Log.w(TAG, "Activity recognition permission not granted")
            stopSelf()
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activityManager.stopTracking()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
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
