package com.guruswarupa.launch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.fragment.app.FragmentActivity
import java.util.Calendar

/**
 * Manages all broadcast receivers for the launcher
 */
class BroadcastReceiverManager(
    private val activity: FragmentActivity,
    private val sharedPreferences: android.content.SharedPreferences,
    private val onSettingsUpdated: () -> Unit,
    private val onNotificationsUpdated: () -> Unit,
    private val onPackageChanged: (String?, Boolean) -> Unit, // packageName, isRemoved
    private val onWallpaperChanged: () -> Unit,
    private val onBatteryChanged: () -> Unit
) {
    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                activity.runOnUiThread {
                    onSettingsUpdated()
                }
            }
        }
    }
    
    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.NOTIFICATIONS_UPDATED") {
                activity.runOnUiThread {
                    onNotificationsUpdated()
                }
            }
        }
    }
    
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val packageName = intent.data?.encodedSchemeSpecificPart
                        onPackageChanged(packageName, true)
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val packageName = intent.data?.encodedSchemeSpecificPart
                        onPackageChanged(packageName, false)
                    }
                }
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val packageName = intent.data?.encodedSchemeSpecificPart
                    onPackageChanged(packageName, false)
                }
            }
        }
    }
    
    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                activity.runOnUiThread {
                    onWallpaperChanged()
                }
            }
        }
    }
    
    private val batteryChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            activity.runOnUiThread {
                onBatteryChanged()
            }
        }
    }
    
    /**
     * Register all receivers
     */
    fun registerReceivers() {
        // Settings update receiver
        val settingsFilter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(settingsUpdateReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(settingsUpdateReceiver, settingsFilter)
        }
        
        // Notification update receiver
        val notificationFilter = IntentFilter("com.guruswarupa.launch.NOTIFICATIONS_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(notificationUpdateReceiver, notificationFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(notificationUpdateReceiver, notificationFilter)
        }
        
        // Package receiver
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        activity.registerReceiver(packageReceiver, packageFilter)
        
        // Wallpaper change receiver
        activity.registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        
        // Battery change receiver
        activity.registerReceiver(batteryChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    
    /**
     * Unregister all receivers
     */
    fun unregisterReceivers() {
        try {
            activity.unregisterReceiver(settingsUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        try {
            activity.unregisterReceiver(notificationUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        try {
            activity.unregisterReceiver(packageReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        try {
            activity.unregisterReceiver(wallpaperChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        try {
            activity.unregisterReceiver(batteryChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }
}
