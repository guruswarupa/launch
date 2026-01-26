package com.guruswarupa.launch

import android.content.SharedPreferences

/**
 * Manages hidden apps - apps that are hidden from the main app list.
 * Similar to FavoriteAppManager but for hiding apps instead.
 */
class HiddenAppManager(private val sharedPreferences: SharedPreferences) {
    private val HIDDEN_APPS_KEY = "hidden_apps"
    
    // Cache hidden apps to avoid repeated SharedPreferences reads
    private var hiddenAppsCache: Set<String>? = null
    private var cacheValid = false
    
    private fun getHiddenAppsInternal(): Set<String> {
        if (!cacheValid || hiddenAppsCache == null) {
            hiddenAppsCache = sharedPreferences.getStringSet(HIDDEN_APPS_KEY, emptySet()) ?: emptySet()
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
        sharedPreferences.edit().putStringSet(HIDDEN_APPS_KEY, hiddenApps).apply()
        invalidateCache()
    }
    
    /**
     * Unhide an app (show it in the app list again)
     */
    fun unhideApp(packageName: String) {
        val hiddenApps = getHiddenAppsInternal().toMutableSet()
        hiddenApps.remove(packageName)
        sharedPreferences.edit().putStringSet(HIDDEN_APPS_KEY, hiddenApps).apply()
        invalidateCache()
    }
    
    /**
     * Check if an app is hidden
     */
    fun isAppHidden(packageName: String): Boolean {
        return getHiddenAppsInternal().contains(packageName)
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
    fun filterHiddenApps(apps: List<android.content.pm.ResolveInfo>): List<android.content.pm.ResolveInfo> {
        val hiddenApps = getHiddenApps()
        if (hiddenApps.isEmpty()) {
            return apps
        }
        return apps.filter { !hiddenApps.contains(it.activityInfo.packageName) }
    }
}
