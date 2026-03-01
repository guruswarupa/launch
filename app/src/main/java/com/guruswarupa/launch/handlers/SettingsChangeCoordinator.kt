package com.guruswarupa.launch.handlers

import android.app.WallpaperManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.WidgetThemeManager

/**
 * Coordinates UI and service updates when settings or theme changes.
 * Holds references to the affected managers and handles the logic for applying those changes.
 */
class SettingsChangeCoordinator(
    private val activity: MainActivity,
    private val adapterProvider: () -> AppAdapter?,
    private val appDockManagerProvider: () -> AppDockManager?,
    private val widgetSetupManagerProvider: () -> WidgetSetupManager?,
    private val widgetThemeManagerProvider: () -> WidgetThemeManager?
) {
    /**
     * Applies theme-appropriate backgrounds to all widget containers based on current theme mode.
     */
    fun applyThemeBasedWidgetBackgrounds() {
        val widgetThemeManager = widgetThemeManagerProvider() ?: return
        val views = activity.views
        val appDockManager = appDockManagerProvider()
        
        widgetThemeManager.apply(
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            searchContainer = if (views.isSearchContainerInitialized()) views.searchContainer else null,
            voiceSearchButton = if (views.isVoiceSearchButtonInitialized()) views.voiceSearchButton else null,
            searchTypeButton = if (views.isSearchTypeButtonInitialized()) views.searchTypeButton else null,
            appDockManager = appDockManager
        )
    }

    /**
     * Handles updates when shared preferences change.
     */
    fun handleSettingsUpdate() {
        val sharedPreferences = activity.sharedPreferences
        val views = activity.views
        val adapter = adapterProvider()
        
        // Update display style if changed
        val viewPreference = sharedPreferences.getString("view_preference", "list") ?: "list"
        val newIsGridMode = viewPreference == "grid"
        val currentIsGridMode = if (views.isRecyclerViewInitialized()) views.recyclerView.layoutManager is GridLayoutManager else false
        
        if (newIsGridMode != currentIsGridMode && adapter != null) {
            // Update layout manager
            views.recyclerView.layoutManager = if (newIsGridMode) {
                GridLayoutManager(activity, 4)
            } else {
                LinearLayoutManager(activity)
            }
            
            // Optimization #5: Use single adapter and switch mode dynamically
            adapter.updateViewMode(newIsGridMode)
            
            // Only update AppSearchManager if data source significantly changed,
            // otherwise the shared metadata cache handles label updates.
            activity.updateAppSearchManager()
        }
        
        // Update fast scroller visibility
        activity.updateFastScrollerVisibility()

        // Update background services if preferences changed
        if (activity.isServiceManagerInitialized()) {
            activity.serviceManager.updateShakeDetectionService()
            activity.serviceManager.updateScreenDimmerService()
            activity.serviceManager.updateNightModeService()
            activity.serviceManager.updateFlipToDndService()
            activity.serviceManager.updateBackTapService()
        }
        
        // Force refresh hidden apps cache to ensure we have latest data
        if (activity.isHiddenAppManagerInitialized()) {
            activity.hiddenAppManager.forceRefresh()
        }
        
        // Always refresh apps to ensure latest data (hidden apps, favorites, etc.)
        if (activity.isAppListLoaderInitialized()) {
            activity.appListLoader.loadApps(forceRefresh = false)
        }
        
        if (activity.isFinanceWidgetManagerInitialized()) {
            try {
                activity.financeWidgetManager.updateDisplay() // Refresh finance display after reset
            } catch (_: Exception) {
                // Ignore if finance widget manager fails
            }
        }
        
        // Refresh wallpaper in case it was changed from settings
        if (activity.isWallpaperManagerHelperInitialized()) {
            activity.wallpaperManagerHelper.clearCache()
            activity.wallpaperManagerHelper.setWallpaperBackground(forceReload = true)
            
            // Update right drawer wallpaper as well
            try {
                val wallpaperManager = WallpaperManager.getInstance(activity)
                val drawable = wallpaperManager.drawable
                if (views.isRightDrawerWallpaperInitialized()) {
                    views.rightDrawerWallpaper.setImageDrawable(drawable)
                }
            } catch (_: Exception) {}
        }
    }
}
