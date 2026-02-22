package com.guruswarupa.launch.managers

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class FocusModeManager(private val context: Context, private val sharedPreferences: SharedPreferences) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val FOCUS_MODE_ENABLED = "focus_mode_enabled"
        private const val FOCUS_MODE_ALLOWED_APPS = "focus_mode_allowed_apps"
    }

    fun isFocusModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(FOCUS_MODE_ENABLED, false)
    }

    fun setFocusModeEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(FOCUS_MODE_ENABLED, enabled) }
        updateDndState(enabled)
    }

    fun updateDndState(enabled: Boolean) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return

        val filter = if (enabled) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }

        if (notificationManager.currentInterruptionFilter != filter) {
            notificationManager.setInterruptionFilter(filter)
        }
    }

    fun getAllowedApps(): Set<String> {
        return sharedPreferences.getStringSet(FOCUS_MODE_ALLOWED_APPS, emptySet()) ?: emptySet()
    }

    fun updateAllowedApps(packageNames: Set<String>) {
        sharedPreferences.edit { putStringSet(FOCUS_MODE_ALLOWED_APPS, packageNames) }
    }

    fun addAllowedApp(packageName: String) {
        val currentApps = getAllowedApps().toMutableSet()
        currentApps.add(packageName)
        updateAllowedApps(currentApps)
    }

    fun removeAllowedApp(packageName: String) {
        val currentApps = getAllowedApps().toMutableSet()
        currentApps.remove(packageName)
        updateAllowedApps(currentApps)
    }
}
