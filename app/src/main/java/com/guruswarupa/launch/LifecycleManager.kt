package com.guruswarupa.launch

import android.os.Handler
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity

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
    private var widgetManager: WidgetManager? = null
    private var usageStatsManager: AppUsageStatsManager? = null
    private var timeDateManager: TimeDateManager? = null
    private var wallpaperManagerHelper2: WallpaperManagerHelper? = null
    private var weeklyUsageGraph: WeeklyUsageGraphView? = null
    private var usageStatsDisplayManager: UsageStatsDisplayManager? = null
    private var todoManager: TodoManager? = null
    private var featureTutorialManager: FeatureTutorialManager? = null
    private var backgroundExecutor: java.util.concurrent.Executor? = null
    
    // Callbacks
    var onResumeCallbacks: MutableList<() -> Unit> = mutableListOf()
    var onPauseCallbacks: MutableList<() -> Unit> = mutableListOf()
    var onBatteryUpdate: (() -> Unit)? = null
    var onUsageUpdate: (() -> Unit)? = null
    var onFocusModeApply: ((Boolean) -> Unit)? = null
    var onLoadApps: ((Boolean) -> Unit)? = null
    
    fun setSystemBarManager(manager: SystemBarManager) {
        this.systemBarManager = manager
    }
    
    fun setAppLockManager(manager: AppLockManager) {
        this.appLockManager = manager
    }
    
    fun setNotificationsWidget(widget: NotificationsWidget) {
        this.notificationsWidget = widget
    }
    
    fun setWallpaperManagerHelper(helper: WallpaperManagerHelper) {
        this.wallpaperManagerHelper = helper
    }
    
    fun setGestureHandler(handler: GestureHandler) {
        this.gestureHandler = handler
    }
    
    fun setAppDockManager(manager: AppDockManager) {
        this.appDockManager = manager
    }
    
    fun setAdapter(adapter: AppAdapter) {
        this.adapter = adapter
    }
    
    fun setAppList(list: MutableList<android.content.pm.ResolveInfo>) {
        this.appList = list
    }
    
    fun setWidgetManager(manager: WidgetManager) {
        this.widgetManager = manager
    }
    
    fun setUsageStatsManager(manager: AppUsageStatsManager) {
        this.usageStatsManager = manager
    }
    
    fun setTimeDateManager(manager: TimeDateManager) {
        this.timeDateManager = manager
    }
    
    fun setWeeklyUsageGraph(graph: WeeklyUsageGraphView) {
        this.weeklyUsageGraph = graph
    }
    
    fun setUsageStatsDisplayManager(manager: UsageStatsDisplayManager) {
        this.usageStatsDisplayManager = manager
    }
    
    fun setTodoManager(manager: TodoManager) {
        this.todoManager = manager
    }
    
    fun setFeatureTutorialManager(manager: FeatureTutorialManager) {
        this.featureTutorialManager = manager
    }
    
    fun setBackgroundExecutor(executor: java.util.concurrent.Executor) {
        this.backgroundExecutor = executor
    }
    
    fun setBlockingBackGesture(isBlocking: Boolean) {
        this.isBlockingBackGesture = isBlocking
    }
    
    private var isBlockingBackGesture = false
    
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
        
        // Update gesture exclusion when activity resumes (unless we're temporarily blocking back gestures)
        if (!isBlockingBackGesture) {
            gestureHandler?.updateGestureExclusion()
        }
        
        // PRIORITY 1: Show UI immediately - receivers are already registered in onCreate
        
        // Reapply focus mode state when returning from apps (fast operation)
        appDockManager?.let {
            onFocusModeApply?.invoke(it.getCurrentMode())
            it.refreshWorkspaceToggle()
        }
        
        // Refresh app list when returning to MainActivity (in case hidden apps changed)
        // This ensures unhidden apps appear immediately
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed && appDockManager != null) {
                try {
                    val mainActivity = activity as? MainActivity
                    if (mainActivity != null) {
                        // Force reload from package manager to ensure all apps are included
                        // This is necessary because fullAppList might not have unhidden apps
                        // Use try-catch to handle initialization safely
                        try {
                            mainActivity.loadApps(forceRefresh = false)
                        } catch (e: UninitializedPropertyAccessException) {
                            // Managers not initialized yet, skip refresh
                        }
                    }
                } catch (e: Exception) {
                    // Not MainActivity or error, ignore
                }
            }
        }, 300)
        
        // Ensure app list is loaded - reload if empty (fixes issue where apps don't load)
        if (adapter != null && (appList?.isEmpty() == true || appDockManager == null)) {
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && appDockManager != null) {
                    onLoadApps?.invoke(false)
                }
            }, 150)
        }
        
        // Start widget managers (fast operations)
        widgetManager?.onStart()
        
        // PRIORITY 2: Load lightweight data in background (non-blocking)
        usageStatsManager?.invalidateCache()
        
        // Start update runnable immediately for time/date
        val isPowerSaverMode = sharedPreferences.getBoolean("power_saver_mode", false)
        timeDateManager?.startUpdates(isPowerSaverMode)
        
        // PRIORITY 3: Defer expensive operations until after UI is shown
        handler.postDelayed({
            // Load wallpaper asynchronously (non-blocking)
            wallpaperManagerHelper?.setWallpaperBackground()
            
            // Update battery and usage (lightweight operations)
            onBatteryUpdate?.invoke()
            onUsageUpdate?.invoke()
            
            // Refresh usage data but defer expensive weekly graph loading
            // This would be handled by UsageStatsDisplayManager
            
            // Check date change in background
            backgroundExecutor?.execute {
                usageStatsDisplayManager?.checkDateChangeAndRefreshUsage()
            }
            
            // Load expensive weekly usage graph data after a delay (only if visible)
            handler.postDelayed({
                weeklyUsageGraph?.let {
                    if (it.visibility == View.VISIBLE) {
                        usageStatsDisplayManager?.loadWeeklyUsageData()
                    }
                }
            }, 500) // Load after 500ms delay
            
            // Reschedule todo alarms (non-critical, can wait)
            todoManager?.rescheduleTodoAlarms()
        }, 50) // Small delay to let UI render first
        
        // Check and show feature tutorial if needed (after UI is fully loaded)
        val shouldStartTutorial = intent.getBooleanExtra("start_tutorial", false)
        handler.postDelayed({
            featureTutorialManager?.let {
                if (shouldStartTutorial || it.shouldShowTutorial()) {
                    it.startTutorial()
                }
            }
        }, 1000) // Wait 1 second for all views to be ready
        
        // Execute custom callbacks
        onResumeCallbacks.forEach { it.invoke() }
    }
    
    fun onPause() {
        timeDateManager?.stopUpdates()
        
        // Stop widget manager listening
        widgetManager?.onStop()
        
        // Execute custom callbacks
        onPauseCallbacks.forEach { it.invoke() }
    }
}
