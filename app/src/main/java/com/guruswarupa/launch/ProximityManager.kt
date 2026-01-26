package com.guruswarupa.launch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

class ProximityManager(private val context: Context) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var proximitySensor: Sensor? = null
    private var isListening = false
    
    private var currentDistance: Float = 0f // in cm
    private var maxRange: Float = 5f // Maximum range in cm
    private var isNear: Boolean = false
    private var onProximityChanged: ((Float, Boolean) -> Unit)? = null
    
    companion object {
        private const val TAG = "ProximityManager"
    }
    
    init {
        initializeSensor()
    }
    
    private fun initializeSensor() {
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        if (proximitySensor == null) {
            Log.w(TAG, "Proximity sensor not available on this device")
        } else {
            maxRange = proximitySensor!!.maximumRange
        }
    }
    
    fun hasProximitySensor(): Boolean {
        return proximitySensor != null
    }
    
    fun setOnProximityChangedListener(listener: (Float, Boolean) -> Unit) {
        onProximityChanged = listener
    }
    
    fun startTracking() {
        if (!hasProximitySensor()) {
            Log.w(TAG, "Cannot start tracking: proximity sensor not available")
            return
        }
        
        if (isListening) {
            return
        }
        
        val success = sensorManager.registerListener(
            this,
            proximitySensor,
            SensorManager.SENSOR_DELAY_UI
        )
        
        if (success) {
            isListening = true
        } else {
            Log.e(TAG, "Failed to register proximity sensor listener")
        }
    }
    
    fun stopTracking() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return
        
        val distance = event.values[0]
        val near = distance < maxRange / 2f // Consider "near" if less than half of max range
        
        if (abs(distance - currentDistance) > 0.1f || near != isNear) {
            currentDistance = distance
            isNear = near
            onProximityChanged?.invoke(currentDistance, isNear)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not critical for proximity readings
    }
    
    fun getCurrentDistance(): Float {
        return currentDistance
    }
    
    fun isNear(): Boolean {
        return isNear
    }
    
    fun getMaxRange(): Float {
        return maxRange
    }
}
