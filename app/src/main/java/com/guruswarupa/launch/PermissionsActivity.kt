package com.guruswarupa.launch

import android.Manifest
import android.app.AppOpsManager
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

class PermissionsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private var hasRequestedUsageStats = false
    private var hasRequestedOverlay = false
    private lateinit var permissionsList: LinearLayout
    private val permissionToggles = mutableMapOf<String, SwitchCompat>()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make status bar and navigation bar transparent BEFORE setContentView
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        window.decorView.systemUiVisibility = 
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        
        setContentView(R.layout.activity_permissions)
        
        setupTheme()

        permissionsList = findViewById(R.id.permissions_list)
        findViewById<Button>(R.id.done_button).setOnClickListener {
            finish()
        }

        setupPermissionsList()
    }
    
    private fun setupTheme() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val overlay = findViewById<View>(R.id.settings_overlay)
        
        if (isDarkMode) {
            overlay.setBackgroundColor("#CC000000".toColorInt())
        } else {
            overlay.setBackgroundColor("#66FFFFFF".toColorInt())
        }
        
        setupWallpaper()
        
        window.decorView.post {
            makeSystemBarsTransparent(isDarkMode)
        }
    }
    
    private fun setupWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaper_background)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED)) {
            try {
                val wallpaperManager = WallpaperManager.getInstance(this)
                val wallpaperDrawable = wallpaperManager.drawable
                if (wallpaperDrawable != null) {
                    wallpaperImageView.setImageDrawable(wallpaperDrawable)
                }
            } catch (_: Exception) {
                wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
            }
        } else {
            wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
        }
    }
    
    @Suppress("DEPRECATION")
    private fun makeSystemBarsTransparent(isDarkMode: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                window.setDecorFitsSystemWindows(false)
                
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
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
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
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
            } catch (_: Exception) {
            }
        }
    }

    private fun setupPermissionsList() {
        permissionsList.removeAllViews()
        permissionToggles.clear()

        data class PermissionItem(
            val permission: String?,
            val name: String,
            val description: String,
            val isGranted: Boolean,
            val isSpecial: Boolean = false,
            val isLauncher: Boolean = false,
            val isOverlay: Boolean = false
        )

        val allPermissions = mutableListOf<PermissionItem>()

        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            permission = Manifest.permission.READ_CONTACTS,
            name = "Contacts",
            description = "Access your contacts to search and call them",
            isGranted = contactsGranted
        ))

        val callGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            permission = Manifest.permission.CALL_PHONE,
            name = "Phone Calls",
            description = "Make phone calls directly from the launcher",
            isGranted = callGranted
        ))

        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            permission = Manifest.permission.SEND_SMS,
            name = "SMS",
            description = "Send text messages directly from the launcher",
            isGranted = smsGranted
        ))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                permission = Manifest.permission.READ_MEDIA_IMAGES,
                name = "Storage (Images)",
                description = "Access images to set custom wallpapers",
                isGranted = storageGranted
            ))
        } else {
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                name = "Storage",
                description = "Access storage to set custom wallpapers",
                isGranted = storageGranted
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                name = "Notifications",
                description = "Show notifications in the notifications widget",
                isGranted = notificationGranted
            ))
        }

        val recordAudioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            permission = Manifest.permission.RECORD_AUDIO,
            name = "Microphone",
            description = "Record audio for voice search functionality",
            isGranted = recordAudioGranted
        ))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                permission = Manifest.permission.ACTIVITY_RECOGNITION,
                name = "Physical Activity",
                description = "Track your steps and distance walked",
                isGranted = activityRecognitionGranted
            ))
        }

        val usageStatsGranted = hasUsageStatsPermission()
        allPermissions.add(PermissionItem(
            permission = null,
            name = "Usage Stats",
            description = "Show app usage time and statistics",
            isGranted = usageStatsGranted,
            isSpecial = true
        ))

        val overlayGranted = Settings.canDrawOverlays(this)
        allPermissions.add(PermissionItem(
            permission = null,
            name = "Display Over Other Apps",
            description = "Required for Screen Dimmer feature",
            isGranted = overlayGranted,
            isOverlay = true
        ))

        val isDefaultLauncher = isDefaultLauncher()
        allPermissions.add(PermissionItem(
            permission = null,
            name = "Default Launcher",
            description = "Set this app as your default home launcher",
            isGranted = isDefaultLauncher,
            isSpecial = false,
            isLauncher = true
        ))

        for (perm in allPermissions) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_permission, permissionsList, false)
            val nameText = itemView.findViewById<TextView>(R.id.permission_name)
            val descText = itemView.findViewById<TextView>(R.id.permission_description)
            val toggle = itemView.findViewById<SwitchCompat>(R.id.permission_switch)

            nameText.text = perm.name
            descText.text = perm.description
            toggle.isChecked = perm.isGranted
            toggle.isEnabled = true

            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !perm.isGranted) {
                    if (perm.isSpecial) {
                        requestUsageStatsPermission()
                    } else if (perm.isLauncher) {
                        openDefaultLauncherSettings()
                    } else if (perm.isOverlay) {
                        requestOverlayPermission()
                    } else if (perm.permission != null) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(perm.permission),
                            PERMISSION_REQUEST_CODE
                        )
                    }
                } else if (!isChecked && perm.isGranted) {
                    if (perm.isLauncher) {
                        openDefaultLauncherSettings()
                    } else {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                            Toast.makeText(this, "Navigate to Permissions and disable ${perm.name}", Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {
                            Toast.makeText(this, "Cannot revoke permissions. Please disable in system settings.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    toggle.postDelayed({
                        toggle.isChecked = true
                    }, 500)
                }
            }

            if (perm.permission != null) {
                permissionToggles[perm.permission] = toggle
            } else if (perm.isSpecial) {
                permissionToggles["USAGE_STATS"] = toggle
            } else if (perm.isLauncher) {
                permissionToggles["DEFAULT_LAUNCHER"] = toggle
            } else if (perm.isOverlay) {
                permissionToggles["OVERLAY"] = toggle
            }

            permissionsList.addView(itemView)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        try {
            hasRequestedUsageStats = true
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open usage stats settings", Toast.LENGTH_SHORT).show()
            hasRequestedUsageStats = false
        }
    }

    private fun requestOverlayPermission() {
        try {
            hasRequestedOverlay = true
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open overlay settings", Toast.LENGTH_SHORT).show()
            hasRequestedOverlay = false
        }
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
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open launcher settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        
        if (hasRequestedUsageStats) {
            hasRequestedUsageStats = false
            if (hasUsageStatsPermission()) {
                Toast.makeText(this, "Usage Stats permission granted", Toast.LENGTH_SHORT).show()
            } else {
                if (!prefs.getBoolean("usage_stats_permission_denied", false)) {
                    AlertDialog.Builder(this, R.style.CustomDialogTheme)
                        .setTitle("Usage Stats Permission")
                        .setMessage("Usage Stats permission is required to show app usage time. Would you like to try again?")
                        .setPositiveButton("Try Again") { _, _ ->
                            requestUsageStatsPermission()
                        }
                        .setNegativeButton("Skip") { _, _ ->
                            prefs.edit { putBoolean("usage_stats_permission_denied", true) }
                        }
                        .show()
                }
            }
        }

        if (hasRequestedOverlay) {
            hasRequestedOverlay = false
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            }
        }
        
        setupPermissionsList()
        setupWallpaper()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                val permission = permissions[i]
                val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                
                if (granted) {
                    val permissionName = when (permission) {
                        Manifest.permission.READ_CONTACTS -> "Contacts"
                        Manifest.permission.CALL_PHONE -> "Phone Calls"
                        Manifest.permission.SEND_SMS -> "SMS"
                        Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                        Manifest.permission.READ_MEDIA_IMAGES -> "Storage (Images)"
                        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                        Manifest.permission.RECORD_AUDIO -> "Microphone"
                        Manifest.permission.ACTIVITY_RECOGNITION -> "Physical Activity"
                        else -> "Permission"
                    }
                    Toast.makeText(this, "$permissionName granted", Toast.LENGTH_SHORT).show()
                }
            }
            setupPermissionsList()
        }
    }
}
