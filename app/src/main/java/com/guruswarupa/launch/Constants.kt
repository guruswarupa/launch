package com.guruswarupa.launch

/**
 * Centralized constants for the application
 */
object Constants {
    // SharedPreferences keys
    object Prefs {
        const val PREFS_NAME = "com.guruswarupa.launch.PREFS"
        const val IS_FIRST_RUN = "isFirstRun"
        const val VIEW_PREFERENCE = "view_preference"
        const val WEATHER_API_KEY = "weather_api_key"
        const val WEATHER_API_KEY_REJECTED = "weather_api_key_rejected"
        const val WEATHER_STORED_LATITUDE = "weather_stored_latitude"
        const val WEATHER_STORED_LONGITUDE = "weather_stored_longitude"
        const val LAST_WEATHER_UPDATE = "last_weather_update"
        const val TODO_ITEMS = "todo_items"
        const val FINANCE_BALANCE = "finance_balance"
        const val FINANCE_CURRENCY = "finance_currency"
        const val FINANCE_EXPENSES_PREFIX = "finance_expenses_"
        const val FINANCE_INCOME_PREFIX = "finance_income_"
        const val TRANSACTION_PREFIX = "transaction_"
        const val USAGE_PREFIX = "usage_"
        const val WORKSPACES_KEY = "workspaces"
        const val ACTIVE_WORKSPACE_KEY = "active_workspace_id"
        const val SAVED_WIDGETS_KEY = "saved_widgets"
        const val SHAKE_TORCH_ENABLED = "shake_torch_enabled"
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
    
    // Intent actions
    const val ACTION_SHARE_APK = "Share %s APK"
    const val ACTION_SHARE_FILE = "Share file"
    
    // Time intervals
    const val WEATHER_UPDATE_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
    const val WEATHER_UPDATE_INTERVAL_ON_RESUME_MS = 10 * 60 * 1000L // 10 minutes
    
    // App usage monitoring
    const val USAGE_MONITOR_INTERVAL_MS = 5000L // 5 seconds
    
    // Transaction limits
    const val MAX_TRANSACTIONS = 100
    
    // Regex patterns
    const val APP_NAME_SANITIZE_REGEX = "[^a-zA-Z0-9.-]"
    const val APP_NAME_SANITIZE_REPLACEMENT = "_"
}
