package com.guruswarupa.launch.ui.activities

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.services.ScreenLockAccessibilityService
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import java.util.*

class ControlCenterConfigActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ShortcutConfigAdapter
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control_center_config)
        applyContentInsets()
        window.decorView.post { makeSystemBarsTransparent() }

        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))
        recyclerView = findViewById(R.id.rv_shortcuts)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val saved = prefs.getString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, ScreenLockAccessibilityService.DEFAULT_SHORTCUTS)
            ?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val allShortcuts = ScreenLockAccessibilityService.DEFAULT_SHORTCUTS.split(",")
        
        val items = mutableListOf<ShortcutItem>()
        for (id in saved) if (allShortcuts.contains(id)) items.add(ShortcutItem(id, getLabel(id), getIcon(id), true))
        for (id in allShortcuts) if (!saved.contains(id)) items.add(ShortcutItem(id, getLabel(id), getIcon(id), false))

        adapter = ShortcutConfigAdapter(items)
        recyclerView.adapter = adapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
                val from = v.adapterPosition; val to = t.adapterPosition
                Collections.swap(items, from, to); adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(v: RecyclerView.ViewHolder, d: Int) {}
        }).attachToRecyclerView(recyclerView)

        findViewById<View>(R.id.btn_save).setOnClickListener {
            val res = items.filter { it.isEnabled }.joinToString(",") { it.id }
            prefs.edit().putString(Constants.Prefs.CONTROL_CENTER_SHORTCUTS, res).apply()
            finish()
        }
    }

    private fun getLabel(id: String): String = when(id) {
        "wifi" -> "Wi-Fi Panel"; "bluetooth" -> "Bluetooth"; "airplane" -> "Airplane Mode"
        "torch" -> "Flashlight"; "data" -> "Cellular Data"; "rotation" -> "Rotation Lock"
        "sound" -> "Audio Profile"; "dnd" -> "Focus Mode"; "location" -> "GPS Location"
        "qr_scan" -> "Scan QR"; "camera" -> "Capture"; "screenshot" -> "Screen Snap"
        "record" -> "Video Capture"; "lock" -> "Security Lock"; "power" -> "System Power"
        "hotspot" -> "Tethering"; "screen_timeout" -> "Display Sleep"
        else -> id.replaceFirstChar { it.uppercase() }
    }

    private fun getIcon(id: String): Int = when(id) {
        "wifi" -> R.drawable.ic_wifi_stat; "bluetooth" -> android.R.drawable.stat_sys_data_bluetooth; "airplane" -> R.drawable.ic_airplane_stat
        "torch" -> R.drawable.ic_torch_stat; "data" -> R.drawable.ic_mobile_data_stat; "rotation" -> R.drawable.ic_rotation_stat
        "sound" -> R.drawable.ic_volume_up_stat; "dnd" -> R.drawable.ic_focus_mode_icon; "location" -> android.R.drawable.ic_menu_mylocation
        "qr_scan" -> R.drawable.ic_qr_scan_stat; "camera" -> android.R.drawable.ic_menu_camera; "screenshot" -> R.drawable.ic_screenshot_stat
        "record" -> android.R.drawable.ic_menu_slideshow; "lock" -> android.R.drawable.ic_lock_idle_lock; "power" -> android.R.drawable.ic_lock_power_off
        "hotspot" -> R.drawable.ic_wifi_stat; "screen_timeout" -> R.drawable.ic_settings_icon
        else -> android.R.drawable.ic_menu_help
    }

    private fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(android.R.id.content).setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun makeSystemBarsTransparent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
    }

    data class ShortcutItem(val id: String, val label: String, val icon: Int, var isEnabled: Boolean)

    inner class ShortcutConfigAdapter(val items: List<ShortcutItem>) : RecyclerView.Adapter<ShortcutConfigAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_config_shortcut, p, false))
        override fun onBindViewHolder(h: ViewHolder, pos: Int) {
            val item = items[pos]
            h.label.text = item.label
            h.icon.setImageResource(item.icon)
            h.icon.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            h.cb.isChecked = item.isEnabled
            h.cb.setOnCheckedChangeListener { _, checked -> item.isEnabled = checked }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.iv_shortcut_icon)
            val label: TextView = v.findViewById(R.id.tv_shortcut_label)
            val cb: CheckBox = v.findViewById(R.id.cb_shortcut_visible)
        }
    }
}
