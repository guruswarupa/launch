package com.guruswarupa.launch

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

        // Setup search box
        searchBox.setOnClickListener {
            // Single tap - focus search box
        }

        searchBox.setOnLongClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://www.google.com".toUri())
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

        dateTextView.setOnClickListener {
            appLauncher.launchAppWithLockCheck("com.google.android.calendar", "Google Calendar")
        }
    }

    fun setupDrawerLayout(drawerLayout: DrawerLayout) {
        // Set drawer to full width - use post to ensure view is laid out
        drawerLayout.post {
            val displayMetrics = activity.resources.displayMetrics
            val drawerWidth = displayMetrics.widthPixels
            val drawerView = activity.findViewById<FrameLayout>(R.id.widgets_drawer)
            drawerView?.let {
                val params = it.layoutParams as DrawerLayout.LayoutParams
                params.width = drawerWidth
                it.layoutParams = params
            }
        }

        // Enable edge swipe for DrawerLayout (this is the default, but making it explicit)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, androidx.core.view.GravityCompat.START)
    }

    fun setupAddWidgetButton(addWidgetButton: Button, widgetManager: WidgetManager, requestCode: Int) {
        addWidgetButton.setOnClickListener {
            widgetManager.requestPickWidget(activity, requestCode)
        }
    }

    fun setupVoiceSearchButton(voiceSearchButton: ImageButton, voiceSearchManager: VoiceSearchManager) {
        voiceSearchButton.setOnClickListener {
            android.widget.Toast.makeText(activity, "Voice search button clicked", android.widget.Toast.LENGTH_SHORT).show()
            voiceSearchManager.startVoiceSearch()
        }
    }
}
