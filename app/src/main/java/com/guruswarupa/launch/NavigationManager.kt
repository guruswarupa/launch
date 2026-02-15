package com.guruswarupa.launch

import android.os.Handler
import android.os.Looper
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

/**
 * Handles navigation and back press logic.
 * Extracted from MainActivity to reduce complexity.
 */
class NavigationManager(
    private val drawerLayout: DrawerLayout,
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

        // Close drawer if it's open, otherwise handle back button normally
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
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
