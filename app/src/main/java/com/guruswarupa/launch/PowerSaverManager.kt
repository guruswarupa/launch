package com.guruswarupa.launch

import android.graphics.Color
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Manages power saver mode: widget visibility, background updates, and UI state
 */
class PowerSaverManager(
    private val activity: MainActivity,
    private val handler: Handler,
    private val adapter: AppAdapter,
    private val recyclerView: RecyclerView,
    private val wallpaperManagerHelper: WallpaperManagerHelper,
    private val weeklyUsageGraph: WeeklyUsageGraphView,
    private val todoRecyclerView: RecyclerView,
    private val timeDateManager: TimeDateManager,
    private val usageStatsDisplayManager: UsageStatsDisplayManager,
    private val onUpdateBattery: () -> Unit,
    private val onUpdateUsage: () -> Unit,
    private val onSetGesturesEnabled: (Boolean) -> Unit
) {
    private var isInPowerSaverMode = false
    
    fun applyPowerSaverMode(isEnabled: Boolean) {
        isInPowerSaverMode = isEnabled

        if (isEnabled) {
            // Fast operations first - update UI immediately
            setPitchBlackBackground()
            hideNonEssentialWidgets()
            
            // Disable drawers and gestures in battery saver mode
            disableDrawersAndGestures()
            
            // Stop or slow down background updates to save battery
            stopBackgroundUpdates()
            
            // Reduce animation duration (if supported)
            activity.window?.setWindowAnimations(android.R.style.Animation_Toast)
            
            // Refresh adapter efficiently (only visible items)
            refreshAdapter()
            
            // Set FLAG_SECURE to hide app preview in recent apps (Overview) on Samsung/Android
            // This prevents the system from taking a screenshot for the preview
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            
        } else {
            // Remove FLAG_SECURE when disabling power saver mode
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            
            // Enable drawers and gestures
            enableDrawersAndGestures()
            
            // Fast operations first - show widgets immediately
            showNonEssentialWidgets()
            
            // Restore background and reload wallpaper asynchronously (non-blocking)
            handler.post {
                restoreOriginalBackground()
                // Reload wallpaper in background to avoid blocking
                wallpaperManagerHelper.setWallpaperBackground()
            }
            
            // Resume background updates (already non-blocking)
            resumeBackgroundUpdates()
            
            // Restore normal animations
            activity.window?.setWindowAnimations(0)
            
            // Refresh adapter efficiently
            refreshAdapter()
        }
    }

    private fun disableDrawersAndGestures() {
        activity.drawerLayout.let { drawer ->
            drawer.closeDrawers()
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }
        onSetGesturesEnabled(false)
    }

    private fun enableDrawersAndGestures() {
        activity.drawerLayout.let { drawer ->
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        }
        onSetGesturesEnabled(true)
    }

    private fun refreshAdapter() {
        handler.post {
            val layoutManager = recyclerView.layoutManager
            if (layoutManager is LinearLayoutManager) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                    adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                } else {
                    adapter.notifyDataSetChanged()
                }
            } else {
                adapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun stopBackgroundUpdates() {
        // Stop time/date updates (will be handled by TimeDateManager)
        timeDateManager.setPowerSaverMode(true)
    }
    
    private fun resumeBackgroundUpdates() {
        // Resume time/date updates
        timeDateManager.setPowerSaverMode(false)
        
        // Resume battery and usage updates
        onUpdateBattery()
        onUpdateUsage()
    }

    private fun hideNonEssentialWidgets() {
        // Don't modify views if activity is finishing or stopped
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        
        // Hide weather, battery, usage stats, todo, finance widgets
        activity.findViewById<View>(R.id.weather_widget)?.visibility = View.GONE
        activity.findViewById<android.widget.TextView>(R.id.battery_percentage)?.visibility = View.GONE
        activity.findViewById<android.widget.TextView>(R.id.screen_time)?.visibility = View.GONE
        activity.findViewById<LinearLayout>(R.id.finance_widget)?.visibility = View.GONE
        weeklyUsageGraph.visibility = View.GONE
        // Hide the entire weekly usage widget container
        activity.findViewById<View>(R.id.weekly_usage_widget)?.visibility = View.GONE

        // Hide the wallpaper background in power saver mode
        activity.findViewById<ImageView>(R.id.wallpaper_background)?.visibility = View.GONE
        activity.findViewById<ImageView>(R.id.drawer_wallpaper_background)?.visibility = View.GONE
        activity.findViewById<ImageView>(R.id.right_drawer_wallpaper)?.visibility = View.GONE
        
        // Hide the clock container in the right drawer
        activity.findViewById<View>(R.id.right_drawer_time)?.parent?.let {
            if (it is View) it.visibility = View.GONE
        }

        // Hide the todo widget (the LinearLayout containing todo list)
        todoRecyclerView.parent?.let { parent ->
            if (parent is View) {
                parent.visibility = View.GONE
            }
        }
    }

    private fun showNonEssentialWidgets() {
        // Don't modify views if activity is finishing or stopped
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        
        // Fast operations - show widgets immediately (non-blocking)
        activity.findViewById<View>(R.id.weather_widget)?.visibility = View.VISIBLE
        activity.findViewById<android.widget.TextView>(R.id.battery_percentage)?.visibility = View.VISIBLE
        activity.findViewById<android.widget.TextView>(R.id.screen_time)?.visibility = View.VISIBLE
        activity.findViewById<LinearLayout>(R.id.finance_widget)?.visibility = View.VISIBLE
        weeklyUsageGraph.visibility = View.VISIBLE
        // Defer loading expensive weekly usage data - load it asynchronously after UI is shown
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed && weeklyUsageGraph.visibility == View.VISIBLE) {
                usageStatsDisplayManager.loadWeeklyUsageData()
            }
        }, 300) // Small delay to let UI render first
        // Show the entire weekly usage widget container
        activity.findViewById<View>(R.id.weekly_usage_widget)?.visibility = View.VISIBLE

        // Show the wallpaper background when power saver mode is disabled
        activity.findViewById<ImageView>(R.id.wallpaper_background)?.visibility = View.VISIBLE
        activity.findViewById<ImageView>(R.id.drawer_wallpaper_background)?.visibility = View.VISIBLE
        activity.findViewById<ImageView>(R.id.right_drawer_wallpaper)?.visibility = View.VISIBLE
        
        // Show the clock container in the right drawer
        activity.findViewById<View>(R.id.right_drawer_time)?.parent?.let {
            if (it is View) it.visibility = View.VISIBLE
        }

        // Show the todo widget (the LinearLayout containing todo list)
        todoRecyclerView.parent?.let { parent ->
            if (parent is View) {
                parent.visibility = View.VISIBLE
            }
        }
    }

    private fun setPitchBlackBackground() {
        if (activity.isFinishing || activity.isDestroyed) return
        
        // Set root backgrounds to pure black
        activity.findViewById<View>(R.id.drawer_layout)?.setBackgroundColor(Color.BLACK)
        activity.findViewById<View>(R.id.main_content)?.setBackgroundColor(Color.BLACK)
        activity.findViewById<View>(android.R.id.content)?.setBackgroundColor(Color.BLACK)
        
        // Remove widget container background and elevation for pitch black look
        val topWidget = activity.findViewById<View>(R.id.top_widget_container)
        topWidget?.background = null
        topWidget?.elevation = 0f
        
        // Set search box to be completely transparent/minimal
        val searchBox = activity.findViewById<EditText>(R.id.search_box)
        searchBox?.setBackgroundColor(Color.TRANSPARENT)
        
        // Drawer content background
        activity.findViewById<View>(R.id.widgets_drawer)?.setBackgroundColor(Color.BLACK)
        activity.findViewById<View>(R.id.wallpaper_drawer)?.setBackgroundColor(Color.BLACK)
        val settingsHeader = activity.findViewById<View>(R.id.widget_settings_header)
        settingsHeader?.background = null
        settingsHeader?.elevation = 0f

        // Ensure status bar and navigation bar are black or transparent over black
        activity.window?.statusBarColor = Color.BLACK
        activity.window?.navigationBarColor = Color.BLACK
    }

    private fun restoreOriginalBackground() {
        if (activity.isFinishing || activity.isDestroyed) return
        
        val backgroundColor = ContextCompat.getColor(activity, R.color.background)
        activity.findViewById<View>(R.id.drawer_layout)?.setBackgroundColor(backgroundColor)
        activity.findViewById<View>(R.id.main_content)?.setBackgroundColor(backgroundColor)
        activity.findViewById<View>(android.R.id.content)?.setBackgroundResource(R.drawable.wallpaper_background)
        
        // Restore widget container background and elevation
        val topWidget = activity.findViewById<View>(R.id.top_widget_container)
        topWidget?.setBackgroundResource(R.drawable.widget_background)
        topWidget?.elevation = activity.resources.getDimension(R.dimen.widget_elevation)
        
        // Restore search box background
        val searchBox = activity.findViewById<EditText>(R.id.search_box)
        searchBox?.setBackgroundResource(R.drawable.search_box_transparent_bg)
        
        // Restore drawer content background
        activity.findViewById<View>(R.id.widgets_drawer)?.setBackgroundColor(backgroundColor)
        activity.findViewById<View>(R.id.wallpaper_drawer)?.setBackgroundColor(Color.TRANSPARENT)
        val settingsHeader = activity.findViewById<View>(R.id.widget_settings_header)
        settingsHeader?.setBackgroundResource(R.drawable.widget_background)
        settingsHeader?.elevation = activity.resources.getDimension(R.dimen.widget_elevation)

        // Restore transparent system bars (SystemBarManager will handle icons)
        activity.window?.statusBarColor = Color.TRANSPARENT
        activity.window?.navigationBarColor = Color.TRANSPARENT
    }
}
