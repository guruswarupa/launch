package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.core.content.edit
import com.guruswarupa.launch.di.BackgroundExecutor
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
import com.guruswarupa.launch.ui.RssFeedPage

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
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    companion object {
        private const val EXTRA_START_TUTORIAL = "start_tutorial"

        var instance: MainActivity? = null
            private set
    }

    private val coreManagers = CoreManagers()
    private val dataManagers = DataManagers()
    private val widgetManagers = WidgetManagers()
    private val uiManagers = UIManagers()

    private fun <T> required(value: T?, name: String): T = requireNotNull(value) { "$name not initialized" }

    @Inject
    lateinit var injectedSharedPreferences: SharedPreferences

    @Inject
    @BackgroundExecutor
    lateinit var injectedBackgroundExecutor: ExecutorService

    @Inject
    lateinit var injectedCacheManager: CacheManager

    @Inject
    lateinit var injectedWeatherManager: WeatherManager

    @Inject
    lateinit var injectedAppListManager: AppListManager

    @Inject
    lateinit var injectedAppSearchManager: AppSearchManager

    @Inject
    lateinit var injectedFavoriteAppManager: FavoriteAppManager

    @Inject
    lateinit var injectedHiddenAppManager: HiddenAppManager

    @Inject
    lateinit var injectedWebAppManager: WebAppManager

    internal lateinit var sharedPreferences: SharedPreferences
    internal val prefsName = "com.guruswarupa.launch.PREFS"
    internal val handler = Handler(Looper.getMainLooper())
    internal lateinit var backgroundExecutor: ExecutorService

    internal var activityInitializer: ActivityInitializer
        get() = required(coreManagers.activityInitializer, "ActivityInitializer")
        set(value) {
            coreManagers.activityInitializer = value
        }
    val views: MainActivityViews get() = activityInitializer.views

    internal var appList: MutableList<ResolveInfo>
        get() = required(dataManagers.appList, "App list")
        set(value) {
            dataManagers.appList = value
        }
    internal val appListOrNull: MutableList<ResolveInfo>? get() = dataManagers.appList

    internal var adapter: AppAdapter
        get() = required(uiManagers.adapter, "AppAdapter")
        set(value) {
            uiManagers.adapter = value
        }
    internal val adapterOrNull: AppAdapter? get() = uiManagers.adapter

    internal var searchTypeMenuManager: SearchTypeMenuManager
        get() = required(uiManagers.searchTypeMenuManager, "SearchTypeMenuManager")
        set(value) {
            uiManagers.searchTypeMenuManager = value
        }

    internal var fullAppList: MutableList<ResolveInfo>
        get() = dataManagers.fullAppList
        set(value) {
            dataManagers.fullAppList = value
        }

    internal var widgetThemeManager: WidgetThemeManager
        get() = required(widgetManagers.widgetThemeManager, "WidgetThemeManager")
        set(value) {
            widgetManagers.widgetThemeManager = value
        }
    internal val widgetThemeManagerOrNull: WidgetThemeManager? get() = widgetManagers.widgetThemeManager

    internal var cacheManager: CacheManager
        get() = required(dataManagers.cacheManager, "CacheManager")
        set(value) {
            dataManagers.cacheManager = value
        }

    internal var permissionManager: PermissionManager
        get() = required(coreManagers.permissionManager, "PermissionManager")
        set(value) {
            coreManagers.permissionManager = value
        }

    internal var systemBarManager: SystemBarManager
        get() = required(coreManagers.systemBarManager, "SystemBarManager")
        set(value) {
            coreManagers.systemBarManager = value
        }

    internal var gestureHandler: GestureHandler
        get() = required(uiManagers.gestureHandler, "GestureHandler")
        set(value) {
            uiManagers.gestureHandler = value
        }
    internal val gestureHandlerOrNull: GestureHandler? get() = uiManagers.gestureHandler

    internal var broadcastReceiverManager: BroadcastReceiverManager
        get() = required(coreManagers.broadcastReceiverManager, "BroadcastReceiverManager")
        set(value) {
            coreManagers.broadcastReceiverManager = value
        }
    internal val broadcastReceiverManagerOrNull: BroadcastReceiverManager? get() = coreManagers.broadcastReceiverManager

    internal var wallpaperManagerHelper: WallpaperManagerHelper
        get() = required(uiManagers.wallpaperManagerHelper, "WallpaperManagerHelper")
        set(value) {
            uiManagers.wallpaperManagerHelper = value
        }
    internal val wallpaperManagerHelperOrNull: WallpaperManagerHelper? get() = uiManagers.wallpaperManagerHelper

    internal var appListManager: AppListManager
        get() = required(dataManagers.appListManager, "AppListManager")
        set(value) {
            dataManagers.appListManager = value
        }

    internal var appListLoader: AppListLoader
        get() = required(dataManagers.appListLoader, "AppListLoader")
        set(value) {
            dataManagers.appListLoader = value
        }
    internal val appListLoaderOrNull: AppListLoader? get() = dataManagers.appListLoader

    internal var contactManager: ContactManager
        get() = required(dataManagers.contactManager, "ContactManager")
        set(value) {
            dataManagers.contactManager = value
        }

    internal var usageStatsCacheManager: UsageStatsCacheManager
        get() = required(dataManagers.usageStatsCacheManager, "UsageStatsCacheManager")
        set(value) {
            dataManagers.usageStatsCacheManager = value
        }

    internal var lifecycleManager: LifecycleManager
        get() = required(coreManagers.lifecycleManager, "LifecycleManager")
        set(value) {
            coreManagers.lifecycleManager = value
        }
    internal val lifecycleManagerOrNull: LifecycleManager? get() = coreManagers.lifecycleManager

    internal var appSearchManager: AppSearchManager
        get() = required(dataManagers.appSearchManager, "AppSearchManager")
        set(value) {
            dataManagers.appSearchManager = value
        }
    internal val appSearchManagerOrNull: AppSearchManager? get() = dataManagers.appSearchManager

    internal var appDockManager: AppDockManager
        get() = required(uiManagers.appDockManager, "AppDockManager")
        set(value) {
            uiManagers.appDockManager = value
        }
    internal val appDockManagerOrNull: AppDockManager? get() = uiManagers.appDockManager

    internal var usageStatsManager: AppUsageStatsManager
        get() = required(dataManagers.usageStatsManager, "AppUsageStatsManager")
        set(value) {
            dataManagers.usageStatsManager = value
        }

    internal var weatherManager: WeatherManager
        get() = required(dataManagers.weatherManager, "WeatherManager")
        set(value) {
            dataManagers.weatherManager = value
        }

    internal var timeDateManager: TimeDateManager
        get() = required(dataManagers.timeDateManager, "TimeDateManager")
        set(value) {
            dataManagers.timeDateManager = value
        }
    internal val timeDateManagerOrNull: TimeDateManager? get() = dataManagers.timeDateManager

    internal var todoManager: TodoManager
        get() = required(dataManagers.todoManager, "TodoManager")
        set(value) {
            dataManagers.todoManager = value
        }
    internal val todoManagerOrNull: TodoManager? get() = dataManagers.todoManager

    internal var todoAlarmManager: TodoAlarmManager
        get() = required(dataManagers.todoAlarmManager, "TodoAlarmManager")
        set(value) {
            dataManagers.todoAlarmManager = value
        }

    internal var financeWidgetManager: FinanceWidgetManager
        get() = required(dataManagers.financeWidgetManager, "FinanceWidgetManager")
        set(value) {
            dataManagers.financeWidgetManager = value
        }
    internal val financeWidgetManagerOrNull: FinanceWidgetManager? get() = dataManagers.financeWidgetManager

    internal var usageStatsDisplayManager: UsageStatsDisplayManager
        get() = required(dataManagers.usageStatsDisplayManager, "UsageStatsDisplayManager")
        set(value) {
            dataManagers.usageStatsDisplayManager = value
        }

    internal var rssFeedManager: RssFeedManager
        get() = required(dataManagers.rssFeedManager, "RssFeedManager")
        set(value) {
            dataManagers.rssFeedManager = value
        }

    internal var widgetSetupManager: WidgetSetupManager
        get() = required(widgetManagers.widgetSetupManager, "WidgetSetupManager")
        set(value) {
            widgetManagers.widgetSetupManager = value
        }
    internal val widgetSetupManagerOrNull: WidgetSetupManager? get() = widgetManagers.widgetSetupManager

    internal var shareManager: ShareManager
        get() = required(coreManagers.shareManager, "ShareManager")
        set(value) {
            coreManagers.shareManager = value
        }

    internal var appLockManager: AppLockManager
        get() = required(coreManagers.appLockManager, "AppLockManager")
        set(value) {
            coreManagers.appLockManager = value
        }

    internal var appTimerManager: AppTimerManager
        get() = required(coreManagers.appTimerManager, "AppTimerManager")
        set(value) {
            coreManagers.appTimerManager = value
        }

    internal var widgetLifecycleCoordinator: WidgetLifecycleCoordinator
        get() = required(widgetManagers.widgetLifecycleCoordinator, "WidgetLifecycleCoordinator")
        set(value) {
            widgetManagers.widgetLifecycleCoordinator = value
        }

    internal var favoriteAppManager: FavoriteAppManager
        get() = required(coreManagers.favoriteAppManager, "FavoriteAppManager")
        set(value) {
            coreManagers.favoriteAppManager = value
        }

    internal var hiddenAppManager: HiddenAppManager
        get() = required(coreManagers.hiddenAppManager, "HiddenAppManager")
        set(value) {
            coreManagers.hiddenAppManager = value
        }

    internal var webAppManager: WebAppManager
        get() = required(coreManagers.webAppManager, "WebAppManager")
        set(value) {
            coreManagers.webAppManager = value
        }

    internal var widgetManager: WidgetManager
        get() = required(widgetManagers.widgetManager, "WidgetManager")
        set(value) {
            widgetManagers.widgetManager = value
        }

    internal var resultRegistry: MainActivityResultRegistry
        get() = required(uiManagers.resultRegistry, "MainActivityResultRegistry")
        set(value) {
            uiManagers.resultRegistry = value
        }

    internal var voiceCommandHandler: VoiceCommandHandler? = null

    internal var appLauncher: AppLauncher
        get() = required(coreManagers.appLauncher, "AppLauncher")
        set(value) {
            coreManagers.appLauncher = value
        }

    internal var voiceSearchManager: VoiceSearchManager
        get() = required(uiManagers.voiceSearchManager, "VoiceSearchManager")
        set(value) {
            uiManagers.voiceSearchManager = value
        }
    internal val voiceSearchManagerOrNull: VoiceSearchManager? get() = uiManagers.voiceSearchManager

    internal var usageStatsRefreshManager: UsageStatsRefreshManager
        get() = required(uiManagers.usageStatsRefreshManager, "UsageStatsRefreshManager")
        set(value) {
            uiManagers.usageStatsRefreshManager = value
        }
    internal val usageStatsRefreshManagerOrNull: UsageStatsRefreshManager? get() = uiManagers.usageStatsRefreshManager

    internal var activityResultHandler: ActivityResultHandler
        get() = required(uiManagers.activityResultHandler, "ActivityResultHandler")
        set(value) {
            uiManagers.activityResultHandler = value
        }
    internal val activityResultHandlerOrNull: ActivityResultHandler? get() = uiManagers.activityResultHandler

    internal var navigationManager: NavigationManager
        get() = required(uiManagers.navigationManager, "NavigationManager")
        set(value) {
            uiManagers.navigationManager = value
        }

    internal var focusModeApplier: FocusModeApplier
        get() = required(uiManagers.focusModeApplier, "FocusModeApplier")
        set(value) {
            uiManagers.focusModeApplier = value
        }
    internal val focusModeApplierOrNull: FocusModeApplier? get() = uiManagers.focusModeApplier

    internal var widgetConfigurationManager: WidgetConfigurationManager
        get() = required(widgetManagers.widgetConfigurationManager, "WidgetConfigurationManager")
        set(value) {
            widgetManagers.widgetConfigurationManager = value
        }

    internal var widgetVisibilityManager: WidgetVisibilityManager
        get() = required(widgetManagers.widgetVisibilityManager, "WidgetVisibilityManager")
        set(value) {
            widgetManagers.widgetVisibilityManager = value
        }

    internal var serviceManager: ServiceManager
        get() = required(dataManagers.serviceManager, "ServiceManager")
        set(value) {
            dataManagers.serviceManager = value
        }

    internal var appListUIUpdater: AppListUIUpdater
        get() = required(uiManagers.appListUIUpdater, "AppListUIUpdater")
        set(value) {
            uiManagers.appListUIUpdater = value
        }
    internal val appListUIUpdaterOrNull: AppListUIUpdater? get() = uiManagers.appListUIUpdater

    internal var drawerManager: DrawerManager
        get() = required(uiManagers.drawerManager, "DrawerManager")
        set(value) {
            uiManagers.drawerManager = value
        }

    internal var screenPagerManager: ScreenPagerManager
        get() = required(uiManagers.screenPagerManager, "ScreenPagerManager")
        set(value) {
            uiManagers.screenPagerManager = value
        }
    internal val screenPagerManagerOrNull: ScreenPagerManager? get() = uiManagers.screenPagerManager

    internal var contactActionHandler: ContactActionHandler
        get() = required(uiManagers.contactActionHandler, "ContactActionHandler")
        set(value) {
            uiManagers.contactActionHandler = value
        }

    internal var settingsChangeCoordinator: SettingsChangeCoordinator
        get() = required(uiManagers.settingsChangeCoordinator, "SettingsChangeCoordinator")
        set(value) {
            uiManagers.settingsChangeCoordinator = value
        }
    internal val settingsChangeCoordinatorOrNull: SettingsChangeCoordinator? get() = uiManagers.settingsChangeCoordinator

    internal var donationPromptManager: DonationPromptManager
        get() = required(uiManagers.donationPromptManager, "DonationPromptManager")
        set(value) {
            uiManagers.donationPromptManager = value
        }
    internal val donationPromptManagerOrNull: DonationPromptManager? get() = uiManagers.donationPromptManager

    internal var reviewPromptManager: ReviewPromptManager
        get() = required(uiManagers.reviewPromptManager, "ReviewPromptManager")
        set(value) {
            uiManagers.reviewPromptManager = value
        }
    internal val reviewPromptManagerOrNull: ReviewPromptManager? get() = uiManagers.reviewPromptManager

    
    private var hasAskedDefaultLauncherThisOpen = false
    internal var deferredWidgetsInitialized = false

    fun isTablet(): Boolean {
        return (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    


    internal fun initializeCoreManagers() {
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        favoriteAppManager = injectedFavoriteAppManager
        hiddenAppManager = injectedHiddenAppManager
        webAppManager = injectedWebAppManager
        
        
        appLauncher = AppLauncher(this, packageManager, appLockManager)
        
        
        activityInitializer = ActivityInitializer(this, sharedPreferences, appLauncher)
        
        
        widgetLifecycleCoordinator = WidgetLifecycleCoordinator()
    }
    
    


    fun applyThemeBasedWidgetBackgrounds() {
        settingsChangeCoordinatorOrNull?.applyThemeBasedWidgetBackgrounds()
    }
    
    


    internal fun checkAndUpdateThemeIfNeeded() {
        widgetThemeManagerOrNull?.checkAndUpdateThemeIfNeeded(
            todoManager = todoManagerOrNull,
            appDockManager = appDockManagerOrNull,
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            searchContainer = if (views.isSearchContainerInitialized()) views.searchContainer else null,
            voiceSearchButton = if (views.isVoiceSearchButtonInitialized()) views.voiceSearchButton else null,
            searchTypeButton = if (views.isSearchTypeButtonInitialized()) views.searchTypeButton else null
        )
    }
    
    


    internal fun initializeBroadcastReceivers() {
        broadcastReceiverManager = BroadcastReceiverManager(
            this,
            sharedPreferences,
            onSettingsUpdated = { handleSettingsUpdate() },
            onNotificationsUpdated = { 
                if (widgetLifecycleCoordinator.isNotificationsWidgetInitialized()) {
                    widgetLifecycleCoordinator.notificationsWidget.updateNotifications()
                }
            },
            onPackageChanged = { packageName, isRemoved -> handlePackageChange(packageName, isRemoved) },
            onWallpaperChanged = { 
                wallpaperManagerHelper.clearCache()
                wallpaperManagerHelper.setWallpaperBackground(forceReload = true)
                refreshRightDrawerWallpaper()
            },
            onBatteryChanged = { 
                usageStatsRefreshManager.updateBatteryInBackground()
            },
            onActivityRecognitionPermissionGranted = {
                if (widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                }
            },
            onDndStateChanged = {
                if (appDockManager.getCurrentMode()) {
                    
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
        weatherManager = injectedWeatherManager
        
        
        searchTypeMenuManager = SearchTypeMenuManager(
            context = this,
            searchTypeButton = views.searchTypeButton,
            appSearchManagerProvider = { appSearchManagerOrNull },
            isFocusModeActive = { appDockManagerOrNull?.getCurrentMode() ?: false }
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
        if (!views.isSearchBoxInitialized() || appListOrNull == null) return
        
        
        val focusMode = appDockManagerOrNull?.getCurrentMode() ?: false
        val workspaceMode = appDockManagerOrNull?.isWorkspaceModeActive() ?: false
        val showFastScroller = sharedPreferences.getBoolean(Constants.Prefs.SHOW_FAST_SCROLLER, true)
        views.fastScroller.setFavoritesVisible(!focusMode && !workspaceMode)

        
        if (showFastScroller && appList.isNotEmpty()) {
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
                val existingAppSearchManager = appSearchManagerOrNull
                if (existingAppSearchManager != null) {
                    
                    existingAppSearchManager.updateData(
                        newFullAppList = existingAppSearchManager.getFullAppList(),
                        newHomeAppList = existingAppSearchManager.getHomeAppList(),
                        newContactsList = loadedContacts
                    )
                } else if (!isFinishing && !isDestroyed && adapterOrNull != null) {
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
        if (!sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)) {
            return
        }

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
        deferredWidgetsInitialized = true
        
        
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
        
        val existingAppSearchManager = appSearchManagerOrNull
        if (existingAppSearchManager != null) {
            existingAppSearchManager.updateData(targetFullList, targetHomeList, contactManager.getContactsList())
        } else {
            
            appSearchManager = injectedAppSearchManager
            appSearchManager.configure(
                fullAppList = targetFullList.toMutableList(),
                homeAppList = targetHomeList,
                adapter = adapterOrNull,
                searchBox = views.searchBox,
                contactsList = contactManager.getContactsList(),
                appMetadataCache = dataManagers.cacheManager?.getMetadataCache(),
                isAppFiltered = { packageName ->
                    appDockManagerOrNull?.isAppHiddenInFocusMode(packageName) == true
                },
                isFocusModeActive = { appDockManagerOrNull?.getCurrentMode() ?: false }
            )
        }
    }
    
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTablet()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
                window.decorView.post { recreate() }
                return
            }
        }
        instance = this
        AppInitializer(this).initialize()
        if (!isFinishing) {
            reviewPromptManager = ReviewPromptManager(this, sharedPreferences)
            donationPromptManager = DonationPromptManager(this, sharedPreferences)
            reviewPromptManager.recordFirstUseIfNeeded()
        }
        if (!isFinishing) {
            systemBarManager.makeSystemBarsTransparent()
        }

        handleStartTutorialIntent(intent)
        
        
        if (!isFinishing) {
            contactManager.loadContactsEagerly()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        val isHomeOrLauncher = intent.action == Intent.ACTION_MAIN && 
            (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))

        if (isHomeOrLauncher) {
            screenPagerManager.openDefaultHomePage(animated = true)
        }

        handleStartTutorialIntent(intent)

        
        if (intent.getBooleanExtra("request_permissions_after_disclosure", false)) {
            if (!sharedPreferences.getBoolean("initial_permissions_asked", false)) {
                requestInitialPermissions {
                    sharedPreferences.edit { putBoolean("initial_permissions_asked", true) }
                }
            }
        }
    }

    private fun handleStartTutorialIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_TUTORIAL, false) != true) return

        handler.post {
            if (isFinishing || isDestroyed) {
                return@post
            }

            openHomePage(animated = false)
            activityInitializer.setHeaderVisibility(true)

            if (views.isRecyclerViewInitialized()) {
                views.recyclerView.stopScroll()
                views.recyclerView.scrollToPosition(0)
            }

            FeatureTutorialManager(this, sharedPreferences).startTutorial()
            setIntent(Intent(intent).apply { removeExtra(EXTRA_START_TUTORIAL) })
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenPagerManagerOrNull?.updatePageWidth()
    }
    
    



    internal fun initializeLifecycleManager() {
        lifecycleManager = LifecycleManager(this, handler, sharedPreferences)
        lifecycleManager.setSystemBarManager(systemBarManager)
        lifecycleManager.setAppLockManager(appLockManager)
        
        if (widgetLifecycleCoordinator.isNotificationsWidgetInitialized()) {
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
        
        todoManagerOrNull?.let(lifecycleManager::setTodoManager)
        
        lifecycleManager.setBackgroundExecutor(backgroundExecutor)
        lifecycleManager.setWidgetLifecycleCoordinator(widgetLifecycleCoordinator)
        lifecycleManager.setWidgetThemeManager(widgetThemeManager)
        lifecycleManager.setViews(views)
        
        broadcastReceiverManagerOrNull?.let(lifecycleManager::setBroadcastReceiverManager)
        
        lifecycleManager.setShareManager(shareManager)
        
        dataManagers.serviceManager?.let(lifecycleManager::setServiceManager)
        
        lifecycleManager.setAppTimerManager(appTimerManager)
        
        lifecycleManager.setHiddenAppManager(hiddenAppManager)
        
        
        lifecycleManager.onBatteryUpdate = { updateBatteryInBackground() }
        lifecycleManager.onUsageUpdate = { updateUsageInBackground() }
        lifecycleManager.onFocusModeApply = { isFocusMode -> applyFocusMode(isFocusMode) }
        lifecycleManager.onLoadApps = { forceRefresh -> appListLoader.loadApps(forceRefresh) }
    }
    
    


    private fun updateLifecycleManagerWithDeferredWidgets() {
        lifecycleManagerOrNull?.let { activeLifecycleManager ->
            if (widgetLifecycleCoordinator.isNotificationsWidgetInitialized()) {
                activeLifecycleManager.setNotificationsWidget(widgetLifecycleCoordinator.notificationsWidget)
            }
            todoManagerOrNull?.let(activeLifecycleManager::setTodoManager)
        }
    }

    fun refreshAppsForFocusMode() {
        appListUIUpdaterOrNull?.refreshAppsForFocusMode()
    }

    fun openWidgetsPage(animated: Boolean = true) {
        screenPagerManagerOrNull?.openWidgetsPage(animated)
    }

    fun openRssPage(animated: Boolean = true) {
        screenPagerManagerOrNull?.openRssPage(animated)
    }

    fun openHomePage(animated: Boolean = true) {
        screenPagerManagerOrNull?.openCenterPage(animated)
    }

    fun openWallpaperPage(animated: Boolean = true) {
        screenPagerManagerOrNull?.openRightPage(animated)
    }

    fun openDefaultHomePage(animated: Boolean = true) {
        screenPagerManagerOrNull?.openDefaultHomePage(animated)
    }

    fun setWidgetsPageLocked(locked: Boolean) {
        screenPagerManagerOrNull?.setLeftPageLocked(locked)
    }
    
    fun refreshAppsForWorkspace() {
        appListUIUpdaterOrNull?.refreshAppsForWorkspace()
    }
    
    fun clearAppCacheAndReload() {
        appListLoaderOrNull?.loadApps(forceRefresh = true)
    }
    
    fun filterAppsWithoutReload() {
        appListUIUpdaterOrNull?.filterAppsWithoutReload()
    }

    private fun handleSettingsUpdate() {
        settingsChangeCoordinatorOrNull?.handleSettingsUpdate()
        syncOptionalPagesAfterSettingsUpdate()
    }

    private fun syncOptionalPagesAfterSettingsUpdate() {
        val widgetsEnabled = sharedPreferences.getBoolean(Constants.Prefs.WIDGETS_PAGE_ENABLED, true)
        if (widgetsEnabled && !deferredWidgetsInitialized) {
            handler.post {
                if (!isFinishing && !isDestroyed) {
                    initializeDeferredWidgets()
                }
            }
        }

        if (sharedPreferences.getBoolean(Constants.Prefs.RSS_PAGE_ENABLED, true)) {
            handler.post {
                if (!isFinishing && !isDestroyed) {
                    findViewById<View?>(R.id.rss_feed_page)?.let { rssPageView ->
                        RssFeedPage(this, rssPageView).setup()
                    }
                }
            }
        }
    }
    
    private fun handlePackageChange(packageName: String?, isRemoved: Boolean) {
        if (packageName == null) return
        
        
        cacheManager.removeMetadata(packageName)
        
        
        if (isRemoved) {
            cacheManager.clearCache()
        }
        
        runOnUiThread {
            appList.removeAll { it.activityInfo.packageName == packageName }
            fullAppList.removeAll { it.activityInfo.packageName == packageName }
            
            appListLoader.clearCache()
            adapter.updateAppList(appList)
            updateFastScrollerVisibility()
        }
        
        if (!isRemoved) {
            
            appListLoader.clearCache()
            appListLoader.loadApps(forceRefresh = true, fullAppList, appList, adapterOrNull)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        lifecycleManagerOrNull?.onDestroy()
        
        
        
    }

    
    private fun updateBatteryInBackground() {
        usageStatsRefreshManagerOrNull?.updateBatteryInBackground()
    }

    private fun updateUsageInBackground() {
        usageStatsRefreshManagerOrNull?.updateUsageInBackground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            gestureHandlerOrNull?.updateGestureExclusion()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleManagerOrNull?.onResume()
        
        
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
        if (reviewPromptManagerOrNull?.promptIfEligible() == true) {
            return
        }
        donationPromptManagerOrNull?.promptIfEligible()
    }

    override fun onPause() {
        super.onPause()
        lifecycleManagerOrNull?.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        permissionManager.handlePermissionResult(
            requestCode,
            permissions,
            grantResults,
            onContactsGranted = { 
                contactManager.loadContacts { loadedContacts ->
                    val existingAppSearchManager = appSearchManagerOrNull
                    if (existingAppSearchManager != null) {
                        
                        existingAppSearchManager.updateData(
                            newFullAppList = existingAppSearchManager.getFullAppList(),
                            newHomeAppList = existingAppSearchManager.getHomeAppList(),
                            newContactsList = loadedContacts
                        )
                    } else if (!isFinishing && !isDestroyed && adapterOrNull != null) {
                        updateAppSearchManager()
                    }
                }
            },
            onCallPhoneGranted = {  },
            onNotificationGranted = {
                todoManagerOrNull?.rescheduleTodoAlarms()
            },
            onStorageGranted = {
                
            },
            onActivityRecognitionGranted = {
                if (widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
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
        
        
        if (requestCode == PermissionManager.VOICE_SEARCH_REQUEST && 
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voiceSearchManagerOrNull?.startVoiceSearch()
            if (widgetLifecycleCoordinator.isNoiseDecibelWidgetInitialized()) {
                widgetLifecycleCoordinator.noiseDecibelWidget.onPermissionGranted()
            }
        }
        
        
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                105 -> if (widgetLifecycleCoordinator.isPhysicalActivityWidgetInitialized()) {
                    widgetLifecycleCoordinator.physicalActivityWidget.onPermissionGranted()
                }
                CalendarEventsWidget.REQUEST_CODE_CALENDAR_PERMISSION -> if (widgetLifecycleCoordinator.isCalendarEventsWidgetInitialized()) {
                    widgetLifecycleCoordinator.calendarEventsWidget.onPermissionGranted()
                }
                CountdownWidget.REQUEST_CODE_CALENDAR_PERMISSION -> if (widgetLifecycleCoordinator.isCountdownWidgetInitialized()) {
                    widgetLifecycleCoordinator.countdownWidget.onPermissionGranted()
                }
            }
        }
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        focusModeApplierOrNull?.applyFocusMode(isFocusMode)
    }

    


    internal fun showWidgetConfigurationDialog() {
        resultRegistry.showWidgetConfigurationDialog()
    }

    


    fun updateRegistryDependencies() {
        val deps = MainActivityResultRegistry.DependencyContainer(
            widgetManager = widgetManagers.widgetManager,
            widgetVisibilityManager = widgetManagers.widgetVisibilityManager,
            widgetConfigurationManager = widgetManagers.widgetConfigurationManager,
            voiceCommandHandler = voiceCommandHandler,
            activityResultHandler = uiManagers.activityResultHandler,
            packageManager = packageManager,
            contentResolver = contentResolver,
            searchBox = if (views.isSearchBoxInitialized()) views.searchBox else null,
            appList = dataManagers.appList,
            widgetLifecycleCoordinator = widgetManagers.widgetLifecycleCoordinator
        )
        resultRegistry.setDependencies(deps)
    }
}
