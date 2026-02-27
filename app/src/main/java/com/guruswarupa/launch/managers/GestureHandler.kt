package com.guruswarupa.launch.managers

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

/**
 * Handles gestures for drawer opening and system gesture exclusion
 */
class GestureHandler(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val drawerLayout: DrawerLayout,
    private val mainContent: FrameLayout
) {
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isSwipeFromLeftEdge = false
    private var isSwipeFromRightEdge = false
    private var isGesturesEnabled = true
    
    private val edgeThresholdPx: Int
    private val minSwipeDistancePx: Int
    
    init {
        val density = activity.resources.displayMetrics.density
        edgeThresholdPx = (50 * density).toInt()  // Reduced from 100dp
        minSwipeDistancePx = (50 * density).toInt()  // Reduced from 100dp for faster response
    }
    
    /**
     * Enable or disable custom gestures
     */
    @Suppress("unused")
    fun setGesturesEnabled(enabled: Boolean) {
        isGesturesEnabled = enabled
        if (!enabled) {
            // Clear any active swipe state
            isSwipeFromLeftEdge = false
            isSwipeFromRightEdge = false
        }
    }
    
    /**
     * Setup gesture exclusion to allow drawer to open with system navigation gestures enabled
     */
    fun setupGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainContent.post {
                updateGestureExclusion()
            }
            
            ViewCompat.setOnApplyWindowInsetsListener(mainContent) { _, _ ->
                updateGestureExclusion()
                androidx.core.view.WindowInsetsCompat.CONSUMED
            }
        }
    }
    
    /**
     * Update gesture exclusion rects
     */
    fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainContent.post {
                val exclusionRects = if (isGesturesEnabled) {
                    listOf(
                        // Left side exclusion - Top half of the screen
                        Rect(0, 0, mainContent.width / 2, mainContent.height / 2),
                        // Right side exclusion - Top half of the screen
                        Rect(mainContent.width / 2, 0, mainContent.width, mainContent.height / 2)
                    )
                } else {
                    // No exclusion when gestures are disabled
                    emptyList()
                }
                ViewCompat.setSystemGestureExclusionRects(mainContent, exclusionRects)
            }
        }
    }
    
    /**
     * Update gesture exclusion to cover entire screen (for widget opening)
     */
    fun updateGestureExclusionForWidgetOpening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mainContent.post {
                val exclusionRects = listOf(
                    Rect(0, 0, mainContent.width, mainContent.height)
                )
                ViewCompat.setSystemGestureExclusionRects(mainContent, exclusionRects)
            }
        }
    }
    
    /**
     * Setup touch listener for drawer opening
     */
    @SuppressLint("ClickableViewAccessibility")
    fun setupTouchListener() {
        mainContent.setOnTouchListener { v, event ->
            if (!isGesturesEnabled) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    // Trigger drawer swipes from anywhere on the screen (left or right side)
                    val screenWidth = v.width
                    val isLeftSide = event.x < screenWidth / 2
                    isSwipeFromLeftEdge = isLeftSide && event.y < (v.height / 2)
                    isSwipeFromRightEdge = !isLeftSide && event.y < (v.height / 2)
                    isSwipeFromLeftEdge || isSwipeFromRightEdge
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSwipeFromLeftEdge) {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        // More responsive detection - reduced threshold from 10 to 5
                        if (deltaX > 5 && abs(deltaY) < abs(deltaX) * 0.9) {
                            true // Consume to prevent system gestures
                        } else {
                            isSwipeFromLeftEdge = false
                            false
                        }
                    } else if (isSwipeFromRightEdge) {
                        val deltaX = touchStartX - event.x
                        val deltaY = event.y - touchStartY
                        // More responsive detection - reduced threshold from 10 to 5
                        if (deltaX > 5 && abs(deltaY) < abs(deltaX) * 0.9) {
                            true // Consume to prevent system gestures
                        } else {
                            isSwipeFromRightEdge = false
                            false
                        }
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.performClick()
                    if (isSwipeFromLeftEdge) {
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        if (deltaX > minSwipeDistancePx && abs(deltaY) < abs(deltaX) * 0.9) {
                            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                                // Open drawer with faster animation
                                openDrawerWithFastAnimation(GravityCompat.START)
                            }
                        }
                        isSwipeFromLeftEdge = false
                        true
                    } else if (isSwipeFromRightEdge) {
                        val deltaX = touchStartX - event.x
                        val deltaY = event.y - touchStartY
                        if (deltaX > minSwipeDistancePx && abs(deltaY) < abs(deltaX) * 0.9) {
                            if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
                                // Open drawer with faster animation
                                openDrawerWithFastAnimation(GravityCompat.END)
                            }
                        }
                        isSwipeFromRightEdge = false
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    /**
     * Open drawer with optimized animation timing
     */
    private fun openDrawerWithFastAnimation(gravity: Int) {
        // Open drawer immediately
        drawerLayout.openDrawer(gravity)
        
        // The DrawerLayout's default animation is already quite fast
        // If there's still a delay, it might be from other parts of the app
        // The main optimizations we've made:
        // 1. Reduced swipe thresholds (50dp instead of 100dp)
        // 2. Reduced move detection threshold (5 instead of 10)
        // 3. Removed unnecessary delays
    }
}
