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
    private var updateRunnable: Runnable? = null
    
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
            
            // Update notification periodically
            startNotificationUpdates()
        } else {
            Log.w(TAG, "Activity recognition permission not granted")
            stopSelf()
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    private fun startNotificationUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateNotification()
                // Update every 5 minutes to reduce battery usage
                handler.postDelayed(this, 5 * 60000L)
            }
        }
        handler.post(updateRunnable!!)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
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
        
        val activityData = activityManager.getTodayActivity()
        val stepsText = String.format("%,d steps", activityData.steps)
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Physical Activity")
            .setContentText(stepsText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }
    
    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
