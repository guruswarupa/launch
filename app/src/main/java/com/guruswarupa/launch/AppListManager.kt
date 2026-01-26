package com.guruswarupa.launch

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import java.util.concurrent.Executor

/**
 * Manages app list filtering, sorting, and updates.
 * Centralizes all app list manipulation logic to reduce MainActivity complexity.
 */
class AppListManager(
    private val packageManager: PackageManager,
    private val appDockManager: AppDockManager,
    private val favoriteAppManager: FavoriteAppManager,
    private val hiddenAppManager: HiddenAppManager?,
    private val cacheManager: CacheManager?,
    private val backgroundExecutor: Executor
) {
    
    /**
     * Filters apps based on focus mode, workspace mode, hidden apps, and launcher exclusion.
     */
    fun filterAppsByMode(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        return apps.filter { app ->
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name
            
            // Allow SettingsActivity, but exclude MainActivity
            val isLauncherApp = packageName == "com.guruswarupa.launch"
            val isAllowedLauncherActivity = activityName.contains("SettingsActivity") && 
                                          !activityName.contains("MainActivity")
            
            val shouldInclude = if (isLauncherApp) {
                isAllowedLauncherActivity
            } else {
                true
            }
            
            shouldInclude &&
            // Filter out hidden apps (unless in workspace mode, where we might want to show them)
            !(hiddenAppManager?.isAppHidden(packageName) ?: false) &&
            (!focusMode || !appDockManager.isAppHiddenInFocusMode(packageName)) &&
            (!workspaceMode || appDockManager.isAppInActiveWorkspace(packageName))
        }
    }
    
    /**
     * Applies favorites filter if workspace mode is not active.
     */
    fun applyFavoritesFilter(
        apps: List<ResolveInfo>,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        return if (workspaceMode) {
            // Show all workspace apps, ignore favorites
            apps
        } else {
            // Always read fresh from manager to avoid stale state
            val currentShowAllMode = favoriteAppManager.isShowAllAppsMode()
            favoriteAppManager.filterApps(apps, currentShowAllMode)
        }
    }
    
    /**
     * Sorts apps alphabetically by name using metadata cache when available.
     */
    fun sortAppsAlphabetically(apps: List<ResolveInfo>): List<ResolveInfo> {
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        return apps.sortedBy {
            metadataCache[it.activityInfo.packageName]?.label?.lowercase() 
                ?: it.activityInfo.packageName.lowercase()
        }
    }
    
    /**
     * Filters and sorts apps in one operation.
     */
    fun filterAndSortApps(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        val filtered = filterAppsByMode(apps, focusMode, workspaceMode)
        val favoritesFiltered = applyFavoritesFilter(filtered, workspaceMode)
        return sortAppsAlphabetically(favoritesFiltered)
    }
    
    /**
     * Builds a label cache for apps in the background.
     */
    fun buildLabelCache(
        apps: List<ResolveInfo>,
        onComplete: (Map<String, String>) -> Unit
    ) {
        backgroundExecutor.execute {
            try {
                val labelCache = mutableMapOf<String, String>()
                apps.forEach { app ->
                    val packageName = app.activityInfo.packageName
                    if (!labelCache.containsKey(packageName)) {
                        try {
                            val label = app.loadLabel(packageManager).toString().lowercase()
                            labelCache[packageName] = label
                            
                            // Update metadata cache if available
                            cacheManager?.updateMetadataCache(
                                packageName,
                                AppMetadata(
                                    packageName = packageName,
                                    activityName = app.activityInfo.name,
                                    label = label,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        } catch (e: Exception) {
                            labelCache[packageName] = packageName.lowercase()
                        }
                    }
                }
                onComplete(labelCache)
            } catch (e: Exception) {
                Log.e("AppListManager", "Error building label cache", e)
                onComplete(emptyMap())
            }
        }
    }
    
    /**
     * Gets the current focus mode state.
     */
    fun getFocusMode(): Boolean = appDockManager.getCurrentMode()
    
    /**
     * Gets the current workspace mode state.
     */
    fun getWorkspaceMode(): Boolean = appDockManager.isWorkspaceModeActive()
}
