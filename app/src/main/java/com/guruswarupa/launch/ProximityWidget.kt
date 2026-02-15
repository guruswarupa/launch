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

class ProximityWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private lateinit var proximityManager: ProximityManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var distanceText: TextView
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var widgetContainer: LinearLayout
    private lateinit var noSensorText: TextView
    private lateinit var widgetView: View
    
    companion object {
        private const val PREF_PROXIMITY_ENABLED = "proximity_enabled"
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && proximityManager.hasProximitySensor()) {
                updateDisplay()
            }
            handler.postDelayed(this, 500) // Update every 500ms for responsiveness
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_proximity, container, false)
        container.addView(widgetView)
        
        distanceText = widgetView.findViewById(R.id.distance_text)
        statusText = widgetView.findViewById(R.id.status_text)
        toggleButton = widgetView.findViewById(R.id.toggle_proximity_button)
        widgetContainer = widgetView.findViewById(R.id.proximity_container)
        noSensorText = widgetView.findViewById(R.id.no_sensor_text)
        
        proximityManager = ProximityManager(context)
        
        proximityManager.setOnProximityChangedListener { distance, isNear ->
            handler.post {
                updateProximityDisplay(distance, isNear)
            }
        }
        
        toggleButton.setOnClickListener {
            toggleProximity()
        }
        
        val isEnabled = sharedPreferences.getBoolean(PREF_PROXIMITY_ENABLED, false)
        updateUI(isEnabled)
        
        isInitialized = true
    }
    
    private fun toggleProximity() {
        val currentState = sharedPreferences.getBoolean(PREF_PROXIMITY_ENABLED, false)
        val newState = !currentState
        sharedPreferences.edit { putBoolean(PREF_PROXIMITY_ENABLED, newState) }
        updateUI(newState)
    }
    
    private fun updateUI(isEnabled: Boolean) {
        if (isEnabled) {
            widgetContainer.visibility = View.VISIBLE
            toggleButton.text = context.getString(R.string.disable)
            
            if (proximityManager.hasProximitySensor()) {
                setupWithSensor()
            } else {
                setupWithoutSensor()
            }
        } else {
            widgetContainer.visibility = View.GONE
            toggleButton.text = context.getString(R.string.enable)
            handler.removeCallbacks(updateRunnable)
            proximityManager.stopTracking()
        }
    }
    
    private fun setupWithSensor() {
        noSensorText.visibility = View.GONE
        distanceText.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        
        proximityManager.startTracking()
        handler.post(updateRunnable)
        updateDisplay()
    }
    
    private fun setupWithoutSensor() {
        noSensorText.visibility = View.VISIBLE
        distanceText.visibility = View.GONE
        statusText.visibility = View.GONE
        noSensorText.text = context.getString(R.string.proximity_sensor_not_available)
    }
    
    private fun updateDisplay() {
        val distance = proximityManager.getCurrentDistance()
        val isNear = proximityManager.isNear()
        updateProximityDisplay(distance, isNear)
    }
    
    private fun updateProximityDisplay(distance: Float, isNear: Boolean) {
        val df = DecimalFormat("#.##")
        distanceText.text = context.getString(R.string.distance_cm_format, df.format(distance))
        
        statusText.text = if (isNear) {
            context.getString(R.string.status_near)
        } else {
            context.getString(R.string.status_far)
        }
    }
    
    fun onResume() {
        if (isInitialized) {
            val isEnabled = sharedPreferences.getBoolean(PREF_PROXIMITY_ENABLED, false)
            if (isEnabled && proximityManager.hasProximitySensor()) {
                proximityManager.startTracking()
                handler.post(updateRunnable)
                updateDisplay()
            }
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
            proximityManager.stopTracking()
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        if (isInitialized) {
            proximityManager.stopTracking()
        }
    }
}
