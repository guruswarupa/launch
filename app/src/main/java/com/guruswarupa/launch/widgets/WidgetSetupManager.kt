package com.guruswarupa.launch.widgets

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.BatteryManager




class WidgetSetupManager(
    private val activity: MainActivity,
    private val usageStatsManager: AppUsageStatsManager,
    private val weatherManager: com.guruswarupa.launch.utils.WeatherManager,
    private val permissionManager: com.guruswarupa.launch.core.PermissionManager
) {
    companion object {
        private const val TAG = "WidgetSetupManager"
    }
    
    fun setupBatteryAndUsage() {
        
        val batteryPercentageTextView = activity.findViewById<TextView>(R.id.battery_percentage)

        
        val batteryManager = BatteryManager(activity)
        batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }
    }
    
    fun setupWeather(weatherIcon: ImageView, weatherText: TextView) {
        
        weatherManager.updateWeather(weatherIcon, weatherText)
        
        
        val weatherClickListener = View.OnClickListener {
            weatherManager.updateWeather(weatherIcon, weatherText)
        }
        weatherIcon.setOnClickListener(weatherClickListener)
        weatherText.setOnClickListener(weatherClickListener)

        
        val weatherLongClickListener = View.OnLongClickListener {
            weatherManager.showWeatherSettings(weatherIcon, weatherText)
            true
        }
        weatherIcon.setOnLongClickListener(weatherLongClickListener)
        weatherText.setOnLongClickListener(weatherLongClickListener)
    }
    
    fun setupCalculatorWidget(): CalculatorWidget {
        val calculatorContainer = activity.findViewById<android.view.ViewGroup?>(R.id.calculator_widget_container)
        if (calculatorContainer == null) {
            Log.e(TAG, "Calculator widget container not found!")
        }
        val calculatorView = LayoutInflater.from(activity).inflate(R.layout.calculator_widget, calculatorContainer, false)
        calculatorContainer?.addView(calculatorView)
        Log.d(TAG, "Calculator widget setup: container=${calculatorContainer != null}, added=${calculatorView.parent != null}")
        return CalculatorWidget(calculatorView)
    }
    
    fun setupMediaControllerWidget(): MediaControllerWidget {
        val mediaContainer = activity.findViewById<android.view.ViewGroup>(R.id.media_controller_widget_container)
        if (mediaContainer == null) {
            Log.e(TAG, "Media controller widget container not found!")
        }
        val mediaView = LayoutInflater.from(activity).inflate(R.layout.media_controller_widget, mediaContainer, false)
        mediaContainer?.addView(mediaView)
        Log.d(TAG, "Media controller widget setup: container=${mediaContainer != null}, added=${mediaView.parent != null}")
        return MediaControllerWidget(activity, mediaView)
    }
    
    fun setupWorkoutWidget(): WorkoutWidget {
        val workoutContainer = activity.findViewById<ViewGroup>(R.id.workout_widget_container)
        if (workoutContainer == null) {
            Log.e(TAG, "Workout widget container not found!")
        }
        val workoutView = LayoutInflater.from(activity).inflate(R.layout.workout_widget, workoutContainer, false)
        workoutContainer?.addView(workoutView)
        Log.d(TAG, "Workout widget setup: container=${workoutContainer != null}, added=${workoutView.parent != null}")
        return WorkoutWidget(workoutView)
    }
    
    fun setupPhysicalActivityWidget(sharedPreferences: android.content.SharedPreferences): PhysicalActivityWidget {
        val activityContainer = activity.findViewById<android.widget.LinearLayout>(R.id.physical_activity_widget_container)
        if (activityContainer == null) {
            Log.e(TAG, "Physical activity widget container not found!")
        }
        val physicalActivityWidget = PhysicalActivityWidget(activity, activityContainer, sharedPreferences)
        physicalActivityWidget.initialize()
        return physicalActivityWidget
    }
    
    fun setupCompassWidget(sharedPreferences: android.content.SharedPreferences): CompassWidget {
        val compassContainer = activity.findViewById<android.widget.LinearLayout>(R.id.compass_widget_container)
        if (compassContainer == null) {
            Log.e(TAG, "Compass widget container not found!")
        }
        val compassWidget = CompassWidget(activity, compassContainer, sharedPreferences)
        compassWidget.initialize()
        return compassWidget
    }
    
    fun setupPressureWidget(sharedPreferences: android.content.SharedPreferences): PressureWidget {
        val pressureContainer = activity.findViewById<android.widget.LinearLayout>(R.id.pressure_widget_container)
        if (pressureContainer == null) {
            Log.e(TAG, "Pressure widget container not found!")
        }
        val pressureWidget = PressureWidget(activity, container = pressureContainer, sharedPreferences = sharedPreferences)
        pressureWidget.initialize()
        return pressureWidget
    }
    
    fun setupTemperatureWidget(sharedPreferences: android.content.SharedPreferences): TemperatureWidget {
        val temperatureContainer = activity.findViewById<android.widget.LinearLayout>(R.id.temperature_widget_container)
        if (temperatureContainer == null) {
            Log.e(TAG, "Temperature widget container not found!")
        }
        val temperatureWidget = TemperatureWidget(activity, temperatureContainer, sharedPreferences)
        temperatureWidget.initialize()
        return temperatureWidget
    }

    fun setupWeatherForecastWidget(): WeatherForecastWidget {
        val forecastContainer = activity.findViewById<android.widget.LinearLayout>(R.id.weather_forecast_widget_container)
        if (forecastContainer == null) {
            Log.e(TAG, "Weather forecast widget container not found!")
        }
        val forecastWidget = WeatherForecastWidget(activity, forecastContainer)
        forecastWidget.initialize()
        return forecastWidget
    }
    
    fun setupNoiseDecibelWidget(sharedPreferences: android.content.SharedPreferences): NoiseDecibelWidget {
        val noiseContainer = activity.findViewById<android.widget.LinearLayout>(R.id.noise_decibel_widget_container)
        if (noiseContainer == null) {
            Log.e(TAG, "Noise decibel widget container not found!")
        }
        val noiseWidget = NoiseDecibelWidget(activity, noiseContainer, sharedPreferences)
        noiseWidget.initialize()
        return noiseWidget
    }
    
    fun setupCalendarEventsWidget(sharedPreferences: android.content.SharedPreferences): CalendarEventsWidget {
        val calendarContainer = activity.findViewById<android.widget.LinearLayout>(R.id.calendar_events_widget_container)
        if (calendarContainer == null) {
            Log.e(TAG, "Calendar events widget container not found!")
        }
        val calendarWidget = CalendarEventsWidget(activity, calendarContainer, sharedPreferences)
        calendarWidget.initialize()
        return calendarWidget
    }
    
    fun setupCountdownWidget(sharedPreferences: android.content.SharedPreferences): CountdownWidget {
        val countdownContainer = activity.findViewById<android.widget.LinearLayout>(R.id.countdown_widget_container)
        if (countdownContainer == null) {
            Log.e(TAG, "Countdown widget container not found!")
        }
        val countdownWidget = CountdownWidget(activity, countdownContainer, sharedPreferences)
        countdownWidget.initialize()
        return countdownWidget
    }
    
    fun setupDnsWidget(sharedPreferences: android.content.SharedPreferences): DnsWidget {
        val dnsContainer = activity.findViewById<android.widget.LinearLayout>(R.id.dns_widget_container)
        if (dnsContainer == null) {
            Log.e(TAG, "DNS widget container not found!")
        }
        val dnsWidget = DnsWidget(activity, dnsContainer, sharedPreferences)
        dnsWidget.initialize()
        return dnsWidget
    }
    
    fun setupNoteWidget(sharedPreferences: android.content.SharedPreferences): NoteWidget {
        val noteContainer = activity.findViewById<android.widget.LinearLayout>(R.id.note_widget_container)
        if (noteContainer == null) {
            Log.e(TAG, "Note widget container not found!")
        }
        val noteWidget = NoteWidget(activity, noteContainer, sharedPreferences)
        noteWidget.initialize()
        return noteWidget
    }

    fun setupNetworkStatsWidget(): NetworkStatsWidget {
        val container = activity.findViewById<android.widget.LinearLayout>(R.id.network_stats_widget_container)
        if (container == null) {
            Log.e(TAG, "Network stats widget container not found!")
        }
        val widget = NetworkStatsWidget(activity, container)
        widget.initialize()
        return widget
    }

    fun setupDeviceInfoWidget(): DeviceInfoWidget {
        val container = activity.findViewById<android.widget.LinearLayout>(R.id.device_info_widget_container)
        if (container == null) {
            Log.e(TAG, "Device info widget container not found!")
        }
        val widget = DeviceInfoWidget(activity, container)
        widget.initialize()
        return widget
    }
    
    fun setupYearProgressWidget(sharedPreferences: android.content.SharedPreferences): YearProgressWidget {
        val container = activity.findViewById<android.widget.LinearLayout>(R.id.year_progress_widget_container)
        if (container == null) {
            Log.e(TAG, "Year progress widget container not found!")
        }
        val yearProgressWidget = YearProgressWidget(activity, container, sharedPreferences)
        yearProgressWidget.initialize()
        return yearProgressWidget
    }
    
    fun setupWeeklyUsageWidget() {
        
        
    }
    
    fun setupGithubContributionWidget(sharedPreferences: android.content.SharedPreferences): GithubContributionWidget {
        val githubContainer = activity.findViewById<android.widget.LinearLayout>(R.id.github_contributions_widget_container)
        if (githubContainer == null) {
            Log.e(TAG, "GitHub contributions widget container not found!")
        }
        val githubWidget = GithubContributionWidget(activity, githubContainer, sharedPreferences)
        githubWidget.initialize()
        return githubWidget
    }
    
    fun setupBatteryHealthWidget(): BatteryHealthWidget {
        val batteryContainer = activity.findViewById<android.widget.LinearLayout>(R.id.battery_health_widget_container)
        if (batteryContainer == null) {
            Log.e(TAG, "Battery health widget container not found!")
        }
        val widget = BatteryHealthWidget(activity, batteryContainer)
        widget.initialize()
        return widget
    }
    
    fun requestNotificationPermission() {
        permissionManager.requestNotificationPermission()
    }
    
    fun getActivity(): MainActivity {
        return activity
    }
}
