package com.guruswarupa.launch.widgets

import android.content.Context
import com.guruswarupa.launch.R

class YearProgressWidget(
    private val context: Context,
    private val container: android.widget.LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private lateinit var yearProgressView: YearProgressView
    private lateinit var progressStatsText: android.widget.TextView
    private lateinit var daysRemainingText: android.widget.TextView
    private lateinit var yearProgressContainer: android.widget.LinearLayout
    private lateinit var widgetView: android.view.View
    
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isInitialized = false
    
    fun initialize() {
        if (isInitialized) return
        
        // Inflate the widget layout
        val inflater = android.view.LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_year_progress, container, false)
        container.addView(widgetView)
        
        // Initialize views
        yearProgressView = widgetView.findViewById(R.id.year_progress_view)
        progressStatsText = widgetView.findViewById(R.id.progress_stats_text)
        daysRemainingText = widgetView.findViewById(R.id.days_remaining_text)
        yearProgressContainer = widgetView.findViewById(R.id.year_progress_container)
        
        // Update progress info initially
        updateProgressInfo()
        
        // Start periodic updates
        startPeriodicUpdates()
        
        isInitialized = true
    }
    
    private fun updateProgressInfo() {
        val progressInfo = yearProgressView.getProgressInfo()
        progressStatsText.text = context.getString(
            R.string.year_progress_stats_format,
            progressInfo.daysCompleted,
            progressInfo.totalDays,
            progressInfo.percentage
        )
        daysRemainingText.text = context.getString(
            R.string.year_progress_days_remaining_format,
            progressInfo.daysRemaining
        )
    }
    
    private fun startPeriodicUpdates() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (isInitialized) {
                    yearProgressView.refresh()
                    updateProgressInfo()
                }
                handler.postDelayed(this, 3600000) // Update every hour
            }
        }
        handler.post(updateRunnable)
    }
    
    fun onResume() {
        if (isInitialized) {
            yearProgressView.refresh()
            updateProgressInfo()
        }
    }
    
    fun onPause() {
        // No specific cleanup needed
    }
    
    fun cleanup() {
        // No specific cleanup needed
    }
    
    fun setGlobalVisibility(visible: Boolean) {
        if (visible) {
            widgetView.visibility = android.view.View.VISIBLE
            // Ensure the content container is visible
            yearProgressContainer.visibility = android.view.View.VISIBLE
            yearProgressView.refresh()
            updateProgressInfo()
        } else {
            widgetView.visibility = android.view.View.GONE
        }
    }
}