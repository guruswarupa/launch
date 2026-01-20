package com.guruswarupa.launch

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppLockSettingsActivity : ComponentActivity() {

    private lateinit var appLockManager: AppLockManager
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var enableAppLockSwitch: Switch
    private lateinit var fingerprintSwitch: Switch
    private lateinit var fingerprintLayout: LinearLayout
    private lateinit var changePinButton: Button
    private lateinit var resetAppLockButton: Button
    private var isPinVerifiedForThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock_settings)
        
        // Make status bar and navigation bar transparent
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        appLockManager = AppLockManager(this)

        // Initialize views
        enableAppLockSwitch = findViewById(R.id.enable_app_lock_switch)
        fingerprintSwitch = findViewById(R.id.fingerprint_switch)
        fingerprintLayout = findViewById(R.id.fingerprint_layout)
        changePinButton = findViewById(R.id.change_pin_button)
        resetAppLockButton = findViewById(R.id.reset_app_lock_button)
        appsRecyclerView = findViewById(R.id.apps_recycler_view)

        // Setup expandable sections
        setupExpandableSections()

        // Setup switches
        enableAppLockSwitch.isChecked = appLockManager.isAppLockEnabled()
        enableAppLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !appLockManager.isPinSet()) {
                // Enabling app lock - set up PIN
                appLockManager.setupPin { success ->
                    if (!success) {
                        enableAppLockSwitch.isChecked = false
                    } else {
                        changePinButton.text = "Change PIN"
                    }
                }
            } else if (!isChecked && appLockManager.isPinSet()) {
                // Disabling app lock - verify PIN first
                appLockManager.verifyPin { isAuthenticated ->
                    if (isAuthenticated) {
                        appLockManager.setAppLockEnabled(false)
                        Toast.makeText(this, "App Lock disabled", Toast.LENGTH_SHORT).show()
                    } else {
                        // Revert switch back to enabled state if PIN verification failed
                        enableAppLockSwitch.isChecked = true
                    }
                }
            } else {
                // Direct enable/disable when no PIN is set
                appLockManager.setAppLockEnabled(isChecked)
            }
        }

        // Fingerprint authentication switch (only show if available)
        if (appLockManager.isFingerprintAvailable()) {
            fingerprintLayout.visibility = View.VISIBLE
            fingerprintSwitch.isChecked = appLockManager.isFingerprintEnabled()
            fingerprintSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (appLockManager.isPinSet()) {
                    appLockManager.setFingerprintEnabled(isChecked)
                    Toast.makeText(
                        this,
                        if (isChecked) "Fingerprint enabled" else "Fingerprint disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    fingerprintSwitch.isChecked = false
                    Toast.makeText(this, "Please set up a PIN first", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            fingerprintLayout.visibility = View.GONE
        }

        // Change PIN button
        changePinButton.text = if (appLockManager.isPinSet()) "Change PIN" else "Set PIN"
        changePinButton.setOnClickListener {
            if (appLockManager.isPinSet()) {
                appLockManager.changePin { success ->
                    if (success) {
                        changePinButton.text = "Change PIN"
                    }
                }
            } else {
                appLockManager.setupPin { success ->
                    if (success) {
                        changePinButton.text = "Change PIN"
                        enableAppLockSwitch.isChecked = true
                    }
                }
            }
        }

        // Reset App Lock button
        resetAppLockButton.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Reset App Lock")
                .setMessage("This will remove the PIN and unlock all apps. Are you sure?")
                .setPositiveButton("Reset") { _, _ ->
                    appLockManager.resetAppLock { success ->
                        if (success) {
                            enableAppLockSwitch.isChecked = false
                            fingerprintSwitch.isChecked = false
                            changePinButton.text = "Set PIN"
                            recreateAppsList()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Setup RecyclerView
        appsRecyclerView.layoutManager = LinearLayoutManager(this)

        recreateAppsList()
    }
    
    private fun setupExpandableSections() {
        // App Lock Settings Section
        val appLockSettingsHeader = findViewById<LinearLayout>(R.id.app_lock_settings_header)
        val appLockSettingsContent = findViewById<LinearLayout>(R.id.app_lock_settings_content)
        val appLockSettingsArrow = findViewById<TextView>(R.id.app_lock_settings_arrow)
        setupSectionToggle(appLockSettingsHeader, appLockSettingsContent, appLockSettingsArrow)
        
        // Apps List Section
        val appsListHeader = findViewById<LinearLayout>(R.id.apps_list_header)
        val appsListContent = appsRecyclerView
        val appsListArrow = findViewById<TextView>(R.id.apps_list_arrow)
        setupSectionToggle(appsListHeader, appsListContent, appsListArrow)
    }
    
    private fun setupSectionToggle(header: LinearLayout, content: View, arrow: TextView) {
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

    private fun recreateAppsList() {
        val installedApps = getInstalledApps()
        val adapter = AppLockAdapter(
            installedApps,
            appLockManager,
            onItemChanged = {},
            requestPinAuthIfNeeded = { onSuccess ->
                if (isPinVerifiedForThisSession) {
                    onSuccess()
                } else {
                    appLockManager.verifyPin { success ->
                        if (success) {
                            isPinVerifiedForThisSession = true
                            onSuccess()
                        } else {
                            Toast.makeText(this, "PIN verification failed", Toast.LENGTH_SHORT).show()
                            recreateAppsList() // Reset UI toggle state if auth failed
                        }
                    }
                }
            }
        )
        appsRecyclerView.adapter = adapter
    }

    private fun getInstalledApps(): List<AppInfo> {
        val packageManager = packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // Only user apps
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(packageManager).toString(),
                    icon = appInfo.loadIcon(packageManager)
                )
            }
            .sortedBy { it.appName }

        return apps
    }

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )

    class AppLockAdapter(
        private val apps: List<AppInfo>,
        private val appLockManager: AppLockManager,
        private val onItemChanged: () -> Unit,
        private val requestPinAuthIfNeeded: (onSuccess: () -> Unit) -> Unit
    ) : RecyclerView.Adapter<AppLockAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val lockSwitch: Switch = view.findViewById(R.id.lock_switch)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_lock, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.appName

            // Clear listener first to prevent unwanted triggers
            holder.lockSwitch.setOnCheckedChangeListener(null)
            holder.lockSwitch.isChecked = appLockManager.isAppLocked(app.packageName)

            // Set listener after setting the state
            holder.lockSwitch.setOnCheckedChangeListener { _, isChecked ->
                requestPinAuthIfNeeded {
                    if (isChecked) {
                        appLockManager.lockApp(app.packageName)
                    } else {
                        appLockManager.unlockApp(app.packageName)
                    }
                    onItemChanged()
                }

                // Revert switch visually if user cancels or fails auth
                if (!appLockManager.isPinSet()) {
                    holder.lockSwitch.isChecked = false
                }
            }
        }

        override fun getItemCount() = apps.size
    }
}