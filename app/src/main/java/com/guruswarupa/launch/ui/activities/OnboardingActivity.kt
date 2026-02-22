package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.text.TextWatcher
import android.text.Editable
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AppOpsManager
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FavoriteAppManager
import com.guruswarupa.launch.managers.WorkspaceManager
import com.guruswarupa.launch.ui.adapters.FavoritesOnboardingAdapter
import com.guruswarupa.launch.ui.adapters.WorkspacesAppsAdapter
import com.guruswarupa.launch.ui.adapters.WorkspacesListAdapter
import org.json.JSONArray
import org.json.JSONObject

data class PermissionInfo(
    val permission: String,
    val title: String,
    val explanation: String,
    val requestCode: Int
)

enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    DEFAULT_LAUNCHER,
    BACKUP_IMPORT,
    DISPLAY_STYLE,
    FAVORITES,
    WORKSPACES,
    WEATHER_API_KEY,
    COMPLETE
}

class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    private var currentStep = OnboardingStep.WELCOME
    private var hasRequestedStoragePermission = false
    private var currentPermissionIndex = 0
    private var hasRequestedDefaultLauncher = false
    private var displayStyleSelected = false
    private var backupImported = false

    companion object {
        private const val IMPORT_BACKUP_REQUEST_CODE = 1000
    }

    // UI References
    private lateinit var onboardingScrollView: android.widget.ScrollView
    private lateinit var welcomeStep: LinearLayout
    private lateinit var permissionsStep: LinearLayout
    private lateinit var defaultLauncherStep: LinearLayout
    private lateinit var backupImportStep: LinearLayout
    private lateinit var displayStyleStep: LinearLayout
    private lateinit var favoritesStep: LinearLayout
    private lateinit var weatherApiKeyStep: LinearLayout
    private lateinit var completeStep: LinearLayout
    private lateinit var backButton: Button
    private lateinit var nextButton: Button
    private lateinit var gridStyleButton: Button
    private lateinit var listStyleButton: Button
    private lateinit var weatherApiKeyInput: EditText
    private lateinit var weatherLocationInput: EditText
    private lateinit var favoritesRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var favoritesAdapter: FavoritesOnboardingAdapter
    private var selectedFavorites = mutableSetOf<String>()
    
    // Workspaces UI
    private lateinit var workspacesStep: LinearLayout
    private lateinit var workspaceNameInput: EditText
    private lateinit var addWorkspaceButton: Button
    private lateinit var workspacesListRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var workspacesAppsRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var workspacesListTitle: TextView
    private lateinit var workspacesListAdapter: WorkspacesListAdapter
    private lateinit var workspacesAppsAdapter: WorkspacesAppsAdapter
    private var currentWorkspaceApps = mutableSetOf<String>()
    private var createdWorkspaces = mutableListOf<Pair<String, Set<String>>>() // name to apps
    private var cachedAppsList: List<android.content.pm.ResolveInfo>? = null // Cached app list
    private var isPreloadingApps = false // Track if preload is in progress
    private var allAppsList = listOf<android.content.pm.ResolveInfo>() // Store all apps

    // Progress indicators
    private lateinit var step1Indicator: View
    private lateinit var step2Indicator: View
    private lateinit var step3Indicator: View
    private lateinit var step4Indicator: View
    private lateinit var step5Indicator: View
    private lateinit var step1Connector: View
    private lateinit var step2Connector: View
    private lateinit var step3Connector: View
    private lateinit var step4Connector: View

    // Define all permissions with explanations
    private val permissionList = mutableListOf<PermissionInfo>().apply {
        add(PermissionInfo(
            Manifest.permission.READ_CONTACTS,
            "Contacts Permission",
            "We need access to your contacts so you can search for people by name in the universal search bar. This allows you to quickly call, message, or WhatsApp your contacts directly from the launcher.",
            100
        ))
        add(PermissionInfo(
            Manifest.permission.CALL_PHONE,
            "Phone Call Permission",
            "This permission lets you make phone calls directly from search results. When you search for a contact, you can tap to call them instantly without opening the phone app.",
            101
        ))
        add(PermissionInfo(
            Manifest.permission.SEND_SMS,
            "SMS Permission",
            "This allows you to send text messages directly from the launcher. When you search for a contact, you can quickly send them an SMS without leaving the launcher.",
            102
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(PermissionInfo(
                Manifest.permission.READ_MEDIA_IMAGES,
                "Photos & Media Permission",
                "We need access to your photos so you can set custom wallpapers for your home screen. You can change your wallpaper from Settings.",
                103
            ))
            add(PermissionInfo(
                Manifest.permission.POST_NOTIFICATIONS,
                "Notifications Permission",
                "This permission allows the launcher to show notifications in the notifications widget. You'll be able to see and interact with your notifications directly from the home screen.",
                104
            ))
            add(PermissionInfo(
                Manifest.permission.ACTIVITY_RECOGNITION,
                "Physical Activity Permission",
                "This permission allows the launcher to track your physical activity such as steps walked and distance traveled. You'll be able to see your daily activity stats in the physical activity widget.",
                105
            ))
        } else {
            add(PermissionInfo(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "Storage Permission",
                "We need access to your storage to load custom wallpapers for your home screen. You can change your wallpaper from Settings.",
                103
            ))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if onboarding is already complete
        if (!prefs.getBoolean("isFirstTime", true)) {
            // Onboarding already completed, redirect to MainActivity
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        
        setContentView(R.layout.activity_onboarding)
        
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        // Make status bar and navigation bar transparent
        window.decorView.post {
            makeSystemBarsTransparent(isDarkMode)
        }

        initializeViews()
        setupClickListeners()
        
        // Preload app list immediately in background to avoid delays later
        preloadAppList()
        
        // Check if we should continue from default launcher step
        val continueFromDefaultLauncher = intent.getBooleanExtra("continueFromDefaultLauncher", false)
        if (continueFromDefaultLauncher && isDefaultLauncher()) {
            showStep(OnboardingStep.BACKUP_IMPORT)
        } else {
            showStep(OnboardingStep.WELCOME)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun makeSystemBarsTransparent(isDarkMode: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setDecorFitsSystemWindows(false)
                
                val decorView = window.decorView
                val insetsController = decorView.windowInsetsController
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
                // Android 5.0+ (API 21+)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                
                val decorView = window.decorView
                var flags = decorView.systemUiVisibility
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                
                if (!isDarkMode) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                
                decorView.systemUiVisibility = flags
            }
        } catch (_: Exception) {
            try {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            } catch (_: Exception) {}
        }
    }

    private fun initializeViews() {
        onboardingScrollView = findViewById(R.id.onboarding_scroll_view)
        welcomeStep = findViewById(R.id.welcome_step)
        permissionsStep = findViewById(R.id.permissions_step)
        defaultLauncherStep = findViewById(R.id.default_launcher_step)
        backupImportStep = findViewById(R.id.backup_import_step)
        displayStyleStep = findViewById(R.id.display_style_step)
        favoritesStep = findViewById(R.id.favorites_step)
        workspacesStep = findViewById(R.id.workspaces_step)
        weatherApiKeyStep = findViewById(R.id.weather_api_key_step)
        completeStep = findViewById(R.id.complete_step)
        
        favoritesRecyclerView = findViewById(R.id.favorites_recycler_view)
        favoritesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        favoritesRecyclerView.setHasFixedSize(true)
        favoritesRecyclerView.setItemViewCacheSize(20)
        favoritesRecyclerView.setRecycledViewPool(androidx.recyclerview.widget.RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 20)
        })
        
        workspaceNameInput = findViewById(R.id.workspace_name_input)
        addWorkspaceButton = findViewById(R.id.add_workspace_button)
        workspacesListRecyclerView = findViewById(R.id.workspaces_list_recycler_view)
        workspacesAppsRecyclerView = findViewById(R.id.workspaces_apps_recycler_view)
        workspacesListTitle = findViewById(R.id.workspaces_list_title)
        workspacesListRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        workspacesAppsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        // Optimize scrolling performance
        workspacesListRecyclerView.setHasFixedSize(true)
        workspacesListRecyclerView.setItemViewCacheSize(20)
        workspacesListRecyclerView.setRecycledViewPool(androidx.recyclerview.widget.RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 20)
        })
        
        workspacesAppsRecyclerView.setHasFixedSize(true)
        workspacesAppsRecyclerView.setItemViewCacheSize(20)
        workspacesAppsRecyclerView.setRecycledViewPool(androidx.recyclerview.widget.RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 20)
        })
        
        backButton = findViewById(R.id.back_button)
        nextButton = findViewById(R.id.next_button)
        gridStyleButton = findViewById(R.id.grid_style_button)
        listStyleButton = findViewById(R.id.list_style_button)
        weatherApiKeyInput = findViewById(R.id.weather_api_key_input)
        weatherLocationInput = findViewById(R.id.weather_location_input)

        step1Indicator = findViewById(R.id.step1_indicator)
        step2Indicator = findViewById(R.id.step2_indicator)
        step3Indicator = findViewById(R.id.step3_indicator)
        step4Indicator = findViewById(R.id.step4_indicator)
        step5Indicator = findViewById(R.id.step5_indicator)
        step1Connector = findViewById(R.id.step1_connector)
        step2Connector = findViewById(R.id.step2_connector)
        step3Connector = findViewById(R.id.step3_connector)
        step4Connector = findViewById(R.id.step4_connector)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener { goToPreviousStep() }
        nextButton.setOnClickListener { goToNextStep() }
        gridStyleButton.setOnClickListener { selectDisplayStyle("grid") }
        listStyleButton.setOnClickListener { selectDisplayStyle("list") }
    }

    private fun showStep(step: OnboardingStep) {
        currentStep = step
        
        // Hide all steps first
        welcomeStep.visibility = View.GONE
        permissionsStep.visibility = View.GONE
        defaultLauncherStep.visibility = View.GONE
        backupImportStep.visibility = View.GONE
        displayStyleStep.visibility = View.GONE
        favoritesStep.visibility = View.GONE
        workspacesStep.visibility = View.GONE
        weatherApiKeyStep.visibility = View.GONE
        completeStep.visibility = View.GONE

        // Show current step and update UI
        when (step) {
            OnboardingStep.WELCOME -> {
                welcomeStep.visibility = View.VISIBLE
                backButton.visibility = View.GONE
                nextButton.setText(R.string.onboarding_get_started)
                updateProgressIndicator(1)
            }
            OnboardingStep.PERMISSIONS -> {
                permissionsStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.setText(R.string.onboarding_start_permissions)
                updateProgressIndicator(1)
                nextButton.setOnClickListener { startPermissionFlow() }
            }
            OnboardingStep.DEFAULT_LAUNCHER -> {
                defaultLauncherStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = if (isDefaultLauncher()) getString(R.string.onboarding_continue) else "Set as Default"
                updateProgressIndicator(2)
                nextButton.setOnClickListener { goToNextStep() }
            }
            OnboardingStep.BACKUP_IMPORT -> {
                backupImportStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.setText(R.string.onboarding_skip)
                updateProgressIndicator(3)
                setupBackupImportButtons()
                if (cachedAppsList == null && !isPreloadingApps) {
                    preloadAppList()
                }
            }
            OnboardingStep.DISPLAY_STYLE -> {
                displayStyleStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = if (displayStyleSelected) getString(R.string.onboarding_continue) else "Select Style First"
                nextButton.isEnabled = displayStyleSelected
                updateProgressIndicator(4)
                if (cachedAppsList == null && !isPreloadingApps) {
                    preloadAppList()
                }
            }
            OnboardingStep.FAVORITES -> {
                favoritesStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.setText(R.string.onboarding_continue)
                nextButton.isEnabled = true
                updateProgressIndicator(5)
                loadAppsForFavoritesSelection()
            }
            OnboardingStep.WORKSPACES -> {
                workspacesStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.setText(R.string.onboarding_continue)
                nextButton.isEnabled = true
                updateProgressIndicator(6)
                loadAppsForWorkspacesSelection()
                setupWorkspaceButtons()
                workspacesAppsRecyclerView.postDelayed({
                    workspacesStep.post {
                        val scrollY = workspacesStep.top
                        onboardingScrollView.post {
                            onboardingScrollView.scrollTo(0, maxOf(0, scrollY))
                        }
                    }
                }, 200)
            }
            OnboardingStep.WEATHER_API_KEY -> {
                weatherApiKeyStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.setText(R.string.onboarding_continue)
                nextButton.isEnabled = true
                updateProgressIndicator(7)
                val existingKey = prefs.getString("weather_api_key", "") ?: ""
                if (existingKey.isNotEmpty()) {
                    weatherApiKeyInput.setText(existingKey)
                }
                val existingLocation = prefs.getString("weather_stored_city_name", "") ?: ""
                if (existingLocation.isNotEmpty()) {
                    weatherLocationInput.setText(existingLocation)
                }
            }
            OnboardingStep.COMPLETE -> {
                completeStep.visibility = View.VISIBLE
                backButton.visibility = View.GONE
                nextButton.setText(R.string.onboarding_launch_app)
                updateProgressIndicator(7)
            }
        }
    }

    private fun updateProgressIndicator(activeStep: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.onboarding_step_indicator_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.onboarding_step_indicator_inactive)

        fun createCircularDrawable(color: Int): android.graphics.drawable.GradientDrawable {
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setColor(color)
            return drawable
        }

        fun setViewColor(view: View, color: Int, isCircular: Boolean = false) {
            if (isCircular) {
                view.background = createCircularDrawable(color)
            } else {
                view.background = color.toDrawable()
            }
        }

        setViewColor(step1Indicator, inactiveColor, true)
        setViewColor(step2Indicator, inactiveColor, true)
        setViewColor(step3Indicator, inactiveColor, true)
        setViewColor(step4Indicator, inactiveColor, true)
        setViewColor(step5Indicator, inactiveColor, true)
        setViewColor(step1Connector, inactiveColor)
        setViewColor(step2Connector, inactiveColor)
        setViewColor(step3Connector, inactiveColor)
        setViewColor(step4Connector, inactiveColor)

        if (activeStep >= 1) setViewColor(step1Indicator, activeColor, true)
        if (activeStep >= 2) {
            setViewColor(step1Connector, activeColor)
            setViewColor(step2Indicator, activeColor, true)
        }
        if (activeStep >= 3) {
            setViewColor(step2Connector, activeColor)
            setViewColor(step3Indicator, activeColor, true)
        }
        if (activeStep >= 4) {
            setViewColor(step3Connector, activeColor)
            setViewColor(step4Indicator, activeColor, true)
        }
        if (activeStep >= 5) {
            setViewColor(step4Connector, activeColor)
            setViewColor(step5Indicator, activeColor, true)
        }
    }

    private fun goToPreviousStep() {
        when (currentStep) {
            OnboardingStep.WELCOME -> {}
            OnboardingStep.PERMISSIONS -> showStep(OnboardingStep.WELCOME)
            OnboardingStep.DEFAULT_LAUNCHER -> showStep(OnboardingStep.PERMISSIONS)
            OnboardingStep.BACKUP_IMPORT -> showStep(OnboardingStep.DEFAULT_LAUNCHER)
            OnboardingStep.DISPLAY_STYLE -> showStep(OnboardingStep.BACKUP_IMPORT)
            OnboardingStep.FAVORITES -> showStep(OnboardingStep.DISPLAY_STYLE)
            OnboardingStep.WORKSPACES -> showStep(OnboardingStep.FAVORITES)
            OnboardingStep.WEATHER_API_KEY -> showStep(OnboardingStep.WORKSPACES)
            OnboardingStep.COMPLETE -> showStep(OnboardingStep.WEATHER_API_KEY)
        }
    }

    private fun goToNextStep() {
        when (currentStep) {
            OnboardingStep.WELCOME -> showStep(OnboardingStep.PERMISSIONS)
            OnboardingStep.PERMISSIONS -> showStep(OnboardingStep.DEFAULT_LAUNCHER)
            OnboardingStep.DEFAULT_LAUNCHER -> {
                if (isDefaultLauncher()) {
                    showStep(OnboardingStep.BACKUP_IMPORT)
                } else {
                    setDefaultLauncher()
                }
            }
            OnboardingStep.BACKUP_IMPORT -> {
                if (backupImported) {
                    showStep(OnboardingStep.COMPLETE)
                } else {
                    showStep(OnboardingStep.DISPLAY_STYLE)
                }
            }
            OnboardingStep.DISPLAY_STYLE -> {
                if (displayStyleSelected) {
                    showStep(OnboardingStep.FAVORITES)
                } else {
                    Toast.makeText(this, "Please select a display style first", Toast.LENGTH_SHORT).show()
                }
            }
            OnboardingStep.FAVORITES -> {
                saveSelectedFavorites()
                showStep(OnboardingStep.WORKSPACES)
            }
            OnboardingStep.WORKSPACES -> showStep(OnboardingStep.WEATHER_API_KEY)
            OnboardingStep.WEATHER_API_KEY -> {
                val apiKey = weatherApiKeyInput.text.toString().trim()
                prefs.edit { putString("weather_api_key", apiKey) }
                if (apiKey.isNotEmpty()) {
                    prefs.edit { putBoolean("weather_api_key_rejected", false) }
                }
                val location = weatherLocationInput.text.toString().trim()
                prefs.edit { putString("weather_stored_city_name", location) }
                showStep(OnboardingStep.COMPLETE)
            }
            OnboardingStep.COMPLETE -> finishSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasRequestedStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasRequestedStoragePermission = false
            if (hasStoragePermission()) {
                requestUsageStatsPermission()
            } else {
                showPermissionDeniedDialog("Storage", "Without storage access, you won't be able to set custom wallpapers. You can grant this permission later in Settings.")
                requestUsageStatsPermission()
            }
        }
        if (currentStep == OnboardingStep.PERMISSIONS && hasUsageStatsPermission()) {
            showStep(OnboardingStep.DEFAULT_LAUNCHER)
        }
        if (hasRequestedDefaultLauncher) {
            hasRequestedDefaultLauncher = false
            if (isDefaultLauncher()) {
                showStep(OnboardingStep.BACKUP_IMPORT)
            } else {
                showDefaultLauncherSkippedDialog()
            }
        } else if (currentStep == OnboardingStep.DEFAULT_LAUNCHER && isDefaultLauncher()) {
            showStep(OnboardingStep.BACKUP_IMPORT)
        }
    }

    private fun startPermissionFlow() {
        currentPermissionIndex = -1
        requestNextPermission()
    }

    private fun requestNextPermission() {
        currentPermissionIndex++
        while (currentPermissionIndex < permissionList.size) {
            val permissionInfo = permissionList[currentPermissionIndex]
            if (ContextCompat.checkSelfPermission(this, permissionInfo.permission) != PackageManager.PERMISSION_GRANTED) {
                showPermissionExplanation(permissionInfo)
                return
            }
            currentPermissionIndex++
        }
        requestStoragePermission()
    }

    private fun showPermissionExplanation(permissionInfo: PermissionInfo) {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle(permissionInfo.title)
            .setMessage(permissionInfo.explanation)
            .setPositiveButton("Allow") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permissionInfo.permission),
                    permissionInfo.requestCode
                )
            }
            .setNegativeButton("Skip") { _, _ ->
                markPermissionAsDenied(permissionInfo.permission)
                requestNextPermission()
            }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener { fixDialogTextColors(dialog) }
        dialog.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestNextPermission()
        } else {
            val permissionInfo = permissionList.find { it.requestCode == requestCode }
            if (permissionInfo != null) {
                markPermissionAsDenied(permissionInfo.permission)
                showPermissionDeniedDialog(permissionInfo.title, permissionInfo.explanation)
            }
            requestNextPermission()
        }
    }

    private fun markPermissionAsDenied(permission: String) {
        when (permission) {
            Manifest.permission.READ_CONTACTS -> prefs.edit { putBoolean("contacts_permission_denied", true) }
            Manifest.permission.SEND_SMS -> prefs.edit { putBoolean("sms_permission_denied", true) }
            Manifest.permission.CALL_PHONE -> prefs.edit { putBoolean("call_phone_permission_denied", true) }
            Manifest.permission.POST_NOTIFICATIONS -> prefs.edit { putBoolean("notification_permission_denied", true) }
            Manifest.permission.ACTIVITY_RECOGNITION -> prefs.edit { putBoolean("activity_recognition_permission_denied", true) }
        }
    }
    
    private fun showPermissionDeniedDialog(title: String, explanation: String) {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("$title Denied")
            .setMessage("$explanation\n\nYou can grant this permission later in Settings if you change your mind.")
            .setPositiveButton("OK", null)
            .create()
        dialog.setOnShowListener { fixDialogTextColors(dialog) }
        dialog.show()
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasStoragePermission()) {
                showStoragePermissionExplanation()
            } else {
                requestUsageStatsPermission()
            }
        } else {
            requestUsageStatsPermission()
        }
    }

    @SuppressLint("InlinedApi")
    private fun showStoragePermissionExplanation() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Storage Access Permission")
            .setMessage("We need access to your files to load custom wallpapers for your home screen.\n\nYou'll be taken to Settings to enable this permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    hasRequestedStoragePermission = true
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: Exception) {
                    Toast.makeText(this, "Enable file access in Settings.", Toast.LENGTH_LONG).show()
                    requestUsageStatsPermission()
                }
            }
            .setNegativeButton("Skip") { _, _ -> requestUsageStatsPermission() }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener { fixDialogTextColors(dialog) }
        dialog.show()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { android.os.Environment.isExternalStorageManager() } catch (_: Exception) { false }
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            showUsageStatsPermissionExplanation()
        } else {
            showStep(OnboardingStep.DEFAULT_LAUNCHER)
        }
    }

    private fun showUsageStatsPermissionExplanation() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Usage Access Permission")
            .setMessage("This permission allows the launcher to show you how much time you spend on each app.\n\nYou'll be taken to Settings to enable this permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "Enable usage access in Settings.", Toast.LENGTH_LONG).show()
                    showStep(OnboardingStep.DEFAULT_LAUNCHER)
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                prefs.edit { putBoolean("usage_stats_permission_denied", true) }
                showStep(OnboardingStep.DEFAULT_LAUNCHER)
            }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener { fixDialogTextColors(dialog) }
        dialog.show()
    }

    @Suppress("DEPRECATION")
    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun setDefaultLauncher() {
        hasRequestedDefaultLauncher = true
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }
    
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    private fun showDefaultLauncherSkippedDialog() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Continue Without Setting Default?")
            .setMessage("You can set this launcher as default later from Settings.\n\nDo you want to continue?")
            .setPositiveButton(R.string.onboarding_continue) { _, _ -> showStep(OnboardingStep.BACKUP_IMPORT) }
            .setNegativeButton("Set as Default") { _, _ -> setDefaultLauncher() }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener { fixDialogTextColors(dialog) }
        dialog.show()
    }

    private fun selectDisplayStyle(style: String) {
        prefs.edit { putString("view_preference", style) }
        displayStyleSelected = true
        if (style == "grid") {
            gridStyleButton.alpha = 1.0f
            listStyleButton.alpha = 0.5f
        } else {
            gridStyleButton.alpha = 0.5f
            listStyleButton.alpha = 1.0f
        }
        nextButton.isEnabled = true
        nextButton.setText(R.string.onboarding_continue)
    }
    
    private fun preloadAppList() {
        if (cachedAppsList != null || isPreloadingApps) return
        isPreloadingApps = true
        Thread {
            try {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val allApps = pm.queryIntentActivities(mainIntent, 0).filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                cachedAppsList = allApps.sortedWith(compareBy { app ->
                    try { app.loadLabel(pm).toString().lowercase() } catch (_: Exception) { app.activityInfo.packageName.lowercase() }
                })
            } catch (_: Exception) {} finally { isPreloadingApps = false }
        }.start()
    }
    
    private fun loadAppsForFavoritesSelection() {
        favoritesRecyclerView.visibility = View.GONE
        if (cachedAppsList == null && isPreloadingApps) {
            Thread {
                var waited = 0
                while (cachedAppsList == null && waited < 1000 && isPreloadingApps) { Thread.sleep(50); waited += 50 }
                loadAppsForFavoritesSelectionInternal()
            }.start()
        } else {
            loadAppsForFavoritesSelectionInternal()
        }
    }
    
    private fun loadAppsForFavoritesSelectionInternal() {
        Thread {
            try {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val quickApps = pm.queryIntentActivities(mainIntent, 0).filter { it.activityInfo.packageName != "com.guruswarupa.launch" }.sortedBy { it.activityInfo.packageName.lowercase() }
                val existingFavorites = FavoriteAppManager(prefs).getFavoriteApps().toMutableSet()
                runOnUiThread {
                    selectedFavorites = existingFavorites
                    val appsToShow = cachedAppsList ?: quickApps
                    favoritesAdapter = FavoritesOnboardingAdapter(appsToShow, selectedFavorites) { packageName, isChecked ->
                        if (isChecked) selectedFavorites.add(packageName) else selectedFavorites.remove(packageName)
                    }
                    favoritesRecyclerView.adapter = favoritesAdapter
                    favoritesRecyclerView.visibility = View.VISIBLE
                }
            } catch (_: Exception) { runOnUiThread { favoritesRecyclerView.visibility = View.VISIBLE } }
        }.start()
    }
    
    private fun saveSelectedFavorites() {
        prefs.edit { 
            putStringSet("favorite_apps", selectedFavorites)
            putBoolean("show_all_apps_mode", false)
        }
    }
    
    private fun loadAppsForWorkspacesSelection() {
        workspacesAppsRecyclerView.visibility = View.GONE
        Thread {
            try {
                val apps = cachedAppsList ?: run {
                    val pm = packageManager
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                    pm.queryIntentActivities(mainIntent, 0).filter { it.activityInfo.packageName != "com.guruswarupa.launch" }.sortedBy { it.activityInfo.packageName.lowercase() }
                }
                runOnUiThread {
                    allAppsList = apps
                    updateAvailableAppsForWorkspace()
                    workspacesListAdapter = WorkspacesListAdapter(createdWorkspaces) { position ->
                        createdWorkspaces.removeAt(position)
                        @SuppressLint("NotifyDataSetChanged")
                        workspacesListAdapter.notifyDataSetChanged()
                        updateWorkspacesListVisibility()
                        updateAvailableAppsForWorkspace()
                    }
                    workspacesListRecyclerView.adapter = workspacesListAdapter
                    updateWorkspacesListVisibility()
                    workspacesAppsRecyclerView.visibility = View.VISIBLE
                }
            } catch (_: Exception) { runOnUiThread { workspacesAppsRecyclerView.visibility = View.VISIBLE } }
        }.start()
    }
    
    private fun updateAvailableAppsForWorkspace() {
        val usedApps = createdWorkspaces.flatMap { it.second }.toSet()
        val availableApps = allAppsList.filter { it.activityInfo.packageName !in usedApps }
        workspacesAppsAdapter = WorkspacesAppsAdapter(availableApps, currentWorkspaceApps) { packageName, isChecked ->
            if (isChecked) currentWorkspaceApps.add(packageName) else currentWorkspaceApps.remove(packageName)
            updateAddWorkspaceButtonState()
        }
        workspacesAppsRecyclerView.adapter = workspacesAppsAdapter
        updateAddWorkspaceButtonState()
        workspacesAppsRecyclerView.post { workspacesAppsRecyclerView.requestLayout() }
    }
    
    private fun updateAddWorkspaceButtonState() {
        val hasName = workspaceNameInput.text.toString().trim().isNotEmpty()
        val hasApps = currentWorkspaceApps.isNotEmpty()
        addWorkspaceButton.isEnabled = hasName && hasApps
        addWorkspaceButton.alpha = if (hasName && hasApps) 1.0f else 0.5f
    }
    
    private fun setupWorkspaceButtons() {
        workspaceNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateAddWorkspaceButtonState() }
            override fun afterTextChanged(s: Editable?) { updateAddWorkspaceButtonState() }
        })
        updateAddWorkspaceButtonState()
        addWorkspaceButton.setOnClickListener {
            val workspaceName = workspaceNameInput.text.toString().trim()
            if (workspaceName.isEmpty()) { workspaceNameInput.requestFocus(); return@setOnClickListener }
            if (currentWorkspaceApps.isEmpty()) return@setOnClickListener
            createdWorkspaces.add(Pair(workspaceName, currentWorkspaceApps.toSet()))
            @SuppressLint("NotifyDataSetChanged")
            workspacesListAdapter.notifyDataSetChanged()
            workspaceNameInput.text.clear()
            currentWorkspaceApps.clear()
            updateAvailableAppsForWorkspace()
            updateWorkspacesListVisibility()
            onboardingScrollView.post {
                if (workspacesListRecyclerView.isVisible) {
                    onboardingScrollView.smoothScrollTo(0, workspacesListRecyclerView.top - 100)
                }
            }
        }
    }
    
    private fun updateWorkspacesListVisibility() {
        workspacesListTitle.isVisible = createdWorkspaces.isNotEmpty()
        workspacesListRecyclerView.isVisible = createdWorkspaces.isNotEmpty()
    }
    
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val textColor = ContextCompat.getColor(this, if (isDarkMode) R.color.white else R.color.black)
            val accentColor = ContextCompat.getColor(this, R.color.onboarding_step_indicator_active)
            
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accentColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(textColor)
        } catch (_: Exception) {}
    }

    private fun finishSetup() {
        saveCreatedWorkspaces()
        prefs.edit { putBoolean("isFirstTime", false) }
        startActivity(Intent(this, MainActivity::class.java).apply { 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK 
        })
        finish()
    }
    
    private fun saveCreatedWorkspaces() {
        val workspaceManager = WorkspaceManager(prefs)
        createdWorkspaces.forEach { (name, apps) ->
            workspaceManager.createWorkspace(name, apps)
        }
    }

    private fun setupBackupImportButtons() {
        findViewById<Button>(R.id.import_backup_button).setOnClickListener { requestBackupFile() }
    }
    
    private fun requestBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, IMPORT_BACKUP_REQUEST_CODE)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions = arrayOf(), grantResults = intArrayOf())
        if (resultCode == RESULT_OK && data != null && requestCode == IMPORT_BACKUP_REQUEST_CODE) {
            data.data?.let { importBackupFromFile(it) }
        }
    }
    
    private fun importBackupFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val settingsJson = JSONObject(jsonString)
                val isNewFormat = settingsJson.has("main_preferences")
                if (isNewFormat) {
                    if (settingsJson.has("main_preferences")) {
                        prefs.edit {
                            importPreferences(settingsJson.getJSONObject("main_preferences"), this)
                        }
                    }
                } else {
                    prefs.edit {
                        importPreferences(settingsJson, this)
                    }
                }
                backupImported = true
                Toast.makeText(this, "Backup imported successfully", Toast.LENGTH_SHORT).show()
                showStep(OnboardingStep.COMPLETE)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to import backup: ${e.message}", Toast.LENGTH_LONG).show()
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
                    for (i in 0 until value.length()) stringSet.add(value.getString(i))
                    editor.putStringSet(key, stringSet)
                }
            }
        }
    }
}
