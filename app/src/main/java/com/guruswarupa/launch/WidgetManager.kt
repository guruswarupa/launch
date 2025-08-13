
package com.guruswarupa.launch

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class WidgetManager(private val context: Context, private val rootView: ViewGroup) {

    private val allWidgets = mutableListOf<View>()
    private val widgetVisibilityState = mutableMapOf<Int, Int>()
    private var isWidgetSectionOpen = false
    private var onWidgetSectionToggle: ((Boolean) -> Unit)? = null

    init {
        scanForAllWidgets(rootView)
        setupGestureDetection()
    }

    private fun setupGestureDetection() {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && isWidgetSectionOpen) {
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y

                    // Detect swipe left or up with sufficient velocity
                    if ((abs(deltaX) > abs(deltaY) && deltaX < -100 && abs(velocityX) > 100) ||
                        (abs(deltaY) > abs(deltaX) && deltaY < -100 && abs(velocityY) > 100)) {
                        closeWidgetSection()
                        return true
                    }
                }
                return false
            }
        })

        rootView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    fun setOnWidgetSectionToggleListener(listener: (Boolean) -> Unit) {
        onWidgetSectionToggle = listener
    }

    fun openWidgetSection() {
        isWidgetSectionOpen = true
        showAllWidgets()
        onWidgetSectionToggle?.invoke(true)
    }

    fun closeWidgetSection() {
        isWidgetSectionOpen = false
        hideAllWidgets()
        onWidgetSectionToggle?.invoke(false)
    }

    fun toggleWidgetSection() {
        if (isWidgetSectionOpen) {
            closeWidgetSection()
        } else {
            openWidgetSection()
        }
    }

    private fun scanForAllWidgets(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)

            // Add all widget types to our list
            when (child) {
                is TextView, is ImageView, is Button, is ImageButton,
                is EditText, is CheckBox, is RadioButton, is Switch,
                is ProgressBar, is SeekBar, is RatingBar,
                is RecyclerView, is ListView, is GridView,
                is CardView, is LinearLayout, is RelativeLayout,
                is FrameLayout, is ScrollView -> {
                    if (isWidget(child)) {
                        allWidgets.add(child)
                        widgetVisibilityState[child.id] = child.visibility
                    }
                }
            }

            // Recursively scan child ViewGroups
            if (child is ViewGroup) {
                scanForAllWidgets(child)
            }
        }
    }

    private fun isWidget(view: View): Boolean {
        // Determine if a view is a widget based on ID naming or other criteria
        val resourceName = try {
            context.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            null
        }

        return resourceName?.contains("widget") == true ||
                resourceName?.contains("todo") == true ||
                resourceName?.contains("weather") == true ||
                resourceName?.contains("battery") == true ||
                resourceName?.contains("finance") == true ||
                resourceName?.contains("usage") == true ||
                resourceName?.contains("time") == true ||
                resourceName?.contains("date") == true ||
                view.tag?.toString()?.contains("widget") == true
    }

    fun hideAllWidgets() {
        allWidgets.forEach { widget ->
            widgetVisibilityState[widget.id] = widget.visibility
            widget.visibility = View.GONE
        }
        isWidgetSectionOpen = false
    }

    fun showAllWidgets() {
        allWidgets.forEach { widget ->
            val previousVisibility = widgetVisibilityState[widget.id] ?: View.VISIBLE
            widget.visibility = previousVisibility
        }
        isWidgetSectionOpen = true
    }

    fun hideSpecificWidgets(widgetIds: List<Int>) {
        widgetIds.forEach { id ->
            rootView.findViewById<View>(id)?.let { widget ->
                widgetVisibilityState[id] = widget.visibility
                widget.visibility = View.GONE
            }
        }
    }

    fun showSpecificWidgets(widgetIds: List<Int>) {
        widgetIds.forEach { id ->
            rootView.findViewById<View>(id)?.let { widget ->
                val previousVisibility = widgetVisibilityState[id] ?: View.VISIBLE
                widget.visibility = previousVisibility
            }
        }
    }

    fun refreshWidgetList() {
        allWidgets.clear()
        widgetVisibilityState.clear()
        scanForAllWidgets(rootView)
    }

    fun getWidgetCount(): Int = allWidgets.size

    fun getAllWidgets(): List<View> = allWidgets.toList()

    fun isWidgetSectionOpen(): Boolean = isWidgetSectionOpen

    fun addWidget(widget: View) {
        if (!allWidgets.contains(widget)) {
            allWidgets.add(widget)
            widgetVisibilityState[widget.id] = widget.visibility
        }
    }

    fun removeWidget(widget: View) {
        allWidgets.remove(widget)
        widgetVisibilityState.remove(widget.id)
    }
}
