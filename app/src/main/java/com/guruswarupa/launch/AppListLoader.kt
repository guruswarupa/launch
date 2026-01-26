package com.guruswarupa.launch

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

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
    private val CACHE_DURATION = 300000L // Cache for 5 minutes
    
    // Callbacks
    var onAppListUpdated: ((List<ResolveInfo>, List<ResolveInfo>) -> Unit)? = null
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
                    
                    val cachedFiltered = appListManager.filterAppsByMode(cachedApps, focusMode, workspaceMode)
                    val cachedFinalList = appListManager.applyFavoritesFilter(cachedFiltered, workspaceMode)
                    
                    if (cachedFinalList.isNotEmpty() && adapter != null) {
                        val sorted = appListManager.sortAppsAlphabetically(cachedFinalList)
                        
                        handler.post {
                            // Pass cachedApps (all apps) as second parameter, not cachedFiltered
                            onAppListUpdated?.invoke(sorted, cachedApps)
                        }
                        
                        // Verify version in background (non-blocking) - refresh if changed
                        safeExecute {
                            if (cacheManager != null && !cacheManager.isVersionCurrent()) {
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
                } catch (e: Exception) {
                    // Continue to load fresh apps
                }
            }
        }
        
        // STEP 0.5: Show in-memory cached app list if available (fallback)
        if (!forceRefresh && cachedUnsortedList != null && 
            (currentTime - lastCacheTime) < CACHE_DURATION && 
            fullAppList.isNotEmpty()) {
            try {
                val focusMode = appDockManager.getCurrentMode()
                val workspaceMode = appDockManager.isWorkspaceModeActive()
                
                val cachedFiltered = appListManager.filterAppsByMode(cachedUnsortedList!!, focusMode, workspaceMode)
                val cachedFinalList = appListManager.applyFavoritesFilter(cachedFiltered, workspaceMode)
                
                if (cachedFinalList.isNotEmpty() && adapter != null) {
                    val sorted = appListManager.sortAppsAlphabetically(cachedFinalList)
                    
                    handler.post {
                        // Pass cachedUnsortedList (all apps) as second parameter, not cachedFiltered
                        onAppListUpdated?.invoke(sorted, cachedUnsortedList!!)
                    }
                }
            } catch (e: Exception) {
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
                    (currentTime - lastCacheTime) < CACHE_DURATION) {
                    cachedUnsortedList!!
                } else {
                    // Reload from package manager - use flag 0 to get all launcher activities
                    val mainIntent = Intent(Intent.ACTION_MAIN, null)
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                    val list = packageManager.queryIntentActivities(mainIntent, 0)
                    
                    cachedUnsortedList = list
                    lastCacheTime = currentTime
                    
                    // Save to persistent cache in background (don't block)
                    cacheManager?.let { cm ->
                        safeExecute {
                            try {
                                cm.saveAppListToCache(list)
                                // Pre-load metadata in background
                                cm.preloadAppMetadata(list)
                            } catch (e: Exception) {
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
                    
                    // STEP 1: Fast filtering without expensive operations
                    val filteredApps = appListManager.filterAppsByMode(unsortedList, focusMode, workspaceMode).toMutableList()
                    val finalAppList = appListManager.applyFavoritesFilter(filteredApps, workspaceMode)
                    
                    // STEP 2: Sort using cached metadata for instant sorted display
                    // This ensures all apps are shown, just sorted using cache (fast)
                    val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
                    val initiallySorted = finalAppList.sortedBy { app ->
                        metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                            ?: app.activityInfo.packageName.lowercase()
                    }
                    
                    // Show sorted list immediately (using cache for sorting)
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) {
                            return@post
                        }
                        
                        // Show list immediately with cached sorting to prevent freeze
                        // Pass unsortedList (all apps) as second parameter, not filteredApps
                        onAppListUpdated?.invoke(initiallySorted, unsortedList)
                        
                        // Optimize: Update existing adapter instead of creating new one
                        if (adapter == null) {
                            recyclerView.layoutManager = if (isGridMode) {
                                GridLayoutManager(activity, 4)
                            } else {
                                LinearLayoutManager(activity)
                            }
                            onAdapterNeedsUpdate?.invoke(isGridMode)
                        }
                    }
                    
                    // Defer expensive operations to avoid blocking UI
                    handler.postDelayed({
                        if (activity.isFinishing || activity.isDestroyed) {
                            return@postDelayed
                        }
                        
                        // Update dock toggle icon (non-critical, can be deferred)
                        appDockManager.refreshFavoriteToggle()
                    }, 50) // Small delay to let UI render first
                    
                    // STEP 3: Refine sorting in background with fresh labels (after UI is shown)
                    // This improves sorting accuracy but doesn't block initial display
                    handler.postDelayed({
                        safeExecute {
                            try {
                                // Use cached metadata, load missing ones on-demand
                                val labelCache = mutableMapOf<String, String>()
                                val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
                                
                                // Load all labels (use cache when available)
                                filteredApps.forEach { app ->
                                    val packageName = app.activityInfo.packageName
                                    val cached = metadataCache[packageName]
                                    if (cached != null) {
                                        labelCache[packageName] = cached.label.lowercase()
                                    } else {
                                        // Load on-demand and cache
                                        try {
                                            val label = app.loadLabel(packageManager).toString().lowercase()
                                            labelCache[packageName] = label
                                            cacheManager?.updateMetadataCache(packageName, AppMetadata(
                                                packageName = packageName,
                                                activityName = app.activityInfo.name,
                                                label = label,
                                                lastUpdated = System.currentTimeMillis()
                                            ))
                                        } catch (e: Exception) {
                                            labelCache[packageName] = packageName.lowercase()
                                        }
                                    }
                                }
                                
                                // Final sort with all labels loaded (more accurate than cache-only sort)
                                val sortedApps = filteredApps.sortedBy { app ->
                                    labelCache[app.activityInfo.packageName] ?: app.activityInfo.packageName.lowercase()
                                }
                                
                                // Save updated metadata cache
                                cacheManager?.saveAppMetadataToCache(cacheManager.getMetadataCache())
                                
                                val currentWorkspaceMode = appListManager.getWorkspaceMode()
                                val sortedFinalList = appListManager.applyFavoritesFilter(sortedApps, currentWorkspaceMode)
                                
                                // Update UI with refined sort (only if different from initial)
                                handler.post {
                                    if (activity.isFinishing || activity.isDestroyed) {
                                        return@post
                                    }
                                    
                                    // Only update if the sort actually changed (avoid unnecessary updates)
                                    // Pass unsortedList (all apps) as second parameter, not sortedApps
                                    onAppListUpdated?.invoke(sortedFinalList, unsortedList)
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }, 200) // Small delay to let UI render first
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
        // Set visibility of search bar and voice search button based on focus mode
        if (!activity.isFinishing && !activity.isDestroyed) {
            val currentFocusMode = appDockManager.getCurrentMode()
            handler.post {
                if (currentFocusMode) {
                    searchBox.visibility = View.GONE
                    voiceSearchButton.visibility = View.GONE
                } else {
                    searchBox.visibility = View.VISIBLE
                    voiceSearchButton.visibility = View.VISIBLE
                }
            }
        }
    }
    
    fun clearCache() {
        cachedUnsortedList = null
        lastCacheTime = 0L
    }
}
