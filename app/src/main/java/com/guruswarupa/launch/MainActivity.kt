package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Import moved managers
import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.core.BroadcastReceiverManager
import com.guruswarupa.launch.core.LifecycleManager
import com.guruswarupa.launch.core.ShareManager

import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.services.*
import com.guruswarupa.launch.ui.views.*
import com.guruswarupa.launch.ui.activities.AppDataDisclosureActivity

import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.CalculatorWidget
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.widgets.WidgetVisibilityManager
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
import com.guruswarupa.launch.widgets.DeferredWidgetInitializer
import com.guruswarupa.launch.widgets.FinanceWidgetInitializer

import com.guruswarupa.launch.utils.TimeDateManager
import com.guruswarupa.launch.utils.WeatherManager
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.utils.TodoAlarmManager
import com.guruswarupa.launch.utils.FinanceWidgetManager
import com.guruswarupa.launch.utils.OnboardingHelper
import com.guruswarupa.launch.utils.FeatureTutorialManager
import com.guruswarupa.launch.utils.VoiceCommandHandler
import com.guruswarupa.launch.widgets.GithubContributionWidget
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import java.util.concurrent.Executors


class MainActivity : FragmentActivity() {

    // Core dependencies
    private lateinit var sharedPreferences: SharedPreferences
    private val prefsName = "com.guruswarupa.launch.PREFS"
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newFixedThreadPool(4)

    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var fastScroller: FastScroller
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var searchBox: AutoCompleteTextView
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchTypeButton: ImageButton
    private lateinit var searchTypeMenuManager: SearchTypeMenuManager
    private lateinit var appDock: LinearLayout
    private lateinit var wallpaperBackground: ImageView
    private lateinit var weeklyUsageGraph: WeeklyUsageGraphView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherText: TextView
    private lateinit var todoRecyclerView: RecyclerView
    private lateinit var addTodoButton: ImageButton
    private lateinit var voiceSearchButton: ImageButton
    private lateinit var topWidgetContainer: LinearLayout
    private var fullAppList: MutableList<ResolveInfo> = mutableListOf()
        
    // Theme tracking
    lateinit var widgetThemeManager: WidgetThemeManager
        
    // Right Drawer Views
    private lateinit var rightDrawerWallpaper: ImageView
    private lateinit var rightDrawerTime: TextView
    private lateinit var rightDrawerDate: TextView

    // Core managers
    internal lateinit var cacheManager: CacheManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var systemBarManager: SystemBarManager
    internal lateinit var gestureHandler: GestureHandler
    private lateinit var broadcastReceiverManager: BroadcastReceiverManager
    private lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    private lateinit var appListManager: AppListManager
    private lateinit var appListLoader: AppListLoader
    private lateinit var contactManager: ContactManager
    private lateinit var usageStatsCacheManager: UsageStatsCacheManager
    private lateinit var onboardingHelper: OnboardingHelper
    private lateinit var lifecycleManager: LifecycleManager
    internal lateinit var appSearchManager: AppSearchManager
    internal lateinit var appDockManager: AppDockManager
    private lateinit var usageStatsManager: AppUsageStatsManager
    private lateinit var weatherManager: WeatherManager
    private lateinit var timeDateManager: TimeDateManager
    private lateinit var todoManager: TodoManager
    private lateinit var todoAlarmManager: TodoAlarmManager
    private lateinit var financeWidgetManager: FinanceWidgetManager
    private lateinit var usageStatsDisplayManager: UsageStatsDisplayManager

    private lateinit var widgetSetupManager: WidgetSetupManager
    private lateinit var calculatorWidget: CalculatorWidget
    private lateinit var notificationsWidget: NotificationsWidget
    private lateinit var workoutWidget: WorkoutWidget
    private lateinit var physicalActivityWidget: PhysicalActivityWidget
    private lateinit var compassWidget: CompassWidget
    private lateinit var pressureWidget: PressureWidget
    private lateinit var proximityWidget: ProximityWidget
    private lateinit var temperatureWidget: TemperatureWidget
    private lateinit var noiseDecibelWidget: NoiseDecibelWidget
    private lateinit var calendarEventsWidget: CalendarEventsWidget
    private lateinit var countdownWidget: CountdownWidget
    private lateinit var yearProgressWidget: YearProgressWidget
    private lateinit var githubContributionWidget: GithubContributionWidget
    private lateinit var shareManager: ShareManager
    internal lateinit var appLockManager: AppLockManager
    lateinit var appTimerManager: AppTimerManager
    private lateinit var widgetLifecycleCoordinator: WidgetLifecycleCoordinator
    lateinit var favoriteAppManager: FavoriteAppManager
    internal lateinit var hiddenAppManager: HiddenAppManager
    internal var isShowAllAppsMode = false
    private lateinit var widgetManager: WidgetManager
    private lateinit var resultRegistry: MainActivityResultRegistry
    internal lateinit var drawerLayout: DrawerLayout
    private lateinit var featureTutorialManager: FeatureTutorialManager
    private var voiceCommandHandler: VoiceCommandHandler? = null

    // New modular managers
    private lateinit var appLauncher: AppLauncher
    private lateinit var voiceSearchManager: VoiceSearchManager
    private lateinit var usageStatsRefreshManager: UsageStatsRefreshManager
    private lateinit var activityResultHandler: ActivityResultHandler
    private lateinit var navigationManager: NavigationManager
    private lateinit var activityInitializer: ActivityInitializer
    private lateinit var focusModeApplier: FocusModeApplier
    private lateinit var widgetConfigurationManager: WidgetConfigurationManager
    private lateinit var widgetVisibilityManager: WidgetVisibilityManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var appListUIUpdater: AppListUIUpdater
    private lateinit var drawerManager: DrawerManager
    private lateinit var contactActionHandler: ContactActionHandler

    /**
     * Initializes core managers that are needed early in the lifecycle.
     */
    private fun initializeCoreManagers() {
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        favoriteAppManager = FavoriteAppManager(sharedPreferences)
        hiddenAppManager = HiddenAppManager(sharedPreferences)
        isShowAllAppsMode = favoriteAppManager.isShowAllAppsMode()
        featureTutorialManager = FeatureTutorialManager(this, sharedPreferences)
        
        // Initialize new modular managers
        appLauncher = AppLauncher(this, packageManager, appLockManager)
    }
    
    /**
     * Applies theme-appropriate backgrounds to all widget containers based on current theme mode.
     */
    fun applyThemeBasedWidgetBackgrounds() {
        widgetThemeManager.apply(
            searchBox = if (::searchBox.isInitialized) searchBox else null,
            searchContainer = if (::searchContainer.isInitialized) searchContainer else null,
            voiceSearchButton = if (::voiceSearchButton.isInitialized) voiceSearchButton else null,
            searchTypeButton = if (::searchTypeButton.isInitialized) searchTypeButton else null,
            appDockManager = if (::appDockManager.isInitialized) appDockManager else null
        )
    }
    
    /**
     * Checks if the UI mode has changed and updates widget backgrounds if needed.
     */
    private fun checkAndUpdateThemeIfNeeded() {
        widgetThemeManager.checkAndUpdateThemeIfNeeded(
            todoManager = if (::todoManager.isInitialized) todoManager else null,
            appDockManager = if (::appDockManager.isInitialized) appDockManager else null,
            searchBox = if (::searchBox.isInitialized) searchBox else null,
            searchContainer = if (::searchContainer.isInitialized) searchContainer else null,
            voiceSearchButton = if (::voiceSearchButton.isInitialized) voiceSearchButton else null,
            searchTypeButton = if (::searchTypeButton.isInitialized) searchTypeButton else null
        )
    }
    
    /**
     * Initializes broadcast receivers.
     */
    private fun initializeBroadcastReceivers() {
        broadcastReceiverManager = BroadcastReceiverManager(
            this,
            sharedPreferences,
            onSettingsUpdated = { handleSettingsUpdate() },
            onNotificationsUpdated = { 
                if (::notificationsWidget.isInitialized) {
                    notificationsWidget.updateNotifications()
                }
            },
            onPackageChanged = { packageName, isRemoved -> handlePackageChange(packageName, isRemoved) },
            onWallpaperChanged = { 
                if (::wallpaperManagerHelper.isInitialized) {
                    wallpaperManagerHelper.clearCache()
                    wallpaperManagerHelper.setWallpaperBackground(forceReload = true)
                }
            },
            onBatteryChanged = { 
                if (::usageStatsRefreshManager.isInitialized) {
                    usageStatsRefreshManager.updateBatteryInBackground()
                }
            },
            onActivityRecognitionPermissionGranted = {
                if (::physicalActivityWidget.isInitialized) {
                    physicalActivityWidget.onPermissionGranted()
                }
            },
            onDndStateChanged = {
                if (::appDockManager.isInitialized && appDockManager.getCurrentMode()) {
                    // Check if Dnd was disabled manually during focus mode
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.isNotificationPolicyAccessGranted && 
                        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        // Re-enable DND
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        Toast.makeText(this, "DND re-enabled (Focus Mode active)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        broadcastReceiverManager.registerReceivers()
    }
    

    
    /**
     * Initializes all view components.
     */
    private fun initializeViews() {
        searchBox = findViewById(R.id.search_box)
        searchContainer = findViewById(R.id.search_container)
        recyclerView = findViewById(R.id.app_list)
        fastScroller = findViewById(R.id.fast_scroller)
        fastScroller.setRecyclerView(recyclerView)
        
        // Disable animations to prevent "Tmp detached view" crash during rapid updates
        recyclerView.itemAnimator = null
        voiceSearchButton = findViewById(R.id.voice_search_button)
        appDock = findViewById(R.id.app_dock)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherText = findViewById(R.id.weather_text)
        timeTextView = findViewById(R.id.time_widget)
        dateTextView = findViewById(R.id.date_widget)
        topWidgetContainer = findViewById(R.id.top_widget_container)
        
        // Right Drawer Views
        rightDrawerWallpaper = findViewById(R.id.right_drawer_wallpaper)
        rightDrawerTime = findViewById(R.id.right_drawer_time)
        rightDrawerDate = findViewById(R.id.right_drawer_date)
        weeklyUsageGraph = findViewById(R.id.weekly_usage_graph)

        usageStatsManager = AppUsageStatsManager(this)
        weatherManager = WeatherManager(this)
        
        activityInitializer = ActivityInitializer(this, sharedPreferences, appLauncher)
        activityInitializer.initializeViews(
            searchBox, recyclerView,
            timeTextView, dateTextView
        )
        
        searchTypeButton = findViewById(R.id.search_type_button)
        
        // Initialize and setup search type menu manager
        searchTypeMenuManager = SearchTypeMenuManager(
            context = this,
            searchTypeButton = searchTypeButton,
            appSearchManager = if (::appSearchManager.isInitialized) appSearchManager else null,
            isFocusModeActive = { appDockManager.getCurrentMode() }
        )
        searchTypeMenuManager.setup()

        // Setup search box listener to show/hide top widget
        setupSearchBoxListener()
    }
    

    
    /**
     * Attempts to turn off the screen. 
     * Prioritizes Accessibility Service (Android P+) to allow biometric unlock.
     * Falls back to Device Admin for older versions.
     */
    private fun lockScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            if (permissionManager.isAccessibilityServiceEnabled()) {
                val locked = ScreenLockAccessibilityService.instance?.lockScreen() ?: false
                if (!locked) {
                    Toast.makeText(this, "Failed to lock using accessibility service", Toast.LENGTH_SHORT).show()
                }
                return
            } else {
                permissionManager.requestAccessibilityPermission()
                return
            }
        }

        // Fallback for pre-Android P
        if (permissionManager.isDeviceAdminActive()) {
            val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            try {
                devicePolicyManager.lockNow()
            } catch (e: Exception) {
                Toast.makeText(this, "Error locking screen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionManager.requestDeviceAdminPermission()
        }
    }
    
    /**
     * Sets up the search box listener to show/hide the top widget based on search text.
     */
    private fun setupSearchBoxListener() {
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    // Show top widget when search is empty
                    topWidgetContainer.visibility = View.VISIBLE
                    // Reset margin when top widget is shown
                    val layoutParams = searchContainer.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.setMargins(
                        layoutParams.leftMargin,
                        0, // Reset to default margin
                        layoutParams.rightMargin,
                        layoutParams.bottomMargin
                    )
                    searchContainer.layoutParams = layoutParams
                    updateFastScrollerVisibility()
                } else {
                    // Hide top widget when searching
                    topWidgetContainer.visibility = View.GONE
                    // Add extra margin to compensate for hidden widget and prevent touching navbar
                    val layoutParams = searchContainer.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.setMargins(
                        layoutParams.leftMargin,
                        resources.getDimensionPixelSize(R.dimen.search_top_margin_when_widget_hidden), // Add margin when widget is hidden
                        layoutParams.rightMargin,
                        layoutParams.bottomMargin
                    )
                    searchContainer.layoutParams = layoutParams
                    fastScroller.visibility = View.GONE
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /**
     * Updates FastScroller visibility based on view mode and search query
     */
    internal fun updateFastScrollerVisibility() {
        if (!::sharedPreferences.isInitialized || !::searchBox.isInitialized || !::appList.isInitialized) return
        
        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"
        val query = searchBox.text.toString().trim()
        
        // Show fast scroller only in list mode when not searching
        if (!isGridMode && query.isEmpty() && appList.isNotEmpty()) {
            fastScroller.visibility = View.VISIBLE
        } else {
            fastScroller.visibility = View.GONE
        }
    }
    
    /**
     * Requests initial permissions needed by the app.
     */
    private fun requestInitialPermissions() {
        permissionManager.requestContactsPermission { 
            contactManager.loadContacts {
                if (::appSearchManager.isInitialized) {
                    appSearchManager.updateContactsList()
                } else if (!isFinishing && !isDestroyed && ::adapter.isInitialized) {
                    updateAppSearchManager()
                }
            }
        }
        permissionManager.requestSmsPermission()
        permissionManager.requestUsageStatsPermission(usageStatsManager)
    }
    
    /**
     * Initializes time/date and weather widgets.
     */
    private fun initializeTimeDateAndWeather() {
        timeDateManager = TimeDateManager(timeTextView, dateTextView, rightDrawerTime, rightDrawerDate)
        timeDateManager.startUpdates()
        
        widgetSetupManager = WidgetSetupManager(this, usageStatsManager, weatherManager, permissionManager)
        widgetSetupManager.setupWeather(weatherIcon, weatherText)
    }
    
    /**
     * Initializes widgets that can be deferred to avoid blocking UI.
     */
    private fun initializeDeferredWidgets() {
        val initializer = DeferredWidgetInitializer(
            widgetSetupManager = widgetSetupManager,
            sharedPreferences = sharedPreferences,
            lifecycleManager = lifecycleManager,
            widgetConfigurationManager = widgetConfigurationManager,
            onComplete = {
                // Initialize Todo components after widgets are set up
                todoAlarmManager = TodoAlarmManager(this)
                todoRecyclerView = findViewById(R.id.todo_recycler_view)
                addTodoButton = findViewById(R.id.add_todo_button)
                todoManager = TodoManager(this, sharedPreferences, todoRecyclerView, addTodoButton, todoAlarmManager)
                todoManager.initialize()
                
                // Update lifecycle manager with deferred widgets
                updateLifecycleManagerWithDeferredWidgets()
            }
        )
        
        val initializedWidgets = initializer.initialize()
        
        // Assign the initialized widgets to our activity fields
        notificationsWidget = initializedWidgets.notificationsWidget
        calculatorWidget = initializedWidgets.calculatorWidget
        workoutWidget = initializedWidgets.workoutWidget
        physicalActivityWidget = initializedWidgets.physicalActivityWidget
        compassWidget = initializedWidgets.compassWidget
        pressureWidget = initializedWidgets.pressureWidget
        proximityWidget = initializedWidgets.proximityWidget
        temperatureWidget = initializedWidgets.temperatureWidget
        noiseDecibelWidget = initializedWidgets.noiseDecibelWidget
        calendarEventsWidget = initializedWidgets.calendarEventsWidget
        countdownWidget = initializedWidgets.countdownWidget
        yearProgressWidget = initializedWidgets.yearProgressWidget
        githubContributionWidget = initializedWidgets.githubContributionWidget
        
        // Update result registry with the initialized widgets
        resultRegistry.setDependencies(
            yearProgressWidget = yearProgressWidget,
            githubContributionWidget = githubContributionWidget,
            calendarEventsWidget = calendarEventsWidget,
            countdownWidget = countdownWidget
        )
        
        // Store widget lifecycle coordinator since it's used elsewhere in the activity
        // We need to recreate it here to ensure it has the correct widget instances
        widgetLifecycleCoordinator = WidgetLifecycleCoordinator().apply {
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
        
        // Update widget visibility based on configuration
        widgetVisibilityManager.update(
            if (::yearProgressWidget.isInitialized) yearProgressWidget else null,
            if (::githubContributionWidget.isInitialized) githubContributionWidget else null
        )
    }
    
    /**
     * Updates AppSearchManager with current app list state.
     */
    internal fun updateAppSearchManager(
        newFullList: List<ResolveInfo>? = null,
        newHomeList: List<ResolveInfo>? = null
    ) {
        if (isFinishing || isDestroyed) return
        
        val targetFullList = newFullList ?: fullAppList
        val targetHomeList = newHomeList ?: appList
        
        if (::appSearchManager.isInitialized) {
            appSearchManager.updateData(targetFullList, targetHomeList, contactManager.getContactsList())
        } else {
            // Initial initialization
            appSearchManager = AppSearchManager(
                packageManager = packageManager,
                fullAppList = targetFullList.toMutableList(),
                homeAppList = targetHomeList,
                adapter = if (::adapter.isInitialized) adapter else null,
                searchBox = searchBox,
                contactsList = contactManager.getContactsList(),
                context = this,
                appMetadataCache = if (::cacheManager.isInitialized) cacheManager.getMetadataCache() else null,
                isAppFiltered = { packageName -> 
                    ::appDockManager.isInitialized && appDockManager.isAppHiddenInFocusMode(packageName)
                },
                isFocusModeActive = { appDockManager.getCurrentMode() }
            )
        }
    }
    
    // Gesture exclusion methods moved to GestureHandler

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = getSharedPreferences(prefsName, MODE_PRIVATE)
        
        setContentView(R.layout.activity_main)
        
        // Make status bar and navigation bar transparent (after setContentView, post to ensure window is ready)
        window.decorView.post {
            systemBarManager.makeSystemBarsTransparent()
        }

        // Initialize widget configuration manager
        widgetConfigurationManager = WidgetConfigurationManager(sharedPreferences)
        
        // Initialize widget visibility manager
        widgetVisibilityManager = WidgetVisibilityManager(this, widgetConfigurationManager)
        
        // Initialize result registry
        resultRegistry = MainActivityResultRegistry(this)
        resultRegistry.setDependencies(
            widgetManager = if (::widgetManager.isInitialized) widgetManager else null,
            widgetVisibilityManager = widgetVisibilityManager,
            widgetConfigurationManager = widgetConfigurationManager,
            voiceCommandHandler = voiceCommandHandler,
            packageManager = packageManager,
            contentResolver = contentResolver
        )
        
        // Initialize managers
        cacheManager = CacheManager(this, packageManager, backgroundExecutor)
        permissionManager = PermissionManager(this, sharedPreferences)
        systemBarManager = SystemBarManager(this)
        
        // Initialize new managers
        usageStatsCacheManager = UsageStatsCacheManager(sharedPreferences, backgroundExecutor)
        contactManager = ContactManager(this, contentResolver, backgroundExecutor)
        onboardingHelper = OnboardingHelper(this, sharedPreferences, packageManager, packageName)
        
        // Load usage stats cache immediately
        usageStatsCacheManager.loadCache()
        
        // Load metadata cache from CacheManager asynchronously
        cacheManager.loadAppMetadataFromCacheAsync {
            // Once metadata is loaded, refresh search manager if it's already initialized
            if (::appSearchManager.isInitialized) {
                updateAppSearchManager()
            }
        }
        
        // Check if onboarding is needed
        if (onboardingHelper.checkAndStartOnboarding()) {
            return
        }
        
        // Check if user has given consent for app data collection
        if (!sharedPreferences.getBoolean("app_data_consent_given", false)) {
            // Show disclosure activity
            startActivity(Intent(this, AppDataDisclosureActivity::class.java))
            finish()
            return
        }
        
        // Initialize core managers
        initializeCoreManagers()

        // Initialize broadcast receiver manager
        initializeBroadcastReceivers()

        // Initialize views and UI components
        initializeViews()
        
        // Update result registry with searchBox that is now initialized
        resultRegistry.setDependencies(
            searchBox = searchBox
        )
        
        // Request necessary permissions
        requestInitialPermissions()

        // Initialize time/date and weather widgets
        initializeTimeDateAndWeather()

        // Optimization #7: Reduced delay to make launcher feel more responsive
        handler.postDelayed({
            initializeDeferredWidgets()
        }, 30) // Reduced from 100ms

        appDockManager = AppDockManager(this, sharedPreferences, appDock, packageManager, favoriteAppManager)
        
        // Initialize widget theme manager
        widgetThemeManager = WidgetThemeManager(this) { resources.configuration.uiMode }
        
        // Apply theme-appropriate widget backgrounds
        applyThemeBasedWidgetBackgrounds()
        
        // Initialize appList before using it (must be initialized before appListLoader)
        appList = mutableListOf()
        fullAppList = mutableListOf()
        
        // Update result registry with appList that is now initialized
        resultRegistry.setDependencies(
            appList = appList
        )
        
        // Initialize ContactActionHandler (depends on searchBox and appList)
        contactActionHandler = ContactActionHandler(
            this, packageManager, contentResolver, searchBox, appList
        ) { handler ->
            voiceCommandHandler = handler
            resultRegistry.setDependencies(voiceCommandHandler = handler)
            if (::activityResultHandler.isInitialized) {
                activityResultHandler.setVoiceCommandHandler(handler)
            }
        }
        
        // Initialize app list manager
        appListManager = AppListManager(appDockManager, favoriteAppManager, hiddenAppManager, cacheManager)
        
        // Initialize app list loader
        appListLoader = AppListLoader(
            this, packageManager, appListManager, appDockManager, favoriteAppManager,
            cacheManager, backgroundExecutor, handler, recyclerView, searchBox, voiceSearchButton, sharedPreferences
        )
        
        // Initialize AppListUIUpdater
        appListUIUpdater = AppListUIUpdater(
            this, recyclerView, if (::adapter.isInitialized) adapter else null,
            appList, fullAppList, appListLoader, appDockManager, appListManager,
            handler, backgroundExecutor, searchBox
        )
        appListUIUpdater.setupCallbacks()

        // Refresh apps after appDockManager is fully initialized
        if (!appDockManager.getCurrentMode()) {
            // If focus mode was disabled during init, refresh the apps
            refreshAppsForFocusMode()
        }

        timeTextView.setOnClickListener {
            launchAppWithLockCheck("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            launchAppWithLockCheck("com.google.android.calendar", "Google Calendar")
        }

        // Initialize adapter immediately with empty/cached list for instant UI
        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"
        adapter = AppAdapter(this, appList, searchBox, isGridMode, this)
        recyclerView.adapter = adapter
        recyclerView.visibility = View.VISIBLE
        updateFastScrollerVisibility()
        
        // Update AppListUIUpdater with the initialized adapter
        appListUIUpdater.setAdapter(adapter)

        // Initialize usage stats display manager (after adapter is created)
        usageStatsDisplayManager = UsageStatsDisplayManager(this, usageStatsManager, weeklyUsageGraph, adapter, recyclerView, handler)
        
        // Load apps in background - will update adapter when ready
        appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        
        // Optimization #7: Reduced delay for initialization
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && ::appDockManager.isInitialized) {
                updateAppSearchManager()
            }
        }, 50) // Reduced from 150ms

        // Initialize DrawerLayout and navigation
        drawerLayout = findViewById(R.id.drawer_layout)
        val mainContent = findViewById<FrameLayout>(R.id.main_content)
        gestureHandler = GestureHandler(this, drawerLayout, mainContent)
        
        drawerManager = DrawerManager(
            this, drawerLayout, gestureHandler, usageStatsDisplayManager, activityInitializer,
            themeCheckCallback = { checkAndUpdateThemeIfNeeded() }
        )
        drawerManager.setup()
        navigationManager = drawerManager.navigationManager
        
        // Initialize WidgetManager
        val drawerContentLayout = findViewById<LinearLayout>(R.id.drawer_content_layout)
        widgetManager = WidgetManager(this, drawerContentLayout)
        
        // Update result registry with widget manager
        resultRegistry.setDependencies(widgetManager = widgetManager)
        
        // Update result registry with widgets when they are initialized
        resultRegistry.setDependencies(
            calendarEventsWidget = if (::calendarEventsWidget.isInitialized) calendarEventsWidget else null,
            countdownWidget = if (::countdownWidget.isInitialized) countdownWidget else null
        )
        
        // Setup widget configuration button
        val widgetConfigButton = findViewById<ImageButton>(R.id.widget_config_button)
        val widgetSettingsHeader = findViewById<LinearLayout>(R.id.widget_settings_header)
        val widgetSettingsText = findViewById<TextView>(R.id.widget_settings_text)
        
        widgetConfigButton.setOnClickListener {
            showWidgetConfigurationDialog()
        }
        
        widgetSettingsHeader.setOnClickListener {
            showWidgetConfigurationDialog()
        }
        
        widgetSettingsText.setOnClickListener {
            showWidgetConfigurationDialog()
        }

        // Initialize wallpaper manager helper
        val drawerWallpaper = findViewById<ImageView>(R.id.drawer_wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(this, wallpaperBackground, drawerWallpaper, backgroundExecutor)
        wallpaperManagerHelper.setWallpaperBackground()
        
        // Set wallpaper for the new right drawer immediately
        if (::rightDrawerWallpaper.isInitialized && ::wallpaperManagerHelper.isInitialized) {
            // Since WallpaperManagerHelper currently supports two ImageViews (main and left drawer),
            // we'll manually set the bitmap for the right drawer.
            // In a production app, we should update WallpaperManagerHelper to handle multiple ImageViews.
            try {
                val wallpaperManager = android.app.WallpaperManager.getInstance(this)
                val drawable = wallpaperManager.drawable
                rightDrawerWallpaper.setImageDrawable(drawable)
            } catch (_: Exception) {}
        }

        // Initialize voice search manager
        voiceSearchManager = VoiceSearchManager(this, packageManager)
        // Set up voice search button with new launcher API
        voiceSearchButton.setOnClickListener {
            voiceSearchManager.startVoiceSearchWithLauncher(resultRegistry.voiceSearchLauncher)
        }
        
        voiceSearchButton.setOnLongClickListener {
            voiceSearchManager.triggerSystemAssistant()
            true
        }
        
        // Initialize usage stats refresh manager
        usageStatsRefreshManager = UsageStatsRefreshManager(
            this, backgroundExecutor, usageStatsManager
        )
        
        // Initialize activity result handler (voiceCommandHandler will be set later)
        activityResultHandler = ActivityResultHandler(
            this, searchBox, voiceCommandHandler, shareManager,
            widgetManager, wallpaperManagerHelper,
            onBlockBackGestures = { navigationManager.blockBackGesturesTemporarily() }
        )
        
        // Update result registry with activity result handler
        resultRegistry.setDependencies(activityResultHandler = activityResultHandler)
        
        // Initialize focus mode applier
        focusModeApplier = FocusModeApplier(
            this, backgroundExecutor, appListManager, appDockManager,
            searchBox, voiceSearchButton, searchContainer, if (::adapter.isInitialized) adapter else null, fullAppList, appList,
            onUpdateAppSearchManager = { updateAppSearchManager() }
        )
        
        // Initialize service manager
        serviceManager = ServiceManager(this, sharedPreferences)

        // Initialize FinanceWidget using the new initializer
        FinanceWidgetInitializer(this, sharedPreferences, 100)
            .onInitialized { manager -> 
                financeWidgetManager = manager
            }
            .initialize(handler)
        
        // Start app usage monitor for daily limits
        startService(Intent(this, AppUsageMonitor::class.java))
        
        // Initialize background services through ServiceManager
        serviceManager.updateShakeDetectionService()
        serviceManager.updateScreenDimmerService()
        serviceManager.updateFlipToDndService()
        serviceManager.updateBackTapService()
        
        // Initialize lifecycle manager
        initializeLifecycleManager()
    }
    

    
    /**
     * Initializes LifecycleManager with all dependencies.
     * Only sets properties that are already initialized.
     */
    private fun initializeLifecycleManager() {
        lifecycleManager = LifecycleManager(this, handler, sharedPreferences)
        lifecycleManager.setSystemBarManager(systemBarManager)
        lifecycleManager.setAppLockManager(appLockManager)
        // notificationsWidget is initialized later in initializeDeferredWidgets()
        if (::notificationsWidget.isInitialized) {
            lifecycleManager.setNotificationsWidget(notificationsWidget)
        }
        lifecycleManager.setWallpaperManagerHelper(wallpaperManagerHelper)
        lifecycleManager.setGestureHandler(gestureHandler)
        lifecycleManager.setAppDockManager(appDockManager)
        lifecycleManager.setAdapter(adapter)
        lifecycleManager.setAppList(appList)
        lifecycleManager.setWidgetManager(widgetManager)
        lifecycleManager.setUsageStatsManager(usageStatsManager)
        lifecycleManager.setUsageStatsDisplayManager(usageStatsDisplayManager)
        lifecycleManager.setTimeDateManager(timeDateManager)
        lifecycleManager.setWeeklyUsageGraph(weeklyUsageGraph)
        // todoManager is initialized later in initializeDeferredWidgets()
        if (::todoManager.isInitialized) {
            lifecycleManager.setTodoManager(todoManager)
        }
        lifecycleManager.setFeatureTutorialManager(featureTutorialManager)
        lifecycleManager.setBackgroundExecutor(backgroundExecutor)
        
        // Setup callbacks
        lifecycleManager.onBatteryUpdate = { updateBatteryInBackground() }
        lifecycleManager.onUsageUpdate = { updateUsageInBackground() }
        lifecycleManager.onFocusModeApply = { isFocusMode -> applyFocusMode(isFocusMode) }
        lifecycleManager.onLoadApps = { forceRefresh -> loadApps(forceRefresh) }
    }
    
    /**
     * Updates LifecycleManager with deferred widgets after they're initialized.
     */
    private fun updateLifecycleManagerWithDeferredWidgets() {
        if (::lifecycleManager.isInitialized) {
            if (::notificationsWidget.isInitialized) {
                lifecycleManager.setNotificationsWidget(notificationsWidget)
            }
            if (::todoManager.isInitialized) {
                lifecycleManager.setTodoManager(todoManager)
            }
        }
    }

    fun refreshAppsForFocusMode() {
        if (::appListUIUpdater.isInitialized) {
            appListUIUpdater.refreshAppsForFocusMode()
        }
    }
    
    fun refreshAppsForWorkspace() {
        if (::appListUIUpdater.isInitialized) {
            appListUIUpdater.refreshAppsForWorkspace()
        }
    }
    
    fun filterAppsWithoutReload() {
        if (::appListUIUpdater.isInitialized) {
            appListUIUpdater.filterAppsWithoutReload()
        }
    }

    // Broadcast receivers moved to BroadcastReceiverManager
    
    private fun handleSettingsUpdate() {
        // Update display style if changed
        val viewPreference = sharedPreferences.getString("view_preference", "list") ?: "list"
        val newIsGridMode = viewPreference == "grid"
        val currentIsGridMode = recyclerView.layoutManager is GridLayoutManager
        
        if (newIsGridMode != currentIsGridMode && ::adapter.isInitialized) {
            // Update layout manager
            recyclerView.layoutManager = if (newIsGridMode) {
                GridLayoutManager(this, 4)
            } else {
                LinearLayoutManager(this)
            }
            
            // Optimization #5: Use single adapter and switch mode dynamically
            adapter.updateViewMode(newIsGridMode)
            
            // Only update AppSearchManager if data source significantly changed,
            // otherwise the shared metadata cache handles label updates.
            updateAppSearchManager()
        }
        
        // Update fast scroller visibility
        updateFastScrollerVisibility()

        // Update background services if preferences changed
        serviceManager.updateShakeDetectionService()
        serviceManager.updateScreenDimmerService()
        serviceManager.updateNightModeService()
        serviceManager.updateFlipToDndService()
        serviceManager.updateBackTapService()
        
        // Force refresh hidden apps cache to ensure we have latest data
        if (::hiddenAppManager.isInitialized) {
            hiddenAppManager.forceRefresh()
        }
        
        // Always refresh apps to ensure latest data (hidden apps, favorites, etc.)
        // Force reload from package manager to ensure fullAppList has all apps including newly unhidden ones
        if (::appListLoader.isInitialized) {
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        }
        if (::financeWidgetManager.isInitialized) {
            try {
                financeWidgetManager.updateDisplay() // Refresh finance display after reset
            } catch (_: Exception) {
                // Ignore if finance widget manager fails
            }
        }
        
        // Refresh wallpaper in case it was changed from settings
        if (::wallpaperManagerHelper.isInitialized) {
            wallpaperManagerHelper.clearCache()
            wallpaperManagerHelper.setWallpaperBackground(forceReload = true)
            
            // Update right drawer wallpaper as well
            try {
                val wallpaperManager = android.app.WallpaperManager.getInstance(this)
                val drawable = wallpaperManager.drawable
                rightDrawerWallpaper.setImageDrawable(drawable)
            } catch (_: Exception) {}
        }
    }
    
    private fun handlePackageChange(packageName: String?, isRemoved: Boolean) {
        if (packageName == null) return
        
        // Clear caches for removed package
        if (::cacheManager.isInitialized) {
            cacheManager.removeMetadata(packageName)
        }
        
        // Clear persistent cache files to force refresh
        if (isRemoved) {
            cacheManager.clearCache()
        }
        
        runOnUiThread {
            appList.removeAll { it.activityInfo.packageName == packageName }
            fullAppList.removeAll { it.activityInfo.packageName == packageName }
            // Clear in-memory cache in AppListLoader
            if (::appListLoader.isInitialized) {
                appListLoader.clearCache()
            }
            if (::adapter.isInitialized) {
                adapter.updateAppList(appList)
            }
            updateFastScrollerVisibility()
        }
        
        if (!isRemoved) {
            // Package added or updated - reload apps
            if (::appListLoader.isInitialized) {
                appListLoader.clearCache()
            }
            appListLoader.loadApps(forceRefresh = true, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        }
    }

    // Contact methods delegated to ContactActionHandler
    fun openWhatsAppChat(contactName: String) {
        contactActionHandler.openWhatsAppChat(contactName)
    }
    
    fun openSMSChat(contactName: String) {
        contactActionHandler.openSMSChat(contactName)
    }



    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup wallpaper manager
        if (::wallpaperManagerHelper.isInitialized) {
            wallpaperManagerHelper.cleanup()
        }
        
        // Unregister receivers
        if (::broadcastReceiverManager.isInitialized) {
            broadcastReceiverManager.unregisterReceivers()
        }
        
        // Shutdown executor to prevent memory leaks
        backgroundExecutor.shutdown()
        
        // Cleanup managers
        if (::shareManager.isInitialized) {
            shareManager.cleanup()
        }
        
        // Destroy widget manager
        if (::widgetManager.isInitialized) {
            widgetManager.onDestroy()
        }
        
        // Use widget lifecycle coordinator to cleanup all widgets
        if (::widgetLifecycleCoordinator.isInitialized) {
            widgetLifecycleCoordinator.onDestroy()
        }
        
        // Stop all background services
        serviceManager.stopAllServices()
        
        // Cleanup remaining managers to prevent memory leaks
        if (::appTimerManager.isInitialized) {
            appTimerManager.cleanup()
        }
        
        if (::usageStatsManager.isInitialized) {
            usageStatsManager.cleanup()
        }
        
        // Cancel any pending handler callbacks
        handler.removeCallbacksAndMessages(null)
        
        // Cleanup lifecycle manager
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.cleanup()
        }
    }

    // App launching methods - delegated to AppLauncher
    internal fun launchAppWithLockCheck(packageName: String, appName: String) {
        appLauncher.launchAppWithLockCheck(packageName, appName)
    }


    // Usage stats refresh methods - delegated to UsageStatsRefreshManager
    private fun updateBatteryInBackground() {
        if (::usageStatsRefreshManager.isInitialized) {
            usageStatsRefreshManager.updateBatteryInBackground()
        }
    }

    private fun updateUsageInBackground() {
        if (::usageStatsRefreshManager.isInitialized) {
            usageStatsRefreshManager.updateUsageInBackground()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::gestureHandler.isInitialized) {
            // Update gesture exclusion rect when window gains focus (e.g., after rotation)
            gestureHandler.updateGestureExclusion()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onResume(intent)
        } else {
            // Fallback if lifecycle manager not initialized
            systemBarManager.makeSystemBarsTransparent()
            if (::appDockManager.isInitialized) {
                applyFocusMode(appDockManager.getCurrentMode())
            }
        }
        
        // Check for theme changes and update widget backgrounds if needed
        checkAndUpdateThemeIfNeeded()
        
        // Ensure search box doesn't gain focus when returning to home screen
        if (::searchBox.isInitialized) {
            searchBox.clearFocus()
        }
        
        // Use widget lifecycle coordinator to resume all widgets
        if (::widgetLifecycleCoordinator.isInitialized) {
            widgetLifecycleCoordinator.onResume()
        }
        
        // Always refresh app list when resuming to catch any changes (hidden apps, etc.)
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                try {
                    // Force refresh hidden apps cache to ensure we have latest data
                    if (::hiddenAppManager.isInitialized) {
                        hiddenAppManager.forceRefresh()
                    }
                    // Force reload from package manager to ensure all apps are included
                    if (::appListLoader.isInitialized) {
                        loadApps(forceRefresh = false)
                    }
                } catch (_: UninitializedPropertyAccessException) {
                    // Managers not initialized yet, skip refresh
                }
            }
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onPause()
        } else {
            // Fallback
            if (::timeDateManager.isInitialized) {
                timeDateManager.stopUpdates()
            }
            if (::todoManager.isInitialized) {
                todoManager.saveTodoItems()
            }
        }
        
        // Use widget lifecycle coordinator to pause all widgets
        if (::widgetLifecycleCoordinator.isInitialized) {
            widgetLifecycleCoordinator.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }


    fun loadApps(forceRefresh: Boolean = false) {
        // Sync isShowAllAppsMode with the manager to ensure consistency
        isShowAllAppsMode = favoriteAppManager.isShowAllAppsMode()
        
        // Delegate to AppListLoader
        if (::appListLoader.isInitialized) {
            appListLoader.loadApps(forceRefresh, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        } else {
            Log.w("MainActivity", "loadApps called before appListLoader initialization, retrying...")
            handler.postDelayed({ loadApps(forceRefresh) }, 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (::permissionManager.isInitialized) {
            permissionManager.handlePermissionResult(
                requestCode,
                permissions,
                grantResults,
                onContactsGranted = { 
                    contactManager.loadContacts {
                        if (::appSearchManager.isInitialized) {
                            appSearchManager.updateContactsList()
                        } else if (!isFinishing && !isDestroyed && ::adapter.isInitialized) {
                            // Initialize AppSearchManager if not already initialized
                            updateAppSearchManager()
                        }
                    }
                },
                onCallPhoneGranted = { /* Handle call phone granted */ },
                onNotificationGranted = {
                    if (::todoManager.isInitialized) {
                        todoManager.rescheduleTodoAlarms()
                    }
                }
            )
        }
        
        // Handle voice search permission separately
        if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::voiceSearchManager.isInitialized) {
                voiceSearchManager.startVoiceSearch()
            }
            // Also notify noise decibel widget if permission was granted
            if (::noiseDecibelWidget.isInitialized) {
                noiseDecibelWidget.onPermissionGranted()
            }
        }
        
        // Handle physical activity permission
        if (requestCode == 105 && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::physicalActivityWidget.isInitialized) {
                physicalActivityWidget.onPermissionGranted()
            }
        }
        
        // Handle calendar permission
        if (requestCode == CalendarEventsWidget.REQUEST_CODE_CALENDAR_PERMISSION && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::calendarEventsWidget.isInitialized) {
                calendarEventsWidget.onPermissionGranted()
            }
        }
        
        // Handle calendar permission for countdown widget
        if (requestCode == CountdownWidget.REQUEST_CODE_CALENDAR_PERMISSION && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::countdownWidget.isInitialized) {
                countdownWidget.onPermissionGranted()
            }
        }
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        if (::focusModeApplier.isInitialized) {
            focusModeApplier.applyFocusMode(isFocusMode)
        }
    }

    /**
     * Shows the widget configuration activity
     */
    private fun showWidgetConfigurationDialog() {
        resultRegistry.showWidgetConfigurationDialog()
    }
}
