package com.guruswarupa.launch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that detects shake gestures in the background
 * and toggles the torch when triple shake is detected.
 */
class ShakeDetectionService : Service() {
    
    private var shakeDetector: ShakeDetector? = null
    private var torchManager: TorchManager? = null
    private var isRunning = false
    private var screenOnReceiver: BroadcastReceiver? = null
    private var settingsReceiver: BroadcastReceiver? = null
    private val powerManager: PowerManager by lazy {
        getSystemService(POWER_SERVICE) as PowerManager
    }
    
    companion object {
        private const val TAG = "ShakeDetectionService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "shake_detection_channel"
        private const val CHANNEL_NAME = "Quick Actions"
        
        const val ACTION_START = "com.guruswarupa.launch.START_SHAKE_DETECTION"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_SHAKE_DETECTION"
    }
    
    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            
            // Start foreground immediately in onCreate
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Initialize torch manager
            torchManager = TorchManager(this)
            
            // Initialize shake detector with torch toggle callback
            shakeDetector = ShakeDetector(this) {
                // Triple shake detected - toggle torch
                try {
                    torchManager?.toggleTorch()
                } catch (_: Exception) {
                    // Error toggling torch - silently fail
                }
            }
        } catch (_: Exception) {
            // Error in onCreate - silently fail
        }
    }
    
    private fun applySensitivity() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val sensitivity = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5)
        shakeDetector?.updateSensitivity(sensitivity)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            // Re-assert foreground state
            startForeground(NOTIFICATION_ID, createNotification())
            
            // Apply current sensitivity
            applySensitivity()

            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    START_NOT_STICKY
                }
                ACTION_START, null -> {
                    if (!isRunning) {
                        registerScreenReceiver()
                        registerSettingsReceiver()
                        updateShakeDetectionState()
                        isRunning = true
                    }
                    START_STICKY // Restart if killed by system
                }
                else -> START_STICKY
            }
        } catch (_: Exception) {
            START_STICKY
        }
    }
    
    /**
     * Updates shake detection based on screen state
     * Only listens when screen is on to save battery
     */
    private fun updateShakeDetectionState() {
        val isScreenOn = powerManager.isInteractive
        
        if (isScreenOn) {
            shakeDetector?.start()
        } else {
            shakeDetector?.stop()
        }
    }
    
    /**
     * Registers receiver to monitor screen on/off state
     */
    private fun registerScreenReceiver() {
        if (screenOnReceiver != null) return
        
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        shakeDetector?.start()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        shakeDetector?.stop()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        // Use ContextCompat to register receiver with appropriate export flag
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    /**
     * Registers receiver to monitor settings updates
     */
    private fun registerSettingsReceiver() {
        if (settingsReceiver != null) return
        
        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                applySensitivity()
            }
        }
        
        val filter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        
        // Use ContextCompat to register receiver with appropriate export flag
        ContextCompat.registerReceiver(
            this,
            settingsReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            isRunning = false
            screenOnReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering screen receiver", e)
                }
            }
            screenOnReceiver = null
            
            settingsReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering settings receiver", e)
                }
            }
            settingsReceiver = null
            
            shakeDetector?.cleanup()
            torchManager?.turnOffTorch() // Turn off torch when service stops
        } catch (_: Exception) {
            // Error in onDestroy - silently fail
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Detects shake gestures to toggle torch"
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
            .setContentTitle("Quick Actions Active")
            .setContentText("Shake two times to toggle torch")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }
}
