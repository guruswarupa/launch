package com.guruswarupa.launch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import java.text.DecimalFormat

class TemperatureWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private lateinit var temperatureManager: TemperatureManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var temperatureText: TextView
    private lateinit var fahrenheitText: TextView
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var widgetContainer: LinearLayout
    private lateinit var noSensorText: TextView
    private lateinit var widgetView: View
    
    companion object {
        private const val PREF_TEMPERATURE_ENABLED = "temperature_enabled"
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && temperatureManager.hasTemperatureSensor()) {
                updateDisplay()
            }
            handler.postDelayed(this, 2000) // Update every 2 seconds
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_temperature, container, false)
        container.addView(widgetView)
        
        temperatureText = widgetView.findViewById(R.id.temperature_text)
        fahrenheitText = widgetView.findViewById(R.id.fahrenheit_text)
        statusText = widgetView.findViewById(R.id.status_text)
        toggleButton = widgetView.findViewById(R.id.toggle_temperature_button)
        widgetContainer = widgetView.findViewById(R.id.temperature_container)
        noSensorText = widgetView.findViewById(R.id.no_sensor_text)
        
        temperatureManager = TemperatureManager(context)
        
        temperatureManager.setOnTemperatureChangedListener { temperature ->
            handler.post {
                updateTemperatureDisplay(temperature)
            }
        }
        
        toggleButton.setOnClickListener {
            toggleTemperature()
        }
        
        val isEnabled = sharedPreferences.getBoolean(PREF_TEMPERATURE_ENABLED, false)
        updateUI(isEnabled)
        
        isInitialized = true
    }
    
    private fun toggleTemperature() {
        val currentState = sharedPreferences.getBoolean(PREF_TEMPERATURE_ENABLED, false)
        val newState = !currentState
        sharedPreferences.edit { putBoolean(PREF_TEMPERATURE_ENABLED, newState) }
        updateUI(newState)
    }
    
    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {
            widgetContainer.visibility = View.VISIBLE
            toggleButton.text = context.getString(R.string.disable)
            
            if (temperatureManager.hasTemperatureSensor()) {
                setupWithSensor()
            } else {
                setupWithoutSensor()
            }
        } else {
            widgetContainer.visibility = View.GONE
            toggleButton.text = context.getString(R.string.enable)
            handler.removeCallbacks(updateRunnable)
            temperatureManager.stopTracking()
        }
    }
    
    private fun setupWithSensor() {
        noSensorText.visibility = View.GONE
        temperatureText.visibility = View.VISIBLE
        fahrenheitText.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        
        temperatureManager.startTracking()
        handler.post(updateRunnable)
        updateDisplay()
    }
    
    private fun setupWithoutSensor() {
        noSensorText.visibility = View.VISIBLE
        temperatureText.visibility = View.GONE
        fahrenheitText.visibility = View.GONE
        statusText.visibility = View.GONE
        noSensorText.text = context.getString(R.string.temperature_sensor_not_available)
    }
    
    private fun updateDisplay() {
        val temperature = temperatureManager.getCurrentTemperature()
        if (temperature != 0f) {
            updateTemperatureDisplay(temperature)
        }
    }
    
    private fun updateTemperatureDisplay(temperature: Float) {
        val df = DecimalFormat("#.#")
        temperatureText.text = context.getString(R.string.temperature_c_format, df.format(temperature))
        
        val fahrenheit = temperatureManager.getTemperatureInFahrenheit()
        fahrenheitText.text = context.getString(R.string.temperature_f_format, df.format(fahrenheit))
        
        val status = temperatureManager.getTemperatureStatus()
        statusText.text = context.getString(R.string.status_format, status)
    }
    
    fun onResume() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_TEMPERATURE_ENABLED, false)
            if (isEnabled && temperatureManager.hasTemperatureSensor()) {
                temperatureManager.startTracking()
                handler.post(updateRunnable)
                updateDisplay()
            }
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            temperatureManager.stopTracking()
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        if (isInitialized) {
            temperatureManager.stopTracking()
        }
    }
}
