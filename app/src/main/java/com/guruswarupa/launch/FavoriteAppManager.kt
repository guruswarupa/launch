package com.guruswarupa.launch

import android.content.SharedPreferences

class FavoriteAppManager(private val sharedPreferences: SharedPreferences) {
    private val FAVORITE_APPS_KEY = "favorite_apps"
    private val SHOW_ALL_APPS_KEY = "show_all_apps_mode"
    
    // Cache favorites to avoid repeated SharedPreferences reads
    private var favoritesCache: Set<String>? = null
    private var cacheValid = false
    
    private fun getFavoriteAppsInternal(): Set<String> {
        if (!cacheValid || favoritesCache == null) {
            favoritesCache = sharedPreferences.getStringSet(FAVORITE_APPS_KEY, emptySet()) ?: emptySet()
            cacheValid = true
        }
        return favoritesCache!!
    }
    
    private fun invalidateCache() {
        cacheValid = false
        favoritesCache = null
    }
    
    fun addFavoriteApp(packageName: String) {
        val favorites = getFavoriteAppsInternal().toMutableSet()
        favorites.add(packageName)
        sharedPreferences.edit().putStringSet(FAVORITE_APPS_KEY, favorites).apply()
        invalidateCache()
    }
    
    fun removeFavoriteApp(packageName: String) {
        val favorites = getFavoriteAppsInternal().toMutableSet()
        favorites.remove(packageName)
        sharedPreferences.edit().putStringSet(FAVORITE_APPS_KEY, favorites).apply()
        invalidateCache()
    }
    
    fun isFavoriteApp(packageName: String): Boolean {
        return getFavoriteAppsInternal().contains(packageName)
    }
    
    fun getFavoriteApps(): Set<String> {
        return getFavoriteAppsInternal()
    }
    
    fun setShowAllAppsMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(SHOW_ALL_APPS_KEY, enabled).apply()
    }
    
    fun isShowAllAppsMode(): Boolean {
        val favorites = getFavoriteApps()
        // If no favorites exist, default to showing all apps
        if (favorites.isEmpty()) {
            return true
        }
        // Otherwise, check saved preference (defaults to false = show favorites)
        return sharedPreferences.getBoolean(SHOW_ALL_APPS_KEY, false)
    }
    
    fun filterApps(allApps: List<android.content.pm.ResolveInfo>, showAll: Boolean): List<android.content.pm.ResolveInfo> {
        val favorites = getFavoriteApps()
        
        return if (showAll || favorites.isEmpty()) {
            // Show all apps if showAll is true or if no favorites are set
            allApps
        } else {
            // Show only favorite apps - use parallel stream for better performance
            allApps.parallelStream()
                .filter { favorites.contains(it.activityInfo.packageName) }
                .collect(java.util.stream.Collectors.toList())
        }
    }
}
