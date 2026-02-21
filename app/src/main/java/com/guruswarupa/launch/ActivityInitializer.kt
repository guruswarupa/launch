package com.guruswarupa.launch

import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Handles MainActivity initialization logic.
 * Extracted from MainActivity to reduce complexity.
 */
class ActivityInitializer(
    private val activity: FragmentActivity,
    private val sharedPreferences: android.content.SharedPreferences,
    private val appLauncher: AppLauncher
) {
    fun initializeViews(
        searchBox: EditText,
        recyclerView: RecyclerView,
        timeTextView: TextView,
        dateTextView: TextView
    ) {
        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"

        // Setup search box - removed click listener to prevent unwanted focus
        // Search box will naturally gain focus when tapped by user

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

        // Setup layout manager
        if (isGridMode) {
            recyclerView.layoutManager = GridLayoutManager(activity, 4)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
        }

        // Setup time/date click listeners
        timeTextView.setOnClickListener {
            appLauncher.launchAppWithLockCheck("com.google.android.deskclock", "Google Clock")
        }

        // Setup date widget click listener to open calendar
        dateTextView.setOnClickListener {
            appLauncher.launchAppWithLockCheck("com.google.android.calendar", "Google Calendar")
        }
    }

    fun setupDrawerLayout(drawerLayout: DrawerLayout) {
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

    fun setupVoiceSearchButton(voiceSearchButton: ImageButton, voiceSearchManager: VoiceSearchManager) {
        voiceSearchButton.setOnClickListener {
            voiceSearchManager.startVoiceSearch()
        }

        voiceSearchButton.setOnLongClickListener {
            voiceSearchManager.triggerSystemAssistant()
            true
        }
    }
}
