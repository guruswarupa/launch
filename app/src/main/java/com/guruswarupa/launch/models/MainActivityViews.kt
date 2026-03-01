package com.guruswarupa.launch.models

import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.ui.views.FastScroller
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView

/**
 * Holder for all MainActivity view references.
 */
class MainActivityViews {
    lateinit var recyclerView: RecyclerView
    lateinit var fastScroller: FastScroller
    lateinit var timeTextView: TextView
    lateinit var dateTextView: TextView
    lateinit var searchBox: AutoCompleteTextView
    lateinit var searchContainer: LinearLayout
    lateinit var searchTypeButton: ImageButton
    lateinit var appDock: LinearLayout
    lateinit var wallpaperBackground: ImageView
    lateinit var weeklyUsageGraph: WeeklyUsageGraphView
    lateinit var weatherIcon: ImageView
    lateinit var weatherText: TextView
    lateinit var todoRecyclerView: RecyclerView
    lateinit var addTodoButton: ImageButton
    lateinit var voiceSearchButton: ImageButton
    lateinit var topWidgetContainer: LinearLayout
    lateinit var rightDrawerWallpaper: ImageView
    lateinit var rightDrawerTime: TextView
    lateinit var rightDrawerDate: TextView
    lateinit var drawerLayout: DrawerLayout

    fun isSearchBoxInitialized() = ::searchBox.isInitialized
    fun isSearchContainerInitialized() = ::searchContainer.isInitialized
    fun isSearchTypeButtonInitialized() = ::searchTypeButton.isInitialized
    fun isVoiceSearchButtonInitialized() = ::voiceSearchButton.isInitialized
    fun isRightDrawerWallpaperInitialized() = ::rightDrawerWallpaper.isInitialized
    fun isRecyclerViewInitialized() = ::recyclerView.isInitialized
}
