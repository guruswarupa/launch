package com.guruswarupa.launch.utils

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.ImageView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants

object WallpaperDisplayHelper {
    fun applySystemWallpaper(target: ImageView, fallbackRes: Int = R.drawable.wallpaper_overlay) {
        val context = target.context
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val wallpaperDrawable = try {
            WallpaperManager.getInstance(context).drawable
        } catch (_: Exception) {
            null
        }

        if (wallpaperDrawable != null) {
            target.setImageDrawable(wallpaperDrawable)
        } else {
            target.setImageResource(fallbackRes)
        }

        applyBlurIfNeeded(target, prefs)
    }

    private fun applyBlurIfNeeded(target: ImageView, prefs: SharedPreferences) {
        // Blur removed - always disable blur effect
        target.setRenderEffect(null)
    }
}
