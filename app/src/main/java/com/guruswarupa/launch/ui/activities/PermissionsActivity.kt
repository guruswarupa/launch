package com.guruswarupa.launch.ui.activities

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.WallpaperDisplayHelper

class PermissionsActivity : ComponentActivity() {

    private val prefs by lazy { getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE) }
    private lateinit var permissionsList: LinearLayout

    companion object {
        private const val REQ_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)
        applyContentInsets()
        window.decorView.post { makeSystemBarsTransparent() }

        permissionsList = findViewById(R.id.permissions_list)
        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))
        
        findViewById<Button>(R.id.done_button).setOnClickListener { finish() }
        setupPermissionsList()
    }

    private fun setupPermissionsList() {
        permissionsList.removeAllViews()
        val items = getPermissionItems()

        for (perm in items) {
            val view = LayoutInflater.from(this).inflate(R.layout.item_permission, permissionsList, false)
            view.findViewById<TextView>(R.id.permission_name).text = perm.name
            view.findViewById<TextView>(R.id.permission_description).text = perm.description
            val sw = view.findViewById<SwitchCompat>(R.id.permission_switch)
            sw.isChecked = perm.granted
            sw.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !perm.granted) handlePermissionGrant(perm)
                else if (!isChecked && perm.granted) {
                    Toast.makeText(this, "Revoke in system settings", Toast.LENGTH_SHORT).show()
                    sw.postDelayed({ sw.isChecked = true }, 500)
                }
            }
            permissionsList.addView(view)
        }
    }

    private fun getPermissionItems(): List<PermItem> {
        val list = mutableListOf<PermItem>()
        list.add(PermItem("Launcher", "Set as default home", isDefaultLauncher(), type = "DEFAULT"))
        list.add(PermItem("Contacts", "Search & call contacts", check(Manifest.permission.READ_CONTACTS), Manifest.permission.READ_CONTACTS))
        
        val storage = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        list.add(PermItem("Storage", "Access wallpapers", check(storage), storage))
        
        if (Build.VERSION.SDK_INT >= 33) list.add(PermItem("Notifications", "Show widget alerts", check(Manifest.permission.POST_NOTIFICATIONS), Manifest.permission.POST_NOTIFICATIONS))
        list.add(PermItem("Microphone", "Voice search access", check(Manifest.permission.RECORD_AUDIO), Manifest.permission.RECORD_AUDIO))
        list.add(PermItem("Usage Stats", "App time tracking", hasUsageStats(), type = "USAGE"))
        list.add(PermItem("Overlay", "Screen dimming tools", Settings.canDrawOverlays(this), type = "OVERLAY"))
        list.add(PermItem("Accessibility", "Double tap lock", hasAccessibility(), type = "ACCESSIBILITY"))
        return list
    }

    private fun handlePermissionGrant(perm: PermItem) {
        when(perm.type) {
            "DEFAULT" -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            "USAGE" -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "OVERLAY" -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
            "ACCESSIBILITY" -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            else -> if (perm.perm != null) ActivityCompat.requestPermissions(this, arrayOf(perm.perm), REQ_CODE)
        }
    }

    private fun check(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun hasUsageStats(): Boolean {
        val mode = (getSystemService(APP_OPS_SERVICE) as AppOpsManager).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    private fun hasAccessibility(): Boolean {
        val services = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return services.contains(packageName)
    }
    private fun isDefaultLauncher(): Boolean {
        val resolve = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == packageName
    }

    private fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.main_layout).setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun makeSystemBarsTransparent() {
        if (Build.VERSION.SDK_INT >= 23) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    override fun onResume() { super.onResume(); setupPermissionsList() }

    data class PermItem(val name: String, val description: String, val granted: Boolean, val perm: String? = null, val type: String = "NORMAL")
}
