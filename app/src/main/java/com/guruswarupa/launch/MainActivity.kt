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
import android.widget.LinearLayout

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "com.guruswarupa.launch.PREFS"
    private val FIRSTTIMEKEY = "isFirstTime"
    private val DOCK_APPS_KEY = "dock_apps"

    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var searchBox: EditText
    private lateinit var appDock: LinearLayout
    private lateinit var addIcon: ImageView
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
        appDock = findViewById(R.id.app_dock)

        updateTime()
        updateDate()

        addIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_add) // Make sure you have an add icon in your drawable
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(16, 0, 16, 0)
            setOnClickListener {
                    openAppPicker()
            }
        }


        appDock.addView(addIcon)

        timeTextView.setOnClickListener {
            launchApp("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            launchApp("com.google.android.calendar", "Google Calendar")
        }

        loadApps()

        adapter = AppAdapter(this, appList, searchBox)

        recyclerView.adapter = adapter

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchBox.setOnLongClickListener {
            // Create an Intent to open a new tab in the default browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Ensures the browser opens in a new task
            try {
                startActivity(intent)  // Launch the default browser
            } catch (e: Exception) {
                Toast.makeText(this, "No browser found!", Toast.LENGTH_SHORT).show()
            }
            true  // Consume the long press event
        }

        loadDockApps()
    }

    private fun openAppPicker() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
        val sortedApps = apps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        val appNames = sortedApps.map { it.loadLabel(packageManager).toString() }
        val appPackageNames = sortedApps.map { it.activityInfo.packageName }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose an app to add to the dock")
        builder.setItems(appNames.toTypedArray()) { _, which ->
            val selectedPackage = appPackageNames[which]
            addAppToDock(selectedPackage)
        }

        builder.show()
    }

    private fun addAppToDock(packageName: String) {
        // Get the ApplicationInfo for the package
        val appInfo = packageManager.getApplicationInfo(packageName, 0)

        // Get the app's icon and name
        val appIcon = packageManager.getApplicationIcon(appInfo)
        val appName = packageManager.getApplicationLabel(appInfo).toString()

        // Add to dock (LinearLayout for the dock)
        val appIconView = ImageView(this).apply {
            setImageDrawable(appIcon)

            // Set LayoutParams with margin between icons
            val params = LinearLayout.LayoutParams(120, 120).apply {
                setMargins(16, 0, 16, 0) // Set horizontal margin (16dp on left and right)
            }
            layoutParams = params

            setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
            }

            // Long press to remove app from dock
            setOnLongClickListener {
                showRemoveDockAppDialog(packageName)
                true
            }
        }

        // Add the app icon to the left side (beginning) of the dock
        appDock.addView(appIconView, 0)  // Adding it at the start

        // Save this app's package name to SharedPreferences
        saveAppToDock(packageName)

        // Add the "+" icon to the right (end)
        if (appDock.childCount == 0) {
            addIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_add) // Make sure you have an add icon in your drawable
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    setMargins(16, 0, 16, 0) // Add margins as needed
                }
                setOnClickListener {
                    openAppPicker()
                }
            }
            appDock.addView(addIcon)  // Add "+" icon to the end
        }
    }

    private fun showRemoveDockAppDialog(packageName: String) {
        val dialog = AlertDialog.Builder(this)
            .setMessage("Do you want to remove this app from the dock?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                // Remove the app's package name from SharedPreferences
                removeAppFromDock(packageName)

                // Refresh the dock to update the UI
                refreshDock()
            }
            .setNegativeButton("No", null)
            .create()

        dialog.show()
    }

    private fun removeAppFromDock(packageName: String) {
        val dockApps = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf()) ?: mutableSetOf()
        if (dockApps.contains(packageName)) {
            dockApps.remove(packageName) // Remove app from set
            sharedPreferences.edit().putStringSet(DOCK_APPS_KEY, dockApps).apply() // Update SharedPreferences
        }
    }

    private fun refreshDock() {
        appDock.removeAllViews()
        loadDockApps()
        ensureAddIcon()
    }

    private fun ensureAddIcon() {
        if (appDock.childCount == 0 || (appDock.getChildAt(appDock.childCount - 1) as? ImageView)?.drawable != resources.getDrawable(R.drawable.ic_add)) {
            addIcon = ImageView(this).apply {
                setImageResource(R.drawable.ic_add) // Ensure you have an add icon in your drawable
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    setMargins(16, 0, 16, 0) // Add margins as needed
                }
                setOnClickListener {
                    openAppPicker()
                }
            }
            appDock.addView(addIcon)
        }
    }

    private fun saveAppToDock(packageName: String) {
        val dockApps = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf()) ?: mutableSetOf()

        // Add the new app's package name
        dockApps.add(packageName)

        // Save the updated set of package names back to SharedPreferences
        sharedPreferences.edit().putStringSet(DOCK_APPS_KEY, dockApps).apply()
    }

    private fun loadDockApps() {
        // Load the saved dock apps from SharedPreferences
        val dockApps = sharedPreferences.getStringSet(DOCK_APPS_KEY, mutableSetOf()) ?: mutableSetOf()

        // Add the saved apps to the dock
        for (packageName in dockApps) {
            addAppToDock(packageName)
        }
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

    // Inside MainActivity
    private fun filterApps(query: String) {
        if (query.isNotEmpty()) {
            // Filter the apps based on the query
            val filteredList = fullAppList.filter {
                it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
            }.toMutableList()

            appList.clear()
            appList.addAll(filteredList)

            if (filteredList.isEmpty()) {
                // Show two options if no results found
                appList.add(createPlayStoreSearchOption(query))
                appList.add(createBrowserSearchOption(query))
            }
        } else {
            // When search is cleared, reset and sort alphabetically
            appList.clear()
            appList.addAll(fullAppList.sortedBy { it.loadLabel(packageManager).toString().lowercase() })
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

    private fun createBrowserSearchOption(query: String): ResolveInfo {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "browser_search"
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
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                .toMutableList()

            adapter = AppAdapter(this, appList, searchBox)
            recyclerView.adapter = adapter
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

class AppAdapter(private val activity: MainActivity, var appList: MutableList<ResolveInfo>,private val searchBox: EditText) :
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
            // Set the Play Store search option
            holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.android.vending"))
            val query = appInfo.activityInfo.name
            holder.appName.text = "Search $query on Play Store"  // Set dynamic text

            holder.itemView.setOnClickListener {
                val encodedQuery = Uri.encode(query)  // Encode the app name for URL
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encodedQuery"))
                activity.startActivity(intent)
                searchBox.text.clear()  // Clear the search box after the search is triggered
            }
        } else if (packageName == "browser_search") {
            holder.appIcon.setImageResource(R.drawable.ic_browser)
            val query = appInfo.activityInfo.name
            holder.appName.text = "Search $query in Browser"

            holder.itemView.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                activity.startActivity(intent)
                searchBox.text.clear()  // Clear the search box after the search is triggered
            }
        } else {
            // For installed apps, load their icon and name
            holder.appIcon.setImageDrawable(appInfo.loadIcon(activity.packageManager))
            holder.appName.text = appInfo.loadLabel(activity.packageManager)

            holder.itemView.setOnClickListener {
                val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    activity.startActivity(intent)
                    searchBox.text.clear()
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
