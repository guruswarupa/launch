package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import android.util.Log

class FavoriteAppManager(private val sharedPreferences: SharedPreferences) {
    
    companion object {
        private const val FAVORITE_APPS_KEY = "favorite_apps"
        private const val TAG = "FavoriteAppManager"
    }
    
    
    private var favoritesCache: Set<String>? = null
    private var cacheValid = false
    
    private fun getFavoriteAppsInternal(): Set<String> {
        if (!cacheValid || favoritesCache == null) {
            try {
                favoritesCache = sharedPreferences.getStringSet(FAVORITE_APPS_KEY, emptySet()) ?: emptySet()
            } catch (e: ClassCastException) {
                Log.e(TAG, "Data corruption: $FAVORITE_APPS_KEY is not a Set. Attempting recovery.", e)
                
                val stringValue = try { sharedPreferences.getString(FAVORITE_APPS_KEY, null) } catch (_: Exception) { null }
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
                    remove(FAVORITE_APPS_KEY)
                    putStringSet(FAVORITE_APPS_KEY, recoveredSet)
                }
                favoritesCache = recoveredSet
            }
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
