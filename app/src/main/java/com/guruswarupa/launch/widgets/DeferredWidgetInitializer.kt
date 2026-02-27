package com.guruswarupa.launch.widgets

import android.content.SharedPreferences
import com.guruswarupa.launch.core.LifecycleManager
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.widgets.CalculatorWidget
import com.guruswarupa.launch.widgets.NotificationsWidget
import com.guruswarupa.launch.widgets.WorkoutWidget
import com.guruswarupa.launch.widgets.PhysicalActivityWidget
import com.guruswarupa.launch.widgets.CompassWidget
import com.guruswarupa.launch.widgets.PressureWidget
import com.guruswarupa.launch.widgets.ProximityWidget
import com.guruswarupa.launch.widgets.TemperatureWidget
import com.guruswarupa.launch.widgets.NoiseDecibelWidget
import com.guruswarupa.launch.widgets.CalendarEventsWidget
import com.guruswarupa.launch.widgets.CountdownWidget
import com.guruswarupa.launch.widgets.YearProgressWidget
import com.guruswarupa.launch.widgets.GithubContributionWidget
import com.guruswarupa.launch.widgets.NetworkStatsWidget
import com.guruswarupa.launch.widgets.DeviceInfoWidget

data class InitializedWidgets(
    val notificationsWidget: NotificationsWidget,
    val calculatorWidget: CalculatorWidget,
    val workoutWidget: WorkoutWidget,
    val physicalActivityWidget: PhysicalActivityWidget,
    val compassWidget: CompassWidget,
    val pressureWidget: PressureWidget,
    val proximityWidget: ProximityWidget,
    val temperatureWidget: TemperatureWidget,
    val noiseDecibelWidget: NoiseDecibelWidget,
    val calendarEventsWidget: CalendarEventsWidget,
    val countdownWidget: CountdownWidget,
    val yearProgressWidget: YearProgressWidget,
    val githubContributionWidget: GithubContributionWidget,
    val networkStatsWidget: NetworkStatsWidget,
    val deviceInfoWidget: DeviceInfoWidget
)

class DeferredWidgetInitializer(
    private val widgetSetupManager: WidgetSetupManager,
    private val sharedPreferences: SharedPreferences,
    private val lifecycleManager: LifecycleManager,
    private val widgetConfigurationManager: WidgetConfigurationManager,
    private val onComplete: () -> Unit
) {
    
    fun initialize(): InitializedWidgets {
        widgetSetupManager.setupBatteryAndUsage()
        val notificationsWidget = widgetSetupManager.setupNotificationsWidget()
        val calculatorWidget = widgetSetupManager.setupCalculatorWidget()
        val workoutWidget = widgetSetupManager.setupWorkoutWidget()
        val physicalActivityWidget = widgetSetupManager.setupPhysicalActivityWidget(sharedPreferences)
        val compassWidget = widgetSetupManager.setupCompassWidget(sharedPreferences)
        val pressureWidget = widgetSetupManager.setupPressureWidget(sharedPreferences)
        val proximityWidget = widgetSetupManager.setupProximityWidget(sharedPreferences)
        val temperatureWidget = widgetSetupManager.setupTemperatureWidget(sharedPreferences)
        val noiseDecibelWidget = widgetSetupManager.setupNoiseDecibelWidget(sharedPreferences)
        val calendarEventsWidget = widgetSetupManager.setupCalendarEventsWidget(sharedPreferences)
        val countdownWidget = widgetSetupManager.setupCountdownWidget(sharedPreferences)
        
        // Initialize new widgets
        val networkStatsWidget = widgetSetupManager.setupNetworkStatsWidget()
        val deviceInfoWidget = widgetSetupManager.setupDeviceInfoWidget()
        val yearProgressWidget = widgetSetupManager.setupYearProgressWidget(sharedPreferences)
        val githubContributionWidget = widgetSetupManager.setupGithubContributionWidget(sharedPreferences)
        
        lifecycleManager.setNetworkStatsWidget(networkStatsWidget)
        lifecycleManager.setDeviceInfoWidget(deviceInfoWidget)
        
        // Note: Widget lifecycle coordinator is initialized in MainActivity after widgets are assigned
        
        widgetSetupManager.requestNotificationPermission()

        // Update widget visibility based on configuration
        val widgetVisibilityManager = com.guruswarupa.launch.widgets.WidgetVisibilityManager(
            widgetSetupManager.getActivity(),
            widgetConfigurationManager
        )
        widgetVisibilityManager.update(
            yearProgressWidget,
            githubContributionWidget
        )
        
        onComplete()
        
        return InitializedWidgets(
            notificationsWidget = notificationsWidget,
            calculatorWidget = calculatorWidget,
            workoutWidget = workoutWidget,
            physicalActivityWidget = physicalActivityWidget,
            compassWidget = compassWidget,
            pressureWidget = pressureWidget,
            proximityWidget = proximityWidget,
            temperatureWidget = temperatureWidget,
            noiseDecibelWidget = noiseDecibelWidget,
            calendarEventsWidget = calendarEventsWidget,
            countdownWidget = countdownWidget,
            yearProgressWidget = yearProgressWidget,
            githubContributionWidget = githubContributionWidget,
            networkStatsWidget = networkStatsWidget,
            deviceInfoWidget = deviceInfoWidget
        )
    }
}