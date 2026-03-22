package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.core.content.edit


import com.guruswarupa.launch.core.CacheManager
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.core.BroadcastReceiverManager
import com.guruswarupa.launch.core.LifecycleManager
import com.guruswarupa.launch.core.ShareManager

import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.models.Constants

import com.guruswarupa.launch.widgets.WidgetSetupManager
import com.guruswarupa.launch.widgets.WidgetThemeManager
import com.guruswarupa.launch.widgets.WidgetVisibilityManager
import com.guruswarupa.launch.widgets.DeferredWidgetInitializer
import com.guruswarupa.launch.widgets.CalendarEventsWidget
import com.guruswarupa.launch.widgets.CountdownWidget

import com.guruswarupa.launch.utils.TimeDateManager
import com.guruswarupa.launch.utils.WeatherManager
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.utils.TodoAlarmManager
import com.guruswarupa.launch.utils.FinanceWidgetManager
import com.guruswarupa.launch.utils.VoiceCommandHandler
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.utils.FeatureTutorialManager
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import java.util.concurrent.Executors


class MainActivity : FragmentActivity() {
    companion object {
        var instance: MainActivity? = null
            private set
    }

    
    internal lateinit var sharedPreferences: SharedPreferences
    internal val prefsName = "com.guruswarupa.launch.PREFS"
    internal val handler = Handler(Looper.getMainLooper())
    internal val backgroundExecutor = Executors.newFixedThreadPool(4)

    
    internal lateinit var activityInitializer: ActivityInitializer
    val views: MainActivityViews get() = activityInitializer.views

    internal lateinit var appList: MutableList<ResolveInfo>
    internal lateinit var adapter: AppAdapter
    internal lateinit var searchTypeMenuManager: SearchTypeMenuManager
    internal var fullAppList: MutableList<ResolveInfo> = mutableListOf()
        
    
    lateinit var widgetThemeManager: WidgetThemeManager

    
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
    internal lateinit var webAppManager: WebAppManager
    internal lateinit var widgetManager: WidgetManager
    internal lateinit var resultRegistry: MainActivityResultRegistry
    internal var voiceCommandHandler: VoiceCommandHandler? = null

    
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
    internal lateinit var screenPagerManager: ScreenPagerManager
    internal lateinit var contactActionHandler: ContactActionHandler
    internal lateinit var settingsChangeCoordinator: SettingsChangeCoordinator

    
    private var hasAskedDefaultLauncherThisOpen = false

    
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
    fun isTimeDateManagerInitialized() = ::timeDateManager.isInitialized
    fun isViewsInitialized() = ::activityInitializer.isInitialized

    fun isTablet(): Boolean {
        return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    


    internal fun initializeCoreManagers() {
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        favoriteAppManager = FavoriteAppManager(sharedPreferences)
        hiddenAppManager = HiddenAppManager(sharedPreferences)
        webAppManager = WebAppManager(sharedPreferences)
        
        
        appLauncher = AppLauncher(this, packageManager, appLockManager)
        
        
        activityInitializer = ActivityInitializer(this, sharedPreferences, appLauncher)
        
        
        widgetLifecycleCoordinator = WidgetLifecycleCoordinator()
    }
    
    


    fun applyThemeBasedWidgetBackgrounds() {
        if (::settingsChangeCoordinator.isInitialized) {
            settingsChangeCoordinator.applyThemeBasedWidgetBackgrounds()
        }
    }
    
    


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
                refreshRightDrawerWallpaper()
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
                    
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.isNotificationPolicyAccessGranted && 
                        notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        Toast.makeText(this, "DND re-enabled (Focus Mode active)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        broadcastReceiverManager.registerReceivers()
    }
    
    @SuppressLint("MissingPermission")
    internal fun refreshRightDrawerWallpaper() {
        if (!views.isRightDrawerWallpaperInitialized()) return
        
        WallpaperDisplayHelper.applySystemWallpaper(views.rightDrawerWallpaper)
    }

    


    internal fun initializeViews() {
        activityInitializer.initializeViews()
        views.fastScroller.refreshTypography(sharedPreferences)

        usageStatsManager = AppUsageStatsManager(this)
        weatherManager = WeatherManager(this)
        
        
        searchTypeMenuManager = SearchTypeMenuManager(
            context = this,
            searchTypeButton = views.searchTypeButton,
            appSearchManagerProvider = { if (::appSearchManager.isInitialized) appSearchManager else null },
            isFocusModeActive = { if (::appDockManager.isInitialized) appDockManager.getCurrentMode() else false }
        )
        searchTypeMenuManager.setup()

        
        setupSearchBoxListener()
    }
    
    


    private fun setupSearchBoxListener() {
        views.searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    
                    activityInitializer.setHeaderVisibility(false)
                } else {
                    
                    activityInitializer.setHeaderVisibility(true)
                    
                    handler.postDelayed({
                        views.recyclerView.scrollToPosition(0)
                    }, 100)
                }
                updateFastScrollerVisibility()
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    


    internal fun updateFastScrollerVisibility() {
        if (!::sharedPreferences.isInitialized || !views.isSearchBoxInitialized() || !::appList.isInitialized) return
        
        
        val focusMode = if (::appDockManager.isInitialized) appDockManager.getCurrentMode() else false
        val workspaceMode = if (::appDockManager.isInitialized) appDockManager.isWorkspaceModeActive() else false
        views.fastScroller.setFavoritesVisible(!focusMode && !workspaceMode)

        
        if (appList.isNotEmpty()) {
            views.fastScroller.visibility = View.VISIBLE
        } else {
            views.fastScroller.visibility = View.GONE
        }
    }
    
    internal fun getPreferredGridColumns(): Int {
        val minColumns = resources.getInteger(R.integer.grid_columns_min)
        val maxColumns = resources.getInteger(R.integer.grid_columns_max)
        val storedColumns = sharedPreferences.getInt(Constants.Prefs.GRID_COLUMNS, -1)
        return if (storedColumns in minColumns..maxColumns) {
            storedColumns
        } else {
            resources.getInteger(R.integer.app_grid_columns)
        }
    }
    
    


    internal fun requestInitialPermissions(onComplete: () -> Unit = {}) {
        
        permissionManager.requestContactsPermission { 
            contactManager.loadContacts { loadedContacts ->
                if (::appSearchManager.isInitialized) {
                    
                    appSearchManager.updateData(
                        newFullAppList = appSearchManager.getFullAppList(),
                        newHomeAppList = appSearchManager.getHomeAppList(),
                        newContactsList = loadedContacts
                    )
                } else if (!isFinishing && !isDestroyed && ::adapter.isInitialized) {
                    updateAppSearchManager()
                }
            }
            
            permissionManager.requestUsageStatsPermission(usageStatsManager) {
                
                permissionManager.requestDefaultLauncher {
                    hasAskedDefaultLauncherThisOpen = true
                    onComplete()
                }
            }
        }
    }
    
    


    fun startFeatureTutorialAndRequestPermissions() {
        val tutorialManager = FeatureTutorialManager(this, sharedPreferences)
        if (tutorialManager.shouldShowTutorial()) {
            tutorialManager.startTutorial {
                requestInitialPermissions()
            }
        } else {
            requestInitialPermissions()
        }
    }

    


    internal fun initializeTimeDateAndWeather() {
        val use24HourClock = sharedPreferences.getBoolean(Constants.Prefs.CLOCK_24_HOUR_FORMAT, false)
        timeDateManager = TimeDateManager(
            views.timeTextView,
            views.dateTextView,
            views.rightDrawerTime,
            views.rightDrawerDate,
            use24HourClock
        )
        timeDateManager.startUpdates()
        
        widgetSetupManager = WidgetSetupManager(this, usageStatsManager, weatherManager, permissionManager)
        widgetSetupManager.setupWeather(views.weatherIcon, views.weatherText)
    }
    
    


    internal fun initializeDeferredWidgets() {
        val initializer = DeferredWidgetInitializer(
            widgetSetupManager = widgetSetupManager,
            sharedPreferences = sharedPreferences,
            lifecycleManager = lifecycleManager,
            widgetConfigurationManager = widgetConfigurationManager,
            widgetLifecycleCoordinator = widgetLifecycleCoordinator,
            onComplete = {
                
                todoAlarmManager = TodoAlarmManager(this)
                todoManager = TodoManager(this, sharedPreferences, views.todoRecyclerView, views.addTodoButton, todoAlarmManager)
                todoManager.initialize()
                
                
                updateLifecycleManagerWithDeferredWidgets()
            }
        )
        
        initializer.initialize()
        
        
        updateRegistryDependencies()
        
        
        widgetVisibilityManager.update(
            if (widgetLifecycleCoordinator.isYearProgressWidgetInitialized()) widgetLifecycleCoordinator.yearProgressWidget else null,
            if (widgetLifecycleCoordinator.isGithubContributionWidgetInitialized()) widgetLifecycleCoordinator.githubContributionWidget else null)
    }
    
    


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
                isFocusModeActive = { if (::appDockManager.isInitialized) appDockManager.getCurrentMode() else false }
            )
        }
    }
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        AppInitializer(this).initialize()
        if (::systemBarManager.isInitialized) {
            systemBarManager.makeSystemBarsTransparent()
        }
        
        
        if (::contactManager.isInitialized) {
            contactManager.loadContactsEagerly()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        val isHomeOrLauncher = intent.action == Intent.ACTION_MAIN && 
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))

        if (isHomeOrLauncher && ::screenPagerManager.isInitialized) {
            screenPagerManager.openDefaultHomePage(animated = true)
        }

        
        if (intent.getBooleanExtra("request_permissions_after_disclosure", false)) {
            if (!sharedPreferences.getBoolean("initial_permissions_asked", false)) {
                requestInitialPermissions {
                    sharedPreferences.edit { putBoolean("initial_permissions_asked", true) }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.updatePageWidth()
        }
    }
    
    



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
        
        
        lifecycleManager.onBatteryUpdate = { updateBatteryInBackground() }
        lifecycleManager.onUsageUpdate = { updateUsageInBackground() }
        lifecycleManager.onFocusModeApply = { isFocusMode -> applyFocusMode(isFocusMode) }
        lifecycleManager.onLoadApps = { forceRefresh -> appListLoader.loadApps(forceRefresh) }
    }
    
    


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

    fun openWidgetsPage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openLeftPage(animated)
        }
    }

    fun openHomePage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openCenterPage(animated)
        }
    }

    fun openWallpaperPage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openRightPage(animated)
        }
    }

    fun openDefaultHomePage(animated: Boolean = true) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.openDefaultHomePage(animated)
        }
    }

    fun setWidgetsPageLocked(locked: Boolean) {
        if (::screenPagerManager.isInitialized) {
            screenPagerManager.setLeftPageLocked(locked)
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

    private fun handleSettingsUpdate() {
        if (::settingsChangeCoordinator.isInitialized) {
            settingsChangeCoordinator.handleSettingsUpdate()
        }
    }
    
    private fun handlePackageChange(packageName: String?, isRemoved: Boolean) {
        if (packageName == null) return
        
        
        if (::cacheManager.isInitialized) {
            cacheManager.removeMetadata(packageName)
        }
        
        
        if (isRemoved) {
            cacheManager.clearCache()
        }
        
        runOnUiThread {
            appList.removeAll { it.activityInfo.packageName == packageName }
            fullAppList.removeAll { it.activityInfo.packageName == packageName }
            
            if (::appListLoader.isInitialized) {
                appListLoader.clearCache()
            }
            if (::adapter.isInitialized) {
                adapter.updateAppList(appList)
            }
            updateFastScrollerVisibility()
        }
        
        if (!isRemoved) {
            
            if (::appListLoader.isInitialized) {
                appListLoader.clearCache()
            }
            appListLoader.loadApps(forceRefresh = true, fullAppList, appList, if (::adapter.isInitialized) adapter else null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onDestroy()
        }
        
        
        
    }

    
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
            
            gestureHandler.updateGestureExclusion()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onResume()
        }
        
        
        if (sharedPreferences.getBoolean("waiting_for_usage_stats_return", false)) {
            
            sharedPreferences.edit { putBoolean("waiting_for_usage_stats_return", false) }
            
            
            permissionManager.requestDefaultLauncher {
                hasAskedDefaultLauncherThisOpen = true
                
                if (!sharedPreferences.getBoolean("initial_permissions_asked", false)) {
                    sharedPreferences.edit { putBoolean("initial_permissions_asked", true) }
                }
            }
            return 
        }
    }

    override fun onPause() {
        super.onPause()
        if (::lifecycleManager.isInitialized) {
            lifecycleManager.onPause()
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
                    contactManager.loadContacts { loadedContacts ->
                        if (::appSearchManager.isInitialized) {
                            
                            appSearchManager.updateData(
                                newFullAppList = appSearchManager.getFullAppList(),
                                newHomeAppList = appSearchManager.getHomeAppList(),
                                newContactsList = loadedContacts
                            )
                        } else if (!isFinishing && !isDestroyed && ::adapter.isInitialized) {
                            updateAppSearchManager()
                        }
                    }
                },
                onCallPhoneGranted = {  },
                onNotificationGranted = {
                    if (::todoManager.isInitialized) {
                        todoManager.rescheduleTodoAlarms()
                    }
                },
                onStorageGranted = {
                    
                },
                onActivityRecognitionGranted = {
                    if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                        widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                    }
                }
            )

            
            if (requestCode == PermissionManager.CONTACTS_PERMISSION_REQUEST) {
                
                if (!sharedPreferences.getBoolean("initial_permissions_asked", false)) {
                    handler.postDelayed({
                        permissionManager.requestUsageStatsPermission(usageStatsManager) {
                            permissionManager.requestDefaultLauncher {
                                hasAskedDefaultLauncherThisOpen = true
                                sharedPreferences.edit { putBoolean("initial_permissions_asked", true) }
                            }
                        }
                    }, 300)
                }
            }
        }
        
        
        if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::voiceSearchManager.isInitialized) {
                voiceSearchManager.startVoiceSearch()
            }
            if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isNoiseDecibelWidgetInitialized()) {
                widgetLifecycleCoordinator.noiseDecibelWidget.onPermissionGranted()
            }
        }
        
        
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                105 -> if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                }
                CalendarEventsWidget.REQUEST_CODE_CALENDAR_PERMISSION -> if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isCalendarEventsWidgetInitialized()) {
                    widgetLifecycleCoordinator.calendarEventsWidget.onPermissionGranted()
                }
                CountdownWidget.REQUEST_CODE_CALENDAR_PERMISSION -> if (::widgetLifecycleCoordinator.isInitialized && widgetLifecycleCoordinator.isCountdownWidgetInitialized()) {
                    widgetLifecycleCoordinator.countdownWidget.onPermissionGranted()
                }
            }
        }
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        if (::focusModeApplier.isInitialized) {
            focusModeApplier.applyFocusMode(isFocusMode)
        }
    }

    


    internal fun showWidgetConfigurationDialog() {
        resultRegistry.showWidgetConfigurationDialog()
    }

    


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
