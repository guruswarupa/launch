package com.guruswarupa.launch.managers

import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo

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
     * Combined single-pass filter that applies all filtering (mode, hidden) in one iteration.
     * Favorites are no longer filtered out; they are shown at the top.
     */
    fun filterAndPrepareApps(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
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
     * Note: This is now a no-op as we show all apps.
     */
    fun applyFavoritesFilter(
        apps: List<ResolveInfo>,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        return apps
    }
    
    /**
     * Sorts apps alphabetically by name using metadata cache when available.
     * Favorites are placed at the very top.
     * Apps starting with numbers or '#' are placed at the end of the alphabetical section.
     */
    fun sortAppsAlphabetically(apps: List<ResolveInfo>): List<ResolveInfo> {
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        val favorites = favoriteAppManager.getFavoriteApps()
        
        return apps.sortedWith(compareBy<ResolveInfo> { app ->
            // First priority: Favorites (0 for favorite, 1 for rest)
            if (favorites.contains(app.activityInfo.packageName)) 0 else 1
        }.thenBy { app ->
            // Second priority: Alphabetical/Numbers
            val label = metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                ?: app.activityInfo.packageName.lowercase()
            getSortKey(label)
        })
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
     * Injects separator ResolveInfo objects into the list.
     * Separates favorites from normal apps and normal apps by starting letter.
     */
    fun addSeparators(apps: List<ResolveInfo>, isGridMode: Boolean): List<ResolveInfo> {
        if (apps.isEmpty()) return apps
        
        val favorites = favoriteAppManager.getFavoriteApps()
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        val result = mutableListOf<ResolveInfo>()
        
        var hasFavorites = false
        for (app in apps) {
            if (favorites.contains(app.activityInfo.packageName)) {
                hasFavorites = true
                break
            }
        }
        
        var lastLetter: Char? = null
        var isAfterFavorites = false
        
        for (app in apps) {
            val packageName = app.activityInfo.packageName
            val isFavorite = favorites.contains(packageName)
            
            // Transition from favorites to normal apps
            if (hasFavorites && !isFavorite && !isAfterFavorites) {
                result.add(createSeparatorInfo("favorites_separator"))
                isAfterFavorites = true
            }
            
            // Between normal apps with different starting letters
            if (!isFavorite) {
                val label = metadataCache[packageName]?.label ?: packageName
                val currentLetter = if (label.isNotEmpty()) label[0].uppercaseChar() else '#'
                val effectiveLetter = if (currentLetter.isLetter()) currentLetter else '#'
                
                if (lastLetter != null && lastLetter != effectiveLetter) {
                    result.add(createSeparatorInfo("letter_separator_$effectiveLetter"))
                }
                lastLetter = effectiveLetter
            }
            
            result.add(app)
        }
        
        return result
    }

    private fun createSeparatorInfo(id: String): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = "com.guruswarupa.launch.SEPARATOR"
        ri.activityInfo.name = id
        return ri
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
