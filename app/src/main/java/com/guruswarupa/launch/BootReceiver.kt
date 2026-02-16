package com.guruswarupa.launch

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            
            // Handle Screen Dimmer
            val isDimmerEnabled = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
            val dimLevel = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 50)
            
            if (isDimmerEnabled && Settings.canDrawOverlays(context)) {
                ScreenDimmerService.startService(context, dimLevel)
            }
            
            // Handle Night Mode
            val isNightModeEnabled = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
            val intensity = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 50)
            
            if (isNightModeEnabled && Settings.canDrawOverlays(context)) {
                NightModeService.startService(context, intensity)
            }
            
            // Handle Flip to DND
            val isFlipEnabled = prefs.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (isFlipEnabled && notificationManager.isNotificationPolicyAccessGranted) {
                FlipToDndService.startService(context)
            }
        }
    }
}
