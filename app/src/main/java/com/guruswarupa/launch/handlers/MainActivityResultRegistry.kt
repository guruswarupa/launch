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
import com.guruswarupa.launch.widgets.CalendarEventsWidget
import com.guruswarupa.launch.widgets.CountdownWidget
import com.guruswarupa.launch.widgets.YearProgressWidget
import com.guruswarupa.launch.widgets.GithubContributionWidget

class MainActivityResultRegistry(private val activity: FragmentActivity) {

    // Public properties for the launchers
    val widgetPickerLauncher: ActivityResultLauncher<Intent>
    val widgetConfigurationLauncher: ActivityResultLauncher<Intent>
    val voiceSearchLauncher: ActivityResultLauncher<Intent>

    // Dependencies that need to be passed in or accessed
    private var widgetManager: WidgetManager? = null
    private var widgetVisibilityManager: WidgetVisibilityManager? = null
    private var widgetConfigurationManager: WidgetConfigurationManager? = null
    private var voiceCommandHandler: VoiceCommandHandler? = null
    private var activityResultHandler: ActivityResultHandler? = null
    private var packageManager: android.content.pm.PackageManager? = null
    private var contentResolver: android.content.ContentResolver? = null
    private var searchBox: android.widget.AutoCompleteTextView? = null
    private var appList: MutableList<android.content.pm.ResolveInfo>? = null
    private var yearProgressWidget: YearProgressWidget? = null
    private var githubContributionWidget: GithubContributionWidget? = null
    private var calendarEventsWidget: CalendarEventsWidget? = null
    private var countdownWidget: CountdownWidget? = null

    init {
        // Register ActivityResultLauncher for widget picking
        widgetPickerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                // Handle widget picked
                widgetManager?.handleWidgetPicked(activity, result.data)
            }
        }

        // Register ActivityResultLauncher for widget configuration
        widgetConfigurationLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                widgetVisibilityManager?.update(yearProgressWidget, githubContributionWidget)
                // Refresh system widgets in the drawer
                refreshSystemWidgets()
                // Update visibility again after refreshing system widgets
                widgetVisibilityManager?.update(yearProgressWidget, githubContributionWidget)
                
                // Refresh calendar widget when it becomes visible
                calendarEventsWidget?.let { widget ->
                    val isEnabled = widgetConfigurationManager?.isWidgetEnabled("calendar_events_widget_container") ?: false
                    if (isEnabled) {
                        widget.refresh()
                    }
                }
                
                // Refresh countdown widget when it becomes visible
                countdownWidget?.let { widget ->
                    val isEnabled = widgetConfigurationManager?.isWidgetEnabled("countdown_widget_container") ?: false
                    if (isEnabled) {
                        widget.refresh()
                    }
                }
            }
        }

        // Register ActivityResultLauncher for voice search
        voiceSearchLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Initialize voice command handler if needed for voice search result
                if (voiceCommandHandler == null && packageManager != null && contentResolver != null && 
                    searchBox != null && appList != null) {
                    voiceCommandHandler = VoiceCommandHandler(
                        activity,
                        packageManager!!,
                        contentResolver!!,
                        searchBox!!,
                        appList!!
                    )
                    // Update the dependencies to reflect the new voice command handler
                    setDependencies(voiceCommandHandler = voiceCommandHandler)
                }
                activityResultHandler?.setVoiceCommandHandler(voiceCommandHandler)
            }
            // Always handle the activity result
            activityResultHandler?.handleActivityResult(PermissionManager.VOICE_SEARCH_REQUEST, result.resultCode, result.data)
        }
    }

    // Method to set the required dependencies
    fun setDependencies(
        widgetManager: WidgetManager? = null,
        widgetVisibilityManager: WidgetVisibilityManager? = null,
        widgetConfigurationManager: WidgetConfigurationManager? = null,
        voiceCommandHandler: VoiceCommandHandler? = null,
        activityResultHandler: ActivityResultHandler? = null,
        packageManager: android.content.pm.PackageManager? = null,
        contentResolver: android.content.ContentResolver? = null,
        searchBox: android.widget.AutoCompleteTextView? = null,
        appList: MutableList<android.content.pm.ResolveInfo>? = null,
        yearProgressWidget: YearProgressWidget? = null,
        githubContributionWidget: GithubContributionWidget? = null,
        calendarEventsWidget: CalendarEventsWidget? = null,
        countdownWidget: CountdownWidget? = null
    ) {
        this.widgetManager = widgetManager ?: this.widgetManager
        this.widgetVisibilityManager = widgetVisibilityManager ?: this.widgetVisibilityManager
        this.widgetConfigurationManager = widgetConfigurationManager ?: this.widgetConfigurationManager
        this.voiceCommandHandler = voiceCommandHandler ?: this.voiceCommandHandler
        this.activityResultHandler = activityResultHandler ?: this.activityResultHandler
        this.packageManager = packageManager ?: this.packageManager
        this.contentResolver = contentResolver ?: this.contentResolver
        this.searchBox = searchBox ?: this.searchBox
        this.appList = appList ?: this.appList
        this.yearProgressWidget = yearProgressWidget ?: this.yearProgressWidget
        this.githubContributionWidget = githubContributionWidget ?: this.githubContributionWidget
        this.calendarEventsWidget = calendarEventsWidget ?: this.calendarEventsWidget
        this.countdownWidget = countdownWidget ?: this.countdownWidget
    }

    /**
     * Refreshes system widgets in the drawer by reloading them from WidgetManager
     */
    private fun refreshSystemWidgets() {
        // Reload widgets from WidgetManager
        widgetManager?.reloadWidgets()
    }

    /**
     * Shows the widget configuration activity
     */
    fun showWidgetConfigurationDialog() {
        val intent = Intent(activity, WidgetConfigurationActivity::class.java)
        widgetConfigurationLauncher.launch(intent)
    }
}
