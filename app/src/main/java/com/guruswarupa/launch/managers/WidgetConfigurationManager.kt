package com.guruswarupa.launch.managers

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages widget visibility and ordering in the drawer
 */
class WidgetConfigurationManager(
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val PREF_WIDGET_ORDER = "widget_order"
        private const val PREFS_SYSTEM_WIDGETS_KEY = "saved_widgets"
        
        // Widget IDs that correspond to container IDs in the layout
        // All widgets are disabled by default - users can enable them from Widget Settings
        val IN_APP_WIDGETS = listOf(
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
            WidgetInfo("device_info_widget_container", "Device Info", false),
            WidgetInfo("year_progress_widget_container", "Year Progress", false)
        )
    }
    
    data class WidgetInfo(
        val id: String,
        val name: String,
        val enabled: Boolean,
        val isSystemWidget: Boolean = false
    )
    
    /**
     * Get the current widget order
     * Preserves saved order, and merges in any new widgets from IN_APP_WIDGETS and system widgets
     */
    fun getWidgetOrder(): List<WidgetInfo> {
        val orderJson = sharedPreferences.getString(PREF_WIDGET_ORDER, null)
        val savedWidgetsList = mutableListOf<WidgetInfo>()
        
        if (orderJson != null) {
            try {
                val jsonArray = JSONArray(orderJson)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val id = jsonObject.getString("id")
                    val name = jsonObject.getString("name")
                    val enabled = jsonObject.getBoolean("enabled")
                    val isSystem = jsonObject.optBoolean("isSystemWidget", false)
                    savedWidgetsList.add(WidgetInfo(id, name, enabled, isSystem))
                }
            } catch (_: Exception) {}
        }
        
        // Get current system widgets from their own storage to ensure consistency
        val systemWidgets = getSystemWidgets()
        val systemWidgetIds = systemWidgets.map { it.id }.toSet()
        
        // Filter out system widgets from saved list that no longer exist
        val result = savedWidgetsList.filter { !it.isSystemWidget || systemWidgetIds.contains(it.id) }.toMutableList()
        
        // Add new system widgets that aren't in the result list yet
        val currentIds = result.map { it.id }.toSet()
        systemWidgets.forEach { systemWidget ->
            if (!currentIds.contains(systemWidget.id)) {
                result.add(systemWidget)
            }
        }
        
        // Add any missing in-app widgets
        IN_APP_WIDGETS.forEach { defaultWidget ->
            if (!currentIds.contains(defaultWidget.id)) {
                result.add(defaultWidget)
            }
        }
        
        return result
    }
    
    /**
     * Fetches system widgets from SharedPreferences
     */
    private fun getSystemWidgets(): List<WidgetInfo> {
        val widgetsJson = sharedPreferences.getString(PREFS_SYSTEM_WIDGETS_KEY, null) ?: return emptyList()
        val systemWidgets = mutableListOf<WidgetInfo>()
        try {
            val jsonArray = JSONArray(widgetsJson)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val appWidgetId = json.getInt("appWidgetId")
                val packageName = json.optString("providerPackage", "System")
                // We use a prefix for system widgets to distinguish them
                val id = "system_widget_$appWidgetId"
                // The name should ideally be fetched from AppWidgetManager, but for now we'll use a placeholder
                // or the package name if available. WidgetConfigurationActivity can improve this.
                systemWidgets.add(WidgetInfo(id, "Widget ($packageName)", true, true))
            }
        } catch (_: Exception) {}
        return systemWidgets
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
            jsonObject.put("isSystemWidget", widget.isSystemWidget)
            jsonArray.put(jsonObject)
        }
        sharedPreferences.edit {
            putString(PREF_WIDGET_ORDER, jsonArray.toString())
        }
    }
    
    /**
     * Check if a widget is enabled
     */
    fun isWidgetEnabled(widgetId: String): Boolean {
        val widgets = getWidgetOrder()
        return widgets.find { it.id == widgetId }?.enabled ?: false
    }
}
