package com.guruswarupa.launch.ui.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.managers.WallpaperManagerHelper
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.ui.adapters.WidgetConfigAdapter
import com.guruswarupa.launch.utils.WidgetPreviewManager
import java.util.concurrent.Executors
import com.guruswarupa.launch.R
import com.guruswarupa.launch.handlers.ActivityResultHandler
import com.guruswarupa.launch.utils.BlurUtils

class WidgetConfigurationActivity : AppCompatActivity() {

    private lateinit var widgetConfigManager: WidgetConfigurationManager
    private lateinit var previewManager: WidgetPreviewManager
    private lateinit var widgetManager: WidgetManager
    private var adapter: WidgetConfigAdapter? = null
    private val prefsName = "com.guruswarupa.launch.PREFS"
    private lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    private val backgroundExecutor = Executors.newFixedThreadPool(2)
    
    // Widget data
    private var allWidgets = mutableListOf<WidgetConfigurationManager.WidgetInfo>()
    private var filteredWidgets = mutableListOf<WidgetConfigurationManager.WidgetInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val systemBarManager = SystemBarManager(this)
        window.decorView.post {
            systemBarManager.makeSystemBarsTransparent()
            // Keep white status bar icons in both light and dark app themes.
            WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        
        supportRequestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_widget_configuration)

        val mainContent = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(mainContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + 16.toPx(),
                view.paddingRight,
                systemBars.bottom + 16.toPx()
            )
            insets
        }

        val sharedPreferences = getSharedPreferences(prefsName, MODE_PRIVATE)
        widgetConfigManager = WidgetConfigurationManager(this, sharedPreferences)
        previewManager = WidgetPreviewManager(this)
        
        // Initialize WidgetManager
        widgetManager = WidgetManager(this, android.widget.LinearLayout(this))

        val wallpaperBackground = findViewById<ImageView>(R.id.wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(this, wallpaperBackground, null, backgroundExecutor)
        wallpaperManagerHelper.setWallpaperBackground()

        val recyclerView = findViewById<RecyclerView>(R.id.widgets_recycler_view)
        val cancelButton = findViewById<Button>(R.id.cancel_button)
        val saveButton = findViewById<Button>(R.id.save_button)
        val searchInput = findViewById<EditText>(R.id.search_widget_input)

        // Load current widget configuration
        loadWidgets()

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        // Setup drag to reorder
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }
                
                // Move item in filteredWidgets
                val movedItem = filteredWidgets.removeAt(fromPos)
                filteredWidgets.add(toPos, movedItem)
                
                // Update the original list to maintain order
                updateOriginalListOrder()
                
                adapter?.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                    viewHolder?.itemView?.scaleX = 1.05f
                    viewHolder?.itemView?.scaleY = 1.05f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Setup button listeners
        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            widgetConfigManager.saveWidgetOrder(allWidgets)
            setResult(RESULT_OK)
            finish()
        }
        
        // Setup search functionality
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterWidgets(s?.toString() ?: "")
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    fun loadWidgets() {
        allWidgets = widgetConfigManager.getWidgetConfiguration().toMutableList()
        filteredWidgets = allWidgets.toMutableList()
        
        if (adapter == null) {
            adapter = WidgetConfigAdapter(
                context = this,
                widgets = filteredWidgets,
                previewManager = previewManager,
                onToggleChanged = { position, isChecked ->
                    val widgetId = filteredWidgets[position].id
                    updateWidgetState(widgetId, isChecked)
                }
            )
            findViewById<RecyclerView>(R.id.widgets_recycler_view).adapter = adapter
        } else {
            adapter?.updateWidgets(filteredWidgets)
            adapter?.notifyDataSetChanged()
        }
        
        // Update header visibility
        val header = findViewById<TextView>(R.id.widgets_header)
        header.visibility = if (filteredWidgets.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                ActivityResultHandler.REQUEST_PICK_WIDGET -> {
                    widgetManager.handleWidgetPicked(this, data)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadWidgets()
                    }, 500)
                }
                ActivityResultHandler.REQUEST_CONFIGURE_WIDGET -> {
                    val appWidgetId = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                    if (appWidgetId != -1) {
                        widgetManager.handleWidgetConfigured(appWidgetId)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            loadWidgets()
                        }, 500)
                    }
                }
                ActivityResultHandler.REQUEST_BIND_WIDGET -> {
                    // This is usually handled by WidgetManager if we pass it, 
                    // but here we just need to reload to see the newly bound widget instance
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadWidgets()
                    }, 500)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wallpaperManagerHelper.isInitialized) {
            wallpaperManagerHelper.cleanup()
        }
        if (::previewManager.isInitialized) {
            previewManager.cleanup()
        }
        backgroundExecutor.shutdown()
    }
    
    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    fun updateWidgetState(widgetId: String, enabled: Boolean) {
        // Update both lists when called from adapter
        val filteredPosition = filteredWidgets.indexOfFirst { it.id == widgetId }
        val originalPosition = allWidgets.indexOfFirst { it.id == widgetId }
        
        if (filteredPosition >= 0) {
            filteredWidgets[filteredPosition] = filteredWidgets[filteredPosition].copy(enabled = enabled)
            adapter?.notifyItemChanged(filteredPosition)
        }
        
        if (originalPosition >= 0) {
            allWidgets[originalPosition] = allWidgets[originalPosition].copy(enabled = enabled)
        }
        
        val widget = allWidgets.find { it.id == widgetId }
        if (widget != null) {
            val action = if (enabled) "enabled" else "disabled"
            android.widget.Toast.makeText(this, "${widget.name} $action!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun addSystemWidgetProvider(widget: WidgetConfigurationManager.WidgetInfo) {
        val pkg = widget.providerPackage ?: return
        val cls = widget.providerClass ?: return
        widgetManager.bindProvider(this, pkg, cls, ActivityResultHandler.REQUEST_BIND_WIDGET)
    }
    
    private fun filterWidgets(query: String) {
        filteredWidgets = if (query.isEmpty()) {
            allWidgets.toMutableList()
        } else {
            allWidgets.filter { 
                it.name.contains(query, ignoreCase = true) ||
                getWidgetDescription(it).contains(query, ignoreCase = true)
            }.toMutableList()
        }
        
        adapter?.updateWidgets(filteredWidgets)
        adapter?.notifyDataSetChanged()
        
        findViewById<TextView>(R.id.widgets_header).visibility = 
            if (filteredWidgets.isNotEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun updateOriginalListOrder() {
        // Update allWidgets to match the current order of filteredWidgets
        // This preserves the order even when filtering is active
        val orderedIds = filteredWidgets.map { it.id }
        allWidgets.sortBy { widget ->
            val index = orderedIds.indexOf(widget.id)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }
    
    private fun getWidgetDescription(widget: WidgetConfigurationManager.WidgetInfo): String {
        if (widget.isSystemWidget) return "System provided widget"
        
        return when (widget.id) {
            "calculator_widget_container" -> "Perform calculations and unit conversions"
            "compass_widget_container" -> "Digital compass with direction tracking"
            "notifications_widget_container" -> "Quick access to recent notifications"
            "calendar_events_widget_container" -> "Upcoming calendar events and reminders"
            "countdown_widget_container" -> "Countdown timers for important events"
            "physical_activity_widget_container" -> "Track steps and physical activity"
            "pressure_widget_container" -> "Atmospheric pressure monitoring"
            "proximity_widget_container" -> "Proximity sensor readings"
            "temperature_widget_container" -> "Temperature monitoring and alerts"
            "noise_decibel_widget_container" -> "Sound level measurement in decibels"
            "workout_widget_container" -> "Workout tracking and fitness metrics"
            "todo_recycler_view" -> "Task manager"
            "finance_widget" -> "Financial tracking and budget monitoring"
            "weekly_usage_widget" -> "Weekly app usage statistics"
            "network_stats_widget_container" -> "Network connection and data usage"
            "device_info_widget_container" -> "Device information and system stats"
            else -> "System provided widget"
        }
    }
}
