package com.guruswarupa.launch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaActionSound
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Foreground service that detects back tap gestures in the background
 * and performs configured actions when detected.
 */
class BackTapService : Service() {
    
    private var backTapDetector: BackTapDetector? = null
    private var torchManager: TorchManager? = null
    private var isRunning = false
    private var screenOnReceiver: BroadcastReceiver? = null
    private var settingsReceiver: BroadcastReceiver? = null
    private val powerManager: PowerManager by lazy {
        getSystemService(POWER_SERVICE) as PowerManager
    }
    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    // Action types
    private var doubleTapAction = ACTION_SOUND_TOGGLE
    private var tripleTapAction = ACTION_SCREENSHOT
    
    companion object {
        private const val TAG = "BackTapService"
        private const val SERVICE_NAME = "Back Tap Gestures"
        
        const val ACTION_START = "com.guruswarupa.launch.START_BACK_TAP"
        const val ACTION_STOP = "com.guruswarupa.launch.STOP_BACK_TAP"
        
        const val ACTION_NONE = "none"
        const val ACTION_TORCH_TOGGLE = "torch_toggle"
        const val ACTION_SCREENSHOT = "screenshot"
        const val ACTION_NOTIFICATIONS = "toggle_notifications"
        const val ACTION_SCREEN_OFF = "screen_off"
        const val ACTION_SOUND_TOGGLE = "sound_toggle"
        
        private const val SHOW_DEBUG_FEEDBACK = false
    }
    
    override fun onCreate() {
        super.onCreate()
        try {
            val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            
            torchManager = TorchManager(this)
            
            backTapDetector = BackTapDetector(this) { count ->
                if (GestureCoordinator.requestTrigger()) {
                    handleBackTapAction(count)
                } else {
                    Log.d(TAG, "Back tap gesture ignored due to coordination")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }
    
    private fun applySettings() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val sensitivity = prefs.getInt(Constants.Prefs.BACK_TAP_SENSITIVITY, 5)
        
        // Load independent actions for double and triple tap
        // Default to user's requested values if not set
        doubleTapAction = prefs.getString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, ACTION_SOUND_TOGGLE) ?: ACTION_SOUND_TOGGLE
        tripleTapAction = prefs.getString(Constants.Prefs.BACK_TAP_TRIPLE_ACTION, ACTION_SCREENSHOT) ?: ACTION_SCREENSHOT
        
        backTapDetector?.updateSensitivity(sensitivity)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            val notification = ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, true)
            startForeground(ServiceNotificationManager.NOTIFICATION_ID, notification)
            
            applySettings()
            
            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    START_NOT_STICKY
                }
                ACTION_START, null -> {
                    if (!isRunning) {
                        registerScreenReceiver()
                        registerSettingsReceiver()
                        updateBackTapDetectionState()
                        isRunning = true
                    }
                    START_STICKY
                }
                else -> START_STICKY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
            START_STICKY
        }
    }
    
    private fun registerScreenReceiver() {
        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> updateBackTapDetectionState()
                    Intent.ACTION_SCREEN_OFF -> backTapDetector?.stop()
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        
        registerReceiver(screenOnReceiver, filter)
    }
    
    private fun registerSettingsReceiver() {
        settingsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                    applySettings()
                    updateBackTapDetectionState()
                }
            }
        }
        
        val filter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        registerReceiver(settingsReceiver, filter)
    }
    
    private fun updateBackTapDetectionState() {
        val prefs = getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
        
        if (isEnabled && powerManager.isInteractive) {
            backTapDetector?.start()
        } else {
            backTapDetector?.stop()
        }
    }
    
    private fun handleBackTapAction(tapCount: Int) {
        try {
            val action = if (tapCount == 2) doubleTapAction else if (tapCount >= 3) tripleTapAction else return
            
            Log.d(TAG, "Back tap action triggered: $action (count: $tapCount)")
            
            if (SHOW_DEBUG_FEEDBACK) {
                val sound = MediaActionSound()
                sound.play(MediaActionSound.SHUTTER_CLICK)
            }
            
            executeAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling back tap action: ${e.message}")
        }
    }
    
    private fun executeAction(action: String) {
        when (action) {
            ACTION_TORCH_TOGGLE -> torchManager?.toggleTorch()
            ACTION_SCREENSHOT -> takeScreenshot()
            ACTION_NOTIFICATIONS -> toggleNotificationsPanel()
            ACTION_SCREEN_OFF -> turnScreenOff()
            ACTION_SOUND_TOGGLE -> toggleSound()
        }
    }
    
    private fun toggleSound() {
        try {
            val currentMode = audioManager.ringerMode
            if (currentMode == AudioManager.RINGER_MODE_NORMAL) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling sound: ${e.message}")
        }
    }
    
    private fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val accessibilityService = ScreenLockAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.takeScreenshot()
            } else {
                Log.d(TAG, "Screenshot failed: Accessibility service not running")
            }
        }
    }
    
    private fun toggleNotificationsPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val accessibilityService = ScreenLockAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.toggleNotifications()
            }
        }
    }
    
    private fun turnScreenOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val accessibilityService = ScreenLockAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.lockScreen()
            }
        }
    }
    
    override fun onDestroy() {
        try {
            backTapDetector?.stop()
            screenOnReceiver?.let { unregisterReceiver(it) }
            settingsReceiver?.let { unregisterReceiver(it) }
            isRunning = false
            ServiceNotificationManager.updateServiceStatus(this, SERVICE_NAME, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
