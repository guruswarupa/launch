package com.guruswarupa.launch.managers

import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R

/**
 * Hosts the left widgets screen, the launcher home screen, and the right wallpaper screen
 * inside a horizontally swipeable container.
 */
class ScreenPagerManager(
    private val activity: FragmentActivity,
    private val drawerLayout: DrawerLayout
) {
    enum class Page(val index: Int) {
        LEFT(0),
        CENTER(1),
        RIGHT(2)
    }

    private lateinit var pagerScrollView: HorizontalScrollView
    private var pageWidth = 0
    private var leftPageLocked = false
    private var currentPage = Page.CENTER
    private var pageChangeListener: ((Page) -> Unit)? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartScrollX = 0
    private var velocityTracker: VelocityTracker? = null
    private val viewConfiguration = ViewConfiguration.get(activity)
    private val minimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
    private val touchSlop = viewConfiguration.scaledTouchSlop

    companion object {
        private const val PAGE_SWITCH_THRESHOLD = 0.12f
        private const val PAGE_SWITCH_SETTLE_THRESHOLD = 0.22f
        private const val FLING_VELOCITY_MULTIPLIER = 6
    }

    fun setup() {
        val leftView = activity.findViewById<View>(R.id.widgets_drawer) ?: return
        val centerView = activity.findViewById<View>(R.id.main_content) ?: return
        val rightView = activity.findViewById<View>(R.id.wallpaper_drawer) ?: return

        val pages = listOf(leftView, centerView, rightView)
        val pageStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        drawerLayout.removeAllViews()

        pages.forEach { page ->
            (page.parent as? ViewGroup)?.removeView(page)
            pageStrip.addView(
                page,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        pagerScrollView = object : HorizontalScrollView(activity) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        touchStartScrollX = scrollX
                        updatePageWidth()
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain().apply {
                            addMovement(event)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val deltaX = event.x - touchStartX
                        val deltaY = event.y - touchStartY
                        val isHorizontalSwipe =
                            kotlin.math.abs(deltaX) > touchSlop &&
                                kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)

                        if (!isHorizontalSwipe) {
                            return false
                        }

                        if (leftPageLocked &&
                            nearestPageFor(touchStartScrollX) == Page.CENTER &&
                            deltaX > 0f
                        ) {
                            return false
                        }

                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }

                }
                return super.onInterceptTouchEvent(event)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouchEvent(event)
                return super.onTouchEvent(event)
            }
        }.apply {
            layoutParams = DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.INVISIBLE
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(pageStrip)
            setOnScrollChangeListener { _, scrollX, _, _, _ ->
                val page = nearestPageFor(scrollX)
                if (page != currentPage) {
                    notifyPageChanged(page, force = false)
                }
            }
        }

        drawerLayout.addView(pagerScrollView)

        pagerScrollView.post {
            updatePageWidth()
            scrollToPage(Page.CENTER, animated = false)
            notifyPageChanged(Page.CENTER, force = true)
            pagerScrollView.visibility = View.VISIBLE
        }
    }

    fun setOnPageChanged(listener: (Page) -> Unit) {
        pageChangeListener = listener
    }

    fun isPageOpen(page: Page): Boolean = currentPage == page

    fun openLeftPage(animated: Boolean = true) {
        if (leftPageLocked) return
        scrollToPage(Page.LEFT, animated)
    }

    fun openCenterPage(animated: Boolean = true) = scrollToPage(Page.CENTER, animated)

    fun openRightPage(animated: Boolean = true) = scrollToPage(Page.RIGHT, animated)

    fun setLeftPageLocked(locked: Boolean) {
        leftPageLocked = locked
        if (locked && currentPage == Page.LEFT) {
            openCenterPage(animated = true)
        }
    }

    private fun handleTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchStartScrollX = pagerScrollView.scrollX
                updatePageWidth()
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply {
                    addMovement(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val dragDistance = touchStartX - event.x
                val flingVelocity = velocityTracker?.xVelocity ?: 0f
                pagerScrollView.post { snapToNearestPage(dragDistance, flingVelocity) }
                recycleVelocityTracker()
            }
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun snapToNearestPage(dragDistance: Float, flingVelocity: Float) {
        if (pageWidth <= 0) return
        val threshold = pageWidth * PAGE_SWITCH_THRESHOLD
        val settleThreshold = pageWidth * PAGE_SWITCH_SETTLE_THRESHOLD
        val startPage = nearestPageFor(touchStartScrollX)
        val target = when {
            flingVelocity < -(minimumFlingVelocity * FLING_VELOCITY_MULTIPLIER) -> nextPage(startPage)
            flingVelocity > (minimumFlingVelocity * FLING_VELOCITY_MULTIPLIER) -> previousPage(startPage)
            dragDistance > threshold -> nextPage(startPage)
            dragDistance < -threshold -> previousPage(startPage)
            kotlin.math.abs(dragDistance) > settleThreshold -> nearestPageFor(pagerScrollView.scrollX)
            else -> startPage
        }
        scrollToPage(target, animated = true)
    }

    private fun scrollToPage(page: Page, animated: Boolean) {
        if (!::pagerScrollView.isInitialized) return
        updatePageWidth()
        if (pageWidth <= 0) return

        val targetX = page.index * pageWidth
        if (animated) {
            pagerScrollView.smoothScrollTo(targetX, 0)
        } else {
            pagerScrollView.scrollTo(targetX, 0)
        }

        pagerScrollView.postDelayed(
            { notifyPageChanged(page, force = false) },
            if (animated) 220L else 0L
        )
    }

    private fun updatePageWidth() {
        val width = drawerLayout.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        if (width == pageWidth) return

        pageWidth = width
        val pageStrip = pagerScrollView.getChildAt(0) as? LinearLayout ?: return
        for (index in 0 until pageStrip.childCount) {
            val child = pageStrip.getChildAt(index)
            val params = child.layoutParams as LinearLayout.LayoutParams
            if (params.width != pageWidth) {
                params.width = pageWidth
                child.layoutParams = params
            }
        }
        pageStrip.requestLayout()
    }

    private fun nearestPageFor(scrollX: Int): Page {
        if (pageWidth <= 0) return Page.CENTER
        return when (((scrollX + (pageWidth / 2)) / pageWidth).coerceIn(0, 2)) {
            0 -> if (leftPageLocked) Page.CENTER else Page.LEFT
            1 -> Page.CENTER
            else -> Page.RIGHT
        }
    }

    private fun nextPage(page: Page): Page {
        return when (page) {
            Page.LEFT -> Page.CENTER
            Page.CENTER -> Page.RIGHT
            Page.RIGHT -> if (leftPageLocked) Page.CENTER else Page.LEFT
        }
    }

    private fun previousPage(page: Page): Page {
        return when (page) {
            Page.LEFT -> Page.RIGHT
            Page.CENTER -> if (leftPageLocked) Page.CENTER else Page.LEFT
            Page.RIGHT -> Page.CENTER
        }
    }

    private fun notifyPageChanged(page: Page, force: Boolean) {
        if (!force && page == currentPage) return
        currentPage = page
        pageChangeListener?.invoke(page)
    }
}
