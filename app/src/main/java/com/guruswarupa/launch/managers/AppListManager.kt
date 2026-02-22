package com.guruswarupa.launch.managers

import android.content.pm.ResolveInfo

import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.managers.FavoriteAppManager
import com.guruswarupa.launch.managers.HiddenAppManager
import com.guruswarupa.launch.core.CacheManager

/**
 * Manages app list filtering, sorting, and updates.
 * Centralizes all app list manipulation logic to reduce MainActivity complexity.
 */
class AppListManager(
    private val appDockManager: AppDockManager,
    private val favoriteAppManager: FavoriteAppManager,
    private val hiddenAppManager: HiddenAppManager?,
    private val cacheManager: CacheManager?
) {
    
    /**
     * Combined single-pass filter that applies all filtering (mode, hidden, favorites) in one iteration.
     * This avoids iterating the list multiple times.
     */
    fun filterAndPrepareApps(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        // Pre-compute favorites state once
        val currentShowAllMode = if (!workspaceMode) favoriteAppManager.isShowAllAppsMode() else true
        val favoriteApps = if (!workspaceMode && !currentShowAllMode) favoriteAppManager.getFavoriteApps() else null
        
        return apps.filter { app ->
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name
            
            // Allow SettingsActivity, but exclude MainActivity
            val isLauncherApp = packageName == "com.guruswarupa.launch"
            if (isLauncherApp && !(activityName.contains("SettingsActivity") && 
                                   !activityName.contains("MainActivity"))) {
                return@filter false
            }
            
            // Filter out hidden apps
            if (hiddenAppManager?.isAppHidden(packageName) == true) return@filter false
            
            // Focus mode filter
            if (focusMode && appDockManager.isAppHiddenInFocusMode(packageName)) return@filter false
            
            // Workspace mode filter
            if (workspaceMode && !appDockManager.isAppInActiveWorkspace(packageName)) return@filter false
            
            // Favorites filter (only when not in workspace mode and not showing all apps)
            if (favoriteApps != null && !favoriteApps.contains(packageName)) return@filter false
            
            true
        }
    }
    
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
     * Apps starting with numbers or '#' are placed at the end.
     */
    fun sortAppsAlphabetically(apps: List<ResolveInfo>): List<ResolveInfo> {
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        return apps.sortedBy {
            val label = metadataCache[it.activityInfo.packageName]?.label?.lowercase() 
                ?: it.activityInfo.packageName.lowercase()
            getSortKey(label)
        }
    }

    /**
     * Generates a sort key that puts numbers and '#' at the end.
     */
    fun getSortKey(label: String): String {
        if (label.isEmpty()) return label
        val firstChar = label[0]
        return if (firstChar.isDigit() || firstChar == '#') {
            "\uFFFF$label"
        } else {
            label
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
