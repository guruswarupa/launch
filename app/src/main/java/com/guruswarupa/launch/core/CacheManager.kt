package com.guruswarupa.launch.core

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import com.guruswarupa.launch.di.BackgroundExecutor
import com.guruswarupa.launch.models.AppMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ExecutorService
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackgroundExecutor private val backgroundExecutor: ExecutorService
) {
    companion object {
        private const val CACHE_DURATION = 300000L 
    }

    private val appListCacheFile: File = File(context.cacheDir, "app_list_cache.dat")
    private val appListCacheTimeFile: File = File(context.cacheDir, "app_list_cache_time.txt")
    private val appMetadataCacheFile: File = File(context.cacheDir, "app_metadata_cache.dat")
    private val appListVersionFile: File = File(context.cacheDir, "app_list_version.txt")
    private val packageManager: PackageManager = context.packageManager

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

            // To support multiple profiles, we MUST query from LauncherApps
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            
            val allAppsMap = mutableMapOf<String, ResolveInfo>()
            
            for (user in launcherApps.profiles) {
                val serial = userManager.getSerialNumberForUser(user).toInt()
                val apps = launcherApps.getActivityList(null, user)
                for (app in apps) {
                    val resolveInfo = ResolveInfo()
                    resolveInfo.activityInfo = android.content.pm.ActivityInfo().apply {
                        packageName = app.componentName.packageName
                        name = app.componentName.className
                        applicationInfo = app.applicationInfo
                    }
                    resolveInfo.preferredOrder = serial
                    
                    val key = "${resolveInfo.activityInfo.packageName}|${resolveInfo.activityInfo.name}|$serial"
                    allAppsMap[key] = resolveInfo
                }
            }

            val apps = mutableListOf<ResolveInfo>()
            for (line in cacheData) {
                // Compatibility: handle old cache format without serial
                val key = if (line.count { it == '|' } == 1) {
                    // Try to find in any profile, preferring main user if ambiguous
                    var found: ResolveInfo? = null
                    val mainUserSerial = userManager.getSerialNumberForUser(android.os.Process.myUserHandle()).toInt()
                    
                    // First check main user
                    found = allAppsMap["$line|$mainUserSerial"]
                    
                    // If not found, search all profiles
                    if (found == null) {
                        found = allAppsMap.values.find { "${it.activityInfo.packageName}|${it.activityInfo.name}" == line }
                    }
                    
                    if (found != null) {
                        "${found.activityInfo.packageName}|${found.activityInfo.name}|${found.preferredOrder}"
                    } else line
                } else line
                
                allAppsMap[key]?.let { apps.add(it) }
            }

            if (apps.isEmpty() && allAppsMap.isNotEmpty()) {
                return allAppsMap.values.toList()
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
                    "${it.activityInfo.packageName}|${it.activityInfo.name}|${it.preferredOrder}"
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
