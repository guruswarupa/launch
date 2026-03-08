package com.guruswarupa.launch.managers

import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.handlers.ActivityInitializer
import com.guruswarupa.launch.handlers.NavigationManager

/**
 * Sets up pager-based screen navigation while preserving the existing initialization flow.
 */
class DrawerManager(
    private val activity: FragmentActivity,
    private val screenPagerManager: ScreenPagerManager,
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
        gestureHandler.setupGestureExclusion()
        activityInitializer.setupDrawerLayout()
        screenPagerManager.setup()
        navigationManager = NavigationManager(screenPagerManager, gestureHandler, handler)
        screenPagerManager.setOnPageChanged { page ->
            themeCheckCallback()
            if (page == ScreenPagerManager.Page.LEFT) {
                handler.post { usageStatsDisplayManager.loadWeeklyUsageData() }
            }
        }

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
