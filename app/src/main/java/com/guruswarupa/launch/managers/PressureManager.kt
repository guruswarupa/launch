package com.guruswarupa.launch.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs
import kotlin.math.pow

class PressureManager(context: Context) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var pressureSensor: Sensor? = null
    private var isListening = false
    
    private var currentPressure: Float = 0f // in hPa (hectopascals)
    private var onPressureChanged: ((Float) -> Unit)? = null
    
    companion object {
        private const val TAG = "PressureManager"
        // For altitude calculation (sea level pressure is approximately 1013.25 hPa)
        private const val SEA_LEVEL_PRESSURE = 1013.25f
    }
    
    init {
        initializeSensor()
    }
    
    private fun initializeSensor() {
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        
        if (pressureSensor == null) {
            Log.w(TAG, "Pressure sensor not available on this device")
        }
    }
    
    fun hasPressureSensor(): Boolean {
        return pressureSensor != null
    }
    
    fun setOnPressureChangedListener(listener: (Float) -> Unit) {
        onPressureChanged = listener
    }
    
    fun startTracking() {
        if (!hasPressureSensor()) {
            Log.w(TAG, "Cannot start tracking: pressure sensor not available")
            return
        }
        
        if (isListening) {
            return
        }
        
        val success = sensorManager.registerListener(
            this,
            pressureSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        
        if (success) {
            isListening = true
        }
    }
    
    fun stopTracking() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return
        
        val pressure = event.values[0]
        
        if (abs(pressure - currentPressure) > 0.1f) { // Only update if change is significant
            currentPressure = pressure
            onPressureChanged?.invoke(currentPressure)
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not critical for pressure readings
    }
    
    fun getCurrentPressure(): Float {
        return currentPressure
    }
    
    /**
     * Calculate approximate altitude from pressure
     * Using barometric formula: h = 44330 * (1 - (P/P0)^0.1903)
     */
    fun getAltitude(): Float {
        if (currentPressure <= 0) return 0f
        val ratio = currentPressure / SEA_LEVEL_PRESSURE
        val power = ratio.toDouble().pow(0.1903).toFloat()
        return 44330f * (1f - power)
    }
    
    /**
     * Get pressure trend (rising, falling, stable)
     */
    fun getPressureTrend(previousPressure: Float): String {
        val diff = currentPressure - previousPressure
        return when {
            diff > 2f -> "Rising"
            diff < -2f -> "Falling"
            else -> "Stable"
        }
    }
}
