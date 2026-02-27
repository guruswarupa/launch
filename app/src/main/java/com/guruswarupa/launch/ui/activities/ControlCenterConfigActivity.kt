package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import java.util.*

class ControlCenterConfigActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ShortcutConfigAdapter
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control_center_config)

        recyclerView = findViewById(R.id.rv_shortcuts)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val savedShortcuts = prefs.getString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, ScreenLockAccessibilityService.DEFAULT_SHORTCUTS)
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            
        val allShortcuts = ScreenLockAccessibilityService.DEFAULT_SHORTCUTS.split(",")
        
        // Merge saved and all to ensure new features appear
        val items = mutableListOf<ShortcutItem>()
        
        // Add existing ones in order
        for (id in savedShortcuts) {
            if (allShortcuts.contains(id)) {
                items.add(ShortcutItem(id, getLabel(id), getIcon(id), true))
            }
        }
        
        // Add remaining ones as unchecked
        for (id in allShortcuts) {
            if (!savedShortcuts.contains(id)) {
                items.add(ShortcutItem(id, getLabel(id), getIcon(id), false))
            }
        }

        adapter = ShortcutConfigAdapter(items)
        recyclerView.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = target.adapterPosition
                Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
        })
        touchHelper.attachToRecyclerView(recyclerView)

        findViewById<View>(R.id.btn_save).setOnClickListener {
            val newShortcuts = items.filter { it.isEnabled }.joinToString(",") { it.id }
            prefs.edit().putString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, newShortcuts).apply()
            finish()
        }
    }

    private fun getLabel(id: String): String = when(id) {
        "wifi" -> "WiFi"
        "bluetooth" -> "Bluetooth"
        "airplane" -> "Airplane"
        "torch" -> "Torch"
        "data" -> "Mobile Data"
        "rotation" -> "Auto-Rotation"
        "sound" -> "Sound Mode"
        "dnd" -> "Do Not Disturb"
        "location" -> "Location"
        "qr_scan" -> "Scan QR"
        "camera" -> "Camera"
        "screenshot" -> "Screenshot"
        "record" -> "Screen Record"
        "lock" -> "Lock Screen"
        "power" -> "Power Menu"
        "hotspot" -> "Hotspot"
        "screen_timeout" -> "Screen Timeout"
        else -> id.replaceFirstChar { it.uppercase() }
    }

    private fun getIcon(id: String): Int = when(id) {
        "wifi" -> R.drawable.ic_wifi_stat
        "bluetooth" -> android.R.drawable.stat_sys_data_bluetooth
        "airplane" -> R.drawable.ic_airplane_stat
        "torch" -> R.drawable.ic_torch_stat
        "data" -> R.drawable.ic_mobile_data_stat
        "rotation" -> R.drawable.ic_rotation_stat
        "sound" -> R.drawable.ic_volume_up_stat
        "dnd" -> R.drawable.ic_focus_mode_icon
        "location" -> android.R.drawable.ic_menu_mylocation
        "qr_scan" -> R.drawable.ic_qr_scan_stat
        "camera" -> android.R.drawable.ic_menu_camera
        "screenshot" -> R.drawable.ic_screenshot_stat
        "record" -> android.R.drawable.ic_menu_slideshow
        "lock" -> android.R.drawable.ic_lock_idle_lock
        "power" -> android.R.drawable.ic_lock_power_off
        "hotspot" -> R.drawable.ic_wifi_stat
        "screen_timeout" -> R.drawable.ic_settings_icon
        else -> android.R.drawable.ic_menu_help
    }

    data class ShortcutItem(val id: String, val label: String, val icon: Int, var isEnabled: Boolean)

    inner class ShortcutConfigAdapter(val items: List<ShortcutItem>) : RecyclerView.Adapter<ShortcutConfigAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_config_shortcut, parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.label.text = item.label
            holder.icon.setImageResource(item.icon)
            holder.checkbox.isChecked = item.isEnabled
            holder.checkbox.setOnCheckedChangeListener { _, isChecked -> item.isEnabled = isChecked }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.iv_shortcut_icon)
            val label: TextView = v.findViewById(R.id.tv_shortcut_label)
            val checkbox: CheckBox = v.findViewById(R.id.cb_shortcut_visible)
        }
    }
}
