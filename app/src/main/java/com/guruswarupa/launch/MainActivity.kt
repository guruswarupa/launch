package com.guruswarupa.launch

import android.Manifest
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var searchBox: EditText
    private lateinit var dock: LinearLayout
    private val handler = Handler()
    private lateinit var wallpaperView: ImageView
    private lateinit var appSearchManager: AppSearchManager
    private lateinit var dockManager: AppDockManager
    private var contacts: List<String> = emptyList()
    private var lastSearchTap = 0L
    private val DOUBLE_TAP = 300
    private val searchJob = Job()
    private val searchScope = CoroutineScope(Dispatchers.Main + searchJob)
    private lateinit var allApps: MutableList<ResolveInfo>

    companion object {
        private const val CONTACTS_REQ = 100
        private const val WALLPAPER_REQ = 456
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
        if (prefs.getBoolean("isFirstRun", true)) {
            prefs.edit().putBoolean("isFirstRun", false).apply()
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        } else setContentView(R.layout.activity_main)

        searchBox = findViewById(R.id.search_box)
        recyclerView = findViewById(R.id.app_list)
        timeView = findViewById(R.id.time_widget)
        dateView = findViewById(R.id.date_widget)
        dock = findViewById(R.id.app_dock)
        wallpaperView = findViewById(R.id.wallpaper_background)

        requestPermissions(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS), 1)
        contacts = if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) loadContacts() else emptyList()

        recyclerView.layoutManager = if (prefs.getString("view_preference", "list") == "grid") GridLayoutManager(this, 4) else LinearLayoutManager(this)

        updateTime()
        updateDate()

        dockManager = AppDockManager(this, prefs, dock, packageManager)
        timeView.setOnClickListener { launchApp("com.google.android.deskclock", "Google Clock") }
        dateView.setOnClickListener { launchApp("com.google.android.calendar", "Google Calendar") }

        loadApps()
        adapter = AppAdapter(this, appList, searchBox, prefs.getString("view_preference", "list") == "grid")
        recyclerView.adapter = adapter

        appSearchManager = AppSearchManager(packageManager, appList, allApps, adapter, recyclerView, searchBox, contacts) //Pass allApps
        dockManager.loadDockApps()
        setWallpaper()

        searchBox.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastSearchTap < DOUBLE_TAP) chooseWallpaper()
            lastSearchTap = now
        }
    }

    private fun setWallpaper() {
        val newWallpaper = WallpaperManager.getInstance(this).drawable
        val newBitmap = (newWallpaper as? BitmapDrawable)?.bitmap ?: run {
            wallpaperView.setImageResource(R.drawable.default_wallpaper)
            null
        }

        val oldBitmap = (wallpaperView.drawable as? BitmapDrawable)?.bitmap
        if (oldBitmap != null && oldBitmap != newBitmap) {
            oldBitmap.recycle()
        }
        wallpaperView.setImageBitmap(newBitmap)
    }

    override fun onDestroy() { super.onDestroy(); (wallpaperView.drawable as? BitmapDrawable)?.bitmap?.recycle() }

    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) setWallpaper() }
    }

    private fun chooseWallpaper() { startActivityForResult(Intent(Intent.ACTION_SET_WALLPAPER), WALLPAPER_REQ) }

    private fun launchApp(pkg: String, name: String) = try { startActivity(packageManager.getLaunchIntentForPackage(pkg) ?: throw Exception()) } catch (e: Exception) { Toast.makeText(this, "$name app not found.", Toast.LENGTH_SHORT).show() }

    private val pkgRemoveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                loadApps()
                appSearchManager.removeInvalidApps() // Remove invalid apps on uninstall
                appSearchManager.updateAppLabels() //Update the app labels.
                adapter.apps = appList
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onResume() { super.onResume(); registerReceiver(pkgRemoveReceiver, IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }); registerReceiver(wallpaperReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED)) }
    override fun onPause() { super.onPause(); unregisterReceiver(pkgRemoveReceiver); unregisterReceiver(wallpaperReceiver) }

    private fun updateTime() { timeView.text = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date()); handler.postDelayed({ updateTime() }, 1000) }
    private fun updateDate() { dateView.text = SimpleDateFormat("dd MMMтиву", Locale.getDefault()).format(Date()); handler.postDelayed({ updateDate() }, 1000) }

    fun loadApps() {
        appList = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            0
        ).filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }.toMutableList()

        // Update allApps as well
        allApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            0
        ).filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }.toMutableList()

        recyclerView.layoutManager =
            if (prefs.getString("view_preference", "list") == "grid") GridLayoutManager(this, 4) else LinearLayoutManager(this)
        adapter = AppAdapter(this, appList, searchBox, prefs.getString("view_preference", "list") == "grid")
        recyclerView.adapter = adapter
    }

    private fun loadContacts(): List<String> {
        val contacts = mutableListOf<String>()
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")?.use { cursor ->
            while (cursor.moveToNext()) contacts.add(cursor.getString(0))
        }
        return contacts
    }
}