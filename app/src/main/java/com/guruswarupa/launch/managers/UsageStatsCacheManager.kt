package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import java.util.concurrent.Executor





class UsageStatsCacheManager(
    private val sharedPreferences: SharedPreferences,
    private val backgroundExecutor: Executor
) {
    private val usageStatsCache = mutableMapOf<String, Int>()
    
    


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
                onComplete?.invoke(usageStatsCache)
            } catch (_: Exception) {
                onComplete?.invoke(usageStatsCache)
            }
        }
    }
}
