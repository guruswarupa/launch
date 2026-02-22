package com.guruswarupa.launch.handlers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects shake gestures using the accelerometer sensor.
 * Supports triple shake detection with configurable thresholds.
 * Optimized for "vigorous shake" to avoid accidental triggers.
 */
class ShakeDetector(
    context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // Shake detection parameters - higher thresholds for "vigorous" shake
    private var shakeThreshold = 25.0f // Default vigorous threshold
    private val shakeTimeWindow = 1000L // Slightly longer window for vigorous shakes
    private val minTimeBetweenShakes = 150L 
    
    private var lastShakeTime = 0L
    private var shakeCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var resetShakeCountRunnable: Runnable? = null
    
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdateTime = 0L
    
    private var isListening = false
    
    /**
     * Starts listening for shake gestures
     */
    fun start() {
        if (isListening) return
        
        accelerometer?.let {
            // Using SENSOR_DELAY_UI for better battery life while still detecting vigorous movement
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            isListening = true
        }
    }
    
    /**
     * Updates shake sensitivity (1-10, where 10 is most sensitive)
     */
    fun updateSensitivity(sensitivity: Int) {
        // Map 1-10 to threshold 45 - 15 (higher threshold = less sensitive/more vigorous)
        // 1 (least sensitive) -> 45.0f (extremely vigorous)
        // 10 (most sensitive) -> 15.0f (moderate shake)
        shakeThreshold = 45f - (sensitivity.coerceIn(1, 10) - 1) * 3.33f
        Log.d("ShakeDetector", "Updated sensitivity to $sensitivity, vigorous threshold set to $shakeThreshold")
    }
    
    /**
     * Stops listening for shake gestures
     */
    fun stop() {
        if (!isListening) return
        
        sensorManager.unregisterListener(this)
        isListening = false
        shakeCount = 0
        resetShakeCountRunnable?.let { handler.removeCallbacks(it) }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        
        // Skip processing if we are in a gesture cooldown period
        if (GestureCoordinator.isInCooldown()) {
            shakeCount = 0
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        if (lastUpdateTime == 0L) {
            lastX = x
            lastY = y
            lastZ = z
            lastUpdateTime = currentTime
            return
        }
        
        val timeDelta = currentTime - lastUpdateTime
        // SENSOR_DELAY_UI samples at ~16Hz, so this throttle is mostly for safety
        if (timeDelta < 20) return 
        
        val deltaX = abs(x - lastX)
        val deltaY = abs(y - lastY)
        val deltaZ = abs(z - lastZ)
        
        // Calculate total acceleration change (jerk)
        val acceleration = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()
        
        if (acceleration > shakeThreshold) {
            val timeSinceLastShake = currentTime - lastShakeTime
            
            // Check if enough time has passed since last shake
            if (timeSinceLastShake > minTimeBetweenShakes) {
                shakeCount++
                lastShakeTime = currentTime
                
                // Cancel previous reset runnable
                resetShakeCountRunnable?.let { handler.removeCallbacks(it) }
                
                if (shakeCount >= 3) {
                    // Triple vigorous shake detected!
                    onShakeDetected()
                    shakeCount = 0
                } else {
                    // Reset shake count after time window if no third shake
                    resetShakeCountRunnable = Runnable {
                        shakeCount = 0
                    }
                    handler.postDelayed(resetShakeCountRunnable!!, shakeTimeWindow)
                }
            }
        }
        
        lastX = x
        lastY = y
        lastZ = z
        lastUpdateTime = currentTime
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    fun cleanup() {
        stop()
    }
}
