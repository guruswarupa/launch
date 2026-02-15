package com.guruswarupa.launch

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

data class ActivityData(
    val steps: Int,
    val distanceKm: Double,
    val date: String
)

data class HourlyActivityData(
    val hour: Int,
    val steps: Int,
    val distanceKm: Double
)

class PhysicalActivityManager(private val context: Context) : SensorEventListener {
    
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val prefs: SharedPreferences = context.getSharedPreferences("physical_activity_prefs", Context.MODE_PRIVATE)
    
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var isListening = false
    
    // Step counter values
    private var lastStepCount = 0
    private var todayStepCount = 0
    private var lastResetDate = ""
    
    // Batch saving to reduce I/O operations
    private var lastSavedStepCount = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    
    // Default step length in meters (approximately 0.75m for average adult)
    
    // Cached formatters and calendar for efficiency
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val calendar = Calendar.getInstance()
    
    // Cache for historical data to avoid repeated parsing
    private var historicalDataCache: MutableMap<String, ActivityData>? = null
    private var hourlyDataCache: MutableMap<String, String>? = null
    
    // Store today's distance to avoid recalculation
    private var todayDistanceKm: Double = 0.0
    
    // Hourly tracking
    private var currentHour = -1
    private var hourlySteps: MutableMap<Int, Int> = mutableMapOf() // hour -> steps
    private var hourlyDistances: MutableMap<Int, Double> = mutableMapOf() // hour -> distanceKm
    
    companion object {
        private const val TAG = "PhysicalActivityManager"
        private const val PREF_LAST_STEP_COUNT = "last_step_count"
        private const val PREF_TODAY_STEP_COUNT = "today_step_count"
        private const val PREF_LAST_RESET_DATE = "last_reset_date"
        private const val PREF_TOTAL_STEPS_BASE = "total_steps_base"
        private const val PREF_HISTORICAL_DATA = "historical_activity_data"
        private const val PREF_HOURLY_DATA = "hourly_activity_data"
        private const val PREF_STRIDE_LENGTH_METERS = "stride_length_meters"
        private const val PREF_USER_HEIGHT_CM = "user_height_cm"
        
        private const val SAVE_INTERVAL_MS = 2 * 60 * 1000L // Save every 2 minutes
        private const val MIN_STEPS_FOR_SAVE = 10 // Only save if at least 10 steps changed
        private const val DEFAULT_STEP_LENGTH_METERS = 0.75
    }
    
    init {
        initializeSensors()
        // Data loading moved to initializeAsync to avoid blocking main thread
    }
    
    /**
     * Initializes data and starts tracking asynchronously.
     * This avoids blocking the main thread during service startup.
     */
    fun initializeAsync(onComplete: (() -> Unit)? = null) {
        Thread {
            try {
                loadSavedData()
                handler.post {
                    if (hasActivityRecognitionPermission()) {
                        startTracking()
                    }
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during async initialization", e)
            }
        }.start()
    }
    
    private fun initializeSensors() {
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        
        if (stepCounterSensor == null && stepDetectorSensor == null) {
            Log.w(TAG, "No step sensors available on this device")
        }
    }
    
    private fun loadSavedData() {
        lastStepCount = prefs.getInt(PREF_LAST_STEP_COUNT, 0)
        todayStepCount = prefs.getInt(PREF_TODAY_STEP_COUNT, 0)
        lastResetDate = prefs.getString(PREF_LAST_RESET_DATE, "") ?: ""
        
        // Check if we need to reset for a new day
        val today = getCurrentDate()
        if (lastResetDate != today) {
            resetDailyCount()
        } else {
            // Load hourly data for today (this will also set todayDistanceKm)
            loadHourlyData(today)
            // If distance not loaded from hourly data, calculate it
            if (todayDistanceKm == 0.0 && todayStepCount > 0) {
                val strideLength = getStrideLengthMeters()
                todayDistanceKm = (todayStepCount * strideLength) / 1000.0
            }
        }
    }
    
    fun getCurrentDate(): String {
        return dateFormatter.format(Date())
    }
    
    /**
     * Get the user's calibrated stride length in meters, or default if not set
     */
    fun getStrideLengthMeters(): Double {
        val stored = prefs.getFloat(PREF_STRIDE_LENGTH_METERS, -1f)
        if (stored > 0) {
            return stored.toDouble()
        }
        // If height is set, calculate stride from height
        val heightCm = prefs.getInt(PREF_USER_HEIGHT_CM, -1)
        if (heightCm > 0) {
            // Stride length ≈ height * 0.43 for walking
            return (heightCm * 0.43) / 100.0
        }
        return DEFAULT_STEP_LENGTH_METERS
    }
    
    /**
     * Set the user's stride length in meters
     */
    fun setStrideLengthMeters(meters: Double) {
        prefs.edit { putFloat(PREF_STRIDE_LENGTH_METERS, meters.toFloat()) }
        // Recalculate today's distance with new stride length
        val strideLength = getStrideLengthMeters()
        todayDistanceKm = (todayStepCount * strideLength) / 1000.0
        // Update hourly distances
        hourlyDistances.clear()
        hourlySteps.forEach { (hour, steps) ->
            hourlyDistances[hour] = (steps * strideLength) / 1000.0
        }
    }
    
    /**
     * Set the user's height in cm and calculate stride length automatically
     */
    fun setUserHeightCm(heightCm: Int) {
        prefs.edit { putInt(PREF_USER_HEIGHT_CM, heightCm) }
        // Calculate stride from height: stride ≈ height * 0.43
        val calculatedStride = (heightCm * 0.43) / 100.0
        setStrideLengthMeters(calculatedStride)
    }
    
    /**
     * Get the user's height in cm, or -1 if not set
     */
    fun getUserHeightCm(): Int {
        return prefs.getInt(PREF_USER_HEIGHT_CM, -1)
    }
    
    /**
     * Reset stride length to default
     */
    fun resetStrideLengthToDefault() {
        prefs.edit { 
            remove(PREF_STRIDE_LENGTH_METERS)
            remove(PREF_USER_HEIGHT_CM)
        }
        val strideLength = getStrideLengthMeters()
        todayDistanceKm = (todayStepCount * strideLength) / 1000.0
        hourlyDistances.clear()
        hourlySteps.forEach { (hour, steps) ->
            hourlyDistances[hour] = (steps * strideLength) / 1000.0
        }
    }
    
    private fun resetDailyCount() {
        val today = getCurrentDate()
        
        // Save yesterday's data before resetting
        if (lastResetDate.isNotEmpty() && lastResetDate != today && todayStepCount > 0) {
            saveHistoricalData(lastResetDate, todayStepCount, todayDistanceKm)
            // Save yesterday's hourly data
            saveHourlyData(lastResetDate, hourlySteps, hourlyDistances)
        }
        
        lastResetDate = today
        todayStepCount = 0
        todayDistanceKm = 0.0
        lastStepCount = 0
        hourlySteps.clear()
        hourlyDistances.clear()
        currentHour = -1
        
        // Batch all writes together
        prefs.edit {
            putString(PREF_LAST_RESET_DATE, today)
            putInt(PREF_TODAY_STEP_COUNT, 0)
            putInt(PREF_LAST_STEP_COUNT, 0)
        }
        
        // Invalidate cache
        historicalDataCache = null
        hourlyDataCache = null
    }
    
    private fun saveHistoricalData(date: String, steps: Int, distanceKm: Double) {
        // Get cached data or load if needed
        val historicalData = historicalDataCache ?: getHistoricalData()
        
        // Update only the specific date (incremental update)
        historicalData[date] = ActivityData(steps, distanceKm, date)
        historicalDataCache = historicalData
        
        // Load existing JSON and update only the changed date
        val jsonString = prefs.getString(PREF_HISTORICAL_DATA, null)
        val json = if (jsonString != null) {
            try {
                org.json.JSONObject(jsonString)
            } catch (_: Exception) {
                org.json.JSONObject()
            }
        } else {
            org.json.JSONObject()
        }
        
        // Update only the specific date entry
        json.put(date, "$steps|$distanceKm")
        
        // Save with single write operation
        prefs.edit { putString(PREF_HISTORICAL_DATA, json.toString()) }
    }
    
    private fun getHistoricalData(): MutableMap<String, ActivityData> {
        // Return cached data if available
        if (historicalDataCache != null) {
            return historicalDataCache!!
        }
        
        val data = mutableMapOf<String, ActivityData>()
        val jsonString = prefs.getString(PREF_HISTORICAL_DATA, null)
        if (jsonString != null) {
            try {
                val json = org.json.JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val date = keys.next()
                    val value = json.getString(date)
                    val parts = value.split("|")
                    if (parts.size == 2) {
                        val steps = parts[0].toIntOrNull() ?: 0
                        val distance = parts[1].toDoubleOrNull() ?: 0.0
                        data[date] = ActivityData(steps, distance, date)
                    }
                }
            } catch (_: Exception) {
            }
        }
        
        // Cache the data
        historicalDataCache = data
        return data
    }
    
    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACTIVITY_RECOGNITION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission not required before Android 10
        }
    }
    
    fun startTracking() {
        if (!hasActivityRecognitionPermission()) {
            Log.w(TAG, "Activity recognition permission not granted")
            return
        }
        
        if (isListening) {
            return
        }
        
        // Check if it's a new day and reset if needed
        val today = getCurrentDate()
        if (lastResetDate != today) {
            resetDailyCount()
        } else {
            // Initialize current hour if not already set
            if (currentHour == -1) {
                calendar.timeInMillis = System.currentTimeMillis()
                currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            }
        }
        
        // Prefer step counter over step detector (more accurate)
        val sensor = stepCounterSensor ?: stepDetectorSensor
        if (sensor != null) {
            // Use SENSOR_DELAY_NORMAL for better battery efficiency
            // Step counter only updates when steps are taken, so we don't need frequent polling
            val success = sensorManager.registerListener(
                this, 
                sensor, 
                SensorManager.SENSOR_DELAY_NORMAL
            )
            if (success) {
                isListening = true
            }
        } else {
            Log.w(TAG, "No step sensors available")
        }
    }
    
    fun stopTracking() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
            // Cancel any pending saves
            saveRunnable?.let { handler.removeCallbacks(it) }
            // Force save before stopping
            saveCurrentData()
        }
    }
    
    private fun scheduleSave() {
        // Only save if significant change (reduces I/O)
        val stepsChanged = todayStepCount - lastSavedStepCount
        if (stepsChanged < MIN_STEPS_FOR_SAVE) {
            // Schedule a delayed save
            saveRunnable?.let { handler.removeCallbacks(it) }
            saveRunnable = Runnable {
                saveCurrentData()
            }
            handler.postDelayed(saveRunnable!!, SAVE_INTERVAL_MS)
        } else {
            // Significant change, save immediately
            saveRunnable?.let { handler.removeCallbacks(it) }
            saveCurrentData()
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                // Step counter gives cumulative count since last reboot
                val currentTotalSteps = event.values[0].toInt()
                
                // Get the base count (steps before app started tracking)
                val baseSteps = prefs.getInt(PREF_TOTAL_STEPS_BASE, 0)
                
                if (baseSteps == 0) {
                    // First time, save current count as base
                    prefs.edit { 
                        putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps)
                    }
                    lastStepCount = currentTotalSteps
                } else {
                    // Calculate steps since base
                    val stepsSinceBase = currentTotalSteps - baseSteps
                    
                    // Calculate today's steps
                    val today = getCurrentDate()
                    if (lastResetDate != today) {
                        // New day, reset
                        resetDailyCount()
                        // Update base to current count
                        prefs.edit { 
                            putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps)
                        }
                        lastStepCount = currentTotalSteps
                    } else {
                        // Same day, calculate difference
                        val stepsToday = stepsSinceBase - (lastStepCount - baseSteps)
                        if (stepsToday > 0) {
                            todayStepCount += stepsToday
                            // Calculate and store distance using calibrated stride length
                            val strideLength = getStrideLengthMeters()
                            val distanceKm = (stepsToday * strideLength) / 1000.0
                            todayDistanceKm += distanceKm
                            lastStepCount = currentTotalSteps
                            updateHourlySteps(stepsToday, distanceKm)
                            scheduleSave() // Batch save instead of immediate
                        }
                    }
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // Step detector fires once per step
                if (event.values[0] == 1.0f) {
                    todayStepCount++
                    // Calculate and store distance using calibrated stride length
                    val strideLength = getStrideLengthMeters()
                    val distanceKm = strideLength / 1000.0
                    todayDistanceKm += distanceKm
                    updateHourlySteps(1, distanceKm)
                    scheduleSave() // Batch save instead of immediate
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step sensors
    }
    
    private fun updateHourlySteps(steps: Int, distanceKm: Double) {
        calendar.timeInMillis = System.currentTimeMillis()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        currentHour = hour
        hourlySteps[hour] = (hourlySteps[hour] ?: 0) + steps
        hourlyDistances[hour] = (hourlyDistances[hour] ?: 0.0) + distanceKm
    }
    
    private fun saveCurrentData() {
        val today = getCurrentDate()
        
        // Batch all writes together
        prefs.edit { 
            putInt(PREF_TODAY_STEP_COUNT, todayStepCount)
            putInt(PREF_LAST_STEP_COUNT, lastStepCount)
        }
        lastSavedStepCount = todayStepCount
        
        // Also save today's data to historical (only if changed significantly)
        if (todayStepCount > 0) {
            saveHistoricalData(today, todayStepCount, todayDistanceKm)
            // Save hourly data
            saveHourlyData(today, hourlySteps, hourlyDistances)
        }
    }
    
    private fun saveHourlyData(date: String, hourlyData: Map<Int, Int>, hourlyDistances: Map<Int, Double>) {
        val dateKey = "hourly_$date"
        
        // Load existing hourly data JSON
        val allHourlyData = hourlyDataCache ?: getHourlyDataMap()
        
        // Convert hourly data to JSON using stored distances
        val json = org.json.JSONObject()
        hourlyData.forEach { (hour, steps) ->
            val distanceKm = hourlyDistances[hour] ?: 0.0
            json.put(hour.toString(), "$steps|$distanceKm")
        }
        
        // Update main hourly data map
        allHourlyData[dateKey] = json.toString()
        hourlyDataCache = allHourlyData
        
        // Save the updated map back to SharedPreferences as a single JSON object
        val mainJson = org.json.JSONObject()
        allHourlyData.forEach { (key, data) ->
            mainJson.put(key, data)
        }
        
        // Implement batching or cleanup for old data? Let's stick to simple save for now
        prefs.edit { putString(PREF_HOURLY_DATA, mainJson.toString()) }
    }
    
    private fun getHourlyDataMap(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        val jsonString = prefs.getString(PREF_HOURLY_DATA, null)
        if (jsonString != null) {
            try {
                val json = org.json.JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.getString(key)
                }
            } catch (_: Exception) {
            }
        }
        return map
    }
    
    private fun loadHourlyData(date: String) {
        val dateKey = "hourly_$date"
        val allHourlyData = getHourlyDataMap()
        hourlyDataCache = allHourlyData
        
        val todayJsonStr = allHourlyData[dateKey]
        if (todayJsonStr != null) {
            try {
                val json = org.json.JSONObject(todayJsonStr)
                hourlySteps.clear()
                hourlyDistances.clear()
                todayDistanceKm = 0.0
                
                val keys = json.keys()
                while (keys.hasNext()) {
                    val hourStr = keys.next()
                    val value = json.getString(hourStr)
                    val parts = value.split("|")
                    if (parts.size == 2) {
                        val hour = hourStr.toIntOrNull() ?: continue
                        val steps = parts[0].toIntOrNull() ?: 0
                        val distance = parts[1].toDoubleOrNull() ?: 0.0
                        hourlySteps[hour] = steps
                        hourlyDistances[hour] = distance
                        todayDistanceKm += distance
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
    
    fun getHourlyActivityForDate(date: String): List<HourlyActivityData> {
        val dateKey = "hourly_$date"
        val allHourlyData = hourlyDataCache ?: getHourlyDataMap()
        val jsonStr = allHourlyData[dateKey]
        
        val list = mutableListOf<HourlyActivityData>()
        if (jsonStr != null) {
            try {
                val json = org.json.JSONObject(jsonStr)
                // Fill all 24 hours
                for (hour in 0..23) {
                    val value = json.optString(hour.toString(), "0|0.0")
                    val parts = value.split("|")
                    if (parts.size == 2) {
                        val steps = parts[0].toIntOrNull() ?: 0
                        val distance = parts[1].toDoubleOrNull() ?: 0.0
                        list.add(HourlyActivityData(hour, steps, distance))
                    } else {
                        list.add(HourlyActivityData(hour, 0, 0.0))
                    }
                }
            } catch (_: Exception) {
            }
        } else {
            // Return empty list with zeros for all 24 hours
            for (hour in 0..23) {
                list.add(HourlyActivityData(hour, 0, 0.0))
            }
        }
        return list
    }
    
    /**
     * Get activity data for a specific date
     */
    fun getActivityForDate(date: String): ActivityData {
        val historicalData = getHistoricalData()
        return historicalData[date] ?: ActivityData(0, 0.0, date)
    }

    /**
     * Get activity data for today
     */
    fun getTodayActivity(): ActivityData {
        return ActivityData(todayStepCount, todayDistanceKm, getCurrentDate())
    }
    
    /**
     * Get activity data for a range of dates
     */
    fun getMonthlyActivity(year: Int, month: Int): Map<String, ActivityData> {
        val historicalData = getHistoricalData()
        val monthPrefix = String.format(Locale.getDefault(), "%d-%02d", year, month)
        
        return historicalData.filter { it.key.startsWith(monthPrefix) }
    }
    
    /**
     * Get all dates that have historical activity data
     */
    fun getAllHistoricalDates(): Set<String> {
        return getHistoricalData().keys
    }
    
    fun getTodaySteps(): Int = todayStepCount
    
    fun getTodayDistanceKm(): Double = todayDistanceKm
    
    fun cleanup() {
        stopTracking()
    }
}
