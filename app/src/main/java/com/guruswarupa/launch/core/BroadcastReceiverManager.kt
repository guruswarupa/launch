package com.guruswarupa.launch.core

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Manages all broadcast receivers for the launcher
 */
class BroadcastReceiverManager(
    private val activity: FragmentActivity,
    @Suppress("UNUSED_PARAMETER") private val sharedPreferences: android.content.SharedPreferences,
    private val onSettingsUpdated: () -> Unit,
    private val onNotificationsUpdated: () -> Unit,
    private val onPackageChanged: (String?, Boolean) -> Unit, // packageName, isRemoved
    private val onWallpaperChanged: () -> Unit,
    private val onBatteryChanged: () -> Unit,
    private val onActivityRecognitionPermissionGranted: () -> Unit = {},
    private val onDndStateChanged: () -> Unit = {}
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
            @Suppress("DEPRECATION")
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
    
    private val activityRecognitionPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.ACTIVITY_RECOGNITION_PERMISSION_GRANTED") {
                activity.runOnUiThread {
                    onActivityRecognitionPermissionGranted()
                }
            }
        }
    }

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                activity.runOnUiThread {
                    onDndStateChanged()
                }
            }
        }
    }
    
    /**
     * Register all receivers
     */
    fun registerReceivers() {
        // Settings update receiver
        val settingsFilter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        registerReceiverCompat(settingsUpdateReceiver, settingsFilter, exported = false)
        
        // Notification update receiver
        val notificationFilter = IntentFilter("com.guruswarupa.launch.NOTIFICATIONS_UPDATED")
        registerReceiverCompat(notificationUpdateReceiver, notificationFilter, exported = false)
        
        // Package receiver
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiverCompat(packageReceiver, packageFilter, exported = true)
        
        // Wallpaper change receiver
        @Suppress("DEPRECATION")
        val wallpaperFilter = IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiverCompat(wallpaperChangeReceiver, wallpaperFilter, exported = true)
        
        // Battery change receiver
        registerReceiverCompat(batteryChangeReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), exported = true)
        
        // Activity recognition permission receiver
        val activityRecognitionFilter = IntentFilter("com.guruswarupa.launch.ACTIVITY_RECOGNITION_PERMISSION_GRANTED")
        registerReceiverCompat(activityRecognitionPermissionReceiver, activityRecognitionFilter, exported = false)

        // DND state change receiver
        registerReceiverCompat(dndReceiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED), exported = true)
    }

    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter, exported: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = if (exported) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
            activity.registerReceiver(receiver, filter, flags)
        } else {
            ContextCompat.registerReceiver(
                activity,
                receiver,
                filter,
                if (exported) ContextCompat.RECEIVER_EXPORTED else ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }
    
    /**
     * Unregister all receivers
     */
    fun unregisterReceivers() {
        val receivers = listOf(
            settingsUpdateReceiver,
            notificationUpdateReceiver,
            packageReceiver,
            wallpaperChangeReceiver,
            batteryChangeReceiver,
            activityRecognitionPermissionReceiver,
            dndReceiver
        )

        for (receiver in receivers) {
            try {
                activity.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was not registered
            }
        }
    }
}
