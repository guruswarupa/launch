package com.guruswarupa.launch

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppLockSettingsActivity : ComponentActivity() {

    private lateinit var appLockManager: AppLockManager
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var enableAppLockSwitch: Switch
    private lateinit var changePinButton: Button
    private lateinit var resetAppLockButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appLockManager = AppLockManager(this)

        // Create layout programmatically
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val titleText = TextView(this).apply {
            text = "App Lock Settings"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        mainLayout.addView(titleText)

        // Enable/Disable App Lock
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val switchLabel = TextView(this).apply {
            text = "Enable App Lock"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        enableAppLockSwitch = Switch(this).apply {
            isChecked = appLockManager.isAppLockEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !appLockManager.isPinSet()) {
                    // Enabling app lock - set up PIN
                    appLockManager.setupPin { success ->
                        if (!success) {
                            this.isChecked = false
                        }
                    }
                } else if (!isChecked && appLockManager.isPinSet()) {
                    // Disabling app lock - verify PIN first
                    appLockManager.verifyPin { isAuthenticated ->
                        if (isAuthenticated) {
                            appLockManager.setAppLockEnabled(false)
                            Toast.makeText(this@AppLockSettingsActivity, "App Lock disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            // Revert switch back to enabled state if PIN verification failed
                            this.isChecked = true
                        }
                    }
                } else {
                    // Direct enable/disable when no PIN is set
                    appLockManager.setAppLockEnabled(isChecked)
                }
            }
        }

        switchLayout.addView(switchLabel)
        switchLayout.addView(enableAppLockSwitch)
        mainLayout.addView(switchLayout)

        // Change PIN button
        changePinButton = Button(this).apply {
            text = if (appLockManager.isPinSet()) "Change PIN" else "Set PIN"
            setOnClickListener {
                if (appLockManager.isPinSet()) {
                    appLockManager.changePin { success ->
                        if (success) {
                            text = "Change PIN"
                        }
                    }
                } else {
                    appLockManager.setupPin { success ->
                        if (success) {
                            text = "Change PIN"
                            enableAppLockSwitch.isChecked = true
                        }
                    }
                }
            }
        }
        mainLayout.addView(changePinButton)

        // Reset App Lock button
        resetAppLockButton = Button(this).apply {
            text = "Reset App Lock"
            setOnClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this@AppLockSettingsActivity)
                    .setTitle("Reset App Lock")
                    .setMessage("This will remove the PIN and unlock all apps. Are you sure?")
                    .setPositiveButton("Reset") { _, _ ->
                        appLockManager.resetAppLock()
                        enableAppLockSwitch.isChecked = false
                        changePinButton.text = "Set PIN"
                        recreateAppsList()
                        Toast.makeText(this@AppLockSettingsActivity, "App Lock reset", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        mainLayout.addView(resetAppLockButton)

        // Apps list title
        val appsTitle = TextView(this).apply {
            text = "Select Apps to Lock"
            textSize = 20f
            setPadding(0, 32, 0, 16)
        }
        mainLayout.addView(appsTitle)

        // Apps RecyclerView
        appsRecyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AppLockSettingsActivity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainLayout.addView(appsRecyclerView)

        setContentView(mainLayout)

        recreateAppsList()
    }

    private fun recreateAppsList() {
        val installedApps = getInstalledApps()
        val adapter = AppLockAdapter(installedApps, appLockManager) {
            // Refresh callback if needed
        }
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
        private val onItemChanged: () -> Unit
    ) : RecyclerView.Adapter<AppLockAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(android.R.id.icon)
            val name: TextView = view.findViewById(android.R.id.text1)
            val lockSwitch: Switch = view.findViewById(android.R.id.toggle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val layout = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 16, 16, 16)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val icon = ImageView(parent.context).apply {
                id = android.R.id.icon
                layoutParams = LinearLayout.LayoutParams(72, 72)
                setPadding(0, 0, 16, 0)
            }

            val name = TextView(parent.context).apply {
                id = android.R.id.text1
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val toggle = Switch(parent.context).apply {
                id = android.R.id.toggle
            }

            layout.addView(icon)
            layout.addView(name)
            layout.addView(toggle)

            return ViewHolder(layout)
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
                if (isChecked) {
                    appLockManager.lockApp(app.packageName)
                } else {
                    appLockManager.unlockApp(app.packageName)
                }
                onItemChanged()
            }
        }

        override fun getItemCount() = apps.size
    }
}