package com.guruswarupa.launch.widgets

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.PressureManager
import java.text.DecimalFormat

class PressureWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private lateinit var pressureManager: PressureManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var pressureText: TextView
    private lateinit var altitudeText: TextView
    private lateinit var trendText: TextView
    private lateinit var toggleButton: Button
    private lateinit var widgetContainer: LinearLayout
    private lateinit var noSensorText: TextView
    private lateinit var widgetView: View
    
    private var previousPressure: Float = 0f
    
    companion object {
        private const val PREF_PRESSURE_ENABLED = "pressure_enabled"
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && pressureManager.hasPressureSensor()) {
                updateDisplay()
            }
            handler.postDelayed(this, 2000) // Update every 2 seconds
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_pressure, container, false)
        container.addView(widgetView)
        
        pressureText = widgetView.findViewById(R.id.pressure_text)
        altitudeText = widgetView.findViewById(R.id.altitude_text)
        trendText = widgetView.findViewById(R.id.trend_text)
        toggleButton = widgetView.findViewById(R.id.toggle_pressure_button)
        widgetContainer = widgetView.findViewById(R.id.pressure_container)
        noSensorText = widgetView.findViewById(R.id.no_sensor_text)
        
        pressureManager = PressureManager(context)
        
        pressureManager.setOnPressureChangedListener { pressure ->
            handler.post {
                updatePressureDisplay(pressure)
            }
        }
        
        toggleButton.setOnClickListener {
            togglePressure()
        }
        
        val isEnabled = sharedPreferences.getBoolean(PREF_PRESSURE_ENABLED, false)
        updateUI(isEnabled)
        
        isInitialized = true
    }
    
    private fun togglePressure() {
        val currentState = sharedPreferences.getBoolean(PREF_PRESSURE_ENABLED, false)
        val newState = !currentState
        sharedPreferences.edit { putBoolean(PREF_PRESSURE_ENABLED, newState) }
        updateUI(newState)
    }
    
    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {
            widgetContainer.visibility = View.VISIBLE
            toggleButton.text = context.getString(R.string.disable)
            
            if (pressureManager.hasPressureSensor()) {
                setupWithSensor()
            } else {
                setupWithoutSensor()
            }
        } else {
            widgetContainer.visibility = View.GONE
            toggleButton.text = context.getString(R.string.enable)
            handler.removeCallbacks(updateRunnable)
            pressureManager.stopTracking()
        }
    }
    
    private fun setupWithSensor() {
        noSensorText.visibility = View.GONE
        pressureText.visibility = View.VISIBLE
        altitudeText.visibility = View.VISIBLE
        trendText.visibility = View.VISIBLE
        
        pressureManager.startTracking()
        handler.post(updateRunnable)
        updateDisplay()
    }
    
    private fun setupWithoutSensor() {
        noSensorText.visibility = View.VISIBLE
        pressureText.visibility = View.GONE
        altitudeText.visibility = View.GONE
        trendText.visibility = View.GONE
        noSensorText.text = context.getString(R.string.pressure_sensor_not_available)
    }
    
    private fun updateDisplay() {
        val pressure = pressureManager.getCurrentPressure()
        if (pressure > 0) {
            updatePressureDisplay(pressure)
        }
    }
    
    private fun updatePressureDisplay(pressure: Float) {
        val df = DecimalFormat("#.##")
        pressureText.text = context.getString(R.string.pressure_format, df.format(pressure))
        
        val altitude = pressureManager.getAltitude()
        altitudeText.text = context.getString(R.string.altitude_format, df.format(altitude))
        
        if (previousPressure > 0) {
            val trend = pressureManager.getPressureTrend(previousPressure)
            trendText.text = context.getString(R.string.trend_format, trend)
        } else {
            trendText.text = context.getString(R.string.trend_format, context.getString(R.string.trend_none))
        }
        
        previousPressure = pressure
    }
    
    fun onResume() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_PRESSURE_ENABLED, false)
            if (isEnabled && pressureManager.hasPressureSensor()) {
                pressureManager.startTracking()
                handler.post(updateRunnable)
                updateDisplay()
            }
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            pressureManager.stopTracking()
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        if (isInitialized) {
            pressureManager.stopTracking()
        }
    }
}
