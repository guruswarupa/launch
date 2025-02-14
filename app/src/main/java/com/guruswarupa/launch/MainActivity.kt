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
import android.graphics.Bitmap
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


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

    private lateinit var wallpaperBackground: ImageView
    private var currentWallpaperBitmap: Bitmap? = null

    private lateinit var appSearchManager: AppSearchManager
    private lateinit var appDockManager: AppDockManager
    private var contactsList: List<String> = emptyList()
    private var lastSearchTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300

    companion object {
        private const val CONTACTS_PERMISSION_REQUEST = 100
        private const val REQUEST_CODE_CALL_PHONE = 200
        private val SMS_PERMISSION_REQUEST = 300
        private const val WALLPAPER_REQUEST_CODE = 456
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (sharedPreferences.getBoolean(FIRSTTIMEKEY, true)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"

        searchBox = findViewById(R.id.search_box)
        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)

        searchBox.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSearchTapTime < DOUBLE_TAP_THRESHOLD) {
                // Double tap detected
                chooseWallpaper()
            }
            lastSearchTapTime = currentTime
        }

        wallpaperBackground = findViewById(R.id.wallpaper_background)

        requestCallPermission()
        requestContactsPermission()
        requestSmsPermission()

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

        appDockManager = AppDockManager(this, sharedPreferences, appDock, packageManager)

        timeTextView.setOnClickListener {
            launchApp("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            launchApp("com.google.android.calendar", "Google Calendar")
        }

        loadApps()

        adapter = AppAdapter(this, appList, searchBox, isGridMode)
        recyclerView.adapter = adapter

        appSearchManager = AppSearchManager(
            packageManager = packageManager,
            appList = appList,
            fullAppList = fullAppList,
            adapter = adapter,
            recyclerView = recyclerView,
            searchBox = searchBox,
            contactsList = contactsList
        )

        appDockManager.loadDockApps()
        setWallpaperBackground()

    }

    private fun setWallpaperBackground() {
        val wallpaperManager = WallpaperManager.getInstance(this)
        try {
            val bitmap = wallpaperManager.drawable.let {
                if (it is BitmapDrawable) it.bitmap else null
            }
            if (bitmap != null) {
                wallpaperBackground.setImageBitmap(bitmap)
            } else {
                wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentWallpaperBitmap?.recycle()
        currentWallpaperBitmap = null
    }

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                setWallpaperBackground()
            }
        }
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivityForResult(intent, WALLPAPER_REQUEST_CODE)
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
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(packageRemoveReceiver)
        unregisterReceiver(wallpaperChangeReceiver)
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

    fun loadApps() {
        val viewPreference =
            sharedPreferences.getString("view_preference", "list") // Read the latest preference
        val isGridMode = viewPreference == "grid"

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

            // Preserve the selected layout mode
            recyclerView.layoutManager = if (isGridMode) {
                GridLayoutManager(this, 4)
            } else {
                LinearLayoutManager(this)
            }

            adapter = AppAdapter(this, appList, searchBox, isGridMode) // pass isGridMode
            recyclerView.adapter = adapter
        }
    }

    private fun requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                CONTACTS_PERMISSION_REQUEST
            )
        } else {
            contactsList = loadContacts()
        }
    }

    private fun requestCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE), 1
            )
        }
    }

    // Request SMS permission
    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SMS_PERMISSION_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CONTACTS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, load contacts
                    contactsList = loadContacts()
                } else {
                    // Permission denied for contacts
                    Toast.makeText(this, "Permission denied to read contacts", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            REQUEST_CODE_CALL_PHONE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with the phone call
                } else {
                    // Permission denied for call
                    Toast.makeText(this, "Permission denied to make calls", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with SMS functionality
                } else {
                    // Permission denied for SMS
                    Toast.makeText(this, "Permission denied to send SMS", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadContacts(): List<String> {
        val contacts = mutableListOf<String>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                contacts.add(it.getString(0))
            }
        }
        return contacts
    }
}