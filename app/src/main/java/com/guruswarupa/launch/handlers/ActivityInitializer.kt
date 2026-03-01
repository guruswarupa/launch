package com.guruswarupa.launch.handlers

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppLauncher
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.ui.views.FastScroller
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView

/**
 * Handles MainActivity initialization logic.
 * Extracted from MainActivity to reduce complexity.
 */
class ActivityInitializer(
    private val activity: FragmentActivity,
    private val sharedPreferences: android.content.SharedPreferences,
    private val appLauncher: AppLauncher
) {
    val views = MainActivityViews()

    fun initializeViews() {
        with(views) {
            searchBox = activity.findViewById(R.id.search_box)
            searchContainer = activity.findViewById(R.id.search_container)
            recyclerView = activity.findViewById(R.id.app_list)
            fastScroller = activity.findViewById(R.id.fast_scroller)
            fastScroller.setRecyclerView(recyclerView)
            
            // Disable animations to prevent "Tmp detached view" crash during rapid updates
            recyclerView.itemAnimator = null
            voiceSearchButton = activity.findViewById(R.id.voice_search_button)
            appDock = activity.findViewById(R.id.app_dock)
            wallpaperBackground = activity.findViewById(R.id.wallpaper_background)
            weatherIcon = activity.findViewById(R.id.weather_icon)
            weatherText = activity.findViewById(R.id.weather_text)
            timeTextView = activity.findViewById(R.id.time_widget)
            dateTextView = activity.findViewById(R.id.date_widget)
            topWidgetContainer = activity.findViewById(R.id.top_widget_container)
            
            // Todo components
            todoRecyclerView = activity.findViewById(R.id.todo_recycler_view)
            addTodoButton = activity.findViewById(R.id.add_todo_button)
            
            // Right Drawer Views
            rightDrawerWallpaper = activity.findViewById(R.id.right_drawer_wallpaper)
            rightDrawerTime = activity.findViewById(R.id.right_drawer_time)
            rightDrawerDate = activity.findViewById(R.id.right_drawer_date)
            weeklyUsageGraph = activity.findViewById(R.id.weekly_usage_graph)
            searchTypeButton = activity.findViewById(R.id.search_type_button)
            drawerLayout = activity.findViewById(R.id.drawer_layout)

            setupSearchBox(searchBox)
            setupLayoutManager(recyclerView)
            setupTimeDateListeners(timeTextView, dateTextView)
        }
    }

    private fun setupSearchBox(searchBox: AutoCompleteTextView) {
        searchBox.setOnLongClickListener {
            val prefs = searchBox.context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
            val engine = prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")
            val url = when (engine) {
                "Bing" -> "https://www.bing.com"
                "DuckDuckGo" -> "https://duckduckgo.com"
                "Ecosia" -> "https://www.ecosia.org"
                "Brave" -> "https://search.brave.com"
                "Startpage" -> "https://www.startpage.com"
                "Yahoo" -> "https://www.yahoo.com"
                "Qwant" -> "https://www.qwant.com"
                else -> "https://www.google.com"
            }
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                searchBox.context.startActivity(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(searchBox.context, "No browser found!", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun setupLayoutManager(recyclerView: RecyclerView) {
        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"

        if (isGridMode) {
            recyclerView.layoutManager = GridLayoutManager(activity, 4)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun setupTimeDateListeners(timeTextView: TextView, dateTextView: TextView) {
        timeTextView.setOnClickListener {
            appLauncher.launchAppWithLockCheck("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            appLauncher.launchAppWithLockCheck("com.google.android.calendar", "Google Calendar")
        }
    }

    fun setupDrawerLayout() {
        val drawerLayout = views.drawerLayout
        // Set drawer to full width - use post to ensure view is laid out
        drawerLayout.post {
            val displayMetrics = activity.resources.displayMetrics
            val drawerWidth = displayMetrics.widthPixels

            // Left drawer
            val leftDrawerView = activity.findViewById<FrameLayout>(R.id.widgets_drawer)
            leftDrawerView?.let {
                val params = it.layoutParams as DrawerLayout.LayoutParams
                params.width = drawerWidth
                it.layoutParams = params
            }

            // Right drawer
            val rightDrawerView = activity.findViewById<FrameLayout>(R.id.wallpaper_drawer)
            rightDrawerView?.let {
                val params = it.layoutParams as DrawerLayout.LayoutParams
                params.width = drawerWidth
                it.layoutParams = params
            }
        }

        // Enable edge swipe for DrawerLayout
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, androidx.core.view.GravityCompat.START)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, androidx.core.view.GravityCompat.END)
    }

    fun setupVoiceSearchButton(voiceSearchManager: VoiceSearchManager) {
        views.voiceSearchButton.setOnClickListener {
            voiceSearchManager.startVoiceSearch()
        }

        views.voiceSearchButton.setOnLongClickListener {
            voiceSearchManager.triggerSystemAssistant()
            true
        }
    }
}
