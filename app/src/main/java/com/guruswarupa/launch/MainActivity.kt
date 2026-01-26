package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
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
import java.util.concurrent.Executors


class MainActivity : FragmentActivity() {

    // Core dependencies
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "com.guruswarupa.launch.PREFS"
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newFixedThreadPool(4)

    // Views
    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var searchBox: EditText
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

    // Core managers
    private lateinit var cacheManager: CacheManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var systemBarManager: SystemBarManager
    private lateinit var gestureHandler: GestureHandler
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
    private lateinit var powerSaverManager: PowerSaverManager
    private lateinit var widgetSetupManager: WidgetSetupManager
    private lateinit var calculatorWidget: CalculatorWidget
    private lateinit var notificationsWidget: NotificationsWidget
    private lateinit var workoutWidget: WorkoutWidget
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
    /**
     * Initializes core managers that are needed early in the lifecycle.
     */
    private fun initializeCoreManagers() {
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        appCategoryManager = AppCategoryManager(packageManager)
        favoriteAppManager = FavoriteAppManager(sharedPreferences)
        hiddenAppManager = HiddenAppManager(sharedPreferences)
        isShowAllAppsMode = favoriteAppManager.isShowAllAppsMode()
        featureTutorialManager = FeatureTutorialManager(this, sharedPreferences)
        
        // Initialize new modular managers
        appLauncher = AppLauncher(this, packageManager, appLockManager, appTimerManager, appCategoryManager)
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
            }
        )
        broadcastReceiverManager.registerReceivers()
    }
    
    /**
     * Initializes all view components.
     */
    private fun initializeViews() {
        searchBox = findViewById(R.id.search_box)
        recyclerView = findViewById(R.id.app_list)
        voiceSearchButton = findViewById(R.id.voice_search_button)
        appDock = findViewById(R.id.app_dock)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        weeklyUsageGraph = findViewById(R.id.weekly_usage_graph)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherText = findViewById(R.id.weather_text)
        timeTextView = findViewById(R.id.time_widget)
        dateTextView = findViewById(R.id.date_widget)
        topWidgetContainer = findViewById(R.id.top_widget_container)

        usageStatsManager = AppUsageStatsManager(this)
        weatherManager = WeatherManager(this)
        
        activityInitializer = ActivityInitializer(this, sharedPreferences, handler, appLauncher)
        activityInitializer.initializeViews(
            searchBox, recyclerView, voiceSearchButton, appDock,
            wallpaperBackground, weeklyUsageGraph, weatherIcon, weatherText,
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
            contactManager.loadContacts { contacts ->
                if (::appSearchManager.isInitialized) {
                    appSearchManager.updateContactsList(contacts)
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
        timeDateManager = TimeDateManager(timeTextView, dateTextView)
        timeDateManager.updateTime()
        timeDateManager.updateDate()
        
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
        todoAlarmManager = TodoAlarmManager(this)
        widgetSetupManager.requestNotificationPermission()

        todoRecyclerView = findViewById(R.id.todo_recycler_view)
        addTodoButton = findViewById(R.id.add_todo_button)
        todoManager = TodoManager(this, sharedPreferences, todoRecyclerView, addTodoButton, todoAlarmManager)
        todoManager.initialize()
        
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
            appList = appList,
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
        setContentView(R.layout.activity_main)
        
        // Make status bar and navigation bar transparent (after setContentView, post to ensure window is ready)
        window.decorView.post {
            systemBarManager.makeSystemBarsTransparent()
        }

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
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

        appDockManager = AppDockManager(this, sharedPreferences, appDock, packageManager, appLockManager, favoriteAppManager)
        
        // Initialize appList before using it (must be initialized before appListLoader)
        appList = mutableListOf()
        fullAppList = mutableListOf()
        
        // Initialize app list manager
        appListManager = AppListManager(packageManager, appDockManager, favoriteAppManager, hiddenAppManager, cacheManager, backgroundExecutor)
        
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
            if (::weeklyUsageGraph.isInitialized && weeklyUsageGraph.visibility == View.VISIBLE) {
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
        
        // Initialize WidgetManager
        val widgetContainer = findViewById<LinearLayout>(R.id.widget_container)
        widgetManager = WidgetManager(this, widgetContainer)
        activityInitializer.setupAddWidgetButton(
            findViewById(R.id.add_widget_button),
            widgetManager,
            ActivityResultHandler.REQUEST_PICK_WIDGET
        )

        // Initialize wallpaper manager helper
        val drawerWallpaper = findViewById<ImageView>(R.id.drawer_wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(this, wallpaperBackground, drawerWallpaper, backgroundExecutor)
        wallpaperManagerHelper.setWallpaperBackground()

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
        lifecycleManager.setTimeDateManager(timeDateManager)
        lifecycleManager.setWeeklyUsageGraph(weeklyUsageGraph)
        lifecycleManager.setUsageStatsDisplayManager(usageStatsDisplayManager)
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
                adapter.appList = appList
                adapter.notifyDataSetChanged()
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

    fun showApkSharingDialog() {
        shareManager.showApkSharingDialog()
    }

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
    }

    // Broadcast receivers moved to BroadcastReceiverManager

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivityForResult(intent, ActivityResultHandler.WALLPAPER_REQUEST_CODE)
    }

    // App launching methods - delegated to AppLauncher
    internal fun launchAppWithLockCheck(packageName: String, appName: String) {
        appLauncher.launchAppWithLockCheck(packageName, appName)
    }

    internal fun launchAppWithTimerCheck(packageName: String, onTimerSet: () -> Unit) {
        appLauncher.launchAppWithTimerCheck(packageName) {
            if (appLockManager.isAppLocked(packageName)) {
                appLockManager.verifyPin { isAuthenticated ->
                    if (isAuthenticated) {
                        onTimerSet()
                    }
                }
            } else {
                onTimerSet()
            }
        }
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
    
    private fun refreshUsageDataInBackground(deferExpensive: Boolean = false) {
        if (::usageStatsRefreshManager.isInitialized) {
            usageStatsRefreshManager.refreshUsageDataInBackground(deferExpensive)
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
                } catch (e: UninitializedPropertyAccessException) {
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
                            appSearchManager.updateContactsList(contacts)
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
        }
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        if (::focusModeApplier.isInitialized) {
            focusModeApplier.applyFocusMode(isFocusMode)
        }
    }



    fun applyPowerSaverMode(isEnabled: Boolean) {
        if (!::powerSaverManager.isInitialized) {
            powerSaverManager = PowerSaverManager(
                this,
                handler,
                adapter,
                recyclerView,
                wallpaperManagerHelper,
                weeklyUsageGraph,
                todoRecyclerView,
                timeDateManager,
                usageStatsDisplayManager,
                { usageStatsRefreshManager.updateBatteryInBackground() },
                { usageStatsRefreshManager.updateUsageInBackground() }
            )
        }
        powerSaverManager.applyPowerSaverMode(isEnabled)
    }
    

    override fun onBackPressed() {
        if (::navigationManager.isInitialized) {
            navigationManager.handleBackPressed { super.onBackPressed() }
        } else {
            super.onBackPressed()
        }
    }
    
    // Gesture exclusion methods moved to GestureHandler
}