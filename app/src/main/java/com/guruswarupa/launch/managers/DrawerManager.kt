package com.guruswarupa.launch.managers

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.handlers.ActivityInitializer
import com.guruswarupa.launch.handlers.NavigationManager

/**
 * Manages DrawerLayout setup and drawer-related events.
 */
class DrawerManager(
    private val activity: FragmentActivity,
    private val drawerLayout: DrawerLayout,
    private val gestureHandler: GestureHandler,
    private val usageStatsDisplayManager: UsageStatsDisplayManager,
    private val activityInitializer: ActivityInitializer,
    private val themeCheckCallback: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    lateinit var navigationManager: NavigationManager
        private set

    /**
     * Sets up the DrawerLayout, listeners, and back-pressed callback.
     */
    fun setup() {
        gestureHandler.setupTouchListener()
        gestureHandler.setupGestureExclusion()

        activityInitializer.setupDrawerLayout(drawerLayout)
        navigationManager = NavigationManager(drawerLayout, gestureHandler, handler)

        // Add drawer listener to check for theme changes when drawer opens
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                // Check for theme changes when drawer opens
                themeCheckCallback()
                // Refresh usage data when right drawer opens (asynchronously)
                if (drawerView.id == R.id.wallpaper_drawer) {
                    handler.post {
                        usageStatsDisplayManager.loadWeeklyUsageData()
                    }
                }
            }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Setup back pressed callback
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::navigationManager.isInitialized) {
                    navigationManager.handleBackPressed {
                        // If navigation manager says we can proceed with standard back
                        // but it's the home screen, we usually don't want to do anything
                    }
                }
            }
        })
    }
}
