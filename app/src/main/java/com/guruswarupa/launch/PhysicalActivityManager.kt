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
    private val SAVE_INTERVAL_MS = 2 * 60 * 1000L // Save every 2 minutes
    private val MIN_STEPS_FOR_SAVE = 10 // Only save if at least 10 steps changed
    
    // Average step length in meters (approximately 0.75m for average adult)
    private val AVERAGE_STEP_LENGTH_METERS = 0.75
    
    // Hourly tracking
    private var currentHour = -1
    private var hourlySteps: MutableMap<Int, Int> = mutableMapOf() // hour -> steps
    private var lastHourlySaveTime = 0L
    
    companion object {
        private const val TAG = "PhysicalActivityManager"
        private const val PREF_LAST_STEP_COUNT = "last_step_count"
        private const val PREF_TODAY_STEP_COUNT = "today_step_count"
        private const val PREF_LAST_RESET_DATE = "last_reset_date"
        private const val PREF_TOTAL_STEPS_BASE = "total_steps_base"
        private const val PREF_HISTORICAL_DATA = "historical_activity_data"
        private const val PREF_HOURLY_DATA = "hourly_activity_data"
    }
    
    init {
        initializeSensors()
        loadSavedData()
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
            // Load hourly data for today
            loadHourlyData(today)
        }
    }
    
    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }
    
    private fun resetDailyCount() {
        val today = getCurrentDate()
        
        // Save yesterday's data before resetting
        if (lastResetDate.isNotEmpty() && lastResetDate != today && todayStepCount > 0) {
            saveHistoricalData(lastResetDate, todayStepCount)
            // Save yesterday's hourly data
            saveHourlyData(lastResetDate, hourlySteps)
        }
        
        lastResetDate = today
        todayStepCount = 0
        lastStepCount = 0
        hourlySteps.clear()
        currentHour = -1
        
        prefs.edit().apply {
            putString(PREF_LAST_RESET_DATE, today)
            putInt(PREF_TODAY_STEP_COUNT, 0)
            putInt(PREF_LAST_STEP_COUNT, 0)
            apply()
        }
    }
    
    private fun saveHistoricalData(date: String, steps: Int) {
        val historicalData = getHistoricalData()
        val distanceKm = (steps * AVERAGE_STEP_LENGTH_METERS) / 1000.0
        historicalData[date] = ActivityData(steps, distanceKm, date)
        
        // Save to preferences (store as JSON string)
        val json = org.json.JSONObject()
        historicalData.forEach { (dateKey, data) ->
            json.put(dateKey, "${data.steps}|${data.distanceKm}")
        }
        prefs.edit().putString(PREF_HISTORICAL_DATA, json.toString()).apply()
    }
    
    private fun getHistoricalData(): MutableMap<String, ActivityData> {
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
            } catch (e: Exception) {
            }
        }
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
                val calendar = Calendar.getInstance()
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
                    prefs.edit().putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps).apply()
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
                        prefs.edit().putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps).apply()
                        lastStepCount = currentTotalSteps
                    } else {
                        // Same day, calculate difference
                        val stepsToday = stepsSinceBase - (lastStepCount - baseSteps)
                        if (stepsToday > 0) {
                            todayStepCount += stepsToday
                            lastStepCount = currentTotalSteps
                            updateHourlySteps(stepsToday)
                            scheduleSave() // Batch save instead of immediate
                        }
                    }
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                // Step detector fires once per step
                if (event.values[0] == 1.0f) {
                    todayStepCount++
                    updateHourlySteps(1)
                    scheduleSave() // Batch save instead of immediate
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for step sensors
    }
    
    private fun updateHourlySteps(steps: Int) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // If hour changed, save previous hour's data
        if (currentHour != -1 && currentHour != hour) {
            // Hour changed, data for previous hour is already saved
        }
        
        currentHour = hour
        hourlySteps[hour] = (hourlySteps[hour] ?: 0) + steps
    }
    
    private fun saveCurrentData() {
        val today = getCurrentDate()
        prefs.edit().apply {
            putInt(PREF_TODAY_STEP_COUNT, todayStepCount)
            putInt(PREF_LAST_STEP_COUNT, lastStepCount)
            apply()
        }
        lastSavedStepCount = todayStepCount
        
        // Also save today's data to historical (only if changed significantly)
        if (todayStepCount > 0) {
            saveHistoricalData(today, todayStepCount)
            // Save hourly data
            saveHourlyData(today, hourlySteps)
        }
    }
    
    private fun saveHourlyData(date: String, hourlyData: Map<Int, Int>) {
        val allHourlyData = getHourlyDataMap()
        val dateKey = "hourly_$date"
        
        // Convert hourly data to JSON
        val json = org.json.JSONObject()
        hourlyData.forEach { (hour, steps) ->
            val distanceKm = (steps * AVERAGE_STEP_LENGTH_METERS) / 1000.0
            json.put(hour.toString(), "$steps|$distanceKm")
        }
        
        allHourlyData[dateKey] = json.toString()
        
        // Save all hourly data
        val mainJson = org.json.JSONObject()
        allHourlyData.forEach { (key, value) ->
            mainJson.put(key, value)
        }
        
        prefs.edit().putString(PREF_HOURLY_DATA, mainJson.toString()).apply()
    }
    
    private fun getHourlyDataMap(): MutableMap<String, String> {
        val data = mutableMapOf<String, String>()
        val jsonString = prefs.getString(PREF_HOURLY_DATA, null)
        if (jsonString != null) {
            try {
                val json = org.json.JSONObject(jsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    data[key] = json.getString(key)
                }
            } catch (e: Exception) {
            }
        }
        return data
    }
    
    private fun loadHourlyData(date: String) {
        val dateKey = "hourly_$date"
        val allHourlyData = getHourlyDataMap()
        val hourlyJsonString = allHourlyData[dateKey]
        
        hourlySteps.clear()
        if (hourlyJsonString != null) {
            try {
                val json = org.json.JSONObject(hourlyJsonString)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val hourStr = keys.next()
                    val value = json.getString(hourStr)
                    val parts = value.split("|")
                    if (parts.size == 2) {
                        val steps = parts[0].toIntOrNull() ?: 0
                        val hour = hourStr.toIntOrNull()
                        if (hour != null) {
                            hourlySteps[hour] = steps
                        }
                        }
                    }
                } catch (e: Exception) {
                }
        }
        
        // Set current hour
        val calendar = Calendar.getInstance()
        currentHour = calendar.get(Calendar.HOUR_OF_DAY)
    }
    
    fun getHourlyActivityForDate(date: String): List<HourlyActivityData> {
        val today = getCurrentDate()
        val hourlyDataList = mutableListOf<HourlyActivityData>()
        
        if (date == today) {
            // Get current hourly data
            for (hour in 0..23) {
                val steps = hourlySteps[hour] ?: 0
                val distanceKm = (steps * AVERAGE_STEP_LENGTH_METERS) / 1000.0
                hourlyDataList.add(HourlyActivityData(hour, steps, distanceKm))
            }
        } else {
            // Get historical hourly data
            val dateKey = "hourly_$date"
            val allHourlyData = getHourlyDataMap()
            val hourlyJsonString = allHourlyData[dateKey]
            
            if (hourlyJsonString != null) {
                try {
                    val json = org.json.JSONObject(hourlyJsonString)
                    for (hour in 0..23) {
                        val hourStr = hour.toString()
                        if (json.has(hourStr)) {
                            val value = json.getString(hourStr)
                            val parts = value.split("|")
                            if (parts.size == 2) {
                                val steps = parts[0].toIntOrNull() ?: 0
                                val distanceKm = parts[1].toDoubleOrNull() ?: 0.0
                                hourlyDataList.add(HourlyActivityData(hour, steps, distanceKm))
                            } else {
                                hourlyDataList.add(HourlyActivityData(hour, 0, 0.0))
                            }
                        } else {
                            hourlyDataList.add(HourlyActivityData(hour, 0, 0.0))
                        }
                    }
                } catch (e: Exception) {
                    // Return empty data for all hours
                    for (hour in 0..23) {
                        hourlyDataList.add(HourlyActivityData(hour, 0, 0.0))
                    }
                }
            } else {
                // No hourly data for this date, return zeros
                for (hour in 0..23) {
                    hourlyDataList.add(HourlyActivityData(hour, 0, 0.0))
                }
            }
        }
        
        return hourlyDataList
    }
    
    fun getTodayActivity(): ActivityData {
        val today = getCurrentDate()
        
        // Check if we need to reset for a new day
        if (lastResetDate != today) {
            resetDailyCount()
        }
        
        val distanceKm = (todayStepCount * AVERAGE_STEP_LENGTH_METERS) / 1000.0
        
        return ActivityData(
            steps = todayStepCount,
            distanceKm = distanceKm,
            date = today
        )
    }
    
    fun getActivityForDate(date: String): ActivityData {
        val today = getCurrentDate()
        if (date == today) {
            return getTodayActivity()
        }
        
        // Get from historical data
        val historicalData = getHistoricalData()
        return historicalData[date] ?: ActivityData(0, 0.0, date)
    }
    
    fun getAllHistoricalDates(): Set<String> {
        val historicalData = getHistoricalData()
        val today = getCurrentDate()
        val dates = historicalData.keys.toMutableSet()
        // Include today if there's activity
        if (todayStepCount > 0) {
            dates.add(today)
        }
        return dates
    }
    
    fun getMonthlyActivity(year: Int, month: Int): Map<String, ActivityData> {
        val historicalData = getHistoricalData()
        val monthlyData = mutableMapOf<String, ActivityData>()
        val today = getCurrentDate()
        
        // Get all dates in the month
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month, day)
            if (dateStr == today) {
                monthlyData[dateStr] = getTodayActivity()
            } else {
                monthlyData[dateStr] = historicalData[dateStr] ?: ActivityData(0, 0.0, dateStr)
            }
        }
        
        return monthlyData
    }
}
