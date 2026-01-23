package com.guruswarupa.launch

import android.content.SharedPreferences
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat

/**
 * Manages the feature tutorial that shows users how to use each feature one by one
 * after completing onboarding.
 */
class FeatureTutorialManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences
) {
    // All methods are defined below
    private var currentStep = 0
    private var tutorialOverlay: View? = null
    private var isTutorialActive = false
    private var scrollViews: MutableList<View> = mutableListOf()
    
    companion object {
        private const val PREF_TUTORIAL_SHOWN = "feature_tutorial_shown"
        private const val PREF_TUTORIAL_STEP = "feature_tutorial_current_step"
    }
    
    enum class TutorialStep(
        val title: String,
        val description: String,
        val targetViewId: Int,
        val position: HighlightPosition = HighlightPosition.BOTTOM,
        val targetViewTag: String? = null // Optional tag for finding views by tag instead of ID
    ) {
        SEARCH_BAR(
            "Universal Search",
            "Search for apps, contacts, or web queries. Double tap to change wallpaper, long press to open Google.",
            R.id.search_box,
            HighlightPosition.BOTTOM
        ),
        VOICE_SEARCH(
            "Voice Search",
            "Tap the microphone to search using your voice. Try saying 'Open YouTube' or 'Call John'.",
            R.id.voice_search_button,
            HighlightPosition.BOTTOM
        ),
        TIME_WIDGET(
            "Time Widget",
            "Tap the time to open the Clock app.",
            R.id.time_widget,
            HighlightPosition.BOTTOM
        ),
        DATE_WIDGET(
            "Date Widget",
            "Tap the date to open the Calendar app and view your schedule.",
            R.id.date_widget,
            HighlightPosition.BOTTOM
        ),
        WEATHER_WIDGET(
            "Weather Widget",
            "View real-time weather information. Configure your API key in Settings if needed.",
            R.id.weather_widget,
            HighlightPosition.BOTTOM
        ),
        DOCK_SETTINGS(
            "Settings",
            "Access launcher settings to customize your experience, change wallpaper, manage widgets, and more.",
            R.id.app_dock,
            HighlightPosition.BOTTOM,
            "settings_button"
        ),
        DOCK_FAVORITES(
            "Favorites Toggle",
            "Toggle between showing only your favorite apps or all apps. Tap the star to show favorites, tap the grid to show all.",
            R.id.app_dock,
            HighlightPosition.BOTTOM,
            "favorite_toggle"
        ),
        DOCK_WORKSPACE(
            "Workspace Toggle",
            "Switch between different workspaces to organize your apps. Long press to configure workspaces.",
            R.id.app_dock,
            HighlightPosition.BOTTOM,
            "workspace_toggle"
        ),
        DOCK_FOCUS_MODE(
            "Focus Mode",
            "Enable focus mode to temporarily hide distracting apps. Long press to configure which apps are allowed during focus mode.",
            R.id.app_dock,
            HighlightPosition.BOTTOM,
            "focus_mode_toggle"
        ),
        DOCK_POWER_SAVER(
            "Power Saver Mode",
            "Enable power saver to save battery by keeping wallpaper black and hiding usage stats. Ideal for OLED displays.",
            R.id.app_dock,
            HighlightPosition.BOTTOM,
            "power_saver_toggle"
        ),
        DOCK_SHARE_APK(
            "Share APK",
            "Share APK files of installed apps. Useful for backing up apps or sharing with others.",
            R.id.app_dock,
            HighlightPosition.BOTTOM,
            "apk_share_button"
        ),
        APP_LIST(
            "Smart App List",
            "Apps are sorted alphabetically. Long press any app to uninstall, lock, set a timer, or add to favorites.",
            R.id.app_list,
            HighlightPosition.TOP
        ),
        DRAWER_GESTURE(
            "Widgets Drawer",
            "Swipe from the left edge of the screen to open the widgets drawer. Here you'll find calculator, todo list, workout tracker, finance tracker, and more widgets.",
            R.id.widgets_drawer,
            HighlightPosition.CENTER
        ),
        ANDROID_WIDGETS(
            "Android Widgets",
            "Add third-party widgets from other apps to your launcher. Tap 'Add Widget' to browse and add widgets from installed apps.",
            R.id.widgets_section,
            HighlightPosition.BOTTOM
        ),
        TODO_WIDGET(
            "Todo List Widget",
            "Manage your daily tasks. Tap the + button to add tasks with due times and priorities.",
            R.id.add_todo_button,
            HighlightPosition.BOTTOM
        ),
        WORKOUT_WIDGET(
            "Workout Tracker",
            "Track your daily workouts and exercises. Add exercises, increment counts, view your workout calendar, and track your streak. Tap the + button to add exercises.",
            R.id.workout_widget_container,
            HighlightPosition.BOTTOM
        ),
        CALCULATOR_WIDGET(
            "Calculator Widget",
            "Perform calculations directly on your home screen. Switch between basic, scientific, and converter modes.",
            R.id.calculator_widget_container,
            HighlightPosition.BOTTOM
        ),
        FINANCE_WIDGET(
            "Finance Tracker",
            "Track your income and expenses. Long press the balance to view transaction history.",
            R.id.finance_widget,
            HighlightPosition.BOTTOM
        ),
        WEEKLY_USAGE(
            "Usage Statistics",
            "View your weekly app usage. Tap on any day to see detailed usage for that day.",
            R.id.weekly_usage_graph,
            HighlightPosition.TOP
        ),
        NOTIFICATIONS_WIDGET(
            "Notifications Widget",
            "View and manage notifications directly from your launcher. Swipe to dismiss.",
            R.id.notifications_widget_container,
            HighlightPosition.BOTTOM
        )
    }
    
    enum class HighlightPosition {
        TOP, BOTTOM, LEFT, RIGHT, CENTER
    }
    
    /**
     * Check if tutorial should be shown
     */
    fun shouldShowTutorial(): Boolean {
        return !sharedPreferences.getBoolean(PREF_TUTORIAL_SHOWN, false)
    }
    
    /**
     * Start the tutorial
     */
    fun startTutorial() {
        if (isTutorialActive) return
        
        currentStep = sharedPreferences.getInt(PREF_TUTORIAL_STEP, 0)
        if (currentStep >= TutorialStep.values().size) {
            // Tutorial already completed
            markTutorialComplete()
            return
        }
        
        isTutorialActive = true
        showCurrentStep()
    }
    
    /**
     * Show the current tutorial step
     */
    private fun showCurrentStep() {
        if (currentStep >= TutorialStep.values().size) {
            finishTutorial()
            return
        }
        
        val step = TutorialStep.values()[currentStep]
        
        // Special handling for drawer gesture
        if (step == TutorialStep.DRAWER_GESTURE) {
            showDrawerGestureTutorial()
            return
        }
        
        // For drawer-based steps, ensure drawer is open first
        val isInDrawer = isDrawerStep(step) && step != TutorialStep.DRAWER_GESTURE
        if (isInDrawer) {
            if (!activity.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                activity.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                // Wait for drawer to open before finding view
                activity.drawerLayout.postDelayed({
                    findAndShowView(step)
                }, 400)
                return
            }
        }
        
        findAndShowView(step)
    }
    
    /**
     * Find the target view and show tutorial overlay
     */
    private fun findAndShowView(step: TutorialStep) {
        // Find view by tag if specified, otherwise by ID
        val targetView = if (step.targetViewTag != null) {
            findViewByTag(step.targetViewTag)
        } else {
            activity.findViewById<View>(step.targetViewId)
        }
        
        if (targetView == null || !targetView.isAttachedToWindow) {
            // View not ready yet, try again after a delay
            // For drawer steps, ensure drawer is open
            val isInDrawer = isDrawerStep(step) && step != TutorialStep.DRAWER_GESTURE
            if (isInDrawer && !activity.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                activity.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                activity.drawerLayout.postDelayed({
                    findAndShowView(step)
                }, 400)
                return
            }
            activity.findViewById<ViewGroup>(android.R.id.content)?.postDelayed({
                findAndShowView(step)
            }, 500)
            return
        }
        
        // Check if view is already visible - skip scrolling for top elements like search bar
        val isViewVisible = isViewVisibleOnScreen(targetView)
        
        if (!isViewVisible) {
            // Scroll to view first, then show overlay
            scrollToView(targetView)
            // Wait a bit for scroll to complete, then show overlay
            targetView.postDelayed({
                showTutorialOverlay(step, targetView)
            }, 400)
        } else {
            // View is already visible, show overlay immediately
            showTutorialOverlay(step, targetView)
        }
    }
    
    /**
     * Check if a view is visible on screen
     */
    private fun isViewVisibleOnScreen(view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        val rootLocation = IntArray(2)
        rootView?.getLocationOnScreen(rootLocation)
        
        val viewTop = location[1]
        val viewBottom = location[1] + view.height
        val screenTop = rootLocation[1]
        val screenBottom = screenTop + (rootView?.height ?: 0)
        
        // View is visible if at least part of it is on screen
        return viewTop < screenBottom && viewBottom > screenTop
    }
    
    /**
     * Find a view by tag, searching in the app dock
     */
    private fun findViewByTag(tag: String): View? {
        val appDock = activity.findViewById<ViewGroup>(R.id.app_dock)
        return appDock?.findViewWithTag<View>(tag)
    }
    
    /**
     * Show tutorial overlay for a specific view
     */
    private fun showTutorialOverlay(step: TutorialStep, targetView: View) {
        // Remove existing overlay if any
        removeTutorialOverlay()
        
        val inflater = LayoutInflater.from(activity)
        tutorialOverlay = inflater.inflate(R.layout.tutorial_overlay, null)
        
        // Determine the correct parent view - use drawer if target is in drawer, otherwise use main content
        val isInDrawer = isDrawerStep(step) && step != TutorialStep.DRAWER_GESTURE
        val parentView = if (isInDrawer) {
            // Find the drawer's root view (widgets_drawer FrameLayout)
            activity.findViewById<ViewGroup>(R.id.widgets_drawer) 
                ?: activity.findViewById<ViewGroup>(android.R.id.content)
        } else {
            activity.findViewById<ViewGroup>(android.R.id.content)
        }
        
        parentView?.addView(tutorialOverlay)
        
        // Make overlay clickable to intercept touches and prevent scrolling
        tutorialOverlay?.isClickable = true
        tutorialOverlay?.isFocusable = true
        tutorialOverlay?.visibility = View.VISIBLE
        
        // Ensure overlay is on top
        tutorialOverlay?.bringToFront()
        
        val titleText = tutorialOverlay?.findViewById<TextView>(R.id.tutorial_title)
        val descriptionText = tutorialOverlay?.findViewById<TextView>(R.id.tutorial_description)
        val buttonsContainer = tutorialOverlay?.findViewById<View>(R.id.tutorial_buttons_container)
        val skipButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_skip)
        val nextButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_next)
        val gotItButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_got_it)
        
        titleText?.text = step.title
        descriptionText?.text = step.description
        
        val isLastStep = currentStep == TutorialStep.values().size - 1
        buttonsContainer?.visibility = if (isLastStep) View.GONE else View.VISIBLE
        nextButton?.visibility = if (isLastStep) View.GONE else View.VISIBLE
        gotItButton?.visibility = if (isLastStep) View.VISIBLE else View.GONE
        
        skipButton?.setOnClickListener {
            finishTutorial()
        }
        
        nextButton?.setOnClickListener {
            nextStep()
        }
        
        gotItButton?.setOnClickListener {
            finishTutorial()
        }
        
        // Disable scrolling while tutorial is active
        disableScrolling()
        
        // Position the highlight and text based on target view
        // Use a post to ensure overlay is fully laid out
        tutorialOverlay?.post {
            // Ensure overlay is visible
            tutorialOverlay?.visibility = View.VISIBLE
            positionTutorialOverlay(step, targetView)
        }
    }
    
    /**
     * Position the tutorial overlay relative to the target view
     */
    private fun positionTutorialOverlay(step: TutorialStep, targetView: View) {
        tutorialOverlay?.let { overlay ->
            val highlightView = overlay.findViewById<View>(R.id.tutorial_highlight)
            val textContainer = overlay.findViewById<View>(R.id.tutorial_text_container)
            
            // Wait for both views to be laid out and measured
            targetView.post {
                overlay.post {
                    // Get the parent view (could be main content or drawer)
                    val parentView = overlay.parent as? ViewGroup
                        ?: activity.findViewById<ViewGroup>(android.R.id.content)
                        ?: return@post
                    
                    // Ensure views are measured
                    if (targetView.width == 0 || targetView.height == 0) {
                        targetView.measure(
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                    }
                    
                    // Get screen coordinates for both views
                    val targetLocation = IntArray(2)
                    targetView.getLocationOnScreen(targetLocation)
                    
                    val rootLocation = IntArray(2)
                    parentView.getLocationOnScreen(rootLocation)
                    
                    // Calculate position relative to root content view (overlay's parent)
                    val targetX = targetLocation[0] - rootLocation[0]
                    val targetY = targetLocation[1] - rootLocation[1]
                    val targetWidth = if (targetView.width > 0) targetView.width else targetView.measuredWidth
                    val targetHeight = if (targetView.height > 0) targetView.height else targetView.measuredHeight
                    
                    // Only position highlight if we have valid dimensions
                    if (targetWidth > 0 && targetHeight > 0) {
                        // Position highlight view to match target view using FrameLayout.LayoutParams
                        val highlightParams = highlightView.layoutParams as? FrameLayout.LayoutParams
                            ?: FrameLayout.LayoutParams(
                                targetWidth + 40,
                                targetHeight + 40
                            )
                        
                        highlightParams.leftMargin = (targetX - 20).coerceAtLeast(0)
                        highlightParams.topMargin = (targetY - 20).coerceAtLeast(0)
                        highlightParams.width = targetWidth + 40
                        highlightParams.height = targetHeight + 40
                        highlightView.layoutParams = highlightParams
                        highlightView.visibility = View.VISIBLE
                    } else {
                        // Hide highlight if we can't position it
                        highlightView.visibility = View.GONE
                    }
                    
                    // Position text container based on position preference
                    val textParams = textContainer.layoutParams as? FrameLayout.LayoutParams
                        ?: FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    
                    val screenWidth = parentView.width
                    val screenHeight = parentView.height
                    val padding = 40
                    val textContainerHeight = 250 // Approximate height of text container
                    
                    when (step.position) {
                        HighlightPosition.TOP -> {
                            val topMargin = (targetY - textContainerHeight - padding).coerceAtLeast(padding)
                            textParams.topMargin = topMargin
                            textParams.leftMargin = padding
                            textParams.rightMargin = padding
                        }
                        HighlightPosition.BOTTOM -> {
                            val bottomMargin = targetY + targetHeight + padding
                            // Ensure text doesn't go off screen
                            val maxBottom = screenHeight - textContainerHeight - padding
                            textParams.topMargin = bottomMargin.coerceAtMost(maxBottom)
                            textParams.leftMargin = padding
                            textParams.rightMargin = padding
                        }
                        HighlightPosition.LEFT -> {
                            val textWidth = 350 // Approximate width of text container
                            textParams.topMargin = targetY
                            textParams.leftMargin = (targetX - textWidth - padding).coerceAtLeast(padding)
                            textParams.rightMargin = padding
                        }
                        HighlightPosition.RIGHT -> {
                            textParams.topMargin = targetY
                            textParams.leftMargin = targetX + targetWidth + padding
                            textParams.rightMargin = padding
                        }
                        HighlightPosition.CENTER -> {
                            textParams.topMargin = (screenHeight / 2 - textContainerHeight / 2).coerceAtLeast(padding)
                            textParams.leftMargin = padding
                            textParams.rightMargin = padding
                        }
                    }
                    textContainer.layoutParams = textParams
                    textContainer.visibility = View.VISIBLE
                    
                    // Ensure text container is actually visible on screen
                    textContainer.post {
                        val textLocation = IntArray(2)
                        textContainer.getLocationOnScreen(textLocation)
                        val textHeight = textContainer.height
                        val screenHeight = parentView.height
                        
                        // If text container is off screen, reposition it
                        if (textLocation[1] < 0 || textLocation[1] + textHeight > screenHeight) {
                            // Reposition to center
                            val fallbackParams = textContainer.layoutParams as? FrameLayout.LayoutParams
                                ?: FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                                )
                            fallbackParams.topMargin = (screenHeight / 2 - textHeight / 2).coerceAtLeast(padding)
                            fallbackParams.leftMargin = padding
                            fallbackParams.rightMargin = padding
                            textContainer.layoutParams = fallbackParams
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Scroll to make the target view visible
     */
    private fun scrollToView(targetView: View) {
        targetView.post {
            val scrollView = findScrollView(targetView)
            if (scrollView != null) {
                scrollView.post {
                    // Get current scroll position
                    val currentScrollY = when (scrollView) {
                        is androidx.core.widget.NestedScrollView -> scrollView.scrollY
                        is android.widget.ScrollView -> scrollView.scrollY
                        else -> 0
                    }
                    
                    // Use a more accurate method to get view position in scroll view
                    // Get screen coordinates
                    val targetLocation = IntArray(2)
                    targetView.getLocationOnScreen(targetLocation)
                    
                    val scrollViewLocation = IntArray(2)
                    scrollView.getLocationOnScreen(scrollViewLocation)
                    
                    // Calculate view's Y position relative to scroll view's content
                    // The view's position in the scroll view = screen position difference + current scroll
                    val viewTopInContent = targetLocation[1] - scrollViewLocation[1] + currentScrollY
                    val viewBottomInContent = viewTopInContent + targetView.height
                    
                    // Check if view is already visible
                    val visibleTop = currentScrollY
                    val visibleBottom = currentScrollY + scrollView.height
                    
                    // Calculate how much of the view is visible
                    val visibleHeight = if (viewTopInContent < visibleBottom && viewBottomInContent > visibleTop) {
                        minOf(viewBottomInContent, visibleBottom) - maxOf(viewTopInContent, visibleTop)
                    } else {
                        0
                    }
                    
                    // If view is already mostly visible (at least 70% visible), don't scroll
                    if (visibleHeight > targetView.height * 0.7) {
                        return@post // View is already visible enough
                    }
                    
                    // View needs scrolling - calculate desired position
                    // Position view with padding at top for tutorial text
                    val paddingForTutorial = 180
                    val desiredScrollY = (viewTopInContent - paddingForTutorial).coerceAtLeast(0)
                    
                    // Only scroll if it's a significant change
                    if (Math.abs(desiredScrollY - currentScrollY) > 20) {
                        when (scrollView) {
                            is androidx.core.widget.NestedScrollView -> {
                                scrollView.smoothScrollTo(0, desiredScrollY)
                            }
                            is android.widget.ScrollView -> {
                                scrollView.smoothScrollTo(0, desiredScrollY)
                            }
                        }
                    }
                }
            } else {
                // No scroll view found, try to scroll the view into view using requestRectangleOnScreen
                targetView.post {
                    val rect = android.graphics.Rect()
                    targetView.getHitRect(rect)
                    // Expand rect to include some padding for tutorial text above
                    rect.top -= 200
                    rect.bottom += 100
                    targetView.requestRectangleOnScreen(rect, true)
                }
            }
        }
    }
    
    /**
     * Find the scroll view containing the target view
     */
    private fun findScrollView(view: View): View? {
        var parent = view.parent
        while (parent != null && parent is View) {
            if (parent is androidx.core.widget.NestedScrollView || 
                parent is android.widget.ScrollView) {
                return parent as View
            }
            parent = parent.parent
        }
        return null
    }
    
    /**
     * Show drawer gesture tutorial (special case)
     */
    private fun showDrawerGestureTutorial() {
        // Open the drawer first
        activity.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        
        // Wait for drawer animation to complete, then show tutorial inside drawer
        activity.drawerLayout.postDelayed({
            val step = TutorialStep.DRAWER_GESTURE
            val drawerView = activity.findViewById<View>(R.id.widgets_drawer)
            if (drawerView != null && drawerView.isAttachedToWindow) {
                // Show tutorial overlay inside the drawer
                showTutorialOverlay(step, drawerView)
            } else {
                // Retry after a bit more delay
                activity.drawerLayout.postDelayed({
                    val retryDrawerView = activity.findViewById<View>(R.id.widgets_drawer)
                    if (retryDrawerView != null && retryDrawerView.isAttachedToWindow) {
                        showTutorialOverlay(step, retryDrawerView)
                    } else {
                        nextStep()
                    }
                }, 300)
            }
        }, 400) // Wait for drawer animation
    }
    
    /**
     * Disable scrolling while tutorial is active
     */
    private fun disableScrolling() {
        scrollViews.clear()
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        findAndDisableScrollViews(rootView)
    }
    
    /**
     * Find and disable all scroll views recursively
     */
    private fun findAndDisableScrollViews(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            when (child) {
                is androidx.core.widget.NestedScrollView -> {
                    // Disable nested scrolling to prevent automatic scrolling
                    child.isNestedScrollingEnabled = false
                    scrollViews.add(child)
                }
                is android.widget.ScrollView -> {
                    // Store original scroll state
                    scrollViews.add(child)
                }
                is ViewGroup -> {
                    findAndDisableScrollViews(child)
                }
            }
        }
    }
    
    /**
     * Re-enable scrolling after tutorial
     */
    private fun enableScrolling() {
        scrollViews.forEach { scrollView ->
            when (scrollView) {
                is androidx.core.widget.NestedScrollView -> {
                    scrollView.isNestedScrollingEnabled = true
                    scrollView.setOnTouchListener(null) // Restore default behavior
                }
                is android.widget.ScrollView -> {
                    scrollView.setOnTouchListener(null) // Restore default behavior
                }
            }
        }
        scrollViews.clear()
    }
    
    /**
     * Check if a tutorial step is in the drawer
     */
    private fun isDrawerStep(step: TutorialStep): Boolean {
        return step in listOf(
            TutorialStep.DRAWER_GESTURE,
            TutorialStep.ANDROID_WIDGETS,
            TutorialStep.TODO_WIDGET,
            TutorialStep.WORKOUT_WIDGET,
            TutorialStep.CALCULATOR_WIDGET,
            TutorialStep.FINANCE_WIDGET,
            TutorialStep.WEEKLY_USAGE,
            TutorialStep.NOTIFICATIONS_WIDGET
        )
    }
    
    /**
     * Move to next tutorial step
     */
    private fun nextStep() {
        val previousStep = if (currentStep > 0) TutorialStep.values()[currentStep - 1] else null
        currentStep++
        sharedPreferences.edit().putInt(PREF_TUTORIAL_STEP, currentStep).apply()
        
        if (currentStep >= TutorialStep.values().size) {
            finishTutorial()
            return
        }
        
        val nextStep = TutorialStep.values()[currentStep]
        val isNextDrawerStep = isDrawerStep(nextStep) && nextStep != TutorialStep.DRAWER_GESTURE
        
        // Only close drawer if moving to a non-drawer step
        if (!isNextDrawerStep) {
            if (activity.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                activity.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                // Wait for drawer to close before showing next step
                activity.drawerLayout.postDelayed({
                    showCurrentStep()
                }, 300)
                return
            }
        } else {
            // Next step is in drawer - ensure drawer is open
            if (!activity.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                activity.drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                // Wait for drawer to open before showing next step
                activity.drawerLayout.postDelayed({
                    showCurrentStep()
                }, 400)
                return
            }
        }
        
        showCurrentStep()
    }
    
    /**
     * Finish the tutorial
     */
    private fun finishTutorial() {
        removeTutorialOverlay()
        
        // Re-enable scrolling
        enableScrolling()
        
        // Close drawer if open
        if (activity.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            activity.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        }
        
        markTutorialComplete()
    }
    
    /**
     * Mark tutorial as complete
     */
    private fun markTutorialComplete() {
        sharedPreferences.edit()
            .putBoolean(PREF_TUTORIAL_SHOWN, true)
            .putInt(PREF_TUTORIAL_STEP, TutorialStep.values().size)
            .apply()
        isTutorialActive = false
    }
    
    /**
     * Remove tutorial overlay
     */
    private fun removeTutorialOverlay() {
        tutorialOverlay?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
            tutorialOverlay = null
        }
    }
    
    /**
     * Reset tutorial (for testing or if user wants to see it again)
     */
    fun resetTutorial() {
        sharedPreferences.edit()
            .putBoolean(PREF_TUTORIAL_SHOWN, false)
            .putInt(PREF_TUTORIAL_STEP, 0)
            .apply()
    }
}
