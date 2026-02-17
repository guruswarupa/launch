package com.guruswarupa.launch

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects back tap gestures using the linear accelerometer sensor.
 * Optimized for "light movement" (taps) and ignores vigorous shakes.
 */
class BackTapDetector(
    context: Context,
    private val onBackTap: (Int) -> Unit
) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // Prefer Linear Acceleration sensor as it filters out gravity
    private var sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) 
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var tapThreshold = 3.0f 
    private var maxTapThreshold = 18.0f // If movement is more vigorous than this, it's likely a shake, not a tap
    
    private val tapTimeWindow = 500L // Shorter window for precise taps
    private val minTimeBetweenTaps = 80L 
    private val confirmationDelay = 300L
    
    private var lastTapTime = 0L
    private var tapCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var triggerActionRunnable: Runnable? = null
    
    private var lastUpdateTime = 0L
    private var isListening = false
    private var isUsingLinearAcc = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION
    
    companion object {
        private const val TAG = "BackTapDetector"
    }
    
    fun start() {
        if (isListening) return
        
        sensor?.let {
            // Using SENSOR_DELAY_FASTEST for low-latency tap detection
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            isListening = true
            Log.d(TAG, "Back tap detector started using ${if (isUsingLinearAcc) "Linear Acc" else "Accelerometer"}")
        }
    }
    
    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        triggerActionRunnable?.let { handler.removeCallbacks(it) }
        triggerActionRunnable = null
        tapCount = 0
        isListening = false
    }
    
    fun updateSensitivity(sensitivity: Int) {
        // Map 1-10 to low thresholds (higher sensitivity = lower threshold)
        // For linear acc, typical tap spikes are between 2.0 and 10.0
        val baseMin = if (isUsingLinearAcc) 1.5f else 5.0f
        val baseMax = if (isUsingLinearAcc) 8.0f else 15.0f
        
        // sensitivity 10 (most sensitive) -> baseMin
        // sensitivity 1 (least sensitive) -> baseMax
        tapThreshold = baseMax - (sensitivity.coerceIn(1, 10) - 1) * ((baseMax - baseMin) / 9f)
        
        // Also adjust max threshold to distinguish from vigorous shake
        maxTapThreshold = tapThreshold * 4.0f
        
        Log.d(TAG, "Sensitivity: $sensitivity, Tap range: $tapThreshold to $maxTapThreshold")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        // Skip processing if we are in a gesture cooldown period
        if (GestureCoordinator.isInCooldown()) {
            tapCount = 0
            return
        }
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 10) return // Sample at ~100Hz
        
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // Calculate magnitude of acceleration change
        val acceleration = sqrt(x*x + y*y + z*z)
        
        // Light movement detection: must be above tapThreshold but below maxTapThreshold (shake territory)
        if (acceleration > tapThreshold && acceleration < maxTapThreshold) {
            val now = System.currentTimeMillis()
            
            if (now - lastTapTime > minTimeBetweenTaps) {
                if (now - lastTapTime > tapTimeWindow) {
                    tapCount = 1
                } else {
                    tapCount++
                }
                
                triggerActionRunnable?.let { handler.removeCallbacks(it) }
                
                if (tapCount >= 2) {
                    onBackTap(2)
                    tapCount = 0
                }
                lastTapTime = now
            }
        } else if (acceleration >= maxTapThreshold) {
            // Movement is too vigorous, likely a shake. Reset tap counter to avoid misfires.
            tapCount = 0
        }
        
        lastUpdateTime = currentTime
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
