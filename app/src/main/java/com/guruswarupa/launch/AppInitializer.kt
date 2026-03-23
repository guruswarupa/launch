package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.core.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.ui.activities.AppDataDisclosureActivity
import com.guruswarupa.launch.widgets.*

class AppInitializer(private val activity: MainActivity) {

    private fun isTablet(): Boolean {
        return (activity.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "SourceLockedOrientationActivity")
    fun initialize() {
        with(activity) {
            sharedPreferences = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

            if (!this@AppInitializer.isTablet()) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            setContentView(R.layout.activity_main)

            initializeCoreManagers()

            window.decorView.post {
                systemBarManager.makeSystemBarsTransparent()
            }

            widgetConfigurationManager = WidgetConfigurationManager(activity, sharedPreferences)

            widgetVisibilityManager = WidgetVisibilityManager(activity, widgetConfigurationManager)

            resultRegistry = MainActivityResultRegistry(activity)

            cacheManager = CacheManager(activity, packageManager, backgroundExecutor)
            permissionManager = PermissionManager(activity, sharedPreferences)
            systemBarManager = SystemBarManager(activity)

            usageStatsCacheManager = UsageStatsCacheManager(sharedPreferences, backgroundExecutor)
            contactManager = ContactManager(activity, contentResolver, backgroundExecutor)

            usageStatsCacheManager.loadCache()

            cacheManager.loadAppMetadataFromCacheAsync {
                if (activity.isAppSearchManagerInitialized()) {
                    updateAppSearchManager()
                }
            }

            if (!sharedPreferences.getBoolean("app_data_consent_given", false)) {
                startActivity(Intent(activity, AppDataDisclosureActivity::class.java))
                finish()
                return@with
            }

            initializeBroadcastReceivers()

            initializeViews()

            val requestPermissionsAfterDisclosure = activity.intent.getBooleanExtra("request_permissions_after_disclosure", false)
            if (requestPermissionsAfterDisclosure) {
                activity.handler.post {
                    activity.startFeatureTutorialAndRequestPermissions()
                }
            }

            initializeTimeDateAndWeather()

            handler.postDelayed({
                initializeDeferredWidgets()
            }, 30) 

            appDockManager = AppDockManager(activity, sharedPreferences, views.appDock)
            
            widgetThemeManager = WidgetThemeManager(activity) { resources.configuration.uiMode }
            
            settingsChangeCoordinator = SettingsChangeCoordinator(
                activity = activity,
                adapterProvider = { if (activity.isAdapterInitialized()) activity.adapter else null },
                appDockManagerProvider = { if (activity.isAppDockManagerInitialized()) activity.appDockManager else null },
                widgetSetupManagerProvider = { activity.widgetSetupManager },
                widgetThemeManagerProvider = { activity.widgetThemeManager }
            )
            
            applyThemeBasedWidgetBackgrounds()
            
            settingsChangeCoordinator.applyBackgroundTranslucency()
            
            appList = mutableListOf()
            fullAppList = mutableListOf()
            
            contactActionHandler = ContactActionHandler(
                activity, packageManager, contentResolver, views.searchBox, appList
            ) { handler ->
                voiceCommandHandler = handler
                if (activity.isActivityResultHandlerInitialized()) {
                    activity.activityResultHandler.setVoiceCommandHandler(handler)
                }
                
                this@AppInitializer.updateRegistryDependencies()
            }
            
            val workspaceManager = WorkspaceManager(sharedPreferences)
            val workProfileManager = WorkProfileManager(activity, sharedPreferences)
            appListManager = AppListManager(activity, appDockManager, favoriteAppManager, hiddenAppManager, cacheManager, workspaceManager, workProfileManager)
            
            appListLoader = AppListLoader(
                activity, packageManager, appListManager, appDockManager,
                cacheManager, webAppManager, backgroundExecutor, handler, views.recyclerView, views.searchBox, views.voiceSearchButton, sharedPreferences
            )
            
            appListUIUpdater = AppListUIUpdater(
                activity, views.recyclerView, if (activity.isAdapterInitialized()) activity.adapter else null,
                appList, fullAppList, appListLoader, appListManager,
                backgroundExecutor, views.searchBox
            )
            appListUIUpdater.setupCallbacks()

            if (!appDockManager.getCurrentMode()) {
                refreshAppsForFocusMode()
            }

            val viewPreference = sharedPreferences.getString("view_preference", "list")
            val isGridMode = viewPreference == "grid"
            adapter = AppAdapter(activity, appList, views.searchBox, isGridMode, activity)
            views.recyclerView.adapter = adapter
            views.recyclerView.visibility = View.VISIBLE
            updateFastScrollerVisibility()
            
            appListUIUpdater.setAdapter(adapter)

            usageStatsDisplayManager = UsageStatsDisplayManager(activity, usageStatsManager, views.weeklyUsageGraph, adapter, views.recyclerView, handler)
            
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (activity.isAdapterInitialized()) activity.adapter else null)
            
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && activity.isAppDockManagerInitialized()) {
                    updateAppSearchManager()
                }
            }, 50) 

            val mainContent = findViewById<FrameLayout>(R.id.main_content)
            gestureHandler = GestureHandler(activity, views.drawerLayout, mainContent)
            screenPagerManager = ScreenPagerManager(activity, views.drawerLayout)
            
            drawerManager = DrawerManager(
                activity, screenPagerManager, gestureHandler, usageStatsDisplayManager, activityInitializer,
                themeCheckCallback = { checkAndUpdateThemeIfNeeded() }
            )
            drawerManager.setup()
            navigationManager = drawerManager.navigationManager
            
            val drawerContentLayout = findViewById<LinearLayout>(R.id.drawer_content_layout)
            widgetManager = WidgetManager(activity, drawerContentLayout)
            
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

            val drawerWallpaper = findViewById<ImageView>(R.id.drawer_wallpaper_background)
            wallpaperManagerHelper = WallpaperManagerHelper(activity, views.wallpaperBackground, drawerWallpaper, backgroundExecutor)
            wallpaperManagerHelper.setWallpaperBackground()
            
            if (activity.isWallpaperManagerHelperInitialized()) {
                activity.refreshRightDrawerWallpaper()
            }

            voiceSearchManager = VoiceSearchManager(activity, packageManager)
            
            views.voiceSearchButton.setOnClickListener {
                voiceSearchManager.startVoiceSearchWithLauncher(resultRegistry.voiceSearchLauncher)
            }

            views.voiceSearchButton.setOnLongClickListener {
                voiceSearchManager.triggerSystemAssistant()
                true
            }
            
            usageStatsRefreshManager = UsageStatsRefreshManager(
                activity, backgroundExecutor, usageStatsManager
            )
            
            activityResultHandler = ActivityResultHandler(
                activity, views.searchBox, voiceCommandHandler, shareManager,
                widgetManager, wallpaperManagerHelper,
                onBlockBackGestures = { navigationManager.blockBackGesturesTemporarily() }
            )
            
            focusModeApplier = FocusModeApplier(
                activity, backgroundExecutor, appListManager, appDockManager,
                views.searchContainer, if (activity.isAdapterInitialized()) activity.adapter else null, fullAppList, appList,
                onUpdateAppSearchManager = { updateAppSearchManager() },
                onUpdateFastScrollerVisibility = { updateFastScrollerVisibility() }
            )
            
            serviceManager = ServiceManager(activity, sharedPreferences)

            FinanceWidgetInitializer(activity, sharedPreferences, 100)
                .onInitialized { manager -> 
                    financeWidgetManager = manager
                }
                .initialize(handler)
            
            ContextCompat.startForegroundService(activity, Intent(activity, AppUsageMonitor::class.java))
            
            serviceManager.updateShakeDetectionService()
            serviceManager.updateScreenDimmerService()
            serviceManager.updateFlipToDndService()
            serviceManager.updateBackTapService()
            
            initializeLifecycleManager()

            this@AppInitializer.updateRegistryDependencies()
        }
    }

    fun updateRegistryDependencies() {
        with(activity) {
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
}
