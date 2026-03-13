package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit

class FavoriteAppManager(private val sharedPreferences: SharedPreferences) {
    
    companion object {
        private const val FAVORITE_APPS_KEY = "favorite_apps"
    }
    
    // Cache favorites to avoid repeated SharedPreferences reads
    private var favoritesCache: Set<String>? = null
    private var cacheValid = false
    
    private fun getFavoriteAppsInternal(): Set<String> {
        if (!cacheValid || favoritesCache == null) {
            favoritesCache = sharedPreferences.getStringSet(FAVORITE_APPS_KEY, emptySet()) ?: emptySet()
            cacheValid = true
        }
        return favoritesCache ?: emptySet()
    }
    
    private fun invalidateCache() {
        cacheValid = false
        favoritesCache = null
    }
    
    fun addFavoriteApp(packageName: String) {
        val favorites = getFavoriteAppsInternal().toMutableSet()
        favorites.add(packageName)
        sharedPreferences.edit { putStringSet(FAVORITE_APPS_KEY, favorites) }
        invalidateCache()
    }
    
    fun removeFavoriteApp(packageName: String) {
        val favorites = getFavoriteAppsInternal().toMutableSet()
        favorites.remove(packageName)
        sharedPreferences.edit { putStringSet(FAVORITE_APPS_KEY, favorites) }
        invalidateCache()
    }
    
    fun isFavoriteApp(packageName: String): Boolean {
        return getFavoriteAppsInternal().contains(packageName)
    }
    
    fun getFavoriteApps(): Set<String> {
        return getFavoriteAppsInternal()
    }
}
