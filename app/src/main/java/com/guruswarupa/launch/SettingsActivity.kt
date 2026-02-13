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
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

class SettingsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private val EXPORT_REQUEST_CODE = 1
    private val IMPORT_REQUEST_CODE = 2
    private val HIDDEN_APPS_REQUEST_CODE = 3
    private var hasRequestedUsageStats = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make status bar and navigation bar transparent before setContentView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        
        setContentView(R.layout.activity_settings)
        
        // Ensure system bars are fully configured after view is created
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        val gridOption = findViewById<Button>(R.id.grid_option)
        val listOption = findViewById<Button>(R.id.list_option)
        val saveButton = findViewById<Button>(R.id.save_settings_button)
        
        // Track selected display style
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
        val appLockButton = findViewById<Button>(R.id.app_lock_button)
        val checkPermissionsButton = findViewById<Button>(R.id.check_permissions_button)
        val showTutorialButton = findViewById<Button>(R.id.show_tutorial_button)
        val restartLauncherButton = findViewById<Button>(R.id.restart_launcher_button)
        val clearCacheButton = findViewById<Button>(R.id.clear_cache_button)
        val clearDataButton = findViewById<Button>(R.id.clear_data_button)
        val changeWallpaperButton = findViewById<Button>(R.id.change_wallpaper_button)
        val feedbackButton = findViewById<Button>(R.id.feedback_button)

        // Load current settings
        loadCurrentSettings(gridOption, listOption)

        saveButton.setOnClickListener {
            saveSettings(selectedStyleRef.value)
        }

        exportButton.setOnClickListener {
            exportSettings()
        }

        importButton.setOnClickListener {
            importSettings()
        }

        appLockButton.setOnClickListener {
            startActivity(Intent(this, AppLockSettingsActivity::class.java))
        }
        
        val hiddenAppsButton = findViewById<Button>(R.id.hidden_apps_button)
        hiddenAppsButton.setOnClickListener {
            startActivityForResult(Intent(this, HiddenAppsSettingsActivity::class.java), HIDDEN_APPS_REQUEST_CODE)
        }
        
        checkPermissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        
        val privacyDashboardButton = findViewById<Button>(R.id.privacy_dashboard_button)
        privacyDashboardButton.setOnClickListener {
            startActivity(Intent(this, PrivacyDashboardActivity::class.java))
        }
        
        showTutorialButton.setOnClickListener {
            showTutorial()
        }
        
        restartLauncherButton.setOnClickListener {
            restartLauncher()
        }
        
        clearCacheButton.setOnClickListener {
            clearCache()
        }
        
        clearDataButton.setOnClickListener {
            clearData()
        }
        
        changeWallpaperButton.setOnClickListener {
            chooseWallpaper()
        }

        feedbackButton.setOnClickListener {
            sendFeedback()
        }
        
        // Setup expandable sections
        setupExpandableSections()
    }
    
    private fun sendFeedback() {
        val deviceInfo = """
            
            
            --- Device Info ---
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android Version: ${Build.VERSION.RELEASE}
            SDK: ${Build.VERSION.SDK_INT}
            App Version: ${getAppVersion()}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Launch App Feedback")
            putExtra(Intent.EXTRA_TEXT, deviceInfo)
        }

        try {
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (e: Exception) {
            Toast.makeText(this, "No mail app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            "${pInfo.versionName} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun setupExpandableSections() {
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
        
        // Wallpaper Section
        val wallpaperHeader = findViewById<LinearLayout>(R.id.wallpaper_header)
        val wallpaperContent = findViewById<LinearLayout>(R.id.wallpaper_content)
        val wallpaperArrow = findViewById<TextView>(R.id.wallpaper_arrow)
        setupSectionToggle(wallpaperHeader, wallpaperContent, wallpaperArrow)
        
        // Tutorial Section
        val tutorialHeader = findViewById<LinearLayout>(R.id.tutorial_header)
        val tutorialContent = findViewById<LinearLayout>(R.id.tutorial_content)
        val tutorialArrow = findViewById<TextView>(R.id.tutorial_arrow)
        setupSectionToggle(tutorialHeader, tutorialContent, tutorialArrow)
        
        // Quick Actions Section
        val quickActionsHeader = findViewById<LinearLayout>(R.id.quick_actions_header)
        val quickActionsContent = findViewById<LinearLayout>(R.id.quick_actions_content)
        val quickActionsArrow = findViewById<TextView>(R.id.quick_actions_arrow)
        setupSectionToggle(quickActionsHeader, quickActionsContent, quickActionsArrow)
        
        // Setup torch toggle switch
        val shakeTorchSwitch = findViewById<Switch>(R.id.shake_torch_switch)
        val isTorchEnabled = prefs.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
        shakeTorchSwitch.isChecked = isTorchEnabled
        
        shakeTorchSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, isChecked).apply()
            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            sendBroadcast(intent)
            
            // Show/hide sensitivity container
            findViewById<View>(R.id.shake_sensitivity_container).visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Setup sensitivity seekbar
        val sensitivitySeekBar = findViewById<SeekBar>(R.id.shake_sensitivity_seekbar)
        val sensitivityValueText = findViewById<TextView>(R.id.sensitivity_value_text)
        val currentSensitivity = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5)
        
        sensitivitySeekBar.progress = currentSensitivity - 1
        sensitivityValueText.text = currentSensitivity.toString()
        findViewById<View>(R.id.shake_sensitivity_container).visibility = if (isTorchEnabled) View.VISIBLE else View.GONE
        
        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = progress + 1
                sensitivityValueText.text = sensitivity.toString()
                if (fromUser) {
                    prefs.edit().putInt(Constants.Prefs.SHAKE_SENSITIVITY, sensitivity).apply()
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Support & Feedback Section
        val supportHeader = findViewById<LinearLayout>(R.id.support_header)
        val supportContent = findViewById<LinearLayout>(R.id.support_content)
        val supportArrow = findViewById<TextView>(R.id.support_arrow)
        setupSectionToggle(supportHeader, supportContent, supportArrow)
        
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
    
    private fun loadCurrentSettings(
        gridOption: Button,
        listOption: Button
    ) {
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

    private fun saveSettings(selectedDisplayStyle: String) {
        val editor = prefs.edit()

        editor.putString("view_preference", selectedDisplayStyle)

        editor.commit()

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()

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

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                EXPORT_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        exportSettingsToFile(uri)
                    }
                }
                IMPORT_REQUEST_CODE -> {
                    data?.data?.let { uri ->
                        importSettingsFromFile(uri)
                    }
                }
                HIDDEN_APPS_REQUEST_CODE -> {
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    sendBroadcast(intent)
                }
                WALLPAPER_REQUEST_CODE -> {
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    sendBroadcast(intent)
                    Toast.makeText(this, "Wallpaper changed", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == WALLPAPER_REQUEST_CODE) {
            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            sendBroadcast(intent)
        }
    }

    private fun exportSettingsToFile(uri: Uri) {
        try {
            val settingsJson = JSONObject()
            
            val mainPrefs = prefs.all
            val mainPrefsJson = JSONObject()
            for ((key, value) in mainPrefs) {
                when (value) {
                    is String -> mainPrefsJson.put(key, value)
                    is Boolean -> mainPrefsJson.put(key, value)
                    is Int -> mainPrefsJson.put(key, value)
                    is Long -> mainPrefsJson.put(key, value)
                    is Float -> mainPrefsJson.put(key, value)
                    is Set<*> -> {
                        val jsonArray = JSONArray()
                        value.forEach { jsonArray.put(it) }
                        mainPrefsJson.put(key, jsonArray)
                    }
                }
            }
            settingsJson.put("main_preferences", mainPrefsJson)
            
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
                val isNewFormat = settingsJson.has("main_preferences")
                
                if (isNewFormat) {
                    if (settingsJson.has("main_preferences")) {
                        val mainPrefsJson = settingsJson.getJSONObject("main_preferences")
                        val editor = prefs.edit()
                        importPreferences(mainPrefsJson, editor)
                        editor.apply()
                    }
                    if (settingsJson.has("app_timer_prefs")) {
                        val appTimerPrefs = getSharedPreferences("app_timer_prefs", MODE_PRIVATE)
                        val appTimerJson = settingsJson.getJSONObject("app_timer_prefs")
                        val editor = appTimerPrefs.edit()
                        importPreferences(appTimerJson, editor)
                        editor.apply()
                    }
                    if (settingsJson.has("app_lock_prefs")) {
                        val appLockPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
                        val appLockJson = settingsJson.getJSONObject("app_lock_prefs")
                        val editor = appLockPrefs.edit()
                        importPreferences(appLockJson, editor)
                        editor.apply()
                    }
                } else {
                    val editor = prefs.edit()
                    importPreferences(settingsJson, editor)
                    editor.apply()
                }

                val gridOption = findViewById<Button>(R.id.grid_option)
                val listOption = findViewById<Button>(R.id.list_option)
                loadCurrentSettings(gridOption, listOption)

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

    
    private fun showTutorial() {
        val editor = prefs.edit()
        editor.putBoolean("feature_tutorial_shown", false)
        editor.putInt("feature_tutorial_current_step", 0)
        editor.apply()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("start_tutorial", true)
        }
        startActivity(intent)
        finish()
    }
    
    private fun restartLauncher() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
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
        
        fixDialogTextColors(dialog)
    }
    
    private fun clearCache() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Clear Cache")
            .setMessage("This will clear the launcher's cache files. This may free up storage space but won't affect your settings or data. Continue?")
            .setPositiveButton("Clear Cache") { _, _ ->
                try {
                    var deletedCount = 0
                    var totalSize = 0L
                    
                    fun getDirectorySize(dir: File): Long {
                        var size = 0L
                        if (dir.isDirectory) {
                            dir.listFiles()?.forEach { file ->
                                size += if (file.isDirectory) {
                                    getDirectorySize(file)
                                } else {
                                    file.length()
                                }
                            }
                        } else {
                            size = dir.length()
                        }
                        return size
                    }
                    
                    if (cacheDir.exists() && cacheDir.isDirectory) {
                        val files = cacheDir.listFiles()
                        files?.forEach { file ->
                            try {
                                val size = getDirectorySize(file)
                                if (file.deleteRecursively()) {
                                    deletedCount++
                                    totalSize += size
                                }
                            } catch (e: Exception) {
                            }
                        }
                    }
                    
                    externalCacheDir?.let { extCacheDir ->
                        if (extCacheDir.exists() && extCacheDir.isDirectory) {
                            val files = extCacheDir.listFiles()
                            files?.forEach { file ->
                                try {
                                    val size = getDirectorySize(file)
                                    if (file.deleteRecursively()) {
                                        deletedCount++
                                        totalSize += size
                                    }
                                } catch (e: Exception) {
                                }
                            }
                        }
                    }
                    
                    val sizeInMB = if (totalSize > 0) totalSize / (1024 * 1024) else 0L
                    val message = if (deletedCount > 0) {
                        "Cache cleared successfully. Freed ${sizeInMB}MB from $deletedCount items."
                    } else {
                        "Cache cleared successfully."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to clear cache: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        fixDialogTextColors(dialog)
    }
    
    private fun clearData() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Clear Data")
            .setMessage("WARNING: This will delete ALL launcher data including:\n\n• All settings and preferences\n• Favorite apps\n• Workspaces\n• App locks and timers\n• Todo items\n• Workout data\n• Finance data\n• Widget configurations\n• All other app data\n\nThis action CANNOT be undone. The launcher will restart after clearing data.\n\nAre you absolutely sure?")
            .setPositiveButton("Clear All Data") { _, _ ->
                try {
                    val allPrefs = listOf(
                        "com.guruswarupa.launch.PREFS",
                        "app_timer_prefs",
                        "app_lock_prefs"
                    )
                    
                    allPrefs.forEach { prefName ->
                        try {
                            val prefs = getSharedPreferences(prefName, MODE_PRIVATE)
                            prefs.edit().clear().commit()
                        } catch (e: Exception) {
                        }
                    }
                    
                    try {
                        if (cacheDir.exists() && cacheDir.isDirectory) {
                            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                        }
                        externalCacheDir?.let { extCacheDir ->
                            if (extCacheDir.exists() && extCacheDir.isDirectory) {
                                extCacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            }
                        }
                    } catch (e: Exception) {
                    }
                    
                    try {
                        val databasesDir = File(filesDir.parent, "databases")
                        if (databasesDir.exists() && databasesDir.isDirectory) {
                            databasesDir.listFiles()?.forEach { file ->
                                if (file.name.startsWith(packageName.replace(".", "_"))) {
                                    file.delete()
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }
                    
                    try {
                        if (filesDir.exists() && filesDir.isDirectory) {
                            filesDir.listFiles()?.forEach { file ->
                                if (!file.name.startsWith(".") && file.name != "shared_prefs") {
                                    file.deleteRecursively()
                                }
                            }
                        }
                    } catch (e: Exception) {
                    }
                    
                    Toast.makeText(this, "All data cleared. Launcher will restart.", Toast.LENGTH_LONG).show()
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val packageManager = packageManager
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            val componentName = intent?.component
                            if (componentName != null) {
                                val mainIntent = Intent.makeRestartActivityTask(componentName)
                                startActivity(mainIntent)
                                java.lang.Runtime.getRuntime().exit(0)
                            } else {
                                finish()
                            }
                        } catch (e: Exception) {
                            finish()
                        }
                    }, 1000)
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to clear data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        fixDialogTextColors(dialog)
    }
    
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(this, R.color.text)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        } catch (_: Exception) {}
    }
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
        private const val WALLPAPER_REQUEST_CODE = 456
    }
    
    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivityForResult(intent, WALLPAPER_REQUEST_CODE)
    }
    
    private fun makeSystemBarsTransparent() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setDecorFitsSystemWindows(false)
                
                val decorView = window.decorView
                if (decorView != null) {
                    val insetsController = decorView.windowInsetsController
                    if (insetsController != null) {
                        insetsController.setSystemBarsAppearance(
                            0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
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
                        decorView.systemUiVisibility = flags
                    }
                }
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
            } catch (ex: Exception) {
            }
        }
    }
}