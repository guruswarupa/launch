package com.guruswarupa.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.Executors

/**
 * Manages caching of app lists and metadata for performance optimization
 */
class CacheManager(
    private val context: Context,
    private val packageManager: PackageManager,
    private val backgroundExecutor: java.util.concurrent.ExecutorService
) {
    private val CACHE_DURATION = 300000L // 5 minutes
    
    private val appListCacheFile: File
    private val appListCacheTimeFile: File
    private val appMetadataCacheFile: File
    private val appListVersionFile: File
    
    private val appMetadataCache = mutableMapOf<String, AppMetadata>()
    private var cachedAppListVersion: String? = null
    
    init {
        appListCacheFile = File(context.cacheDir, "app_list_cache.dat")
        appListCacheTimeFile = File(context.cacheDir, "app_list_cache_time.txt")
        appMetadataCacheFile = File(context.cacheDir, "app_metadata_cache.dat")
        appListVersionFile = File(context.cacheDir, "app_list_version.txt")
    }
    
    /**
     * Get app list version based on installed packages
     */
    fun getAppListVersion(): String {
        return try {
            val packages = packageManager.getInstalledPackages(0)
                .map { it.packageName }
                .sorted()
                .joinToString("")
            packages.hashCode().toString()
        } catch (e: Exception) {
            Log.e("CacheManager", "Error getting app list version", e)
            System.currentTimeMillis().toString()
        }
    }
    
    /**
     * Check if cache is valid (exists and not expired)
     */
    fun isCacheValid(): Boolean {
        if (!appListCacheFile.exists()) return false
        
        val cacheAge = try {
            val cachedTime = appListCacheTimeFile.readText().toLongOrNull() ?: 0L
            System.currentTimeMillis() - cachedTime
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
        
        return cacheAge < CACHE_DURATION
    }
    
    /**
     * Check if cached version matches current version
     */
    fun isVersionCurrent(): Boolean {
        val currentVersion = getAppListVersion()
        val cachedVersion = try {
            if (appListVersionFile.exists()) {
                appListVersionFile.readText().trim()
            } else null
        } catch (e: Exception) {
            null
        }
        return currentVersion == cachedVersion
    }
    
    /**
     * Load app list from persistent cache
     */
    fun loadAppListFromCache(): List<ResolveInfo> {
        return try {
            if (!appListCacheFile.exists()) return emptyList()
            
            val cacheData = appListCacheFile.readText().lines()
            val apps = mutableListOf<ResolveInfo>()
            
            for (line in cacheData) {
                if (line.isBlank()) continue
                val parts = line.split("|")
                if (parts.size == 2) {
                    val packageName = parts[0]
                    val activityName = parts[1]
                    
                    try {
                        val intent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setPackage(packageName)
                            setClassName(packageName, activityName)
                        }
                        val resolveInfo = packageManager.resolveActivity(intent, 0)
                        if (resolveInfo != null) {
                            apps.add(resolveInfo)
                        }
                    } catch (e: Exception) {
                        // App may have been uninstalled, skip
                    }
                }
            }
            
            apps
        } catch (e: Exception) {
            Log.e("CacheManager", "Error loading app list cache", e)
            emptyList()
        }
    }
    
    /**
     * Save app list to persistent cache
     */
    fun saveAppListToCache(apps: List<ResolveInfo>) {
        backgroundExecutor.execute {
            try {
                val cacheData = apps.map { 
                    "${it.activityInfo.packageName}|${it.activityInfo.name}" 
                }
                appListCacheFile.writeText(cacheData.joinToString("\n"))
                appListCacheTimeFile.writeText(System.currentTimeMillis().toString())
                
                // Save version
                val version = getAppListVersion()
                appListVersionFile.writeText(version)
                cachedAppListVersion = version
            } catch (e: Exception) {
                Log.e("CacheManager", "Error saving app list cache", e)
            }
        }
    }
    
    /**
     * Load app metadata from persistent cache
     */
    @Suppress("UNCHECKED_CAST")
    fun loadAppMetadataFromCache(): Map<String, AppMetadata> {
        return try {
            if (!appMetadataCacheFile.exists()) return emptyMap()
            
            val inputStream = FileInputStream(appMetadataCacheFile)
            val objectInputStream = ObjectInputStream(inputStream)
            val metadata = objectInputStream.readObject() as? Map<String, AppMetadata> ?: emptyMap()
            objectInputStream.close()
            inputStream.close()
            
            appMetadataCache.clear()
            appMetadataCache.putAll(metadata)
            
            metadata
        } catch (e: Exception) {
            Log.e("CacheManager", "Error loading app metadata cache", e)
            emptyMap()
        }
    }
    
    /**
     * Save app metadata to persistent cache
     */
    fun saveAppMetadataToCache(metadata: Map<String, AppMetadata>) {
        backgroundExecutor.execute {
            try {
                val outputStream = FileOutputStream(appMetadataCacheFile)
                val objectOutputStream = ObjectOutputStream(outputStream)
                objectOutputStream.writeObject(metadata)
                objectOutputStream.close()
                outputStream.close()
            } catch (e: Exception) {
                Log.e("CacheManager", "Error saving app metadata cache", e)
            }
        }
    }
    
    /**
     * Pre-load app metadata (labels) in background
     */
    fun preloadAppMetadata(apps: List<ResolveInfo>) {
        backgroundExecutor.execute {
            try {
                val metadata = mutableMapOf<String, AppMetadata>()
                val currentTime = System.currentTimeMillis()
                
                apps.forEach { app ->
                    val packageName = app.activityInfo.packageName
                    try {
                        val cached = appMetadataCache[packageName]
                        if (cached != null && (currentTime - cached.lastUpdated) < CACHE_DURATION) {
                            metadata[packageName] = cached
                        } else {
                            val label = app.loadLabel(packageManager).toString()
                            metadata[packageName] = AppMetadata(
                                packageName = packageName,
                                activityName = app.activityInfo.name,
                                label = label,
                                lastUpdated = currentTime
                            )
                        }
                    } catch (e: Exception) {
                        // Handle errors silently
                    }
                }
                
                appMetadataCache.putAll(metadata)
                saveAppMetadataToCache(appMetadataCache)
            } catch (e: Exception) {
                Log.e("CacheManager", "Error preloading app metadata", e)
            }
        }
    }
    
    /**
     * Clear all cache files
     */
    fun clearCache() {
        try {
            if (appListCacheFile.exists()) appListCacheFile.delete()
            if (appListCacheTimeFile.exists()) appListCacheTimeFile.delete()
            if (appMetadataCacheFile.exists()) appMetadataCacheFile.delete()
            if (appListVersionFile.exists()) appListVersionFile.delete()
            appMetadataCache.clear()
            cachedAppListVersion = null
        } catch (e: Exception) {
            Log.e("CacheManager", "Error clearing cache", e)
        }
    }
    
    /**
     * Get metadata cache (for read access)
     */
    fun getMetadataCache(): Map<String, AppMetadata> = appMetadataCache
    
    /**
     * Update metadata cache
     */
    fun updateMetadataCache(packageName: String, metadata: AppMetadata) {
        appMetadataCache[packageName] = metadata
    }
    
    /**
     * Remove metadata for a package
     */
    fun removeMetadata(packageName: String) {
        appMetadataCache.remove(packageName)
    }
}
