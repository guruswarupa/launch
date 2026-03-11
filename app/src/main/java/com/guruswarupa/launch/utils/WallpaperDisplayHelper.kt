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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            target.setRenderEffect(null)
            return
        }

        val elderlyModeEnabled = prefs.getBoolean(Constants.Prefs.ELDERLY_READABILITY_MODE_ENABLED, false)
        val blurLevel = if (elderlyModeEnabled) 0 else prefs.getInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, 50)

        if (blurLevel > 0) {
            val radius = blurLevel.toFloat().coerceAtLeast(1f)
            val effect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            target.setRenderEffect(effect)
        } else {
            target.setRenderEffect(null)
        }
    }
}
