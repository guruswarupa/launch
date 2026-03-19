package com.guruswarupa.launch.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.animation.ValueAnimator
import android.view.View
import android.view.MotionEvent
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.Gravity
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.transition.ChangeBounds
import android.transition.Fade
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.AppLauncher
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.ui.views.FastScroller
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView
import java.util.Locale

/**
 * Handles MainActivity initialization logic.
 * Extracted from MainActivity to reduce complexity.
 */
class ActivityInitializer(
    private val activity: FragmentActivity,
    private val sharedPreferences: android.content.SharedPreferences,
    private val appLauncher: AppLauncher
) {
    companion object {
        private const val CATEGORY_APP_CLOCK = "android.intent.category.APP_CLOCK"
        private const val CATEGORY_APP_CALENDAR = "android.intent.category.APP_CALENDAR"
    }

    val views = MainActivityViews()
    private lateinit var searchMarginParams: MarginLayoutParams
    private var defaultSearchTopMargin = 0
    private var pinnedSearchTopMargin = 0
    private var headerHidden = false

    fun initializeViews() {
        with(views) {
            searchBox = activity.findViewById(R.id.search_box)
            searchContainer = activity.findViewById(R.id.search_container)
            searchMarginParams = searchContainer.layoutParams as MarginLayoutParams
            defaultSearchTopMargin = searchMarginParams.topMargin
            pinnedSearchTopMargin = activity.resources.getDimensionPixelSize(R.dimen.search_bar_top_margin_pinned)
            recyclerView = activity.findViewById(R.id.app_list)
            fastScroller = activity.findViewById(R.id.fast_scroller)
            fastScroller.setRecyclerView(recyclerView)
            
            // Position FastScroller to avoid search bar/mic and start from below the app dock area
            val displayMetrics = activity.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val scrollerParams = fastScroller.layoutParams as FrameLayout.LayoutParams
            scrollerParams.height = (screenHeight * 0.65).toInt()
            scrollerParams.gravity = Gravity.BOTTOM or Gravity.END
            fastScroller.layoutParams = scrollerParams

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
            backgroundTranslucencyOverlay = activity.findViewById(R.id.background_translucency_overlay)
            widgetsDrawerTranslucencyOverlay = activity.findViewById(R.id.widgets_drawer_translucency_overlay)

            setupSearchBox(searchBox)
            setupLayoutManager(recyclerView)
            setupHeaderVisibilityOnScroll(recyclerView)
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
            val columns = (activity as? MainActivity)?.getPreferredGridColumns()
                ?: activity.resources.getInteger(R.integer.app_grid_columns)
            recyclerView.layoutManager = GridLayoutManager(activity, columns)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
        }
    }

    private fun setupHeaderVisibilityOnScroll(recyclerView: RecyclerView) {
        val hideThreshold = (activity.resources.displayMetrics.density * 40).toInt() 
        val showThreshold = (activity.resources.displayMetrics.density * 10).toInt()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val searchBox = activity.findViewById<android.widget.AutoCompleteTextView>(R.id.search_box)
                val isSearching = !searchBox?.text.toString().trim().isNullOrEmpty()
                
                if (!isSearching) {
                    val offset = rv.computeVerticalScrollOffset()
                    val scrollRange = rv.computeVerticalScrollRange()
                    val viewportHeight = rv.height
                    
                    // Safety check: if we are at the top or nearly at the top, always show header
                    if (offset <= showThreshold && headerHidden) {
                        setHeaderVisibility(true)
                        return
                    }

                    // If the header is hidden, we check if the content still needs scrolling.
                    // If it fits entirely within the viewport, show the header again to prevent being "stuck".
                    if (headerHidden && scrollRange <= viewportHeight) {
                        setHeaderVisibility(true)
                        return
                    }

                    // Directional hiding logic: only hide if scrolling down AND we have enough content to warrant it
                    if (dy > 0 && !headerHidden && offset > hideThreshold) {
                        // Only hide if the list is substantially longer than the screen to avoid glitching on short lists
                        if (scrollRange > viewportHeight * 1.5) {
                            setHeaderVisibility(false)
                        }
                    } else if (dy < 0 && headerHidden && offset <= showThreshold) {
                        setHeaderVisibility(true)
                    }
                }
            }
        })

        // Robust touch listener to catch "swipe down" when already at the top or stuck
        recyclerView.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startY = event.y
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.y - startY
                        // If user swipes down (deltaY > threshold) and header is hidden
                        if (deltaY > 50 && headerHidden) {
                            val offset = recyclerView.computeVerticalScrollOffset()
                            // If we're at or near the top but can't scroll up further to trigger dy < 0
                            if (offset <= showThreshold) {
                                setHeaderVisibility(true)
                            }
                        }
                    }
                }
                return false // Don't consume the touch, let RecyclerView handle scrolling
            }
        })

        // Ensure header comes back if the list size changes (e.g. workspace switch)
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (headerHidden) {
                val scrollRange = recyclerView.computeVerticalScrollRange()
                val viewportHeight = recyclerView.height
                if (scrollRange <= viewportHeight) {
                    recyclerView.post {
                        if (headerHidden) setHeaderVisibility(true)
                    }
                }
            }
        }
    }

    /**
     * Animate header and dock visibility; called from scroll or search events.
     * Uses TransitionManager for smooth layout reflow without glitches.
     */
    fun setHeaderVisibility(visible: Boolean) {
        if (visible && !headerHidden) return
        if (!visible && headerHidden) return
        headerHidden = !visible
        
        val stack = views.topWidgetContainer.parent as? ViewGroup ?: return
        
        // Use TransitionManager to handle visibility and bounds changes smoothly.
        val transition = TransitionSet().apply {
            addTransition(Fade().apply {
                addTarget(views.topWidgetContainer)
                addTarget(views.appDock.parent as View)
            })
            addTransition(ChangeBounds())
            duration = 250
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        
        TransitionManager.beginDelayedTransition(stack, transition)

        views.topWidgetContainer.isVisible = visible
        // Dock is wrapped in a HorizontalScrollView, hide the wrapper to ensure correct layout reflow
        (views.appDock.parent as? View)?.isVisible = visible
        
        // Adjust search container margin to pin it to top or restore default spacing
        val targetMargin = if (visible) defaultSearchTopMargin else pinnedSearchTopMargin
        val params = views.searchContainer.layoutParams as MarginLayoutParams
        params.topMargin = targetMargin
        views.searchContainer.layoutParams = params
    }

    private fun setupTimeDateListeners(timeTextView: TextView, dateTextView: TextView) {
        timeTextView.setOnClickListener {
            val launched = launchResolvedAppWithLockCheck(
                Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APP_CLOCK),
                "Clock"
            )
            if (!launched) {
                val openedClock = launchIntentDirect(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    || launchIntentDirect(Intent(AlarmClock.ACTION_SHOW_TIMERS))
                    || launchIntentDirect(Intent(AlarmClock.ACTION_SET_ALARM))
                    || launchLikelyClockLauncherApp()
                if (!openedClock) {
                    Toast.makeText(activity, "No clock app found", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dateTextView.setOnClickListener {
            val launched = launchResolvedAppWithLockCheck(
                Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_APP_CALENDAR),
                "Calendar"
            )
            if (!launched) {
                val openedCalendar = launchIntentDirect(
                    Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
                )
                if (!openedCalendar) {
                    Toast.makeText(activity, "No calendar app found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun launchResolvedAppWithLockCheck(intent: Intent, fallbackName: String): Boolean {
        val resolveInfo = activity.packageManager.resolveActivity(intent, 0) ?: return false
        val packageName = resolveInfo.activityInfo?.packageName ?: return false
        val appName = try {
            activity.packageManager.getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString()
        } catch (_: Exception) {
            fallbackName
        }
        appLauncher.launchAppWithLockCheck(packageName, appName)
        return true
    }

    private fun launchIntentDirect(intent: Intent): Boolean {
        try {
            activity.startActivity(intent)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun launchLikelyClockLauncherApp(): Boolean {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = activity.packageManager.queryIntentActivities(launcherIntent, 0)

        val clockApp = candidates
            .asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = try {
                    activity.packageManager.getApplicationLabel(info.activityInfo.applicationInfo).toString()
                } catch (_: Exception) {
                    packageName
                }
                packageName to label
            }
            .distinctBy { it.first }
            .firstOrNull { (packageName, label) ->
                val packageLower = packageName.lowercase(Locale.ROOT)
                val labelLower = label.lowercase(Locale.ROOT)
                "clock" in packageLower || "clock" in labelLower || "alarm" in packageLower || "alarm" in labelLower
            } ?: return false

        appLauncher.launchAppWithLockCheck(clockApp.first, clockApp.second)
        return true
    }

    fun setupDrawerLayout() {
        val drawerLayout = views.drawerLayout
        // Set drawer to full width - use post to ensure view is laid out
        drawerLayout.post {
            val displayMetrics = activity.resources.displayMetrics
            val drawerWidth = displayMetrics.widthPixels
            val targetWidth = drawerWidth

            // Left drawer
            val leftDrawerView = activity.findViewById<FrameLayout>(R.id.widgets_drawer)
            leftDrawerView?.let {
                val params = it.layoutParams as ViewGroup.LayoutParams
                params.width = targetWidth
                it.layoutParams = params

                val header = activity.findViewById<LinearLayout>(R.id.widget_settings_header)
                val drawerScroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.widgets_drawer_scroll)
                if (header != null && drawerScroll != null) {
                    val headerParams = header.layoutParams as FrameLayout.LayoutParams
                    val initialHeaderTopMargin = headerParams.topMargin
                    val initialScrollTopPadding = drawerScroll.paddingTop

                    ViewCompat.setOnApplyWindowInsetsListener(it) { _, insets ->
                        val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

                        val updatedHeaderParams = header.layoutParams as FrameLayout.LayoutParams
                        updatedHeaderParams.topMargin = initialHeaderTopMargin + topInset
                        header.layoutParams = updatedHeaderParams

                        drawerScroll.setPadding(
                            drawerScroll.paddingLeft,
                            initialScrollTopPadding + topInset,
                            drawerScroll.paddingRight,
                            drawerScroll.paddingBottom
                        )
                        insets
                    }
                    ViewCompat.requestApplyInsets(it)
                }
            }

            // Right drawer
            val rightDrawerView = activity.findViewById<FrameLayout>(R.id.wallpaper_drawer)
            rightDrawerView?.let {
                val params = it.layoutParams as ViewGroup.LayoutParams
                params.width = targetWidth
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
