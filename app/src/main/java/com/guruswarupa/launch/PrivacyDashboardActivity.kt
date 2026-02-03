package com.guruswarupa.launch

import android.Manifest
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
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
    private lateinit var chipGroup: com.google.android.material.chip.ChipGroup

    private var allApps = listOf<AppPrivacyInfo>()
    private val executor = Executors.newSingleThreadExecutor()

    private val sensitivePermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_SMS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.POST_NOTIFICATIONS,
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        setContentView(R.layout.activity_privacy_dashboard)

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
        
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            applyFilters()
        }

        loadApps()
    }

    private fun loadApps() {
        executor.execute {
            val pm = packageManager
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
                    } catch (e: Exception) {
                        packageInfo.packageName ?: "Unknown"
                    }
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        resources.getDrawable(R.mipmap.ic_launcher, theme)
                    }
                    
                    val severity = when {
                        granted.any { it in listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_SMS, Manifest.permission.ACCESS_FINE_LOCATION) } -> 2
                        granted.isNotEmpty() -> 1
                        else -> 0
                    }

                    // Detect sideloaded apps
                    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            pm.getInstallSourceInfo(packageInfo.packageName).installingPackageName
                        } catch (e: Exception) { null }
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageInfo.packageName)
                    }
                    
                    val isSideloaded = installer == null || 
                        installer.isEmpty() || 
                        installer in listOf("com.android.packageinstaller", "com.google.android.packageinstaller")

                    AppPrivacyInfo(
                        packageName = packageInfo.packageName ?: "Unknown",
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
