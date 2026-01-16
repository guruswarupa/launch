package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
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

class OnboardingActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }

    private var hasRequestedStoragePermission = false
    private var currentPermissionIndex = 0

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<Button>(R.id.permission_button).setOnClickListener { 
            startPermissionFlow() 
        }
        findViewById<Button>(R.id.continue_button).setOnClickListener { continueSetup() }
        findViewById<Button>(R.id.set_default_launcher_button).setOnClickListener { setDefaultLauncher() }
        findViewById<Button>(R.id.set_display_style).setOnClickListener { chooseDisplayStyle() }
    }

    override fun onResume() {
        super.onResume()
        // Check if user returned from storage permission settings
        if (hasRequestedStoragePermission && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            hasRequestedStoragePermission = false
            // Check if storage permission was granted, then continue
            if (hasStoragePermission()) {
                requestUsageStatsPermission()
            } else {
                showPermissionDeniedDialog("Storage", "Without storage access, you won't be able to set custom wallpapers. You can grant this permission later in Settings.")
            }
        }
        
        // Check if user returned from usage stats settings
        if (hasUsageStatsPermission()) {
            finishSetup()
        }
    }

    private fun startPermissionFlow() {
        currentPermissionIndex = 0
        requestNextPermission()
    }

    private fun requestNextPermission() {
        // Filter out already granted permissions
        val remainingPermissions = permissionList.filter { permissionInfo ->
            ContextCompat.checkSelfPermission(this, permissionInfo.permission) != PackageManager.PERMISSION_GRANTED
        }

        if (remainingPermissions.isEmpty()) {
            // All runtime permissions granted, move to storage
            requestStoragePermission()
            return
        }

        // Update current permission index to the first missing one
        val firstMissing = permissionList.indexOfFirst { 
            ContextCompat.checkSelfPermission(this, it.permission) != PackageManager.PERMISSION_GRANTED 
        }
        if (firstMissing >= 0) {
            currentPermissionIndex = firstMissing
            showPermissionExplanation(permissionList[currentPermissionIndex])
        } else {
            requestStoragePermission()
        }
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
                // User skipped, move to next permission
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
                showPermissionDeniedDialog(permissionInfo.title, permissionInfo.explanation)
            }
            // Still move to next permission
            requestNextPermission()
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
                // Fallback for devices that don't support this check
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
            finishSetup()
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
                    finishSetup()
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                finishSetup()
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }
    
    /**
     * Fix dialog text colors programmatically for latest Android versions
     * This ensures text is visible even if theme attributes don't apply correctly
     */
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val whiteColor = ContextCompat.getColor(this, android.R.color.white)
            val nord7Color = ContextCompat.getColor(this, R.color.nord7)
            
            // Fix title text color
            val titleView = dialog.findViewById<TextView>(android.R.id.title)
            titleView?.setTextColor(whiteColor)
            
            // Fix message text color
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            messageView?.setTextColor(whiteColor)
            
            // Fix button text colors
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(nord7Color)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(whiteColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(whiteColor)
            
            // Fix list items if present
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

    private fun continueSetup() {
        // This is called by the continue button - check what's left
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            finishSetup()
        }
    }

    private fun finishSetup() {
        prefs.edit().putBoolean("isFirstTime", false).apply()
        startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finish()
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

    private fun setDefaultLauncher() = startActivity(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun chooseDisplayStyle() {
        val dialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Choose App Display Style")
            .setMessage("Grid or List?")
            .setPositiveButton("Grid") { _, _ -> setViewPreference("grid") }
            .setNegativeButton("List") { _, _ -> setViewPreference("list") }
            .create()
        
        dialog.setOnShowListener {
            fixDialogTextColors(dialog)
        }
        
        dialog.show()
    }

    private fun setViewPreference(style: String) {
        prefs.edit().putString("view_preference", style).apply()
        (this as? MainActivity)?.loadApps()
    }
}