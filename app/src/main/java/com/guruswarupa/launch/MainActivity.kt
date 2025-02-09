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

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "com.guruswarupa.launch.PREFS"
    private val FIRSTTIMEKEY = "isFirstTime"

    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (isFirstTime()) {
            checkAndAskSetAsDefault()
        }

        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        timeTextView = findViewById(R.id.time_widget)
        dateTextView = findViewById(R.id.date_widget)
        updateTime()
        updateDate()

        loadApps()
        adapter = AppAdapter(this, appList)
        recyclerView.adapter = adapter
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

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        appList = packageManager.queryIntentActivities(intent, 0).toMutableList()

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

        holder.appIcon.setImageDrawable(appInfo.loadIcon(activity.packageManager))
        holder.appName.text = appInfo.loadLabel(activity.packageManager)

        // Launch app on regular click
        holder.itemView.setOnClickListener {
            val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "Cannot launch app", Toast.LENGTH_SHORT).show()
            }
        }

        // Show uninstall prompt on long click
        holder.itemView.setOnLongClickListener {
            showUninstallDialog(packageName)
            true
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
