package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.BlurUtils
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.BackTapService
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.ScreenDimmerService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                exportSettingsToFile(uri)
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                importSettingsFromFile(uri)
            }
        }
    }

    private val hiddenAppsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        }
    }

    private val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        setupWallpaper()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make status bar and navigation bar transparent before setContentView
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        
        setContentView(R.layout.activity_settings)
        
        // Ensure system bars are fully configured after view is created
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        // Setup Wallpaper
        setupWallpaper()

        val gridOption = findViewById<Button>(R.id.grid_option)
        val listOption = findViewById<Button>(R.id.list_option)
        val saveButton = findViewById<Button>(R.id.save_settings_button)
        
        // Track selected display style
        val currentStyle = prefs.getString("view_preference", "list") ?: "list"
        var selectedStyle = currentStyle
        
        // Update button states based on current preference
        updateDisplayStyleButtons(gridOption, listOption, selectedStyle)
        
        // Set click listeners for display style buttons
        gridOption.setOnClickListener {
            selectedStyle = "grid"
            updateDisplayStyleButtons(gridOption, listOption, selectedStyle)
        }
        
        listOption.setOnClickListener {
            selectedStyle = "list"
            updateDisplayStyleButtons(gridOption, listOption, selectedStyle)
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

        saveButton.setOnClickListener {
            saveSettings(selectedStyle)
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
            hiddenAppsLauncher.launch(Intent(this, HiddenAppsSettingsActivity::class.java))
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
    
    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        val overlay = findViewById<View>(R.id.settings_overlay)
        
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        if (isDarkMode) {
            overlay.setBackgroundColor("#CC000000".toColorInt()) // Darker overlay for dark mode
        } else {
            overlay.setBackgroundColor("#66FFFFFF".toColorInt()) // Lighter white overlay for light mode
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED)) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(this)
                val wallpaperDrawable = wallpaperManager.drawable
                if (wallpaperDrawable != null) {
                    wallpaperImageView.setImageDrawable(wallpaperDrawable)
                }
            } catch (_: Exception) {
                // Fallback to default wallpaper if anything fails
                wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
            }
        } else {
            // Default wallpaper if permission not granted
            wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
        }
        
        // Apply blur to settings wallpaper if enabled
        applyWallpaperBlur(wallpaperImageView)
    }

    private fun applyWallpaperBlur(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurLevel = prefs.getInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, 50)
            if (blurLevel > 0) {
                val blurRadius = blurLevel.toFloat().coerceAtLeast(1f)
                imageView.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                    )
                )
            } else {
                imageView.setRenderEffect(null)
            }
        }
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
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Launch App Feedback")
            putExtra(Intent.EXTRA_TEXT, deviceInfo)
        }

        try {
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (_: Exception) {
            Toast.makeText(this, "No mail app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode
            "${pInfo.versionName} ($versionCode)"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun setupExpandableSections() {
        // Display Style Header
        val displayStyleHeader = findViewById<LinearLayout>(R.id.display_style_header)
        val displayStyleContent = findViewById<LinearLayout>(R.id.display_style_content)
        val displayStyleArrow = findViewById<TextView>(R.id.display_style_arrow)
        setupSectionToggle(displayStyleHeader, displayStyleContent, displayStyleArrow)
        
        // Wallpaper Header
        val wallpaperHeader = findViewById<LinearLayout>(R.id.wallpaper_header)
        val wallpaperContent = findViewById<LinearLayout>(R.id.wallpaper_content)
        val wallpaperArrow = findViewById<TextView>(R.id.wallpaper_arrow)
        setupSectionToggle(wallpaperHeader, wallpaperContent, wallpaperArrow)
        
        // Setup wallpaper blur slider
        val wallpaperBlurSeekBar = findViewById<SeekBar>(R.id.wallpaper_blur_seekbar)
        val wallpaperBlurValueText = findViewById<TextView>(R.id.wallpaper_blur_value_text)
        
        val blurLevel = prefs.getInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, 50)
        
        wallpaperBlurSeekBar.progress = blurLevel
        wallpaperBlurValueText.text = "$blurLevel%"
        
        wallpaperBlurSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                wallpaperBlurValueText.text = "$progress%"
                if (fromUser) {
                    prefs.edit { putInt(Constants.Prefs.WALLPAPER_BLUR_LEVEL, progress) }
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                    applyWallpaperBlur(findViewById(R.id.wallpaper_background))
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Search Engine Header
        val searchEngineHeader = findViewById<LinearLayout>(R.id.search_engine_header)
        val searchEngineContent = findViewById<LinearLayout>(R.id.search_engine_content)
        val searchEngineArrow = findViewById<TextView>(R.id.search_engine_arrow)
        setupSectionToggle(searchEngineHeader, searchEngineContent, searchEngineArrow)
        setupSearchEngine()

        // Backup Header
        val backupHeader = findViewById<LinearLayout>(R.id.backup_restore_header)
        val backupContent = findViewById<LinearLayout>(R.id.backup_restore_content)
        val backupArrow = findViewById<TextView>(R.id.backup_restore_arrow)
        setupSectionToggle(backupHeader, backupContent, backupArrow)
        
        // Security Header
        val appLockHeader = findViewById<LinearLayout>(R.id.app_lock_header)
        val appLockContent = findViewById<LinearLayout>(R.id.app_lock_content)
        val appLockArrow = findViewById<TextView>(R.id.app_lock_arrow)
        setupSectionToggle(appLockHeader, appLockContent, appLockArrow)
        
        // Permissions Header
        val permissionsHeader = findViewById<LinearLayout>(R.id.permissions_header)
        val permissionsContent = findViewById<LinearLayout>(R.id.permissions_content)
        val permissionsArrow = findViewById<TextView>(R.id.permissions_arrow)
        setupSectionToggle(permissionsHeader, permissionsContent, permissionsArrow)

        // Tutorial Header
        val tutorialHeader = findViewById<LinearLayout>(R.id.tutorial_header)
        val tutorialContent = findViewById<LinearLayout>(R.id.tutorial_content)
        val tutorialArrow = findViewById<TextView>(R.id.tutorial_arrow)
        setupSectionToggle(tutorialHeader, tutorialContent, tutorialArrow)
        
        // Quick Actions Header
        val quickActionsHeader = findViewById<LinearLayout>(R.id.quick_actions_header)
        val quickActionsContent = findViewById<LinearLayout>(R.id.quick_actions_content)
        val quickActionsArrow = findViewById<TextView>(R.id.quick_actions_arrow)
        setupSectionToggle(quickActionsHeader, quickActionsContent, quickActionsArrow)
        
        // Setup torch toggle switch
        val shakeTorchSwitch = findViewById<SwitchCompat>(R.id.shake_torch_switch)
        val isTorchEnabled = prefs.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
        shakeTorchSwitch.isChecked = isTorchEnabled
        
        shakeTorchSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, isChecked) }
            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            // Show/hide sensitivity container
            findViewById<View>(R.id.shake_sensitivity_container).isVisible = isChecked
        }
        
        // Setup sensitivity seekbar
        val sensitivitySeekBar = findViewById<SeekBar>(R.id.shake_sensitivity_seekbar)
        val sensitivityValueText = findViewById<TextView>(R.id.sensitivity_value_text)
        val currentSensitivity = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5)
        
        sensitivitySeekBar.progress = currentSensitivity - 1
        sensitivityValueText.text = currentSensitivity.toString()
        findViewById<View>(R.id.shake_sensitivity_container).isVisible = isTorchEnabled
        
        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = progress + 1
                sensitivityValueText.text = sensitivity.toString()
                if (fromUser) {
                    prefs.edit { putInt(Constants.Prefs.SHAKE_SENSITIVITY, sensitivity) }
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup screen dimmer
        setupScreenDimmer()
        
        // Setup night mode
        setupNightMode()
        
        // Setup flip to dnd
        setupFlipToDnd()
        
        // Setup back tap gestures
        setupBackTap()

        // Support Header
        val supportHeader = findViewById<LinearLayout>(R.id.support_header)
        val supportContent = findViewById<LinearLayout>(R.id.support_content)
        val supportArrow = findViewById<TextView>(R.id.support_arrow)
        setupSectionToggle(supportHeader, supportContent, supportArrow)
        
        // Launcher Header
        val launcherHeader = findViewById<LinearLayout>(R.id.launcher_header)
        val launcherContent = findViewById<LinearLayout>(R.id.launcher_content)
        val launcherArrow = findViewById<TextView>(R.id.launcher_arrow)
        setupSectionToggle(launcherHeader, launcherContent, launcherArrow)
        
    }
    
    private fun setupSearchEngine() {
        val searchEngineSpinner = findViewById<Spinner>(R.id.search_engine_spinner)
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Ecosia", "Brave", "Startpage", "Yahoo", "Qwant")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, engines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        searchEngineSpinner.adapter = adapter

        val currentEngine = prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")
        val index = engines.indexOf(currentEngine)
        if (index >= 0) {
            searchEngineSpinner.setSelection(index)
        }

        searchEngineSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedEngine = engines[position]
                prefs.edit { putString(Constants.Prefs.SEARCH_ENGINE, selectedEngine) }
                
                val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupScreenDimmer() {
        val screenDimmerSwitch = findViewById<SwitchCompat>(R.id.screen_dimmer_switch)
        val dimmerSeekBar = findViewById<SeekBar>(R.id.screen_dimmer_seekbar)
        val dimmerValueText = findViewById<TextView>(R.id.dimmer_value_text)
        val dimmerContainer = findViewById<View>(R.id.screen_dimmer_container)
        
        val isDimmerEnabled = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
        val currentDimLevel = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 10)
        
        screenDimmerSwitch.isChecked = isDimmerEnabled
        dimmerSeekBar.progress = currentDimLevel
        dimmerValueText.text = "$currentDimLevel%"
        dimmerContainer.isVisible = isDimmerEnabled
        
        screenDimmerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    screenDimmerSwitch.isChecked = false
                    AlertDialog.Builder(this, R.style.CustomDialogTheme)
                        .setTitle("Permission Required")
                        .setMessage("Screen Dimmer requires 'Display over other apps' permission to work. Please grant it in the Permissions section.")
                        .setPositiveButton("Go to Permissions") { _, _ ->
                            startActivity(Intent(this, PermissionsActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    prefs.edit { putBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, true) }
                    dimmerContainer.isVisible = true
                    ScreenDimmerService.startService(this, dimmerSeekBar.progress)
                }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false) }
                dimmerContainer.isVisible = false
                ScreenDimmerService.stopService(this)
            }
        }
        
        dimmerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dimmerValueText.text = "$progress%"
                if (fromUser) {
                    prefs.edit { putInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, progress) }
                    if (screenDimmerSwitch.isChecked) {
                        ScreenDimmerService.updateDimLevel(this@SettingsActivity, progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupNightMode() {
        val nightModeSwitch = findViewById<SwitchCompat>(R.id.night_mode_switch)
        val nightModeSeekBar = findViewById<SeekBar>(R.id.night_mode_intensity_seekbar)
        val nightModeValueText = findViewById<TextView>(R.id.night_mode_value_text)
        val nightModeContainer = findViewById<View>(R.id.night_mode_container)
        
        val isNightModeEnabled = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
        val currentIntensity = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
        
        nightModeSwitch.isChecked = isNightModeEnabled
        nightModeSeekBar.progress = currentIntensity
        nightModeValueText.text = "$currentIntensity%"
        nightModeContainer.isVisible = isNightModeEnabled
        
        nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    nightModeSwitch.isChecked = false
                    AlertDialog.Builder(this, R.style.CustomDialogTheme)
                        .setTitle("Permission Required")
                        .setMessage("Night Mode requires 'Display over other apps' permission to work. Please grant it in the Permissions section.")
                        .setPositiveButton("Go to Permissions") { _, _ ->
                            startActivity(Intent(this, PermissionsActivity::class.java))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    prefs.edit { putBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, true) }
                    nightModeContainer.isVisible = true
                    NightModeService.startService(this, nightModeSeekBar.progress)
                }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false) }
                nightModeContainer.isVisible = false
                NightModeService.stopService(this)
            }
        }
        
        nightModeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                nightModeValueText.text = "$progress%"
                if (fromUser) {
                    prefs.edit { putInt(Constants.Prefs.NIGHT_MODE_INTENSITY, progress) }
                    if (nightModeSwitch.isChecked) {
                        NightModeService.updateIntensity(this@SettingsActivity, progress)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupFlipToDnd() {
        val flipDndSwitch = findViewById<SwitchCompat>(R.id.flip_dnd_switch)
        val isFlipEnabled = prefs.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
        
        flipDndSwitch.isChecked = isFlipEnabled
        
        flipDndSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    flipDndSwitch.isChecked = false
                    AlertDialog.Builder(this, R.style.CustomDialogTheme)
                        .setTitle("DND Access Required")
                        .setMessage("Flip to DND requires Do Not Disturb access to change the DND state. Please grant it in the settings.")
                        .setPositiveButton("Grant Access") { _, _ ->
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    prefs.edit { putBoolean(Constants.Prefs.FLIP_DND_ENABLED, true) }
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.FLIP_DND_ENABLED, false) }
                val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        }
    }
    
    private fun setupSectionToggle(header: LinearLayout, content: LinearLayout, arrow: TextView) {
        header.setOnClickListener {
            val isExpanded = content.isVisible
            content.isVisible = !isExpanded
            arrow.text = if (!isExpanded) "▲" else "▼"
        }
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
        prefs.edit {
            putString("view_preference", selectedDisplayStyle)
        }

        Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()

        val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
        intent.setPackage(packageName)
        sendBroadcast(intent)

        finish()
    }

    private fun exportSettings() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "launch_settings_backup.json")
        }
        exportLauncher.launch(intent)
    }

    private fun importSettings() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher.launch(intent)
    }

    private val sensitiveMainKeys = setOf(
        "weather_api_key"
    )

    private val sensitiveAppLockKeys = setOf(
        "pin_hash",
        "pin_salt",
        "last_auth_time",
        "fingerprint_enabled"
    )

    private fun exportSettingsToFile(uri: Uri) {
        try {
            val settingsJson = JSONObject()
            
            val mainPrefs = prefs.all
            val mainPrefsJson = JSONObject()
            for ((key, value) in mainPrefs) {
                if (key in sensitiveMainKeys) continue
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
                    if (key in sensitiveAppLockKeys) continue
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
            
            // Export physical activity data
            exportPhysicalActivityData(settingsJson)
            
            // Export workout tracker data
            exportWorkoutData(settingsJson)
            
            // Export todo list data
            exportTodoData(settingsJson)
            
            // Export finance tracker data
            exportFinanceData(settingsJson)

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
                        prefs.edit {
                            importPreferences(mainPrefsJson, this)
                        }
                    }
                    if (settingsJson.has("app_timer_prefs")) {
                        val appTimerPrefs = getSharedPreferences("app_timer_prefs", MODE_PRIVATE)
                        val appTimerJson = settingsJson.getJSONObject("app_timer_prefs")
                        appTimerPrefs.edit {
                            importPreferences(appTimerJson, this)
                        }
                    }
                    if (settingsJson.has("app_lock_prefs")) {
                        val appLockPrefs = getSharedPreferences("app_lock_prefs", MODE_PRIVATE)
                        val appLockJson = settingsJson.getJSONObject("app_lock_prefs")
                        appLockPrefs.edit {
                            importPreferences(appLockJson, this)
                        }
                    }
                    
                    // Import physical activity data
                    if (settingsJson.has("physical_activity_data")) {
                        importPhysicalActivityData(settingsJson.getJSONObject("physical_activity_data"))
                    }
                    
                    // Import workout data
                    if (settingsJson.has("workout_data")) {
                        importWorkoutData(settingsJson.getJSONObject("workout_data"))
                    }
                    
                    // Import todo data
                    if (settingsJson.has("todo_data")) {
                        importTodoData(settingsJson.getJSONObject("todo_data"))
                    }
                    
                    // Import finance data
                    if (settingsJson.has("finance_data")) {
                        importFinanceData(settingsJson.getJSONObject("finance_data"))
                    }
                } else {
                    prefs.edit {
                        importPreferences(settingsJson, this)
                    }
                }

                val gridOption = findViewById<Button>(R.id.grid_option)
                val listOption = findViewById<Button>(R.id.list_option)
                updateDisplayStyleButtons(gridOption, listOption, prefs.getString("view_preference", "list") ?: "list")

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
            when (val value = prefsJson.get(key)) {
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
        prefs.edit {
            putBoolean("feature_tutorial_shown", false)
            putInt("feature_tutorial_current_step", 0)
        }
        
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
                            } catch (_: Exception) {
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
                                } catch (_: Exception) {
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
            .setMessage("WARNING: This will delete ALL launcher data including:\n\n• All settings and preferences\n• Favorite apps\n• Workspaces\n• App locks and timers\n• Todo items\n• Workout data\n• Finance data\n• Physical activity data\n• Widget configurations\n• All other app data\n\nThis action CANNOT be undone. The launcher will restart after clearing data.\n\nAre you absolutely sure?")
            .setPositiveButton("Clear All Data") { _, _ ->
                try {
                    val allPrefs = listOf(
                        "com.guruswarupa.launch.PREFS",
                        "app_timer_prefs",
                        "app_lock_prefs"
                    )
                    
                    allPrefs.forEach { prefName ->
                        try {
                            getSharedPreferences(prefName, MODE_PRIVATE).edit { clear() }
                        } catch (_: Exception) {
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
                    } catch (_: Exception) {
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
                    } catch (_: Exception) {
                    }
                    
                    try {
                        if (filesDir.exists() && filesDir.isDirectory) {
                            filesDir.listFiles()?.forEach { file ->
                                if (!file.name.startsWith(".") && file.name != "shared_prefs") {
                                    file.deleteRecursively()
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                    
                    Toast.makeText(this, "All data cleared. Launcher will restart.", Toast.LENGTH_LONG).show()
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            val componentName = intent?.component
                            if (componentName != null) {
                                val mainIntent = Intent.makeRestartActivityTask(componentName)
                                startActivity(mainIntent)
                                Runtime.getRuntime().exit(0)
                            } else {
                                finish()
                            }
                        } catch (_: Exception) {
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
    
    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        wallpaperLauncher.launch(intent)
    }
    
    private fun makeSystemBarsTransparent() {
        try {
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
                
                WindowCompat.setDecorFitsSystemWindows(window, false)
                
                val insetsController = window.decorView.windowInsetsController
                if (insetsController != null) {
                    val appearance = if (!isDarkMode) {
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    } else {
                        0
                    }
                    insetsController.setSystemBarsAppearance(
                        appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                
                @Suppress("DEPRECATION")
                val decorView = window.decorView
                @Suppress("DEPRECATION")
                var flags = decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                
                if (!isDarkMode) {
                    @Suppress("DEPRECATION")
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        @Suppress("DEPRECATION")
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = flags
            }
            
            // Apply blur effect to status bar
            BlurUtils.applyBlurToStatusBar(this)
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
                // Apply blur effect as fallback
                BlurUtils.applyBlurToStatusBar(this)
            } catch (_: Exception) {
            }
        }
    }
    
    /**
     * Export physical activity data including steps, distance, and historical data
     */
    private fun exportPhysicalActivityData(settingsJson: JSONObject) {
        try {
            val activityPrefs = getSharedPreferences("physical_activity_prefs", MODE_PRIVATE)
            val activityAll = activityPrefs.all
            if (activityAll.isNotEmpty()) {
                val activityJson = JSONObject()
                for ((key, value) in activityAll) {
                    when (value) {
                        is String -> activityJson.put(key, value)
                        is Boolean -> activityJson.put(key, value)
                        is Int -> activityJson.put(key, value)
                        is Long -> activityJson.put(key, value)
                        is Float -> activityJson.put(key, value)
                        is Set<*> -> {
                            val jsonArray = JSONArray()
                            value.forEach { jsonArray.put(it) }
                            activityJson.put(key, jsonArray)
                        }
                    }
                }
                settingsJson.put("physical_activity_data", activityJson)
            }
        } catch (_: Exception) {
            // Silently fail if physical activity data is not available
        }
    }
    
    /**
     * Export workout tracker data including exercises and workout history
     */
    private fun exportWorkoutData(settingsJson: JSONObject) {
        try {
            val workoutPrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
            val exercisesString = workoutPrefs.getString("workout_exercises", null)
            val lastResetDate = workoutPrefs.getString("workout_last_reset_date", null)
            val streak = workoutPrefs.getInt("workout_streak", 0)
            val lastStreakDate = workoutPrefs.getString("workout_last_streak_date", null)
            
            if (exercisesString != null) {
                val workoutJson = JSONObject()
                workoutJson.put("exercises", exercisesString)
                if (lastResetDate != null) {
                    workoutJson.put("last_reset_date", lastResetDate)
                }
                workoutJson.put("streak", streak)
                if (lastStreakDate != null) {
                    workoutJson.put("last_streak_date", lastStreakDate)
                }
                settingsJson.put("workout_data", workoutJson)
            }
        } catch (_: Exception) {
            // Silently fail if workout data is not available
        }
    }
    
    /**
     * Export todo list data including tasks and their status
     */
    private fun exportTodoData(settingsJson: JSONObject) {
        try {
            val todoPrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
            val todoItemsString = todoPrefs.getString("todo_items", null)
            
            if (todoItemsString != null) {
                val todoJson = JSONObject()
                todoJson.put("todo_items", todoItemsString)
                settingsJson.put("todo_data", todoJson)
            }
        } catch (_: Exception) {
            // Silently fail if todo data is not available
        }
    }
    
    /**
     * Export finance tracker data including transactions and balance
     */
    private fun exportFinanceData(settingsJson: JSONObject) {
        try {
            val financePrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
            val financeJson = JSONObject()
            
            // Export balance and currency
            val balance = financePrefs.getFloat("finance_balance", 0.0f)
            val currency = financePrefs.getString("finance_currency", "USD")
            financeJson.put("balance", balance.toDouble())
            if (currency != null) {
                financeJson.put("currency", currency)
            }
            
            // Export monthly income/expense data
            val allPrefs = financePrefs.all
            val monthlyData = JSONObject()
            allPrefs.keys.filter { it.startsWith("finance_income_") || it.startsWith("finance_expenses_") }
                .forEach { key ->
                    val value = allPrefs[key]
                    if (value is Float) {
                        monthlyData.put(key, value.toDouble())
                    }
                }
            if (monthlyData.length() > 0) {
                financeJson.put("monthly_data", monthlyData)
            }
            
            // Export transaction history
            val transactions = JSONArray()
            allPrefs.keys.filter { it.startsWith("transaction_") }
                .forEach { key ->
                    val transactionData = financePrefs.getString(key, null)
                    if (transactionData != null) {
                        transactions.put(transactionData)
                    }
                }
            if (transactions.length() > 0) {
                financeJson.put("transactions", transactions)
            }
            
            if (financeJson.length() > 0) {
                settingsJson.put("finance_data", financeJson)
            }
        } catch (_: Exception) {
            // Silently fail if finance data is not available
        }
    }
    
    /**
     * Import physical activity data
     */
    private fun importPhysicalActivityData(activityJson: JSONObject) {
        try {
            val activity_prefs = getSharedPreferences("physical_activity_prefs", MODE_PRIVATE)
            activity_prefs.edit {
                importPreferences(activityJson, this)
            }
        } catch (_: Exception) {
            // Silently fail if import fails
        }
    }
    
    /**
     * Import workout tracker data
     */
    private fun importWorkoutData(workoutJson: JSONObject) {
        try {
            val workoutPrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
            workoutPrefs.edit {
                if (workoutJson.has("exercises")) {
                    putString("workout_exercises", workoutJson.getString("exercises"))
                }
                if (workoutJson.has("last_reset_date")) {
                    putString("workout_last_reset_date", workoutJson.getString("last_reset_date"))
                }
                if (workoutJson.has("streak")) {
                    putInt("workout_streak", workoutJson.getInt("streak"))
                }
                if (workoutJson.has("last_streak_date")) {
                    putString("workout_last_streak_date", workoutJson.getString("last_streak_date"))
                }
            }
        } catch (_: Exception) {
            // Silently fail if import fails
        }
    }
    
    /**
     * Import todo list data
     */
    private fun importTodoData(todoJson: JSONObject) {
        try {
            val todoPrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
            todoPrefs.edit {
                if (todoJson.has("todo_items")) {
                    putString("todo_items", todoJson.getString("todo_items"))
                }
            }
        } catch (_: Exception) {
            // Silently fail if import fails
        }
    }
    
    /**
     * Import finance tracker data
     */
    private fun importFinanceData(financeJson: JSONObject) {
        try {
            val financePrefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
            financePrefs.edit {
                // Import balance and currency
                if (financeJson.has("balance")) {
                    putFloat("finance_balance", financeJson.getDouble("balance").toFloat())
                }
                if (financeJson.has("currency")) {
                    putString("finance_currency", financeJson.getString("currency"))
                }
                
                // Import monthly data
                if (financeJson.has("monthly_data")) {
                    val monthlyData = financeJson.getJSONObject("monthly_data")
                    val keys = monthlyData.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        putFloat(key, monthlyData.getDouble(key).toFloat())
                    }
                }
                
                // Import transactions
                if (financeJson.has("transactions")) {
                    val transactions = financeJson.getJSONArray("transactions")
                    for (i in 0 until transactions.length()) {
                        val transactionData = transactions.getString(i)
                        // Generate a unique key for each transaction
                        val timestamp = System.currentTimeMillis() + i
                        putString("transaction_$timestamp", transactionData)
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fail if import fails
        }
    }
    
    /**
     * Setup back tap gesture settings
     */
    private fun setupBackTap() {
        val backTapSwitch = findViewById<SwitchCompat>(R.id.back_tap_switch)
        val backTapSettingsContainer = findViewById<View>(R.id.back_tap_settings_container)
        val doubleActionSpinner = findViewById<android.widget.Spinner>(R.id.back_tap_double_action_spinner)
        val sensitivitySeekBar = findViewById<SeekBar>(R.id.back_tap_sensitivity_seekbar)
        val sensitivityValueText = findViewById<TextView>(R.id.back_tap_sensitivity_value)
        
        // Set default values if not set
        if (!prefs.contains(Constants.Prefs.BACK_TAP_ENABLED)) {
            prefs.edit { 
                putBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
                putString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, BackTapService.ACTION_SOUND_TOGGLE)
                putInt(Constants.Prefs.BACK_TAP_SENSITIVITY, 7) // More sensitive by default
            }
        }
        
        val isBackTapEnabled = prefs.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
        val currentDoubleAction = prefs.getString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, BackTapService.ACTION_SOUND_TOGGLE)
            ?: BackTapService.ACTION_SOUND_TOGGLE
        val currentSensitivity = prefs.getInt(Constants.Prefs.BACK_TAP_SENSITIVITY, 7)
        
        backTapSwitch.isChecked = isBackTapEnabled
        backTapSettingsContainer.isVisible = isBackTapEnabled
        sensitivitySeekBar.progress = currentSensitivity - 1
        sensitivityValueText.text = currentSensitivity.toString()
        
        // Setup actions
        val actions = arrayOf(
            "None",
            "Toggle Torch",
            "Take Screenshot",
            "Toggle Notifications",
            "Turn Screen Off",
            "Toggle Sound"
        )
        val actionValues = arrayOf(
            BackTapService.ACTION_NONE,
            BackTapService.ACTION_TORCH_TOGGLE,
            BackTapService.ACTION_SCREENSHOT,
            BackTapService.ACTION_NOTIFICATIONS,
            BackTapService.ACTION_SCREEN_OFF,
            BackTapService.ACTION_SOUND_TOGGLE
        )
        
        val actionAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, actions)
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        doubleActionSpinner.adapter = actionAdapter
        
        // Set current action selections
        val doubleActionIndex = actionValues.indexOf(currentDoubleAction)
        if (doubleActionIndex >= 0) {
            doubleActionSpinner.setSelection(doubleActionIndex)
        }
        
        // Setup switch listener
        backTapSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.BACK_TAP_ENABLED, isChecked) }
            backTapSettingsContainer.isVisible = isChecked
            
            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        }
        
        // Setup action spinner listeners
        doubleActionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedAction = actionValues[position]
                prefs.edit { putString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, selectedAction) }
                val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        
        // Setup sensitivity seekbar
        sensitivitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = progress + 1
                sensitivityValueText.text = sensitivity.toString()
                if (fromUser) {
                    prefs.edit { putInt(Constants.Prefs.BACK_TAP_SENSITIVITY, sensitivity) }
                    val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
                    intent.setPackage(packageName)
                    sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
