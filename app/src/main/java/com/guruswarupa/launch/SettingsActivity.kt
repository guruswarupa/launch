package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import java.lang.Runtime
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private val EXPORT_REQUEST_CODE = 1
    private val IMPORT_REQUEST_CODE = 2
    private var hasRequestedUsageStats = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Make status bar and navigation bar transparent
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        val weatherApiKeyInput = findViewById<EditText>(R.id.weather_api_key_input)
        val currencySpinner = findViewById<Spinner>(R.id.currency_spinner)
        val gridOption = findViewById<Button>(R.id.grid_option)
        val listOption = findViewById<Button>(R.id.list_option)
        val saveButton = findViewById<Button>(R.id.save_settings_button)
        
        // Track selected display style (use a mutable variable that can be accessed in saveSettings)
        val selectedStyleRef = object {
            var value = prefs.getString("view_preference", "list") ?: "list"
        }
        
        // Update button states based on current preference
        updateDisplayStyleButtons(gridOption, listOption, selectedStyleRef.value)
        
        // Set click listeners for display style buttons
        gridOption.setOnClickListener {
            selectedStyleRef.value = "grid"
            updateDisplayStyleButtons(gridOption, listOption, selectedStyleRef.value)
        }
        
        listOption.setOnClickListener {
            selectedStyleRef.value = "list"
            updateDisplayStyleButtons(gridOption, listOption, selectedStyleRef.value)
        }
        val exportButton = findViewById<Button>(R.id.export_settings_button)
        val importButton = findViewById<Button>(R.id.import_settings_button)
        val resetFinanceButton = findViewById<Button>(R.id.reset_finance_button)
        val appLockButton = findViewById<Button>(R.id.app_lock_button)
        val checkPermissionsButton = findViewById<Button>(R.id.check_permissions_button)
        val restartLauncherButton = findViewById<Button>(R.id.restart_launcher_button)

        // Setup currency spinner
        setupCurrencySpinner(currencySpinner)
        
        // Load current settings
        loadCurrentSettings(weatherApiKeyInput, currencySpinner, gridOption, listOption)

        saveButton.setOnClickListener {
            saveSettings(weatherApiKeyInput, currencySpinner, selectedStyleRef.value)
        }

        exportButton.setOnClickListener {
            exportSettings()
        }

        importButton.setOnClickListener {
            importSettings()
        }

        resetFinanceButton.setOnClickListener {
            resetFinanceData()
        }
        appLockButton.setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }
        
        checkPermissionsButton.setOnClickListener {
            checkAndRequestPermissions()
        }
        
        restartLauncherButton.setOnClickListener {
            restartLauncher()
        }
        
        // Setup expandable sections
        setupExpandableSections()
    }
    
    private fun setupExpandableSections() {
        // Weather API Key Section
        val weatherHeader = findViewById<LinearLayout>(R.id.weather_api_key_header)
        val weatherContent = findViewById<LinearLayout>(R.id.weather_api_key_content)
        val weatherArrow = findViewById<TextView>(R.id.weather_api_key_arrow)
        setupSectionToggle(weatherHeader, weatherContent, weatherArrow)
        
        // Currency Section
        val currencyHeader = findViewById<LinearLayout>(R.id.currency_header)
        val currencyContent = findViewById<LinearLayout>(R.id.currency_content)
        val currencyArrow = findViewById<TextView>(R.id.currency_arrow)
        setupSectionToggle(currencyHeader, currencyContent, currencyArrow)
        
        // Display Style Section
        val displayStyleHeader = findViewById<LinearLayout>(R.id.display_style_header)
        val displayStyleContent = findViewById<LinearLayout>(R.id.display_style_content)
        val displayStyleArrow = findViewById<TextView>(R.id.display_style_arrow)
        setupSectionToggle(displayStyleHeader, displayStyleContent, displayStyleArrow)
        
        // Backup & Restore Section
        val backupHeader = findViewById<LinearLayout>(R.id.backup_restore_header)
        val backupContent = findViewById<LinearLayout>(R.id.backup_restore_content)
        val backupArrow = findViewById<TextView>(R.id.backup_restore_arrow)
        setupSectionToggle(backupHeader, backupContent, backupArrow)
        
        // Finance Data Section
        val financeHeader = findViewById<LinearLayout>(R.id.finance_data_header)
        val financeContent = findViewById<LinearLayout>(R.id.finance_data_content)
        val financeArrow = findViewById<TextView>(R.id.finance_data_arrow)
        setupSectionToggle(financeHeader, financeContent, financeArrow)
        
        // App Lock Section
        val appLockHeader = findViewById<LinearLayout>(R.id.app_lock_header)
        val appLockContent = findViewById<LinearLayout>(R.id.app_lock_content)
        val appLockArrow = findViewById<TextView>(R.id.app_lock_arrow)
        setupSectionToggle(appLockHeader, appLockContent, appLockArrow)
        
        // Permissions Section
        val permissionsHeader = findViewById<LinearLayout>(R.id.permissions_header)
        val permissionsContent = findViewById<LinearLayout>(R.id.permissions_content)
        val permissionsArrow = findViewById<TextView>(R.id.permissions_arrow)
        setupSectionToggle(permissionsHeader, permissionsContent, permissionsArrow)
        
        // Launcher Section
        val launcherHeader = findViewById<LinearLayout>(R.id.launcher_header)
        val launcherContent = findViewById<LinearLayout>(R.id.launcher_content)
        val launcherArrow = findViewById<TextView>(R.id.launcher_arrow)
        setupSectionToggle(launcherHeader, launcherContent, launcherArrow)
    }
    
    private fun setupSectionToggle(header: LinearLayout, content: LinearLayout, arrow: TextView) {
        header.setOnClickListener {
            val isExpanded = content.visibility == View.VISIBLE
            if (isExpanded) {
                content.visibility = View.GONE
                arrow.text = "▼"
            } else {
                content.visibility = View.VISIBLE
                arrow.text = "▲"
            }
        }
    }

    private fun setupCurrencySpinner(spinner: Spinner) {
        val currencies = FinanceManager.SUPPORTED_CURRENCIES.map { (code, symbol) ->
            "$code ($symbol)"
        }.toTypedArray()
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }
    
    private fun loadCurrentSettings(
        weatherApiKeyInput: EditText,
        currencySpinner: Spinner,
        gridOption: Button,
        listOption: Button
    ) {
        // Load weather API key
        val currentApiKey = prefs.getString("weather_api_key", "") ?: ""
        weatherApiKeyInput.setText(currentApiKey)

        // Load currency
        val currentCurrency = prefs.getString("finance_currency", "USD") ?: "USD"
        val currencyIndex = FinanceManager.SUPPORTED_CURRENCIES.keys.indexOf(currentCurrency)
        if (currencyIndex >= 0) {
            currencySpinner.setSelection(currencyIndex)
        }

        // Load display style
        val currentStyle = prefs.getString("view_preference", "list") ?: "list"
        updateDisplayStyleButtons(gridOption, listOption, currentStyle)
    }
    
    private fun updateDisplayStyleButtons(gridOption: Button, listOption: Button, selectedStyle: String) {
        if (selectedStyle == "grid") {
            gridOption.alpha = 1.0f
            listOption.alpha = 0.5f
        } else {
            gridOption.alpha = 0.5f
            listOption.alpha = 1.0f
        }
    }

    private fun saveSettings(weatherApiKeyInput: EditText, currencySpinner: Spinner, selectedDisplayStyle: String) {
        val editor = prefs.edit()

        // Save weather API key
        val apiKey = weatherApiKeyInput.text.toString().trim()
        editor.putString("weather_api_key", apiKey)
        if (apiKey.isNotEmpty()) {
            editor.putBoolean("weather_api_key_rejected", false)
        }

        // Save currency
        val selectedCurrencyIndex = currencySpinner.selectedItemPosition
        val currencyCodes = FinanceManager.SUPPORTED_CURRENCIES.keys.toList()
        if (selectedCurrencyIndex >= 0 && selectedCurrencyIndex < currencyCodes.size) {
            editor.putString("finance_currency", currencyCodes[selectedCurrencyIndex])
        }

        // Save display style
        editor.putString("view_preference", selectedDisplayStyle)

        editor.apply()

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()

        // Send broadcast to refresh main activity if needed
        val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
        sendBroadcast(intent)

        finish()
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    private fun openDefaultLauncherSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open launcher settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportSettings() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "launch_settings_backup.json")
        }
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
    }

    private fun importSettings() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                EXPORT_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        exportSettingsToFile(uri)
                    }
                }
                IMPORT_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        importSettingsFromFile(uri)
                    }
                }
            }
        }
    }

    private fun exportSettingsToFile(uri: Uri) {
        try {
            val settingsJson = JSONObject()
            
            // Export main preferences (com.guruswarupa.launch.PREFS)
            // This includes:
            // - Favorite apps (favorite_apps)
            // - Workspaces (workspaces)
            // - Active workspace ID (active_workspace_id)
            // - Show all apps mode (show_all_apps_mode)
            // - Weather API key (weather_api_key)
            // - Currency preference (finance_currency)
            // - Display style (view_preference)
            // - Todo items (todo_items)
            // - Workout widget data (workout_exercises, workout_streak, workout_last_reset_date, workout_last_streak_date)
            // - Android widgets (saved_widgets)
            // - Transaction data
            // - All other preferences
            val mainPrefs = prefs.all
            val mainPrefsJson = JSONObject()
            for ((key, value) in mainPrefs) {
                when (value) {
                    is String -> {
                        mainPrefsJson.put(key, value)
                        // Special handling for todo_items to ensure proper format
                        if (key == "todo_items" && value.isNotEmpty()) {
                            // Validate todo items format before export
                            val todoArray = value.split("|")
                            var isValidFormat = true
                            for (todoString in todoArray) {
                                if (todoString.isNotEmpty()) {
                                    val parts = todoString.split(":")
                                    if (parts.size < 7) {
                                        isValidFormat = false
                                        break
                                    }
                                }
                            }
                            if (!isValidFormat) {
                                Toast.makeText(this, "Warning: Todo items may not export correctly due to format issues", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is Boolean -> mainPrefsJson.put(key, value)
                    is Int -> mainPrefsJson.put(key, value)
                    is Long -> mainPrefsJson.put(key, value)
                    is Float -> mainPrefsJson.put(key, value)
                    is Set<*> -> {
                        // This includes favorite_apps and other Set preferences
                        val jsonArray = JSONArray()
                        value.forEach { jsonArray.put(it) }
                        mainPrefsJson.put(key, jsonArray)
                    }
                }
            }
            settingsJson.put("main_preferences", mainPrefsJson)
            
            // Export app timer preferences
            val appTimerPrefs = getSharedPreferences("app_timer_prefs", MODE_PRIVATE)
            val appTimerAll = appTimerPrefs.all
            if (appTimerAll.isNotEmpty()) {
                val appTimerJson = JSONObject()
                for ((key, value) in appTimerAll) {
                    when (value) {
                        is String -> appTimerJson.put(key, value)
                        is Boolean -> appTimerJson.put(key, value)
                        is Int -> appTimerJson.put(key, value)
                        is Long -> appTimerJson.put(key, value)
                        is Float -> appTimerJson.put(key, value)
                        is Set<*> -> {
                            val jsonArray = JSONArray()
                            value.forEach { jsonArray.put(it) }
                            appTimerJson.put(key, jsonArray)
                        }
                    }
                }
                settingsJson.put("app_timer_prefs", appTimerJson)
            }
            
            // Export app lock preferences
            val appLockPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
            val appLockAll = appLockPrefs.all
            if (appLockAll.isNotEmpty()) {
                val appLockJson = JSONObject()
                for ((key, value) in appLockAll) {
                    when (value) {
                        is String -> appLockJson.put(key, value)
                        is Boolean -> appLockJson.put(key, value)
                        is Int -> appLockJson.put(key, value)
                        is Long -> appLockJson.put(key, value)
                        is Float -> appLockJson.put(key, value)
                        is Set<*> -> {
                            val jsonArray = JSONArray()
                            value.forEach { jsonArray.put(it) }
                            appLockJson.put(key, jsonArray)
                        }
                    }
                }
                settingsJson.put("app_lock_prefs", appLockJson)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(settingsJson.toString(2).toByteArray())
            }

            Toast.makeText(this, "Settings exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettingsFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val settingsJson = JSONObject(jsonString)
                
                // Check if this is the new format (with separate SharedPreferences files)
                // or the old format (all at root level)
                val isNewFormat = settingsJson.has("main_preferences")
                
                if (isNewFormat) {
                    // New format: organized by SharedPreferences file
                    // Import main preferences
                    if (settingsJson.has("main_preferences")) {
                        val mainPrefsJson = settingsJson.getJSONObject("main_preferences")
                        val editor = prefs.edit()
                        importPreferences(mainPrefsJson, editor)
                        editor.apply()
                    }
                    
                    // Import app timer preferences
                    if (settingsJson.has("app_timer_prefs")) {
                        val appTimerPrefs = getSharedPreferences("app_timer_prefs", MODE_PRIVATE)
                        val appTimerJson = settingsJson.getJSONObject("app_timer_prefs")
                        val editor = appTimerPrefs.edit()
                        importPreferences(appTimerJson, editor)
                        editor.apply()
                    }
                    
                    // Import app lock preferences
                    if (settingsJson.has("app_lock_prefs")) {
                        val appLockPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
                        val appLockJson = settingsJson.getJSONObject("app_lock_prefs")
                        val editor = appLockPrefs.edit()
                        importPreferences(appLockJson, editor)
                        editor.apply()
                    }
                } else {
                    // Old format: all preferences at root level (backward compatibility)
                    val editor = prefs.edit()
                    importPreferences(settingsJson, editor)
                    editor.apply()
                }

                // Reload current settings in UI
                val weatherApiKeyInput = findViewById<EditText>(R.id.weather_api_key_input)
                val currencySpinner = findViewById<Spinner>(R.id.currency_spinner)
                val gridOption = findViewById<Button>(R.id.grid_option)
                val listOption = findViewById<Button>(R.id.list_option)
                loadCurrentSettings(weatherApiKeyInput, currencySpinner, gridOption, listOption)

                Toast.makeText(this, "Settings imported successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to import settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun importPreferences(prefsJson: JSONObject, editor: SharedPreferences.Editor) {
        val keys = prefsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = prefsJson.get(key)

            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is Float -> editor.putFloat(key, value)
                is JSONArray -> {
                    val stringSet = mutableSetOf<String>()
                    for (i in 0 until value.length()) {
                        stringSet.add(value.getString(i))
                    }
                    editor.putStringSet(key, stringSet)
                }
            }
        }
    }


    private fun resetFinanceData() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Reset Finance Data")
            .setMessage("Are you sure you want to reset all finance data? This will clear your balance, transaction history, and monthly records. This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                // Reset all finance-related data
                val editor = prefs.edit()
                val allPrefs = prefs.all
                for (key in allPrefs.keys) {
                    if (key.startsWith("finance_") || key.startsWith("transaction_")) {
                        editor.remove(key)
                    }
                }
                editor.apply()

                Toast.makeText(this, "Finance data reset successfully", Toast.LENGTH_SHORT).show()

                // Send broadcast to refresh MainActivity
                val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                sendBroadcast(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private var permissionsDialog: AlertDialog? = null
    
    private fun checkAndRequestPermissions() {
        // Create custom dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permissions, null)
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        permissionsDialog = dialog
        
        // Make dialog window scrollable with proper sizing
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.75).toInt()
        )
        
        val permissionsList = dialogView.findViewById<LinearLayout>(R.id.permissions_list)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.close_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val applyButton = dialogView.findViewById<Button>(R.id.apply_button)
        
        // Permission data structure
        data class PermissionItem(
            val permission: String?,
            val name: String,
            val description: String,
            val isGranted: Boolean,
            val isSpecial: Boolean = false, // For Usage Stats which needs special handling
            val isLauncher: Boolean = false // For Default Launcher which needs special handling
        )
        
        val allPermissions = mutableListOf<PermissionItem>()
        val permissionToggles = mutableMapOf<String, Switch>()
        
        // Check Contacts permission - always show
        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.READ_CONTACTS,
            "Contacts",
            "Access your contacts to search and call them",
            contactsGranted
        ))
        
        // Check Call Phone permission - always show
        val callGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.CALL_PHONE,
            "Phone Calls",
            "Make phone calls directly from the launcher",
            callGranted
        ))
        
        // Check SMS permission - always show
        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.SEND_SMS,
            "SMS",
            "Send text messages directly from the launcher",
            smsGranted
        ))
        
        // Check Storage permission - always show (different for different Android versions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) uses READ_MEDIA_IMAGES
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.READ_MEDIA_IMAGES,
                "Storage (Images)",
                "Access images to set custom wallpapers",
                storageGranted
            ))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 (API 30-32) - no permission needed for scoped storage, but check if we can still request
            // For these versions, we might not need explicit permission, but let's check READ_EXTERNAL_STORAGE
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "Storage",
                "Access storage to set custom wallpapers",
                storageGranted
            ))
        } else {
            // Android 10 and below
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "Storage",
                "Access storage to set custom wallpapers",
                storageGranted
            ))
        }
        
        // Check Location permission - always show
        val locationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.ACCESS_FINE_LOCATION,
            "Location",
            "Get your location for weather information",
            locationGranted
        ))
        
        // Check Usage Stats permission - always show
        val usageStatsGranted = hasUsageStatsPermission()
        allPermissions.add(PermissionItem(
            null,
            "Usage Stats",
            "Show app usage time and statistics",
            usageStatsGranted,
            true
        ))
        
        // Check Default Launcher - always show
        val isDefaultLauncher = isDefaultLauncher()
        allPermissions.add(PermissionItem(
            null,
            "Default Launcher",
            "Set this app as your default home launcher",
            isDefaultLauncher,
            false,
            true // Mark as launcher type
        ))
        
        // Populate permissions list
        for (perm in allPermissions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_permission, permissionsList, false)
            val nameText = itemView.findViewById<TextView>(R.id.permission_name)
            val descText = itemView.findViewById<TextView>(R.id.permission_description)
            val toggle = itemView.findViewById<Switch>(R.id.permission_switch)
            
            nameText.text = perm.name
            descText.text = perm.description
            toggle.isChecked = perm.isGranted
            toggle.isEnabled = true
            
            // Set toggle listener to request permission immediately when toggled on
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !perm.isGranted) {
                    // User wants to enable this permission - request it immediately
                    if (perm.isSpecial) {
                        // Usage Stats needs special handling
                        requestUsageStatsPermission()
                    } else if (perm.isLauncher) {
                        // Default Launcher needs special handling
                        openDefaultLauncherSettings()
                    } else if (perm.permission != null) {
                        // Request the permission immediately
                        // Special handling for READ_MEDIA_IMAGES on Android 13+
                        if (perm.permission == Manifest.permission.READ_MEDIA_IMAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                                PERMISSION_REQUEST_CODE
                            )
                        } else {
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(perm.permission),
                                PERMISSION_REQUEST_CODE
                            )
                        }
                    }
                } else if (!isChecked && perm.isGranted) {
                    // User wants to disable - redirect to app permissions page
                    if (perm.isLauncher) {
                        // For launcher, open home settings
                        openDefaultLauncherSettings()
                    } else {
                        // For other permissions, open app's permissions page in system settings
                        try {
                            // Try to open permissions page directly (Android 6.0+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                                Toast.makeText(this, "Navigate to Permissions and disable ${perm.name}", Toast.LENGTH_LONG).show()
                            } else {
                                // Fallback for older Android versions
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                                Toast.makeText(this, "Please disable ${perm.name} permission in system settings", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Cannot revoke permissions. Please disable in system settings.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // Reset toggle to checked state after a delay to show the redirect
                    toggle.postDelayed({
                        toggle.isChecked = true
                    }, 500)
                }
            }
            
            if (perm.permission != null) {
                permissionToggles[perm.permission] = toggle
            } else if (perm.isSpecial) {
                permissionToggles["USAGE_STATS"] = toggle
            } else if (perm.isLauncher) {
                permissionToggles["DEFAULT_LAUNCHER"] = toggle
            } else if (perm.isLauncher) {
                permissionToggles["DEFAULT_LAUNCHER"] = toggle
            }
            
            permissionsList.addView(itemView)
        }
        
        // Close button
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Cancel button
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Apply button - just closes the dialog since permissions are requested immediately on toggle
        applyButton.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show()
        }
        
        dialog.show()
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }
    }
    
    private fun requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                hasRequestedUsageStats = true
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open usage stats settings", Toast.LENGTH_SHORT).show()
                hasRequestedUsageStats = false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if user returned from usage stats settings
        if (hasRequestedUsageStats) {
            hasRequestedUsageStats = false
            if (hasUsageStatsPermission()) {
                Toast.makeText(this, "Usage Stats permission granted", Toast.LENGTH_SHORT).show()
                // Update toggle in dialog if still open
                updatePermissionToggle("USAGE_STATS", true)
            } else {
                // User might have denied it
                if (!prefs.getBoolean("usage_stats_permission_denied", false)) {
                    // Ask if they want to try again
                    AlertDialog.Builder(this, R.style.CustomDialogTheme)
                        .setTitle("Usage Stats Permission")
                        .setMessage("Usage Stats permission is required to show app usage time. Would you like to try again?")
                        .setPositiveButton("Try Again") { _, _ ->
                            requestUsageStatsPermission()
                        }
                        .setNegativeButton("Skip") { _, _ ->
                            prefs.edit().putBoolean("usage_stats_permission_denied", true).apply()
                        }
                        .show()
                } else {
                    updatePermissionToggle("USAGE_STATS", false)
                }
            }
        }
        
        // Check if user returned from default launcher settings
        if (permissionsDialog?.isShowing == true) {
            updatePermissionToggle("DEFAULT_LAUNCHER", isDefaultLauncher())
        }
    }
    
    private fun updatePermissionToggle(key: String, isGranted: Boolean) {
        if (permissionsDialog?.isShowing == true) {
            val dialogView = permissionsDialog?.window?.decorView
            dialogView?.post {
                val permissionsList = dialogView.findViewById<LinearLayout>(R.id.permissions_list)
                if (permissionsList != null) {
                    for (j in 0 until permissionsList.childCount) {
                        val itemView = permissionsList.getChildAt(j)
                        val toggle = itemView.findViewById<Switch>(R.id.permission_switch)
                        val nameText = itemView.findViewById<TextView>(R.id.permission_name)
                        val expectedName = when (key) {
                            "USAGE_STATS" -> "Usage Stats"
                            "DEFAULT_LAUNCHER" -> "Default Launcher"
                            else -> null
                        }
                        if (nameText?.text == expectedName) {
                            toggle?.isChecked = isGranted
                            break
                        }
                    }
                }
            }
        }
    }
    
    private fun restartLauncher() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Restart Launcher")
            .setMessage("This will restart the launcher. Continue?")
            .setPositiveButton("Restart") { _, _ ->
                try {
                    val packageManager = packageManager
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    val componentName = intent?.component
                    if (componentName != null) {
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    startActivity(mainIntent)
                    Runtime.getRuntime().exit(0)
                    } else {
                        Toast.makeText(this, "Failed to restart launcher", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to restart launcher: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                val permission = permissions[i]
                val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                
                val permissionName = when (permission) {
                    Manifest.permission.READ_CONTACTS -> "Contacts"
                    Manifest.permission.CALL_PHONE -> "Phone Calls"
                    Manifest.permission.SEND_SMS -> "SMS"
                    Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                    Manifest.permission.READ_MEDIA_IMAGES -> "Storage (Images)"
                    Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
                    else -> null
                }
                
                if (granted) {
                    // Permission granted - update toggle state in dialog if still open
                    if (permissionsDialog?.isShowing == true) {
                        val dialogView = permissionsDialog?.window?.decorView
                        dialogView?.post {
                            // Refresh the dialog by finding the toggle and updating it
                            val permissionsList = dialogView.findViewById<LinearLayout>(R.id.permissions_list)
                            if (permissionsList != null) {
                                for (j in 0 until permissionsList.childCount) {
                                    val itemView = permissionsList.getChildAt(j)
                                    val toggle = itemView.findViewById<Switch>(R.id.permission_switch)
                                    val nameText = itemView.findViewById<TextView>(R.id.permission_name)
                                    if (nameText?.text == permissionName) {
                                        toggle?.isChecked = true
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (permissionName != null) {
                        Toast.makeText(this, "$permissionName permission granted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Permission denied - update toggle state
                    if (permissionsDialog?.isShowing == true) {
                        val dialogView = permissionsDialog?.window?.decorView
                        dialogView?.post {
                            val permissionsList = dialogView.findViewById<LinearLayout>(R.id.permissions_list)
                            if (permissionsList != null) {
                                for (j in 0 until permissionsList.childCount) {
                                    val itemView = permissionsList.getChildAt(j)
                                    val toggle = itemView.findViewById<Switch>(R.id.permission_switch)
                                    val nameText = itemView.findViewById<TextView>(R.id.permission_name)
                                    if (nameText?.text == permissionName) {
                                        toggle?.isChecked = false
                                        break
                                    }
                                }
                            }
                        }
                    }
                    
                    // Mark as denied if user permanently denied
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        when (permission) {
                            Manifest.permission.READ_CONTACTS -> {
                                prefs.edit().putBoolean("contacts_permission_denied", true).apply()
                            }
                            Manifest.permission.SEND_SMS -> {
                                prefs.edit().putBoolean("sms_permission_denied", true).apply()
                            }
                        }
                    }
                    
                    if (permissionName != null) {
                        Toast.makeText(this, "$permissionName permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun makeSystemBarsTransparent() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setDecorFitsSystemWindows(false)
                
                // Use decorView to get insetsController safely
                val decorView = window.decorView
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
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val decorView = window.decorView
                    if (decorView != null) {
                        var flags = decorView.systemUiVisibility
                        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        
                        // Always use white/light icons regardless of mode (don't set LIGHT_STATUS_BAR flag)
                        // When LIGHT_STATUS_BAR is NOT set, icons are light/white
                        
                        decorView.systemUiVisibility = flags
                    }
                }
            }
        } catch (e: Exception) {
            // If anything fails, at least try to set the colors
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
            } catch (ex: Exception) {
                // Ignore if even this fails
            }
        }
    }
}