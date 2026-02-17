package com.guruswarupa.launch

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
 * Detects back tap gestures using the linear accelerometer sensor.
 * Optimized for "light movement" (taps) and ignores vigorous shakes or slow lifts.
 */
class BackTapDetector(
    context: Context,
    private val onBackTap: (Int) -> Unit
) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // Prefer Linear Acceleration sensor as it filters out gravity
    private var sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) 
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var tapThreshold = 6.0f 
    private var maxTapThreshold = 18.0f // If movement is more vigorous than this, it's likely a shake, not a tap
    
    private val tapTimeWindow = 450L // Window to complete a double tap
    private val minTimeBetweenTaps = 220L // Increased to prevent lift-to-wake or tilt triggers
    
    private var lastTapTime = 0L
    private var tapCount = 0
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastUpdateTime = 0L
    private var isListening = false
    private var isUsingLinearAcc = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION
    
    // For accelerometer fallback: track last values to calculate delta (jerk)
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    
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
        tapCount = 0
        isListening = false
    }
    
    fun updateSensitivity(sensitivity: Int) {
        // Map 1-10 to thresholds. 
        // Significantly increased base values to handle "too sensitive" feedback.
        // Sensitivity 1 (least) should be very hard to trigger.
        val baseMin = if (isUsingLinearAcc) 4.0f else 12.0f
        val baseMax = if (isUsingLinearAcc) 25.0f else 40.0f
        
        // sensitivity 10 (most sensitive) -> baseMin
        // sensitivity 1 (least sensitive) -> baseMax
        tapThreshold = baseMax - (sensitivity.coerceIn(1, 10) - 1) * ((baseMax - baseMin) / 9f)
        
        // Narrower window for taps: max threshold is lower to avoid overlapping with shake gestures
        // A shake is typically > 15-45. If maxTapThreshold is too high (e.g. 100), 
        // every shake move counts as a tap.
        maxTapThreshold = tapThreshold * 2.2f 
        
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
        
        val acceleration: Float
        val zAcceleration: Float
        
        if (isUsingLinearAcc) {
            acceleration = sqrt(x*x + y*y + z*z)
            zAcceleration = abs(z)
        } else {
            val dx = x - lastX
            val dy = y - lastY
            val dz = z - lastZ
            acceleration = sqrt(dx*dx + dy*dy + dz*dz)
            zAcceleration = abs(dz)
        }
        
        lastX = x
        lastY = y
        lastZ = z
        
        // Back taps should have a significant Z component (hitting the back of the phone)
        // We require at least 60% of the movement to be on the Z axis to filter out side-to-side lifts
        val isMostlyZ = zAcceleration > (acceleration * 0.6f)
        
        // Light movement detection: must be above tapThreshold but below maxTapThreshold (shake territory)
        if (acceleration > tapThreshold && acceleration < maxTapThreshold && isMostlyZ) {
            val now = System.currentTimeMillis()
            
            if (now - lastTapTime > minTimeBetweenTaps) {
                if (now - lastTapTime > tapTimeWindow) {
                    tapCount = 1
                } else {
                    tapCount++
                }
                
                if (tapCount >= 2) {
                    onBackTap(2)
                    tapCount = 0
                    // Longer post-trigger cooldown
                    lastTapTime = now + 300 
                } else {
                    lastTapTime = now
                }
            }
        } else if (acceleration >= maxTapThreshold) {
            // Movement is too vigorous, likely a shake. Reset tap counter immediately.
            tapCount = 0
        }
        
        lastUpdateTime = currentTime
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
