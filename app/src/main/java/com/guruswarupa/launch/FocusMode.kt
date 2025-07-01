
package com.guruswarupa.launch

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ResolveInfo

data class FocusModeConfig(
    val isEnabled: Boolean = false,
    val allowedApps: Set<String> = emptySet()
)

class FocusModeManager(private val context: Context, private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val FOCUS_MODE_ENABLED = "focus_mode_enabled"
        private const val FOCUS_MODE_ALLOWED_APPS = "focus_mode_allowed_apps"
    }

    fun isFocusModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(FOCUS_MODE_ENABLED, false)
    }

    fun toggleFocusMode() {
        val currentState = isFocusModeEnabled()
        sharedPreferences.edit().putBoolean(FOCUS_MODE_ENABLED, !currentState).apply()
    }

    fun getAllowedApps(): Set<String> {
        return sharedPreferences.getStringSet(FOCUS_MODE_ALLOWED_APPS, emptySet()) ?: emptySet()
    }

    fun setAllowedApps(packageNames: Set<String>) {
        sharedPreferences.edit().putStringSet(FOCUS_MODE_ALLOWED_APPS, packageNames).apply()
    }

    fun addAllowedApp(packageName: String) {
        val currentApps = getAllowedApps().toMutableSet()
        currentApps.add(packageName)
        setAllowedApps(currentApps)
    }

    fun removeAllowedApp(packageName: String) {
        val currentApps = getAllowedApps().toMutableSet()
        currentApps.remove(packageName)
        setAllowedApps(currentApps)
    }

    fun isAppAllowedInFocusMode(packageName: String): Boolean {
        return if (isFocusModeEnabled()) {
            getAllowedApps().contains(packageName)
        } else {
            true // All apps allowed in normal mode
        }
    }

    fun filterAppsForFocusMode(apps: List<ResolveInfo>): List<ResolveInfo> {
        return if (isFocusModeEnabled()) {
            val allowedApps = getAllowedApps()
            apps.filter { allowedApps.contains(it.activityInfo.packageName) }
        } else {
            apps
        }
    }
}
