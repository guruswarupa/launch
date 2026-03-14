package com.guruswarupa.launch.ui.activities

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import com.guruswarupa.launch.managers.DownloadableFontManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.BackTapService
import com.guruswarupa.launch.services.NightModeService
import com.guruswarupa.launch.services.ScreenDimmerService
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SettingsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_SETTINGS_TUTORIAL = "start_settings_tutorial"
    }

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val vaultManager by lazy { EncryptedFolderManager(this) }
    private var settingsTutorialStepIndex = 0
    private var settingsTutorialActive = false

    private data class SettingsTutorialStep(
        val title: String,
        val description: String,
        val targetViewId: Int
    )

    private val settingsTutorialSteps = listOf(
        SettingsTutorialStep("Interface", "Customize layouts, grid sizes, and icon shapes.", R.id.display_style_header),
        SettingsTutorialStep("Wallpaper", "Adjust background blur for a modern look.", R.id.wallpaper_header),
        SettingsTutorialStep("Typography", "Change fonts and text styling.", R.id.typography_header),
        SettingsTutorialStep("Gestures", "Enable Back Tap and Shake shortcuts.", R.id.quick_actions_header),
        SettingsTutorialStep("Security", "Manage App Lock and your Encrypted Vault.", R.id.app_lock_header),
        SettingsTutorialStep("System", "Backup, Restore, and Launcher maintenance.", R.id.backup_restore_header)
    )
    
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { exportSettingsToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { importSettingsFromFile(it) }
    }

    private val wallpaperLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notifySettingsChanged()
        setupWallpaper()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge for transparent system bars with white icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        
        setContentView(R.layout.activity_settings)
        applyContentInsets()

        setupWallpaper()
        setupAppearanceSection()
        setupActionsSection()
        setupSecuritySection()
        setupMaintenanceSection()
        setupSupportSection()
        setupVersionInfo()

        findViewById<View>(R.id.save_settings_button).setOnClickListener {
            Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
            finish()
        }

        if (intent.getBooleanExtra(EXTRA_START_SETTINGS_TUTORIAL, false)) {
            window.decorView.post { startSettingsTutorial() }
        }
    }
    
    private fun setupVersionInfo() {
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            findViewById<TextView>(R.id.version_text)?.text = "v$version"
        } catch (_: Exception) {
            findViewById<TextView>(R.id.version_text)?.text = "v1.0"
        }
    }

    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperImageView)
    }

    private fun setupAppearanceSection() {
        val displayHeader = findViewById<LinearLayout>(R.id.display_style_header)
        val displayContent = findViewById<LinearLayout>(R.id.display_style_content)
        val displayArrow = findViewById<TextView>(R.id.display_style_arrow)
        setupSectionToggle(displayHeader, displayContent, displayArrow)

        val gridBtn = findViewById<Button>(R.id.grid_option)
        val listBtn = findViewById<Button>(R.id.list_option)
        val gridSection = findViewById<LinearLayout>(R.id.grid_columns_section)
        val gridValue = findViewById<TextView>(R.id.grid_columns_value)
        val gridSeek = findViewById<SeekBar>(R.id.grid_columns_seekbar)
        
        val minCols = resources.getInteger(R.integer.grid_columns_min)
        val maxCols = resources.getInteger(R.integer.grid_columns_max)
        var selectedCols = prefs.getInt(Constants.Prefs.GRID_COLUMNS, resources.getInteger(R.integer.app_grid_columns))
            .coerceIn(minCols, maxCols)

        var selectedStyle = prefs.getString("view_preference", "list") ?: "list"

        gridSeek.max = maxCols - minCols
        gridSeek.progress = selectedCols - minCols
        gridValue.text = getString(R.string.apps_per_row_format, selectedCols)
        
        gridSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                selectedCols = minCols + p
                gridValue.text = getString(R.string.apps_per_row_format, selectedCols)
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.GRID_COLUMNS, selectedCols) }
                    notifySettingsChanged()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        updateDisplayStyleButtons(gridBtn, listBtn, selectedStyle)
        gridSection.isVisible = selectedStyle == "grid"
        
        gridBtn.setOnClickListener {
            selectedStyle = "grid"
            updateDisplayStyleButtons(gridBtn, listBtn, "grid")
            gridSection.isVisible = true
            prefs.edit { putString("view_preference", "grid") }
            notifySettingsChanged()
        }

        listBtn.setOnClickListener {
            selectedStyle = "list"
            updateDisplayStyleButtons(gridBtn, listBtn, "list")
            gridSection.isVisible = false
            prefs.edit { putString("view_preference", "list") }
            notifySettingsChanged()
        }

        // Icons
        val iconSpinner = findViewById<Spinner>(R.id.icon_style_spinner)
        val iconSeek = findViewById<SeekBar>(R.id.icon_size_seekbar)
        val iconVal = findViewById<TextView>(R.id.icon_size_value)
        
        val shapes = arrayOf("Round", "Squircle", "Squared", "Teardrop", "Vortex", "Overlay")
        val values = arrayOf("round", "squircle", "squared", "teardrop", "vortex", "overlay")
        iconSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, shapes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        iconSpinner.setSelection(values.indexOf(prefs.getString(Constants.Prefs.ICON_STYLE, "squircle")).coerceAtLeast(0))
        iconSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.ICON_STYLE, values[pos]) }
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        
        val currentSize = prefs.getInt(Constants.Prefs.ICON_SIZE, 40)
        iconSeek.max = 60 // 36 to 96
        iconSeek.progress = currentSize - 36
        iconVal.text = "${currentSize}dp"
        iconSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                val size = p + 36
                iconVal.text = "${size}dp"
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.ICON_SIZE, size) }
                    notifySettingsChanged()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Wallpaper Blur Section
        val wallHeader = findViewById<LinearLayout>(R.id.wallpaper_header)
        val wallContent = findViewById<LinearLayout>(R.id.wallpaper_content)
        val wallArrow = findViewById<TextView>(R.id.wallpaper_arrow)
        setupSectionToggle(wallHeader, wallContent, wallArrow)
        findViewById<View>(R.id.change_wallpaper_button).setOnClickListener { chooseWallpaper() }

        // Blur controls removed - no blur effect

        // Typography
        val typoHeader = findViewById<LinearLayout>(R.id.typography_header)
        val typoContent = findViewById<LinearLayout>(R.id.typography_content)
        val typoArrow = findViewById<TextView>(R.id.typography_arrow)
        setupSectionToggle(typoHeader, typoContent, typoArrow)
        setupTypographySettings()

        findViewById<SwitchCompat>(R.id.clock_24_hour_switch).apply {
            isChecked = prefs.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit { putBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, isChecked) }
                notifySettingsChanged()
            }
        }
    }

    private fun setupActionsSection() {
        val wHeader = findViewById<LinearLayout>(R.id.widgets_settings_header)
        val wContent = findViewById<LinearLayout>(R.id.widgets_settings_content)
        val wArrow = findViewById<TextView>(R.id.widgets_settings_arrow)
        setupSectionToggle(wHeader, wContent, wArrow)
        findViewById<View>(R.id.configure_widgets_button).setOnClickListener {
            startActivity(Intent(this, WidgetConfigurationActivity::class.java))
        }

        val sHeader = findViewById<LinearLayout>(R.id.search_engine_header)
        val sContent = findViewById<LinearLayout>(R.id.search_engine_content)
        val sArrow = findViewById<TextView>(R.id.search_engine_arrow)
        setupSectionToggle(sHeader, sContent, sArrow)
        setupSearchEngine()

        val qHeader = findViewById<LinearLayout>(R.id.quick_actions_header)
        val qContent = findViewById<LinearLayout>(R.id.quick_actions_content)
        val qArrow = findViewById<TextView>(R.id.quick_actions_arrow)
        setupSectionToggle(qHeader, qContent, qArrow)

        setupAccessibilityShortcut()
        setupBackTap()
        setupShakeTorch()
        setupExtraDimmer()
        setupNightMode()
        setupFlipToDnd()

        findViewById<View>(R.id.config_control_center_button).setOnClickListener {
            startActivity(Intent(this, ControlCenterConfigActivity::class.java))
        }
    }

    private fun setupSecuritySection() {
        val h = findViewById<LinearLayout>(R.id.app_lock_header)
        val c = findViewById<LinearLayout>(R.id.app_lock_content)
        val a = findViewById<TextView>(R.id.app_lock_arrow)
        setupSectionToggle(h, c, a)

        findViewById<View>(R.id.app_lock_button).setOnClickListener { startActivity(Intent(this, AppLockSettingsActivity::class.java)) }
        findViewById<View>(R.id.hidden_apps_button).setOnClickListener { startActivity(Intent(this, HiddenAppsSettingsActivity::class.java)) }
        findViewById<View>(R.id.privacy_dashboard_button).setOnClickListener { startActivity(Intent(this, PrivacyDashboardActivity::class.java)) }
    }

    private fun setupMaintenanceSection() {
        val bHeader = findViewById<LinearLayout>(R.id.backup_restore_header)
        val bContent = findViewById<LinearLayout>(R.id.backup_restore_content)
        val bArrow = findViewById<TextView>(R.id.backup_restore_arrow)
        setupSectionToggle(bHeader, bContent, bArrow)

        findViewById<View>(R.id.export_settings_button).setOnClickListener { exportSettings() }
        findViewById<View>(R.id.import_settings_button).setOnClickListener { importSettings() }

        val lHeader = findViewById<LinearLayout>(R.id.launcher_header)
        val lContent = findViewById<LinearLayout>(R.id.launcher_content)
        val lArrow = findViewById<TextView>(R.id.launcher_arrow)
        setupSectionToggle(lHeader, lContent, lArrow)

        findViewById<View>(R.id.restart_launcher_button).setOnClickListener { restartLauncher() }
        findViewById<View>(R.id.clear_cache_button).setOnClickListener { clearCache() }
        findViewById<View>(R.id.clear_data_button).setOnClickListener { clearData() }

        val pHeader = findViewById<LinearLayout>(R.id.permissions_header)
        val pContent = findViewById<LinearLayout>(R.id.permissions_content)
        val pArrow = findViewById<TextView>(R.id.permissions_arrow)
        setupSectionToggle(pHeader, pContent, pArrow)

        findViewById<View>(R.id.check_permissions_button).setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        findViewById<View>(R.id.show_tutorial_button).setOnClickListener { showTutorial() }
    }

    private fun setupSupportSection() {
        val h = findViewById<LinearLayout>(R.id.support_header)
        val c = findViewById<LinearLayout>(R.id.support_content)
        val a = findViewById<TextView>(R.id.support_arrow)
        setupSectionToggle(h, c, a)
        findViewById<View>(R.id.feedback_button).setOnClickListener { sendFeedback() }
        findViewById<TextView>(R.id.github_links_text).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = Html.fromHtml(getString(R.string.github_project_links), Html.FROM_HTML_MODE_COMPACT)
        }
        findViewById<TextView>(R.id.sponsor_github_link).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = Html.fromHtml(getString(R.string.sponsor_github_link), Html.FROM_HTML_MODE_COMPACT)
        }
        findViewById<TextView>(R.id.buy_me_a_coffee_link).apply {
            movementMethod = LinkMovementMethod.getInstance()
            text = Html.fromHtml(getString(R.string.buy_me_a_coffee_link), Html.FROM_HTML_MODE_COMPACT)
        }
    }

    private fun setupSectionToggle(header: View, content: View, arrow: TextView) {
        header.setOnClickListener {
            val visible = content.visibility == View.VISIBLE
            content.visibility = if (visible) View.GONE else View.VISIBLE
            arrow.animate().rotation(if (visible) 0f else 180f).setDuration(250).start()
        }
    }

    private fun updateDisplayStyleButtons(grid: Button, list: Button, style: String) {
        val isGrid = style == "grid"
        grid.alpha = if (isGrid) 1.0f else 0.4f
        grid.setBackgroundResource(if (isGrid) R.drawable.settings_card_background else 0)
        list.alpha = if (!isGrid) 1.0f else 0.4f
        list.setBackgroundResource(if (!isGrid) R.drawable.settings_card_background else 0)
    }

    private fun setupAccessibilityShortcut() {
        val sw = findViewById<SwitchCompat>(R.id.accessibility_shortcut_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.ACCESSIBILITY_SHORTCUT_ENABLED, false)
        sw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasAccessibilityServicePermission()) {
                sw.isChecked = false
                showPermissionDialog("Accessibility Required", "Enable Launch in Accessibility settings.") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.ACCESSIBILITY_SHORTCUT_ENABLED, isChecked) }
                startService(Intent(this, ScreenLockAccessibilityService::class.java).apply {
                    action = "TOGGLE_SHORTCUT"
                    putExtra("enabled", isChecked)
                })
            }
        }
    }

    private fun setupBackTap() {
        val sw = findViewById<SwitchCompat>(R.id.back_tap_switch)
        val cont = findViewById<View>(R.id.back_tap_settings_container)
        val spin = findViewById<Spinner>(R.id.back_tap_double_action_spinner)
        val seek = findViewById<SeekBar>(R.id.back_tap_sensitivity_seekbar)
        val valT = findViewById<TextView>(R.id.back_tap_sensitivity_value)

        val en = prefs.getBoolean(Constants.Prefs.BACK_TAP_ENABLED, false)
        sw.isChecked = en
        cont.isVisible = en

        val acts = arrayOf("None", "Torch", "Screenshot", "Notifications", "Screen Off", "Sound")
        val vals = arrayOf(BackTapService.ACTION_NONE, BackTapService.ACTION_TORCH_TOGGLE, BackTapService.ACTION_SCREENSHOT, BackTapService.ACTION_NOTIFICATIONS, BackTapService.ACTION_SCREEN_OFF, BackTapService.ACTION_SOUND_TOGGLE)
        spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, acts).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spin.setSelection(vals.indexOf(prefs.getString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, BackTapService.ACTION_SOUND_TOGGLE)).coerceAtLeast(0))

        val cur = prefs.getInt(Constants.Prefs.BACK_TAP_SENSITIVITY, 7).coerceIn(1, 10)
        seek.max = 9
        seek.progress = cur - 1
        valT.text = cur.toString()

        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.BACK_TAP_ENABLED, isChecked) }
            cont.isVisible = isChecked
            notifySettingsChanged()
        }
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.BACK_TAP_DOUBLE_ACTION, vals[pos]) }
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valT.text = (p + 1).toString()
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.BACK_TAP_SENSITIVITY, p + 1) }
                    notifySettingsChanged()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupShakeTorch() {
        val sw = findViewById<SwitchCompat>(R.id.shake_torch_switch)
        val cont = findViewById<View>(R.id.shake_sensitivity_container)
        val seek = findViewById<SeekBar>(R.id.shake_sensitivity_seekbar)
        val valT = findViewById<TextView>(R.id.sensitivity_value_text)

        val en = prefs.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
        sw.isChecked = en
        cont.isVisible = en
        val cur = prefs.getInt(Constants.Prefs.SHAKE_SENSITIVITY, 5).coerceIn(1, 10)
        seek.max = 9
        seek.progress = cur - 1
        valT.text = cur.toString()

        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, isChecked) }
            cont.isVisible = isChecked
            notifySettingsChanged()
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valT.text = (p + 1).toString()
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.SHAKE_SENSITIVITY, p + 1) }
                    notifySettingsChanged()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupExtraDimmer() {
        val sw = findViewById<SwitchCompat>(R.id.screen_dimmer_switch)
        val seek = findViewById<SeekBar>(R.id.screen_dimmer_seekbar)
        val valT = findViewById<TextView>(R.id.dimmer_value_text)
        val cont = findViewById<View>(R.id.screen_dimmer_container)
        
        val en = prefs.getBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, false)
        sw.isChecked = en
        seek.progress = prefs.getInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, 10)
        valT.text = "${seek.progress}%"
        cont.isVisible = en

        sw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                sw.isChecked = false
                showPermissionDialog("Overlay Permission", "Enable Display over other apps.") { startActivity(Intent(this, PermissionsActivity::class.java)) }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.SCREEN_DIMMER_ENABLED, isChecked) }
                cont.isVisible = isChecked
                if (isChecked) ScreenDimmerService.startService(this, seek.progress) else ScreenDimmerService.stopService(this)
            }
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valT.text = "$p%"
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.SCREEN_DIMMER_LEVEL, p) }
                    if (sw.isChecked) ScreenDimmerService.updateDimLevel(this@SettingsActivity, p)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupNightMode() {
        val sw = findViewById<SwitchCompat>(R.id.night_mode_switch)
        val seek = findViewById<SeekBar>(R.id.night_mode_intensity_seekbar)
        val valT = findViewById<TextView>(R.id.night_mode_value_text)
        val cont = findViewById<View>(R.id.night_mode_container)
        
        val en = prefs.getBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, false)
        sw.isChecked = en
        seek.progress = prefs.getInt(Constants.Prefs.NIGHT_MODE_INTENSITY, 10)
        valT.text = "${seek.progress}%"
        cont.isVisible = en

        sw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                sw.isChecked = false
                showPermissionDialog("Overlay Permission", "Enable Display over other apps.") { startActivity(Intent(this, PermissionsActivity::class.java)) }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.NIGHT_MODE_ENABLED, isChecked) }
                cont.isVisible = isChecked
                if (isChecked) NightModeService.startService(this, seek.progress) else NightModeService.stopService(this)
            }
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valT.text = "$p%"
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.NIGHT_MODE_INTENSITY, p) }
                    if (sw.isChecked) NightModeService.updateIntensity(this@SettingsActivity, p)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun setupFlipToDnd() {
        val sw = findViewById<SwitchCompat>(R.id.flip_dnd_switch)
        sw.isChecked = prefs.getBoolean(Constants.Prefs.FLIP_DND_ENABLED, false)
        sw.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (!nm.isNotificationPolicyAccessGranted) {
                    sw.isChecked = false
                    showPermissionDialog("DND Permission", "Grant DND access to use this feature.") { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
                } else {
                    prefs.edit { putBoolean(Constants.Prefs.FLIP_DND_ENABLED, true) }
                    notifySettingsChanged()
                }
            } else {
                prefs.edit { putBoolean(Constants.Prefs.FLIP_DND_ENABLED, false) }
                notifySettingsChanged()
            }
        }
    }

    private fun setupSearchEngine() {
        val s = findViewById<Spinner>(R.id.search_engine_spinner)
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Ecosia", "Brave", "Startpage", "Yahoo", "Qwant")
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, engines).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        s.setSelection(engines.indexOf(prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")).coerceAtLeast(0))
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.SEARCH_ENGINE, engines[pos]) }
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
    }

    private fun setupTypographySettings() {
        val seek = findViewById<SeekBar>(R.id.typography_size_seekbar)
        val valT = findViewById<TextView>(R.id.typography_size_value_text)
        val styS = findViewById<Spinner>(R.id.typography_style_spinner)
        val intS = findViewById<Spinner>(R.id.typography_intensity_spinner)
        val colS = findViewById<Spinner>(R.id.typography_color_spinner)

        val scale = prefs.getInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, 100).coerceIn(80, 140)
        
        // Prepare font style lists including downloaded fonts
        val baseStyV = mutableListOf("default", "serif", "monospace", "condensed", "rounded", "casual", "cursive")
        val baseStyL = mutableListOf("Modern Sans", "Classic Serif", "Dev Mono", "Clean Condensed", "Soft Rounded", "Casual Hand", "Creative Script")
        
        // Add downloaded fonts to the dropdown lists
        DownloadableFontManager.getFontOptions().forEach { font ->
            if (DownloadableFontManager.isDownloaded(this, font.styleKey)) {
                baseStyV.add(font.styleKey)
                baseStyL.add(font.displayName)
            }
        }

        seek.max = 60
        seek.progress = scale - 80
        valT.text = "$scale%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                valT.text = "${p + 80}%"
                if (f) {
                    prefs.edit { putInt(Constants.Prefs.TYPOGRAPHY_SCALE_PERCENT, p + 80) }
                    TypographyManager.applyToActivity(this@SettingsActivity)
                    notifySettingsChanged()
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        styS.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, baseStyL).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        
        val currentStyle = prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") ?: "default"
        val selectedIndex = baseStyV.indexOf(currentStyle)
        if (selectedIndex >= 0) {
            styS.setSelection(selectedIndex)
        } else {
            // Font was uninstalled, reset to default
            prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") }
            styS.setSelection(0)
            TypographyManager.applyToActivity(this)
            notifySettingsChanged()
        }

        styS.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, baseStyV[pos]) }
                TypographyManager.applyToActivity(this@SettingsActivity)
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val intV = arrayOf("light", "regular", "bold", "extra_bold")
        val intL = arrayOf("Light weight", "Regular weight", "Bold weight", "Heavy weight")
        intS.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intL).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        intS.setSelection(intV.indexOf(prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, "regular")).coerceAtLeast(0))
        intS.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_INTENSITY, intV[pos]) }
                TypographyManager.applyToActivity(this@SettingsActivity)
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }

        val colL = arrayOf("Default Adaptive", "Pure White", "Deep Ocean", "Electric Purple", "Neon Pink", "Solar Gold", "Emerald Mist", "Arctic Frost", "Midnight Teal", "Cyan Accent", "Nord Mint", "Lavender", "Orange Glow", "Slate Gray")
        val colV = arrayOf(Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT, "#FFFFFFFF", "#FF1E3A8A", "#FF7C3AED", "#FFEC4899", "#FFF59E0B", "#FF10B981", "#FF93C5FD", "#FF0F766E", "#FF03DAC5", "#FF8FBCBB", "#FFB48EAD", "#FFD08770", "#FF4A5568")
        colS.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colL).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        colS.setSelection(colV.indexOf(prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, Constants.TYPOGRAPHY_FONT_COLOR_DEFAULT)).coerceAtLeast(0))
        colS.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_COLOR, colV[pos]) }
                TypographyManager.applyToActivity(this@SettingsActivity)
                notifySettingsChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>) {}
        }
        setupDownloadableFontsSection()
    }

    private fun setupDownloadableFontsSection() {
        val h = findViewById<View>(R.id.downloadable_fonts_header)
        val a = findViewById<ImageView>(R.id.downloadable_fonts_arrow)
        val c = findViewById<LinearLayout>(R.id.downloadable_fonts_container)
        h.setOnClickListener {
            val vis = c.isVisible
            c.visibility = if (vis) View.GONE else View.VISIBLE
            a.animate().rotation(if (vis) 0f else 90f).start()
            if (!vis) updateDownloadableFontsList(c)
        }
    }

    private fun updateDownloadableFontsList(cont: LinearLayout) {
        cont.removeAllViews()
        DownloadableFontManager.getFontOptions().forEach { opt ->
            val view = layoutInflater.inflate(R.layout.item_downloadable_font_option, cont, false)
            val lbl = view.findViewById<TextView>(R.id.downloadable_font_label)
            val btn = view.findViewById<Button>(R.id.downloadable_font_button)
            lbl.text = opt.displayName
            val inst = DownloadableFontManager.isDownloaded(this, opt.styleKey)
            btn.text = if (inst) "Remove" else "Install"
            btn.setTextColor(if (inst) Color.RED else Color.GREEN)
            btn.setOnClickListener {
                btn.isEnabled = false
                if (inst) {
                    DownloadableFontManager.uninstallFont(this, opt.styleKey)
                    if (prefs.getString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") == opt.styleKey) {
                        prefs.edit { putString(Constants.Prefs.TYPOGRAPHY_FONT_STYLE, "default") }
                    }
                    // Refresh the typography settings UI to update the spinner
                    setupTypographySettings()
                    updateDownloadableFontsList(cont)
                } else {
                    btn.text = "Fetching…"
                    DownloadableFontManager.requestFont(this, opt.styleKey) { success ->
                        handler.post {
                            // Refresh the typography settings UI to update the spinner
                            setupTypographySettings()
                            updateDownloadableFontsList(cont)
                        }
                    }
                }
            }
            cont.addView(view)
        }
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.guruswarupa.launch.SETTINGS_UPDATED").apply { setPackage(packageName) })
    }

    private fun hasAccessibilityServicePermission(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(ComponentName(this, ScreenLockAccessibilityService::class.java).flattenToString())
    }

    private fun showPermissionDialog(t: String, m: String, onP: () -> Unit) {
        AlertDialog.Builder(this, R.style.CustomDialogTheme).setTitle(t).setMessage(m).setPositiveButton("Settings") { _, _ -> onP() }.setNegativeButton("Cancel", null).show()
    }

    private fun chooseWallpaper() { wallpaperLauncher.launch(Intent(Intent.ACTION_SET_WALLPAPER)) }

    private fun showTutorial() {
        prefs.edit { putBoolean("feature_tutorial_shown", false); putInt("feature_tutorial_current_step", 0) }
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra("start_tutorial", true) })
        finish()
    }

    private fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<ScrollView>(R.id.settings_scroll_view).setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun makeSystemBarsTransparent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    private fun restartLauncher() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Restart Launcher")
            .setMessage("Are you sure you want to restart the launcher?")
            .setPositiveButton("Restart") { _, _ ->
                packageManager.getLaunchIntentForPackage(packageName)?.let { 
                    startActivity(Intent.makeRestartActivityTask(it.component!!))
                    Runtime.getRuntime().exit(0) 
                }
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun clearCache() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Clear Temporary Cache")
            .setMessage("This will remove temporary app data and cached icons. Your settings and organization will remain intact. Proceed?")
            .setPositiveButton("Clear") { _, _ ->
                cacheDir.deleteRecursively()
                externalCacheDir?.deleteRecursively()
                Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearData() {
        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Factory Reset Launcher")
            .setMessage("WARNING: This will permanently delete all your settings, custom workspaces, and configuration. The app will return to its initial state. Are you absolutely sure?")
            .setPositiveButton("Reset Everything") { _, _ ->
                (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).clearApplicationUserData()
            }
            .setNegativeButton("Keep Data", null)
            .show()
    }

    private fun sendFeedback() {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = "mailto:".toUri(); putExtra(Intent.EXTRA_EMAIL, arrayOf("msgswarupa@gmail.com")); putExtra(Intent.EXTRA_SUBJECT, "Launch Feedback") }, "Feedback")) } catch (_: Exception) {}
    }

    private fun exportSettings() { exportLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "launch_backup.zip") }) }
    private fun importSettings() { importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip" }) }

    private fun exportSettingsToFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                ZipOutputStream(os).use { zos ->
                    val j = JSONObject(); val p = JSONObject()
                    prefs.all.forEach { (k, v) -> p.put(k, v) }
                    j.put("main_preferences", p)
                    zos.putNextEntry(ZipEntry("settings.json")); zos.write(j.toString(2).toByteArray()); zos.closeEntry()
                }
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show() }
    }

    private fun importSettingsFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { ins ->
                ZipInputStream(ins).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "settings.json") {
                            val p = JSONObject(zis.bufferedReader().readText()).optJSONObject("main_preferences")
                            if (p != null) {
                                prefs.edit {
                                    val stringSetKeys = setOf("favorite_apps", "hidden_apps", "focus_mode_allowed_apps", "locked_apps")
                                    p.keys().forEach { k ->
                                        val v = p.get(k)
                                        
                                        // Fix for corrupted data format: if it should be a set but is a string like "[a, b]"
                                        if (k in stringSetKeys) {
                                            val set = when (v) {
                                                is JSONArray -> {
                                                    val s = mutableSetOf<String>()
                                                    for (i in 0 until v.length()) s.add(v.getString(i))
                                                    s
                                                }
                                                is String -> {
                                                    if (v.startsWith("[") && v.endsWith("]")) {
                                                        v.substring(1, v.length - 1)
                                                            .split(",")
                                                            .map { it.trim() }
                                                            .filter { it.isNotEmpty() }
                                                            .toSet()
                                                    } else {
                                                        setOf(v)
                                                    }
                                                }
                                                else -> emptySet<String>()
                                            }
                                            putStringSet(k, set)
                                        } else {
                                            when (v) {
                                                is String -> putString(k, v)
                                                is Boolean -> putBoolean(k, v)
                                                is Int -> putInt(k, v)
                                                is Long -> putLong(k, v)
                                                is Double -> putFloat(k, v.toFloat())
                                                is JSONArray -> {
                                                    putString(k, v.toString())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        zis.closeEntry(); entry = zis.nextEntry
                    }
                }
            }
            restartLauncher()
        } catch (e: Exception) { Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show() }
    }

    private fun startSettingsTutorial() { settingsTutorialStepIndex = 0; settingsTutorialActive = true; showCurrentSettingsTutorialStep() }
    private fun showCurrentSettingsTutorialStep() {
        if (settingsTutorialStepIndex >= settingsTutorialSteps.size) { settingsTutorialActive = false; return }
        val step = settingsTutorialSteps[settingsTutorialStepIndex]
        AlertDialog.Builder(this, R.style.CustomDialogTheme).setTitle(step.title).setMessage(step.description).setPositiveButton(if (settingsTutorialStepIndex == settingsTutorialSteps.size - 1) "Finish" else "Next") { _, _ -> settingsTutorialStepIndex++; showCurrentSettingsTutorialStep() }.setNegativeButton("Skip") { _, _ -> settingsTutorialActive = false }.show()
    }
}
