package com.guruswarupa.launch

import android.content.SharedPreferences
import androidx.core.content.edit

class FocusModeManager(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val FOCUS_MODE_ENABLED = "focus_mode_enabled"
        private const val FOCUS_MODE_ALLOWED_APPS = "focus_mode_allowed_apps"
    }

    @Suppress("unused")
    fun isFocusModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(FOCUS_MODE_ENABLED, false)
    }


    fun getAllowedApps(): Set<String> {
        return sharedPreferences.getStringSet(FOCUS_MODE_ALLOWED_APPS, emptySet()) ?: emptySet()
    }

    private fun setAllowedApps(packageNames: Set<String>) {
        sharedPreferences.edit { putStringSet(FOCUS_MODE_ALLOWED_APPS, packageNames) }
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
