package com.guruswarupa.launch

import android.Manifest
import android.app.WallpaperManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.Executors

class PrivacyDashboardActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PrivacyDashboardAdapter
    private lateinit var searchBox: EditText
    
    // Summary views
    private lateinit var statTotalValue: TextView
    private lateinit var statCriticalValue: TextView
    private lateinit var statSideloadedValue: TextView
    private lateinit var chipGroup: ChipGroup

    private var allApps = listOf<AppPrivacyInfo>()
    private val executor = Executors.newSingleThreadExecutor()

    private val sensitivePermissions = listOfNotNull(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_SMS,
        Manifest.permission.CALL_PHONE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else null,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setContentView(R.layout.activity_privacy_dashboard)
        
        setupTheme()

        findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            finish()
        }

        recyclerView = findViewById(R.id.privacy_apps_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PrivacyDashboardAdapter(emptyList())
        recyclerView.adapter = adapter

        searchBox = findViewById(R.id.search_box)
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        statTotalValue = findViewById(R.id.stat_total_value)
        statCriticalValue = findViewById(R.id.stat_critical_value)
        statSideloadedValue = findViewById(R.id.stat_sideloaded_value)
        chipGroup = findViewById(R.id.filter_chip_group)
        
        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            applyFilters()
        }

        loadApps()
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
    
    private fun makeSystemBarsTransparent(isDarkMode: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
                
                WindowCompat.setDecorFitsSystemWindows(window, false)
                
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
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                
                @Suppress("DEPRECATION")
                val decorView = window.decorView
                @Suppress("DEPRECATION")
                var flags = decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                
                if (!isDarkMode) {
                    @Suppress("DEPRECATION")
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        @Suppress("DEPRECATION")
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = flags
            }
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.TRANSPARENT
            } catch (_: Exception) {
            }
        }
    }

    private fun loadApps() {
        executor.execute {
            val pm = packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            
            val appPrivacyList = packages.mapNotNull { packageInfo ->
                val granted = mutableListOf<String>()
                packageInfo.requestedPermissions?.forEachIndexed { index, permission ->
                    if (permission in sensitivePermissions) {
                        val flag = packageInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
                        if ((flag and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            granted.add(permission)
                        }
                    }
                }
                
                val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                
                if (granted.isNotEmpty() || (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    val appName = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {
                        packageInfo.packageName
                    }
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        ResourcesCompat.getDrawable(resources, R.mipmap.ic_launcher, theme)
                    } ?: ResourcesCompat.getDrawable(resources, R.mipmap.ic_launcher, theme)!!
                    
                    val severity = when {
                        granted.any { it in listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_SMS, Manifest.permission.ACCESS_FINE_LOCATION) } -> 2
                        granted.isNotEmpty() -> 1
                        else -> 0
                    }

                    // Detect sideloaded apps
                    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            pm.getInstallSourceInfo(packageInfo.packageName).installingPackageName
                        } catch (_: Exception) { null }
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageInfo.packageName)
                    }
                    
                    val isSideloaded = installer == null || 
                        installer.isEmpty() || 
                        installer in listOf("com.android.packageinstaller", "com.google.android.packageinstaller")

                    AppPrivacyInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        icon = icon,
                        grantedPermissions = granted,
                        severity = severity,
                        isSideloaded = isSideloaded
                    )
                } else {
                    null
                }
            }.sortedWith(compareByDescending<AppPrivacyInfo> { it.severity }
                .thenByDescending { it.grantedPermissions.size }
                .thenBy { it.appName.lowercase() })

            val totalApps = appPrivacyList.size
            val criticalApps = appPrivacyList.count { it.severity == 2 }
            val sideloadedApps = appPrivacyList.count { it.isSideloaded }

            runOnUiThread {
                allApps = appPrivacyList
                statTotalValue.text = totalApps.toString()
                statCriticalValue.text = criticalApps.toString()
                statSideloadedValue.text = sideloadedApps.toString()
                adapter.updateData(allApps)
            }
        }
    }

    private fun applyFilters() {
        val query = searchBox.text.toString().trim()
        val checkedChipId = chipGroup.checkedChipId
        
        val filtered = allApps.filter { app ->
            val matchesQuery = query.isEmpty() || 
                app.appName.contains(query, ignoreCase = true) || 
                app.packageName.contains(query, ignoreCase = true)
            
            val matchesChip = when (checkedChipId) {
                R.id.chip_camera -> app.grantedPermissions.contains(Manifest.permission.CAMERA)
                R.id.chip_microphone -> app.grantedPermissions.contains(Manifest.permission.RECORD_AUDIO)
                R.id.chip_location -> app.grantedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) || 
                                     app.grantedPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
                R.id.chip_sideloaded -> app.isSideloaded
                else -> true // chip_all or no selection
            }
            
            matchesQuery && matchesChip
        }
        adapter.updateData(filtered)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
