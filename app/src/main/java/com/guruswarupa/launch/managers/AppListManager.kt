package com.guruswarupa.launch.managers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Process
import android.os.UserManager
import com.guruswarupa.launch.core.CacheManager
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.util.Locale
import javax.inject.Inject

@ActivityScoped
class AppListManager @Inject constructor(
    @ActivityContext private val context: Context,
    private val favoriteAppManager: FavoriteAppManager,
    private val hiddenAppManager: HiddenAppManager,
    private val cacheManager: CacheManager,
    private val workspaceManager: WorkspaceManager,
    private val workProfileManager: WorkProfileManager
) {
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()
    private var appDockManager: AppDockManager? = null

    fun attach(appDockManager: AppDockManager) {
        this.appDockManager = appDockManager
    }

    private fun requireAppDockManager(): AppDockManager = requireNotNull(appDockManager) { "AppDockManager not attached" }

    fun filterAndPrepareApps(
        apps: List<ResolveInfo>,
        focusMode: Boolean,
        workspaceMode: Boolean
    ): List<ResolveInfo> {
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        val dockManager = requireAppDockManager()
        
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
            
            val isWorkApp = app.preferredOrder != mainUserSerial

            // Focus Mode takes absolute priority over Workspace and Work Profile toggle
            if (focusMode) {
                return@filter if (isWorkApp) true else !dockManager.isAppHiddenInFocusMode(packageName)
            }
            
            if (isWorkProfileEnabled) {
                if (!isWorkApp) return@filter false
            } else {
                if (isWorkApp) return@filter false
                
                if (workspaceMode && !dockManager.isAppInActiveWorkspace(packageName)) {
                    return@filter false
                }
            }
            
            true
        }
    }
    
    fun sortAppsAlphabetically(apps: List<ResolveInfo>): List<ResolveInfo> {
        val favorites = favoriteAppManager.getFavoriteApps()
        val dockManager = requireAppDockManager()
        val focusMode = dockManager.getCurrentMode()
        val workspaceMode = dockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        val showFavoritesAtTop = !focusMode && !workspaceMode && !isWorkProfileEnabled
        val comparator = compareBy<ResolveInfo>(
            { if (isInternalApp(it)) 1 else 0 },
            { getSortKey(getDisplayLabel(it).lowercase(Locale.ROOT)) },
            { it.activityInfo.packageName },
            { it.activityInfo.name }
        )

        val favoriteApps = if (showFavoritesAtTop) apps.filter { favorites.contains(it.activityInfo.packageName) } else emptyList()
        val sortedFavorites = favoriteApps.sortedWith(comparator)
        val sortedAllApps = apps.sortedWith(comparator)
        return sortedFavorites + sortedAllApps
    }

    fun getSortKey(label: String): String {
        if (label.isEmpty()) return label
        val firstChar = label[0]
        return if (!firstChar.isLetter()) {
            "\uFFFF$label"
        } else {
            label
        }
    }

    fun addSeparators(apps: List<ResolveInfo>): List<ResolveInfo> {
        if (apps.isEmpty()) return apps
        
        val favorites = favoriteAppManager.getFavoriteApps()
        val result = mutableListOf<ResolveInfo>()
        val dockManager = requireAppDockManager()
        val focusMode = dockManager.getCurrentMode()
        val workspaceMode = dockManager.isWorkspaceModeActive()
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
            val isFavorite = favorites.contains(packageName)
            val isInternal = isInternalApp(app)
            
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
                val label = getDisplayLabel(app)
                val currentLetter = if (label.isNotEmpty()) label[0].uppercaseChar() else '#'
                val effectiveLetter = if (currentLetter.isLetter()) currentLetter else '#'
                
                if (lastLetter != null && lastLetter != effectiveLetter) {
                    result.add(createSeparatorInfo("letter_separator_$effectiveLetter"))
                }
                lastLetter = effectiveLetter
            } else if (isFavorite && (isAfterFavorites || !showFavoritesSection)) {
                val label = getDisplayLabel(app)
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
    
    fun getFocusMode(): Boolean = requireAppDockManager().getCurrentMode()
    fun getWorkspaceMode(): Boolean = requireAppDockManager().isWorkspaceModeActive()

    private fun isInternalApp(app: ResolveInfo): Boolean {
        val packageName = app.activityInfo.packageName
        val activityName = app.activityInfo.name
        return packageName == "com.guruswarupa.launch" &&
            (activityName.contains("SettingsActivity") || activityName.contains("EncryptedVaultActivity"))
    }

    private fun getDisplayLabel(app: ResolveInfo): String {
        val packageName = app.activityInfo.packageName
        if (WebAppManager.isWebAppPackage(packageName)) {
            return app.activityInfo.name.ifBlank { packageName }
        }

        val cacheKey = "${packageName}|${app.preferredOrder}"
        val cachedLabel = cacheManager?.getMetadataCache()?.get(cacheKey)?.label
        if (!cachedLabel.isNullOrBlank()) {
            return cachedLabel
        }

        val activityName = app.activityInfo.name
        return when {
            activityName.isNotBlank() && packageName.startsWith("launcher_") -> activityName
            // Never call loadLabel() - return packageName as fallback
            // Labels should be pre-populated by AppListLoader in background
            else -> packageName
        }
    }
}
