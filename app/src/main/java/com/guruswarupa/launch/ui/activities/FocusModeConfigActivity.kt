package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FocusModeManager
import com.guruswarupa.launch.utils.BlurUtils
import com.guruswarupa.launch.ui.adapters.FocusModeAppAdapter
import java.util.concurrent.Executors

class FocusModeConfigActivity : ComponentActivity() {

    private lateinit var focusModeManager: FocusModeManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FocusModeAppAdapter
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var wallpaperBackground: ImageView
    private lateinit var themeOverlay: View
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var appsContainer: LinearLayout
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_focus_mode_config)

        // Make system bars transparent
        window.decorView.post {
            makeSystemBarsTransparent()
        }

        focusModeManager = FocusModeManager(this, getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE))

        // Initialize views
        recyclerView = findViewById(R.id.focus_mode_app_list)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        themeOverlay = findViewById(R.id.theme_overlay)
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        appsContainer = findViewById(R.id.apps_container)
        saveButton = findViewById(R.id.save_focus_config)
        cancelButton = findViewById(R.id.cancel_focus_config)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = null

        // Apply theme and wallpaper
        applyThemeAndWallpaper()

        loadApps()

        adapter = FocusModeAppAdapter(appList, focusModeManager)
        recyclerView.adapter = adapter

        saveButton.setOnClickListener {
            // Bulk save the selected apps
            focusModeManager.updateAllowedApps(adapter.getSelectedApps())
            
            Toast.makeText(this, "Focus mode configuration saved", Toast.LENGTH_SHORT).show()
            
            // Notify other components that settings updated
            val intent = Intent("com.guruswarupa.launch.SETTINGS_UPDATED")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }
    }

    private fun applyThemeAndWallpaper() {
        // Set system wallpaper
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(this)
            val drawable = wallpaperManager.drawable
            wallpaperBackground.setImageDrawable(drawable)
        } catch (_: Exception) {
            wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
        }
        
        // Apply theme-based colors and backgrounds
        val isNightMode = (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
            
        val overlayColor = if (isNightMode) {
            Color.parseColor("#90000000")
        } else {
            Color.parseColor("#40000000")
        }
        themeOverlay.setBackgroundColor(overlayColor)
        
        val widgetBg = if (isNightMode) R.drawable.widget_background_dark else R.drawable.widget_background
        appsContainer.setBackgroundResource(widgetBg)
        
        val textColor = ContextCompat.getColor(this, if (isNightMode) R.color.white else R.color.black)
        val subTextColor = ContextCompat.getColor(this, if (isNightMode) R.color.gray_light else R.color.gray)
        
        titleText.setTextColor(textColor)
        subtitleText.setTextColor(subTextColor)
        saveButton.setTextColor(textColor)
        cancelButton.setTextColor(textColor)
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        appList = apps.toMutableList()
    }

    private fun makeSystemBarsTransparent() {
        try {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.decorView.windowInsetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                
                @Suppress("DEPRECATION")
                val decorView = window.decorView
                @Suppress("DEPRECATION")
                var flags = decorView.systemUiVisibility
                @Suppress("DEPRECATION")
                flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = flags
            }
            
            // Apply blur effect to status bar
            BlurUtils.applyBlurToStatusBar(this)
        } catch (_: Exception) {
            // Apply blur effect as fallback
            BlurUtils.applyBlurToStatusBar(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}
