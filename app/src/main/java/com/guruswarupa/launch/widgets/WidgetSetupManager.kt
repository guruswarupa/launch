package com.guruswarupa.launch.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.BatteryManager

/**
 * Manages initialization and setup of all widgets
 */
class WidgetSetupManager(
    private val activity: MainActivity,
    private val usageStatsManager: AppUsageStatsManager,
    private val weatherManager: com.guruswarupa.launch.utils.WeatherManager,
    private val permissionManager: com.guruswarupa.launch.core.PermissionManager
) {
    
    fun setupBatteryAndUsage() {
        // Updated: Screen time display removed from main layout to improve privacy and battery
        val batteryPercentageTextView = activity.findViewById<TextView>(R.id.battery_percentage)

        // Get battery percentage using BatteryManager
        val batteryManager = BatteryManager(activity)
        batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }
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

        // Add long click listener to show API settings
        val weatherLongClickListener = View.OnLongClickListener {
            weatherManager.showWeatherSettings(weatherIcon, weatherText)
            true
        }
        weatherIcon.setOnLongClickListener(weatherLongClickListener)
        weatherText.setOnLongClickListener(weatherLongClickListener)
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
        val pressureWidget = PressureWidget(activity, container = pressureContainer, sharedPreferences = sharedPreferences)
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
    
    fun setupNoiseDecibelWidget(sharedPreferences: android.content.SharedPreferences): NoiseDecibelWidget {
        val noiseContainer = activity.findViewById<android.widget.LinearLayout>(R.id.noise_decibel_widget_container)
        val noiseWidget = NoiseDecibelWidget(activity, noiseContainer, sharedPreferences)
        noiseWidget.initialize()
        return noiseWidget
    }
    
    fun setupCalendarEventsWidget(sharedPreferences: android.content.SharedPreferences): CalendarEventsWidget {
        val calendarContainer = activity.findViewById<android.widget.LinearLayout>(R.id.calendar_events_widget_container)
        val calendarWidget = CalendarEventsWidget(activity, calendarContainer, sharedPreferences)
        calendarWidget.initialize()
        return calendarWidget
    }
    
    fun setupCountdownWidget(sharedPreferences: android.content.SharedPreferences): CountdownWidget {
        val countdownContainer = activity.findViewById<android.widget.LinearLayout>(R.id.countdown_widget_container)
        val countdownWidget = CountdownWidget(activity, countdownContainer, sharedPreferences)
        countdownWidget.initialize()
        return countdownWidget
    }

    fun setupNetworkStatsWidget(): NetworkStatsWidget {
        val container = activity.findViewById<android.widget.LinearLayout>(R.id.network_stats_widget_container)
        val widget = NetworkStatsWidget(activity, container)
        widget.initialize()
        return widget
    }

    fun setupDeviceInfoWidget(): DeviceInfoWidget {
        val container = activity.findViewById<android.widget.LinearLayout>(R.id.device_info_widget_container)
        val widget = DeviceInfoWidget(activity, container)
        widget.initialize()
        return widget
    }
    
    fun setupYearProgressWidget(sharedPreferences: android.content.SharedPreferences): YearProgressWidget {
        val container = activity.findViewById<android.widget.LinearLayout>(R.id.year_progress_widget_container)
        val yearProgressWidget = YearProgressWidget(activity, container, sharedPreferences)
        yearProgressWidget.initialize()
        return yearProgressWidget
    }
    
    fun setupWeeklyUsageWidget() {
        // The weekly usage graph is managed by UsageStatsDisplayManager
        // This method exists for consistency with other widget setup methods
    }
    
    fun requestNotificationPermission() {
        permissionManager.requestNotificationPermission()
    }
}
