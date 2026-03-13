package com.guruswarupa.launch.utils

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.widget.NestedScrollView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.SettingsActivity
import kotlin.math.abs

/**
 * Page-aware tutorial flow for the launcher pager:
 * widgets page -> home page -> wallpaper page.
 */
class FeatureTutorialManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences
) {
    private var currentStep = 0
    private var tutorialOverlay: View? = null
    private var isTutorialActive = false
    private var renderToken = 0

    companion object {
        private const val PREF_TUTORIAL_SHOWN = "feature_tutorial_shown"
        private const val PREF_TUTORIAL_STEP = "feature_tutorial_current_step"
        private const val PAGE_SETTLE_DELAY_MS = 260L
        private const val RETRY_DELAY_MS = 180L
        private const val MAX_RENDER_ATTEMPTS = 10
        private const val SCROLL_SETTLE_DELAY_MS = 320L
    }

    private enum class TutorialPage(val rootViewId: Int) {
        HOME(R.id.main_content),
        WIDGETS(R.id.widgets_drawer),
        WALLPAPER(R.id.wallpaper_drawer)
    }

    private enum class HighlightPosition {
        TOP, BOTTOM, LEFT, RIGHT, CENTER
    }

    private enum class TutorialStep(
        val page: TutorialPage,
        val title: String,
        val description: String,
        val targetViewId: Int,
        val highlightPosition: HighlightPosition = HighlightPosition.BOTTOM,
        val targetViewTag: String? = null,
        val highlightVisible: Boolean = true,
        val scrollToTarget: Boolean = true
    ) {
        HOME_OVERVIEW(
            page = TutorialPage.HOME,
            title = "Home Page",
            description = "This middle page is your launcher home. It combines widgets, search, shortcuts, and the full app list in one vertical flow.",
            targetViewId = R.id.main_content,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        ),
        SEARCH_BAR(
            page = TutorialPage.HOME,
            title = "Universal Search",
            description = "Search apps, contacts, and quick actions from here. Keep it empty to browse normally, or type to filter instantly.",
            targetViewId = R.id.search_box
        ),
        VOICE_SEARCH(
            page = TutorialPage.HOME,
            title = "Voice Search",
            description = "Tap the mic to run voice commands or search hands-free.",
            targetViewId = R.id.voice_search_button
        ),
        TIME_AND_WEATHER(
            page = TutorialPage.HOME,
            title = "Top Widgets",
            description = "The top card keeps time, date, weather, and status info visible without leaving the home page.",
            targetViewId = R.id.top_widget_container
        ),
        APP_LIST(
            page = TutorialPage.HOME,
            title = "App List Actions",
            description = "Your installed apps live here. Long press any app to open actions like Add to Favorites, share, app info, and limits.",
            targetViewId = R.id.app_list,
            highlightPosition = HighlightPosition.TOP
        ),
        APP_DOCK(
            page = TutorialPage.HOME,
            title = "Dock Shortcuts",
            description = "The dock holds your quick actions like settings, favorites, workspace switching, focus mode and encrypted vault.",
            targetViewId = R.id.app_dock
        ),
        WORKSPACE_TOGGLE(
            page = TutorialPage.HOME,
            title = "Workspaces",
            description = "Long press this icon to create and manage workspaces. Tap it to enable a saved workspace, switch workspaces, or turn workspace mode off.",
            targetViewId = R.id.app_dock,
            targetViewTag = "workspace_toggle"
        ),
        FOCUS_MODE(
            page = TutorialPage.HOME,
            title = "Focus Mode",
            description = "Long press here to build your focus app list. Tap it to start a focus session, pick a duration, and optionally enable Do Not Disturb.",
            targetViewId = R.id.app_dock,
            targetViewTag = "focus_mode_container"
        ),
        VAULT_BUTTON(
            page = TutorialPage.HOME,
            title = "Encrypted Vault",
            description = "This opens the encrypted vault where you can keep private files protected inside the launcher.",
            targetViewId = R.id.app_dock,
            targetViewTag = "vault_button"
        ),
        SETTINGS_BUTTON(
            page = TutorialPage.HOME,
            title = "Launcher Settings",
            description = "This settings shortcut opens the full launcher configuration screen. The tutorial will walk through each settings section next.",
            targetViewId = R.id.app_dock,
            targetViewTag = "settings_button"
        ),
        WIDGETS_PAGE(
            page = TutorialPage.WIDGETS,
            title = "Widgets Page",
            description = "The left page is widgets page. Swipe over to see larger widgets and utility panels.",
            targetViewId = R.id.widgets_drawer,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        ),
        WIDGETS_SETTINGS(
            page = TutorialPage.WIDGETS,
            title = "Configure Widgets",
            description = "Use this action to choose which widgets appear in the drawer and adjust the widgets page to fit your setup.",
            targetViewId = R.id.widget_config_button,
            scrollToTarget = false
        ),
        WIDGETS_SCROLL(
            page = TutorialPage.WIDGETS,
            title = "Scrollable Widget Feed",
            description = "This page scrolls vertically, so you can keep multiple widgets here without crowding the home page.",
            targetViewId = R.id.widgets_drawer_scroll,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        ),
        WALLPAPER_PAGE(
            page = TutorialPage.WALLPAPER,
            title = "Wallpaper Page",
            description = "The right page gives you a clean wallpaper-focused view with a large ambient clock.",
            targetViewId = R.id.wallpaper_drawer,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        ),
        WALLPAPER_CLOCK(
            page = TutorialPage.WALLPAPER,
            title = "Ambient Clock",
            description = "Use this page when you want a distraction-free wallpaper and clock view.",
            targetViewId = R.id.right_drawer_time
        ),
        PAGE_NAVIGATION(
            page = TutorialPage.HOME,
            title = "Pager Navigation",
            description = "Swipe left for widgets, stay in the middle for home, and swipe right for the wallpaper page. The tutorial will always return here when it finishes.",
            targetViewId = R.id.main_content,
            highlightPosition = HighlightPosition.CENTER,
            highlightVisible = false,
            scrollToTarget = false
        )
    }

    fun shouldShowTutorial(): Boolean {
        return !sharedPreferences.getBoolean(PREF_TUTORIAL_SHOWN, false)
    }

    fun startTutorial() {
        removeTutorialOverlay()
        currentStep = sharedPreferences.getInt(PREF_TUTORIAL_STEP, 0)
        if (currentStep >= TutorialStep.entries.size) {
            markTutorialComplete()
            return
        }

        isTutorialActive = true
        showCurrentStep()
    }

    private fun showCurrentStep() {
        if (!isTutorialActive) return
        if (currentStep >= TutorialStep.entries.size) {
            finishTutorial()
            return
        }

        val step = TutorialStep.entries[currentStep]
        val token = ++renderToken
        openPage(step.page, animated = false) {
            renderStep(step, token, attempt = 0)
        }
    }

    private fun renderStep(step: TutorialStep, token: Int, attempt: Int) {
        if (!isTutorialActive || token != renderToken) return

        val pageRoot = activity.findViewById<ViewGroup>(step.page.rootViewId)
        val targetView = resolveTargetView(step, pageRoot)
        if (pageRoot == null || targetView == null || !targetView.isAttachedToWindow) {
            if (attempt >= MAX_RENDER_ATTEMPTS) {
                nextStep()
                return
            }
            (pageRoot ?: activity.findViewById(android.R.id.content))?.postDelayed({
                renderStep(step, token, attempt + 1)
            }, RETRY_DELAY_MS)
            return
        }

        if (step.scrollToTarget) {
            scrollToView(targetView)
        }

        pageRoot.postDelayed({
            if (!isTutorialActive || token != renderToken) return@postDelayed
            showTutorialOverlay(step, pageRoot, targetView)
        }, if (step.scrollToTarget) SCROLL_SETTLE_DELAY_MS else 60L)
    }

    private fun resolveTargetView(step: TutorialStep, pageRoot: ViewGroup?): View? {
        if (pageRoot == null) return null
        return if (step.targetViewTag != null) {
            pageRoot.findViewWithTag(step.targetViewTag)
        } else {
            pageRoot.findViewById(step.targetViewId)
        }
    }

    private fun openPage(page: TutorialPage, animated: Boolean, onReady: () -> Unit) {
        when (page) {
            TutorialPage.HOME -> activity.openHomePage(animated)
            TutorialPage.WIDGETS -> activity.openWidgetsPage(animated)
            TutorialPage.WALLPAPER -> activity.openWallpaperPage(animated)
        }

        val rootView = activity.findViewById<View>(page.rootViewId)
        rootView?.postDelayed(onReady, if (animated) PAGE_SETTLE_DELAY_MS else 80L) ?: onReady()
    }

    @SuppressLint("InflateParams")
    private fun showTutorialOverlay(step: TutorialStep, parentView: ViewGroup, targetView: View) {
        removeTutorialOverlay()

        tutorialOverlay = LayoutInflater.from(activity).inflate(R.layout.tutorial_overlay, null)
        parentView.addView(tutorialOverlay)

        @SuppressLint("ClickableViewAccessibility")
        tutorialOverlay?.setOnTouchListener { view, _ ->
            view.performClick()
            false
        }
        tutorialOverlay?.bringToFront()

        val titleText = tutorialOverlay?.findViewById<TextView>(R.id.tutorial_title)
        val descriptionText = tutorialOverlay?.findViewById<TextView>(R.id.tutorial_description)
        val buttonsContainer = tutorialOverlay?.findViewById<View>(R.id.tutorial_buttons_container)
        val skipButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_skip)
        val nextButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_next)
        val gotItButton = tutorialOverlay?.findViewById<Button>(R.id.tutorial_got_it)

        titleText?.text = step.title
        descriptionText?.text = step.description

        val isLastStep = currentStep == TutorialStep.entries.lastIndex
        buttonsContainer?.visibility = if (isLastStep) View.GONE else View.VISIBLE
        nextButton?.visibility = if (isLastStep) View.GONE else View.VISIBLE
        gotItButton?.visibility = if (isLastStep) View.VISIBLE else View.GONE

        skipButton?.setOnClickListener { finishTutorial() }
        nextButton?.setOnClickListener { nextStep() }
        gotItButton?.setOnClickListener { finishTutorial() }

        tutorialOverlay?.post {
            positionTutorialOverlay(step, parentView, targetView)
        }
    }

    private fun positionTutorialOverlay(step: TutorialStep, parentView: ViewGroup, targetView: View) {
        val overlay = tutorialOverlay ?: return
        val highlightView = overlay.findViewById<View>(R.id.tutorial_highlight)
        val textContainer = overlay.findViewById<View>(R.id.tutorial_text_container)

        val targetLocation = IntArray(2)
        targetView.getLocationOnScreen(targetLocation)
        val rootLocation = IntArray(2)
        parentView.getLocationOnScreen(rootLocation)

        val targetX = targetLocation[0] - rootLocation[0]
        val targetY = targetLocation[1] - rootLocation[1]
        val targetWidth = targetView.width.takeIf { it > 0 } ?: targetView.measuredWidth
        val targetHeight = targetView.height.takeIf { it > 0 } ?: targetView.measuredHeight

        if (!step.highlightVisible || targetWidth <= 0 || targetHeight <= 0) {
            highlightView.visibility = View.GONE
        } else {
            val params = (highlightView.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(targetWidth + 40, targetHeight + 40)
            params.leftMargin = (targetX - 20).coerceAtLeast(0)
            params.topMargin = (targetY - 20).coerceAtLeast(0)
            params.width = targetWidth + 40
            params.height = targetHeight + 40
            highlightView.layoutParams = params
            highlightView.visibility = View.VISIBLE
        }

        val textParams = (textContainer.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        val screenWidth = parentView.width
        val screenHeight = parentView.height
        val padding = 40

        textContainer.measure(
            View.MeasureSpec.makeMeasureSpec(screenWidth - padding * 2, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val textHeight = textContainer.measuredHeight
        val longTarget = targetHeight > screenHeight * 0.6
        var centerText = step.highlightPosition == HighlightPosition.CENTER

        when (step.highlightPosition) {
            HighlightPosition.TOP -> {
                val topMargin = (targetY - textHeight - padding).coerceAtLeast(padding)
                if (topMargin <= padding) {
                    centerText = true
                } else {
                    textParams.topMargin = topMargin
                }
            }

            HighlightPosition.BOTTOM -> {
                val topMargin = targetY + targetHeight + padding
                val maxTop = screenHeight - textHeight - padding
                if (longTarget || topMargin > maxTop) {
                    centerText = true
                } else {
                    textParams.topMargin = topMargin.coerceAtMost(maxTop)
                }
            }

            HighlightPosition.LEFT -> {
                textParams.topMargin = targetY
                textParams.leftMargin = padding
            }

            HighlightPosition.RIGHT -> {
                textParams.topMargin = targetY
                textParams.leftMargin = padding
            }

            HighlightPosition.CENTER -> {
                centerText = true
            }
        }

        if (centerText) {
            textParams.topMargin = ((screenHeight - textHeight) / 2).coerceAtLeast(padding)
        }

        textParams.leftMargin = padding
        textParams.rightMargin = padding
        textContainer.layoutParams = textParams
    }

    private fun scrollToView(targetView: View) {
        targetView.post {
            val scrollView = findVerticalScrollParent(targetView) ?: run {
                val rect = Rect()
                targetView.getHitRect(rect)
                val rootHeight = activity.findViewById<ViewGroup>(android.R.id.content)?.height ?: 0
                val centerOffset = (rootHeight / 2) - (targetView.height / 2)
                rect.top -= centerOffset
                rect.bottom += centerOffset
                targetView.requestRectangleOnScreen(rect, true)
                return@post
            }

            scrollView.post {
                val currentScrollY = scrollView.scrollY

                val targetLocation = IntArray(2)
                targetView.getLocationOnScreen(targetLocation)
                val scrollLocation = IntArray(2)
                scrollView.getLocationOnScreen(scrollLocation)

                val targetTopInContent = targetLocation[1] - scrollLocation[1] + currentScrollY
                val desiredScrollY =
                    (targetTopInContent - (scrollView.height / 2) + (targetView.height / 2)).coerceAtLeast(0)
                val maxScrollY =
                    (scrollView.getChildAt(0)?.height?.minus(scrollView.height) ?: 0).coerceAtLeast(0)
                val finalScrollY = desiredScrollY.coerceIn(0, maxScrollY)

                if (abs(finalScrollY - currentScrollY) > 10) {
                    scrollView.smoothScrollTo(0, finalScrollY)
                }
            }
        }
    }

    private fun findVerticalScrollParent(view: View): NestedScrollView? {
        var parent = view.parent
        while (parent is View) {
            if (parent is NestedScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun nextStep() {
        if (TutorialStep.entries[currentStep] == TutorialStep.SETTINGS_BUTTON) {
            launchSettingsTutorial()
            return
        }

        currentStep++
        sharedPreferences.edit { putInt(PREF_TUTORIAL_STEP, currentStep) }

        if (currentStep >= TutorialStep.entries.size) {
            finishTutorial()
            return
        }

        showCurrentStep()
    }

    private fun launchSettingsTutorial() {
        currentStep++
        sharedPreferences.edit { putInt(PREF_TUTORIAL_STEP, currentStep) }

        removeTutorialOverlay()
        isTutorialActive = false
        renderToken++

        activity.startActivity(
            Intent(activity, SettingsActivity::class.java).apply {
                putExtra(SettingsActivity.EXTRA_START_SETTINGS_TUTORIAL, true)
            }
        )
    }

    private fun finishTutorial() {
        removeTutorialOverlay()
        isTutorialActive = false
        renderToken++
        activity.openHomePage(animated = false)
        markTutorialComplete()
    }

    private fun markTutorialComplete() {
        sharedPreferences.edit {
            putBoolean(PREF_TUTORIAL_SHOWN, true)
            putInt(PREF_TUTORIAL_STEP, TutorialStep.entries.size)
        }
    }

    private fun removeTutorialOverlay() {
        tutorialOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        tutorialOverlay = null
    }

    @Suppress("unused")
    fun resetTutorial() {
        renderToken++
        removeTutorialOverlay()
        isTutorialActive = false
        sharedPreferences.edit {
            putBoolean(PREF_TUTORIAL_SHOWN, false)
            putInt(PREF_TUTORIAL_STEP, 0)
        }
    }
}
