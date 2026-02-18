package com.guruswarupa.launch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.collection.LruCache
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages generation and caching of widget preview images
 */
class WidgetPreviewManager(private val context: Context) {
    
    companion object {
        private const val PREVIEW_WIDTH_DP = 200
        private const val PREVIEW_HEIGHT_DP = 200
        private const val CACHE_SIZE = 20 // Number of preview images to cache
    }
    
    private val previewCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024 // Size in KB
        }
    }
    
    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    
    /**
     * Generate preview for a widget
     */
    fun generatePreview(
        widgetId: String,
        widgetName: String,
        callback: (Bitmap?) -> Unit
    ) {
        // Check cache first
        val cachedPreview = previewCache.get(widgetId)
        if (cachedPreview != null) {
            callback(cachedPreview)
            return
        }
        
        // Generate preview in background
        backgroundExecutor.execute {
            try {
                val preview = createWidgetPreview(widgetId, widgetName)
                if (preview != null) {
                    previewCache.put(widgetId, preview)
                }
                // Post result to main thread
                (context as? android.app.Activity)?.runOnUiThread {
                    callback(preview)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? android.app.Activity)?.runOnUiThread {
                    callback(null)
                }
            }
        }
    }
    
    /**
     * Create preview bitmap for a specific widget
     */
    private fun createWidgetPreview(widgetId: String, widgetName: String): Bitmap? {
        return when (widgetId) {
            "calculator_widget_container" -> createCalculatorPreview()
            "compass_widget_container" -> createCompassPreview()
            "notifications_widget_container" -> createNotificationsPreview()
            "calendar_events_widget_container" -> createCalendarPreview()
            "countdown_widget_container" -> createCountdownPreview()
            "physical_activity_widget_container" -> createPhysicalActivityPreview()
            "pressure_widget_container" -> createPressurePreview()
            "proximity_widget_container" -> createProximityPreview()
            "temperature_widget_container" -> createTemperaturePreview()
            "noise_decibel_widget_container" -> createNoiseDecibelPreview()
            "workout_widget_container" -> createWorkoutPreview()
            "todo_recycler_view" -> createTodoPreview()
            "finance_widget" -> createFinancePreview()
            "weekly_usage_widget" -> createWeeklyUsagePreview()
            "network_stats_widget_container" -> createNetworkStatsPreview()
            "device_info_widget_container" -> createDeviceInfoPreview()
            else -> createDefaultPreview(widgetName)
        }
    }
    
    private fun createCalculatorPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_calculator_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createCompassPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_compass_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createNotificationsPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_notifications_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createCalendarPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_calendar_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createCountdownPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_countdown_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createPhysicalActivityPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_physical_activity_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createPressurePreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_pressure_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createProximityPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_proximity_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createTemperaturePreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_temperature_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createNoiseDecibelPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_noise_decibel_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createWorkoutPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_workout_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createTodoPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_todo_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createFinancePreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_finance_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createWeeklyUsagePreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_weekly_usage_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createNetworkStatsPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_network_stats_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createDeviceInfoPreview(): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_device_info_preview, null)
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun createDefaultPreview(widgetName: String): Bitmap? {
        return try {
            val inflater = LayoutInflater.from(context)
            val previewView = inflater.inflate(R.layout.widget_default_preview, null)
            
            // Set widget name
            val nameView = previewView.findViewById<android.widget.TextView>(R.id.preview_widget_name)
            nameView.text = widgetName
            
            measureAndLayoutView(previewView)
            viewToBitmap(previewView)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun measureAndLayoutView(view: View) {
        val width = dpToPx(PREVIEW_WIDTH_DP)
        val height = dpToPx(PREVIEW_HEIGHT_DP)
        
        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        
        view.measure(widthMeasureSpec, heightMeasureSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    
    private fun viewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * Clear the preview cache
     */
    fun clearCache() {
        previewCache.evictAll()
    }
    
    /**
     * Shutdown background executor
     */
    fun cleanup() {
        backgroundExecutor.shutdown()
    }
}