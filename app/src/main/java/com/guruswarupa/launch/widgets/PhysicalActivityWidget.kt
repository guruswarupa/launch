package com.guruswarupa.launch.widgets

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.ActivityData
import com.guruswarupa.launch.managers.PhysicalActivityManager
import com.guruswarupa.launch.services.PhysicalActivityTrackingService
import com.guruswarupa.launch.ui.views.HourlyStepsChartView
import com.guruswarupa.launch.ui.views.PhysicalActivityCalendarView
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class PhysicalActivityWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private lateinit var activityManager: PhysicalActivityManager
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var stepsText: TextView
    private lateinit var distanceText: TextView
    private lateinit var permissionButton: Button
    private lateinit var viewToggleButton: Button
    private lateinit var calibrationButton: ImageButton
    private lateinit var statsViewContainer: LinearLayout
    private lateinit var calendarViewContainer: FrameLayout
    
    private var isCalendarView = false
    private var calendarView: PhysicalActivityCalendarView? = null
    private lateinit var widgetView: View
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && activityManager.hasActivityRecognitionPermission()) {
                updateDisplay()
            }
            handler.postDelayed(this, 10000) // Update every 10 seconds for better responsiveness
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        // Inflate the widget layout
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.widget_physical_activity, container, false)
        container.addView(widgetView)
        
        // Initialize views
        stepsText = widgetView.findViewById(R.id.steps_text)
        distanceText = widgetView.findViewById(R.id.distance_text)
        permissionButton = widgetView.findViewById(R.id.request_permission_button)
        viewToggleButton = widgetView.findViewById(R.id.view_toggle_button)
        calibrationButton = widgetView.findViewById(R.id.calibration_button)
        statsViewContainer = widgetView.findViewById(R.id.stats_view_container)
        calendarViewContainer = widgetView.findViewById(R.id.calendar_view_container)
        
        // Stats container is already in stats_view_container in the layout
        
        // Initialize activity manager
        activityManager = PhysicalActivityManager(context)
        activityManager.initializeAsync {
            // Check permission and setup UI once initialized
            if (activityManager.hasActivityRecognitionPermission()) {
                setupWithPermission()
            } else {
                setupWithoutPermission()
            }
        }
        
        // Setup toggle button
        viewToggleButton.setOnClickListener {
            toggleView()
        }

        calibrationButton.setOnClickListener {
            // Check if data looks corrupted and offer reset option
            val activityData = activityManager.getTodayActivity()
            if (activityData.steps > 100000 || activityData.distanceKm > 100.0) {
                showResetDataDialog()
            } else {
                showCalibrationDialog()
            }
        }
        
        // Initialize calendar view
        initializeCalendarView()
        
        isInitialized = true
    }
    
    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.physical_activity_calendar_view, calendarViewContainer, false)
        calendarViewContainer.addView(calendarViewLayout)
        calendarView = PhysicalActivityCalendarView(
            calendarViewLayout,
            activityManager
        ) { date, activityData ->
            showDayActivityDetails(date, activityData)
        }
    }
    
    private fun showDayActivityDetails(date: String, activityData: ActivityData) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        
        val parsedDate = try {
            dateFormat.parse(date)
        } catch (_: Exception) {
            null
        }
        
        val displayDate = parsedDate?.let { displayFormat.format(it) } ?: date
        
        // Inflate dialog layout
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_hourly_activity, null)
        
        // Get views
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
        val totalStepsText = dialogView.findViewById<TextView>(R.id.total_steps_text)
        val totalDistanceText = dialogView.findViewById<TextView>(R.id.total_distance_text)
        val hourlyChart = dialogView.findViewById<HourlyStepsChartView>(R.id.hourly_chart)
        val hourlyStatsContainer = dialogView.findViewById<LinearLayout>(R.id.hourly_stats_container)
        
        // Set title
        titleText.text = displayDate
        
        // Set summary stats
        val df = DecimalFormat("#.##")
        val stepsFormatted = String.format(Locale.getDefault(), "%,d", activityData.steps)
        val distanceFormatted = df.format(activityData.distanceKm)
        totalStepsText.text = stepsFormatted
        totalDistanceText.text = context.getString(R.string.distance_km_format, distanceFormatted)
        
        // Get hourly data
        val hourlyData = activityManager.getHourlyActivityForDate(date)
        hourlyChart.setHourlyData(hourlyData)
        
        // Populate hourly stats list
        hourlyStatsContainer.removeAllViews()
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        
        hourlyData.forEach { hourly ->
            if (hourly.steps > 0) {
                val hourView = LayoutInflater.from(context).inflate(
                    android.R.layout.simple_list_item_2,
                    hourlyStatsContainer,
                    false
                )
                val text1 = hourView.findViewById<TextView>(android.R.id.text1)
                val text2 = hourView.findViewById<TextView>(android.R.id.text2)
                
                val hourLabel = when {
                    hourly.hour == 0 -> "12:00 AM"
                    hourly.hour < 12 -> "${hourly.hour}:00 AM"
                    hourly.hour == 12 -> "12:00 PM"
                    else -> "${hourly.hour - 12}:00 PM"
                }
                
                text1.text = hourLabel
                text1.setTextColor(textColor)
                text1.textSize = 14f
                
                val distanceFormattedHourly = df.format(hourly.distanceKm)
                text2.text = context.getString(R.string.steps_distance_format, hourly.steps, distanceFormattedHourly)
                text2.setTextColor(secondaryTextColor)
                text2.textSize = 12f
                
                hourlyStatsContainer.addView(hourView)
            }
        }
        
        // Show dialog
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun toggleView() {
        isCalendarView = !isCalendarView
        
        if (isCalendarView) {
            // Show calendar, hide stats
            statsViewContainer.visibility = View.GONE
            calendarViewContainer.visibility = View.VISIBLE
            viewToggleButton.text = context.getString(R.string.stats)
            calendarView?.refreshData()
        } else {
            // Show stats, hide calendar
            statsViewContainer.visibility = View.VISIBLE
            calendarViewContainer.visibility = View.GONE
            viewToggleButton.text = context.getString(R.string.calendar)
        }
    }
    
    private fun setupWithPermission() {
        permissionButton.visibility = View.GONE
        viewToggleButton.visibility = View.VISIBLE
        calibrationButton.visibility = View.VISIBLE
        
        // Start foreground service for background tracking
        startTrackingService()
        
        // Start tracking in widget (for immediate display)
        activityManager.startTracking()
        
        // Initial update with delay to ensure data is loaded
        handler.postDelayed({
            updateDisplay()
        }, 1000)
        
        // Start periodic updates
        handler.postDelayed(updateRunnable, 5000) // First update after 5 seconds
    }
    
    private fun startTrackingService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(context, PhysicalActivityTrackingService::class.java)
            context.startForegroundService(intent)
        } else {
            val intent = Intent(context, PhysicalActivityTrackingService::class.java)
            context.startService(intent)
        }
    }
    
    private fun stopTrackingService() {
        val intent = Intent(context, PhysicalActivityTrackingService::class.java)
        context.stopService(intent)
    }
    
    private fun setupWithoutPermission() {
        permissionButton.visibility = View.VISIBLE
        viewToggleButton.visibility = View.GONE
        calibrationButton.visibility = View.GONE
        // Show stats container but with default/empty values
        statsViewContainer.visibility = View.VISIBLE
        calendarViewContainer.visibility = View.GONE
        
        // Set default values
        stepsText.text = context.getString(R.string.zero_steps)
        distanceText.text = context.getString(R.string.zero_distance)
        
        permissionButton.setOnClickListener {
            requestPermission()
        }
        
        // Check if permission was previously denied
        val permissionDenied = sharedPreferences.getBoolean("activity_recognition_permission_denied", false)
        if (permissionDenied) {
            permissionButton.text = context.getString(R.string.enable_in_settings)
            permissionButton.setOnClickListener {
                openSettings()
            }
        }
    }
    
    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Show explanation dialog
                AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle("Physical Activity Permission")
                    .setMessage("This permission allows the launcher to track your steps and distance walked. The data is stored locally on your device and helps you monitor your daily physical activity.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        // Permission request will be handled by the activity
                        if (context is FragmentActivity) {
                            ActivityCompat.requestPermissions(
                                context,
                                arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION),
                                105
                            )
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Handle error
        }
    }
    
    fun onPermissionGranted() {
        setupWithPermission()
    }
    
    fun updateDisplay() {
        if (!isInitialized) return
        
        val activityData = activityManager.getTodayActivity()
        
        // Animate count update if desired, for now just set text
        val df = DecimalFormat("#.##")
        val stepsFormatted = String.format(Locale.getDefault(), "%,d", activityData.steps)
        val distanceFormatted = df.format(activityData.distanceKm)
        
        stepsText.text = context.getString(R.string.steps_format, stepsFormatted)
        distanceText.text = context.getString(R.string.distance_km_format, distanceFormatted)
        
        // Refresh calendar if visible
        if (isCalendarView) {
            calendarView?.refreshData()
        }
    }
    
    private fun showCalibrationDialog() {
        val options = arrayOf("Enter Height (cm)", "Enter Stride Length (m)", "Reset to Default")
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Calibrate Distance")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showHeightInputDialog()
                    1 -> showStrideLengthInputDialog()
                    2 -> {
                        activityManager.resetStrideLengthToDefault()
                        updateDisplay()
                        Toast.makeText(context, "Reset to default stride length", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showHeightInputDialog() {
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            val currentHeight = activityManager.getUserHeightCm()
            if (currentHeight > 0) setText(currentHeight.toString())
            hint = "Height in cm (e.g. 175)"
        }
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Enter Your Height")
            .setMessage("Your stride length will be calculated based on your height (approx. 43% of height).")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val height = input.text.toString().toIntOrNull()
                if (height != null && height in 50..250) {
                    activityManager.setUserHeightCm(height)
                    updateDisplay()
                    Toast.makeText(context, "Calibration saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid height entered", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showStrideLengthInputDialog() {
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            val currentStride = activityManager.getStrideLengthMeters()
            setText(String.format(Locale.US, "%.2f", currentStride))
            hint = "Stride length in meters (e.g. 0.75)"
        }
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Enter Stride Length")
            .setMessage("Enter the average distance of one step in meters.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val stride = input.text.toString().toDoubleOrNull()
                if (stride != null && stride in 0.1..2.0) {
                    activityManager.setStrideLengthMeters(stride)
                    updateDisplay()
                    Toast.makeText(context, "Calibration saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid stride length", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showResetDataDialog() {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Data Integrity Check")
            .setMessage(String.format(Locale.getDefault(), "The step count or distance appears to be corrupted (%d steps, %.2f km). Would you like to reset all activity data?", 
                activityManager.getTodaySteps(), activityManager.getTodayDistanceKm()))
            .setPositiveButton("Reset All Data") { _, _ ->
                activityManager.resetAllData()
                updateDisplay()
                calendarView?.refreshData()
                Toast.makeText(context, "Activity data has been reset", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Recalibrate Only") { _, _ ->
                showCalibrationDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    fun onResume() {
        if (isInitialized && activityManager.hasActivityRecognitionPermission()) {
            activityManager.startTracking()
            updateDisplay()
            handler.post(updateRunnable)
        }
    }
    
    fun onPause() {
        handler.removeCallbacks(updateRunnable)
        // Keep service tracking, but stop widget internal tracking
        if (isInitialized) {
            activityManager.stopTracking()
        }
    }
    
    fun cleanup() {
        onPause()
        if (isInitialized) {
            activityManager.cleanup()
        }
    }
}
