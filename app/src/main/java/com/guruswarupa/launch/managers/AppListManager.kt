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
            
            // Allow internal activities (Settings, Vault), but exclude MainActivity
            val isLauncherApp = packageName == "com.guruswarupa.launch"
            val isAllowedInternalActivity = isLauncherApp && (activityName.contains("SettingsActivity") || 
                                              activityName.contains("EncryptedVaultActivity"))
            
            if (isLauncherApp && !isAllowedInternalActivity) return@filter false
            
            // If it's a settings or vault activity, we ALWAYS show it (bypass focus/workspace/hidden filters)
            // EXCEPT in focus mode, where we hide settings to minimize distractions
            if (isAllowedInternalActivity) {
                if (focusMode && activityName.contains("SettingsActivity")) return@filter false
                return@filter true
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
            
            // Allow internal activities (Settings, Vault), but exclude MainActivity
            val isLauncherApp = packageName == "com.guruswarupa.launch"
            val isAllowedInternalActivity = isLauncherApp && (activityName.contains("SettingsActivity") || 
                                              activityName.contains("EncryptedVaultActivity"))
            
            if (isLauncherApp && !isAllowedInternalActivity) return@filter false
            
            // If it's a settings or vault activity, we ALWAYS show it
            // EXCEPT in focus mode, where we hide settings
            if (isAllowedInternalActivity) {
                if (focusMode && activityName.contains("SettingsActivity")) return@filter false
                return@filter true
            }
            
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
     * Favorites are shown BOTH at the top AND in their alphabetical positions.
     * Internal Launcher apps (Settings, Vault) are placed at the absolute bottom.
     * Apps starting with numbers or '#' are placed at the end of the alphabetical section.
     */
    fun sortAppsAlphabetically(apps: List<ResolveInfo>): List<ResolveInfo> {
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        val favorites = favoriteAppManager.getFavoriteApps()
        
        // Get all favorites that exist in the current app list
        val favoriteApps = apps.filter { app ->
            favorites.contains(app.activityInfo.packageName)
        }
        
        // Sort favorites alphabetically among themselves
        val sortedFavorites = favoriteApps.sortedWith(compareBy<ResolveInfo> { app ->
            val label = metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                ?: app.activityInfo.packageName.lowercase()
            getSortKey(label)
        })
        
        // Sort ALL apps (including favorites) alphabetically, with internal apps at the end
        val sortedAllApps = apps.sortedWith(compareBy<ResolveInfo> { app ->
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name
            val isInternal = packageName == "com.guruswarupa.launch" && 
                           (activityName.contains("SettingsActivity") || activityName.contains("EncryptedVaultActivity"))
            
            // Priority: 0 for normal apps, 1 for internal apps (at the end)
            if (isInternal) 1 else 0
        }.thenBy { app ->
            // Second priority: Alphabetical/Numbers
            val label = metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                ?: app.activityInfo.packageName.lowercase()
            getSortKey(label)
        })
        
        // Combine: favorites first, then all apps (so favorites appear twice - at top and in alphabetical order)
        return sortedFavorites + sortedAllApps
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
     * Favorites are shown at the top and also in their alphabetical positions.
     * Settings and Vault shortcuts are added at the very end.
     */
    fun addSeparators(apps: List<ResolveInfo>, isGridMode: Boolean): List<ResolveInfo> {
        if (apps.isEmpty()) return apps
        
        val favorites = favoriteAppManager.getFavoriteApps()
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        val result = mutableListOf<ResolveInfo>()
        
        // Check if we have favorites
        var hasFavorites = false
        for (app in apps) {
            if (favorites.contains(app.activityInfo.packageName)) {
                hasFavorites = true
                break
            }
        }
        
        var lastLetter: Char? = null
        var isAfterFavorites = false
        var processedFirstFavoriteSection = false
        var addedFavoritesEndSeparator = false
        
        for (app in apps) {
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name
            val isFavorite = favorites.contains(packageName)
            val isInternal = packageName == "com.guruswarupa.launch" && 
                           (activityName.contains("SettingsActivity") || activityName.contains("EncryptedVaultActivity"))
            
            // Add favorites separator before first favorite app
            if (hasFavorites && isFavorite && !processedFirstFavoriteSection) {
                result.add(createSeparatorInfo("favorites_separator"))
                processedFirstFavoriteSection = true
            }
            
            // Transition from favorites to normal apps - add separator after favorites
            if (hasFavorites && !isFavorite && !isAfterFavorites && processedFirstFavoriteSection && !addedFavoritesEndSeparator) {
                // Add a separator to mark the end of favorites section
                result.add(createSeparatorInfo("favorites_end_separator"))
                addedFavoritesEndSeparator = true
                // Reset lastLetter so we add a separator for the first non-favorite app
                lastLetter = null
                isAfterFavorites = true
            }
            
            // Between normal apps with different starting letters
            // Skip separators for internal apps shown at the end
            if (!isFavorite && !isInternal) {
                val label = metadataCache[packageName]?.label ?: packageName
                val currentLetter = if (label.isNotEmpty()) label[0].uppercaseChar() else '#'
                val effectiveLetter = if (currentLetter.isLetter()) currentLetter else '#'
                
                if (lastLetter != null && lastLetter != effectiveLetter) {
                    result.add(createSeparatorInfo("letter_separator_$effectiveLetter"))
                }
                lastLetter = effectiveLetter
            } else if (isFavorite && isAfterFavorites) {
                // This is a favorite appearing in its alphabetical position
                // Add a letter separator if needed
                val label = metadataCache[packageName]?.label ?: packageName
                val currentLetter = if (label.isNotEmpty()) label[0].uppercaseChar() else '#'
                val effectiveLetter = if (currentLetter.isLetter()) currentLetter else '#'
                
                if (lastLetter != null && lastLetter != effectiveLetter) {
                    result.add(createSeparatorInfo("letter_separator_$effectiveLetter"))
                }
                lastLetter = effectiveLetter
            } else if (isInternal && lastLetter != null) {
                // When we hit internal apps at the end, ensure we don't add more letter separators
                // We might want one final separator for system apps
                if (lastLetter != '⚙') {
                    result.add(createSeparatorInfo("letter_separator_SYSTEM"))
                    lastLetter = '⚙'
                }
            }
            
            result.add(app)
        }
        
        // Add Settings and Vault shortcuts at the very end
        result.add(createLauncherShortcut("launcher_settings_shortcut"))
        result.add(createLauncherShortcut("launcher_vault_shortcut"))
        
        return result
    }

    private fun createSeparatorInfo(id: String): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = "com.guruswarupa.launch.SEPARATOR"
        ri.activityInfo.name = id
        return ri
    }
    
    private fun createLauncherShortcut(shortcutType: String): ResolveInfo {
        val ri = ResolveInfo()
        ri.activityInfo = ActivityInfo()
        ri.activityInfo.packageName = shortcutType
        // Use a descriptive name that will be shown as the label
        ri.activityInfo.name = when (shortcutType) {
            "launcher_settings_shortcut" -> "Launch Settings"
            "launcher_vault_shortcut" -> "Launch Vault"
            else -> shortcutType
        }
        // Mark it as a special shortcut type
        ri.activityInfo.applicationInfo = android.content.pm.ApplicationInfo().apply {
            packageName = shortcutType
        }
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
