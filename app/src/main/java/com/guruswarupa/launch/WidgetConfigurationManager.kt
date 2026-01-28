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
            WidgetInfo("calendar_events_widget_container", "Calendar Events", false),
            WidgetInfo("physical_activity_widget_container", "Physical Activity", true),
            WidgetInfo("compass_widget_container", "Compass", false),
            WidgetInfo("pressure_widget_container", "Pressure", false),
            WidgetInfo("proximity_widget_container", "Proximity", false),
            WidgetInfo("temperature_widget_container", "Temperature", false),
            WidgetInfo("noise_decibel_widget_container", "Noise Decibel Analyzer", false),
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
     * Merges saved configuration with ALL_WIDGETS to ensure new widgets are included
     */
    fun getWidgetOrder(): List<WidgetInfo> {
        val orderJson = sharedPreferences.getString(PREF_WIDGET_ORDER, null)
        val savedWidgets = if (orderJson != null) {
            try {
                val jsonArray = JSONArray(orderJson)
                val widgets = mutableMapOf<String, WidgetInfo>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    val name = jsonObject.getString("name")
                    val enabled = jsonObject.getBoolean("enabled")
                    widgets[id] = WidgetInfo(id, name, enabled)
                }
                widgets
            } catch (e: Exception) {
                // If parsing fails, return empty map
                mutableMapOf()
            }
        } else {
            mutableMapOf()
        }
        
        // Merge saved widgets with ALL_WIDGETS
        // This ensures new widgets added to ALL_WIDGETS will appear even if not in saved config
        val mergedWidgets = mutableListOf<WidgetInfo>()
        for (defaultWidget in ALL_WIDGETS) {
            // Use saved widget if it exists, otherwise use default widget
            val widget = savedWidgets[defaultWidget.id] ?: defaultWidget
            mergedWidgets.add(widget)
        }
        
        // Also include any saved widgets that might not be in ALL_WIDGETS (for backward compatibility)
        for ((id, widget) in savedWidgets) {
            if (mergedWidgets.none { it.id == id }) {
                mergedWidgets.add(widget)
            }
        }
        
        return mergedWidgets
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
