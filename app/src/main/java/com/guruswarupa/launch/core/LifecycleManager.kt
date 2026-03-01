package com.guruswarupa.launch.core

import android.os.Handler
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity

import com.guruswarupa.launch.managers.*
import com.guruswarupa.launch.widgets.NotificationsWidget
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.widgets.DeviceInfoWidget
import com.guruswarupa.launch.widgets.NetworkStatsWidget
import com.guruswarupa.launch.utils.TimeDateManager
import com.guruswarupa.launch.utils.TodoManager
import com.guruswarupa.launch.utils.TodoAlarmManager
import com.guruswarupa.launch.utils.FeatureTutorialManager
import com.guruswarupa.launch.models.MainActivityViews
import com.guruswarupa.launch.ui.views.WeeklyUsageGraphView
import com.guruswarupa.launch.widgets.WidgetThemeManager

/**
 * Manages activity lifecycle operations.
 * Extracted from MainActivity to reduce complexity.
 */
class LifecycleManager(
    private val activity: FragmentActivity,
    private val handler: Handler,
    private val sharedPreferences: android.content.SharedPreferences
) {
    // Dependencies (initialized via setters)
    private var systemBarManager: SystemBarManager? = null
    private var appLockManager: AppLockManager? = null
    private var notificationsWidget: NotificationsWidget? = null
    private var wallpaperManagerHelper: WallpaperManagerHelper? = null
    private var gestureHandler: GestureHandler? = null
    private var appDockManager: AppDockManager? = null
    private var adapter: AppAdapter? = null
    private var appList: MutableList<android.content.pm.ResolveInfo>? = null
    private var appListLoader: AppListLoader? = null
    private var widgetManager: WidgetManager? = null
    private var deviceInfoWidget: DeviceInfoWidget? = null
    private var networkStatsWidget: NetworkStatsWidget? = null
    private var usageStatsManager: AppUsageStatsManager? = null
    private var timeDateManager: TimeDateManager? = null
    private var weeklyUsageGraph: WeeklyUsageGraphView? = null
    private var usageStatsDisplayManager: UsageStatsDisplayManager? = null
    private var todoManager: TodoManager? = null
    private var todoAlarmManager: TodoAlarmManager? = null
    private var featureTutorialManager: FeatureTutorialManager? = null
    private var backgroundExecutor: java.util.concurrent.ExecutorService? = null
    private var widgetLifecycleCoordinator: WidgetLifecycleCoordinator? = null
    private var widgetThemeManager: WidgetThemeManager? = null
    private var views: MainActivityViews? = null
    private var broadcastReceiverManager: BroadcastReceiverManager? = null
    private var shareManager: ShareManager? = null
    private var serviceManager: ServiceManager? = null
    private var appTimerManager: AppTimerManager? = null
    private var hiddenAppManager: HiddenAppManager? = null
    
    // Callbacks
    var onResumeCallbacks: MutableList<() -> Unit> = mutableListOf()
    var onPauseCallbacks: MutableList<() -> Unit> = mutableListOf()
    var onBatteryUpdate: (() -> Unit)? = null
    var onUsageUpdate: (() -> Unit)? = null
    var onFocusModeApply: ((Boolean) -> Unit)? = null
    var onLoadApps: ((Boolean) -> Unit)? = null
    
    fun setSystemBarManager(manager: SystemBarManager) { this.systemBarManager = manager }
    fun setAppLockManager(manager: AppLockManager) { this.appLockManager = manager }
    fun setNotificationsWidget(widget: NotificationsWidget) { this.notificationsWidget = widget }
    fun setWallpaperManagerHelper(helper: WallpaperManagerHelper) { this.wallpaperManagerHelper = helper }
    fun setGestureHandler(handler: GestureHandler) { this.gestureHandler = handler }
    fun setAppDockManager(manager: AppDockManager) { this.appDockManager = manager }
    fun setAdapter(adapter: AppAdapter) { this.adapter = adapter }
    fun setAppList(list: MutableList<android.content.pm.ResolveInfo>) { this.appList = list }
    fun setAppListLoader(loader: AppListLoader) { this.appListLoader = loader }
    fun setWidgetManager(manager: WidgetManager) { this.widgetManager = manager }
    fun setDeviceInfoWidget(widget: DeviceInfoWidget) { this.deviceInfoWidget = widget }
    fun setNetworkStatsWidget(widget: NetworkStatsWidget) { this.networkStatsWidget = networkStatsWidget }
    fun setUsageStatsManager(manager: AppUsageStatsManager) { this.usageStatsManager = manager }
    fun setTimeDateManager(manager: TimeDateManager) { this.timeDateManager = manager }
    fun setWeeklyUsageGraph(graph: WeeklyUsageGraphView) { this.weeklyUsageGraph = graph }
    fun setUsageStatsDisplayManager(manager: UsageStatsDisplayManager) { this.usageStatsDisplayManager = manager }
    fun setTodoManager(manager: TodoManager) { this.todoManager = manager }
    fun setTodoAlarmManager(manager: TodoAlarmManager) { this.todoAlarmManager = manager }
    fun setFeatureTutorialManager(manager: FeatureTutorialManager) { this.featureTutorialManager = manager }
    fun setBackgroundExecutor(executor: java.util.concurrent.ExecutorService) { this.backgroundExecutor = executor }
    fun setWidgetLifecycleCoordinator(coordinator: WidgetLifecycleCoordinator) { this.widgetLifecycleCoordinator = coordinator }
    fun setWidgetThemeManager(manager: WidgetThemeManager) { this.widgetThemeManager = manager }
    fun setViews(views: MainActivityViews) { this.views = views }
    fun setBroadcastReceiverManager(manager: BroadcastReceiverManager) { this.broadcastReceiverManager = manager }
    fun setShareManager(manager: ShareManager) { this.shareManager = manager }
    fun setServiceManager(manager: ServiceManager) { this.serviceManager = manager }
    fun setAppTimerManager(manager: AppTimerManager) { this.appTimerManager = manager }
    fun setHiddenAppManager(manager: HiddenAppManager) { this.hiddenAppManager = manager }
    
    private var isBlockingBackGesture = false
    fun setBlockingBackGesture(isBlocking: Boolean) { this.isBlockingBackGesture = isBlocking }
    
    fun onResume(intent: android.content.Intent) {
        // Ensure system bars stay transparent
        systemBarManager?.makeSystemBarsTransparent()
        
        // Clear app lock authentication timeout when returning to launcher
        appLockManager?.clearAuthTimeout()
        
        // Update notifications widget when activity resumes
        notificationsWidget?.updateNotifications()
        
        // Refresh wallpaper when returning from Settings (in case it was changed)
        wallpaperManagerHelper?.let {
            it.clearCache()
            it.setWallpaperBackground(forceReload = true)
        }
        
        // Update gesture exclusion when activity resumes
        if (!isBlockingBackGesture) {
            gestureHandler?.updateGestureExclusion()
        }
        
        // Reapply focus mode state
        appDockManager?.let {
            onFocusModeApply?.invoke(it.getCurrentMode())
            it.refreshWorkspaceToggle()
        }

        // Theme check
        widgetThemeManager?.let { themeManager ->
            themeManager.checkAndUpdateThemeIfNeeded(
                todoManager = todoManager,
                appDockManager = appDockManager,
                searchBox = views?.let { if (it.isSearchBoxInitialized()) it.searchBox else null },
                searchContainer = views?.let { if (it.isSearchContainerInitialized()) it.searchContainer else null },
                voiceSearchButton = views?.let { if (it.isVoiceSearchButtonInitialized()) it.voiceSearchButton else null },
                searchTypeButton = views?.let { if (it.isSearchTypeButtonInitialized()) it.searchTypeButton else null }
            )
        }

        // Clear search box focus
        views?.let { 
            if (it.isSearchBoxInitialized()) {
                it.searchBox.clearFocus()
            }
        }

        // Resume widgets
        widgetLifecycleCoordinator?.onResume()
        
        // Refresh app list with consolidated delay
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                hiddenAppManager?.forceRefresh()
                
                appListLoader?.let {
                    it.loadApps(forceRefresh = false)
                } ?: run {
                    onLoadApps?.invoke(false)
                }
            }
        }, 500)
        
        // Start widget managers
        widgetManager?.onStart()
        deviceInfoWidget?.onResume()
        networkStatsWidget?.onResume()
        
        // Update time/date
        val isPowerSaverMode = sharedPreferences.getBoolean("power_saver_mode", false)
        timeDateManager?.startUpdates(isPowerSaverMode)
        
        // Lightweight background tasks
        usageStatsManager?.invalidateCache()
        
        handler.postDelayed({
            wallpaperManagerHelper?.setWallpaperBackground()
            onBatteryUpdate?.invoke()
            onUsageUpdate?.invoke()
            
            backgroundExecutor?.execute {
                usageStatsDisplayManager?.checkDateChangeAndRefreshUsage()
            }
            
            handler.postDelayed({
                weeklyUsageGraph?.let {
                    if (it.isVisible) {
                        usageStatsDisplayManager?.loadWeeklyUsageData()
                    }
                }
            }, 500)
            
            todoManager?.rescheduleTodoAlarms()
        }, 50)
        
        // Feature tutorial
        val shouldStartTutorial = intent.getBooleanExtra("start_tutorial", false)
        handler.postDelayed({
            featureTutorialManager?.let {
                if (shouldStartTutorial || it.shouldShowTutorial()) {
                    it.startTutorial()
                }
            }
        }, 1000)
        
        onResumeCallbacks.forEach { it.invoke() }
    }
    
    fun onPause() {
        timeDateManager?.stopUpdates()
        
        // Save todo items
        todoManager?.saveTodoItems()
        
        // Stop widget manager listening
        widgetManager?.onStop()
        deviceInfoWidget?.onPause()
        networkStatsWidget?.onPause()

        // Pause widgets
        widgetLifecycleCoordinator?.onPause()
        
        onPauseCallbacks.forEach { it.invoke() }
    }

    fun onDestroy() {
        wallpaperManagerHelper?.cleanup()
        broadcastReceiverManager?.unregisterReceivers()
        backgroundExecutor?.shutdown()
        shareManager?.cleanup()
        widgetManager?.onDestroy()
        widgetLifecycleCoordinator?.onDestroy()
        serviceManager?.stopAllServices()
        appTimerManager?.cleanup()
        usageStatsManager?.cleanup()
        
        cleanup()
    }
    
    /**
     * Cleanup method to cancel pending handler callbacks and prevent memory leaks
     */
    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}
