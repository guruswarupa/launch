package com.guruswarupa.launch.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import com.guruswarupa.launch.models.AppMetadata
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream




class CacheManager(
    context: Context,
    private val packageManager: PackageManager,
    private val backgroundExecutor: java.util.concurrent.ExecutorService
) {
    companion object {
        private const val CACHE_DURATION = 300000L 
    }

    private val appListCacheFile: File = File(context.cacheDir, "app_list_cache.dat")
    private val appListCacheTimeFile: File = File(context.cacheDir, "app_list_cache_time.txt")
    private val appMetadataCacheFile: File = File(context.cacheDir, "app_metadata_cache.dat")
    private val appListVersionFile: File = File(context.cacheDir, "app_list_version.txt")

    private val appMetadataCache = mutableMapOf<String, AppMetadata>()
    private var cachedAppListVersion: String? = null
    @Volatile
    private var metadataLoaded = false
    private val mainHandler = Handler(Looper.getMainLooper())

    



    fun getAppListVersion(): String {
        return try {
            val packages = packageManager.getInstalledPackages(0)
            
            var maxUpdateTime = 0L
            for (pkg in packages) {
                if (pkg.lastUpdateTime > maxUpdateTime) {
                    maxUpdateTime = pkg.lastUpdateTime
                }
            }
            "${packages.size}_$maxUpdateTime"
        } catch (_: Exception) {
            System.currentTimeMillis().toString()
        }
    }


    


    fun isVersionCurrent(): Boolean {
        val currentVersion = getAppListVersion()
        val cachedVersion = try {
            if (appListVersionFile.exists()) {
                appListVersionFile.readText().trim()
            } else null
        } catch (_: Exception) {
            null
        }
        return currentVersion == cachedVersion
    }


    


    fun isCacheValid(): Boolean {
        if (!appListCacheFile.exists()) return false

        val cacheAge = try {
            val cachedTime = appListCacheTimeFile.readText().toLongOrNull() ?: 0L
            System.currentTimeMillis() - cachedTime
        } catch (_: Exception) {
            Long.MAX_VALUE
        }

        return cacheAge < CACHE_DURATION
    }

    


    fun loadAppListFromCache(): List<ResolveInfo> {
        return try {
            if (!appListCacheFile.exists()) return emptyList()

            val cacheData = appListCacheFile.readText().lines().filter { it.isNotBlank() }
            if (cacheData.isEmpty()) return emptyList()

            
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val allApps = packageManager.queryIntentActivities(mainIntent, 0)
            
            
            val appMap = allApps.associateBy { "${it.activityInfo.packageName}|${it.activityInfo.name}" }

            val apps = mutableListOf<ResolveInfo>()
            for (line in cacheData) {
                appMap[line]?.let { apps.add(it) }
            }

            
            
            if (apps.isEmpty() && allApps.isNotEmpty()) {
                return allApps
            }

            apps
        } catch (_: Exception) {
            emptyList()
        }
    }

    


    fun saveAppListToCache(apps: List<ResolveInfo>) {
        backgroundExecutor.execute {
            try {
                val cacheData = apps.map {
                    "${it.activityInfo.packageName}|${it.activityInfo.name}"
                }
                appListCacheFile.writeText(cacheData.joinToString("\n"))
                appListCacheTimeFile.writeText(System.currentTimeMillis().toString())

                
                val version = getAppListVersion()
                appListVersionFile.writeText(version)
                cachedAppListVersion = version
            } catch (_: Exception) {
            }
        }
    }

    


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
            metadataLoaded = true

            metadata
        } catch (_: Exception) {
            metadataLoaded = true
            emptyMap()
        }
    }

    



    fun loadAppMetadataFromCacheAsync(onLoaded: (() -> Unit)? = null) {
        if (metadataLoaded) {
            onLoaded?.invoke()
            return
        }
        backgroundExecutor.execute {
            loadAppMetadataFromCache()
            onLoaded?.let { callback ->
                mainHandler.post { callback() }
            }
        }
    }

    


    fun saveAppMetadataToCache(metadata: Map<String, AppMetadata>) {
        backgroundExecutor.execute {
            try {
                val outputStream = FileOutputStream(appMetadataCacheFile)
                val objectOutputStream = ObjectOutputStream(outputStream)
                objectOutputStream.writeObject(metadata)
                objectOutputStream.close()
                outputStream.close()
            } catch (_: Exception) {
            }
        }
    }

    


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
                    } catch (_: Exception) {
                        
                    }
                }
                
                appMetadataCache.putAll(metadata)
                saveAppMetadataToCache(appMetadataCache)
            } catch (_: Exception) {
            }
        }
    }
    
    


    fun clearCache() {
        try {
            if (appListCacheFile.exists()) appListCacheFile.delete()
            if (appListCacheTimeFile.exists()) appListCacheTimeFile.delete()
            if (appMetadataCacheFile.exists()) appMetadataCacheFile.delete()
            if (appListVersionFile.exists()) appListVersionFile.delete()
            appMetadataCache.clear()
            cachedAppListVersion = null
        } catch (_: Exception) {
        }
    }
    
    


    fun getMetadataCache(): Map<String, AppMetadata> = appMetadataCache
    
    


    fun updateMetadataCache(packageName: String, metadata: AppMetadata) {
        appMetadataCache[packageName] = metadata
    }
    
    


    fun removeMetadata(packageName: String) {
        appMetadataCache.remove(packageName)
    }
}
