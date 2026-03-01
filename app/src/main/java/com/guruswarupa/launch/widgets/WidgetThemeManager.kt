package com.guruswarupa.launch.widgets

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.R
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.managers.AppDockManager

class WidgetThemeManager(
    private val activity: Activity,
    private val getCurrentUiMode: () -> Int
) {
    private var currentUiMode: Int = 0
    
    fun apply(
        searchBox: EditText? = null,
        searchContainer: View? = null,
        voiceSearchButton: ImageButton? = null,
        searchTypeButton: ImageButton? = null,
        appDockManager: AppDockManager? = null
    ) {
        // Check if we're in night mode (dark theme)
        val isNightMode = (activity.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Save current UI mode to detect changes
        currentUiMode = activity.resources.configuration.uiMode
        
        // Select appropriate background drawables
        val widgetBackground = if (isNightMode) {
            R.drawable.widget_background_dark // Semi-transparent black
        } else {
            R.drawable.widget_background // Semi-transparent white
        }
        
        // Apply backgrounds to all widget containers
        activity.findViewById<View>(R.id.top_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.notifications_widget_container)?.parent?.let { parent ->
            if (parent is View) parent.setBackgroundResource(widgetBackground)
        }
        activity.findViewById<View>(R.id.calendar_events_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.countdown_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.physical_activity_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.compass_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.pressure_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.proximity_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.temperature_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.network_stats_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.device_info_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.noise_decibel_widget_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.workout_widget_container)?.parent?.let { parent ->
            if (parent is View) parent.setBackgroundResource(widgetBackground)
        }
        activity.findViewById<View>(R.id.calculator_widget_container)?.parent?.let { parent ->
            if (parent is View) parent.setBackgroundResource(widgetBackground)
        }
        activity.findViewById<View>(R.id.todo_widget_main_container)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.finance_widget)?.setBackgroundResource(widgetBackground)
        activity.findViewById<View>(R.id.weekly_usage_widget)?.setBackgroundResource(widgetBackground)
        
        // Apply theme to search box
        searchBox?.let { sb ->
            val searchBg = if (isNightMode) R.drawable.search_box_transparent_bg else R.drawable.search_box_light_bg
            val textColor = ContextCompat.getColor(activity, if (isNightMode) R.color.white else R.color.black)
            val hintColor = ContextCompat.getColor(activity, if (isNightMode) R.color.gray_light else R.color.gray)
            
            // Apply background to search container if initialized, otherwise to searchBox
            searchContainer?.let { sc ->
                sc.setBackgroundResource(searchBg)
                sb.background = null // Keep EditText background transparent
            } ?: run {
                sb.setBackgroundResource(searchBg)
            }
            
            sb.setTextColor(textColor)
            sb.setHintTextColor(hintColor)
            
            // Tint search icons
            val iconColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            sb.compoundDrawablesRelative[0]?.setTint(iconColor)
            voiceSearchButton?.setColorFilter(iconColor)
            searchTypeButton?.setColorFilter(iconColor)
        }
        
        // Update dock icons to match current theme
        appDockManager?.updateDockIcons()
    }
    
    /**
     * Checks if the UI mode has changed and updates widget backgrounds if needed.
     */
    fun checkAndUpdateThemeIfNeeded(
        todoManager: TodoManager? = null, 
        appDockManager: AppDockManager? = null,
        searchBox: EditText? = null,
        searchContainer: View? = null,
        voiceSearchButton: ImageButton? = null,
        searchTypeButton: ImageButton? = null
    ) {
        val newUiMode = activity.resources.configuration.uiMode
        if (newUiMode != currentUiMode) {
            currentUiMode = newUiMode
            apply(
                searchBox = searchBox,
                searchContainer = searchContainer,
                voiceSearchButton = voiceSearchButton,
                searchTypeButton = searchTypeButton,
                appDockManager = appDockManager
            )
            // Notify todo manager of theme change
            todoManager?.onThemeChanged()
        }
    }
}