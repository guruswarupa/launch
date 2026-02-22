package com.guruswarupa.launch.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for Device Admin permission to turn off the screen.
 */
class ScreenOffAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // Device Admin enabled
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Device Admin disabled
    }
}
