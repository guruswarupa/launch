package com.guruswarupa.launch.managers

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
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.WidgetConfigurationActivity
import org.json.JSONArray
import org.json.JSONObject

class WidgetManager(private val context: Context, private val widgetContainer: LinearLayout) {
    
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val appWidgetHost: AppWidgetHost = AppWidgetHost(context, APPWIDGET_HOST_ID)
    private val prefs: SharedPreferences = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
    private val widgets = mutableListOf<WidgetInfo>()
    private val widgetOptionsCache = mutableMapOf<Int, String>()
    
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
        val minHeight: Int,
        val customHeightDp: Int? = null
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
            Toast.makeText(context, "Error opening widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun requestPickWidgetWithLauncher(launcher: ActivityResultLauncher<Intent>) {
        try {
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetHost.allocateAppWidgetId())
            pickIntent.putExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, true)
            launcher.launch(pickIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening widget picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Binds a specific widget provider directly
     */
    fun bindProvider(activity: Activity, providerPackage: String, providerClass: String, requestCode: Int) {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val providers = appWidgetManager.installedProviders
        val providerInfo = providers.find { 
            it.provider.packageName == providerPackage && it.provider.className == providerClass 
        } ?: return
        
        val success = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
        } catch (_: Exception) {
            false
        }
        
        if (success) {
            bindWidget(appWidgetId, providerInfo)
            // No need to configure if it doesn't have a configure activity
            if (providerInfo.configure != null) {
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                configIntent.component = providerInfo.configure
                configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                activity.startActivityForResult(configIntent, 801) // REQUEST_CONFIGURE_WIDGET
            } else {
                // Refresh activity list
                (activity as? WidgetConfigurationActivity)?.loadWidgets()
            }
        } else {
            // Request permission to bind
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            activity.startActivityForResult(intent, requestCode)
        }
    }
    
    fun handleWidgetPicked(activity: Activity, data: Intent?) {
        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        
        if (appWidgetId == -1) {
            Toast.makeText(context, "Invalid widget selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo == null) {
            Toast.makeText(context, "Widget info not found", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            return
        }
        
        // Check if widget needs configuration
        if (appWidgetInfo.configure != null) {
            // Launch configuration activity
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            configIntent.component = appWidgetInfo.configure
            configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            try {
                // Use REQUEST_CONFIGURE_WIDGET constant from MainActivity
                activity.startActivityForResult(configIntent, 801) // REQUEST_CONFIGURE_WIDGET
            } catch (_: Exception) {
                // If config fails, try to bind anyway
                bindWidget(appWidgetId, appWidgetInfo)
            }
        } else {
            // No configuration needed, bind directly
            bindWidget(appWidgetId, appWidgetInfo)
        }
    }
    
    fun handleWidgetConfigured(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo != null) {
            bindWidget(appWidgetId, appWidgetInfo)
        }
    }
    
    private fun bindWidget(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            // Create widget view - this will automatically bind if needed
            val widgetView = try {
                appWidgetHost.createView(context, appWidgetId, appWidgetInfo)
            } catch (_: Exception) {
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
                } catch (_: Exception) {
                    false
                }
                
                if (!bound) {
                    // Binding failed - this is expected for some widgets that require special permissions
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
                    Toast.makeText(context, "Failed to create widget: ${e2.message}", Toast.LENGTH_SHORT).show()
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    return
                }
            }
            
            if (widgetView == null) {
                Toast.makeText(context, "Failed to create widget view", Toast.LENGTH_SHORT).show()
                appWidgetHost.deleteAppWidgetId(appWidgetId)
                return
            }
            
            // Set widget properties on the view
            widgetView.setAppWidget(appWidgetId, appWidgetInfo)
            
            // Save widget info first (needed for button visibility calculation)
            val existingCustomHeightDp = widgets.firstOrNull { it.appWidgetId == appWidgetId }?.customHeightDp
            val widgetInfo = WidgetInfo(
                appWidgetId = appWidgetId,
                providerPackage = appWidgetInfo.provider.packageName,
                providerClass = appWidgetInfo.provider.className,
                minWidth = appWidgetInfo.minWidth,
                minHeight = appWidgetInfo.minHeight,
                customHeightDp = existingCustomHeightDp
            )
            // Avoid duplicates
            widgets.removeAll { it.appWidgetId == appWidgetId }
            widgets.add(widgetInfo)
            
            // Create container for widget with controls
            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo, appWidgetInfo)
            
            // Add to layout if container exists
            widgetContainer.addView(widgetContainerView)
            
            saveWidgets()
            
            Toast.makeText(context, "Widget added successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error adding widget: ${e.message}", Toast.LENGTH_SHORT).show()
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }
    
    private fun createWidgetContainer(
        widgetView: AppWidgetHostView,
        widgetInfo: WidgetInfo,
        appWidgetInfo: AppWidgetProviderInfo
    ): View {
        val appWidgetId = widgetInfo.appWidgetId
        val resizeHandleSizePx = dpToPx(34)
        val resizeHandleInsetPx = dpToPx(6)
        val minHeightPx = dpToPx(120)
        val maxHeightPx = (context.resources.displayMetrics.heightPixels * 0.85f).toInt()

        val containerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
            background = null
            tag = appWidgetId
        }

        widgetView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            widgetInfo.customHeightDp?.let { dpToPx(it) } ?: FrameLayout.LayoutParams.WRAP_CONTENT
        )
        containerLayout.addView(widgetView)

        val resizeHandle = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                resizeHandleSizePx,
                resizeHandleSizePx,
                Gravity.END or Gravity.BOTTOM
            ).apply {
                marginEnd = resizeHandleInsetPx
                bottomMargin = resizeHandleInsetPx
            }
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundResource(R.drawable.drawer_widgets_action_bg)
            imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, android.R.color.white)
            )
            contentDescription = "Resize widget"
            setPadding(dpToPx(7), dpToPx(7), dpToPx(7), dpToPx(7))
            visibility = View.GONE
        }
        containerLayout.addView(resizeHandle)
        val hideResizeHandleRunnable = Runnable { resizeHandle.visibility = View.GONE }

        val showResizeHandle = {
            resizeHandle.visibility = View.VISIBLE
            resizeHandle.bringToFront()
            resizeHandle.removeCallbacks(hideResizeHandleRunnable)
            resizeHandle.postDelayed(hideResizeHandleRunnable, 4000L)
        }

        // Show resize control on long press or double tap.
        containerLayout.setOnLongClickListener {
            showResizeHandle()
            true
        }
        widgetView.setOnLongClickListener {
            showResizeHandle()
            true
        }
        val resizeGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                showResizeHandle()
                return true
            }
        })
        containerLayout.setOnTouchListener { _, event ->
            resizeGestureDetector.onTouchEvent(event)
            false
        }
        widgetView.setOnTouchListener { _, event ->
            resizeGestureDetector.onTouchEvent(event)
            false
        }

        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var startRawY = 0f
            private var startHeightPx = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        resizeHandle.removeCallbacks(hideResizeHandleRunnable)
                        startRawY = event.rawY
                        startHeightPx = widgetView.height
                            .takeIf { it > 0 }
                            ?: widgetView.measuredHeight.takeIf { it > 0 }
                            ?: dpToPx(widgetInfo.customHeightDp ?: widgetInfo.minHeight.coerceAtLeast(120))
                        containerLayout.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = (event.rawY - startRawY).toInt()
                        val targetHeightPx = (startHeightPx + deltaY).coerceIn(minHeightPx, maxHeightPx)
                        val lp = widgetView.layoutParams as FrameLayout.LayoutParams
                        if (lp.height != targetHeightPx) {
                            lp.height = targetHeightPx
                            widgetView.layoutParams = lp
                            val targetHeightDp = pxToDp(targetHeightPx).coerceAtLeast(1)
                            applyWidgetSizeOptions(
                                widgetView,
                                appWidgetId,
                                containerLayout,
                                appWidgetInfo.minHeight,
                                forcedHeightDp = targetHeightDp
                            )
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val finalHeightDp = pxToDp(widgetView.height).coerceAtLeast(1)
                        updateWidgetCustomHeight(appWidgetId, finalHeightDp)
                        containerLayout.parent?.requestDisallowInterceptTouchEvent(false)
                        resizeHandle.postDelayed(hideResizeHandleRunnable, 2500L)
                        return true
                    }
                }
                return false
            }
        })

        containerLayout.post {
            applyWidgetSizeOptions(
                widgetView,
                appWidgetId,
                containerLayout,
                appWidgetInfo.minHeight,
                forcedHeightDp = widgetInfo.customHeightDp
            )
        }
        containerLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyWidgetSizeOptions(
                widgetView,
                appWidgetId,
                containerLayout,
                appWidgetInfo.minHeight,
                forcedHeightDp = widgetInfo.customHeightDp
            )
        }

        return containerLayout
    }

    private fun applyWidgetSizeOptions(
        widgetView: AppWidgetHostView,
        appWidgetId: Int,
        containerView: View,
        providerMinHeightDp: Int,
        forcedHeightDp: Int? = null
    ) {
        val widthPx = containerView.width
        if (widthPx <= 0) return

        val widthDp = pxToDp(widthPx).coerceAtLeast(1)
        val measuredHeightDp = pxToDp(containerView.height)
        val heightDp = forcedHeightDp?.coerceAtLeast(1)
            ?: if (measuredHeightDp > 0) measuredHeightDp else providerMinHeightDp.coerceAtLeast(1)
        val optionsKey = "$widthDp:$heightDp"
        if (widgetOptionsCache[appWidgetId] == optionsKey) return
        widgetOptionsCache[appWidgetId] = optionsKey

        try {
            widgetView.updateAppWidgetSize(
                null,
                widthDp,
                heightDp,
                widthDp,
                heightDp
            )
        } catch (_: Exception) {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            runCatching { appWidgetManager.updateAppWidgetOptions(appWidgetId, options) }
        }
    }

    private fun pxToDp(px: Int): Int {
        return (px / context.resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private fun updateWidgetCustomHeight(appWidgetId: Int, customHeightDp: Int) {
        val index = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        if (index < 0) return
        widgets[index] = widgets[index].copy(customHeightDp = customHeightDp)
        saveWidgets()
    }
    
    private fun showWidgetOptionsMenu(appWidgetId: Int, appWidgetInfo: AppWidgetProviderInfo) {
        val currentIndex = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        val widgetName = appWidgetInfo.loadLabel(context.packageManager)
        
        val options = mutableListOf<String>()
        
        if (currentIndex > 0) {
            options.add("Move Up")
        }
        if (currentIndex < widgets.size - 1) {
            options.add("Move Down")
        }
        options.add("Delete")
        
        if (options.isEmpty()) return
        
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(widgetName)
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "Move Up" -> moveWidgetUp(appWidgetId)
                    "Move Down" -> moveWidgetDown(appWidgetId)
                    "Delete" -> showRemoveWidgetDialog(appWidgetId, widgetName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        fixDialogTextColors(dialog)
    }
    
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(context, R.color.text)
            val listView = dialog.listView
            listView?.post {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(textColor)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    
    fun moveWidgetUp(appWidgetId: Int) {
        val currentIndex = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        if (currentIndex > 0 && currentIndex < widgetContainer.childCount) {
            // Swap widgets in list
            val widget = widgets.removeAt(currentIndex)
            widgets.add(currentIndex - 1, widget)
            
            // Swap views in container
            val viewToMove = widgetContainer.getChildAt(currentIndex)
            val viewToSwapWith = widgetContainer.getChildAt(currentIndex - 1)
            
            widgetContainer.removeView(viewToMove)
            widgetContainer.removeView(viewToSwapWith)
            
            widgetContainer.addView(viewToSwapWith, currentIndex - 1)
            widgetContainer.addView(viewToMove, currentIndex - 1)
            
            saveWidgets()
        }
    }
    
    fun moveWidgetDown(appWidgetId: Int) {
        val currentIndex = widgets.indexOfFirst { it.appWidgetId == appWidgetId }
        if (currentIndex < widgets.size - 1 && currentIndex < widgetContainer.childCount - 1) {
            // Swap widgets in list
            val widget = widgets.removeAt(currentIndex)
            widgets.add(currentIndex + 1, widget)
            
            // Swap views in container
            val viewToMove = widgetContainer.getChildAt(currentIndex)
            val viewToSwapWith = widgetContainer.getChildAt(currentIndex + 1)
            
            widgetContainer.removeView(viewToSwapWith)
            widgetContainer.removeView(viewToMove)
            
            widgetContainer.addView(viewToSwapWith, currentIndex)
            widgetContainer.addView(viewToMove, currentIndex + 1)
            
            saveWidgets()
        }
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
            widgetOptionsCache.remove(appWidgetId)
            
            // Delete widget ID
            appWidgetHost.deleteAppWidgetId(appWidgetId)
            
            // Save updated list
            saveWidgets()
            
            Toast.makeText(context, "Widget removed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
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
                    widget.customHeightDp?.let { put("customHeightDp", it) }
                }
                jsonArray.put(json)
            }
            prefs.edit { putString(PREFS_WIDGETS_KEY, jsonArray.toString()) }
        } catch (_: Exception) {
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
                        minHeight = json.getInt("minHeight"),
                        customHeightDp = if (json.has("customHeightDp")) json.optInt("customHeightDp") else null
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
        } catch (_: Exception) {
        }
    }
    
    private fun recreateWidgetView(widgetInfo: WidgetInfo, appWidgetInfo: AppWidgetProviderInfo) {
        try {
            val widgetView = appWidgetHost.createView(context, widgetInfo.appWidgetId, appWidgetInfo)
            val widgetContainerView = createWidgetContainer(widgetView, widgetInfo, appWidgetInfo)
            widgetContainer.addView(widgetContainerView)
        } catch (_: Exception) {
            // Remove invalid widget
            widgets.remove(widgetInfo)
            appWidgetHost.deleteAppWidgetId(widgetInfo.appWidgetId)
        }
    }
    
    fun onStop() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            // Handle case where stopListening fails due to stale widget references
            Log.w(TAG, "Error stopping widget host listening", e)
        }
    }
    
    fun onStart() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error starting widget host listening", e)
        }
    }
    
    /**
     * Reloads widgets from SharedPreferences and recreates views
     */
    fun reloadWidgets() {
        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until widgetContainer.childCount) {
            val child = widgetContainer.getChildAt(i)
            if (child.tag is Int) {
                viewsToRemove.add(child)
            }
        }
        viewsToRemove.forEach { widgetContainer.removeView(it) }
        widgets.clear()
        widgetOptionsCache.clear()

        loadWidgets()
    }
    
    fun onDestroy() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            // Handle case where stopListening fails due to stale widget references
            Log.w(TAG, "Error stopping widget host listening in destroy", e)
        }
    }
}
