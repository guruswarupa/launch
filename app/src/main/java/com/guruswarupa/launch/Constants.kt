package com.guruswarupa.launch

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
