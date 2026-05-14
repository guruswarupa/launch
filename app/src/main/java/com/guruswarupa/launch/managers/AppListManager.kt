package com.guruswarupa.launch.managers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.SharedPreferences
import android.os.Process
import android.os.UserManager
import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.models.Constants
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
    private val workProfileManager: WorkProfileManager,
    private val sharedPreferences: SharedPreferences
) {
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mainUserSerial = userManager.getSerialNumberForUser(Process.myUserHandle()).toInt()
    private var appDockManager: AppDockManager? = null

    fun attach(appDockManager: AppDockManager) {
        this.appDockManager = appDockManager
    }

    private fun requireAppDockManager(): AppDockManager = requireNotNull(appDockManager) { "AppDockManager not attached" }
    
    fun getFavoriteApps(): Set<String> {
        return favoriteAppManager.getFavoriteApps()
    }

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
    
    fun sortAppsAlphabetically(apps: List<ResolveInfo>, showOnlyFavorites: Boolean = false): List<ResolveInfo> {
        val dockManager = requireAppDockManager()
        val focusMode = dockManager.getCurrentMode()
        val workspaceMode = dockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        val comparator = compareBy<ResolveInfo>(
            { if (isInternalApp(it)) 1 else 0 },
            { getSortKey(getDisplayLabel(it).lowercase(Locale.ROOT)) },
            { it.activityInfo.packageName },
            { it.activityInfo.name }
        )

        // When focus mode or workspace mode is active, ignore favorites filter
        // These modes have their own filtered app lists
        if (focusMode || workspaceMode || isWorkProfileEnabled) {
            return apps.sortedWith(comparator)
        }
        
        // When showing only favorites, filter to favorites only
        // When showing all apps, don't show favorites section - just show all apps
        if (showOnlyFavorites) {
            val favorites = favoriteAppManager.getFavoriteApps()
            val favoriteApps = apps.filter { favorites.contains(it.activityInfo.packageName) }
            return favoriteApps.sortedWith(comparator)
        } else {
            return apps.sortedWith(comparator)
        }
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

    fun addSeparators(apps: List<ResolveInfo>, showOnlyFavorites: Boolean = false): List<ResolveInfo> {
        if (apps.isEmpty()) return apps
        
        val result = mutableListOf<ResolveInfo>()
        val dockManager = requireAppDockManager()
        val focusMode = dockManager.getCurrentMode()
        val workspaceMode = dockManager.isWorkspaceModeActive()
        val isWorkProfileEnabled = workProfileManager.isWorkProfileEnabled()
        
        // Don't show favorites section when showing all apps or when focus/workspace mode is active
        val showFavoritesSection = showOnlyFavorites && !focusMode && !workspaceMode && !isWorkProfileEnabled
        
        // Add transparent spacer at the top of all apps list to improve scroll-to-top trigger
        // Only add when showing all apps, not in focus/workspace mode, AND favorites exist
        if (!showOnlyFavorites && !focusMode && !workspaceMode && !isWorkProfileEnabled) {
            val favorites = favoriteAppManager.getFavoriteApps()
            if (favorites.isNotEmpty()) {
                // Only add top spacers if there are favorites to scroll back to
                for (i in 0 until 4) {
                    result.add(createSeparatorInfo("all_apps_top_spacer_$i"))
                }
            }
        }
        
        var lastLetter: Char? = null
        
        for (app in apps) {
            val packageName = app.activityInfo.packageName
            val isInternal = isInternalApp(app)
            
            if (!isInternal) {
                // Don't add letter separators - apps flow continuously
            } else if (lastLetter != null) {
                if (lastLetter != '⚙') {
                    result.add(createSeparatorInfo("letter_separator_SYSTEM"))
                    lastLetter = '⚙'
                }
            }
            
            result.add(app)
        }
        
        // Add transparent spacer below favorites to improve scroll-to-bottom trigger
        // Only add when showing favorites and not in focus/workspace mode
        // Dynamic spacer count: starts at 13 for 1 app, reduces by 1 for each additional app
        // Adds extra spacers when top widget is visible to compensate for reduced screen space
        if (showOnlyFavorites && !focusMode && !workspaceMode && !isWorkProfileEnabled) {
            val favorites = favoriteAppManager.getFavoriteApps()
            val favoriteCount = favorites.size
            
            // Check if top widget is visible
            val isTopWidgetVisible = sharedPreferences.getBoolean(Constants.Prefs.TOP_WIDGET_ENABLED, true)
            
            // Calculate spacer count: 
            // With widget: starts at 10 for 1 app, reduce by 1 for each additional app
            // Without widget: starts at 13 for 1 app, reduce by 1 for each additional app
            // Minimum 4 spacers to ensure some scrollable area
            val baseCount = if (isTopWidgetVisible) 10 else 13
            val spacerCount = maxOf(4, baseCount - (favoriteCount - 1).coerceAtLeast(0))
            
            for (i in 0 until spacerCount) {
                result.add(createSeparatorInfo("favorites_bottom_spacer_$i"))
            }
            
            if (result.isNotEmpty()) {
                result.add(createSeparatorInfo("bottom_system_separator"))
            }
        }
        
        // Add launcher shortcuts (always show except when showing only favorites in normal mode)
        if (!showOnlyFavorites || focusMode || workspaceMode || isWorkProfileEnabled) {
            result.add(createLauncherShortcut("launcher_settings_shortcut"))
            result.add(createLauncherShortcut("launcher_vault_shortcut"))
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
    fun isWorkProfileModeEnabled(): Boolean = workProfileManager.isWorkProfileEnabled()
    fun isWorkProfileApp(app: ResolveInfo): Boolean = app.preferredOrder != mainUserSerial

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
