package com.guruswarupa.launch.models

import android.annotation.SuppressLint

/**
 * Centralized constants for the application
 */
object Constants {
    // SharedPreferences keys
    @SuppressLint("unused")
    object Prefs {
        const val PREFS_NAME = "com.guruswarupa.launch.PREFS"
        const val SHAKE_TORCH_ENABLED = "shake_torch_enabled"
        const val SHAKE_SENSITIVITY = "shake_sensitivity"
        const val SCREEN_DIMMER_ENABLED = "screen_dimmer_enabled"
        const val SCREEN_DIMMER_LEVEL = "screen_dimmer_level"
        const val NIGHT_MODE_ENABLED = "night_mode_enabled"
        const val NIGHT_MODE_INTENSITY = "night_mode_intensity"
        const val FLIP_DND_ENABLED = "flip_dnd_enabled"
        const val BACK_TAP_ENABLED = "back_tap_enabled"
        const val BACK_TAP_ACTION = "back_tap_action"
        const val BACK_TAP_SENSITIVITY = "back_tap_sensitivity"
        const val BACK_TAP_MULTIPLIER = "back_tap_multiplier"
        const val BACK_TAP_DOUBLE_ACTION = "back_tap_double_action"
        const val WALLPAPER_BLUR_LEVEL = "wallpaper_blur_level"
        const val SEARCH_ENGINE = "search_engine"
        const val ACCESSIBILITY_SHORTCUT_ENABLED = "accessibility_shortcut_enabled"
    }
    
    // FileProvider
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    
    // Cache directories
    const val SHARED_APKS_DIR = "shared_apks"
    
    // File extensions
    const val APK_EXTENSION = ".apk"
    
    // MIME types
    const val MIME_TYPE_APK = "application/vnd.android.package-archive"
    const val MIME_TYPE_ALL = "*/*"
    // Regex patterns
    const val APP_NAME_SANITIZE_REGEX = "[^a-zA-Z0-9.-]"
    const val APP_NAME_SANITIZE_REPLACEMENT = "_"
}
