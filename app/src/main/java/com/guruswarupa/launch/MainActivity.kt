package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import android.provider.Settings
import android.content.SharedPreferences
import android.net.Uri
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher

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
    private var fullAppList: MutableList<ResolveInfo> = mutableListOf()
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (isFirstTime()) {
            checkAndAskSetAsDefault()
        }

        searchBox = findViewById(R.id.search_box)
        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        timeTextView = findViewById(R.id.time_widget)
        dateTextView = findViewById(R.id.date_widget)
        updateTime()
        updateDate()

        timeTextView.setOnClickListener {
            launchApp("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            launchApp("com.google.android.calendar", "Google Calendar")
        }

        loadApps()
        adapter = AppAdapter(this, appList)
        recyclerView.adapter = adapter

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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

    private fun filterApps(query: String) {
        val filteredList = fullAppList.filter {
            it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
        }.toMutableList()

        appList.clear()
        appList.addAll(filteredList)

        // If no installed apps match, add a "Search on Play Store" option
        if (filteredList.isEmpty() && query.isNotBlank()) {
            appList.add(createPlayStoreSearchOption(query))
        }

        adapter.notifyDataSetChanged()
    }

    private fun createPlayStoreSearchOption(query: String): ResolveInfo {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "play_store_search"
                name = query
            }
        }
        return resolveInfo
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        appList = packageManager.queryIntentActivities(intent, 0).toMutableList()
        fullAppList = appList.toMutableList()

        if (appList.isEmpty()) {
            Toast.makeText(this, "No apps found!", Toast.LENGTH_SHORT).show()
        } else {
            appList = appList.filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                .sortedBy { it.loadLabel(packageManager).toString() }.toMutableList()
        }
    }

    private fun isFirstTime(): Boolean {
        val isFirstTime = sharedPreferences.getBoolean(FIRSTTIMEKEY, true)
        if (isFirstTime) {
            sharedPreferences.edit().putBoolean(FIRSTTIMEKEY, false).apply()
        }
        return isFirstTime
    }

    private fun checkAndAskSetAsDefault() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.queryIntentActivities(homeIntent, 0)

        if (resolveInfo.isNotEmpty()) {
            val currentHomePackage = resolveInfo[0].activityInfo.packageName
            if (currentHomePackage != packageName) {
                // Show dialog asking to set this app as default launcher
                showSetAsDefaultDialog()
            }
        }
    }

    private fun showSetAsDefaultDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage("Do you want to set this app as the default home launcher?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                // Open the default apps settings page
                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
        val alert = dialogBuilder.create()
        alert.show()
    }
}

class AppAdapter(private val activity: MainActivity, var appList: MutableList<ResolveInfo>) :
    RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName

        if (packageName == "play_store_search") {
            holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.android.vending"))
            holder.appName.text = appInfo.activityInfo.name

            holder.itemView.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(appInfo.activityInfo.name)}"))
                activity.startActivity(intent)
            }
        } else {
            holder.appIcon.setImageDrawable(appInfo.loadIcon(activity.packageManager))
            holder.appName.text = appInfo.loadLabel(activity.packageManager)

            holder.itemView.setOnClickListener {
                val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    activity.startActivity(intent)
                } else {
                    Toast.makeText(activity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
            }

            holder.itemView.setOnLongClickListener {
                showUninstallDialog(packageName)
                true
            }
        }
    }

    override fun getItemCount(): Int = appList.size

    private fun showUninstallDialog(packageName: String) {
        uninstallApp(packageName)
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }
}
