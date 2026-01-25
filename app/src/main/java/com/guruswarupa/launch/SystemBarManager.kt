package com.guruswarupa.launch

import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * Manages system bar (status bar and navigation bar) transparency and appearance
 */
class SystemBarManager(private val activity: androidx.fragment.app.FragmentActivity) {
    
    /**
     * Make status bar and navigation bar transparent
     */
    fun makeSystemBarsTransparent() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                activity.window.setDecorFitsSystemWindows(false)
                
                val decorView = activity.window.decorView
                if (decorView != null) {
                    val insetsController = decorView.windowInsetsController
                    if (insetsController != null) {
                        // Always use white/light icons regardless of mode
                        insetsController.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Android 5.0+ (API 21+)
                activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val decorView = activity.window.decorView
                    if (decorView != null) {
                        var flags = decorView.systemUiVisibility
                        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        
                        // Always use white/light icons (don't set LIGHT_STATUS_BAR flag)
                        decorView.systemUiVisibility = flags
                    }
                }
            }
        } catch (e: Exception) {
            // If anything fails, at least try to set the colors
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
                    activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
            } catch (ex: Exception) {
                // Ignore if window is not ready
            }
        }
    }
}
