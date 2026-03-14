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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.FocusModeManager
import com.guruswarupa.launch.utils.BlurUtils
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
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
        
        // Enable edge-to-edge for transparent system bars with white icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        
        setContentView(R.layout.activity_focus_mode_config)

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
        WallpaperDisplayHelper.applySystemWallpaper(wallpaperBackground, fallbackRes = R.drawable.wallpaper_overlay)
        
        themeOverlay.setBackgroundColor(Color.parseColor("#66000000"))
        
        appsContainer.setBackgroundResource(R.drawable.widget_background)
        
        val textColor = Color.WHITE
        val subTextColor = Color.parseColor("#B0B0B0")
        
        titleText.setTextColor(textColor)
        subtitleText.setTextColor(subTextColor)
        saveButton.setTextColor(textColor)
        cancelButton.setTextColor(textColor)
        
        saveButton.setBackgroundResource(R.drawable.settings_card_background)
        cancelButton.setBackgroundResource(R.drawable.settings_card_background)
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

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
    }
}
