package com.guruswarupa.launch

import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * Manages initialization and setup of all widgets
 */
class WidgetSetupManager(
    private val activity: MainActivity,
    private val handler: Handler,
    private val usageStatsManager: AppUsageStatsManager,
    private val weatherManager: WeatherManager,
    private val permissionManager: PermissionManager
) {
    
    fun setupBatteryAndUsage() {
        // Assuming you have TextViews in your layout with these IDs
        val batteryPercentageTextView = activity.findViewById<TextView>(R.id.battery_percentage)
        val screenTimeTextView = activity.findViewById<TextView>(R.id.screen_time)

        // Get battery percentage using BatteryManager
        val batteryManager = BatteryManager(activity)
        batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }

        // Get screen time usage in minutes for today
        val calendar = java.util.Calendar.getInstance()
        // Set to start of current day
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val screenTimeMillis = usageStatsManager.getTotalUsageForPeriod(startTime, endTime)
        val formattedTime = usageStatsManager.formatUsageTime(screenTimeMillis)
        screenTimeTextView?.text = "Screen Time: $formattedTime"
    }
    
    fun setupWeather(weatherIcon: ImageView, weatherText: TextView) {
        // Try to load cached weather first, otherwise show placeholder
        weatherManager.updateWeather(weatherIcon, weatherText)
        
        // Add click listeners to refresh weather when tapped
        val weatherClickListener = View.OnClickListener {
            weatherManager.updateWeather(weatherIcon, weatherText)
        }
        weatherIcon.setOnClickListener(weatherClickListener)
        weatherText.setOnClickListener(weatherClickListener)
    }
    
    fun setupNotificationsWidget(): NotificationsWidget {
        val notificationsContainer = activity.findViewById<ViewGroup>(R.id.notifications_widget_container)
        val notificationsView = LayoutInflater.from(activity).inflate(R.layout.notifications_widget, notificationsContainer, false)
        notificationsContainer.addView(notificationsView)
        return NotificationsWidget(notificationsView)
    }
    
    fun setupCalculatorWidget(): CalculatorWidget {
        val calculatorContainer = activity.findViewById<ViewGroup>(R.id.calculator_widget_container)
        val calculatorView = LayoutInflater.from(activity).inflate(R.layout.calculator_widget, calculatorContainer, false)
        calculatorContainer.addView(calculatorView)
        return CalculatorWidget(calculatorView)
    }
    
    fun setupWorkoutWidget(): WorkoutWidget {
        val workoutContainer = activity.findViewById<ViewGroup>(R.id.workout_widget_container)
        val workoutView = LayoutInflater.from(activity).inflate(R.layout.workout_widget, workoutContainer, false)
        workoutContainer.addView(workoutView)
        return WorkoutWidget(workoutView)
    }
    
    fun setupPhysicalActivityWidget(sharedPreferences: android.content.SharedPreferences): PhysicalActivityWidget {
        val activityContainer = activity.findViewById<android.widget.LinearLayout>(R.id.physical_activity_widget_container)
        val physicalActivityWidget = PhysicalActivityWidget(activity, activityContainer, sharedPreferences)
        physicalActivityWidget.initialize()
        return physicalActivityWidget
    }
    
    fun setupCompassWidget(sharedPreferences: android.content.SharedPreferences): CompassWidget {
        val compassContainer = activity.findViewById<android.widget.LinearLayout>(R.id.compass_widget_container)
        val compassWidget = CompassWidget(activity, compassContainer, sharedPreferences)
        compassWidget.initialize()
        return compassWidget
    }
    
    fun setupPressureWidget(sharedPreferences: android.content.SharedPreferences): PressureWidget {
        val pressureContainer = activity.findViewById<android.widget.LinearLayout>(R.id.pressure_widget_container)
        val pressureWidget = PressureWidget(activity, pressureContainer, sharedPreferences)
        pressureWidget.initialize()
        return pressureWidget
    }
    
    fun setupProximityWidget(sharedPreferences: android.content.SharedPreferences): ProximityWidget {
        val proximityContainer = activity.findViewById<android.widget.LinearLayout>(R.id.proximity_widget_container)
        val proximityWidget = ProximityWidget(activity, proximityContainer, sharedPreferences)
        proximityWidget.initialize()
        return proximityWidget
    }
    
    fun setupTemperatureWidget(sharedPreferences: android.content.SharedPreferences): TemperatureWidget {
        val temperatureContainer = activity.findViewById<android.widget.LinearLayout>(R.id.temperature_widget_container)
        val temperatureWidget = TemperatureWidget(activity, temperatureContainer, sharedPreferences)
        temperatureWidget.initialize()
        return temperatureWidget
    }
    
    fun requestNotificationPermission() {
        permissionManager.requestNotificationPermission()
    }
}
