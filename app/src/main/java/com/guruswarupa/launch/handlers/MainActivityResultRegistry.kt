package com.guruswarupa.launch.handlers

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.widgets.WidgetVisibilityManager
import com.guruswarupa.launch.ui.activities.WidgetConfigurationActivity
import com.guruswarupa.launch.utils.VoiceCommandHandler
import com.guruswarupa.launch.core.PermissionManager
import com.guruswarupa.launch.widgets.WidgetLifecycleCoordinator

class MainActivityResultRegistry(private val activity: FragmentActivity) {

    // Public properties for the launchers
    val widgetPickerLauncher: ActivityResultLauncher<Intent>
    val widgetConfigurationLauncher: ActivityResultLauncher<Intent>
    val voiceSearchLauncher: ActivityResultLauncher<Intent>

    /**
     * Container for all dependencies required by the result registry
     */
    data class DependencyContainer(
        var widgetManager: WidgetManager? = null,
        var widgetVisibilityManager: WidgetVisibilityManager? = null,
        var widgetConfigurationManager: WidgetConfigurationManager? = null,
        var voiceCommandHandler: VoiceCommandHandler? = null,
        var activityResultHandler: ActivityResultHandler? = null,
        var packageManager: android.content.pm.PackageManager? = null,
        var contentResolver: android.content.ContentResolver? = null,
        var searchBox: android.widget.AutoCompleteTextView? = null,
        var appList: MutableList<android.content.pm.ResolveInfo>? = null,
        var widgetLifecycleCoordinator: WidgetLifecycleCoordinator? = null
    )

    private var deps = DependencyContainer()

    init {
        // Register ActivityResultLauncher for widget picking
        widgetPickerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                // Handle widget picked
                deps.widgetManager?.handleWidgetPicked(activity, result.data)
            }
        }

        // Register ActivityResultLauncher for widget configuration
        widgetConfigurationLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val yearProgress = deps.widgetLifecycleCoordinator?.let { if (it.isYearProgressWidgetInitialized()) it.yearProgressWidget else null }
                val githubContribution = deps.widgetLifecycleCoordinator?.let { if (it.isGithubContributionWidgetInitialized()) it.githubContributionWidget else null }
                
                deps.widgetVisibilityManager?.update(yearProgress, githubContribution)
                // Refresh system widgets in the drawer
                refreshSystemWidgets()
                // Update visibility again after refreshing system widgets
                deps.widgetVisibilityManager?.update(yearProgress, githubContribution)
                
                // Refresh calendar widget when it becomes visible
                deps.widgetLifecycleCoordinator?.let { coordinator ->
                    if (coordinator.isCalendarEventsWidgetInitialized()) {
                        val isEnabled = deps.widgetConfigurationManager?.isWidgetEnabled("calendar_events_widget_container") ?: false
                        if (isEnabled) {
                            coordinator.calendarEventsWidget.refresh()
                        }
                    }
                }
                
                // Refresh countdown widget when it becomes visible
                deps.widgetLifecycleCoordinator?.let { coordinator ->
                    if (coordinator.isCountdownWidgetInitialized()) {
                        val isEnabled = deps.widgetConfigurationManager?.isWidgetEnabled("countdown_widget_container") ?: false
                        if (isEnabled) {
                            coordinator.countdownWidget.refresh()
                        }
                    }
                }
            }
        }

        // Register ActivityResultLauncher for voice search
        voiceSearchLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Initialize voice command handler if needed for voice search result
                if (deps.voiceCommandHandler == null && deps.packageManager != null && deps.contentResolver != null && 
                    deps.searchBox != null && deps.appList != null) {
                    deps.voiceCommandHandler = VoiceCommandHandler(
                        activity,
                        deps.packageManager!!,
                        deps.contentResolver!!,
                        deps.searchBox!!,
                        deps.appList!!
                    )
                }
                deps.activityResultHandler?.setVoiceCommandHandler(deps.voiceCommandHandler)
            }
            // Always handle the activity result
            deps.activityResultHandler?.handleActivityResult(PermissionManager.VOICE_SEARCH_REQUEST, result.resultCode, result.data)
        }
    }

    /**
     * Sets all dependencies at once
     */
    fun setDependencies(dependencies: DependencyContainer) {
        this.deps = dependencies
    }

    /**
     * Refreshes system widgets in the drawer by reloading them from WidgetManager
     */
    private fun refreshSystemWidgets() {
        // Reload widgets from WidgetManager
        deps.widgetManager?.reloadWidgets()
    }

    /**
     * Shows the widget configuration activity
     */
    fun showWidgetConfigurationDialog() {
        val intent = Intent(activity, WidgetConfigurationActivity::class.java)
        widgetConfigurationLauncher.launch(intent)
    }
}
