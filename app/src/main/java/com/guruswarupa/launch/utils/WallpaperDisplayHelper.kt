package com.guruswarupa.launch.utils

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.ThemeOption

object WallpaperDisplayHelper {
    fun applySystemWallpaper(target: ImageView, fallbackRes: Int = R.drawable.wallpaper_background) {
        val context = target.context
        val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
        val selectedThemeId = prefs.getString(Constants.Prefs.SELECTED_THEME, "system_default")
        
        if (selectedThemeId == "system_default") {
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
        } else {
            val theme = ThemeOption.PREDEFINED_THEMES.find { it.id == selectedThemeId }
            if (theme != null) {
                // Use highest quality settings for the actual wallpaper
                val options = RequestOptions()
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(Target.SIZE_ORIGINAL) // Load original resolution
                    .centerCrop()

                Glide.with(context)
                    .asDrawable()
                    .load(theme.wallpaperUrl)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .placeholder(R.drawable.wallpaper_background)
                    .error(fallbackRes)
                    .into(target)
            } else {
                target.setImageResource(fallbackRes)
            }
        }
    }
    
    fun applyThemePreview(target: ImageView, themeId: String) {
        val context = target.context
        if (themeId == "system_default") {
            val wallpaperDrawable = try {
                WallpaperManager.getInstance(context).drawable
            } catch (_: Exception) {
                null
            }
            if (wallpaperDrawable != null) {
                target.setImageDrawable(wallpaperDrawable)
            } else {
                target.setImageResource(R.drawable.wallpaper_background)
            }
        } else {
            val theme = ThemeOption.PREDEFINED_THEMES.find { it.id == themeId }
            if (theme != null) {
                // Previews can be smaller to save memory, but still clear
                Glide.with(context)
                    .load(theme.wallpaperUrl)
                    .placeholder(R.drawable.wallpaper_background)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(target)
            }
        }
    }
}
