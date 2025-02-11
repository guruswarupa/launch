package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class AppDockManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val appDock: LinearLayout,
    private val packageManager: PackageManager
) {
    private val DOCK_APPS_KEY = "dock_apps"
    private lateinit var addIcon: ImageView

    init {
        ensureAddIcon()
    }

    fun openAppPicker() {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
        val sortedApps = apps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val appNames = sortedApps.map { it.loadLabel(packageManager).toString() }
        val appPackageNames = sortedApps.map { it.activityInfo.packageName }

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Choose an app to add to the dock")
        builder.setItems(appNames.toTypedArray()) { _, which ->
            val selectedPackage = appPackageNames[which]
            addAppToDock(selectedPackage)
        }

        builder.show()
    }

    private fun addAppToDock(packageName: String) {
        // Get the ApplicationInfo for the package
        val appInfo = packageManager.getApplicationInfo(packageName, 0)

        // Get the app's icon and name
        val appIcon = packageManager.getApplicationIcon(appInfo)
        val appName = packageManager.getApplicationLabel(appInfo).toString()

        // Add to dock (LinearLayout for the dock)
        val appIconView = ImageView(context).apply {
            setImageDrawable(appIcon)

            // Set LayoutParams with margin between icons
            val params = LinearLayout.LayoutParams(120, 120).apply {
                setMargins(16, 0, 16, 0) // Set horizontal margin (16dp on left and right)
            }
            layoutParams = params

            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
            }

            // Long press to remove app from dock
            setOnLongClickListener {
                showRemoveDockAppDialog(packageName)
                true
            }
        }

        // Add the app icon to the left side (beginning) of the dock
        appDock.addView(appIconView, 0)  // Adding it at the start

        // Save this app's package name to SharedPreferences
        saveAppToDock(packageName)

        if (appDock.childCount == 0) {
            addIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_add) // Make sure you have an add icon in your drawable
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    setMargins(16, 0, 16, 0) // Add margins as needed
                }
                setOnClickListener {
                    openAppPicker()
                }
            }
            appDock.addView(addIcon)  // Add "+" icon to the end
        }
    }

    private fun showRemoveDockAppDialog(packageName: String) {
        val dialog = AlertDialog.Builder(context)
            .setMessage("Do you want to remove this app from the dock?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                // Remove the app's package name from SharedPreferences
                removeAppFromDock(packageName)

                // Refresh the dock to update the UI
                refreshDock()
            }
            .setNegativeButton("No", null)
            .create()

        dialog.show()
    }

    private fun refreshDock() {
        appDock.removeAllViews()
        loadDockApps()
        ensureAddIcon()
    }

    private fun ensureAddIcon() {
        // Check if the last child of the dock is the "+" icon
        val lastChild = appDock.getChildAt(appDock.childCount - 1)
        if (lastChild == null || (lastChild as? ImageView)?.drawable != context.resources.getDrawable(R.drawable.ic_add)) {
            addIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_add) // Ensure you have an add icon in your drawable
                layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                    setMargins(16, 0, 16, 0) // Add margins as needed
                }
                setOnClickListener {
                    openAppPicker()
                }
            }
            appDock.addView(addIcon) // Add the "+" icon if not already present
        }
    }

    private fun saveAppToDock(packageName: String) {
        // Retrieve the current set of dock apps
        val dockApps = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // Add the new app's package name
        dockApps.add(packageName)

        // Save the updated set of package names back to SharedPreferences
        sharedPreferences.edit().putStringSet(DOCK_APPS_KEY, dockApps).apply()
    }

    private fun removeAppFromDock(packageName: String) {
        // Retrieve the current set of dock apps
        val dockApps = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // Remove the app's package name from the set
        if (dockApps.contains(packageName)) {
            dockApps.remove(packageName)

            // Save the updated set of package names back to SharedPreferences
            sharedPreferences.edit().putStringSet(DOCK_APPS_KEY, dockApps).apply()
        }
    }

    fun loadDockApps() {
        // Load the saved dock apps from SharedPreferences
        val dockApps = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf()) ?: mutableSetOf()

        // Add the saved apps to the dock
        for (packageName in dockApps) {
            addAppToDock(packageName)
        }
    }
}

