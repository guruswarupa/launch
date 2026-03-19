package com.guruswarupa.launch.managers

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
    
    
    private var lastStepCount = 0
    private var todayStepCount = 0
    private var lastResetDate = ""
    private var totalStepsBase = 0 
    
    
    private var lastSavedStepCount = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    
    
    
    
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val calendar = Calendar.getInstance()
    
    
    private var historicalDataCache: MutableMap<String, ActivityData>? = null
    private var hourlyDataCache: MutableMap<String, String>? = null
    
    
    private var todayDistanceKm: Double = 0.0
    
    
    private var currentHour = -1
    private var hourlySteps: MutableMap<Int, Int> = mutableMapOf() 
    private var hourlyDistances: MutableMap<Int, Double> = mutableMapOf() 
    
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
        
        private const val SAVE_INTERVAL_MS = 2 * 60 * 1000L 
        private const val MIN_STEPS_FOR_SAVE = 10 
        private const val DEFAULT_STEP_LENGTH_METERS = 0.75
    }
    
    init {
        initializeSensors()
        
    }
    
    



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
        
        
        if (lastStepCount < 0) lastStepCount = 0
        if (todayStepCount < 0) todayStepCount = 0
        if (totalStepsBase < 0) totalStepsBase = 0
        
        
        val today = getCurrentDate()
        if (lastResetDate != today) {
            resetDailyCount()
        } else {
            
            loadHourlyData(today)
            
            if (todayDistanceKm == 0.0 && todayStepCount > 0) {
                val strideLength = getStrideLengthMeters()
                todayDistanceKm = (todayStepCount * strideLength) / 1000.0
            }
            
            if (todayDistanceKm < 0.0) todayDistanceKm = 0.0
        }
    }
    
    fun getCurrentDate(): String {
        return dateFormatter.format(Date())
    }
    
    


    fun getStrideLengthMeters(): Double {
        val stored = prefs.getFloat(PREF_STRIDE_LENGTH_METERS, -1f)
        if (stored > 0) {
            return stored.toDouble()
        }
        
        val heightCm = prefs.getInt(PREF_USER_HEIGHT_CM, -1)
        if (heightCm > 0) {
            
            return (heightCm * 0.43) / 100.0
        }
        return DEFAULT_STEP_LENGTH_METERS
    }
    
    


    fun setStrideLengthMeters(meters: Double) {
        prefs.edit { putFloat(PREF_STRIDE_LENGTH_METERS, meters.toFloat()) }
        
        val strideLength = getStrideLengthMeters()
        todayDistanceKm = (todayStepCount * strideLength) / 1000.0
        
        hourlyDistances.clear()
        hourlySteps.forEach { (hour, steps) ->
            hourlyDistances[hour] = (steps * strideLength) / 1000.0
        }
    }
    
    


    fun setUserHeightCm(heightCm: Int) {
        prefs.edit { putInt(PREF_USER_HEIGHT_CM, heightCm) }
        
        val calculatedStride = (heightCm * 0.43) / 100.0
        setStrideLengthMeters(calculatedStride)
    }
    
    


    fun getUserHeightCm(): Int {
        return prefs.getInt(PREF_USER_HEIGHT_CM, -1)
    }
    
    


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
        
        
        if (lastResetDate.isNotEmpty() && lastResetDate != today && todayStepCount > 0) {
            saveHistoricalData(lastResetDate, todayStepCount, todayDistanceKm)
            
            saveHourlyData(lastResetDate, hourlySteps, hourlyDistances)
        }
        
        lastResetDate = today
        todayStepCount = 0
        todayDistanceKm = 0.0
        lastStepCount = 0
        hourlySteps.clear()
        hourlyDistances.clear()
        currentHour = -1
        
        
        prefs.edit {
            putString(PREF_LAST_RESET_DATE, today)
            putInt(PREF_TODAY_STEP_COUNT, 0)
            putInt(PREF_LAST_STEP_COUNT, 0)
        }
        
        
        historicalDataCache = null
        hourlyDataCache = null
    }
    
    private fun saveHistoricalData(date: String, steps: Int, distanceKm: Double) {
        
        val historicalData = historicalDataCache ?: getHistoricalData()
        
        
        historicalData[date] = ActivityData(steps, distanceKm, date)
        historicalDataCache = historicalData
        
        
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
        
        
        json.put(date, "$steps|$distanceKm")
        
        
        prefs.edit { putString(PREF_HISTORICAL_DATA, json.toString()) }
    }
    
    private fun getHistoricalData(): MutableMap<String, ActivityData> {
        
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
            true 
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
        
        
        val today = getCurrentDate()
        if (lastResetDate != today) {
            resetDailyCount()
        } else {
            
            if (currentHour == -1) {
                calendar.timeInMillis = System.currentTimeMillis()
                currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            }
        }
        
        
        val sensor = stepCounterSensor ?: stepDetectorSensor
        if (sensor != null) {
            
            
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
            
            saveRunnable?.let { handler.removeCallbacks(it) }
            
            saveCurrentData()
        }
    }
    
    private fun scheduleSave() {
        
        val stepsChanged = todayStepCount - lastSavedStepCount
        if (stepsChanged < MIN_STEPS_FOR_SAVE) {
            
            saveRunnable?.let { handler.removeCallbacks(it) }
            saveRunnable = Runnable {
                saveCurrentData()
            }
            handler.postDelayed(saveRunnable!!, SAVE_INTERVAL_MS)
        } else {
            
            saveRunnable?.let { handler.removeCallbacks(it) }
            saveCurrentData()
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                
                val currentTotalSteps = event.values[0].toInt()
                
                
                if (currentTotalSteps < 0) {
                    Log.w(TAG, "Invalid step count from sensor: $currentTotalSteps")
                    return
                }
                
                
                if (totalStepsBase == 0) {
                    
                    totalStepsBase = currentTotalSteps
                    prefs.edit { 
                        putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps)
                    }
                    lastStepCount = currentTotalSteps
                    return 
                }
                
                
                if (totalStepsBase < 0) {
                    Log.w(TAG, "Invalid base step count: $totalStepsBase, resetting")
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                    return
                }
                
                
                val stepsSinceBase = currentTotalSteps - totalStepsBase
                
                
                if (stepsSinceBase < 0) {
                    Log.w(TAG, "Sensor reset detected: stepsSinceBase=$stepsSinceBase, current=$currentTotalSteps, base=$totalStepsBase")
                    
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                    
                    val today = getCurrentDate()
                    if (lastResetDate == today) {
                        todayStepCount = 0
                        todayDistanceKm = 0.0
                        hourlySteps.clear()
                        hourlyDistances.clear()
                    }
                    return
                }
                
                
                if (stepsSinceBase > 1000000) {
                    Log.w(TAG, "Unrealistic cumulative steps: $stepsSinceBase, resetting base")
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                    return
                }
                
                
                val today = getCurrentDate()
                if (lastResetDate != today) {
                    
                    resetDailyCount()
                    
                    totalStepsBase = currentTotalSteps
                    prefs.edit { putInt(PREF_TOTAL_STEPS_BASE, currentTotalSteps) }
                    lastStepCount = currentTotalSteps
                } else {
                    
                    val previousStepsFromBase = lastStepCount - totalStepsBase
                    val stepsToday = stepsSinceBase - previousStepsFromBase
                    
                    
                    if (stepsToday > 0 && stepsToday <= 5000) { 
                        todayStepCount += stepsToday
                        
                        val strideLength = getStrideLengthMeters()
                        val distanceKm = (stepsToday * strideLength) / 1000.0
                        todayDistanceKm += distanceKm
                        lastStepCount = currentTotalSteps
                        updateHourlySteps(stepsToday, distanceKm)
                        scheduleSave()
                    } else if (stepsToday > 5000) {
                        Log.w(TAG, "Ignoring unrealistic step count: $stepsToday (current=$currentTotalSteps, last=$lastStepCount, base=$totalStepsBase)")
                        
                        lastStepCount = currentTotalSteps
                    } else if (stepsToday < 0) {
                        Log.w(TAG, "Negative step difference: $stepsToday (current=$currentTotalSteps, last=$lastStepCount, base=$totalStepsBase)")
                        
                        lastStepCount = currentTotalSteps
                    }
                }
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                
                if (event.values[0] == 1.0f) {
                    
                    todayStepCount++
                    
                    val strideLength = getStrideLengthMeters()
                    val distanceKm = strideLength / 1000.0
                    todayDistanceKm += distanceKm
                    updateHourlySteps(1, distanceKm)
                    scheduleSave() 
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        
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
        
        
        if (todayStepCount < 0) {
            Log.w(TAG, "Invalid step count for saving: $todayStepCount")
            return
        }
        if (todayDistanceKm < 0.0) {
            Log.w(TAG, "Invalid distance for saving: $todayDistanceKm")
            todayDistanceKm = 0.0
        }
        
        
        if (todayStepCount > 100000) { 
            Log.w(TAG, "Unrealistic step count detected: $todayStepCount, not saving")
            return
        }
        if (todayDistanceKm > 100.0) { 
            Log.w(TAG, "Unrealistic distance detected: $todayDistanceKm km, not saving")
            return
        }
        
        
        prefs.edit { 
            putInt(PREF_TODAY_STEP_COUNT, todayStepCount)
            putInt(PREF_LAST_STEP_COUNT, lastStepCount)
            putInt(PREF_TOTAL_STEPS_BASE, totalStepsBase)
        }
        lastSavedStepCount = todayStepCount
        
        
        if (todayStepCount > 0) {
            saveHistoricalData(today, todayStepCount, todayDistanceKm)
            
            saveHourlyData(today, hourlySteps, hourlyDistances)
        }
    }
    
    private fun saveHourlyData(date: String, hourlyData: Map<Int, Int>, hourlyDistances: Map<Int, Double>) {
        val dateKey = "hourly_$date"
        
        
        val allHourlyData = hourlyDataCache ?: getHourlyDataMap()
        
        
        val json = org.json.JSONObject()
        hourlyData.forEach { (hour, steps) ->
            val distanceKm = hourlyDistances[hour] ?: 0.0
            json.put(hour.toString(), "$steps|$distanceKm")
        }
        
        
        allHourlyData[dateKey] = json.toString()
        hourlyDataCache = allHourlyData
        
        
        val mainJson = org.json.JSONObject()
        allHourlyData.forEach { (key, data) ->
            mainJson.put(key, data)
        }
        
        
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
            
            for (hour in 0..23) {
                list.add(HourlyActivityData(hour, 0, 0.0))
            }
        }
        return list
    }
    
    


    fun getActivityForDate(date: String): ActivityData {
        val historicalData = getHistoricalData()
        return historicalData[date] ?: ActivityData(0, 0.0, date)
    }

    


    fun getTodayActivity(): ActivityData {
        
        if (todayStepCount > 100000 || todayDistanceKm > 100.0) {
            Log.w(TAG, "Corrupted data detected: steps=$todayStepCount, distance=$todayDistanceKm km")
            
            todayStepCount = 0
            todayDistanceKm = 0.0
            hourlySteps.clear()
            hourlyDistances.clear()
            
        }
        
        
        val validSteps = todayStepCount.coerceAtLeast(0)
        val validDistance = todayDistanceKm.coerceAtLeast(0.0)
        
        if (validSteps != todayStepCount || validDistance != todayDistanceKm) {
            Log.w(TAG, "Data validation corrected: steps $todayStepCount->$validSteps, distance $todayDistanceKm->$validDistance")
            
            if (todayStepCount < 0) todayStepCount = 0
            if (todayDistanceKm < 0.0) todayDistanceKm = 0.0
        }
        
        return ActivityData(validSteps, validDistance, getCurrentDate())
    }
    
    


    fun getMonthlyActivity(year: Int, month: Int): Map<String, ActivityData> {
        val historicalData = getHistoricalData()
        val monthPrefix = String.format(Locale.getDefault(), "%d-%02d", year, month)
        
        return historicalData.filter { it.key.startsWith(monthPrefix) }
    }
    
    


    fun getAllHistoricalDates(): Set<String> {
        return getHistoricalData().keys
    }
    
    fun getTodaySteps(): Int = todayStepCount
    
    fun getTodayDistanceKm(): Double = todayDistanceKm
    
    


    fun resetAllData() {
        Log.w(TAG, "Resetting all activity data due to corruption")
        
        
        todayStepCount = 0
        todayDistanceKm = 0.0
        lastStepCount = 0
        totalStepsBase = 0
        lastResetDate = ""
        hourlySteps.clear()
        hourlyDistances.clear()
        currentHour = -1
        
        
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
        
        
        historicalDataCache = null
        hourlyDataCache = null
    }
    
    fun cleanup() {
        stopTracking()
    }
}
