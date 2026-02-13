package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors


class MainActivity : FragmentActivity() {

    // Core dependencies
    private lateinit var sharedPreferences: SharedPreferences
    private val prefsName = "com.guruswarupa.launch.PREFS"
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newFixedThreadPool(4)

    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var searchBox: EditText
    private lateinit var searchContainer: FrameLayout
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
    private var currentUiMode: Int = 0
        
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
    private lateinit var shareManager: ShareManager
    internal lateinit var appLockManager: AppLockManager
    lateinit var appTimerManager: AppTimerManager
    lateinit var appCategoryManager: AppCategoryManager
    lateinit var favoriteAppManager: FavoriteAppManager
    internal lateinit var hiddenAppManager: HiddenAppManager
    internal var isShowAllAppsMode = false
    private lateinit var widgetManager: WidgetManager
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
    /**
     * Initializes core managers that are needed early in the lifecycle.
     */
    private fun initializeCoreManagers() {
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        appCategoryManager = AppCategoryManager()
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
        // Check if we're in night mode (dark theme)
        val isNightMode = (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Save current UI mode to detect changes
        currentUiMode = resources.configuration.uiMode
        
        // Select appropriate background drawables
        val widgetBackground = if (isNightMode) {
            R.drawable.widget_background_dark // Semi-transparent black
        } else {
            R.drawable.widget_background // Semi-transparent white
        }
        
        // Apply backgrounds to all widget containers
        findViewById<View>(R.id.top_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.widgets_section)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.notifications_widget_container)?.parent?.let { parent ->
            if (parent is View) parent.setBackgroundResource(widgetBackground)
        }
        findViewById<View>(R.id.calendar_events_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.countdown_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.physical_activity_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.compass_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.pressure_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.proximity_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.temperature_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.network_stats_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.device_info_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.noise_decibel_widget_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.workout_widget_container)?.parent?.let { parent ->
            if (parent is View) parent.setBackgroundResource(widgetBackground)
        }
        findViewById<View>(R.id.calculator_widget_container)?.parent?.let { parent ->
            if (parent is View) parent.setBackgroundResource(widgetBackground)
        }
        findViewById<View>(R.id.todo_widget_main_container)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.finance_widget)?.setBackgroundResource(widgetBackground)
        findViewById<View>(R.id.weekly_usage_widget)?.setBackgroundResource(widgetBackground)

        // Apply theme to search box
        if (::searchBox.isInitialized) {
            val searchBg = if (isNightMode) R.drawable.search_box_transparent_bg else R.drawable.search_box_light_bg
            val textColor = ContextCompat.getColor(this, if (isNightMode) R.color.white else R.color.black)
            val hintColor = ContextCompat.getColor(this, if (isNightMode) R.color.gray_light else R.color.gray)
            
            // Apply background to search container if initialized, otherwise to searchBox
            if (::searchContainer.isInitialized) {
                searchContainer.setBackgroundResource(searchBg)
                searchBox.background = null // Keep EditText background transparent
            } else {
                searchBox.setBackgroundResource(searchBg)
            }
            
            searchBox.setTextColor(textColor)
            searchBox.setHintTextColor(hintColor)
            
            // Tint search icon
            val iconColor = if (isNightMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            searchBox.compoundDrawablesRelative[0]?.setTint(iconColor)
            if (::voiceSearchButton.isInitialized) {
                voiceSearchButton.setColorFilter(iconColor)
            }
        }
    }
    
    /**
     * Checks if the UI mode has changed and updates widget backgrounds if needed.
     */
    private fun checkAndUpdateThemeIfNeeded() {
        val newUiMode = resources.configuration.uiMode
        if (newUiMode != currentUiMode) {
            currentUiMode = newUiMode
            applyThemeBasedWidgetBackgrounds()
            // Notify todo manager of theme change
            if (::todoManager.isInitialized) {
                todoManager.onThemeChanged()
            }
        }
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
        // Disable animations to prevent "Tmp detached view" crash during rapid updates
        recyclerView.itemAnimator = null
        voiceSearchButton = findViewById(R.id.voice_search_button)
        appDock = findViewById(R.id.app_dock)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        weeklyUsageGraph = findViewById(R.id.weekly_usage_graph)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherText = findViewById(R.id.weather_text)
        timeTextView = findViewById(R.id.time_widget)
        dateTextView = findViewById(R.id.date_widget)
        topWidgetContainer = findViewById(R.id.top_widget_container)
        
        // Right Drawer Views
        rightDrawerWallpaper = findViewById(R.id.right_drawer_wallpaper)
        rightDrawerTime = findViewById(R.id.right_drawer_time)
        rightDrawerDate = findViewById(R.id.right_drawer_date)

        usageStatsManager = AppUsageStatsManager(this)
        weatherManager = WeatherManager(this)
        
        activityInitializer = ActivityInitializer(this, sharedPreferences, appLauncher)
        activityInitializer.initializeViews(
            searchBox, recyclerView,
            timeTextView, dateTextView
        )
        
        // Setup search box listener to show/hide top widget
        setupSearchBoxListener()
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
                } else {
                    // Hide top widget when searching
                    topWidgetContainer.visibility = View.GONE
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
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
        
        widgetSetupManager = WidgetSetupManager(this, handler, usageStatsManager, weatherManager, permissionManager)
        widgetSetupManager.setupWeather(weatherIcon, weatherText)
    }
    
    /**
     * Initializes widgets that can be deferred to avoid blocking UI.
     */
    private fun initializeDeferredWidgets() {
        widgetSetupManager.setupBatteryAndUsage()
        notificationsWidget = widgetSetupManager.setupNotificationsWidget()
        calculatorWidget = widgetSetupManager.setupCalculatorWidget()
        workoutWidget = widgetSetupManager.setupWorkoutWidget()
        physicalActivityWidget = widgetSetupManager.setupPhysicalActivityWidget(sharedPreferences)
        compassWidget = widgetSetupManager.setupCompassWidget(sharedPreferences)
        pressureWidget = widgetSetupManager.setupPressureWidget(sharedPreferences)
        proximityWidget = widgetSetupManager.setupProximityWidget(sharedPreferences)
        temperatureWidget = widgetSetupManager.setupTemperatureWidget(sharedPreferences)
        noiseDecibelWidget = widgetSetupManager.setupNoiseDecibelWidget(sharedPreferences)
        calendarEventsWidget = widgetSetupManager.setupCalendarEventsWidget(sharedPreferences)
        countdownWidget = widgetSetupManager.setupCountdownWidget(sharedPreferences)
        
        // Initialize new widgets
        val networkStatsWidget = widgetSetupManager.setupNetworkStatsWidget(sharedPreferences)
        val deviceInfoWidget = widgetSetupManager.setupDeviceInfoWidget(sharedPreferences)
        
        lifecycleManager.setNetworkStatsWidget(networkStatsWidget)
        lifecycleManager.setDeviceInfoWidget(deviceInfoWidget)
        
        todoAlarmManager = TodoAlarmManager(this)
        widgetSetupManager.requestNotificationPermission()

        todoRecyclerView = findViewById(R.id.todo_recycler_view)
        addTodoButton = findViewById(R.id.add_todo_button)
        todoManager = TodoManager(this, sharedPreferences, todoRecyclerView, addTodoButton, todoAlarmManager)
        todoManager.initialize()
        
        // Update widget visibility based on configuration
        updateWidgetVisibility()
        
        // Update lifecycle manager with deferred widgets
        updateLifecycleManagerWithDeferredWidgets()
    }
    
    /**
     * Updates the app list UI and AppSearchManager with new data.
     */
    private fun updateAppListUI(
        newAppList: List<ResolveInfo>,
        newFullAppList: List<ResolveInfo>,
        updateSearchManager: Boolean = true
    ) {
        if (isFinishing || isDestroyed) return
        
        appList = newAppList.toMutableList()
        fullAppList = ArrayList(newFullAppList)
        
        if (::adapter.isInitialized) {
            adapter.updateAppList(appList)
        }
        
        recyclerView.visibility = View.VISIBLE
        
        if (updateSearchManager) {
            // Initialize or update AppSearchManager with new app data
            if (!::appSearchManager.isInitialized && !isFinishing && !isDestroyed && ::adapter.isInitialized) {
                updateAppSearchManager()
            } else if (::appSearchManager.isInitialized) {
                updateAppSearchManager()
            }
        }
    }
    
    /**
     * Updates AppSearchManager with current app list state.
     */
    private fun updateAppSearchManager() {
        if (isFinishing || isDestroyed) return
        
        // Always reinitialize to get fresh data (app list, contacts, etc.)
        appSearchManager = AppSearchManager(
            packageManager = packageManager,
            fullAppList = fullAppList,
            adapter = adapter,
            searchBox = searchBox,
            contactsList = contactManager.getContactsList(),
            appMetadataCache = if (::cacheManager.isInitialized) cacheManager.getMetadataCache() else null,
            isAppFiltered = { packageName -> 
                ::appDockManager.isInitialized && appDockManager.isAppHiddenInFocusMode(packageName)
            }
        )
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
        widgetConfigurationManager = WidgetConfigurationManager(this, sharedPreferences)
        
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
        
        // Load metadata cache from CacheManager
        cacheManager.loadAppMetadataFromCache()
        
        // Check if onboarding is needed
        if (onboardingHelper.checkAndStartOnboarding()) {
            return
        }
        
        // Initialize core managers
        initializeCoreManagers()

        // Initialize broadcast receiver manager
        initializeBroadcastReceivers()

        // Initialize views and UI components
        initializeViews()
        
        // Request necessary permissions
        requestInitialPermissions()

        // Initialize time/date and weather widgets
        initializeTimeDateAndWeather()

        // Defer expensive widget initialization to avoid blocking UI
        handler.postDelayed({
            initializeDeferredWidgets()
        }, 100) // Defer by 100ms to let UI render first

        appDockManager = AppDockManager(this, sharedPreferences, appDock, packageManager, favoriteAppManager)
        
        // Apply theme-appropriate widget backgrounds
        applyThemeBasedWidgetBackgrounds()
        
        // Initialize appList before using it (must be initialized before appListLoader)
        appList = mutableListOf()
        fullAppList = mutableListOf()
        
        // Initialize app list manager
        appListManager = AppListManager(appDockManager, favoriteAppManager, hiddenAppManager, cacheManager)
        
        // Initialize app list loader
        appListLoader = AppListLoader(
            this, packageManager, appListManager, appDockManager, favoriteAppManager,
            cacheManager, backgroundExecutor, handler, recyclerView, searchBox, voiceSearchButton, sharedPreferences
        )
        appListLoader.onAppListUpdated = { sortedList, filteredList ->
            updateAppListUI(sortedList, filteredList, updateSearchManager = true)
        }
        appListLoader.onAdapterNeedsUpdate = { isGridMode ->
            recyclerView.layoutManager = if (isGridMode) {
                GridLayoutManager(this, 4)
            } else {
                LinearLayoutManager(this)
            }
            adapter = AppAdapter(this, appList, searchBox, isGridMode, this)
            recyclerView.adapter = adapter
        }

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

        // Initialize usage stats display manager (after adapter is created)
        usageStatsDisplayManager = UsageStatsDisplayManager(this, usageStatsManager, weeklyUsageGraph, adapter, recyclerView, handler)
        
        // Load weekly usage data lazily (only when graph is visible)
        // Defer to avoid blocking initial load
        handler.postDelayed({
            if (::weeklyUsageGraph.isInitialized && weeklyUsageGraph.isVisible) {
                usageStatsDisplayManager.loadWeeklyUsageData()
            }
        }, 300)

        // Load apps in background - will update adapter when ready
        appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        
        // Defer AppSearchManager initialization to avoid blocking
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && ::appDockManager.isInitialized) {
                updateAppSearchManager()
            }
        }, 150)

        // Initialize DrawerLayout and navigation
        drawerLayout = findViewById(R.id.drawer_layout)
        val mainContent = findViewById<FrameLayout>(R.id.main_content)
        gestureHandler = GestureHandler(this, drawerLayout, mainContent)
        gestureHandler.setupTouchListener()
        gestureHandler.setupGestureExclusion()
        
        activityInitializer.setupDrawerLayout(drawerLayout)
        navigationManager = NavigationManager(this, drawerLayout, gestureHandler, handler)
        
        // Add drawer listener to check for theme changes when drawer opens
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                // Check for theme changes when drawer opens
                checkAndUpdateThemeIfNeeded()
            }
            override fun onDrawerClosed(drawerView: View) {}
            override fun onDrawerStateChanged(newState: Int) {}
        })
        
        // Initialize WidgetManager
        val widgetContainer = findViewById<LinearLayout>(R.id.widget_container)
        widgetManager = WidgetManager(this, widgetContainer)
        activityInitializer.setupAddWidgetButton(
            findViewById(R.id.add_widget_button),
            widgetManager,
            ActivityResultHandler.REQUEST_PICK_WIDGET
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
        
        // Set wallpaper for the new right drawer
        handler.postDelayed({
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
        }, 500)

        // Initialize voice search manager
        voiceSearchManager = VoiceSearchManager(this, packageManager, searchBox, permissionManager)
        activityInitializer.setupVoiceSearchButton(voiceSearchButton, voiceSearchManager)
        
        // Initialize usage stats refresh manager
        usageStatsRefreshManager = UsageStatsRefreshManager(
            this, backgroundExecutor, handler, usageStatsManager,
            adapter, recyclerView, weeklyUsageGraph, usageStatsDisplayManager
        )
        
        // Initialize activity result handler (voiceCommandHandler will be set later)
        activityResultHandler = ActivityResultHandler(
            this, searchBox, null, shareManager,
            widgetManager, wallpaperManagerHelper
        ) { navigationManager.blockBackGesturesTemporarily() }
        
        // Initialize focus mode applier
        focusModeApplier = FocusModeApplier(
            this, backgroundExecutor, appListManager, appDockManager,
            searchBox, voiceSearchButton, adapter, fullAppList, appList
        ) { updateAppSearchManager() }

        // Defer finance widget initialization to avoid blocking
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val financeManager = FinanceManager(sharedPreferences)
                val balanceText = findViewById<TextView>(R.id.balance_text)
                val monthlySpentText = findViewById<TextView>(R.id.monthly_spent_text)
                val amountInput = findViewById<EditText>(R.id.amount_input)
                val descriptionInput = findViewById<EditText>(R.id.description_input)
                financeWidgetManager = FinanceWidgetManager(
                    this, sharedPreferences, financeManager,
                    balanceText, monthlySpentText, amountInput, descriptionInput
                )
                financeWidgetManager.setup()
                financeWidgetManager.updateDisplay()
            }
        }, 100)
        
        // Start shake detection service for background quick actions (if enabled)
        updateShakeDetectionService()
        
        // Initialize lifecycle manager
        initializeLifecycleManager()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::navigationManager.isInitialized) {
                    navigationManager.handleBackPressed { 
                        // If navigation manager says we can proceed with standard back
                        // but it's the home screen, we usually don't want to do anything
                    }
                }
            }
        })
        

    }
    
    /**
     * Updates shake detection service based on user preference
     */
    private fun updateShakeDetectionService() {
        val isTorchEnabled = sharedPreferences.getBoolean(Constants.Prefs.SHAKE_TORCH_ENABLED, false)
        if (isTorchEnabled) {
            startShakeDetectionService()
        } else {
            stopShakeDetectionService()
        }
    }
    
    /**
     * Starts the shake detection service for background quick actions
     */
    private fun startShakeDetectionService() {
        val intent = Intent(this, ShakeDetectionService::class.java).apply {
            action = ShakeDetectionService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }
    
    /**
     * Stops the shake detection service
     */
    private fun stopShakeDetectionService() {
        val intent = Intent(this, ShakeDetectionService::class.java).apply {
            action = ShakeDetectionService.ACTION_STOP
        }
        stopService(intent)
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
        appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
    }
    
    fun refreshAppsForWorkspace() {
        appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
    }
    
    fun filterAppsWithoutReload() {
        // Optimized: Filter existing list without reloading from package manager
        if (fullAppList.isEmpty()) {
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
            return
        }
        
        try {
            if (backgroundExecutor.isShutdown || isFinishing || isDestroyed) return
            backgroundExecutor.execute {
                try {
                    val focusMode = appListManager.getFocusMode()
                    val workspaceMode = appListManager.getWorkspaceMode()
                    
                    // First filter by mode (includes hidden apps, focus mode, workspace mode)
                    val modeFilteredApps = appListManager.filterAppsByMode(fullAppList, focusMode, workspaceMode)
                    
                    // Then apply favorites filter
                    val finalAppList = appListManager.applyFavoritesFilter(modeFilteredApps, workspaceMode)
                    
                    // Finally sort
                    val sortedFinalList = appListManager.sortAppsAlphabetically(finalAppList)
                    
                    // Update UI on main thread
                    runOnUiThread {
                        updateAppListUI(sortedFinalList, fullAppList, updateSearchManager = true)
                        appDockManager.refreshFavoriteToggle()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error filtering apps: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Task rejected by executor", e)
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
            
            // Recreate adapter with new mode
            adapter = AppAdapter(this, appList, searchBox, newIsGridMode, this)
            recyclerView.adapter = adapter
            
            // Update app search manager with new adapter
            updateAppSearchManager()
        }
        
        // Update shake detection service based on preference
        updateShakeDetectionService()
        
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
            financeWidgetManager.updateDisplay() // Refresh finance display after reset
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
        }
        
        if (!isRemoved) {
            // Package added or updated - reload apps
            if (::appListLoader.isInitialized) {
                appListLoader.clearCache()
            }
            appListLoader.loadApps(forceRefresh = true, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        }
    }

    // Contact methods moved to VoiceCommandHandler - wrapper methods for AppAdapter compatibility
    fun openWhatsAppChat(contactName: String) {
        // Initialize voice command handler if not already initialized
        if (voiceCommandHandler == null) {
            voiceCommandHandler = VoiceCommandHandler(this, packageManager, contentResolver, searchBox, appList)
        }
        voiceCommandHandler?.openWhatsAppChat(contactName)
    }
    
    fun openSMSChat(contactName: String) {
        // Initialize voice command handler if not already initialized
        if (voiceCommandHandler == null) {
            voiceCommandHandler = VoiceCommandHandler(this, packageManager, contentResolver, searchBox, appList)
        }
        voiceCommandHandler?.openSMSChat(contactName)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ActivityResultHandler.REQUEST_WIDGET_CONFIGURATION && resultCode == Activity.RESULT_OK) {
            updateWidgetVisibility()
            // Refresh calendar widget when it becomes visible
            if (::calendarEventsWidget.isInitialized) {
                val isEnabled = widgetConfigurationManager.isWidgetEnabled("calendar_events_widget_container")
                if (isEnabled) {
                    calendarEventsWidget.refresh()
                }
            }
            // Refresh countdown widget when it becomes visible
            if (::countdownWidget.isInitialized) {
                val isEnabled = widgetConfigurationManager.isWidgetEnabled("countdown_widget_container")
                if (isEnabled) {
                    countdownWidget.refresh()
                }
            }
        }
        
        if (::activityResultHandler.isInitialized) {
            // Initialize voice command handler if needed for voice search result
            if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST && resultCode == Activity.RESULT_OK) {
                if (voiceCommandHandler == null) {
                    voiceCommandHandler = VoiceCommandHandler(this, packageManager, contentResolver, searchBox, appList)
                }
                activityResultHandler.setVoiceCommandHandler(voiceCommandHandler)
            }
            activityResultHandler.handleActivityResult(requestCode, resultCode, data)
        }
    }

    // WhatsApp and SMS methods moved to VoiceCommandHandler

    // Voice command handling moved to VoiceCommandHandler

    // Wallpaper management moved to WallpaperManagerHelper

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
        
        // Cleanup physical activity widget
        if (::physicalActivityWidget.isInitialized) {
            physicalActivityWidget.cleanup()
        }
        
        // Cleanup compass widget
        if (::compassWidget.isInitialized) {
            compassWidget.cleanup()
        }
        
        // Cleanup pressure widget
        if (::pressureWidget.isInitialized) {
            pressureWidget.cleanup()
        }
        
        // Cleanup proximity widget
        if (::proximityWidget.isInitialized) {
            proximityWidget.cleanup()
        }
        
        // Cleanup temperature widget
        if (::temperatureWidget.isInitialized) {
            temperatureWidget.cleanup()
        }
        
        // Cleanup noise decibel widget
        if (::noiseDecibelWidget.isInitialized) {
            noiseDecibelWidget.cleanup()
        }
        
        // Cleanup calendar events widget
        if (::calendarEventsWidget.isInitialized) {
            calendarEventsWidget.cleanup()
        }
        
        // Cleanup countdown widget
        if (::countdownWidget.isInitialized) {
            countdownWidget.cleanup()
        }
        
        // Stop shake detection service
        stopShakeDetectionService()
    }

    // Broadcast receivers moved to BroadcastReceiverManager

    // App launching methods - delegated to AppLauncher
    internal fun launchAppWithLockCheck(packageName: String, appName: String) {
        appLauncher.launchAppWithLockCheck(packageName, appName)
    }

    // Package receiver moved to BroadcastReceiverManager


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
            // Update gesture exclusion rects when window gains focus (e.g., after rotation)
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
        
        // Resume physical activity tracking
        if (::physicalActivityWidget.isInitialized) {
            physicalActivityWidget.onResume()
        }
        
        // Resume compass tracking
        if (::compassWidget.isInitialized) {
            compassWidget.onResume()
        }
        
        // Resume pressure tracking
        if (::pressureWidget.isInitialized) {
            pressureWidget.onResume()
        }
        
        // Resume proximity tracking
        if (::proximityWidget.isInitialized) {
            proximityWidget.onResume()
        }
        
        // Resume temperature tracking
        if (::temperatureWidget.isInitialized) {
            temperatureWidget.onResume()
        }
        
        // Resume noise decibel tracking
        if (::noiseDecibelWidget.isInitialized) {
            noiseDecibelWidget.onPause() // Ensure it's not double-started
            noiseDecibelWidget.onResume()
        }
        
        // Resume calendar events widget
        if (::calendarEventsWidget.isInitialized) {
            calendarEventsWidget.onResume()
        }
        
        // Resume countdown widget
        if (::countdownWidget.isInitialized) {
            countdownWidget.onResume()
        }
        
        // Shake detection service runs in background, no need to start/stop here
        
        // Always refresh app list when resuming to catch any changes (hidden apps, etc.)
        // This ensures unhidden apps appear when returning from settings
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                try {
                    // Force refresh hidden apps cache to ensure we have latest data
                    if (::hiddenAppManager.isInitialized) {
                        hiddenAppManager.forceRefresh()
                    }
                    // Force reload from package manager to ensure all apps are included
                    // This is necessary because unhidden apps need to be in fullAppList
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
        
        // Pause physical activity tracking
        if (::physicalActivityWidget.isInitialized) {
            physicalActivityWidget.onPause()
        }
        
        // Pause compass tracking
        if (::compassWidget.isInitialized) {
            compassWidget.onPause()
        }
        
        // Pause pressure tracking
        if (::pressureWidget.isInitialized) {
            pressureWidget.onResume() // Resume just ensures it's stopped correctly if needed, wait, onPause should stop
            pressureWidget.onPause()
        }
        
        // Pause proximity tracking
        if (::proximityWidget.isInitialized) {
            proximityWidget.onPause()
        }
        
        // Pause temperature tracking
        if (::temperatureWidget.isInitialized) {
            temperatureWidget.onPause()
        }
        
        // Pause noise decibel tracking
        if (::noiseDecibelWidget.isInitialized) {
            noiseDecibelWidget.onPause()
        }
        
        // Pause calendar events widget
        if (::calendarEventsWidget.isInitialized) {
            calendarEventsWidget.onPause()
        }
        
        // Pause countdown widget
        if (::countdownWidget.isInitialized) {
            countdownWidget.onPause()
        }
        
        // Shake detection service runs in background, no need to stop here
    }

    override fun onStop() {
        super.onStop()
        // Avoid modifying view visibility during stop to prevent WindowManager warnings
        // For HOME launcher activities, the system manages window lifecycle
        // and we should avoid interfering with that process
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save state without modifying views to prevent window management issues
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
    
    // Cache methods moved to CacheManager
    
    
    // Cache methods moved to CacheManager
    

    // Permission methods moved to PermissionManager

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
                    contactManager.loadContacts { contacts ->
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
        val intent = Intent(this, WidgetConfigurationActivity::class.java)
        startActivityForResult(intent, ActivityResultHandler.REQUEST_WIDGET_CONFIGURATION)
    }
    
    /**
     * Updates widget visibility based on configuration
     */
    private fun updateWidgetVisibility() {
        val widgets = widgetConfigurationManager.getWidgetOrder()
        
        // Create a map for quick lookup
        val widgetMap = widgets.associateBy { it.id }
        
        // Check if any widgets are enabled
        val hasEnabledWidgets = widgets.any { it.enabled }
        val emptyState = findViewById<View>(R.id.widgets_empty_state)
        emptyState?.visibility = if (hasEnabledWidgets) View.GONE else View.VISIBLE
        
        // Update visibility for each widget
        findViewById<View>(R.id.widgets_section)?.visibility = 
            if (widgetMap["widgets_section"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Notifications widget - the parent LinearLayout contains the container
        val notificationsParent = findViewById<ViewGroup>(R.id.notifications_widget_container)?.parent as? ViewGroup
        notificationsParent?.visibility = if (widgetMap["notifications_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Calendar Events widget
        findViewById<View>(R.id.calendar_events_widget_container)?.visibility = 
            if (widgetMap["calendar_events_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Countdown widget
        findViewById<View>(R.id.countdown_widget_container)?.visibility = 
            if (widgetMap["countdown_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.physical_activity_widget_container)?.visibility = 
            if (widgetMap["physical_activity_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.compass_widget_container)?.visibility = 
            if (widgetMap["compass_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.pressure_widget_container)?.visibility = 
            if (widgetMap["pressure_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.proximity_widget_container)?.visibility = 
            if (widgetMap["proximity_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.temperature_widget_container)?.visibility = 
            if (widgetMap["temperature_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.noise_decibel_widget_container)?.visibility = 
            if (widgetMap["noise_decibel_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Workout widget - the parent LinearLayout contains the container
        val workoutParent = findViewById<ViewGroup>(R.id.workout_widget_container)?.parent as? ViewGroup
        workoutParent?.visibility = if (widgetMap["workout_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Calculator widget - the parent LinearLayout contains the container
        val calculatorParent = findViewById<ViewGroup>(R.id.calculator_widget_container)?.parent as? ViewGroup
        calculatorParent?.visibility = if (widgetMap["calculator_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Todo widget - the parent LinearLayout contains the RecyclerView
        val todoParent = findViewById<ViewGroup>(R.id.todo_recycler_view)?.parent as? ViewGroup
        todoParent?.visibility = if (widgetMap["todo_recycler_view"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.finance_widget)?.visibility = 
            if (widgetMap["finance_widget"]?.enabled == true) View.VISIBLE else View.GONE
        
        findViewById<View>(R.id.weekly_usage_widget)?.visibility = 
            if (widgetMap["weekly_usage_widget"]?.enabled == true) View.VISIBLE else View.GONE
            
        findViewById<View>(R.id.network_stats_widget_container)?.visibility = 
            if (widgetMap["network_stats_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
            
        findViewById<View>(R.id.device_info_widget_container)?.visibility = 
            if (widgetMap["device_info_widget_container"]?.enabled == true) View.VISIBLE else View.GONE
        
        // Reorder widgets - get the parent LinearLayout that contains all widgets
        // Structure: FrameLayout > NestedScrollView > LinearLayout (content)
        val drawer = findViewById<FrameLayout>(R.id.widgets_drawer)
        val scrollView = drawer?.let { view ->
            // Find NestedScrollView (it's a child of the FrameLayout)
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is androidx.core.widget.NestedScrollView) {
                    return@let child
                }
            }
            null
        }
        val contentLayout = scrollView?.getChildAt(0) as? LinearLayout
        
        contentLayout?.let { layout ->
            // Store all views with their widget IDs
            val viewMap = mutableMapOf<String, View>()
            
            widgets.forEach { widget ->
                val view = when (widget.id) {
                    "widgets_section" -> findViewById<View>(R.id.widgets_section)
                    "notifications_widget_container" -> findViewById<ViewGroup>(R.id.notifications_widget_container)?.parent as? View
                    "calendar_events_widget_container" -> findViewById<View>(R.id.calendar_events_widget_container)
                    "countdown_widget_container" -> findViewById<View>(R.id.countdown_widget_container)
                    "physical_activity_widget_container" -> findViewById<View>(R.id.physical_activity_widget_container)
                    "compass_widget_container" -> findViewById<View>(R.id.compass_widget_container)
                    "pressure_widget_container" -> findViewById<View>(R.id.pressure_widget_container)
                    "proximity_widget_container" -> findViewById<View>(R.id.proximity_widget_container)
                    "temperature_widget_container" -> findViewById<View>(R.id.temperature_widget_container)
                    "noise_decibel_widget_container" -> findViewById<View>(R.id.noise_decibel_widget_container)
                    "workout_widget_container" -> findViewById<ViewGroup>(R.id.workout_widget_container)?.parent as? View
                    "calculator_widget_container" -> findViewById<ViewGroup>(R.id.calculator_widget_container)?.parent as? View
                    "todo_recycler_view" -> findViewById<ViewGroup>(R.id.todo_recycler_view)?.parent as? View
                    "finance_widget" -> findViewById<View>(R.id.finance_widget)
                    "weekly_usage_widget" -> findViewById<View>(R.id.weekly_usage_widget)
                    "network_stats_widget_container" -> findViewById<View>(R.id.network_stats_widget_container)
                    "device_info_widget_container" -> findViewById<View>(R.id.device_info_widget_container)
                    else -> null
                }
                view?.let { viewMap[widget.id] = it }
            }
            
            // Remove only the widgets we're managing (in reverse order to avoid index issues)
            val viewsToRemove = viewMap.values.filter { it.parent == layout }.toList()
            viewsToRemove.reversed().forEach { view ->
                layout.removeView(view)
            }
            
            // Add views back in the exact configured order from widgets list
            // This preserves the order set by the user, regardless of enabled/disabled state
            widgets.forEach { widget ->
                viewMap[widget.id]?.let { view ->
                    // View should have no parent after removal, add it back
                    if (view.parent == null) {
                        layout.addView(view)
                    }
                }
            }
        }
    }
    
    // Gesture exclusion methods moved to GestureHandler
}
