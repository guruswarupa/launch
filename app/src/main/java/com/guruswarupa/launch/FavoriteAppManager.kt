package com.guruswarupa.launch

import android.content.SharedPreferences

class FavoriteAppManager(private val sharedPreferences: SharedPreferences) {
    private val FAVORITE_APPS_KEY = "favorite_apps"
    private val SHOW_ALL_APPS_KEY = "show_all_apps_mode"
    
    fun addFavoriteApp(packageName: String) {
        val favorites = getFavoriteApps().toMutableSet()
        favorites.add(packageName)
        sharedPreferences.edit().putStringSet(FAVORITE_APPS_KEY, favorites).apply()
    }
    
    fun removeFavoriteApp(packageName: String) {
        val favorites = getFavoriteApps().toMutableSet()
        favorites.remove(packageName)
        sharedPreferences.edit().putStringSet(FAVORITE_APPS_KEY, favorites).apply()
    }
    
    fun isFavoriteApp(packageName: String): Boolean {
        return getFavoriteApps().contains(packageName)
    }
    
    fun getFavoriteApps(): Set<String> {
        return sharedPreferences.getStringSet(FAVORITE_APPS_KEY, emptySet()) ?: emptySet()
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
            // Show only favorite apps
            allApps.filter { favorites.contains(it.activityInfo.packageName) }
        }
    }
}
