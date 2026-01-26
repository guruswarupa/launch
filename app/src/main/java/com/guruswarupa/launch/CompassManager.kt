package com.guruswarupa.launch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.*

class CompassManager(private val context: Context) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometerSensor: Sensor? = null
    private var magnetometerSensor: Sensor? = null
    private var isListening = false
    
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    
    private var currentAzimuth: Float = 0f
    private var onDirectionChanged: ((Float) -> Unit)? = null
    
    companion object {
        private const val TAG = "CompassManager"
    }
    
    init {
        initializeSensors()
    }
    
    private fun initializeSensors() {
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        if (accelerometerSensor == null || magnetometerSensor == null) {
            Log.w(TAG, "Required sensors not available for compass")
        }
    }
    
    fun hasRequiredSensors(): Boolean {
        return accelerometerSensor != null && magnetometerSensor != null
    }
    
    fun setOnDirectionChangedListener(listener: (Float) -> Unit) {
        onDirectionChanged = listener
    }
    
    fun startTracking() {
        if (!hasRequiredSensors()) {
            Log.w(TAG, "Cannot start tracking: sensors not available")
            return
        }
        
        if (isListening) {
            return
        }
        
        val success1 = sensorManager.registerListener(
            this,
            accelerometerSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        
        val success2 = sensorManager.registerListener(
            this,
            magnetometerSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        
        if (success1 && success2) {
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
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
        }
        
        updateOrientationAngles()
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not critical for compass functionality
    }
    
    private fun updateOrientationAngles() {
        // Get rotation matrix from accelerometer and magnetometer readings
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        // Get orientation angles (azimuth, pitch, roll)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        
        // Azimuth is the angle around the Z axis (0 to 2π)
        // Convert from radians to degrees and normalize to 0-360
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        val normalizedAzimuth = ((azimuth + 360) % 360)
        
        if (abs(normalizedAzimuth - currentAzimuth) > 1f) { // Only update if change is significant
            currentAzimuth = normalizedAzimuth
            onDirectionChanged?.invoke(currentAzimuth)
        }
    }
    
    fun getCurrentDirection(): Float {
        return currentAzimuth
    }
    
    fun getDirectionName(azimuth: Float): String {
        return when {
            azimuth >= 337.5 || azimuth < 22.5 -> "N"
            azimuth >= 22.5 && azimuth < 67.5 -> "NE"
            azimuth >= 67.5 && azimuth < 112.5 -> "E"
            azimuth >= 112.5 && azimuth < 157.5 -> "SE"
            azimuth >= 157.5 && azimuth < 202.5 -> "S"
            azimuth >= 202.5 && azimuth < 247.5 -> "SW"
            azimuth >= 247.5 && azimuth < 292.5 -> "W"
            azimuth >= 292.5 && azimuth < 337.5 -> "NW"
            else -> "N"
        }
    }
}
