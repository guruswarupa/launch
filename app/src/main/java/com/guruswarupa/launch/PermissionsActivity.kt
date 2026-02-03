package com.guruswarupa.launch

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class PermissionsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private var hasRequestedUsageStats = false
    private lateinit var permissionsList: LinearLayout
    private val permissionToggles = mutableMapOf<String, SwitchCompat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        
        setContentView(R.layout.activity_permissions)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        permissionsList = findViewById(R.id.permissions_list)
        findViewById<Button>(R.id.done_button).setOnClickListener {
            finish()
        }

        setupPermissionsList()
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
            val isLauncher: Boolean = false
        )

        val allPermissions = mutableListOf<PermissionItem>()

        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.READ_CONTACTS,
            "Contacts",
            "Access your contacts to search and call them",
            contactsGranted
        ))

        val callGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.CALL_PHONE,
            "Phone Calls",
            "Make phone calls directly from the launcher",
            callGranted
        ))

        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.SEND_SMS,
            "SMS",
            "Send text messages directly from the launcher",
            smsGranted
        ))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.READ_MEDIA_IMAGES,
                "Storage (Images)",
                "Access images to set custom wallpapers",
                storageGranted
            ))
        } else {
            val storageGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "Storage",
                "Access storage to set custom wallpapers",
                storageGranted
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.POST_NOTIFICATIONS,
                "Notifications",
                "Show notifications in the notifications widget",
                notificationGranted
            ))
        }

        val recordAudioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        allPermissions.add(PermissionItem(
            Manifest.permission.RECORD_AUDIO,
            "Microphone",
            "Record audio for voice search functionality",
            recordAudioGranted
        ))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activityRecognitionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            allPermissions.add(PermissionItem(
                Manifest.permission.ACTIVITY_RECOGNITION,
                "Physical Activity",
                "Track your steps and distance walked",
                activityRecognitionGranted
            ))
        }

        val usageStatsGranted = hasUsageStatsPermission()
        allPermissions.add(PermissionItem(
            null,
            "Usage Stats",
            "Show app usage time and statistics",
            usageStatsGranted,
            true
        ))

        val isDefaultLauncher = isDefaultLauncher()
        allPermissions.add(PermissionItem(
            null,
            "Default Launcher",
            "Set this app as your default home launcher",
            isDefaultLauncher,
            false,
            true
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
                        } catch (e: Exception) {
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
            }

            permissionsList.addView(itemView)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }
    }

    private fun requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                hasRequestedUsageStats = true
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open usage stats settings", Toast.LENGTH_SHORT).show()
                hasRequestedUsageStats = false
            }
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
        } catch (e: Exception) {
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
                            prefs.edit().putBoolean("usage_stats_permission_denied", true).apply()
                        }
                        .show()
                }
            }
        }
        
        setupPermissionsList()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                val permission = permissions[i]
                val granted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                
                val permissionName = when (permission) {
                    Manifest.permission.READ_CONTACTS -> "Contacts"
                    Manifest.permission.CALL_PHONE -> "Phone Calls"
                    Manifest.permission.SEND_SMS -> "SMS"
                    Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
                    Manifest.permission.READ_MEDIA_IMAGES -> "Storage (Images)"
                    Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
                    Manifest.permission.RECORD_AUDIO -> "Microphone"
                    Manifest.permission.ACTIVITY_RECOGNITION -> "Physical Activity"
                    else -> null
                }
                
                if (granted) {
                    setupPermissionsList()
                    if (permissionName != null) {
                        Toast.makeText(this, "$permissionName permission granted", Toast.LENGTH_SHORT).show()
                        if (permission == Manifest.permission.ACTIVITY_RECOGNITION) {
                            val intent = Intent("com.guruswarupa.launch.ACTIVITY_RECOGNITION_PERMISSION_GRANTED")
                            sendBroadcast(intent)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1000
    }
}
