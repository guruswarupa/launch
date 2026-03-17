package com.guruswarupa.launch.handlers

import android.os.Handler
import android.os.Looper
import com.guruswarupa.launch.managers.GestureHandler
import com.guruswarupa.launch.managers.ScreenPagerManager

/**
 * Handles navigation and back press logic.
 * Extracted from MainActivity to reduce complexity.
 */
class NavigationManager(
    private val screenPagerManager: ScreenPagerManager,
    private val gestureHandler: GestureHandler,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var isBlockingBackGesture = false
    private val backGestureBlockDuration = 800L // Block back gestures for 800ms after widget picker returns

    /**
     * Handles back press - closes drawer if open, otherwise calls super.
     */
    fun handleBackPressed(superOnBackPressed: () -> Unit) {
        // Block back gesture if we're temporarily blocking it (e.g., after widget picker returns)
        if (isBlockingBackGesture) {
            return
        }

        val defaultPage = screenPagerManager.getDefaultPage()
        // If we're not on the default home page, return to it
        if (!screenPagerManager.isPageOpen(defaultPage)) {
            screenPagerManager.openDefaultHomePage(animated = true)
        } else {
            superOnBackPressed()
        }
    }

    /**
     * Temporarily blocks back gestures to prevent system back gesture from exiting launcher
     * when returning from widget picker or configuration activities.
     */
    fun blockBackGesturesTemporarily() {
        isBlockingBackGesture = true
        // Also update gesture exclusion to cover entire screen temporarily
        gestureHandler.updateGestureExclusionForWidgetOpening()

        // Reset the flag after the blocking duration
        handler.postDelayed({
            isBlockingBackGesture = false
            // Restore normal gesture exclusion
            gestureHandler.updateGestureExclusion()
        }, backGestureBlockDuration)
    }

    /**
     * Returns whether back gestures are currently blocked.
     */
    @Suppress("unused")
    fun isBlockingBackGesture(): Boolean = isBlockingBackGesture
}
