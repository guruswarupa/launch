package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.app.AppOpsManager
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
    private val IMPORT_BACKUP_REQUEST_CODE = 1000

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        
        // Make status bar and navigation bar transparent
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        initializeViews()
        setupClickListeners()
        
        // Preload app list immediately in background to avoid delays later
        // Start loading as early as possible
        preloadAppList()
        
        // Check if we should continue from default launcher step (when MainActivity redirects here)
        val continueFromDefaultLauncher = intent.getBooleanExtra("continueFromDefaultLauncher", false)
        if (continueFromDefaultLauncher && isDefaultLauncher()) {
            // User set launcher as default, continue to backup import step
            showStep(OnboardingStep.BACKUP_IMPORT)
        } else {
            showStep(OnboardingStep.WELCOME)
        }
        
        // Also preload when user reaches backup import step (gives more time)
        // This ensures app list is ready by the time they reach favorites
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
                nextButton.text = "Get Started"
                updateProgressIndicator(1)
            }
            OnboardingStep.PERMISSIONS -> {
                permissionsStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = "Start Permissions"
                updateProgressIndicator(1)
                // Auto-start permission flow when entering this step
                nextButton.setOnClickListener { startPermissionFlow() }
            }
            OnboardingStep.DEFAULT_LAUNCHER -> {
                defaultLauncherStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = if (isDefaultLauncher()) "Continue" else "Set as Default"
                updateProgressIndicator(2)
                // Reset next button listener
                nextButton.setOnClickListener { goToNextStep() }
            }
            OnboardingStep.BACKUP_IMPORT -> {
                backupImportStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = "Skip"
                updateProgressIndicator(3)
                // Setup backup import buttons
                setupBackupImportButtons()
                // Setup backup import buttons
                setupBackupImportButtons()
                
                // Ensure app list is preloading (start if not already)
                if (cachedAppsList == null && !isPreloadingApps) {
                    preloadAppList()
                }
            }
            OnboardingStep.DISPLAY_STYLE -> {
                displayStyleStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = if (displayStyleSelected) "Continue" else "Select Style First"
                nextButton.isEnabled = displayStyleSelected
                updateProgressIndicator(4)
                
                // Preload app list when user reaches display style step
                // This gives us time to load before favorites step
                if (cachedAppsList == null && !isPreloadingApps) {
                    preloadAppList()
                }
            }
            OnboardingStep.FAVORITES -> {
                favoritesStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = "Continue"
                nextButton.isEnabled = true
                updateProgressIndicator(5)
                
                // Load apps and setup RecyclerView
                loadAppsForFavoritesSelection()
            }
            OnboardingStep.WORKSPACES -> {
                workspacesStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = "Continue"
                nextButton.isEnabled = true
                updateProgressIndicator(6)
                
                // Load apps and setup RecyclerView
                loadAppsForWorkspacesSelection()
                setupWorkspaceButtons()
                
                // Scroll to top of workspaces step after layout is complete
                // Wait for RecyclerView to finish calculating height
                workspacesAppsRecyclerView.postDelayed({
                    workspacesStep.post {
                        val scrollY = workspacesStep.top
                        onboardingScrollView.post {
                            onboardingScrollView.scrollTo(0, maxOf(0, scrollY))
                        }
                    }
                }, 200) // Give RecyclerView time to calculate height
            }
            OnboardingStep.WEATHER_API_KEY -> {
                weatherApiKeyStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = "Continue"
                nextButton.isEnabled = true
                updateProgressIndicator(7)
                // Load existing API key if any
                val existingKey = prefs.getString("weather_api_key", "") ?: ""
                if (existingKey.isNotEmpty()) {
                    weatherApiKeyInput.setText(existingKey)
                }
                // Load existing location if any
                val existingLocation = prefs.getString("weather_stored_city_name", "") ?: ""
                if (existingLocation.isNotEmpty()) {
                    weatherLocationInput.setText(existingLocation)
                }
            }
            OnboardingStep.COMPLETE -> {
                completeStep.visibility = View.VISIBLE
                backButton.visibility = View.GONE
                nextButton.text = "Launch App"
                updateProgressIndicator(7)
            }
        }
    }

    private fun updateProgressIndicator(activeStep: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.nord9)
        val inactiveColor = ContextCompat.getColor(this, R.color.nord3)

        // Helper function to create circular drawable
        fun createCircularDrawable(color: Int): android.graphics.drawable.GradientDrawable {
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setColor(color)
            return drawable
        }

        // Helper function to set background color
        fun setViewColor(view: View, color: Int, isCircular: Boolean = false) {
            if (isCircular) {
                view.background = createCircularDrawable(color)
            } else {
                view.background = android.graphics.drawable.ColorDrawable(color)
            }
        }

        // Reset all indicators
        setViewColor(step1Indicator, inactiveColor, true)
        setViewColor(step2Indicator, inactiveColor, true)
        setViewColor(step3Indicator, inactiveColor, true)
        setViewColor(step4Indicator, inactiveColor, true)
        setViewColor(step5Indicator, inactiveColor, true)
        setViewColor(step1Connector, inactiveColor)
        setViewColor(step2Connector, inactiveColor)
        setViewColor(step3Connector, inactiveColor)
        setViewColor(step4Connector, inactiveColor)

        // Activate steps up to current
        when (activeStep) {
            1 -> {
                setViewColor(step1Indicator, activeColor, true)
            }
            2 -> {
                setViewColor(step1Indicator, activeColor, true)
                setViewColor(step1Connector, activeColor)
                setViewColor(step2Indicator, activeColor, true)
            }
            3 -> {
                setViewColor(step1Indicator, activeColor, true)
                setViewColor(step1Connector, activeColor)
                setViewColor(step2Indicator, activeColor, true)
                setViewColor(step2Connector, activeColor)
                setViewColor(step3Indicator, activeColor, true)
            }
            4 -> {
                setViewColor(step1Indicator, activeColor, true)
                setViewColor(step1Connector, activeColor)
                setViewColor(step2Indicator, activeColor, true)
                setViewColor(step2Connector, activeColor)
                setViewColor(step3Indicator, activeColor, true)
                setViewColor(step3Connector, activeColor)
                setViewColor(step4Indicator, activeColor, true)
            }
            5 -> {
                setViewColor(step1Indicator, activeColor, true)
                setViewColor(step1Connector, activeColor)
                setViewColor(step2Indicator, activeColor, true)
                setViewColor(step2Connector, activeColor)
                setViewColor(step3Indicator, activeColor, true)
                setViewColor(step3Connector, activeColor)
                setViewColor(step4Indicator, activeColor, true)
                setViewColor(step4Connector, activeColor)
                setViewColor(step5Indicator, activeColor, true)
            }
            6 -> {
                setViewColor(step1Indicator, activeColor, true)
                setViewColor(step1Connector, activeColor)
                setViewColor(step2Indicator, activeColor, true)
                setViewColor(step2Connector, activeColor)
                setViewColor(step3Indicator, activeColor, true)
                setViewColor(step3Connector, activeColor)
                setViewColor(step4Indicator, activeColor, true)
                setViewColor(step4Connector, activeColor)
                setViewColor(step5Indicator, activeColor, true)
            }
            7 -> {
                setViewColor(step1Indicator, activeColor, true)
                setViewColor(step1Connector, activeColor)
                setViewColor(step2Indicator, activeColor, true)
                setViewColor(step2Connector, activeColor)
                setViewColor(step3Indicator, activeColor, true)
                setViewColor(step3Connector, activeColor)
                setViewColor(step4Indicator, activeColor, true)
                setViewColor(step4Connector, activeColor)
                setViewColor(step5Indicator, activeColor, true)
            }
        }
    }

    private fun goToPreviousStep() {
        when (currentStep) {
            OnboardingStep.WELCOME -> {
                // Can't go back from welcome
            }
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
            OnboardingStep.PERMISSIONS -> {
                // Permissions are handled separately via dialogs
                // This will be called after permissions are done
                showStep(OnboardingStep.DEFAULT_LAUNCHER)
            }
            OnboardingStep.DEFAULT_LAUNCHER -> {
                if (isDefaultLauncher()) {
                    showStep(OnboardingStep.BACKUP_IMPORT)
                } else {
                    setDefaultLauncher()
                }
            }
            OnboardingStep.BACKUP_IMPORT -> {
                // If backup was imported, skip to complete
                if (backupImported) {
                    showStep(OnboardingStep.COMPLETE)
                } else {
                    // User chose to skip, continue with normal flow
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
                // Save selected favorites
                saveSelectedFavorites()
                showStep(OnboardingStep.WORKSPACES)
            }
            OnboardingStep.WORKSPACES -> {
                // Workspaces are handled in dialog, continue to weather
                showStep(OnboardingStep.WEATHER_API_KEY)
            }
            OnboardingStep.WEATHER_API_KEY -> {
                // Save weather API key (can be empty if user skips)
                val apiKey = weatherApiKeyInput.text.toString().trim()
                prefs.edit().putString("weather_api_key", apiKey).apply()
                if (apiKey.isNotEmpty()) {
                    prefs.edit().putBoolean("weather_api_key_rejected", false).apply()
                }
                // Save weather location (can be empty if user skips)
                val location = weatherLocationInput.text.toString().trim()
                prefs.edit().putString("weather_stored_city_name", location).apply()
                showStep(OnboardingStep.COMPLETE)
            }
            OnboardingStep.COMPLETE -> finishSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if user returned from storage permission settings
        if (hasRequestedStoragePermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            hasRequestedStoragePermission = false
            if (hasStoragePermission()) {
                requestUsageStatsPermission()
            } else {
                showPermissionDeniedDialog("Storage", "Without storage access, you won't be able to set custom wallpapers. You can grant this permission later in Settings.")
                // Continue with next permission anyway
                requestUsageStatsPermission()
            }
        }
        
        // Check if user returned from usage stats settings
        if (currentStep == OnboardingStep.PERMISSIONS && hasUsageStatsPermission()) {
            // All permissions done, move to next step
            showStep(OnboardingStep.DEFAULT_LAUNCHER)
        }
        
        // Check if user returned from home settings (default launcher)
        if (hasRequestedDefaultLauncher) {
            val wasRequested = hasRequestedDefaultLauncher
            hasRequestedDefaultLauncher = false
            if (isDefaultLauncher()) {
                // Launcher is set as default, move to backup import step
                showStep(OnboardingStep.BACKUP_IMPORT)
            } else {
                // User didn't set it as default, ask if they want to continue anyway
                showDefaultLauncherSkippedDialog()
            }
        } else if (currentStep == OnboardingStep.DEFAULT_LAUNCHER && isDefaultLauncher()) {
            // User might have set launcher as default and Android launched MainActivity which redirected here
            // Check if we should move to next step
            showStep(OnboardingStep.BACKUP_IMPORT)
        }
    }

    private fun startPermissionFlow() {
        currentPermissionIndex = -1  // Start at -1 so first increment brings us to 0
        requestNextPermission()
    }

    private fun requestNextPermission() {
        // Move to the next permission index
        currentPermissionIndex++
        
        // Find the next permission that hasn't been granted
        while (currentPermissionIndex < permissionList.size) {
            val permissionInfo = permissionList[currentPermissionIndex]
            if (ContextCompat.checkSelfPermission(this, permissionInfo.permission) != PackageManager.PERMISSION_GRANTED) {
                // Found a permission that needs to be requested
                showPermissionExplanation(permissionInfo)
                return
            }
            // This permission is already granted, move to next
            currentPermissionIndex++
        }
        
        // All runtime permissions have been processed (granted or skipped), move to storage
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
                // User skipped, mark as denied so MainActivity doesn't ask again
                markPermissionAsDenied(permissionInfo.permission)
                // Move to next permission without incrementing again
                // (requestNextPermission will increment)
                requestNextPermission()
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted, move to next
            requestNextPermission()
        } else {
            // Permission denied
            val permissionInfo = permissionList.find { it.requestCode == requestCode }
            if (permissionInfo != null) {
                // Mark as denied so MainActivity doesn't ask again
                markPermissionAsDenied(permissionInfo.permission)
                showPermissionDeniedDialog(permissionInfo.title, permissionInfo.explanation)
            }
            // Still move to next permission
            requestNextPermission()
        }
    }

    private fun markPermissionAsDenied(permission: String) {
        // Mark permission as denied in shared preferences so MainActivity doesn't ask again
        when (permission) {
            Manifest.permission.READ_CONTACTS -> {
                prefs.edit().putBoolean("contacts_permission_denied", true).apply()
            }
            Manifest.permission.SEND_SMS -> {
                prefs.edit().putBoolean("sms_permission_denied", true).apply()
            }
            Manifest.permission.CALL_PHONE -> {
                prefs.edit().putBoolean("call_phone_permission_denied", true).apply()
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                prefs.edit().putBoolean("notification_permission_denied", true).apply()
            }
            Manifest.permission.ACTIVITY_RECOGNITION -> {
                prefs.edit().putBoolean("activity_recognition_permission_denied", true).apply()
            }
        }
    }
    
    private fun showPermissionDeniedDialog(title: String, explanation: String) {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("$title Denied")
            .setMessage("$explanation\n\nYou can grant this permission later in Settings if you change your mind.")
            .setPositiveButton("OK", null)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ requires special storage permission
            if (!hasStoragePermission()) {
                showStoragePermissionExplanation()
            } else {
                requestUsageStatsPermission()
            }
        } else {
            // For Android 10 and below, READ_EXTERNAL_STORAGE is already requested above
            requestUsageStatsPermission()
        }
    }

    private fun showStoragePermissionExplanation() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Storage Access Permission")
            .setMessage("We need access to your files to load custom wallpapers for your home screen. This allows you to set any image from your device as your launcher background.\n\nYou'll be taken to Settings to enable this permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    hasRequestedStoragePermission = true
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (e: Exception) {
                    Toast.makeText(this, "Enable file access in Settings.", Toast.LENGTH_LONG).show()
                    requestUsageStatsPermission()
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                requestUsageStatsPermission()
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }

    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                android.os.Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            showUsageStatsPermissionExplanation()
        } else {
            // All permissions done, move to next step
            showStep(OnboardingStep.DEFAULT_LAUNCHER)
        }
    }

    private fun showUsageStatsPermissionExplanation() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Usage Access Permission")
            .setMessage("This permission allows the launcher to show you how much time you spend on each app. It helps you:\n\n• See app usage stats next to each app icon\n• Track your daily and weekly screen time\n• Organize apps by usage frequency\n\nYou'll be taken to Settings to enable this permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Enable usage access in Settings.", Toast.LENGTH_LONG).show()
                    // Move to next step even if permission not granted
                    showStep(OnboardingStep.DEFAULT_LAUNCHER)
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                // Mark as denied so MainActivity doesn't ask again
                prefs.edit().putBoolean("usage_stats_permission_denied", true).apply()
                // Move to next step
                showStep(OnboardingStep.DEFAULT_LAUNCHER)
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun setDefaultLauncher() {
        hasRequestedDefaultLauncher = true
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }
    
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    private fun showDefaultLauncherSkippedDialog() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Continue Without Setting Default?")
            .setMessage("You can set this launcher as default later from Settings. However, you'll need to set it as default to use it as your home screen.\n\nDo you want to continue?")
            .setPositiveButton("Continue") { _, _ ->
                showStep(OnboardingStep.BACKUP_IMPORT)
            }
            .setNegativeButton("Set as Default") { _, _ ->
                setDefaultLauncher()
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }

    private fun selectDisplayStyle(style: String) {
        prefs.edit().putString("view_preference", style).apply()
        displayStyleSelected = true
        
        // Update button states
        if (style == "grid") {
            gridStyleButton.alpha = 1.0f
            listStyleButton.alpha = 0.5f
        } else {
            gridStyleButton.alpha = 0.5f
            listStyleButton.alpha = 1.0f
        }
        
        nextButton.isEnabled = true
        nextButton.text = "Continue"
    }
    
    /**
     * Preloads the app list early in the background to avoid delays when needed
     * This loads and sorts apps asynchronously so it's ready when needed
     */
    private fun preloadAppList() {
        if (cachedAppsList != null) {
            return // Already cached
        }
        if (isPreloadingApps) {
            return // Already preloading
        }
        
        isPreloadingApps = true
        Thread {
            try {
                val packageManager = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                // Load all apps (fast operation)
                val allApps = packageManager.queryIntentActivities(mainIntent, 0)
                    .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                
                // Sort by label in background (this is the expensive operation)
                // Use parallel processing if possible, or optimize sorting
                val sortedApps = allApps.sortedWith(compareBy { app ->
                    try {
                        app.loadLabel(packageManager).toString().lowercase()
                    } catch (e: Exception) {
                        app.activityInfo.packageName.lowercase()
                    }
                })
                
                // Cache the sorted app list
                cachedAppsList = sortedApps
            } catch (e: Exception) {
                // If preload fails, will load on demand
            } finally {
                isPreloadingApps = false
            }
        }.start()
    }
    
    /**
     * Gets the app list, using cached version if available, otherwise loading it
     * Note: This is called from background thread, so sorting is OK
     */
    private fun getAppList(): List<android.content.pm.ResolveInfo> {
        // If cached, return immediately
        if (cachedAppsList != null) {
            return cachedAppsList!!
        }
        
        // If not cached, load it (this is in background thread, so OK)
        val packageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
        
        // Sort by label (expensive but in background thread)
        val sortedApps = apps.sortedWith(compareBy { app ->
            try {
                app.loadLabel(packageManager).toString().lowercase()
            } catch (e: Exception) {
                app.activityInfo.packageName.lowercase()
            }
        })
        
        cachedAppsList = sortedApps
        return sortedApps
    }
    
    private fun loadAppsForFavoritesSelection() {
        // Show loading state initially
        favoritesRecyclerView.visibility = View.GONE
        
        // Check if cache is ready, if not wait a bit for preload
        if (cachedAppsList == null && isPreloadingApps) {
            // Wait for preload to complete (max 1 second)
            Thread {
                var waited = 0
                while (cachedAppsList == null && waited < 1000 && isPreloadingApps) {
                    Thread.sleep(50)
                    waited += 50
                }
                // Continue with loading
                loadAppsForFavoritesSelectionInternal()
            }.start()
        } else {
            // Load immediately
            loadAppsForFavoritesSelectionInternal()
        }
    }
    
    private fun loadAppsForFavoritesSelectionInternal() {
        // Load apps asynchronously to avoid blocking UI
        Thread {
            try {
                val packageManager = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                
                // Phase 1: Show apps immediately with fast sort by package name
                val quickApps = packageManager.queryIntentActivities(mainIntent, 0)
                    .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                    .sortedBy { it.activityInfo.packageName.lowercase() } // Fast sort
                
                // Load existing favorites
                val favoriteAppManager = FavoriteAppManager(prefs)
                val existingFavorites = favoriteAppManager.getFavoriteApps().toMutableSet()
                
                // Show apps immediately (fast)
                runOnUiThread {
                    selectedFavorites = existingFavorites
                    favoritesAdapter = FavoritesOnboardingAdapter(quickApps, selectedFavorites) { packageName, isChecked ->
                        if (isChecked) {
                            selectedFavorites.add(packageName)
                        } else {
                            selectedFavorites.remove(packageName)
                        }
                    }
                    favoritesRecyclerView.adapter = favoritesAdapter
                    favoritesRecyclerView.visibility = View.VISIBLE
                    favoritesRecyclerView.post {
                        favoritesRecyclerView.requestLayout()
                    }
                }
                
                // Phase 2: Sort properly by label in background and update
                if (cachedAppsList == null) {
                    val sortedApps = quickApps.sortedWith(compareBy { app ->
                        try {
                            app.loadLabel(packageManager).toString().lowercase()
                        } catch (e: Exception) {
                            app.activityInfo.packageName.lowercase()
                        }
                    })
                    cachedAppsList = sortedApps
                    
                    // Update with properly sorted list
                    runOnUiThread {
                        favoritesAdapter = FavoritesOnboardingAdapter(sortedApps, selectedFavorites) { packageName, isChecked ->
                            if (isChecked) {
                                selectedFavorites.add(packageName)
                            } else {
                                selectedFavorites.remove(packageName)
                            }
                        }
                        favoritesRecyclerView.adapter = favoritesAdapter
                    }
                } else {
                    // Use cached sorted list
                    runOnUiThread {
                        favoritesAdapter = FavoritesOnboardingAdapter(cachedAppsList!!, selectedFavorites) { packageName, isChecked ->
                            if (isChecked) {
                                selectedFavorites.add(packageName)
                            } else {
                                selectedFavorites.remove(packageName)
                            }
                        }
                        favoritesRecyclerView.adapter = favoritesAdapter
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    favoritesRecyclerView.visibility = View.VISIBLE
                }
            }
        }.start()
    }
    
    private fun saveSelectedFavorites() {
        // Save favorites
        prefs.edit().putStringSet("favorite_apps", selectedFavorites).apply()
        
        // Set to show favorites by default
        prefs.edit().putBoolean("show_all_apps_mode", false).apply()
    }
    
    private fun loadAppsForWorkspacesSelection() {
        // Show loading state
        workspacesAppsRecyclerView.visibility = View.GONE
        
        // Load apps asynchronously to avoid blocking UI
        Thread {
            try {
                // Use cached app list if available, otherwise load quickly
                val apps = if (cachedAppsList != null) {
                    cachedAppsList!!
                } else {
                    // Quick load without expensive sorting
                    val packageManager = packageManager
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    packageManager.queryIntentActivities(mainIntent, 0)
                        .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                        .sortedBy { it.activityInfo.packageName.lowercase() }
                }
                
                // Update UI on main thread
                runOnUiThread {
                    allAppsList = apps
                    
                    // Update available apps (filter out apps already in workspaces)
                    updateAvailableAppsForWorkspace()
                    
                    // Initialize workspaces list adapter
                    workspacesListAdapter = WorkspacesListAdapter(createdWorkspaces) { position ->
                        // Remove workspace
                        createdWorkspaces.removeAt(position)
                        workspacesListAdapter.notifyDataSetChanged()
                        updateWorkspacesListVisibility()
                        // Refresh available apps when workspace is deleted
                        updateAvailableAppsForWorkspace()
                    }
                    workspacesListRecyclerView.adapter = workspacesListAdapter
                    
                    updateWorkspacesListVisibility()
                    workspacesAppsRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                // If loading fails, show empty state
                runOnUiThread {
                    workspacesAppsRecyclerView.visibility = View.VISIBLE
                }
            }
        }.start()
    }
    
    private fun updateAvailableAppsForWorkspace() {
        // Get all apps already used in created workspaces
        val usedApps = createdWorkspaces.flatMap { it.second }.toSet()
        
        // Filter out apps that are already in workspaces
        val availableApps = allAppsList.filter { 
            it.activityInfo.packageName !in usedApps 
        }
        
        // Also remove apps that are currently selected for this workspace from usedApps check
        // (so they can be selected, but once workspace is created, they'll be filtered)
        
        // Create and set adapter for apps selection
        workspacesAppsAdapter = WorkspacesAppsAdapter(availableApps, currentWorkspaceApps) { packageName, isChecked ->
            if (isChecked) {
                currentWorkspaceApps.add(packageName)
            } else {
                currentWorkspaceApps.remove(packageName)
            }
            // Update button state based on selection
            updateAddWorkspaceButtonState()
        }
        workspacesAppsRecyclerView.adapter = workspacesAppsAdapter
        
        // Update button state
        updateAddWorkspaceButtonState()
        
        // Force RecyclerView to recalculate height after adapter is set
        workspacesAppsRecyclerView.post {
            workspacesAppsRecyclerView.requestLayout()
        }
    }
    
    private fun updateAddWorkspaceButtonState() {
        val hasName = workspaceNameInput.text.toString().trim().isNotEmpty()
        val hasApps = currentWorkspaceApps.isNotEmpty()
        addWorkspaceButton.isEnabled = hasName && hasApps
        addWorkspaceButton.alpha = if (hasName && hasApps) 1.0f else 0.5f
    }
    
    private fun setupWorkspaceButtons() {
        // Add text change listener to workspace name input
        workspaceNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAddWorkspaceButtonState()
            }
            override fun afterTextChanged(s: Editable?) {
                updateAddWorkspaceButtonState()
            }
        })
        
        // Initialize button state
        updateAddWorkspaceButtonState()
        
        addWorkspaceButton.setOnClickListener {
            val workspaceName = workspaceNameInput.text.toString().trim()
            if (workspaceName.isEmpty()) {
                Toast.makeText(this, "Please enter a workspace name", Toast.LENGTH_SHORT).show()
                workspaceNameInput.requestFocus()
                return@setOnClickListener
            }
            
            if (currentWorkspaceApps.isEmpty()) {
                Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Add workspace
            createdWorkspaces.add(Pair(workspaceName, currentWorkspaceApps.toSet()))
            workspacesListAdapter.notifyDataSetChanged()
            
            // Clear inputs
            workspaceNameInput.text.clear()
            currentWorkspaceApps.clear()
            
            // Update available apps (remove apps that are now in a workspace)
            updateAvailableAppsForWorkspace()
            
            updateWorkspacesListVisibility()
            
            // Scroll to show the created workspace
            onboardingScrollView.post {
                workspacesListRecyclerView.let {
                    if (it.visibility == View.VISIBLE) {
                        val targetY = it.top - 100 // Scroll to show workspace list
                        onboardingScrollView.smoothScrollTo(0, targetY)
                    }
                }
            }
            
            Toast.makeText(this, "Workspace '$workspaceName' created with ${createdWorkspaces.last().second.size} apps", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateWorkspacesListVisibility() {
        if (createdWorkspaces.isNotEmpty()) {
            workspacesListTitle.visibility = View.VISIBLE
            workspacesListRecyclerView.visibility = View.VISIBLE
        } else {
            workspacesListTitle.visibility = View.GONE
            workspacesListRecyclerView.visibility = View.GONE
        }
    }
    
    private fun saveCreatedWorkspaces() {
        val workspaceManager = WorkspaceManager(prefs)
        createdWorkspaces.forEach { (name, apps) ->
            workspaceManager.createWorkspace(name, apps)
        }
    }
    
    /**
     * Fix dialog text colors programmatically for latest Android versions
     */
    
    private fun showWorkspaceCreationDialog() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Create Workspaces")
            .setMessage("Would you like to create workspaces to organize your apps? You can create workspaces later from settings.")
            .setPositiveButton("Create Workspace") { _, _ ->
                showCreateWorkspaceDialog()
            }
            .setNegativeButton("Skip") { _, _ ->
                // User skipped, continue
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }
    
    private fun showCreateWorkspaceDialog() {
        val input = EditText(this)
        input.hint = "Workspace name (e.g., Work, Personal)"
        
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Create Workspace")
            .setView(input)
            .setPositiveButton("Next") { _, _ ->
                val workspaceName = input.text.toString().trim()
                if (workspaceName.isNotEmpty()) {
                    showAppPickerForWorkspace(workspaceName)
                } else {
                    Toast.makeText(this, "Please enter a workspace name", Toast.LENGTH_SHORT).show()
                    showCreateWorkspaceDialog()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Ask if user wants to create another or skip
                showWorkspaceCreationDialog()
            }
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }
    
    private fun showAppPickerForWorkspace(workspaceName: String) {
        val packageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val allApps = packageManager.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        
        val appNames = allApps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val packageNames = allApps.map { it.activityInfo.packageName }.toTypedArray()
        val checkedItems = BooleanArray(appNames.size) { false }
        val selectedApps = mutableSetOf<String>()
        
        val dialogBuilder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Select Apps for '$workspaceName'")
            .setMultiChoiceItems(appNames, checkedItems) { _, which, isChecked ->
                val packageName = packageNames[which]
                if (isChecked) {
                    selectedApps.add(packageName)
                } else {
                    selectedApps.remove(packageName)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                if (selectedApps.isEmpty()) {
                    Toast.makeText(this, "Please select at least one app", Toast.LENGTH_SHORT).show()
                    showAppPickerForWorkspace(workspaceName)
                } else {
                    val workspaceManager = WorkspaceManager(prefs)
                    workspaceManager.createWorkspace(workspaceName, selectedApps)
                    Toast.makeText(this, "Workspace '$workspaceName' created with ${selectedApps.size} apps", Toast.LENGTH_SHORT).show()
                    
                    // Ask if user wants to create another workspace
                    AlertDialog.Builder(this, R.style.CustomDialogTheme)
                        .setTitle("Create Another Workspace?")
                        .setMessage("Would you like to create another workspace?")
                        .setPositiveButton("Yes") { _, _ ->
                            showCreateWorkspaceDialog()
                        }
                        .setNegativeButton("No") { _, _ ->
                            // Continue with onboarding
                        }
                        .setOnDismissListener {
                            // Continue with onboarding when dismissed
                        }
                        .show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showCreateWorkspaceDialog()
            }
        
        val dialog = dialogBuilder.create()
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        dialog.show()
    }
    
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(this, R.color.text)
            val nord7Color = ContextCompat.getColor(this, R.color.nord7)
            
            val titleView = dialog.findViewById<TextView>(android.R.id.title)
            titleView?.setTextColor(textColor)
            
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.setTextColor(textColor)
            
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(nord7Color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(textColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(textColor)
            
            val listView = dialog.listView
            if (listView != null) {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(textColor)
                    }
                }
            }
        } catch (e: Exception) {
            // If anything fails, at least the theme should handle it
        }
    }

    private fun finishSetup() {
        prefs.edit().putBoolean("isFirstTime", false).apply()
        startActivity(Intent(this, MainActivity::class.java).apply { 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK 
        })
        finish()
    }
    
    private fun setupBackupImportButtons() {
        val importButton = findViewById<Button>(R.id.import_backup_button)
        val skipButton = findViewById<Button>(R.id.skip_backup_button)
        
        importButton.setOnClickListener {
            requestBackupFile()
        }
        
        skipButton.setOnClickListener {
            backupImported = false
            goToNextStep()
        }
    }
    
    private fun requestBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_BACKUP_REQUEST_CODE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                IMPORT_BACKUP_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        importBackupFromFile(uri)
                    }
                }
            }
        }
    }
    
    private fun importBackupFromFile(uri: Uri) {
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
                    
                    // Import app usage preferences
                    if (settingsJson.has("app_usage")) {
                        val appUsagePrefs = getSharedPreferences("app_usage", MODE_PRIVATE)
                        val appUsageJson = settingsJson.getJSONObject("app_usage")
                        val editor = appUsagePrefs.edit()
                        importPreferences(appUsageJson, editor)
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
                    
                    // Import daily usage preferences
                    if (settingsJson.has("daily_usage_prefs")) {
                        val dailyUsagePrefs = getSharedPreferences("daily_usage_prefs", MODE_PRIVATE)
                        val dailyUsageJson = settingsJson.getJSONObject("daily_usage_prefs")
                        val editor = dailyUsagePrefs.edit()
                        importPreferences(dailyUsageJson, editor)
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
                
                backupImported = true
                Toast.makeText(this, "Backup imported successfully", Toast.LENGTH_SHORT).show()
                // Skip to complete step
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
}