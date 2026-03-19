package com.guruswarupa.launch.ui.activities

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.AppTimerManager
import com.guruswarupa.launch.managers.DailyUsageManager
import com.guruswarupa.launch.utils.DialogStyler
import com.guruswarupa.launch.utils.WallpaperDisplayHelper
import com.guruswarupa.launch.utils.setDialogInputView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit






class AppTimerManagementActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appTimerManager: AppTimerManager
    private lateinit var dailyUsageManager: DailyUsageManager
    private lateinit var loadingProgressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_timer_management)
        applyContentInsets()

        appTimerManager = AppTimerManager(this)
        dailyUsageManager = DailyUsageManager(this)

        setupViews()
        loadAppsList()
    }

    private fun setupViews() {
        WallpaperDisplayHelper.applySystemWallpaper(findViewById(R.id.wallpaper_background))
        findViewById<TextView>(R.id.toolbar_title).text = "Manage App Timers"
        findViewById<View>(R.id.back_button).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.apps_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadingProgressBar = findViewById(R.id.loading_progress) ?: ProgressBar(this).apply {
            visibility = View.GONE
        }
    }

    private fun loadAppsList() {
        loadingProgressBar.visibility = View.VISIBLE
        recyclerView.alpha = 0f

        lifecycleScope.launch {
            
            val apps = withContext(Dispatchers.IO) {
                
                val usageMap = dailyUsageManager.getTodayUsageMap()
                
                val pm = packageManager
                val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                }

                installedApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) && it.packageName != packageName }
                    .map { appInfo ->
                        val pkgName = appInfo.packageName
                        AppTimerItem(
                            name = appInfo.loadLabel(pm).toString(),
                            packageName = pkgName,
                            icon = appInfo.loadIcon(pm),
                            limitMs = appTimerManager.getDailyLimit(pkgName),
                            usageTimeMs = usageMap[pkgName] ?: 0L,
                            enabled = dailyUsageManager.isTimerEnabled(pkgName)
                        )
                    }.sortedBy { it.name.lowercase() }
            }
            
            
            loadingProgressBar.visibility = View.GONE
            recyclerView.alpha = 1.0f
            recyclerView.adapter = AppTimerAdapter(apps.toMutableList())
        }
    }

    private fun applyContentInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.apps_recycler_view).setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun showEditLimitDialog(item: AppTimerItem, position: Int) {
        val currentLimit = appTimerManager.getDailyLimit(item.packageName)
        val currentLimitMinutes = if (currentLimit > 0) currentLimit / 60000 else 0

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Enter minutes (0 to disable)"
            setText(currentLimitMinutes.toString())
            DialogStyler.styleInput(this@AppTimerManagementActivity, this)
        }

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Set Daily Limit for ${item.name}")
            .setMessage("Enter daily usage limit in minutes:")
            .setDialogInputView(this, input)
            .setPositiveButton("Set") { _, _ ->
                try {
                    val minutes = input.text.toString().toLongOrNull() ?: 0L
                    val limitMs = minutes * 60000L

                    appTimerManager.setDailyLimit(item.packageName, limitMs)
                    dailyUsageManager.setTimerEnabled(item.packageName, limitMs > 0)

                    val updatedItem = item.copy(limitMs = limitMs, enabled = limitMs > 0)
                    (recyclerView.adapter as? AppTimerAdapter)?.updateItem(updatedItem, position)

                    Toast.makeText(
                        this,
                        if (limitMs > 0) "Daily limit set to ${formatTime(limitMs)}" else "Daily limit disabled",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: NumberFormatException) {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatTime(ms: Long): String {
        if (ms == 0L) return "0m"
        val hours = TimeUnit.MILLISECONDS.toHours(ms)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    private data class AppTimerItem(
        val name: String,
        val packageName: String,
        val icon: Drawable?,
        val limitMs: Long,
        val usageTimeMs: Long,
        val enabled: Boolean
    )

    private inner class AppTimerAdapter(
        private val items: MutableList<AppTimerItem>
    ) : RecyclerView.Adapter<AppTimerAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.app_icon)
            val name: TextView = v.findViewById(R.id.app_name)
            val limit: TextView = v.findViewById(R.id.app_limit)
            val usage: TextView = v.findViewById(R.id.app_usage)
            val sw: SwitchCompat = v.findViewById(R.id.timer_switch)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.item_app_timer, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = items[p]
            h.icon.setImageDrawable(item.icon)
            h.name.text = item.name
            h.limit.text = if (item.limitMs > 0) "Limit: ${formatTime(item.limitMs)}" else "No limit"
            h.usage.text = "Used: ${formatTime(item.usageTimeMs)}"

            h.sw.setOnCheckedChangeListener(null)
            h.sw.isChecked = item.enabled
            h.sw.setOnCheckedChangeListener { _, isChecked ->
                dailyUsageManager.setTimerEnabled(item.packageName, isChecked)
                items[p] = items[p].copy(enabled = isChecked)
                notifyItemChanged(p)
            }

            h.itemView.setOnClickListener { showEditLimitDialog(items[p], p) }
        }

        fun updateItem(item: AppTimerItem, position: Int) {
            items[position] = item
            notifyItemChanged(position)
        }
    }
}
