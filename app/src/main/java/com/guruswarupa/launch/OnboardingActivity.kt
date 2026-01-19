package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
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
    DISPLAY_STYLE,
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

    // UI References
    private lateinit var welcomeStep: LinearLayout
    private lateinit var permissionsStep: LinearLayout
    private lateinit var defaultLauncherStep: LinearLayout
    private lateinit var displayStyleStep: LinearLayout
    private lateinit var weatherApiKeyStep: LinearLayout
    private lateinit var completeStep: LinearLayout
    private lateinit var backButton: Button
    private lateinit var nextButton: Button
    private lateinit var gridStyleButton: Button
    private lateinit var listStyleButton: Button
    private lateinit var weatherApiKeyInput: EditText

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
                "We need access to your photos so you can set custom wallpapers for your home screen. You can double-tap the search bar anytime to change your wallpaper.",
                103
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
                "We need access to your storage to load custom wallpapers for your home screen. You can double-tap the search bar anytime to change your wallpaper.",
                103
            ))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        
        // Make status bar and navigation bar transparent
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        initializeViews()
        setupClickListeners()
        
        // Check if we should continue from default launcher step (when MainActivity redirects here)
        val continueFromDefaultLauncher = intent.getBooleanExtra("continueFromDefaultLauncher", false)
        if (continueFromDefaultLauncher && isDefaultLauncher()) {
            // User set launcher as default, continue to display style step
            showStep(OnboardingStep.DISPLAY_STYLE)
        } else {
            showStep(OnboardingStep.WELCOME)
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

    private fun initializeViews() {
        welcomeStep = findViewById(R.id.welcome_step)
        permissionsStep = findViewById(R.id.permissions_step)
        defaultLauncherStep = findViewById(R.id.default_launcher_step)
        displayStyleStep = findViewById(R.id.display_style_step)
        weatherApiKeyStep = findViewById(R.id.weather_api_key_step)
        completeStep = findViewById(R.id.complete_step)
        
        backButton = findViewById(R.id.back_button)
        nextButton = findViewById(R.id.next_button)
        gridStyleButton = findViewById(R.id.grid_style_button)
        listStyleButton = findViewById(R.id.list_style_button)
        weatherApiKeyInput = findViewById(R.id.weather_api_key_input)

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
        
        // Hide all steps
        welcomeStep.visibility = View.GONE
        permissionsStep.visibility = View.GONE
        defaultLauncherStep.visibility = View.GONE
        displayStyleStep.visibility = View.GONE
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
            OnboardingStep.DISPLAY_STYLE -> {
                displayStyleStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = if (displayStyleSelected) "Continue" else "Select Style First"
                nextButton.isEnabled = displayStyleSelected
                updateProgressIndicator(3)
            }
            OnboardingStep.WEATHER_API_KEY -> {
                weatherApiKeyStep.visibility = View.VISIBLE
                backButton.visibility = View.VISIBLE
                nextButton.text = "Continue"
                nextButton.isEnabled = true
                updateProgressIndicator(4)
                // Load existing API key if any
                val existingKey = prefs.getString("weather_api_key", "") ?: ""
                if (existingKey.isNotEmpty()) {
                    weatherApiKeyInput.setText(existingKey)
                }
            }
            OnboardingStep.COMPLETE -> {
                completeStep.visibility = View.VISIBLE
                backButton.visibility = View.GONE
                nextButton.text = "Launch App"
                updateProgressIndicator(5)
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
        }
    }

    private fun goToPreviousStep() {
        when (currentStep) {
            OnboardingStep.WELCOME -> {
                // Can't go back from welcome
            }
            OnboardingStep.PERMISSIONS -> showStep(OnboardingStep.WELCOME)
            OnboardingStep.DEFAULT_LAUNCHER -> showStep(OnboardingStep.PERMISSIONS)
            OnboardingStep.DISPLAY_STYLE -> showStep(OnboardingStep.DEFAULT_LAUNCHER)
            OnboardingStep.WEATHER_API_KEY -> showStep(OnboardingStep.DISPLAY_STYLE)
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
                    showStep(OnboardingStep.DISPLAY_STYLE)
                } else {
                    setDefaultLauncher()
                }
            }
            OnboardingStep.DISPLAY_STYLE -> {
                if (displayStyleSelected) {
                    showStep(OnboardingStep.WEATHER_API_KEY)
                } else {
                    Toast.makeText(this, "Please select a display style first", Toast.LENGTH_SHORT).show()
                }
            }
            OnboardingStep.WEATHER_API_KEY -> {
                // Save weather API key (can be empty if user skips)
                val apiKey = weatherApiKeyInput.text.toString().trim()
                prefs.edit().putString("weather_api_key", apiKey).apply()
                if (apiKey.isNotEmpty()) {
                    prefs.edit().putBoolean("weather_api_key_rejected", false).apply()
                }
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
                // Launcher is set as default, move to display style step
                showStep(OnboardingStep.DISPLAY_STYLE)
            } else {
                // User didn't set it as default, ask if they want to continue anyway
                showDefaultLauncherSkippedDialog()
            }
        } else if (currentStep == OnboardingStep.DEFAULT_LAUNCHER && isDefaultLauncher()) {
            // User might have set launcher as default and Android launched MainActivity which redirected here
            // Check if we should move to next step
            showStep(OnboardingStep.DISPLAY_STYLE)
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
                showStep(OnboardingStep.DISPLAY_STYLE)
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
     * Fix dialog text colors programmatically for latest Android versions
     */
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val whiteColor = ContextCompat.getColor(this, android.R.color.white)
            val nord7Color = ContextCompat.getColor(this, R.color.nord7)
            
            val titleView = dialog.findViewById<TextView>(android.R.id.title)
            titleView?.setTextColor(whiteColor)
            
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.setTextColor(whiteColor)
            
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(nord7Color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(whiteColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(whiteColor)
            
            val listView = dialog.listView
            if (listView != null) {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(whiteColor)
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
}