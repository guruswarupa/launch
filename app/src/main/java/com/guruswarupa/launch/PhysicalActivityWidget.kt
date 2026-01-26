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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
        statsViewContainer = widgetView.findViewById(R.id.stats_view_container)
        calendarViewContainer = widgetView.findViewById(R.id.calendar_view_container)
        
        // Stats container is already in stats_view_container in the layout
        
        // Initialize activity manager
        activityManager = PhysicalActivityManager(context)
        
        // Setup toggle button
        viewToggleButton.setOnClickListener {
            toggleView()
        }
        
        // Initialize calendar view
        initializeCalendarView()
        
        // Check permission and setup UI
        if (activityManager.hasActivityRecognitionPermission()) {
            setupWithPermission()
        } else {
            setupWithoutPermission()
        }
        
        isInitialized = true
    }
    
    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.workout_calendar_view, calendarViewContainer, false)
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
        
        val df = DecimalFormat("#.##")
        val stepsFormatted = String.format("%,d", activityData.steps)
        val distanceFormatted = df.format(activityData.distanceKm)
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(displayDate)
            .setMessage("Steps: $stepsFormatted\nDistance: $distanceFormatted km")
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
            // Start the service when permission is granted
            startTrackingService()
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
