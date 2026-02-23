package com.guruswarupa.launch.utils

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.Window
import androidx.core.graphics.ColorUtils

/**
 * Utility class for applying blur effects to system bars
 */
object BlurUtils {
    
    /**
     * Apply blur effect to status bar only (not the entire screen)
     * @param activity The activity to apply blur to
     * @param radius The blur radius (default: 15f)
     */
    fun applyBlurToStatusBar(activity: Activity, radius: Float = 15f) {
        // Create a solid blurred background for status bar that doesn't show underlying content
        createSolidStatusBarBlur(activity, radius)
    }
    
    /**
     * Create a solid blurred background for status bar that hides underlying content
     * This creates a frosted glass effect without showing what's behind
     */
    private fun createSolidStatusBarBlur(activity: Activity, radius: Float) {
        try {
            val window = activity.window
            val decorView = window.decorView
            
            decorView.post {
                try {
                    // Get status bar height
                    val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
                    val statusBarHeight = if (resourceId > 0) {
                        activity.resources.getDimensionPixelSize(resourceId)
                    } else {
                        (24 * activity.resources.displayMetrics.density).toInt()
                    }
                    
                    // Determine if dark mode is enabled
                    val isDarkMode = (activity.resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    
                    // Create a completely opaque status bar that fully hides underlying content
                    // Use actual theme background colors from the app
                    val overlay = android.view.View(activity).apply {
                        id = android.R.id.content + 1000 // Unique ID
                        
                        // Use a solid opaque background that completely obscures content behind
                        background = android.graphics.drawable.GradientDrawable().apply {
                            // Use exact theme-appropriate background color with 80% opacity
                            val baseColor = activity.resources.getColor(com.guruswarupa.launch.R.color.background, null)
                            // Apply 80% opacity using ColorUtils
                            val backgroundColor = ColorUtils.setAlphaComponent(baseColor, (255 * 0.8).toInt())
                            
                            colors = intArrayOf(backgroundColor, backgroundColor)
                            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
                            
                            // No corner radius needed for solid color
                            cornerRadius = 0f
                        }
                        
                        // Position only at the top (status bar area)
                        val layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            statusBarHeight
                        )
                        layoutParams.gravity = android.view.Gravity.TOP
                        setLayoutParams(layoutParams)
                    }
                    
                    // Add overlay to decor view
                    if (decorView is android.widget.FrameLayout) {
                        // Remove existing overlay if present
                        val existingOverlay = decorView.findViewById<android.view.View>(overlay.id)
                        existingOverlay?.let { decorView.removeView(it) }
                        
                        decorView.addView(overlay)
                    }
                } catch (_: Exception) {
                    // Fallback to simple transparent status bar
                    makeSystemBarsTransparent(activity)
                }
            }
        } catch (_: Exception) {
            // Fallback to transparent status bar
            makeSystemBarsTransparent(activity)
        }
    }
    
    /**
     * Make system bars transparent without blur (fallback for older Android versions)
     */
    private fun makeSystemBarsTransparent(activity: Activity) {
        val window = activity.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
    
    /**
     * Remove blur effect from status bar
     */
    fun removeBlurFromStatusBar(activity: Activity) {
        try {
            val window = activity.window
            val decorView = window.decorView
            
            decorView.post {
                try {
                    if (decorView is android.widget.FrameLayout) {
                        // Remove the blur overlay
                        val overlayId = android.R.id.content + 1000
                        val overlay = decorView.findViewById<android.view.View>(overlayId)
                        overlay?.let { decorView.removeView(it) }
                    }
                } catch (_: Exception) {
                    // Ignore if removal fails
                }
            }
        } catch (_: Exception) {
            // Ignore if removal fails
        }
    }
    
    /**
     * Apply blur to a specific view (general purpose)
     * @param view The view to apply blur to
     * @param radius The blur radius
     */
    fun applyBlurToView(view: View, radius: Float = 15f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val blurEffect = RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP
                )
                view.setRenderEffect(blurEffect)
            } catch (_: Exception) {
                // Ignore if blur fails
            }
        }
    }
    
    /**
     * Remove blur from a specific view (general purpose)
     */
    fun removeBlurFromView(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                view.setRenderEffect(null)
            } catch (_: Exception) {
                // Ignore if removal fails
            }
        }
    }
}