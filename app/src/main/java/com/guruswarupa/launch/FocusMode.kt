
package com.guruswarupa.launch

import android.content.SharedPreferences

class FocusModeManager(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val FOCUS_MODE_ENABLED = "focus_mode_enabled"
        private const val FOCUS_MODE_ALLOWED_APPS = "focus_mode_allowed_apps"
    }

    fun isFocusModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(FOCUS_MODE_ENABLED, false)
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
}
