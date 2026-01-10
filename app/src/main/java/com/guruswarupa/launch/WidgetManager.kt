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
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class WidgetManager(private val context: Context, private val widgetContainer: LinearLayout) {
    
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, APPWIDGET_HOST_ID)
    private val prefs: SharedPreferences = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
    private val widgets = mutableListOf<WidgetInfo>()
    
    companion object {
        private const val APPWIDGET_HOST_ID = 1024
        private const val TAG = "WidgetManager"
        private const val PREFS_WIDGETS_KEY = "saved_widgets"
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
        loadWidgets()
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
                // Use REQUEST_CONFIGURE_WIDGET constant from MainActivity
                activity.startActivityForResult(configIntent, 801) // REQUEST_CONFIGURE_WIDGET
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
    
    fun handleWidgetConfigured(activity: Activity, appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo != null) {
            bindWidget(appWidgetId, appWidgetInfo)
        }
    }
    
    private fun bindWidget(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            Log.d(TAG, "Attempting to bind widget ID: $appWidgetId, Provider: ${appWidgetInfo.provider.packageName}")
            
            // For launchers, widgets picked from the picker should already be bound
            // We just need to create the view using our AppWidgetHost
            // The widget ID should already be bound to our host when returned from picker
            
            // Create widget view - this will automatically bind if needed
            val widgetView = try {
                appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create widget view: ${e.message}", e)
                
                // If creation fails, the widget might not be bound to our host
                // Try to explicitly bind it (this may fail if we don't have permissions)
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
                    // Binding failed - this is expected for some widgets that require special permissions
                    Log.e(TAG, "Cannot bind widget. This launcher may not have widget binding permissions.")
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
            
            // Set default size if not specified
            val minWidth = appWidgetInfo.minWidth
            val minHeight = appWidgetInfo.minHeight
            
            // Convert from density-independent pixels to actual pixels
            val displayMetrics = context.resources.displayMetrics
            val widthPx = (minWidth * displayMetrics.density).toInt()
            val heightPx = (minHeight * displayMetrics.density).toInt()
            
            // Set layout parameters for the widget view
            widgetView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            
            // Create container for widget with delete button
            val widgetContainerView = createWidgetContainer(widgetView, appWidgetId, appWidgetInfo)
            
            // Add to layout
            widgetContainer.addView(widgetContainerView)
            
            // Save widget info
            val widgetInfo = WidgetInfo(
                appWidgetId = appWidgetId,
                providerPackage = appWidgetInfo.provider.packageName,
                providerClass = appWidgetInfo.provider.className,
                minWidth = minWidth,
                minHeight = minHeight
            )
            widgets.add(widgetInfo)
            saveWidgets()
            
            Toast.makeText(context, "Widget added successfully", Toast.LENGTH_SHORT).show()
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
        // Simple container with no background, just the widget view
        val containerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            // No background - transparent
            background = null
            tag = appWidgetId
            
            // Long press to remove widget
            setOnLongClickListener {
                showRemoveWidgetDialog(appWidgetId, appWidgetInfo.loadLabel(context.packageManager))
                true
            }
        }
        
        containerLayout.addView(widgetView)
        
        return containerLayout
    }
    
    private fun showRemoveWidgetDialog(appWidgetId: Int, widgetName: String) {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Remove Widget")
            .setMessage("Remove \"$widgetName\" widget?")
            .setPositiveButton("Remove") { _, _ ->
                removeWidget(appWidgetId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    
    fun removeWidget(appWidgetId: Int) {
        try {
            // Find and remove widget view
            for (i in 0 until widgetContainer.childCount) {
                val view = widgetContainer.getChildAt(i)
                if (view.tag == appWidgetId) {
                    widgetContainer.removeViewAt(i)
                    break
                }
            }
            
            // Remove from list
            widgets.removeAll { it.appWidgetId == appWidgetId }
            
            // Delete widget ID
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            
            // Save updated list
            saveWidgets()
            
            Toast.makeText(context, "Widget removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error removing widget", e)
            Toast.makeText(context, "Error removing widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveWidgets() {
        try {
            val jsonArray = JSONArray()
            widgets.forEach { widget ->
                val json = JSONObject().apply {
                    put("appWidgetId", widget.appWidgetId)
                    put("providerPackage", widget.providerPackage)
                    put("providerClass", widget.providerClass)
                    put("minWidth", widget.minWidth)
                    put("minHeight", widget.minHeight)
                }
                jsonArray.put(json)
            }
            prefs.edit().putString(PREFS_WIDGETS_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving widgets", e)
        }
    }
    
    private fun loadWidgets() {
        try {
            val widgetsJson = prefs.getString(PREFS_WIDGETS_KEY, null) ?: return
            val jsonArray = JSONArray(widgetsJson)
            
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
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
                    widgets.add(widgetInfo)
                    
                    // Recreate widget view
                    recreateWidgetView(widgetInfo, appWidgetInfo)
                } else {
                    // Widget no longer exists, clean up
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                }
            }
            
            // Save cleaned up list
            if (widgets.size != jsonArray.length()) {
                saveWidgets()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading widgets", e)
        }
    }
    
    private fun recreateWidgetView(widgetInfo: WidgetInfo, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            val widgetView = appWidgetHost.createView(context, widgetInfo.appWidgetId, appWidgetInfo)
            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo.appWidgetId, appWidgetInfo)
            widgetContainer.addView(widgetContainerView)
        } catch (e: Exception) {
            Log.e(TAG, "Error recreating widget view", e)
            // Remove invalid widget
            widgets.remove(widgetInfo)
            appWidgetHost.deleteAppWidgetId(widgetInfo.appWidgetId)
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
