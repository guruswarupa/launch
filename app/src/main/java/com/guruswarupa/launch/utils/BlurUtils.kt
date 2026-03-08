package com.guruswarupa.launch.utils

import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

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
    }
    
    /**
     * Create a solid blurred background for status bar that hides underlying content
     * This creates a frosted glass effect without showing what's behind
     */
    private fun createSolidStatusBarBlur(activity: Activity, radius: Float) {
    }
    
    /**
     * Make system bars transparent without blur (fallback for older Android versions)
     */
    private fun makeSystemBarsTransparent(activity: Activity) {
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
