package com.guruswarupa.launch

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
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
            handler.postDelayed(this, 30000) // Update every 30 seconds
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
            showCalibrationDialog()
        }
        
        // Initialize calendar view
        initializeCalendarView()
        
        isInitialized = true
    }
    
    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.physical_activity_calendar_view, calendarViewContainer, false)
        calendarViewContainer.addView(calendarViewLayout)
        calendarView = PhysicalActivityCalendarView(calendarViewLayout, activityManager) { date, activityData ->
            showDayActivityDetails(date, activityData)
        }
    }
    
    private fun showDayActivityDetails(date: String, activityData: ActivityData) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        
        val parsedDate = try {
            dateFormat.parse(date)
        } catch (e: Exception) {
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
        val stepsFormatted = String.format("%,d", activityData.steps)
        val distanceFormatted = df.format(activityData.distanceKm)
        totalStepsText.text = stepsFormatted
        totalDistanceText.text = "$distanceFormatted km"
        
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
                text2.text = "${hourly.steps} steps • $distanceFormattedHourly km"
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
            viewToggleButton.text = "Stats"
            calendarView?.refreshData()
        } else {
            // Show stats, hide calendar
            statsViewContainer.visibility = View.VISIBLE
            calendarViewContainer.visibility = View.GONE
            viewToggleButton.text = "Calendar"
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
        
        // Initial update
        updateDisplay()
        
        // Start periodic updates
        handler.post(updateRunnable)
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
        stepsText.text = "0 steps"
        distanceText.text = "0.00 km"
        
        permissionButton.setOnClickListener {
            requestPermission()
        }
        
        // Check if permission was previously denied
        val permissionDenied = sharedPreferences.getBoolean("activity_recognition_permission_denied", false)
        if (permissionDenied) {
            permissionButton.text = "Enable in Settings"
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
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    fun onPermissionGranted() {
        if (isInitialized) {
            setupWithPermission()
        }
    }
    
    private fun updateDisplay() {
        try {
            val activityData = activityManager.getTodayActivity()
            
            // Format steps
            val stepsFormatted = String.format("%,d", activityData.steps)
            stepsText.text = "$stepsFormatted steps"
            
            // Format distance
            val df = DecimalFormat("#.##")
            val distanceFormatted = df.format(activityData.distanceKm)
            distanceText.text = "$distanceFormatted km"
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    private fun showCalibrationDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_physical_activity_calibration, null)
        val heightInput = dialogView.findViewById<EditText>(R.id.height_input)
        val strideLengthInput = dialogView.findViewById<EditText>(R.id.stride_length_input)
        val currentStrideDisplay = dialogView.findViewById<TextView>(R.id.current_stride_display)
        val resetButton = dialogView.findViewById<Button>(R.id.reset_calibration_button)

        val currentHeight = activityManager.getUserHeightCm()
        val currentStride = activityManager.getStrideLengthMeters()

        if (currentHeight > 0) {
            heightInput.setText(currentHeight.toString())
        }
        currentStrideDisplay.text = String.format(Locale.getDefault(), "Current stride length: %.2f m", currentStride)

        heightInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && heightInput.text.isNotEmpty()) {
                val height = heightInput.text.toString().toIntOrNull()
                if (height != null && height > 0) {
                    val calculatedStride = (height * 0.43) / 100.0
                    strideLengthInput.setText(String.format(Locale.getDefault(), "%.2f", calculatedStride))
                }
            }
        }

        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Calibrate Stride Length")
            .setView(dialogView)
            .setPositiveButton("Save", null) // Set to null to handle manually below
            .setNegativeButton("Cancel", null)
            .create()

        resetButton.setOnClickListener {
            activityManager.resetStrideLengthToDefault()
            heightInput.setText("")
            strideLengthInput.setText("")
            val defaultStride = activityManager.getStrideLengthMeters()
            currentStrideDisplay.text = String.format(Locale.getDefault(), "Current stride length: %.2f m", defaultStride)
            updateDisplay()
            Toast.makeText(context, "Calibration reset to default", Toast.LENGTH_SHORT).show()
        }

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                var saved = false
                
                val heightText = heightInput.text.toString().trim()
                if (heightText.isNotEmpty()) {
                    val height = heightText.toIntOrNull()
                    if (height != null && height > 0 && height < 300) {
                        activityManager.setUserHeightCm(height)
                        saved = true
                    } else {
                        Toast.makeText(context, "Please enter a valid height (1-299 cm)", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                
                val strideText = strideLengthInput.text.toString().trim()
                if (strideText.isNotEmpty()) {
                    val stride = strideText.toDoubleOrNull()
                    if (stride != null && stride > 0 && stride < 2.0) {
                        activityManager.setStrideLengthMeters(stride)
                        saved = true
                    } else {
                        Toast.makeText(context, "Please enter a valid stride length (0.1-2.0 m)", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                
                if (saved) {
                    Toast.makeText(context, "Calibration saved", Toast.LENGTH_SHORT).show()
                    updateDisplay()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Please enter either height or stride length", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
    
    fun onResume() {
        if (isInitialized && activityManager.hasActivityRecognitionPermission()) {
            // Service handles background tracking, widget just displays
            activityManager.startTracking()
            updateDisplay()
            if (isCalendarView) {
                calendarView?.refreshData()
            }
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            // Don't stop tracking - service continues in background
            // Just stop widget updates
            handler.removeCallbacks(updateRunnable)
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
        // Stop the service when widget is cleaned up
        if (isInitialized) {
            stopTrackingService()
        }
    }
}
