package com.guruswarupa.launch.widgets

/**
 * Coordinates the lifecycle of multiple widgets by delegating onResume, onPause and onDestroy calls.
 * Also serves as a registry for all widget instances.
 */
class WidgetLifecycleCoordinator {
    lateinit var calculatorWidget: CalculatorWidget
    lateinit var notificationsWidget: NotificationsWidget
    lateinit var workoutWidget: WorkoutWidget
    lateinit var physicalActivityWidget: PhysicalActivityWidget
    lateinit var compassWidget: CompassWidget
    lateinit var pressureWidget: PressureWidget
    lateinit var proximityWidget: ProximityWidget
    lateinit var temperatureWidget: TemperatureWidget
    lateinit var noiseDecibelWidget: NoiseDecibelWidget
    lateinit var calendarEventsWidget: CalendarEventsWidget
    lateinit var countdownWidget: CountdownWidget
    lateinit var yearProgressWidget: YearProgressWidget
    lateinit var githubContributionWidget: GithubContributionWidget
    lateinit var networkStatsWidget: NetworkStatsWidget
    lateinit var deviceInfoWidget: DeviceInfoWidget

    // Initialization check helpers
    fun isNotificationsWidgetInitialized() = ::notificationsWidget.isInitialized
    fun isPhysicalActivityWidgetInitialized() = ::physicalActivityWidget.isInitialized
    fun isCompassWidgetInitialized() = ::compassWidget.isInitialized
    fun isPressureWidgetInitialized() = ::pressureWidget.isInitialized
    fun isProximityWidgetInitialized() = ::proximityWidget.isInitialized
    fun isTemperatureWidgetInitialized() = ::temperatureWidget.isInitialized
    fun isNoiseDecibelWidgetInitialized() = ::noiseDecibelWidget.isInitialized
    fun isCalendarEventsWidgetInitialized() = ::calendarEventsWidget.isInitialized
    fun isCountdownWidgetInitialized() = ::countdownWidget.isInitialized
    fun isYearProgressWidgetInitialized() = ::yearProgressWidget.isInitialized
    fun isGithubContributionWidgetInitialized() = ::githubContributionWidget.isInitialized
    fun isNetworkStatsWidgetInitialized() = ::networkStatsWidget.isInitialized
    fun isDeviceInfoWidgetInitialized() = ::deviceInfoWidget.isInitialized

    private data class WidgetWrapper(
        val isInitialized: () -> Boolean,
        val onResume: () -> Unit,
        val onPause: () -> Unit,
        val onDestroy: () -> Unit
    )

    private val widgets = mutableListOf<WidgetWrapper>()

    /**
     * Register a widget for lifecycle management
     */
    fun register(
        isInitializedCheck: () -> Boolean,
        onResumeAction: () -> Unit,
        onPauseAction: () -> Unit,
        onDestroyAction: () -> Unit = {}
    ) {
        val wrapper = WidgetWrapper(
            isInitialized = isInitializedCheck,
            onResume = {
                if (isInitializedCheck()) {
                    onResumeAction()
                }
            },
            onPause = {
                if (isInitializedCheck()) {
                    onPauseAction()
                }
            },
            onDestroy = {
                if (isInitializedCheck()) {
                    onDestroyAction()
                }
            }
        )
        widgets.add(wrapper)
    }

    /**
     * Setup default lifecycle registrations for all widgets
     */
    fun setupDefaultLifecycle() {
        widgets.clear()
        register({ ::physicalActivityWidget.isInitialized }, { physicalActivityWidget.onResume() }, { physicalActivityWidget.onPause() }, { physicalActivityWidget.cleanup() })
        register({ ::compassWidget.isInitialized }, { compassWidget.onResume() }, { compassWidget.onPause() }, { compassWidget.onPause() })
        register({ ::pressureWidget.isInitialized }, { pressureWidget.onResume() }, { pressureWidget.onPause() }, { pressureWidget.cleanup() })
        register({ ::proximityWidget.isInitialized }, { proximityWidget.onResume() }, { proximityWidget.onPause() }, { proximityWidget.cleanup() })
        register({ ::temperatureWidget.isInitialized }, { temperatureWidget.onResume() }, { temperatureWidget.onPause() }, { temperatureWidget.cleanup() })
        register({ ::noiseDecibelWidget.isInitialized }, { noiseDecibelWidget.onResume() }, { noiseDecibelWidget.onPause() }, { noiseDecibelWidget.cleanup() })
        register({ ::calendarEventsWidget.isInitialized }, { calendarEventsWidget.onResume() }, { calendarEventsWidget.onPause() }, { calendarEventsWidget.cleanup() })
        register({ ::countdownWidget.isInitialized }, { countdownWidget.onResume() }, { countdownWidget.onPause() }, { countdownWidget.cleanup() })
        register({ ::githubContributionWidget.isInitialized }, { githubContributionWidget.onResume() }, { githubContributionWidget.onPause() }, { githubContributionWidget.cleanup() })
    }

    /**
     * Call onResume on all registered widgets that are initialized
     */
    fun onResume() {
        widgets.forEach { widget ->
            widget.onResume()
        }
    }

    /**
     * Call onPause on all registered widgets that are initialized
     */
    fun onPause() {
        widgets.forEach { widget ->
            widget.onPause()
        }
    }

    /**
     * Call onDestroy on all registered widgets that are initialized
     */
    fun onDestroy() {
        widgets.forEach { widget ->
            widget.onDestroy()
        }
    }
}
