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
        val widgetMap = widgets.associateBy { it.id }
        
        val hasEnabledWidgets = widgets.any { it.enabled }
        val emptyState = activity.findViewById<View>(com.guruswarupa.launch.R.id.widgets_empty_state)
        emptyState?.visibility = if (hasEnabledWidgets) View.GONE else View.VISIBLE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.media_controller_widget_container)?.visibility = 
            if (widgetMap["media_controller_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
            
        activity.findViewById<View>(com.guruswarupa.launch.R.id.calendar_events_widget_container)?.visibility = 
            if (widgetMap["calendar_events_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.countdown_widget_container)?.visibility = 
            if (widgetMap["countdown_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.dns_widget_container)?.visibility = 
            if (widgetMap["dns_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.note_widget_container)?.visibility = 
            if (widgetMap["note_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.battery_health_widget_container)?.visibility = 
            if (widgetMap["battery_health_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.physical_activity_widget_container)?.visibility = 
            if (widgetMap["physical_activity_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.compass_widget_container)?.visibility = 
            if (widgetMap["compass_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.pressure_widget_container)?.visibility = 
            if (widgetMap["pressure_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.temperature_widget_container)?.visibility = 
            if (widgetMap["temperature_widget_container"]?.enabled == true) View.VISIBLE else View.GONE

        activity.findViewById<View>(com.guruswarupa.launch.R.id.weather_forecast_widget_container)?.visibility =
            if (widgetMap["weather_forecast_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        activity.findViewById<View>(com.guruswarupa.launch.R.id.noise_decibel_widget_container)?.visibility = 
            if (widgetMap["noise_decibel_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        val workoutParent = activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.workout_widget_container)?.parent as? ViewGroup
        workoutParent?.visibility = if (widgetMap["workout_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        val calculatorParent = activity.findViewById<ViewGroup>(com.guruswarupa.launch.R.id.calculator_widget_container)?.parent as? ViewGroup
        calculatorParent?.visibility = if (widgetMap["calculator_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
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
        
        yearProgressWidget?.setGlobalVisibility(widgetMap["year_progress_widget_container"]?.enabled == true)
        githubContributionWidget?.setGlobalVisibility(widgetMap["github_contributions_widget_container"]?.enabled == true)
        
        val contentLayout = activity.findViewById<LinearLayout>(com.guruswarupa.launch.R.id.drawer_content_layout)
        contentLayout?.let { layout ->
            val viewMap = mutableMapOf<String, View>()
            widgets.forEach { widget ->
                val view = if (widget.isSystemWidget) {
                    val widgetId = widget.id.removePrefix("system_widget_").toIntOrNull()
                    if (widgetId != null) {
                        layout.findViewWithTag<View>(widgetId)
                    } else null
                } else {
                    when (widget.id) {
                        "media_controller_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.media_controller_widget_container)
                        "calendar_events_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.calendar_events_widget_container)
                        "countdown_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.countdown_widget_container)
                        "dns_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.dns_widget_container)
                        "note_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.note_widget_container)
                        "battery_health_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.battery_health_widget_container)
                        "physical_activity_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.physical_activity_widget_container)
                        "compass_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.compass_widget_container)
                        "pressure_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.pressure_widget_container)
                        "temperature_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.temperature_widget_container)
                        "weather_forecast_widget_container" -> activity.findViewById(com.guruswarupa.launch.R.id.weather_forecast_widget_container)
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
            
            val nonWidgetViews = mutableListOf<View>()
            for (i in 0 until layout.childCount) {
                val child = layout.getChildAt(i)
                if (!viewMap.values.contains(child)) {
                    nonWidgetViews.add(child)
                }
            }

            layout.removeAllViews()
            nonWidgetViews.forEach { layout.addView(it) }
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
