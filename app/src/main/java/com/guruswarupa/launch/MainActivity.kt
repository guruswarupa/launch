package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentActivity

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
import com.guruswarupa.launch.models.MainActivityViews

import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.widgets.WidgetVisibilityManager
import com.guruswarupa.launch.widgets.DeferredWidgetInitializer

import com.guruswarupa.launch.utils.TimeDateManager
import com.guruswarupa.launch.utils.WeatherManager
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.utils.TodoAlarmManager
import com.guruswarupa.launch.utils.FinanceWidgetManager
import com.guruswarupa.launch.utils.OnboardingHelper
import com.guruswarupa.launch.utils.FeatureTutorialManager
import com.guruswarupa.launch.utils.VoiceCommandHandler
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import java.util.concurrent.Executors


class MainActivity : FragmentActivity() {

    // Core dependencies
    internal lateinit var sharedPreferences: SharedPreferences
    internal val prefsName = "com.guruswarupa.launch.PREFS"
    internal val handler = Handler(Looper.getMainLooper())
    internal val backgroundExecutor = Executors.newFixedThreadPool(4)

    // Modular managers
    internal lateinit var activityInitializer: ActivityInitializer
    val views: MainActivityViews get() = activityInitializer.views
    val drawerLayout: DrawerLayout get() = views.drawerLayout

    internal lateinit var appList: MutableList<ResolveInfo>
    internal lateinit var adapter: AppAdapter
    internal lateinit var searchTypeMenuManager: SearchTypeMenuManager
    internal var fullAppList: MutableList<ResolveInfo> = mutableListOf()
        
    // Theme tracking
    lateinit var widgetThemeManager: WidgetThemeManager

    // Core managers
    internal lateinit var cacheManager: CacheManager
    internal lateinit var permissionManager: PermissionManager
    internal lateinit var systemBarManager: SystemBarManager
    internal lateinit var gestureHandler: GestureHandler
    internal lateinit var broadcastReceiverManager: BroadcastReceiverManager
    internal lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    internal lateinit var appListManager: AppListManager
    internal lateinit var appListLoader: AppListLoader
    internal lateinit var contactManager: ContactManager
    internal lateinit var usageStatsCacheManager: UsageStatsCacheManager
    internal lateinit var onboardingHelper: OnboardingHelper
    internal lateinit var lifecycleManager: LifecycleManager
    internal lateinit var appSearchManager: AppSearchManager
    internal lateinit var appDockManager: AppDockManager
    internal lateinit var usageStatsManager: AppUsageStatsManager
    internal lateinit var weatherManager: WeatherManager
    internal lateinit var timeDateManager: TimeDateManager
    internal lateinit var todoManager: TodoManager
    internal lateinit var todoAlarmManager: TodoAlarmManager
    internal lateinit var financeWidgetManager: FinanceWidgetManager
    internal lateinit var usageStatsDisplayManager: UsageStatsDisplayManager

    internal lateinit var widgetSetupManager: WidgetSetupManager
    internal lateinit var shareManager: ShareManager
    internal lateinit var appLockManager: AppLockManager
    lateinit var appTimerManager: AppTimerManager
    internal lateinit var widgetLifecycleCoordinator: WidgetLifecycleCoordinator
    lateinit var favoriteAppManager: FavoriteAppManager
    internal lateinit var hiddenAppManager: HiddenAppManager
    internal var isShowAllAppsMode = false
    internal lateinit var widgetManager: WidgetManager
    internal lateinit var resultRegistry: MainActivityResultRegistry
    internal lateinit var featureTutorialManager: FeatureTutorialManager
    internal var voiceCommandHandler: VoiceCommandHandler? = null

    // New modular managers
    internal lateinit var appLauncher: AppLauncher
    internal lateinit var voiceSearchManager: VoiceSearchManager
    internal lateinit var usageStatsRefreshManager: UsageStatsRefreshManager
    internal lateinit var activityResultHandler: ActivityResultHandler
    internal lateinit var navigationManager: NavigationManager
    internal lateinit var focusModeApplier: FocusModeApplier
    internal lateinit var widgetConfigurationManager: WidgetConfigurationManager
    internal lateinit var widgetVisibilityManager: WidgetVisibilityManager
    internal lateinit var serviceManager: ServiceManager
    internal lateinit var appListUIUpdater: AppListUIUpdater
    internal lateinit var drawerManager: DrawerManager
    internal lateinit var contactActionHandler: ContactActionHandler
    internal lateinit var settingsChangeCoordinator: SettingsChangeCoordinator

    // Initialization check helpers for AppInitializer
    fun isWidgetManagerInitialized() = ::widgetManager.isInitialized
    fun isAppSearchManagerInitialized() = ::appSearchManager.isInitialized
    fun isActivityResultHandlerInitialized() = ::activityResultHandler.isInitialized
    fun isAdapterInitialized() = ::adapter.isInitialized
    fun isAppDockManagerInitialized() = ::appDockManager.isInitialized
    fun isWallpaperManagerHelperInitialized() = ::wallpaperManagerHelper.isInitialized
    fun isWidgetVisibilityManagerInitialized() = ::widgetVisibilityManager.isInitialized
    fun isWidgetConfigurationManagerInitialized() = ::widgetConfigurationManager.isInitialized
    fun isAppListInitialized() = ::appList.isInitialized
    fun isWidgetLifecycleCoordinatorInitialized() = ::widgetLifecycleCoordinator.isInitialized
    fun isServiceManagerInitialized() = ::serviceManager.isInitialized
    fun isHiddenAppManagerInitialized() = ::hiddenAppManager.isInitialized
    fun isAppListLoaderInitialized() = ::appListLoader.isInitialized
    fun isFinanceWidgetManagerInitialized() = ::financeWidgetManager.isInitialized

    /**
     * Initializes core managers that are needed early in the lifecycle.
     */
    internal fun initializeCoreManagers() {
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        favoriteAppManager = FavoriteAppManager(sharedPreferences)
        hiddenAppManager = HiddenAppManager(sharedPreferences)
        isShowAllAppsMode = favoriteAppManager.isShowAllAppsMode()
        featureTutorialManager = FeatureTutorialManager(this, sharedPreferences)
        
        // Initialize new modular managers
        appLauncher = AppLauncher(this, packageManager, appLockManager)
        
        // Initialize activity initializer
        activityInitializer = ActivityInitializer(this, sharedPreferences, appLauncher)
        
        // Initialize widget lifecycle coordinator early
        widgetLifecycleCoordinator = WidgetLifecycleCoordinator()
    }
    
    /**
     * Applies theme-appropriate backgrounds to all widget containers based on current theme mode.
     */
    fun applyThemeBasedWidgetBackgrounds() {
        if (::settingsChangeCoordinator.isInitialized) {
            settingsChangeCoordinator.applyThemeBasedWidgetBackgrounds()
        }
    }
    
    /**
     * Checks if the UI mode has changed and updates widget backgrounds if needed.
     */
    internal fun checkAndUpdateThemeIfNeeded() {
        if (::widgetThemeManager.isInitialized) {
            widgetThemeManager.checkAndUpdateThemeIfNeeded(
                todoManager = if (::todoManager.isInitialized) todoManager else null,
                appDockManager = if (::appDockManager.isInitialized) appDockManager else null,
                searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
                searchContainer = if (views.isSearchContainerInitialized()) views.searchContainer else null,
                voiceSearchButton = if (views.isVoiceSearchButtonInitialized()) views.voiceSearchButton else null,
                searchTypeButton = if (views.isSearchTypeButtonInitialized()) views.searchTypeButton else null
            )
        }
    }
    
    /**
     * Initializes broadcast receivers.
     */
    internal fun initializeBroadcastReceivers() {
        broadcastReceiverManager = BroadcastReceiverManager(
            this,
            sharedPreferences,
            onSettingsUpdated = { handleSettingsUpdate() },
            onNotificationsUpdated = { 
                if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isNotificationsWidgetInitialized()) {
                    widgetLifecycleCoordinator.notificationsWidget.updateNotifications()
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
                if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
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
    internal fun initializeViews() {
        activityInitializer.initializeViews()

        usageStatsManager = AppUsageStatsManager(this)
        weatherManager = WeatherManager(this)
        
        // Initialize and setup search type menu manager
        searchTypeMenuManager = SearchTypeMenuManager(
            context = this,
            searchTypeButton = views.searchTypeButton,
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
        views.searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    // Show top widget when search is empty
                    views.topWidgetContainer.visibility = View.VISIBLE
                    // Reset margin when top widget is shown
                    val layoutParams = views.searchContainer.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.setMargins(
                        layoutParams.leftMargin,
                        0, // Reset to default margin
                        layoutParams.rightMargin,
                        layoutParams.bottomMargin
                    )
                    views.searchContainer.layoutParams = layoutParams
                    updateFastScrollerVisibility()
                } else {
                    // Hide top widget when searching
                    views.topWidgetContainer.visibility = View.GONE
                    // Add extra margin to compensate for hidden widget and prevent touching navbar
                    val layoutParams = views.searchContainer.layoutParams as ViewGroup.MarginLayoutParams
                    layoutParams.setMargins(
                        layoutParams.leftMargin,
                        resources.getDimensionPixelSize(R.dimen.search_top_margin_when_widget_hidden), // Add margin when widget is hidden
                        layoutParams.rightMargin,
                        layoutParams.bottomMargin
                    )
                    views.searchContainer.layoutParams = layoutParams
                    views.fastScroller.visibility = View.GONE
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /**
     * Updates FastScroller visibility based on view mode and search query
     */
    internal fun updateFastScrollerVisibility() {
        if (!::sharedPreferences.isInitialized || !views.isSearchBoxInitialized() || !::appList.isInitialized) return
        
        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"
        val query = views.searchBox.text.toString().trim()
        
        // Show fast scroller only in list mode when not searching
        if (!isGridMode && query.isEmpty() && appList.isNotEmpty()) {
            views.fastScroller.visibility = View.VISIBLE
        } else {
            views.fastScroller.visibility = View.GONE
        }
    }
    
    /**
     * Requests initial permissions needed by the app.
     */
    internal fun requestInitialPermissions() {
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
    internal fun initializeTimeDateAndWeather() {
        timeDateManager = TimeDateManager(views.timeTextView, views.dateTextView, views.rightDrawerTime, views.rightDrawerDate)
        timeDateManager.startUpdates()
        
        widgetSetupManager = WidgetSetupManager(this, usageStatsManager, weatherManager, permissionManager)
        widgetSetupManager.setupWeather(views.weatherIcon, views.weatherText)
    }
    
    /**
     * Initializes widgets that can be deferred to avoid blocking UI.
     */
    internal fun initializeDeferredWidgets() {
        val initializer = DeferredWidgetInitializer(
            widgetSetupManager = widgetSetupManager,
            sharedPreferences = sharedPreferences,
            lifecycleManager = lifecycleManager,
            widgetConfigurationManager = widgetConfigurationManager,
            widgetLifecycleCoordinator = widgetLifecycleCoordinator,
            onComplete = {
                // Initialize Todo components after widgets are set up
                todoAlarmManager = TodoAlarmManager(this)
                todoManager = TodoManager(this, sharedPreferences, views.todoRecyclerView, views.addTodoButton, todoAlarmManager)
                todoManager.initialize()
                
                // Update lifecycle manager with deferred widgets
                updateLifecycleManagerWithDeferredWidgets()
            }
        )
        
        initializer.initialize()
        
        // Update result registry with the initialized widgets via coordinator
        updateRegistryDependencies()
        
        // Update widget visibility based on configuration
        widgetVisibilityManager.update(
            if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
            if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null
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
                searchBox = views.searchBox,
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
        AppInitializer(this).initialize(savedInstanceState)
    }
    

    
    /**
     * Initializes LifecycleManager with all dependencies.
     * Only sets properties that are already initialized.
     */
    internal fun initializeLifecycleManager() {
        lifecycleManager = LifecycleManager(this, handler, sharedPreferences)
        lifecycleManager.setSystemBarManager(systemBarManager)
        lifecycleManager.setAppLockManager(appLockManager)
        
        if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isNotificationsWidgetInitialized()) {
            lifecycleManager.setNotificationsWidget(widgetLifecycleCoordinator.notificationsWidget)
        }
        
        lifecycleManager.setWallpaperManagerHelper(wallpaperManagerHelper)
        lifecycleManager.setGestureHandler(gestureHandler)
        lifecycleManager.setAppDockManager(appDockManager)
        lifecycleManager.setAdapter(adapter)
        lifecycleManager.setAppList(appList)
        lifecycleManager.setAppListLoader(appListLoader)
        lifecycleManager.setWidgetManager(widgetManager)
        lifecycleManager.setUsageStatsManager(usageStatsManager)
        lifecycleManager.setUsageStatsDisplayManager(usageStatsDisplayManager)
        lifecycleManager.setTimeDateManager(timeDateManager)
        lifecycleManager.setWeeklyUsageGraph(views.weeklyUsageGraph)
        
        if (::todoManager.isInitialized) {
            lifecycleManager.setTodoManager(todoManager)
        }
        
        lifecycleManager.setFeatureTutorialManager(featureTutorialManager)
        lifecycleManager.setBackgroundExecutor(backgroundExecutor)
        lifecycleManager.setWidgetLifecycleCoordinator(widgetLifecycleCoordinator)
        lifecycleManager.setWidgetThemeManager(widgetThemeManager)
        lifecycleManager.setViews(views)
        
        if (::broadcastReceiverManager.isInitialized) {
            lifecycleManager.setBroadcastReceiverManager(broadcastReceiverManager)
        }
        
        lifecycleManager.setShareManager(shareManager)
        
        if (::serviceManager.isInitialized) {
            lifecycleManager.setServiceManager(serviceManager)
        }
        
        lifecycleManager.setAppTimerManager(appTimerManager)
        
        if (::hiddenAppManager.isInitialized) {
            lifecycleManager.setHiddenAppManager(hiddenAppManager)
        }
        
        // Setup callbacks
        lifecycleManager.onBatteryUpdate = { updateBatteryInBackground() }
        lifecycleManager.onUsageUpdate = { updateUsageInBackground() }
        lifecycleManager.onFocusModeApply = { isFocusMode -> applyFocusMode(isFocusMode) }
        lifecycleManager.onLoadApps = { forceRefresh -> appListLoader.loadApps(forceRefresh) }
    }
    
    /**
     * Updates LifecycleManager with deferred widgets after they're initialized.
     */
    private fun updateLifecycleManagerWithDeferredWidgets() {
        if (::lifecycleManager.isInitialized) {
            if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isNotificationsWidgetInitialized()) {
                lifecycleManager.setNotificationsWidget(widgetLifecycleCoordinator.notificationsWidget)
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
        if (::settingsChangeCoordinator.isInitialized) {
            settingsChangeCoordinator.handleSettingsUpdate()
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



    override fun onDestroy() {
        super.onDestroy()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onDestroy()
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
        }
    }

    override fun onPause() {
        super.onPause()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
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
            if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isNoiseDecibelWidgetInitialized()) {
                widgetLifecycleCoordinator.noiseDecibelWidget.onPermissionGranted()
            }
        }
        
        // Handle physical activity permission
        if (requestCode == 105 && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
            }
        }
        
        // Handle calendar permission
        if (requestCode == 101 && // CalendarEventsWidget.REQUEST_CODE_CALENDAR_PERMISSION is 101
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isCalendarEventsWidgetInitialized()) {
                widgetLifecycleCoordinator.calendarEventsWidget.onPermissionGranted()
            }
        }
        
        // Handle calendar permission for countdown widget
        if (requestCode == 102 && // CountdownWidget.REQUEST_CODE_CALENDAR_PERMISSION is 102
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isCountdownWidgetInitialized()) {
                widgetLifecycleCoordinator.countdownWidget.onPermissionGranted()
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
    internal fun showWidgetConfigurationDialog() {
        resultRegistry.showWidgetConfigurationDialog()
    }

    /**
     * Updates the MainActivityResultRegistry with all current dependencies
     */
    fun updateRegistryDependencies() {
        val deps = MainActivityResultRegistry.DependencyContainer(
            widgetManager = if (isWidgetManagerInitialized()) widgetManager else null,
            widgetVisibilityManager = if (isWidgetVisibilityManagerInitialized()) widgetVisibilityManager else null,
            widgetConfigurationManager = if (isWidgetConfigurationManagerInitialized()) widgetConfigurationManager else null,
            voiceCommandHandler = voiceCommandHandler,
            activityResultHandler = if (isActivityResultHandlerInitialized()) activityResultHandler else null,
            packageManager = packageManager,
            contentResolver = contentResolver,
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            appList = if (isAppListInitialized()) appList else null,
            widgetLifecycleCoordinator = if (isWidgetLifecycleCoordinatorInitialized()) widgetLifecycleCoordinator else null
        )
        resultRegistry.setDependencies(deps)
    }
}
