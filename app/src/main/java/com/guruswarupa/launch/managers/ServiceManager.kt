package com.guruswarupa.launch.managers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.*

/**
 * Manages background services for the launcher application.
 * Handles starting/stopping various services based on user preferences.
 */
class ServiceManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    
    /**
     * Updates shake detection service based on user preference
     */
    fun updateShakeDetectionService() {
        val isTorchEnabled = sharedPreferences.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
        if (isTorchEnabled) {
            startShakeDetectionService()
        } else {
            stopShakeDetectionService()
        }
    }
    
    /**
     * Updates back tap detection service based on user preference
     */
    fun updateBackTapService() {
        val isBackTapEnabled = sharedPreferences.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
        Log.d("ServiceManager", "Back tap enabled in settings: $isBackTapEnabled")
        if (isBackTapEnabled) {
            startBackTapService()
        } else {
            stopBackTapService()
        }
    }
    
    /**
     * Starts the back tap detection service for background quick actions
     */
    private fun startBackTapService() {
        Log.d("ServiceManager", "Starting BackTapService")
        val intent = Intent(context, BackTapService::class.java).apply {
            action = BackTapService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }
    
    /**
     * Stops the back tap detection service
     */
    private fun stopBackTapService() {
        val intent = Intent(context, BackTapService::class.java).apply {
            action = BackTapService.ACTION_STOP
        }
        context.stopService(intent)
    }
    
    /**
     * Starts the shake detection service for background quick actions
     */
    private fun startShakeDetectionService() {
        val intent = Intent(context, ShakeDetectionService::class.java).apply {
            action = ShakeDetectionService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }
    
    /**
     * Stops the shake detection service
     */
    private fun stopShakeDetectionService() {
        val intent = Intent(context, ShakeDetectionService::class.java).apply {
            action = ShakeDetectionService.ACTION_STOP
        }
        context.stopService(intent)
    }

    /**
     * Updates screen dimmer service based on user preference
     */
    fun updateScreenDimmerService() {
        val isDimmerEnabled = sharedPreferences.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
        if (isDimmerEnabled && Settings.canDrawOverlays(context)) {
            val dimLevel = sharedPreferences.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 10)
            ScreenDimmerService.startService(context, dimLevel)
        } else {
            ScreenDimmerService.stopService(context)
        }
    }

    /**
     * Updates night mode service based on user preference
     */
    fun updateNightModeService() {
        val isNightModeEnabled = sharedPreferences.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
        if (isNightModeEnabled && Settings.canDrawOverlays(context)) {
            val intensity = sharedPreferences.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
            NightModeService.startService(context, intensity)
        } else {
            NightModeService.stopService(context)
        }
    }

    /**
     * Updates Flip to DND service based on user preference
     */
    fun updateFlipToDndService() {
        val isFlipEnabled = sharedPreferences.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (isFlipEnabled && notificationManager.isNotificationPolicyAccessGranted) {
            FlipToDndService.startService(context)
        } else {
            FlipToDndService.stopService(context)
        }
    }
    
    /**
     * Stops all services managed by this manager
     */
    fun stopAllServices() {
        stopShakeDetectionService()
        FlipToDndService.stopService(context)
    }
}