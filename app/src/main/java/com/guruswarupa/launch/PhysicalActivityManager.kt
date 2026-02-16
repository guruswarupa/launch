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
    private var totalStepsBase = 0 // Base count when app started tracking
    
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
        totalStepsBase = prefs.getInt(PREF_TOTAL_STEPS_BASE, 0)
        
        // Validate data integrity
        if (lastStepCount < 0) lastStepCount = 0
        if (todayStepCount < 0) todayStepCount = 0
        if (totalStepsBase < 0) totalStepsBase = 0
        
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
            // Validate distance is not negative
            if (todayDistanceKm < 0.0) todayDistanceKm = 0.0
        }
        
        Log.d(TAG, "Loaded data: lastStepCount=$lastStepCount, todayStepCount=$todayStepCount, base=$totalStepsBase")
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
                
                // Validate sensor data
                if (currentTotalSteps < 0) {
                    Log.w(TAG, "Invalid step count from sensor: $currentTotalSteps")
                    return
                }
                
                // Load current base steps
                if (totalStepsBase == 0) {
                    // First time, save current count as base
                    Log.d(TAG, "Setting initial base step count: $currentTotalSteps")
                    totalStepsBase = currentTotalSteps
                    prefs.edit { 
                        putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps)
                    }
                    lastStepCount = currentTotalSteps
                    return // Don't process further on first initialization
                }
                
                // Validate base steps
                if (totalStepsBase < 0) {
                    Log.w(TAG, "Invalid base step count: $totalStepsBase, resetting")
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                    return
                }
                
                // Calculate steps since base
                val stepsSinceBase = currentTotalSteps - totalStepsBase
                
                // Validate calculation - if negative, sensor likely reset
                if (stepsSinceBase < 0) {
                    Log.w(TAG, "Sensor reset detected: stepsSinceBase=$stepsSinceBase, current=$currentTotalSteps, base=$totalStepsBase")
                    // Reset base to current count
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                    // Reset today's count to prevent accumulation from old data
                    val today = getCurrentDate()
                    if (lastResetDate == today) {
                        Log.d(TAG, "Resetting today's count due to sensor reset")
                        todayStepCount = 0
                        todayDistanceKm = 0.0
                        hourlySteps.clear()
                        hourlyDistances.clear()
                    }
                    return
                }
                
                // Validate steps since base is reasonable (less than 1M steps)
                if (stepsSinceBase > 1000000) {
                    Log.w(TAG, "Unrealistic cumulative steps: $stepsSinceBase, resetting base")
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                    return
                }
                
                // Calculate today's steps
                val today = getCurrentDate()
                if (lastResetDate != today) {
                    // New day, reset
                    Log.d(TAG, "New day detected: $today, resetting counts")
                    resetDailyCount()
                    // Update base to current count
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                } else {
                    // Same day, calculate difference
                    val previousStepsFromBase = lastStepCount - totalStepsBase
                    val stepsToday = stepsSinceBase - previousStepsFromBase
                    
                    // Validate step difference is reasonable
                    if (stepsToday > 0 && stepsToday <= 5000) { // Max 5000 steps per sensor update
                        todayStepCount += stepsToday
                        // Calculate and store distance using calibrated stride length
                        val strideLength = getStrideLengthMeters()
                        val distanceKm = (stepsToday * strideLength) / 1000.0
                        todayDistanceKm += distanceKm
                        lastStepCount = currentTotalSteps
                        updateHourlySteps(stepsToday, distanceKm)
                        Log.d(TAG, "Added $stepsToday steps (sensor diff), total today: $todayStepCount")
                        scheduleSave()
                    } else if (stepsToday > 5000) {
                        Log.w(TAG, "Ignoring unrealistic step count: $stepsToday (current=$currentTotalSteps, last=$lastStepCount, base=$totalStepsBase)")
                        // Still update lastStepCount to prevent large jumps on next reading
                        lastStepCount = currentTotalSteps
                    } else if (stepsToday < 0) {
                        Log.w(TAG, "Negative step difference: $stepsToday (current=$currentTotalSteps, last=$lastStepCount, base=$totalStepsBase)")
                        // This might happen due to small timing differences, just update lastStepCount
                        lastStepCount = currentTotalSteps
                    }
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // Step detector fires once per step
                if (event.values[0] == 1.0f) {
                    // Validate we're not getting too many steps too quickly
                    todayStepCount++
                    // Calculate and store distance using calibrated stride length
                    val strideLength = getStrideLengthMeters()
                    val distanceKm = strideLength / 1000.0
                    todayDistanceKm += distanceKm
                    updateHourlySteps(1, distanceKm)
                    Log.d(TAG, "Step detected, total: $todayStepCount")
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
        
        // Validate data before saving - prevent saving corrupted data
        if (todayStepCount < 0) {
            Log.w(TAG, "Invalid step count for saving: $todayStepCount")
            return
        }
        if (todayDistanceKm < 0.0) {
            Log.w(TAG, "Invalid distance for saving: $todayDistanceKm")
            todayDistanceKm = 0.0
        }
        
        // Additional validation to prevent saving obviously wrong data
        if (todayStepCount > 100000) { // More than 100k steps in a day is unrealistic
            Log.w(TAG, "Unrealistic step count detected: $todayStepCount, not saving")
            return
        }
        if (todayDistanceKm > 100.0) { // More than 100km in a day is unrealistic
            Log.w(TAG, "Unrealistic distance detected: $todayDistanceKm km, not saving")
            return
        }
        
        // Batch all writes together
        prefs.edit { 
            putInt(PREF_TODAY_STEP_COUNT, todayStepCount)
            putInt(PREF_LAST_STEP_COUNT, lastStepCount)
            putInt(PREF_TOTAL_STEPS_BASE, totalStepsBase)
        }
        lastSavedStepCount = todayStepCount
        
        // Also save today's data to historical (only if changed significantly)
        if (todayStepCount > 0) {
            saveHistoricalData(today, todayStepCount, todayDistanceKm)
            // Save hourly data
            saveHourlyData(today, hourlySteps, hourlyDistances)
            Log.d(TAG, "Saved data: $todayStepCount steps, $todayDistanceKm km")
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
        // Check for obviously corrupted data and reset if needed
        if (todayStepCount > 100000 || todayDistanceKm > 100.0) {
            Log.w(TAG, "Corrupted data detected: steps=$todayStepCount, distance=$todayDistanceKm km")
            // Reset the corrupted data
            todayStepCount = 0
            todayDistanceKm = 0.0
            hourlySteps.clear()
            hourlyDistances.clear()
            // Don't reset the base count as it might be valid
        }
        
        // Validate and sanitize data
        val validSteps = todayStepCount.coerceAtLeast(0)
        val validDistance = todayDistanceKm.coerceAtLeast(0.0)
        
        if (validSteps != todayStepCount || validDistance != todayDistanceKm) {
            Log.w(TAG, "Data validation corrected: steps $todayStepCount->$validSteps, distance $todayDistanceKm->$validDistance")
            // Update invalid data
            if (todayStepCount < 0) todayStepCount = 0
            if (todayDistanceKm < 0.0) todayDistanceKm = 0.0
        }
        
        return ActivityData(validSteps, validDistance, getCurrentDate())
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
    
    /**
     * Reset all activity data to fix corrupted counts
     */
    fun resetAllData() {
        Log.w(TAG, "Resetting all activity data due to corruption")
        
        // Reset all counters
        todayStepCount = 0
        todayDistanceKm = 0.0
        lastStepCount = 0
        totalStepsBase = 0
        lastResetDate = ""
        hourlySteps.clear()
        hourlyDistances.clear()
        currentHour = -1
        
        // Clear all saved data
        prefs.edit {
            remove(PREF_LAST_STEP_COUNT)
            remove(PREF_TODAY_STEP_COUNT)
            remove(PREF_LAST_RESET_DATE)
            remove(PREF_TOTAL_STEPS_BASE)
            remove(PREF_HISTORICAL_DATA)
            remove(PREF_HOURLY_DATA)
            remove(PREF_STRIDE_LENGTH_METERS)
            remove(PREF_USER_HEIGHT_CM)
        }
        
        // Clear caches
        historicalDataCache = null
        hourlyDataCache = null
        
        Log.d(TAG, "All activity data has been reset")
    }
    
    fun cleanup() {
        stopTracking()
    }
}
