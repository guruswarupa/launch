package com.guruswarupa.launch.managers

import android.content.Context
import android.content.pm.ResolveInfo
import android.content.pm.ActivityInfo
import android.os.Process
import android.os.UserManager
import android.util.Log
import com.guruswarupa.launch.core.CacheManager

class AppListManager(
    private val context: Context,
    private val appDockManager: AppDockManager,
    private val favoriteAppManager: FavoriteAppManager,
    private val hiddenAppManager: HiddenAppManager?,
    private val cacheManager: CacheManager?,
    private val workspaceManager: WorkspaceManager,
    private val workProfileManager: WorkProfileManager
) {
    companion object {
        private const val TAG = "AppListManager"
    }

    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()

    fun filterAndPrepareApps(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        return apps.filter { app ->
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name
            
            val isLauncherApp = packageName == "com.guruswarupa.launch"
            val isAllowedInternalActivity = isLauncherApp && (activityName.contains("SettingsActivity") || 
                                              activityName.contains("EncryptedVaultActivity"))
            
            if (isLauncherApp && !isAllowedInternalActivity) return@filter false
            
            if (isAllowedInternalActivity) {
                return@filter !(focusMode && activityName.contains("SettingsActivity"))
            }
            
            if (hiddenAppManager?.isAppHidden(packageName) == true) return@filter false
            
            // Work Profile Filtering - Strict separation
            val isWorkApp = app.preferredOrder != mainUserSerial
            
            if (isWorkProfileEnabled) {
                // When "Work Profile" is toggled ON, ONLY show work profile apps
                if (!isWorkApp) return@filter false
                // In Focus Mode, we show ALL work apps (no filtering for work apps)
            } else {
                // When "Work Profile" is toggled OFF, ONLY show personal apps
                if (isWorkApp) return@filter false
                
                // Focus Mode Logic: Filter personal apps
                if (focusMode && appDockManager.isAppHiddenInFocusMode(packageName)) {
                    return@filter false
                }
                
                // Workspace Logic: Filter personal apps
                if (workspaceMode && !appDockManager.isAppInActiveWorkspace(packageName)) {
                    return@filter false
                }
            }
            
            true
        }
    }
    
    fun sortAppsAlphabetically(apps: List<ResolveInfo>): List<ResolveInfo> {
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        val favorites = favoriteAppManager.getFavoriteApps()
        val focusMode = appDockManager.getCurrentMode()
        val workspaceMode = appDockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        // Show favorites at top only in normal personal mode
        val showFavoritesAtTop = !focusMode && !workspaceMode && !isWorkProfileEnabled
        
        val favoriteApps = if (showFavoritesAtTop) {
            apps.filter { app ->
                favorites.contains(app.activityInfo.packageName)
            }
        } else {
            emptyList()
        }
        
        val sortedFavorites = favoriteApps.sortedWith(compareBy { app ->
            val label = metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                ?: app.activityInfo.packageName.lowercase()
            getSortKey(label)
        })
        
        val sortedAllApps = apps.sortedWith(compareBy<ResolveInfo> { app ->
            val packageName = app.activityInfo.packageName
            val activityName = app.activityInfo.name
            val isInternal = packageName == "com.guruswarupa.launch" && 
                           (activityName.contains("SettingsActivity") || activityName.contains("EncryptedVaultActivity"))
            
            if (isInternal) 1 else 0
        }.thenBy { app ->
            val label = metadataCache[app.activityInfo.packageName]?.label?.lowercase() 
                ?: app.activityInfo.packageName.lowercase()
            getSortKey(label)
        })
        
        return sortedFavorites + sortedAllApps
    }

    fun getSortKey(label: String): String {
        if (label.isEmpty()) return label
        val firstChar = label[0]
        return if (firstChar.isDigit() || firstChar == '#') {
            "\uFFFF$label"
        } else {
            label
        }
    }

    fun addSeparators(apps: List<ResolveInfo>): List<ResolveInfo> {
        if (apps.isEmpty()) return apps
        
        val favorites = favoriteAppManager.getFavoriteApps()
        val metadataCache = cacheManager?.getMetadataCache() ?: emptyMap()
        val result = mutableListOf<ResolveInfo>()
        val focusMode = appDockManager.getCurrentMode()
        val workspaceMode = appDockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        val showFavoritesSection = !focusMode && !workspaceMode && !isWorkProfileEnabled
        
        var hasFavorites = false
        if (showFavoritesSection) {
            for (app in apps) {
                if (favorites.contains(app.activityInfo.packageName)) {
                    hasFavorites = true
                    break
                }
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
            
            if (hasFavorites && isFavorite && !processedFirstFavoriteSection) {
                result.add(createSeparatorInfo("favorites_separator"))
                processedFirstFavoriteSection = true
            }
            
            if (hasFavorites && !isFavorite && !isAfterFavorites && processedFirstFavoriteSection && !addedFavoritesEndSeparator) {
                result.add(createSeparatorInfo("favorites_end_separator"))
                addedFavoritesEndSeparator = true
                lastLetter = null
                isAfterFavorites = true
            }
            
            if (!isFavorite && !isInternal) {
                val label = metadataCache[packageName]?.label ?: packageName
                val currentLetter = if (label.isNotEmpty()) label[0].uppercaseChar() else '#'
                val effectiveLetter = if (currentLetter.isLetter()) currentLetter else '#'
                
                if (lastLetter != null && lastLetter != effectiveLetter) {
                    result.add(createSeparatorInfo("letter_separator_$effectiveLetter"))
                }
                lastLetter = effectiveLetter
            } else if (isFavorite && (isAfterFavorites || !showFavoritesSection)) {
                val label = metadataCache[packageName]?.label ?: packageName
                val currentLetter = if (label.isNotEmpty()) label[0].uppercaseChar() else '#'
                val effectiveLetter = if (currentLetter.isLetter()) currentLetter else '#'
                
                if (lastLetter != null && lastLetter != effectiveLetter) {
                    result.add(createSeparatorInfo("letter_separator_$effectiveLetter"))
                }
                lastLetter = effectiveLetter
            } else if (isInternal && lastLetter != null) {
                if (lastLetter != '⚙') {
                    result.add(createSeparatorInfo("letter_separator_SYSTEM"))
                    lastLetter = '⚙'
                }
            }
            
            result.add(app)
        }
        
        if (result.isNotEmpty()) {
            result.add(createSeparatorInfo("bottom_system_separator"))
        }
        
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
        
        ri.activityInfo.name = when (shortcutType) {
            "launcher_settings_shortcut" -> "Launch Settings"
            "launcher_vault_shortcut" -> "Launch Vault"
            else -> shortcutType
        }
        
        ri.activityInfo.applicationInfo = android.content.pm.ApplicationInfo().apply {
            packageName = shortcutType
        }
        return ri
    }
    
    fun getFocusMode(): Boolean = appDockManager.getCurrentMode()
    fun getWorkspaceMode(): Boolean = appDockManager.isWorkspaceModeActive()
}
