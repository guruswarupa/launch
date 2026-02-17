package com.guruswarupa.launch

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
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
        private const val SERVICE_NAME = "Shake Detection"
        
        const val ACTION_START = "com.guruswarupa.launch.START_SHAKE_DETECTION"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_SHAKE_DETECTION"
    }
    
    override fun onCreate() {
        super.onCreate()
        try {
            // Start foreground immediately in onCreate to avoid timeout
            startForegroundServiceStatus()
            
            // Initialize torch manager
            torchManager = TorchManager(this)
            
            // Initialize shake detector with torch toggle callback
            shakeDetector = ShakeDetector(this) {
                // Triple shake detected - toggle torch
                if (GestureCoordinator.requestTrigger()) {
                    try {
                        torchManager?.toggleTorch()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling torch", e)
                    }
                } else {
                    Log.d(TAG, "Shake gesture ignored due to coordination")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // If startForeground fails, the service will likely crash or be killed by the system
        }
    }

    private fun startForegroundServiceStatus() {
        val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                ServiceNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
        }
    }
    
    private fun applySensitivity() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val sensitivity = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5)
        shakeDetector?.updateSensitivity(sensitivity)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Re-assert foreground state
            startForegroundServiceStatus()
            
            // Apply current sensitivity
            applySensitivity()

            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_START, null -> {
                    if (!isRunning) {
                        registerScreenReceiver()
                        registerSettingsReceiver()
                        updateShakeDetectionState()
                        isRunning = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY // Restart if killed by system
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
            
            ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
