package com.guruswarupa.launch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import android.content.SharedPreferences
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "com.guruswarupa.launch.PREFS"
    private val FIRSTTIMEKEY = "isFirstTime"

    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var searchBox: EditText
    private lateinit var appDock: LinearLayout
    private var fullAppList: MutableList<ResolveInfo> = mutableListOf()
    private val handler = Handler()

    private lateinit var appSearchManager: AppSearchManager
    private lateinit var appDockManager: AppDockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (isFirstTime(this, PREFS_NAME, FIRSTTIMEKEY)) {
            checkAndAskSetAsDefault(this, packageName)
        }

        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"

        searchBox = findViewById(R.id.search_box)
        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (isGridMode) {
            recyclerView.layoutManager = GridLayoutManager(this, 4)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }

        timeTextView = findViewById(R.id.time_widget)
        dateTextView = findViewById(R.id.date_widget)
        appDock = findViewById(R.id.app_dock)

        updateTime()
        updateDate()

        appDockManager = AppDockManager(this, sharedPreferences, appDock, packageManager) // Initialize AppDockManager

        timeTextView.setOnClickListener {
            launchApp("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            launchApp("com.google.android.calendar", "Google Calendar")
        }

        loadApps(isGridMode) // Pass the view mode to loadApps

        adapter = AppAdapter(this, appList, searchBox, isGridMode)

        recyclerView.adapter = adapter

        appSearchManager = AppSearchManager(
            packageManager = packageManager,
            appList = appList,
            fullAppList = fullAppList,
            adapter = adapter,
            recyclerView = recyclerView,
            searchBox = searchBox
        )

        appDockManager.loadDockApps() // Load dock apps
    }

    private fun launchApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "$appName app is not installed.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening $appName app.", Toast.LENGTH_SHORT).show()
        }
    }

    private val packageRemoveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED) {
                // Check if the package is not being replaced (i.e., it's a true uninstall)
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    loadApps()
                    adapter.appList = appList
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
            addDataScheme("package")
        }
        registerReceiver(packageRemoveReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(packageRemoveReceiver)
    }

    private fun updateTime() {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val currentTime = sdf.format(Date())
        timeTextView.text = currentTime

        handler.postDelayed({ updateTime() }, 1000)
    }

    private fun updateDate() {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val currentTime = sdf.format(Date())
        dateTextView.text = currentTime
        handler.postDelayed({ updateTime() }, 1000)
    }

    fun loadApps(isGridMode: Boolean = false) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        appList = packageManager.queryIntentActivities(intent, 0).toMutableList()
        fullAppList = appList.toMutableList()

        if (appList.isEmpty()) {
            Toast.makeText(this, "No apps found!", Toast.LENGTH_SHORT).show()
        } else {
            appList = appList.filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                .toMutableList()

            // Set the layout manager based on the view mode
            recyclerView.layoutManager = if (isGridMode) {
                GridLayoutManager(this, 4)
            } else {
                LinearLayoutManager(this)
            }

            adapter = AppAdapter(this, appList, searchBox, isGridMode)
            recyclerView.adapter = adapter
        }
    }
}
