package com.guruswarupa.launch.widgets

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.guruswarupa.launch.managers.WidgetConfigurationManager

class WidgetVisibilityManager(
    private val activity: Activity,
    private val widgetConfigurationManager: WidgetConfigurationManager
) {
    fun update(
        yearProgressWidget: YearProgressWidget? = null,
        githubContributionWidget: GithubContributionWidget? = null
    ) {
        val widgets = widgetConfigurationManager.getWidgetOrder()
        
        // Create a map for quick lookup
        val widgetMap = widgets.associateBy { it.id }
        
        // Check if any widgets are enabled
        val hasEnabledWidgets = widgets.any { it.enabled }
        val emptyState = activity.findViewById<View>(com.guruswarupa.launch.R.id.widgets_empty_state)
        emptyState?.visibility = if (hasEnabledWidgets) View.GONE else View.VISIBLE
        
        // Notifications widget - the parent LinearLayout contains the container
        val notificationsParent = activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.notifications_widget_container)?.parent as? ViewGroup
        notificationsParent?.visibility = if (widgetMap["notifications_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Calendar Events widget
        activity.findViewById<View>(com.guruswarupa.launch.R.id.calendar_events_widget_container)?.visibility = 
            if (widgetMap["calendar_events_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Countdown widget
        activity.findViewById<View>(com.guruswarupa.launch.R.id.countdown_widget_container)?.visibility = 
            if (widgetMap["countdown_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.physical_activity_widget_container)?.visibility = 
            if (widgetMap["physical_activity_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.compass_widget_container)?.visibility = 
            if (widgetMap["compass_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.pressure_widget_container)?.visibility = 
            if (widgetMap["pressure_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.proximity_widget_container)?.visibility =
            if (widgetMap["proximity_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.temperature_widget_container)?.visibility = 
            if (widgetMap["temperature_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.noise_decibel_widget_container)?.visibility = 
            if (widgetMap["noise_decibel_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Workout widget - the parent LinearLayout contains the container
        val workoutParent = activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.workout_widget_container)?.parent as? ViewGroup
        workoutParent?.visibility = if (widgetMap["workout_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Calculator widget - the parent LinearLayout contains the container
        val calculatorParent = activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.calculator_widget_container)?.parent as? ViewGroup
        calculatorParent?.visibility = if (widgetMap["calculator_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Todo widget - the parent LinearLayout contains the RecyclerView
        val todoParent = activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.todo_recycler_view)?.parent as? ViewGroup
        todoParent?.visibility = if (widgetMap["todo_recycler_view"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.finance_widget)?.visibility = 
            if (widgetMap["finance_widget"]?.enabled == true) View.VISIBLE else View.GONE
            
        activity.findViewById<View>(com.guruswarupa.launch.R.id.network_stats_widget_container)?.visibility = 
            if (widgetMap["network_stats_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
            
        activity.findViewById<View>(com.guruswarupa.launch.R.id.device_info_widget_container)?.visibility = 
            if (widgetMap["device_info_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.weekly_usage_widget)?.visibility = 
            if (widgetMap["weekly_usage_widget"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.github_contributions_widget_container)?.visibility = 
            if (widgetMap["github_contributions_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Control YearProgressWidget visibility through its dedicated method
        yearProgressWidget?.setGlobalVisibility(widgetMap["year_progress_widget_container"]?.enabled == true)
        
        // Control GithubContributionWidget visibility through its dedicated method
        githubContributionWidget?.setGlobalVisibility(widgetMap["github_contributions_widget_container"]?.enabled == true)
        
        // Reorder widgets - get the parent LinearLayout that contains all widgets
        val contentLayout = activity.findViewById<LinearLayout>(com.guruswarupa.launch.R.id.drawer_content_layout)
        
        contentLayout?.let { layout ->
            // Store all views with their widget IDs
            val viewMap = mutableMapOf<String, View>()
            
            widgets.forEach { widget ->
                val view = if (widget.isSystemWidget) {
                    // System widgets are dynamically added to the bottom of the drawer
                    // We need to find them by tag in the layout
                    val widgetId = widget.id.removePrefix("system_widget_").toIntOrNull()
                    if (widgetId != null) {
                        layout.findViewWithTag<View>(widgetId)
                    } else null
                } else {
                    when (widget.id) {
                        "notifications_widget_container" -> activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.notifications_widget_container)?.parent as? View
                        "calendar_events_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.calendar_events_widget_container)
                        "countdown_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.countdown_widget_container)
                        "physical_activity_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.physical_activity_widget_container)
                        "compass_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.compass_widget_container)
                        "pressure_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.pressure_widget_container)
                        "proximity_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.proximity_widget_container)
                        "temperature_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.temperature_widget_container)
                        "noise_decibel_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.noise_decibel_widget_container)
                        "workout_widget_container" -> activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.workout_widget_container)?.parent as? View
                        "calculator_widget_container" -> activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.calculator_widget_container)?.parent as? View
                        "todo_recycler_view" -> activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.todo_recycler_view)?.parent as? View
                        "finance_widget" -> activity.findViewById(com.guruswarupa.launch.R.id.finance_widget)
                        "weekly_usage_widget" -> activity.findViewById(com.guruswarupa.launch.R.id.weekly_usage_widget)
                        "github_contributions_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.github_contributions_widget_container)
                        "network_stats_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.network_stats_widget_container)
                        "device_info_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.device_info_widget_container)
                        "year_progress_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.year_progress_widget_container)
                        else -> null
                    }
                }
                view?.let { viewMap[widget.id] = it }
            }
            
            // Collect other views that aren't managed widgets (like headers, empty state, add button)
            val nonWidgetViews = mutableListOf<View>()
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (!viewMap.values.contains(child)) {
                    nonWidgetViews.add(child)
                }
            }

            // Remove all views
            layout.removeAllViews()
            
            // Add back non-widget views first (keeping them at the top)
            nonWidgetViews.forEach { layout.addView(it) }
            
            // Add widget views back in the exact configured order
            widgets.forEach { widget ->
                viewMap[widget.id]?.let { view ->
                    view.visibility = if (widget.enabled) View.VISIBLE else View.GONE

                    if (view.parent == null) {
                        layout.addView(view)
                    }
                }
            }
        }
    }
}