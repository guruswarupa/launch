package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextPaint
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.guruswarupa.launch.core.SystemBarManager
import com.guruswarupa.launch.managers.WallpaperManagerHelper
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.managers.WidgetManager
import com.guruswarupa.launch.ui.adapters.WidgetConfigAdapter
import com.guruswarupa.launch.utils.WidgetPreviewManager
import java.util.concurrent.Executors
import com.guruswarupa.launch.R
import com.guruswarupa.launch.handlers.ActivityResultHandler
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.models.Constants

class WidgetConfigurationActivity : AppCompatActivity() {

    private lateinit var widgetConfigManager: WidgetConfigurationManager
    private lateinit var previewManager: WidgetPreviewManager
    private lateinit var widgetManager: WidgetManager
    private var adapter: WidgetConfigAdapter? = null
    private val prefsName = "com.guruswarupa.launch.PREFS"
    private lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    private val backgroundExecutor = Executors.newFixedThreadPool(2)
    private lateinit var widgetsRecyclerView: RecyclerView
    private lateinit var widgetSectionDecoration: WidgetSectionDecoration
    
    
    private var allWidgets = mutableListOf<WidgetConfigurationManager.WidgetInfo>()
    private var filteredWidgets = mutableListOf<WidgetConfigurationManager.WidgetInfo>()
    
    private val prefs by lazy { getSharedPreferences(prefsName, MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val systemBarManager = SystemBarManager(this)
        window.decorView.post {
            systemBarManager.makeSystemBarsTransparent()
            
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
        applyBackgroundTranslucency()

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
        
        
        widgetManager = WidgetManager(this, android.widget.LinearLayout(this))

        val wallpaperBackground = findViewById<ImageView>(R.id.wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(this, wallpaperBackground, null, backgroundExecutor)
        wallpaperManagerHelper.setWallpaperBackground()

        widgetsRecyclerView = findViewById(R.id.widgets_recycler_view)
        widgetSectionDecoration = WidgetSectionDecoration(this)
        widgetsRecyclerView.addItemDecoration(widgetSectionDecoration)
        
        val saveButton = findViewById<View>(R.id.save_button)
        val searchInput = findViewById<EditText>(R.id.search_widget_input)

        val configuredFontColor = TypographyManager.getConfiguredFontColor(this)
        if (configuredFontColor != null) {
            searchInput?.setTextColor(configuredFontColor)
            searchInput?.setHintTextColor(configuredFontColor)
        }
        TypographyManager.applyToView(searchInput)

        
        loadWidgets()

        widgetsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        
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
                
                
                val movedItem = filteredWidgets.removeAt(fromPos)
                filteredWidgets.add(toPos, movedItem)
                
                
                updateOriginalListOrder()
                
                adapter?.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                
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
        itemTouchHelper.attachToRecyclerView(widgetsRecyclerView)

        saveButton?.setOnClickListener {
            widgetConfigManager.saveWidgetOrder(allWidgets)
            setResult(RESULT_OK)
            finish()
        }
        
        
        searchInput?.addTextChangedListener(object : TextWatcher {
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
            widgetsRecyclerView.adapter = adapter
        } else {
            adapter?.updateWidgets(filteredWidgets)
            adapter?.notifyDataSetChanged()
        }

        refreshSectionHeaders()
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
    
    private fun applyBackgroundTranslucency() {
        val translucency = prefs.getInt(Constants.Prefs.BACKGROUND_TRANSLUCENCY, 40)
        val alpha = (translucency * 255 / 100).coerceIn(0, 255)
        val color = Color.argb(alpha, 0, 0, 0)
        findViewById<View>(R.id.settings_overlay)?.setBackgroundColor(color)
    }
    
    fun updateWidgetState(widgetId: String, enabled: Boolean) {
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
            Toast.makeText(this, "${widget.name} $action!", Toast.LENGTH_SHORT).show()
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

        refreshSectionHeaders()
    }
    
    private fun updateOriginalListOrder() {
        val orderedIds = filteredWidgets.map { it.id }
        allWidgets.sortBy { widget ->
            val index = orderedIds.indexOf(widget.id)
            if (index >= 0) index else Int.MAX_VALUE
        }
    }

    private fun refreshSectionHeaders() {
        val sections = mutableListOf<WidgetSectionDecoration.SectionInfo>()
        val seenCategories = mutableSetOf<WidgetSectionCategory>()

        filteredWidgets.forEachIndexed { index, widget ->
            val category = when {
                widget.enabled -> WidgetSectionCategory.ENABLED
                widget.isSystemWidget -> WidgetSectionCategory.SYSTEM_DISABLED
                else -> WidgetSectionCategory.CUSTOM_DISABLED
            }

            if (seenCategories.add(category)) {
                sections.add(WidgetSectionDecoration.SectionInfo(index, getString(category.titleRes)))
            }
        }

        widgetSectionDecoration.updateSections(sections)
        widgetsRecyclerView.invalidateItemDecorations()
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

    private enum class WidgetSectionCategory(@StringRes val titleRes: Int) {
        ENABLED(R.string.widget_section_enabled),
        CUSTOM_DISABLED(R.string.widget_section_custom_disabled),
        SYSTEM_DISABLED(R.string.widget_section_system_disabled)
    }

    private class WidgetSectionDecoration(private val context: Context) : RecyclerView.ItemDecoration() {
        data class SectionInfo(val position: Int, val title: String)

        private val headerHeightPx = (context.resources.displayMetrics.density * 36f).toInt().coerceAtLeast(1)
        private val horizontalMarginPx = (context.resources.displayMetrics.density * 16f).toInt()
        private val backgroundPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.widget_config_surface)
        }
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.widget_config_text_secondary)
            textSize = 14f * context.resources.displayMetrics.scaledDensity
            typeface = Typeface.DEFAULT_BOLD
        }

        private val sections = mutableListOf<SectionInfo>()
        private val headerPositions = mutableSetOf<Int>()

        fun updateSections(newSections: List<SectionInfo>) {
            sections.clear()
            sections.addAll(newSections.sortedBy { it.position })
            headerPositions.clear()
            headerPositions.addAll(sections.map { it.position })
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            if (headerPositions.contains(position)) {
                outRect.top = headerHeightPx
            }
        }

        override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            sections.forEach { section ->
                val view = parent.layoutManager?.findViewByPosition(section.position) ?: return@forEach
                val top = view.top - headerHeightPx
                val left = parent.paddingLeft.toFloat()
                val right = (parent.width - parent.paddingRight).toFloat().coerceAtLeast(left)
                val bottom = top + headerHeightPx

                canvas.drawRect(left, top.toFloat(), right, bottom.toFloat(), backgroundPaint)
                val textBaseline = top + headerHeightPx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(
                    section.title,
                    left + horizontalMarginPx,
                    textBaseline,
                    textPaint
                )
            }
        }
    }
}
