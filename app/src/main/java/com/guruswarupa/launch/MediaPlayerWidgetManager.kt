package com.guruswarupa.launch

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import org.json.JSONObject

class MediaPlayerWidgetManager(private val context: Context, private val widgetContainer: FrameLayout, private val addButton: ImageButton) {
    
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, APPWIDGET_HOST_ID)
    private val prefs: SharedPreferences = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
    private var currentWidget: WidgetInfo? = null
    
    companion object {
        private const val APPWIDGET_HOST_ID = 2048 // Different ID from regular widgets
        private const val TAG = "MediaPlayerWidgetManager"
        private const val PREFS_MEDIA_WIDGET_KEY = "media_player_widget"
        
        // Common media player package names
        private val MEDIA_PLAYER_PACKAGES = setOf(
            "com.spotify.music",
            "com.deezer.android",
            "com.google.android.music",
            "com.apple.android.music",
            "com.amazon.mp3",
            "com.soundcloud.android",
            "com.pandora.android",
            "com.iheartradio.android",
            "com.tidal.android",
            "com.spotify.lite",
            "com.google.android.apps.youtube.music"
        )
    }
    
    data class WidgetInfo(
        val appWidgetId: Int,
        val providerPackage: String,
        val providerClass: String,
        val minWidth: Int,
        val minHeight: Int
    )
    
    init {
        // Start listening for widget updates
        appWidgetHost.startListening()
        loadWidget()
        updateAddButtonVisibility()
    }
    
    fun requestPickWidget(activity: Activity, requestCode: Int) {
        try {
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetHost.allocateAppWidgetId())
            pickIntent.putExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, true)
            activity.startActivityForResult(pickIntent, requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting widget picker", e)
            Toast.makeText(context, "Error opening widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun handleWidgetPicked(activity: Activity, data: Intent?, requestCode: Int) {
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        
        if (appWidgetId == -1) {
            Log.e(TAG, "No widget ID in result")
            Toast.makeText(context, "Invalid widget selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "Widget picked with ID: $appWidgetId")
        
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo == null) {
            Log.e(TAG, "Widget info not found for ID: $appWidgetId")
            Toast.makeText(context, "Widget info not found", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return
        }
        
        Log.d(TAG, "Widget info found: ${appWidgetInfo.provider.packageName}")
        
        // Check if widget needs configuration
        if (appWidgetInfo.configure != null) {
            Log.d(TAG, "Widget requires configuration")
            // Launch configuration activity
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            configIntent.component = appWidgetInfo.configure
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            try {
                activity.startActivityForResult(configIntent, requestCode + 1) // REQUEST_CONFIGURE_MEDIA_WIDGET
            } catch (e: Exception) {
                Log.e(TAG, "Error launching widget config", e)
                // If config fails, try to bind anyway
                bindWidget(appWidgetId, appWidgetInfo)
            }
        } else {
            // No configuration needed, bind directly
            Log.d(TAG, "Widget does not require configuration, binding directly")
            bindWidget(appWidgetId, appWidgetInfo)
        }
    }
    
    private fun isMediaPlayerWidget(widgetInfo: AppWidgetProviderInfo): Boolean {
        val packageName = widgetInfo.provider.packageName
        val label = widgetInfo.loadLabel(context.packageManager).toString().lowercase()
        
        // Check if widget is from a known media player package
        if (MEDIA_PLAYER_PACKAGES.contains(packageName)) {
            return true
        }
        
        // Also check label for common media player keywords
        return label.contains("music", ignoreCase = true) ||
               label.contains("player", ignoreCase = true) ||
               label.contains("spotify", ignoreCase = true) ||
               label.contains("deezer", ignoreCase = true) ||
               label.contains("soundcloud", ignoreCase = true) ||
               label.contains("pandora", ignoreCase = true) ||
               label.contains("tidal", ignoreCase = true) ||
               label.contains("youtube music", ignoreCase = true) ||
               label.contains("youtube", ignoreCase = true)
    }
    
    fun handleWidgetConfigured(activity: Activity, appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo != null) {
            bindWidget(appWidgetId, appWidgetInfo)
        }
    }
    
    private fun bindWidget(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            // Remove existing widget if any
            currentWidget?.let { removeWidget(it.appWidgetId, showToast = false) }
            
            Log.d(TAG, "Attempting to bind widget ID: $appWidgetId, Provider: ${appWidgetInfo.provider.packageName}")
            
            // Create widget view
            val widgetView = try {
                appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create widget view: ${e.message}", e)
                
                // Try to bind explicitly
                val bound = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        appWidgetManager.bindAppWidgetIdIfAllowed(
                            appWidgetId,
                            appWidgetInfo.profile,
                            appWidgetInfo.provider,
                            null
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, appWidgetInfo.provider)
                    }
                } catch (bindException: Exception) {
                    Log.e(TAG, "Binding also failed: ${bindException.message}", bindException)
                    false
                }
                
                if (!bound) {
                    Log.e(TAG, "Cannot bind widget.")
                    Toast.makeText(
                        context,
                        "Cannot add this widget. Some widgets require special launcher permissions.",
                        Toast.LENGTH_LONG
                    ).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }
                
                // Try creating view again after binding
                try {
                    appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
                } catch (e2: Exception) {
                    Log.e(TAG, "Still failed to create view after binding: ${e2.message}", e2)
                    Toast.makeText(context, "Failed to create widget: ${e2.message}", Toast.LENGTH_SHORT).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }
            }
            
            if (widgetView == null) {
                Log.e(TAG, "Widget view is null")
                Toast.makeText(context, "Failed to create widget view", Toast.LENGTH_SHORT).show()
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return
            }
            
            Log.d(TAG, "Widget view created successfully")
            
            // Set widget properties on the view
            widgetView.setAppWidget(appWidgetId, appWidgetInfo)
            
            // Get widget dimensions and convert from dp to pixels
            val displayMetrics = context.resources.displayMetrics
            val minWidth = (appWidgetInfo.minWidth * displayMetrics.density).toInt()
            val minHeight = (appWidgetInfo.minHeight * displayMetrics.density).toInt()
            
            // Update widget to ensure it renders properly
            appWidgetManager.updateAppWidget(appWidgetId, null)
            
            // Save widget info
            val widgetInfo = WidgetInfo(
                appWidgetId = appWidgetId,
                providerPackage = appWidgetInfo.provider.packageName,
                providerClass = appWidgetInfo.provider.className,
                minWidth = minWidth,
                minHeight = minHeight
            )
            currentWidget = widgetInfo
            
            // Create container for widget with long press handler
            val widgetContainerView = createWidgetContainer(widgetView, appWidgetId, appWidgetInfo)
            
            // Remove add button and any existing widget, then add new widget
            removeAllWidgetViews()
            widgetContainer.addView(widgetContainerView)
            
            saveWidget()
            updateAddButtonVisibility()
            
            Toast.makeText(context, "Media player widget added", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error binding widget", e)
            Toast.makeText(context, "Error adding widget: ${e.message}", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }
    
    private fun createWidgetContainer(
        widgetView: AppWidgetHostView,
        appWidgetId: Int,
        appWidgetInfo: AppWidgetProviderInfo
    ): View {
        // Calculate widget dimensions in pixels
        val displayMetrics = context.resources.displayMetrics
        val widgetWidth = (appWidgetInfo.minWidth * displayMetrics.density).toInt()
        val widgetHeight = (appWidgetInfo.minHeight * displayMetrics.density).toInt()
        val paddingPx = (16 * context.resources.displayMetrics.density).toInt()
        
        val containerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (widgetHeight + paddingPx * 2).coerceAtLeast((80 * displayMetrics.density).toInt())
            ).apply {
                // Add padding to increase long-press area around widget
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            }
            tag = appWidgetId
            
            // Long press to remove - make entire container area clickable
            setOnLongClickListener {
                showRemoveWidgetDialog(appWidgetId, appWidgetInfo.loadLabel(context.packageManager).toString())
                true
            }
            
            // Also make it clickable to ensure touch events are captured
            isClickable = true
            isFocusable = true
        }
        
        // Add widget view to container with proper dimensions
        widgetView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            widgetHeight.coerceAtLeast((60 * displayMetrics.density).toInt())
        )
        
        // Set minimum dimensions on the widget view itself
        widgetView.minimumWidth = widgetWidth
        widgetView.minimumHeight = widgetHeight
        
        containerLayout.addView(widgetView)
        
        // Update widget after adding to ensure it renders
        appWidgetManager.updateAppWidget(appWidgetId, null)
        
        return containerLayout
    }
    
    private fun showRemoveWidgetDialog(appWidgetId: Int, widgetName: String) {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Remove Media Player Widget")
            .setMessage("Remove \"$widgetName\" widget?")
            .setPositiveButton("Remove") { _, _ ->
                removeWidget(appWidgetId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun removeAllWidgetViews() {
        // Remove all views except the add button
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child != addButton) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { widgetContainer.removeView(it) }
    }
    
    fun removeWidget(appWidgetId: Int, showToast: Boolean = true) {
        try {
            // Find and remove widget view (but keep add button)
            var widgetViewFound = false
            for (i in 0 until widgetContainer.childCount) {
                val child = widgetContainer.getChildAt(i)
                if (child.tag == appWidgetId) {
                    widgetContainer.removeView(child)
                    widgetViewFound = true
                    break
                }
            }
            
            // If not found by tag, remove all non-button views
            if (!widgetViewFound) {
                removeAllWidgetViews()
            }
            
            // Remove from current widget
            if (currentWidget?.appWidgetId == appWidgetId) {
                currentWidget = null
            }
            
            // Delete widget ID
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            
            // Save updated state
            saveWidget()
            updateAddButtonVisibility()
            
            if (showToast) {
                Toast.makeText(context, "Media player widget removed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing widget", e)
            Toast.makeText(context, "Error removing widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAddButtonVisibility() {
        // Show "+" button only when no widget is present
        addButton.visibility = if (currentWidget == null) View.VISIBLE else View.GONE
    }
    
    private fun saveWidget() {
        try {
            currentWidget?.let { widget ->
                val json = JSONObject().apply {
                    put("appWidgetId", widget.appWidgetId)
                    put("providerPackage", widget.providerPackage)
                    put("providerClass", widget.providerClass)
                    put("minWidth", widget.minWidth)
                    put("minHeight", widget.minHeight)
                }
                prefs.edit().putString(PREFS_MEDIA_WIDGET_KEY, json.toString()).apply()
            } ?: run {
                prefs.edit().remove(PREFS_MEDIA_WIDGET_KEY).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving widget", e)
        }
    }
    
    private fun loadWidget() {
        try {
            val widgetJson = prefs.getString(PREFS_MEDIA_WIDGET_KEY, null) ?: return
            
            val json = JSONObject(widgetJson)
            val appWidgetId = json.getInt("appWidgetId")
            
            // Verify widget still exists
            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (appWidgetInfo != null) {
                val widgetInfo = WidgetInfo(
                    appWidgetId = appWidgetId,
                    providerPackage = json.getString("providerPackage"),
                    providerClass = json.getString("providerClass"),
                    minWidth = json.getInt("minWidth"),
                    minHeight = json.getInt("minHeight")
                )
                currentWidget = widgetInfo
                
                // Recreate widget view
                recreateWidgetView(widgetInfo, appWidgetInfo)
            } else {
                // Widget no longer exists, clean up
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                currentWidget = null
                saveWidget()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading widget", e)
        }
    }
    
    private fun recreateWidgetView(widgetInfo: WidgetInfo, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            val widgetView = appWidgetHost.createView(context, widgetInfo.appWidgetId, appWidgetInfo)
            widgetView.setAppWidget(widgetInfo.appWidgetId, appWidgetInfo)
            
            // Update widget to ensure it renders properly
            appWidgetManager.updateAppWidget(widgetInfo.appWidgetId, null)
            
            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo.appWidgetId, appWidgetInfo)
            
            removeAllWidgetViews()
            widgetContainer.addView(widgetContainerView)
            updateAddButtonVisibility()
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating widget view", e)
            // Remove invalid widget
            currentWidget = null
            appWidgetHost.deleteAppWidgetId(widgetInfo.appWidgetId)
            saveWidget()
            updateAddButtonVisibility()
        }
    }
    
    fun onStop() {
        appWidgetHost.stopListening()
    }
    
    fun onStart() {
        appWidgetHost.startListening()
    }
    
    fun onDestroy() {
        appWidgetHost.stopListening()
    }
}
