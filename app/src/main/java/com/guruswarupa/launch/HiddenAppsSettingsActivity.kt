package com.guruswarupa.launch

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HiddenAppsSettingsActivity : ComponentActivity() {

    private lateinit var hiddenAppManager: HiddenAppManager
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var emptyStateLayout: LinearLayout
    private var hiddenAppsList = mutableListOf<ResolveInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make status bar and navigation bar transparent BEFORE setContentView
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        
        setContentView(R.layout.activity_hidden_apps_settings)
        
        setupTheme()

        val prefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
        hiddenAppManager = HiddenAppManager(prefs)

        // Initialize views
        appsRecyclerView = findViewById(R.id.hidden_apps_recycler_view)
        emptyStateText = findViewById(R.id.empty_state_text)
        emptyStateLayout = findViewById(R.id.empty_state_layout)

        // Setup RecyclerView
        appsRecyclerView.layoutManager = LinearLayoutManager(this)

        recreateAppsList()
    }
    
    private fun setupTheme() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val overlay = findViewById<View>(R.id.settings_overlay)
        
        if (isDarkMode) {
            overlay.setBackgroundColor(Color.parseColor("#CC000000"))
        } else {
            overlay.setBackgroundColor(Color.parseColor("#66FFFFFF"))
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
            } catch (e: Exception) {
                wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
            }
        } else {
            wallpaperImageView.setImageResource(R.drawable.wallpaper_background)
        }
    }
    
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
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
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
                        
                        if (!isDarkMode) {
                            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            }
                        }
                        decorView.systemUiVisibility = flags
                    }
                }
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = Color.TRANSPARENT
                    window.navigationBarColor = Color.TRANSPARENT
                }
            } catch (ex: Exception) {
            }
        }
    }

    private fun recreateAppsList() {
        val packageManager = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val allApps = packageManager.queryIntentActivities(mainIntent, 0)
        val hiddenPackageNames = hiddenAppManager.getHiddenApps()
        
        hiddenAppsList = allApps.filter { 
            it.activityInfo.packageName != "com.guruswarupa.launch" &&
            hiddenPackageNames.contains(it.activityInfo.packageName)
        }.sortedBy { 
            it.loadLabel(packageManager).toString().lowercase() 
        }.toMutableList()

        if (hiddenAppsList.isEmpty()) {
            appsRecyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            emptyStateText.text = "No hidden apps. Long press an app in the app list to hide it."
        } else {
            appsRecyclerView.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            
            val adapter = HiddenAppsAdapter(hiddenAppsList) { packageName ->
                hiddenAppManager.unhideApp(packageName)
                Toast.makeText(this, "App unhidden", Toast.LENGTH_SHORT).show()
                recreateAppsList()
                // Set result to indicate an app was unhidden
                setResult(RESULT_OK)
            }
            appsRecyclerView.adapter = adapter
        }
    }

    private class HiddenAppsAdapter(
        private val apps: List<ResolveInfo>,
        private val onUnhide: (String) -> Unit
    ) : RecyclerView.Adapter<HiddenAppsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val unhideButton: Button = view.findViewById(R.id.unhide_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hidden_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val packageName = app.activityInfo.packageName
            val context = holder.itemView.context
            val packageManager = context.packageManager

            try {
                holder.icon.setImageDrawable(app.loadIcon(packageManager))
                holder.name.text = app.loadLabel(packageManager)
            } catch (e: Exception) {
                holder.icon.setImageResource(R.drawable.ic_default_app_icon)
                holder.name.text = packageName
            }

            holder.unhideButton.setOnClickListener {
                onUnhide(packageName)
            }
        }

        override fun getItemCount() = apps.size
    }
}
