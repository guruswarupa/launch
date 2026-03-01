package com.guruswarupa.launch.managers

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.models.AppMetadata

/**
 * Handles loading and caching of app lists.
 * Extracted from MainActivity to reduce complexity.
 */
class AppListLoader(
    private val activity: MainActivity,
    private val packageManager: PackageManager,
    private val appListManager: AppListManager,
    private val appDockManager: AppDockManager,
    private val favoriteAppManager: FavoriteAppManager,
    private val cacheManager: CacheManager?,
    private val backgroundExecutor: Executor,
    private val handler: Handler,
    private val recyclerView: RecyclerView,
    private val searchBox: EditText,
    private val voiceSearchButton: ImageButton,
    private val sharedPreferences: android.content.SharedPreferences
) {
    private var cachedUnsortedList: List<ResolveInfo>? = null
    private var lastCacheTime = 0L
    private val cacheDuration = 300000L // Cache for 5 minutes
    
    // Callbacks: (appList, fullAppList, isFinal)
    var onAppListUpdated: ((List<ResolveInfo>, List<ResolveInfo>, Boolean) -> Unit)? = null
    var onAdapterNeedsUpdate: ((Boolean) -> Unit)? = null
    
    /**
     * Safely execute a task on the background executor.
     */
    private fun safeExecute(task: Runnable): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            return false
        }
        try {
            if ((backgroundExecutor as? java.util.concurrent.ExecutorService)?.isShutdown == true) {
                Log.w("AppListLoader", "Background executor is shut down, skipping task")
                return false
            }
            backgroundExecutor.execute(task)
            return true
        } catch (e: RejectedExecutionException) {
            Log.w("AppListLoader", "Task rejected by executor", e)
            return false
        }
    }

    /**
     * Loads apps using the state from MainActivity.
     * This is the preferred method to call from outside.
     */
    fun loadApps(forceRefresh: Boolean = false) {
        if (activity.isFinishing || activity.isDestroyed) return
        
        // Sync isShowAllAppsMode with the manager to ensure consistency
        activity.isShowAllAppsMode = favoriteAppManager.isShowAllAppsMode()
        
        val adapter = if (activity.isAdapterInitialized()) activity.adapter else null
        loadApps(forceRefresh, activity.fullAppList, activity.appList, adapter)
    }
    
    fun loadApps(forceRefresh: Boolean = false, fullAppList: MutableList<ResolveInfo>, appList: MutableList<ResolveInfo>, adapter: AppAdapter?) {
        // appDockManager is always initialized as it's a constructor parameter
        
        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"
        
        // STEP 0: If forceRefresh, skip ALL cache checks and go straight to loading
        val currentTime = System.currentTimeMillis()
        if (forceRefresh) {
            // Clear all caches immediately
            cachedUnsortedList = null
            cacheManager?.clearCache()
            // Skip to background loading - no cache checks
        } else if (cacheManager != null && cacheManager.isCacheValid()) {
            // STEP 0: Check persistent cache (only if not forceRefresh)
            val cachedApps = cacheManager.loadAppListFromCache()
            if (cachedApps.isNotEmpty()) {
                // Load metadata cache (fast deserialization)
                cacheManager.loadAppMetadataFromCache()
                
                // Show cached list immediately without blocking version check
                try {
                    val focusMode = appDockManager.getCurrentMode()
                    val workspaceMode = appDockManager.isWorkspaceModeActive()
                    
                    val cachedFinalList = appListManager.filterAndPrepareApps(cachedApps, focusMode, workspaceMode)
                    
                    if (cachedFinalList.isNotEmpty() && adapter != null) {
                        val sorted = appListManager.sortAppsAlphabetically(cachedFinalList)
                        
                        handler.post {
                            // Pass false as isFinal because version check is pending
                            onAppListUpdated?.invoke(sorted, cachedApps, false)
                        }
                        
                        // Verify version in background (non-blocking) - use list-based check to avoid re-querying PM
                        safeExecute {
                            if (!cacheManager.isVersionCurrentWithList(cachedApps)) {
                                handler.post {
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        loadApps(forceRefresh = false, fullAppList, appList, adapter) // Refresh without clearing cache
                                    }
                                }
                            }
                        }
                        
                        updateSearchVisibility()
                        return // Exit early - cached list shown, version check happens in background
                    }
                } catch (_: Exception) {
                    // Continue to load fresh apps
                }
            }
        }
        
        // STEP 0.5: Show in-memory cached app list if available (fallback)
        if (!forceRefresh && cachedUnsortedList != null && 
            (currentTime - lastCacheTime) < cacheDuration && 
            fullAppList.isNotEmpty()) {
            try {
                val focusMode = appDockManager.getCurrentMode()
                val workspaceMode = appDockManager.isWorkspaceModeActive()
                
                val cachedFinalList = appListManager.filterAndPrepareApps(cachedUnsortedList!!, focusMode, workspaceMode)
                
                if (cachedFinalList.isNotEmpty() && adapter != null) {
                    val sorted = appListManager.sortAppsAlphabetically(cachedFinalList)
                    
                    handler.post {
                        // Pass false as isFinal
                        onAppListUpdated?.invoke(sorted, cachedUnsortedList!!, false)
                    }
                }
            } catch (_: Exception) {
                // Continue to load fresh apps
            }
        }
        
        // CRITICAL: Show UI immediately even if empty to prevent freeze
        handler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                return@post
            }
            recyclerView.visibility = View.VISIBLE
            // Initialize adapter if needed
            if (adapter == null) {
                recyclerView.layoutManager = if (isGridMode) {
                    GridLayoutManager(activity, 4)
                } else {
                    LinearLayoutManager(activity)
                }
                onAdapterNeedsUpdate?.invoke(isGridMode)
            }
        }
        
        safeExecute {
            try {
                // Use cached list if available and not forcing refresh
                val unsortedList = if (!forceRefresh && 
                    cachedUnsortedList != null && 
                    (currentTime - lastCacheTime) < cacheDuration) {
                    cachedUnsortedList!!
                } else {
                    // Optimization #1: Use getInstalledApplications instead of queryIntentActivities
                    // This is significantly faster on most Android versions
                    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    val list = mutableListOf<ResolveInfo>()
                    
                    for (app in installedApps) {
                        try {
                            // Only include enabled apps with launch intents
                            if (!app.enabled) continue
                            
                            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                            if (launchIntent != null) {
                                val resolveInfo = ResolveInfo()
                                // Create a dummy ActivityInfo since we only need package and class name
                                val activityInfo = android.content.pm.ActivityInfo()
                                activityInfo.packageName = app.packageName
                                activityInfo.name = launchIntent.component?.className ?: ""
                                activityInfo.applicationInfo = app
                                resolveInfo.activityInfo = activityInfo
                                list.add(resolveInfo)
                            }
                        } catch (_: Exception) {
                            // Skip apps that fail to resolve launch intent
                        }
                    }
                    
                    cachedUnsortedList = list
                    lastCacheTime = currentTime
                    
                    // Save to persistent cache in background (don't block)
                    cacheManager?.let { cm ->
                        safeExecute {
                            try {
                                cm.saveAppListToCache(list)
                                // Pre-load metadata in background
                                cm.preloadAppMetadata(list)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    
                    list
                }
                
                if (unsortedList.isEmpty()) {
                    handler.post {
                        if (appList.isEmpty()) {
                            Toast.makeText(activity, "No apps found!", Toast.LENGTH_SHORT).show()
                        }
                        recyclerView.visibility = View.VISIBLE
                    }
                } else {
                    val focusMode = appListManager.getFocusMode()
                    val workspaceMode = appListManager.getWorkspaceMode()
                    
                    // STEP 1: Fast single-pass filtering without expensive operations
                    val finalAppList = appListManager.filterAndPrepareApps(unsortedList, focusMode, workspaceMode)
                    
                    // STEP 2: Sort using cached metadata for instant sorted display
                    // This ensures all apps are shown, just sorted using cache (fast)
                    val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
                    val initiallySorted = finalAppList.sortedBy { app ->
                        val label = metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                            ?: app.activityInfo.packageName.lowercase()
                        appListManager.getSortKey(label)
                    }
                    
                    // Show sorted list immediately (using cache for sorting)
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) {
                            return@post
                        }
                        
                        // Show list immediately with cached sorting to prevent freeze
                        onAppListUpdated?.invoke(initiallySorted, unsortedList, false)
                        
                        // Optimize: Use existing adapter instead of creating new one
                        if (adapter == null) {
                            onAdapterNeedsUpdate?.invoke(isGridMode)
                        } else if (recyclerView.adapter != adapter) {
                            recyclerView.adapter = adapter
                        }
                    }
                    
                    // Dock toggle refresh (use post instead of postDelayed to avoid artificial latency)
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) {
                            return@post
                        }
                        
                        // Update dock toggle icon (non-critical)
                        appDockManager.refreshFavoriteToggle()
                    }
                    
                    // STEP 3: Refine sorting in background with fresh labels (after UI is shown)
                    // Reduced delay from 200ms to 100ms to improve responsiveness
                    handler.postDelayed({
                        safeExecute {
                            try {
                                // Use cached metadata, load missing ones on-demand
                                val labelCache = mutableMapOf<String, String>()
                                val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
                                
                                // Load all labels (use cache when available)
                                finalAppList.forEach { app ->
                                    val packageName = app.activityInfo.packageName
                                    val cached = metadataCache[packageName]
                                    if (cached != null) {
                                        labelCache[packageName] = cached.label.lowercase()
                                    } else {
                                        // Load on-demand and cache
                                        try {
                                            val label = app.loadLabel(packageManager).toString().lowercase()
                                            labelCache[packageName] = label
                                            cacheManager?.updateMetadataCache(packageName,
                                                AppMetadata(
                                                    packageName = packageName,
                                                    activityName = app.activityInfo.name,
                                                    label = label,
                                                    lastUpdated = System.currentTimeMillis()
                                                )
                                            )
                                        } catch (_: Exception) {
                                            labelCache[packageName] = packageName.lowercase()
                                        }
                                    }
                                }
                                
                                // Final sort with all labels loaded (more accurate than cache-only sort)
                                val sortedApps = finalAppList.sortedBy { app ->
                                    val label = labelCache[app.activityInfo.packageName] ?: app.activityInfo.packageName.lowercase()
                                    appListManager.getSortKey(label)
                                }
                                
                                // Save updated metadata cache
                                cacheManager?.saveAppMetadataToCache(cacheManager.getMetadataCache())
                                
                                // Update UI with refined sort (only if different from initial)
                                handler.post {
                                    if (activity.isFinishing || activity.isDestroyed) {
                                        return@post
                                    }
                                    
                                    // Mark as final update
                                    onAppListUpdated?.invoke(sortedApps, unsortedList, true)
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }, 100)
                }
            } catch (e: Exception) {
                handler.post {
                    if (activity.isFinishing || activity.isDestroyed) {
                        return@post
                    }
                    
                    // If app list is empty, try to reload after a delay
                    if (appList.isEmpty() && !forceRefresh) {
                        Log.w("AppListLoader", "App list is empty after error, retrying loadApps...")
                        handler.postDelayed({ loadApps(forceRefresh = true, fullAppList, appList, adapter) }, 500)
                    }
                    
                    if (appList.isEmpty()) {
                        Toast.makeText(activity, "Error loading apps: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
        
        updateSearchVisibility()
    }
    
    private fun updateSearchVisibility() {
        if (!activity.isFinishing && !activity.isDestroyed) {
            handler.post {
                searchBox.visibility = View.VISIBLE
                voiceSearchButton.visibility = View.VISIBLE
            }
        }
    }
    
    fun clearCache() {
        cachedUnsortedList = null
        lastCacheTime = 0L
    }
}
