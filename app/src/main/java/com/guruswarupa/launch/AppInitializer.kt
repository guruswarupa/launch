package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.guruswarupa.launch.core.*
import com.guruswarupa.launch.handlers.*
import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.ui.activities.AppDataDisclosureActivity
import com.guruswarupa.launch.utils.*
import com.guruswarupa.launch.widgets.*
import com.guruswarupa.launch.services.*

/**
 * Handles the initialization of MainActivity and its components.
 */
class AppInitializer(private val activity: MainActivity) {

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun initialize(savedInstanceState: Bundle?) {
        with(activity) {
            sharedPreferences = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            
            setContentView(R.layout.activity_main)
            
            // Initialize core managers
            initializeCoreManagers()

            // Make status bar and navigation bar transparent (after setContentView, post to ensure window is ready)
            window.decorView.post {
                systemBarManager.makeSystemBarsTransparent()
            }

            // Initialize widget configuration manager
            widgetConfigurationManager = WidgetConfigurationManager(sharedPreferences)
            
            // Initialize widget visibility manager
            widgetVisibilityManager = WidgetVisibilityManager(activity, widgetConfigurationManager)
            
            // Initialize result registry
            resultRegistry = MainActivityResultRegistry(activity)
            
            // Initialize managers
            cacheManager = CacheManager(activity, packageManager, backgroundExecutor)
            permissionManager = PermissionManager(activity, sharedPreferences)
            systemBarManager = SystemBarManager(activity)
            
            // Initialize new managers
            usageStatsCacheManager = UsageStatsCacheManager(sharedPreferences, backgroundExecutor)
            contactManager = ContactManager(activity, contentResolver, backgroundExecutor)
            onboardingHelper = OnboardingHelper(activity, sharedPreferences, packageManager, packageName)
            
            // Load usage stats cache immediately
            usageStatsCacheManager.loadCache()
            
            // Load metadata cache from CacheManager asynchronously
            cacheManager.loadAppMetadataFromCacheAsync {
                // Once metadata is loaded, refresh search manager if it's already initialized
                if (activity.isAppSearchManagerInitialized()) {
                    updateAppSearchManager()
                }
            }
            
            // Check if onboarding is needed
            if (onboardingHelper.checkAndStartOnboarding()) {
                return@with
            }
            
            // Check if user has given consent for app data collection
            if (!sharedPreferences.getBoolean("app_data_consent_given", false)) {
                // Show disclosure activity
                startActivity(Intent(activity, AppDataDisclosureActivity::class.java))
                finish()
                return@with
            }
            
            // Initialize broadcast receiver manager
            initializeBroadcastReceivers()

            // Initialize views and UI components
            initializeViews()
            
            // Request necessary permissions
            requestInitialPermissions()

            // Initialize time/date and weather widgets
            initializeTimeDateAndWeather()

            // Optimization #7: Reduced delay to make launcher feel more responsive
            handler.postDelayed({
                initializeDeferredWidgets()
            }, 30) // Reduced from 100ms

            appDockManager = AppDockManager(activity, sharedPreferences, views.appDock, packageManager, favoriteAppManager)
            
            // Initialize widget theme manager
            widgetThemeManager = WidgetThemeManager(activity) { resources.configuration.uiMode }
            
            // Initialize settings change coordinator
            settingsChangeCoordinator = SettingsChangeCoordinator(
                activity = activity,
                adapterProvider = { if (activity.isAdapterInitialized()) activity.adapter else null },
                appDockManagerProvider = { if (activity.isAppDockManagerInitialized()) activity.appDockManager else null },
                widgetSetupManagerProvider = { activity.widgetSetupManager },
                widgetThemeManagerProvider = { activity.widgetThemeManager }
            )
            
            // Apply theme-appropriate widget backgrounds
            applyThemeBasedWidgetBackgrounds()
            
            // Initialize appList before using it (must be initialized before appListLoader)
            appList = mutableListOf()
            fullAppList = mutableListOf()
            
            // Initialize ContactActionHandler (depends on searchBox and appList)
            contactActionHandler = ContactActionHandler(
                activity, packageManager, contentResolver, views.searchBox, appList
            ) { handler ->
                voiceCommandHandler = handler
                if (activity.isActivityResultHandlerInitialized()) {
                    activity.activityResultHandler.setVoiceCommandHandler(handler)
                }
                // Update registry if it's already been fully initialized
                updateRegistryDependencies()
            }
            
            // Initialize app list manager
            appListManager = AppListManager(appDockManager, favoriteAppManager, hiddenAppManager, cacheManager)
            
            // Initialize app list loader
            appListLoader = AppListLoader(
                activity, packageManager, appListManager, appDockManager, favoriteAppManager,
                cacheManager, backgroundExecutor, handler, views.recyclerView, views.searchBox, views.voiceSearchButton, sharedPreferences
            )
            
            // Initialize AppListUIUpdater
            appListUIUpdater = AppListUIUpdater(
                activity, views.recyclerView, if (activity.isAdapterInitialized()) activity.adapter else null,
                appList, fullAppList, appListLoader, appDockManager, appListManager,
                handler, backgroundExecutor, views.searchBox
            )
            appListUIUpdater.setupCallbacks()

            // Refresh apps after appDockManager is fully initialized
            if (!appDockManager.getCurrentMode()) {
                // If focus mode was disabled during init, refresh the apps
                refreshAppsForFocusMode()
            }

            // Initialize adapter immediately with empty/cached list for instant UI
            val viewPreference = sharedPreferences.getString("view_preference", "list")
            val isGridMode = viewPreference == "grid"
            adapter = AppAdapter(activity, appList, views.searchBox, isGridMode, activity)
            views.recyclerView.adapter = adapter
            views.recyclerView.visibility = View.VISIBLE
            updateFastScrollerVisibility()
            
            // Update AppListUIUpdater with the initialized adapter
            appListUIUpdater.setAdapter(adapter)

            // Initialize usage stats display manager (after adapter is created)
            usageStatsDisplayManager = UsageStatsDisplayManager(activity, usageStatsManager, views.weeklyUsageGraph, adapter, views.recyclerView, handler)
            
            // Load apps in background - will update adapter when ready
            appListLoader.loadApps(forceRefresh = false, fullAppList, appList, if (activity.isAdapterInitialized()) activity.adapter else null)
            
            // Optimization #7: Reduced delay for initialization
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && activity.isAppDockManagerInitialized()) {
                    updateAppSearchManager()
                }
            }, 50) // Reduced from 150ms

            // Initialize DrawerLayout and navigation
            val mainContent = findViewById<FrameLayout>(R.id.main_content)
            gestureHandler = GestureHandler(activity, views.drawerLayout, mainContent)
            
            drawerManager = DrawerManager(
                activity, views.drawerLayout, gestureHandler, usageStatsDisplayManager, activityInitializer,
                themeCheckCallback = { checkAndUpdateThemeIfNeeded() }
            )
            drawerManager.setup()
            navigationManager = drawerManager.navigationManager
            
            // Initialize WidgetManager
            val drawerContentLayout = findViewById<LinearLayout>(R.id.drawer_content_layout)
            widgetManager = WidgetManager(activity, drawerContentLayout)
            
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
            wallpaperManagerHelper = WallpaperManagerHelper(activity, views.wallpaperBackground, drawerWallpaper, backgroundExecutor)
            wallpaperManagerHelper.setWallpaperBackground()
            
            // Set wallpaper for the new right drawer immediately
            if (views.isRightDrawerWallpaperInitialized() && activity.isWallpaperManagerHelperInitialized()) {
                try {
                    val wallpaperManager = android.app.WallpaperManager.getInstance(activity)
                    val drawable = wallpaperManager.drawable
                    views.rightDrawerWallpaper.setImageDrawable(drawable)
                } catch (_: Exception) {}
            }

            // Initialize voice search manager
            voiceSearchManager = VoiceSearchManager(activity, packageManager)
            // Set up voice search button with new launcher API
            views.voiceSearchButton.setOnClickListener {
                voiceSearchManager.startVoiceSearchWithLauncher(resultRegistry.voiceSearchLauncher)
            }

            views.voiceSearchButton.setOnLongClickListener {
                voiceSearchManager.triggerSystemAssistant()
                true
            }
            
            // Initialize usage stats refresh manager
            usageStatsRefreshManager = UsageStatsRefreshManager(
                activity, backgroundExecutor, usageStatsManager
            )
            
            // Initialize activity result handler (voiceCommandHandler will be set later)
            activityResultHandler = ActivityResultHandler(
                activity, views.searchBox, voiceCommandHandler, shareManager,
                widgetManager, wallpaperManagerHelper,
                onBlockBackGestures = { navigationManager.blockBackGesturesTemporarily() }
            )
            
            // Initialize focus mode applier
            focusModeApplier = FocusModeApplier(
                activity, backgroundExecutor, appListManager, appDockManager,
                views.searchBox, views.voiceSearchButton, views.searchContainer, if (activity.isAdapterInitialized()) activity.adapter else null, fullAppList, appList,
                onUpdateAppSearchManager = { updateAppSearchManager() }
            )
            
            // Initialize service manager
            serviceManager = ServiceManager(activity, sharedPreferences)

            // Initialize FinanceWidget using the new initializer
            FinanceWidgetInitializer(activity, sharedPreferences, 100)
                .onInitialized { manager -> 
                    financeWidgetManager = manager
                }
                .initialize(handler)
            
            // Start app usage monitor for daily limits
            startService(Intent(activity, AppUsageMonitor::class.java))
            
            // Initialize background services through ServiceManager
            serviceManager.updateShakeDetectionService()
            serviceManager.updateScreenDimmerService()
            serviceManager.updateFlipToDndService()
            serviceManager.updateBackTapService()
            
            // Initialize lifecycle manager
            initializeLifecycleManager()

            // Finally, set all dependencies for result registry in one go
            updateRegistryDependencies()
        }
    }

    /**
     * Updates the MainActivityResultRegistry with all current dependencies from MainActivity
     */
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
