package com.guruswarupa.launch

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages widget visibility and ordering in the drawer
 */
class WidgetConfigurationManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val PREF_WIDGET_CONFIG = "widget_configuration"
        private const val PREF_WIDGET_ORDER = "widget_order"
        
        // Widget IDs that correspond to container IDs in the layout
        // All widgets are disabled by default - users can enable them from Widget Settings
        val ALL_WIDGETS = listOf(
            WidgetInfo("widgets_section", "Android Widgets", false),
            WidgetInfo("notifications_widget_container", "Notifications", false),
            WidgetInfo("calendar_events_widget_container", "Calendar Events", false),
            WidgetInfo("countdown_widget_container", "Countdown", false),
            WidgetInfo("physical_activity_widget_container", "Physical Activity", false),
            WidgetInfo("compass_widget_container", "Compass", false),
            WidgetInfo("pressure_widget_container", "Pressure", false),
            WidgetInfo("proximity_widget_container", "Proximity", false),
            WidgetInfo("temperature_widget_container", "Temperature", false),
            WidgetInfo("noise_decibel_widget_container", "Noise Decibel Analyzer", false),
            WidgetInfo("workout_widget_container", "Workout Tracker", false),
            WidgetInfo("calculator_widget_container", "Calculator", false),
            WidgetInfo("todo_recycler_view", "Todo List", false),
            WidgetInfo("finance_widget", "Finance Tracker", false),
            WidgetInfo("weekly_usage_widget", "Weekly Usage", false),
            WidgetInfo("network_stats_widget_container", "Network Stats", false),
            WidgetInfo("device_info_widget_container", "Device Info", false)
        )
    }
    
    data class WidgetInfo(
        val id: String,
        val name: String,
        val enabled: Boolean
    )
    
    /**
     * Get the current widget order
     * Preserves saved order, and merges in any new widgets from ALL_WIDGETS
     */
    fun getWidgetOrder(): List<WidgetInfo> {
        val orderJson = sharedPreferences.getString(PREF_WIDGET_ORDER, null)
        val savedWidgetsList = if (orderJson != null) {
            try {
                val jsonArray = JSONArray(orderJson)
                val widgets = mutableListOf<WidgetInfo>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    val name = jsonObject.getString("name")
                    val enabled = jsonObject.getBoolean("enabled")
                    widgets.add(WidgetInfo(id, name, enabled))
                }
                widgets
            } catch (e: Exception) {
                // If parsing fails, return empty list
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
        
        // If we have saved widgets, use them in the saved order
        if (savedWidgetsList.isNotEmpty()) {
            val savedWidgetIds = savedWidgetsList.map { it.id }.toSet()
            val savedWidgetsMap = savedWidgetsList.associateBy { it.id }
            
            // Add any new widgets from ALL_WIDGETS that aren't in saved list
            val result = savedWidgetsList.toMutableList()
            ALL_WIDGETS.forEach { defaultWidget ->
                if (!savedWidgetIds.contains(defaultWidget.id)) {
                    // New widget not in saved list, add it at the end
                    result.add(defaultWidget)
                }
            }
            
            return result
        }
        
        // No saved order, return default order
        return ALL_WIDGETS.toList()
    }
    
    /**
     * Save widget order and visibility
     */
    fun saveWidgetOrder(widgets: List<WidgetInfo>) {
        val jsonArray = JSONArray()
        widgets.forEach { widget ->
            val jsonObject = JSONObject()
            jsonObject.put("id", widget.id)
            jsonObject.put("name", widget.name)
            jsonObject.put("enabled", widget.enabled)
            jsonArray.put(jsonObject)
        }
        sharedPreferences.edit()
            .putString(PREF_WIDGET_ORDER, jsonArray.toString())
            .apply()
    }
    
    /**
     * Check if a widget is enabled
     */
    fun isWidgetEnabled(widgetId: String): Boolean {
        val widgets = getWidgetOrder()
        return widgets.find { it.id == widgetId }?.enabled ?: true
    }
    
    /**
     * Get widget name by ID
     */
    fun getWidgetName(widgetId: String): String {
        val widgets = getWidgetOrder()
        return widgets.find { it.id == widgetId }?.name ?: widgetId
    }
}
