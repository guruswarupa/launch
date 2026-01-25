package com.guruswarupa.launch

import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.Executor

/**
 * Manages usage stats cache loading and storage.
 * Extracted from MainActivity to reduce complexity.
 */
class UsageStatsCacheManager(
    private val sharedPreferences: SharedPreferences,
    private val backgroundExecutor: Executor
) {
    private val usageStatsCache = mutableMapOf<String, Int>()
    
    /**
     * Load usage stats cache from SharedPreferences (batch read)
     */
    fun loadCache(onComplete: ((Map<String, Int>) -> Unit)? = null) {
        backgroundExecutor.execute {
            try {
                val allPrefs = sharedPreferences.all
                usageStatsCache.clear()
                for ((key, value) in allPrefs) {
                    if (key.startsWith("usage_") && value is Int) {
                        val packageName = key.removePrefix("usage_")
                        usageStatsCache[packageName] = value
                    }
                }
                Log.d("UsageStatsCacheManager", "Loaded ${usageStatsCache.size} usage stats into cache")
                onComplete?.invoke(usageStatsCache)
            } catch (e: Exception) {
                Log.e("UsageStatsCacheManager", "Error loading usage stats cache", e)
                onComplete?.invoke(usageStatsCache)
            }
        }
    }
    
    fun getCache(): Map<String, Int> = usageStatsCache.toMap()
    
    fun isCacheEmpty(): Boolean = usageStatsCache.isEmpty()
    
    fun clearCache() {
        usageStatsCache.clear()
    }
}
