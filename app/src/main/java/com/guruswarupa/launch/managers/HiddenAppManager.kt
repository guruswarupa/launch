package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import android.util.Log

/**
 * Manages hidden apps - apps that are hidden from the main app list.
 * Similar to FavoriteAppManager but for hiding apps instead.
 */
class HiddenAppManager(private val sharedPreferences: SharedPreferences) {
    
    companion object {
        private const val HIDDEN_APPS_KEY = "hidden_apps"
        private const val TAG = "HiddenAppManager"
    }
    
    // Cache hidden apps to avoid repeated SharedPreferences reads
    private var hiddenAppsCache: Set<String>? = null
    private var cacheValid = false
    
    private fun getHiddenAppsInternal(): Set<String> {
        if (!cacheValid || hiddenAppsCache == null) {
            try {
                hiddenAppsCache = sharedPreferences.getStringSet(HIDDEN_APPS_KEY, emptySet()) ?: emptySet()
            } catch (e: ClassCastException) {
                Log.e(TAG, "Data corruption: $HIDDEN_APPS_KEY is not a Set. Attempting recovery.", e)
                val stringValue = try { sharedPreferences.getString(HIDDEN_APPS_KEY, null) } catch (_: Exception) { null }
                val recoveredSet = if (stringValue != null) {
                    if (stringValue.startsWith("[") && stringValue.endsWith("]")) {
                        stringValue.substring(1, stringValue.length - 1)
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                    } else {
                        setOf(stringValue)
                    }
                } else {
                    emptySet()
                }
                
                sharedPreferences.edit { 
                    remove(HIDDEN_APPS_KEY)
                    putStringSet(HIDDEN_APPS_KEY, recoveredSet)
                }
                hiddenAppsCache = recoveredSet
            }
            cacheValid = true
        }
        return hiddenAppsCache ?: emptySet()
    }
    
    private fun invalidateCache() {
        cacheValid = false
        hiddenAppsCache = null
    }
    
    /**
     * Hide an app from the app list
     */
    fun hideApp(packageName: String) {
        val hiddenApps = getHiddenAppsInternal().toMutableSet()
        hiddenApps.add(packageName)
        sharedPreferences.edit { putStringSet(HIDDEN_APPS_KEY, hiddenApps) }
        invalidateCache()
    }
    
    /**
     * Unhide an app (show it in the app list again)
     */
    fun unhideApp(packageName: String) {
        val hiddenApps = getHiddenAppsInternal().toMutableSet()
        hiddenApps.remove(packageName)
        sharedPreferences.edit { putStringSet(HIDDEN_APPS_KEY, hiddenApps) }
        invalidateCache()
    }
    
    /**
     * Check if an app is hidden
     */
    fun isAppHidden(packageName: String): Boolean {
        return getHiddenAppsInternal().contains(packageName)
    }
    
    /**
     * Force refresh from SharedPreferences (invalidates cache)
     * Call this when you know the data might have changed externally
     */
    fun forceRefresh() {
        invalidateCache()
    }
    
    /**
     * Get all hidden apps
     */
    fun getHiddenApps(): Set<String> {
        return getHiddenAppsInternal()
    }
    
    /**
     * Filter out hidden apps from a list of apps
     */
    @Suppress("unused")
    fun filterHiddenApps(apps: List<android.content.pm.ResolveInfo>): List<android.content.pm.ResolveInfo> {
        val hiddenApps = getHiddenApps()
        if (hiddenApps.isEmpty()) {
            return apps
        }
        return apps.filter { !hiddenApps.contains(it.activityInfo.packageName) }
    }
}
