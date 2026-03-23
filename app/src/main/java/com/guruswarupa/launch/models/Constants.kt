package com.guruswarupa.launch.models

import android.annotation.SuppressLint




object Constants {
    
    @SuppressLint("unused")
    object Prefs {
        const val PREFS_NAME = "com.guruswarupa.launch.PREFS"
        const val SELECTED_THEME = "selected_theme"
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
        const val OPAQUE_SURFACES_ENABLED = "opaque_surfaces_enabled"
        const val SEARCH_ENGINE = "search_engine"
        const val ACCESSIBILITY_SHORTCUT_ENABLED = "accessibility_shortcut_enabled"
        const val CONTROL_CENTER_SHORTCUTS = "control_center_shortcuts"
        const val TYPOGRAPHY_SCALE_PERCENT = "typography_scale_percent"
        const val TYPOGRAPHY_FONT_STYLE = "typography_font_style"
        const val TYPOGRAPHY_FONT_INTENSITY = "typography_font_intensity"
        const val TYPOGRAPHY_FONT_COLOR = "typography_font_color"
        const val CLOCK_24_HOUR_FORMAT = "clock_24_hour_format"
        const val GRID_COLUMNS = "grid_columns"
        const val SHOW_APP_NAME_IN_GRID = "show_app_name_in_grid"
        const val ICON_STYLE = "icon_style"
        const val ICON_SIZE = "icon_size"
        const val GRAYSCALE_ICONS_ENABLED = "grayscale_icons_enabled"
        const val BACKGROUND_TRANSLUCENCY = "background_translucency"
        const val DEFAULT_HOME_PAGE_INDEX = "default_home_page_index"
        const val WEB_APPS = "web_apps"
        const val GRAYSCALE_MODE_ENABLED = "grayscale_mode_enabled"
        
        
        const val VAULT_TIMEOUT_ENABLED = "vault_timeout_enabled"
        const val VAULT_TIMEOUT_DURATION = "vault_timeout_duration"  
        const val VAULT_SETUP_COMPLETE = "vault_setup_complete"

    }

    const val TYPOGRAPHY_FONT_COLOR_DEFAULT = "#FFFFFFF"

    
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    
    
    const val SHARED_APKS_DIR = "shared_apks"
    
    
    const val APK_EXTENSION = ".apk"
    
    
    const val MIME_TYPE_APK = "application_vnd.android.package-archive"
    const val MIME_TYPE_ALL = "*/*"
    
    const val APP_NAME_SANITIZE_REGEX = "[^a-zA-Z0-9.-]"
    const val APP_NAME_SANITIZE_REPLACEMENT = "_"
}
