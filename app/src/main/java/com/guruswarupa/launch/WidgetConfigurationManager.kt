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
        val ALL_WIDGETS = listOf(
            WidgetInfo("widgets_section", "Android Widgets", true),
            WidgetInfo("notifications_widget_container", "Notifications", true),
            WidgetInfo("physical_activity_widget_container", "Physical Activity", true),
            WidgetInfo("compass_widget_container", "Compass", true),
            WidgetInfo("pressure_widget_container", "Pressure", true),
            WidgetInfo("proximity_widget_container", "Proximity", true),
            WidgetInfo("temperature_widget_container", "Temperature", true),
            WidgetInfo("workout_widget_container", "Workout Tracker", true),
            WidgetInfo("calculator_widget_container", "Calculator", true),
            WidgetInfo("todo_recycler_view", "Todo List", true),
            WidgetInfo("finance_widget", "Finance Tracker", true),
            WidgetInfo("weekly_usage_widget", "Weekly Usage", true)
        )
    }
    
    data class WidgetInfo(
        val id: String,
        val name: String,
        val enabled: Boolean
    )
    
    /**
     * Get the current widget order
     */
    fun getWidgetOrder(): List<WidgetInfo> {
        val orderJson = sharedPreferences.getString(PREF_WIDGET_ORDER, null)
        if (orderJson != null) {
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
                return widgets
            } catch (e: Exception) {
                // If parsing fails, return default order
            }
        }
        // Return default order if no saved configuration
        return ALL_WIDGETS
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
