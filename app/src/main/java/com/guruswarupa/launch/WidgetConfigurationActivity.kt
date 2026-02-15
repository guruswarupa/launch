package com.guruswarupa.launch

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class WidgetConfigurationActivity : AppCompatActivity() {

    private lateinit var widgetConfigManager: WidgetConfigurationManager
    private var adapter: WidgetConfigAdapter? = null
    private val prefsName = "com.guruswarupa.launch.PREFS"
    private lateinit var wallpaperManagerHelper: WallpaperManagerHelper
    private val backgroundExecutor = Executors.newFixedThreadPool(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val systemBarManager = SystemBarManager(this)
        window.decorView.post {
            systemBarManager.makeSystemBarsTransparent()
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
        widgetConfigManager = WidgetConfigurationManager(sharedPreferences)

        val wallpaperBackground = findViewById<ImageView>(R.id.wallpaper_background)
        wallpaperManagerHelper = WallpaperManagerHelper(this, wallpaperBackground, null, backgroundExecutor)
        wallpaperManagerHelper.setWallpaperBackground()

        val recyclerView = findViewById<RecyclerView>(R.id.widgets_recycler_view)
        val backButton = findViewById<ImageButton>(R.id.back_button)
        val cancelButton = findViewById<Button>(R.id.cancel_button)
        val saveButton = findViewById<Button>(R.id.save_button)
        
        // Ensure buttons are dark tinted to match theme
        val tintColor = "#33000000".toColorInt()
        cancelButton.backgroundTintList = ColorStateList.valueOf(tintColor)
        saveButton.backgroundTintList = ColorStateList.valueOf(tintColor)

        // Load current widget configuration
        val widgets = widgetConfigManager.getWidgetOrder().toMutableList()

        // Setup RecyclerView
        adapter = WidgetConfigAdapter(widgets) { position, isChecked ->
            widgets[position] = widgets[position].copy(enabled = isChecked)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

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
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }
                adapter?.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Setup button listeners
        backButton.setOnClickListener {
            finish()
        }

        cancelButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            val updatedWidgets = adapter?.getWidgets() ?: widgets
            widgetConfigManager.saveWidgetOrder(updatedWidgets)
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::wallpaperManagerHelper.isInitialized) {
            wallpaperManagerHelper.cleanup()
        }
        backgroundExecutor.shutdown()
    }
    
    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
