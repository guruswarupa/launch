package com.guruswarupa.launch.handlers

import android.graphics.Color
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.AppDockManager
import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.managers.TypographyManager

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
     * Applies background translucency to center and left pages based on saved preference.
     */
    fun applyBackgroundTranslucency() {
        val sharedPreferences = activity.sharedPreferences
        val views = activity.views
        val translucency = sharedPreferences.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val color = Color.argb(alpha, 0, 0, 0)
        
        if (activity.isViewsInitialized() && views.areTranslucencyOverlaysInitialized()) {
            views.backgroundTranslucencyOverlay.setBackgroundColor(color)
            views.widgetsDrawerTranslucencyOverlay.setBackgroundColor(color)
        }
    }

    /**
     * Handles updates when shared preferences change.
     */
    fun handleSettingsUpdate() {
        val sharedPreferences = activity.sharedPreferences
        val views = activity.views
        val adapter = adapterProvider()
        val use24HourClock = sharedPreferences.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)

        applyThemeBasedWidgetBackgrounds()
        applyBackgroundTranslucency()
        TypographyManager.applyToActivity(activity)
        views.fastScroller.refreshTypography(sharedPreferences)

        if (activity.isTimeDateManagerInitialized()) {
            activity.timeDateManager.setUse24HourFormat(use24HourClock)
        }
        
        // Update display style if changed
        val viewPreference = sharedPreferences.getString("view_preference", "list") ?: "list"
        val newIsGridMode = viewPreference == "grid"
        val desiredColumns = activity.getPreferredGridColumns()
        val currentIsGridMode = if (views.isRecyclerViewInitialized()) views.recyclerView.layoutManager is GridLayoutManager else false

        if (newIsGridMode != currentIsGridMode && adapter != null) {
            // Update layout manager
            views.recyclerView.layoutManager = if (newIsGridMode) {
                val gridLayoutManager = GridLayoutManager(activity, desiredColumns)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val viewType = adapter.getItemViewType(position)
                        return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR) {
                            desiredColumns
                        } else {
                            1
                        }
                    }
                }
                gridLayoutManager
            } else {
                LinearLayoutManager(activity)
            }
            
            // Optimization #5: Use single adapter and switch mode dynamically
            adapter.updateViewMode(newIsGridMode)
            
            // Only update AppSearchManager if data source significantly changed,
            // otherwise the shared metadata cache handles label updates.
            activity.updateAppSearchManager()
        } else if (newIsGridMode && currentIsGridMode) {
            val layoutManager = views.recyclerView.layoutManager as? GridLayoutManager
            if (layoutManager != null && layoutManager.spanCount != desiredColumns) {
                layoutManager.spanCount = desiredColumns
                // Update span size lookup for the new column count
                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val viewType = adapter?.getItemViewType(position)
                        return if (viewType == AppAdapter.VIEW_TYPE_SEPARATOR) {
                            desiredColumns
                        } else {
                            1
                        }
                    }
                }
                layoutManager.requestLayout()
                // Force a reload to ensure separators are correctly placed in the new grid
                if (activity.isAppListLoaderInitialized()) {
                    activity.appListLoader.loadApps(forceRefresh = false)
                }
            }
        }
        
        // Update icon shape style if changed
        val iconStyle = sharedPreferences.getString(Constants.Prefs.ICON_STYLE, "squircle") ?: "round"
        adapter?.updateIconStyle(iconStyle)
        
        // Update icon size if changed
        val iconSize = sharedPreferences.getInt(Constants.Prefs.ICON_SIZE, 40)
        adapter?.updateIconSize(iconSize)

        val grayscaleIconsEnabled = sharedPreferences.getBoolean(Constants.Prefs.GRAYSCALE_ICONS_ENABLED, false)
        adapter?.updateGrayscaleIcons(grayscaleIconsEnabled)
        
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
            activity.wallpaperManagerHelper.applyBlurToViews()
            activity.wallpaperManagerHelper.clearCache()
            activity.wallpaperManagerHelper.setWallpaperBackground(forceReload = true)
            
            activity.refreshRightDrawerWallpaper()
        }
    }
}
