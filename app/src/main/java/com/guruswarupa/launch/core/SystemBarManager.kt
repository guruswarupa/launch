package com.guruswarupa.launch.core

import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

class SystemBarManager(private val activity: androidx.fragment.app.FragmentActivity) {
    fun makeSystemBarsTransparent() {
        // Match launcher-like transparent system bars.
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
    }

    fun removeBlurEffect() {
    }
}
