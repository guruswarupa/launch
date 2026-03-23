package com.guruswarupa.launch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.edit
import com.guruswarupa.launch.managers.ServiceManager
import com.guruswarupa.launch.models.Constants

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TOGGLE_NIGHT_MODE = "com.guruswarupa.launch.TOGGLE_NIGHT_MODE"
        const val ACTION_TOGGLE_DIMMER = "com.guruswarupa.launch.TOGGLE_DIMMER"
        const val ACTION_TOGGLE_GRAYSCALE = "com.guruswarupa.launch.TOGGLE_GRAYSCALE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val serviceManager = ServiceManager(context, prefs)

        when (intent?.action) {
            ACTION_TOGGLE_NIGHT_MODE -> {
                val currentState = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
                prefs.edit { putBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, !currentState) }
                serviceManager.updateNightModeService()
            }
            ACTION_TOGGLE_DIMMER -> {
                val currentState = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
                prefs.edit { putBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, !currentState) }
                serviceManager.updateScreenDimmerService()
            }
            ACTION_TOGGLE_GRAYSCALE -> {
                val currentState = prefs.getBoolean(Constants.Prefs.GRAYSCALE_MODE_ENABLED, false)
                val newState = !currentState
                try {
                    Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer_enabled", if (newState) 1 else 0)
                    Settings.Secure.putInt(context.contentResolver, "accessibility_display_daltonizer", if (newState) 0 else -1)
                    prefs.edit { putBoolean(Constants.Prefs.GRAYSCALE_MODE_ENABLED, newState) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
