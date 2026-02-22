package com.guruswarupa.launch.core

import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Manages system bar (status bar and navigation bar) transparency and appearance
 */
import com.guruswarupa.launch.utils.BlurUtils

class SystemBarManager(private val activity: androidx.fragment.app.FragmentActivity) {
    
    /**
     * Make status bar and navigation bar translucent with blur effect
     */
    fun makeSystemBarsTransparent() {
        val window = activity.window
        
        // Set colors to transparent
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        
        // Modern way to enable edge-to-edge (drawing behind system bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Modern way to control icon appearance (light vs dark)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        
        // Set to false for light icons (best for dark backgrounds/wallpapers)
        // Set to true if you have a very light background
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        
        // Ensure proper flags are set for older versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        }
        
        // Apply blur effect to status bar
        BlurUtils.applyBlurToStatusBar(activity)
    }
    
    /**
     * Remove blur effect from status bar
     */
    fun removeBlurEffect() {
        BlurUtils.removeBlurFromStatusBar(activity)
    }
}
