package com.guruswarupa.launch

import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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
    private val onUpdateUsage: () -> Unit
) {
    private var isInPowerSaverMode = false
    
    fun applyPowerSaverMode(isEnabled: Boolean) {
        isInPowerSaverMode = isEnabled

        if (isEnabled) {
            // Fast operations first - update UI immediately
            setPitchBlackBackground()
            hideNonEssentialWidgets()
            
            // Stop or slow down background updates to save battery
            stopBackgroundUpdates()
            
            // Reduce animation duration (if supported)
            activity.window?.setWindowAnimations(android.R.style.Animation_Toast)
            
            // Refresh adapter efficiently (only visible items)
            // Use efficient update that only refreshes visible items
            handler.post {
                val layoutManager = recyclerView.layoutManager
                if (layoutManager is LinearLayoutManager) {
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                        adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                    }
                }
            }
            
        } else {
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
            
            // Refresh adapter efficiently with small delay to let UI render first
            handler.postDelayed({
                val layoutManager = recyclerView.layoutManager
                if (layoutManager is LinearLayoutManager) {
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                        adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                    } else {
                        // Fallback to full update if positions not available
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    adapter.notifyDataSetChanged()
                }
            }, 100) // Small delay to ensure UI is responsive
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
        // (Wallpaper will be reloaded asynchronously by setWallpaperBackground() called from applyPowerSaverMode)
        activity.findViewById<ImageView>(R.id.wallpaper_background)?.visibility = View.VISIBLE
        activity.findViewById<ImageView>(R.id.drawer_wallpaper_background)?.visibility = View.VISIBLE

        // Show the todo widget (the LinearLayout containing todo list)
        todoRecyclerView.parent?.let { parent ->
            if (parent is View) {
                parent.visibility = View.VISIBLE
            }
        }
    }

    private fun setPitchBlackBackground() {
        // Don't modify root view if activity is finishing or destroyed
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        activity.findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(android.graphics.Color.BLACK)
    }

    private fun restoreOriginalBackground() {
        // Don't modify root view if activity is finishing or destroyed
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        activity.findViewById<android.view.View>(android.R.id.content)?.setBackgroundResource(R.drawable.wallpaper_background)
    }
}
