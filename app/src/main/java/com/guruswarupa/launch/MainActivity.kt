package com.guruswarupa.launch

import android.app.AlertDialog
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.ImageButton
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
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.os.Build
import android.util.Log
import android.widget.Button
import androidx.annotation.RequiresApi
import java.util.Calendar
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import com.guruswarupa.launch.TodoItem
import com.guruswarupa.launch.TodoAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executors


class MainActivity : FragmentActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "com.guruswarupa.launch.PREFS"

    private lateinit var recyclerView: RecyclerView
    private lateinit var appList: MutableList<ResolveInfo>
    private lateinit var adapter: AppAdapter
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var searchBox: EditText
    private lateinit var appDock: LinearLayout
    private var fullAppList: MutableList<ResolveInfo> = mutableListOf()
    private var cachedUnsortedList: List<ResolveInfo>? = null // Cache the raw app list
    private var lastCacheTime = 0L
    private val CACHE_DURATION = 60000L // Cache for 1 minute
    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newFixedThreadPool(4) // Reusable thread pool

    private lateinit var wallpaperBackground: ImageView
    private var currentWallpaperBitmap: Bitmap? = null

    lateinit var appSearchManager: AppSearchManager
    internal lateinit var appDockManager: AppDockManager
    private lateinit var usageStatsManager: AppUsageStatsManager
    private var contactsList: MutableList<String> = mutableListOf()
    private var lastSearchTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300
    private lateinit var weeklyUsageGraph: WeeklyUsageGraphView // Add this line
    private var lastUpdateDate: String = ""
    private var lastWeatherUpdateTime = 0L
    private val WEATHER_UPDATE_INTERVAL = 30 * 60 * 1000L // 30 minutes in milliseconds
    // Cache SimpleDateFormat instances to avoid repeated creation
    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
    private lateinit var weatherManager: WeatherManager // Add this line
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherText: TextView
    private lateinit var todoRecyclerView: RecyclerView
    private lateinit var todoAdapter: TodoAdapter
    private lateinit var addTodoButton: ImageButton
    private var todoItems: MutableList<TodoItem> = mutableListOf()
    private lateinit var voiceSearchButton: ImageButton
    private var isInPowerSaverMode = false
    private lateinit var todoAlarmManager: TodoAlarmManager
    private lateinit var calculatorWidget: CalculatorWidget
    private lateinit var notificationsWidget: NotificationsWidget

    // Finance widget variables
    private lateinit var financeManager: FinanceManager
    private lateinit var balanceText: TextView
    private lateinit var monthlySpentText: TextView
    private lateinit var amountInput: EditText
    private lateinit var descriptionInput: EditText

    // APK sharing manager
    private lateinit var shareManager: ShareManager
    internal lateinit var appLockManager: AppLockManager
    lateinit var appTimerManager: AppTimerManager
    lateinit var appCategoryManager: AppCategoryManager
    lateinit var favoriteAppManager: FavoriteAppManager
    internal var isShowAllAppsMode = false
    private lateinit var widgetManager: WidgetManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var gestureDetector: GestureDetector

    companion object {
        private const val CONTACTS_PERMISSION_REQUEST = 100
        private const val REQUEST_CODE_CALL_PHONE = 200
        val SMS_PERMISSION_REQUEST = 300
        private const val WALLPAPER_REQUEST_CODE = 456
        private const val VOICE_SEARCH_REQUEST = 500
        private const val USAGE_STATS_REQUEST = 600
        private const val LOCATION_PERMISSION_REQUEST = 700
        private const val REQUEST_PICK_WIDGET = 800
        private const val REQUEST_CONFIGURE_WIDGET = 801
        private const val NOTIFICATION_PERMISSION_REQUEST = 900
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            sharedPreferences.edit().putBoolean("isFirstRun", false).apply()

            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        } else {
            setContentView(R.layout.activity_main)
        }

        // Initialize APK sharing manager
        shareManager = ShareManager(this)
        appLockManager = AppLockManager(this)
        appTimerManager = AppTimerManager(this)
        appCategoryManager = AppCategoryManager(packageManager)
        favoriteAppManager = FavoriteAppManager(sharedPreferences)
        isShowAllAppsMode = favoriteAppManager.isShowAllAppsMode()

        val filter = IntentFilter("com.guruswarupa.launch.SETTINGS_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsUpdateReceiver, filter)
        }

        val viewPreference = sharedPreferences.getString("view_preference", "list")
        val isGridMode = viewPreference == "grid"

        searchBox = findViewById(R.id.search_box)
        recyclerView = findViewById(R.id.app_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        voiceSearchButton = findViewById(R.id.voice_search_button)

        searchBox.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSearchTapTime < DOUBLE_TAP_THRESHOLD) {
                // Double tap detected
                chooseWallpaper()
            }
            lastSearchTapTime = currentTime
        }

        searchBox.setOnLongClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                searchBox.context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(searchBox.context, "No browser found!", Toast.LENGTH_SHORT).show()
            }
            true
        }

        appDock = findViewById(R.id.app_dock)
        wallpaperBackground = findViewById(R.id.wallpaper_background)
        weeklyUsageGraph = findViewById(R.id.weekly_usage_graph)
        weatherIcon = findViewById(R.id.weather_icon) // Initialize weatherIcon
        weatherText = findViewById(R.id.weather_text) // Initialize weatherText

        usageStatsManager = AppUsageStatsManager(this)
        weatherManager = WeatherManager(this)
        // Set callback for location permission requests
        weatherManager.onLocationPermissionNeeded = {
            requestLocationPermissionForWeather()
        }

        // Request necessary permissions
        requestContactsPermission()
        requestSmsPermission()
        requestUsageStatsPermission()

        // Setup weekly usage graph callback
        weeklyUsageGraph.onDaySelected = { day, appUsages ->
            showDailyUsageDialog(day, appUsages)
        }
        
        // Load weekly usage data lazily (only when graph is visible)
        // Defer to avoid blocking initial load
        handler.postDelayed({
            if (::weeklyUsageGraph.isInitialized && weeklyUsageGraph.visibility == View.VISIBLE) {
                loadWeeklyUsageData()
            }
        }, 300)

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
        setupWeather()

        // Initialize battery and phone usage widgets
        setupBatteryAndUsage()

        // Initialize notifications widget
        val notificationsContainer = findViewById<ViewGroup>(R.id.notifications_widget_container)
        val notificationsView = LayoutInflater.from(this).inflate(R.layout.notifications_widget, notificationsContainer, false)
        notificationsContainer.addView(notificationsView)
        notificationsWidget = NotificationsWidget(notificationsView)
        
        // Register broadcast receiver for notification updates
        val notificationFilter = IntentFilter("com.guruswarupa.launch.NOTIFICATIONS_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationUpdateReceiver, notificationFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationUpdateReceiver, notificationFilter)
        }
        
        // Initialize calculator widget
        val calculatorContainer = findViewById<ViewGroup>(R.id.calculator_widget_container)
        val calculatorView = LayoutInflater.from(this).inflate(R.layout.calculator_widget, calculatorContainer, false)
        calculatorContainer.addView(calculatorView)
        calculatorWidget = CalculatorWidget(calculatorView)

        // Initialize todo widget
        setupTodoWidget()

        // Initialize todo alarm manager
        todoAlarmManager = TodoAlarmManager(this)
        
        // Request notification permission
        requestNotificationPermission()

        todoRecyclerView = findViewById(R.id.todo_recycler_view)
        addTodoButton = findViewById(R.id.add_todo_button)

        todoRecyclerView.layoutManager = LinearLayoutManager(this)
        todoAdapter = TodoAdapter(todoItems, { todoItem ->
            removeTodoItem(todoItem)
        }, {
            // onTaskStateChanged callback - save and reschedule alarms when task state changes
            saveTodoItems()
            rescheduleTodoAlarms()
        })
        todoRecyclerView.adapter = todoAdapter

        addTodoButton.setOnClickListener {
            showAddTodoDialog()
        }

        loadTodoItems()
        // Reschedule alarms after loading todos
        rescheduleTodoAlarms()

        appDockManager = AppDockManager(this, sharedPreferences, appDock, packageManager, appLockManager, favoriteAppManager)

        // Refresh apps after appDockManager is fully initialized
        if (!appDockManager.getCurrentMode()) {
            // If focus mode was disabled during init, refresh the apps
            refreshAppsForFocusMode()
        }

        timeTextView.setOnClickListener {
            launchAppWithLockCheck("com.google.android.deskclock", "Google Clock")
        }

        dateTextView.setOnClickListener {
            launchAppWithLockCheck("com.google.android.calendar", "Google Calendar")
        }

        // Initialize appList before using it
        appList = mutableListOf()
        fullAppList = mutableListOf()

        loadApps()
        adapter = AppAdapter(this, appList, searchBox, isGridMode, this)
        recyclerView.adapter = adapter

        appSearchManager = AppSearchManager(
            packageManager = packageManager,
            appList = appList,
            fullAppList = fullAppList,
            adapter = adapter,
            searchBox = searchBox,
            contactsList = contactsList
        )

        // Initialize DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        
        // Set drawer to full width - use post to ensure view is laid out
        drawerLayout.post {
            val displayMetrics = resources.displayMetrics
            val drawerWidth = displayMetrics.widthPixels
            val drawerView = findViewById<FrameLayout>(R.id.widgets_drawer)
            drawerView?.let {
                val params = it.layoutParams as DrawerLayout.LayoutParams
                params.width = drawerWidth
                it.layoutParams = params
            }
        }
        
        // Enable edge swipe for DrawerLayout (this is the default, but making it explicit)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)
        
        // DrawerLayout already handles edge swipes automatically, but we can add additional
        // gesture detection for more responsive swipes from the left corner
        gestureDetector = GestureDetector(this, object : SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null) {
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y
                    // Check if swipe is from left edge (start x < 100px) and rightward
                    if (e1.x < 100 && deltaX > 200 && Math.abs(deltaY) < Math.abs(deltaX) * 0.8) {
                        if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                            drawerLayout.openDrawer(GravityCompat.START)
                        }
                        return true
                    }
                }
                return false
            }
        })
        
        // Setup touch listener on main content to detect swipes from left edge
        // This works alongside DrawerLayout's built-in edge swipe detection
        val mainContent = findViewById<FrameLayout>(R.id.main_content)
        mainContent.setOnTouchListener { _, event ->
            // Process gesture but don't consume event - let DrawerLayout handle it too
            gestureDetector.onTouchEvent(event)
            false
        }
        
        // Initialize WidgetManager
        val widgetContainer = findViewById<LinearLayout>(R.id.widget_container)
        widgetManager = WidgetManager(this, widgetContainer)
        
        // Setup add widget button
        findViewById<Button>(R.id.add_widget_button).setOnClickListener {
            widgetManager.requestPickWidget(this, REQUEST_PICK_WIDGET)
        }

        setWallpaperBackground()

        findViewById<ImageButton>(R.id.voice_search_button).setOnClickListener {
            Toast.makeText(this, "Voice search button clicked", Toast.LENGTH_SHORT).show()
            startVoiceSearch()
        }

        lastUpdateDate = getCurrentDateString()

        // Initialize finance widget
        financeManager = FinanceManager(sharedPreferences)
        balanceText = findViewById(R.id.balance_text)
        monthlySpentText = findViewById(R.id.monthly_spent_text)
        amountInput = findViewById(R.id.amount_input)
        descriptionInput = findViewById(R.id.description_input)

        setupFinanceWidget()
        updateFinanceDisplay()
    }

    fun refreshAppsForFocusMode() {
        loadApps()
    }
    
    fun refreshAppsForWorkspace() {
        loadApps()
    }
    
    fun filterAppsWithoutReload() {
        // Optimized: Filter existing list without reloading from package manager
        if (fullAppList.isEmpty()) {
            loadApps()
            return
        }
        
        backgroundExecutor.execute {
            try {
                // Filter apps based on favorite/show all mode in background
                val finalAppList = favoriteAppManager.filterApps(fullAppList, isShowAllAppsMode)
                
                // Update UI on main thread
                runOnUiThread {
                    appList = finalAppList.toMutableList()
                    adapter.updateAppList(appList)
                    appDockManager.refreshFavoriteToggle()
                    
                    // Update AppSearchManager with new filtered list
                    appSearchManager = AppSearchManager(
                        packageManager,
                        appList,
                        fullAppList,
                        adapter,
                        searchBox,
                        contactsList
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error filtering apps: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                loadApps() // Refresh apps with new settings
                updateFinanceDisplay() // Refresh finance display after reset
            }
        }
    }
    
    private val notificationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.NOTIFICATIONS_UPDATED") {
                if (::notificationsWidget.isInitialized) {
                    notificationsWidget.updateNotifications()
                }
            }
        }
    }

    private fun getPhoneNumberForContact(contactName: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        fun normalize(name: String): List<String> {
            return name.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .split(" ")
                .filter { it.isNotBlank() }
        }

        val inputParts = normalize(contactName)
        val seenNames = mutableSetOf<String>() // Track unique names

        val matches = mutableListOf<Pair<String, String>>() // name -> number

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)?.trim() ?: continue
                val number = it.getString(1)?.trim() ?: continue

                if (number.isEmpty() || !number.any { it.isDigit() }) continue

                val nameParts = normalize(name)

                if (inputParts.any { input -> nameParts.any { part -> part.contains(input) } }) {
                    if (!seenNames.contains(name.lowercase())) {
                        matches.add(name to number)
                        seenNames.add(name.lowercase())
                    }
                }
            }
        }

        return matches.minByOrNull { (name, _) ->
            val norm = normalize(name).joinToString(" ")
            when {
                norm == inputParts.joinToString(" ") -> 0
                norm.startsWith(inputParts.joinToString(" ")) -> 1
                else -> 2
            }
        }?.second
    }

    private fun startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), VOICE_SEARCH_REQUEST)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        if (intent.resolveActivity(packageManager) != null) {
            try {
                startActivityForResult(intent, VOICE_SEARCH_REQUEST)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Voice recognition not supported on this device",
                    Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Voice recognition not available",
                Toast.LENGTH_SHORT).show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VOICE_SEARCH_REQUEST && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.get(0)?.let { result ->
                searchBox.setText(result)
                searchBox.setSelection(result.length)
                handleVoiceCommand(result)
            }
        }
        if (requestCode == ShareManager.FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            shareManager.handleFilePickerResult(data?.data)
        }
        // Handle widget picking
        if (requestCode == REQUEST_PICK_WIDGET && resultCode == RESULT_OK) {
            widgetManager.handleWidgetPicked(this, data, REQUEST_PICK_WIDGET)
        }
        // Handle widget configuration
        if (requestCode == REQUEST_CONFIGURE_WIDGET && resultCode == RESULT_OK) {
            val appWidgetId = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: return
            widgetManager.handleWidgetConfigured(this, appWidgetId)
        }
    }

    private fun sendWhatsAppMessage(phoneNumber: String, message: String) {
        try {
            val formattedPhoneNumber = phoneNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/${Uri.encode(formattedPhoneNumber)}?text=${Uri.encode(message)}")
                setPackage("com.whatsapp")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed or failed to open message.", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsAppChat(contactName: String) {
        val contentResolver: ContentResolver = contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                var phoneNumber = it.getString(0)
                    .replace(" ", "")
                    .replace("-", "")
                    .replace("(", "")
                    .replace(")", "")



                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/${Uri.encode(phoneNumber)}")
                        setPackage("com.whatsapp")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openSMSChat(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", "")
        }

        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "No SMS app installed!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open messaging app.", Toast.LENGTH_SHORT).show()
        }
    }

    fun showApkSharingDialog() {
        shareManager.showApkSharingDialog()
    }

    private fun handleVoiceCommand(command: String) {
        when {
            command.startsWith("WhatsApp ", ignoreCase = true) -> {
                val contactName = command.substringAfter("WhatsApp ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    openWhatsAppChat(contactName)
                    searchBox.text.clear()
                }
            }
            command.startsWith("send ", ignoreCase = true) && command.contains(" to ", ignoreCase = true) -> {
                val parts = command.split(" to ", ignoreCase = true)
                if (parts.size == 2) {
                    val message = parts[0].substringAfter("send ").trim() // Extract message (e.g., "hi")
                    val contactName = parts[1].trim() // Extract contact name (e.g., "Swaroop")
                    val phoneNumber = getPhoneNumberForContact(contactName)

                    phoneNumber?.let {
                        sendWhatsAppMessage(it, message)
                        searchBox.text.clear()
                    } ?: Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show()
                }
            }

            command.startsWith("message ", ignoreCase = true) -> {
                val contactName = command.substringAfter("message ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    openSMSChat(contactName)
                    searchBox.text.clear()
                }
            }
            command.startsWith("call ", ignoreCase = true) -> {
                val contactName = command.substringAfter("call ", "").trim()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CODE_CALL_PHONE)
                    return
                }
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    val callIntent = Intent(Intent.ACTION_CALL)
                    callIntent.data = Uri.parse("tel:$it")
                    startActivity(callIntent)
                    searchBox.text.clear()
                }
            }
            command.startsWith("search ", ignoreCase = true) -> {
                val query = command.substringAfter("search ", "").trim()
                val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                startActivity(searchIntent)
                searchBox.text.clear()
            }
            command.startsWith("open ", ignoreCase = true) -> {
                val appName = command.substringAfter("open ", "").trim()
                if (appName.isNotEmpty()) {
                    val matchingApp = appList.find { resolveInfo ->
                        try {
                            val label = resolveInfo.activityInfo?.applicationInfo?.let {
                                resolveInfo.loadLabel(packageManager)?.toString()
                            }
                            label?.contains(appName, ignoreCase = true) ?: false
                        } catch (e: Exception) {
                            false
                        }
                    }
                    matchingApp?.let {
                        val intent = packageManager.getLaunchIntentForPackage(it.activityInfo.packageName)
                        intent?.let { startActivity(it) }
                        searchBox.text.clear()
                    } ?: searchBox.setText(command)
                } else {
                    searchBox.setText(command)
                }
            }
            command.startsWith("uninstall ", ignoreCase = true) -> {
                val appName = command.substringAfter("uninstall ", "").trim()
                if (appName.isNotEmpty()) {
                    val matchingApp = appList.find { resolveInfo ->
                        try {
                            val label = resolveInfo.activityInfo?.applicationInfo?.let {
                                resolveInfo.loadLabel(packageManager)?.toString()
                            }
                            label?.contains(appName, ignoreCase = true) ?: false
                        } catch (e: Exception) {
                            false
                        }
                    }

                    matchingApp?.let {
                        val packageName = it.activityInfo.packageName
                        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        searchBox.text.clear()
                    } ?: searchBox.setText(command)
                } else {
                    searchBox.setText(command)
                }
            }
            command.contains(" to ", ignoreCase = true) -> {
                val locations = command.split(" to ", ignoreCase = true)
                if (locations.size == 2) {
                    val origin = locations[0].trim()
                    val destination = locations[1].trim()
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(destination)}&travelmode=driving")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        startActivity(mapIntent)
                        searchBox.text.clear()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Google Maps not installed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setWallpaperBackground() {
        // Load wallpaper asynchronously to avoid blocking UI
        backgroundExecutor.execute {
            try {
                val wallpaperManager = WallpaperManager.getInstance(this)
                val bitmap = wallpaperManager.drawable.let {
                    if (it is BitmapDrawable) it.bitmap else null
                }
                runOnUiThread {
                    if (bitmap != null) {
                        wallpaperBackground.setImageBitmap(bitmap)
                        // Also set wallpaper for drawer background
                        findViewById<ImageView>(R.id.drawer_wallpaper_background)?.setImageBitmap(bitmap)
                    } else {
                        wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                        findViewById<ImageView>(R.id.drawer_wallpaper_background)?.setImageResource(R.drawable.default_wallpaper)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    wallpaperBackground.setImageResource(R.drawable.default_wallpaper)
                    findViewById<ImageView>(R.id.drawer_wallpaper_background)?.setImageResource(R.drawable.default_wallpaper)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentWallpaperBitmap?.recycle()
        currentWallpaperBitmap = null
        
        // Shutdown executor to prevent memory leaks
        backgroundExecutor.shutdown()
        
        // Cleanup managers
        if (::shareManager.isInitialized) {
            shareManager.cleanup()
        }
        
        // Destroy widget manager
        if (::widgetManager.isInitialized) {
            widgetManager.onDestroy()
        }
    }

    private val wallpaperChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
                setWallpaperBackground()
            }
        }
    }

    private val batteryChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryInBackground()
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

    // Method to launch app with lock check
    internal fun launchAppWithLockCheck(packageName: String, appName: String) {
        if (appLockManager.isAppLocked(packageName)) {
            appLockManager.verifyPin { isAuthenticated ->
                if (isAuthenticated) {
                    launchApp(packageName,appName)
                }
            }
        } else {
            launchApp(packageName,appName)
        }
    }

    // Method to launch app with timer check and then lock check
    internal fun launchAppWithTimerCheck(packageName: String, onTimerSet: () -> Unit) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        // Only show timer dialog for social media and entertainment apps
        if (appCategoryManager.shouldShowTimer(packageName, appName)) {
            appTimerManager.showTimerDialog(packageName, appName) { timerDuration ->
                if (timerDuration == AppTimerManager.NO_TIMER) {
                    // No timer selected, proceed with normal launch (includes lock check)
                    onTimerSet()
                } else {
                    // Timer selected - handle lock check first, then start timer (which launches app)
                    if (appLockManager.isAppLocked(packageName)) {
                        appLockManager.verifyPin { isAuthenticated ->
                            if (isAuthenticated) {
                                appTimerManager.startTimer(packageName, timerDuration)
                            }
                        }
                    } else {
                        appTimerManager.startTimer(packageName, timerDuration)
                    }
                }
            }
        } else {
            // For productivity and other apps, launch directly without timer (includes lock check)
            onTimerSet()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val packageName = intent.data?.encodedSchemeSpecificPart
                        if (packageName != null && context is MainActivity) {
                            context.runOnUiThread {
                                context.appList.removeAll { it.activityInfo.packageName == packageName }
                                context.fullAppList.removeAll { it.activityInfo.packageName == packageName }
                                if (context::adapter.isInitialized) {
                                    context.adapter.appList = context.appList
                                    context.adapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // Reload apps when a new package is installed (force refresh to clear cache)
                        if (context is MainActivity) {
                            context.cachedUnsortedList = null // Clear cache
                            context.loadApps(forceRefresh = true)
                        }
                    }
                }
            }
        }
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTime()
            updateDate()
            // Skip usage checks in power saver mode to save battery
            if (!isInPowerSaverMode) {
                checkDateChangeAndRefreshUsage()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun updateBatteryInBackground() {
        backgroundExecutor.execute {
            val batteryManager = BatteryManager(this)

            runOnUiThread {
                val batteryPercentageTextView = findViewById<TextView>(R.id.battery_percentage)
                batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }
            }
        }
    }

    private fun updateUsageInBackground() {
        backgroundExecutor.execute {
            // Get screen time usage for today
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val screenTimeMillis = usageStatsManager.getTotalUsageForPeriod(startTime, endTime)
            val formattedTime = usageStatsManager.formatUsageTime(screenTimeMillis)

            runOnUiThread {
                val screenTimeTextView = findViewById<TextView>(R.id.screen_time)
                screenTimeTextView?.text = "Screen Time: $formattedTime"
            }
        }
    }
    
    /**
     * Refresh all usage data in background without blocking UI
     * Updates app list usage times and weekly graph
     * Optimized: Defer expensive operations until after UI is shown
     */
    private fun refreshUsageDataInBackground(deferExpensive: Boolean = false) {
        backgroundExecutor.execute {
            try {
                // Clear adapter cache first to ensure fresh data
                adapter?.clearUsageCache()
                
                // Only refresh visible items first for fast UI response
                // Defer expensive weekly usage graph data until after UI is shown
                if (!deferExpensive && usageStatsManager.hasUsageStatsPermission()) {
                    // Load weekly data only if not deferring (e.g., user interaction)
                    val weeklyData = usageStatsManager.getWeeklyUsageData()
                    val appUsageData = usageStatsManager.getWeeklyAppUsageData()
                    
                    runOnUiThread {
                        if (::weeklyUsageGraph.isInitialized) {
                            weeklyUsageGraph.setUsageData(weeklyData)
                            weeklyUsageGraph.setAppUsageData(appUsageData)
                        }
                    }
                }
                
                // Refresh only visible items in adapter (much faster than full refresh)
                // Use handler.postDelayed to allow UI to render first
                handler.postDelayed({
                    // Only refresh visible items, not entire list
                    val layoutManager = recyclerView.layoutManager
                    if (layoutManager is LinearLayoutManager) {
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                            adapter?.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                        } else {
                            adapter?.notifyDataSetChanged()
                        }
                    } else {
                        adapter?.notifyDataSetChanged()
                    }
                }, 100) // Small delay to let UI render first
            } catch (e: Exception) {
                // Silently handle errors to prevent crashes
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load expensive weekly usage graph data (called lazily when needed)
     * This is the async version of loadWeeklyUsageData()
     */
    private fun loadWeeklyUsageGraphData() {
        backgroundExecutor.execute {
            try {
                loadWeeklyUsageData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Update notifications widget when activity resumes
        if (::notificationsWidget.isInitialized) {
            notificationsWidget.updateNotifications()
        }

        // PRIORITY 1: Show UI immediately - register receivers and update visible elements first
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryChangeReceiver, batteryFilter)

        // Reapply focus mode state when returning from apps (fast operation)
        applyFocusMode(appDockManager.getCurrentMode())
        appDockManager.refreshWorkspaceToggle()

        // Start widget managers (fast operations)
        if (::widgetManager.isInitialized) {
            widgetManager.onStart()
        }

        // PRIORITY 2: Load lightweight data in background (non-blocking)
        // Invalidate cache but don't refresh immediately
        usageStatsManager.invalidateCache()
        
        // Start update runnable immediately for time/date
        if (isInPowerSaverMode) {
            handler.post(powerSaverUpdateRunnable)
        } else {
            handler.post(updateRunnable)
        }

        // PRIORITY 3: Defer expensive operations until after UI is shown
        // Use handler.postDelayed to let UI render first
        handler.postDelayed({
            // Load wallpaper asynchronously (non-blocking)
            setWallpaperBackground()
            
            // Update battery and usage (lightweight operations)
            updateBatteryInBackground()
            updateUsageInBackground()
            
            // Refresh usage data but defer expensive weekly graph loading
            refreshUsageDataInBackground(deferExpensive = true)
            
            // Check date change in background
            backgroundExecutor.execute {
                checkDateChangeAndRefreshUsage()
            }
            
            // Load expensive weekly usage graph data after a delay (only if visible)
            handler.postDelayed({
                if (::weeklyUsageGraph.isInitialized && weeklyUsageGraph.visibility == View.VISIBLE) {
                    loadWeeklyUsageGraphData()
                }
            }, 500) // Load after 500ms delay
            
            // Reschedule todo alarms (non-critical, can wait)
            if (::todoAlarmManager.isInitialized) {
                rescheduleTodoAlarms()
            }
        }, 50) // Small delay to let UI render first

        // Only update weather if it's been more than 10 minutes (defer this too)
        val lastWeatherUpdate = sharedPreferences.getLong("last_weather_update", 0)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastWeatherUpdate > 600000) { // 10 minutes
            lastWeatherUpdateTime = currentTime
            handler.postDelayed({
                setupWeather()
                sharedPreferences.edit().putLong("last_weather_update", currentTime).apply()
            }, 200) // Defer weather update
        } else {
            lastWeatherUpdateTime = lastWeatherUpdate
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(powerSaverUpdateRunnable)
        try {
            unregisterReceiver(packageReceiver)
            unregisterReceiver(wallpaperChangeReceiver)
            unregisterReceiver(batteryChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        saveTodoItems()
        
        // Stop widget manager listening
        if (::widgetManager.isInitialized) {
            widgetManager.onStop()
        }
    }

    override fun onStop() {
        super.onStop()
        // Avoid modifying view visibility during stop to prevent WindowManager warnings
        // For HOME launcher activities, the system manages window lifecycle
        // and we should avoid interfering with that process
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save state without modifying views to prevent window management issues
    }

    private fun updateTime() {
        val currentTime = timeFormat.format(Date())
        timeTextView.text = currentTime

        // Update weather every 30 minutes (efficient check)
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis - lastWeatherUpdateTime >= WEATHER_UPDATE_INTERVAL) {
            lastWeatherUpdateTime = currentTimeMillis
            updateWeather()
        }
    }

    private fun updateDate() {
        val currentTime = dateFormat.format(Date())
        dateTextView.text = currentTime
    }

    fun loadApps(forceRefresh: Boolean = false) {
        val viewPreference = sharedPreferences.getString("view_preference", "list") // Read the latest preference
        val isGridMode = viewPreference == "grid"

        backgroundExecutor.execute {
            try {
                // Use cached list if available and not forcing refresh
                val currentTime = System.currentTimeMillis()
                val unsortedList = if (!forceRefresh && 
                    cachedUnsortedList != null && 
                    (currentTime - lastCacheTime) < CACHE_DURATION) {
                    cachedUnsortedList!!
                } else {
                    // Reload from package manager only when needed
                    val mainIntent = Intent(Intent.ACTION_MAIN, null)
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                    val list = packageManager.queryIntentActivities(mainIntent, 0)
                    cachedUnsortedList = list
                    lastCacheTime = currentTime
                    list
                }

                if (unsortedList.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this, "No apps found!", Toast.LENGTH_SHORT).show()
                        recyclerView.visibility = View.VISIBLE
                    }
                } else {
                    val focusMode = appDockManager.getCurrentMode()
                    val workspaceMode = appDockManager.isWorkspaceModeActive()
                    
                    // STEP 1: Fast filtering without expensive operations - show list immediately
                    val filteredApps = unsortedList.filter { app ->
                        val packageName = app.activityInfo.packageName
                        packageName != "com.guruswarupa.launch" &&
                                (!focusMode || !appDockManager.isAppHiddenInFocusMode(packageName)) &&
                                (!workspaceMode || appDockManager.isAppInActiveWorkspace(packageName))
                    }.toMutableList()
                    
                    // Filter apps based on favorite/show all mode
                    val finalAppList = favoriteAppManager.filterApps(filteredApps, isShowAllAppsMode)

                    // STEP 2: Show list immediately without sorting/labels (fast initial render)
                    runOnUiThread {
                        appList = finalAppList.toMutableList()
                        fullAppList = ArrayList(filteredApps)

                        // Optimize: Update existing adapter instead of creating new one
                        if (::adapter.isInitialized) {
                            adapter.updateAppList(appList)
                        } else {
                            recyclerView.layoutManager = if (isGridMode) {
                                GridLayoutManager(this, 4)
                            } else {
                                LinearLayoutManager(this)
                            }
                            adapter = AppAdapter(this, appList, searchBox, isGridMode, this)
                            recyclerView.adapter = adapter
                        }
                        
                        recyclerView.visibility = View.VISIBLE
                        
                        // Update dock toggle icon
                        appDockManager.refreshFavoriteToggle()

                        // Initialize AppSearchManager
                        if (!::appSearchManager.isInitialized) {
                            appSearchManager = AppSearchManager(
                                packageManager,
                                appList,
                                fullAppList,
                                adapter,
                                searchBox,
                                contactsList
                            )
                        }
                    }
                    
                    // STEP 3: Load labels, usage stats, and sort in background (after UI is shown)
                    backgroundExecutor.execute {
                        try {
                            // Batch read all usage stats at once (much faster than individual reads)
                            val allPrefs = sharedPreferences.all
                            val usageCache = mutableMapOf<String, Int>()
                            for ((key, value) in allPrefs) {
                                if (key.startsWith("usage_") && value is Int) {
                                    val packageName = key.removePrefix("usage_")
                                    usageCache[packageName] = value
                                }
                            }
                            
                            // Load labels and create sorted list
                            val labelCache = mutableMapOf<String, String>()
                            val sortedApps = filteredApps.sortedWith(
                                compareByDescending<ResolveInfo> { 
                                    usageCache[it.activityInfo.packageName] ?: 0
                                }
                                .thenBy { app ->
                                    val packageName = app.activityInfo.packageName
                                    labelCache.getOrPut(packageName) {
                                        try {
                                            app.loadLabel(packageManager).toString().lowercase()
                                        } catch (e: Exception) {
                                            packageName.lowercase()
                                        }
                                    }
                                }
                            )
                            
                            // Update with sorted list
                            val sortedFinalList = favoriteAppManager.filterApps(sortedApps, isShowAllAppsMode)
                            
                            runOnUiThread {
                                appList = sortedFinalList.toMutableList()
                                fullAppList = ArrayList(sortedApps)
                                adapter.updateAppList(appList)
                                
                                // Update AppSearchManager with sorted list
                                if (::appSearchManager.isInitialized) {
                                    appSearchManager = AppSearchManager(
                                        packageManager,
                                        appList,
                                        fullAppList,
                                        adapter,
                                        searchBox,
                                        contactsList
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error sorting apps", e)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error loading apps: ${e.message}", Toast.LENGTH_SHORT).show()
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }

        // Set visibility of search bar and voice search button based on focus mode
        // Only modify if activity is not finishing
        if (!isFinishing && !isDestroyed && ::searchBox.isInitialized && ::voiceSearchButton.isInitialized) {
            if (appDockManager.getCurrentMode()) {
                searchBox.visibility = android.view.View.GONE
                voiceSearchButton.visibility = android.view.View.GONE
            } else {
                searchBox.visibility = android.view.View.VISIBLE
                voiceSearchButton.visibility = android.view.View.VISIBLE
            }
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
            loadContacts()
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

    // Request location permission for weather
    private fun requestLocationPermissionForWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            // Permission already granted, force refresh to get location
            if (::weatherManager.isInitialized && ::weatherIcon.isInitialized && ::weatherText.isInitialized) {
                weatherManager.updateWeather(weatherIcon, weatherText, forceRefreshLocation = true)
            }
        }
    }

    // Request usage stats permission
    private fun requestUsageStatsPermission() {
        if (!usageStatsManager.hasUsageStatsPermission()) {
            AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("Usage Stats Permission")
                .setMessage("To show app usage time, please grant usage access permission in the next screen.")
                .setPositiveButton("Grant") { _, _ ->
                    startActivityForResult(usageStatsManager.requestUsageStatsPermission(), USAGE_STATS_REQUEST)
                }
                .setNegativeButton("Skip", null)
                .show()
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
                    loadContacts()
                } else {
                }
            }

            REQUEST_CODE_CALL_PHONE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with the phone call
                } else {
                }
            }

            SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with SMS functionality
                } else {
                }
            }
            VOICE_SEARCH_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    startVoiceSearch()
                }
            }
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Location permission granted, force refresh to get new location and store it
                    if (::weatherManager.isInitialized && ::weatherIcon.isInitialized && ::weatherText.isInitialized) {
                        weatherManager.updateWeather(weatherIcon, weatherText, forceRefreshLocation = true)
                    }
                }
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Notification permission granted, reschedule alarms
                    if (::todoAlarmManager.isInitialized) {
                        rescheduleTodoAlarms()
                    }
                }
            }
        }
    }
    private fun loadContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            backgroundExecutor.execute {
                try {
                    val tempContactsList = mutableListOf<String>()
                    val cursor = contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        while (it.moveToNext()) {
                            val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                            if (name != null && !tempContactsList.contains(name)) {
                                tempContactsList.add(name)
                            }
                        }
                    }

                    // Sort contacts in background thread
                    tempContactsList.sort()

                    runOnUiThread {
                        contactsList.clear()
                        contactsList.addAll(tempContactsList)
                        // Update search manager if it exists
                        if (::appSearchManager.isInitialized) {
                            appSearchManager.updateContactsList(contactsList)
                        }
                    }
                } catch (e: Exception) {
                    // Handle error silently or log
                }
            }
        }
    }

    fun applyFocusMode(isFocusMode: Boolean) {
        // Don't modify views if activity is finishing or stopped
        if (isFinishing || isDestroyed) {
            return
        }
        
        // Run filtering/sorting in background thread
        backgroundExecutor.execute {
            try {
                val filteredOrSortedApps: MutableList<ResolveInfo>
                
                if (isFocusMode) {
                    // Filter out hidden apps (fast operation, no sorting needed)
                    filteredOrSortedApps = fullAppList.filter { app ->
                        !appDockManager.isAppHiddenInFocusMode(app.activityInfo.packageName)
                    }.toMutableList()
                } else {
                    // Restore all apps and sort by usage then alphabetically (expensive operation)
                    // Pre-load all usage values at once
                    val allPrefs = sharedPreferences.all
                    val usageCache = mutableMapOf<String, Int>()
                    for ((key, value) in allPrefs) {
                        if (key.startsWith("usage_") && value is Int) {
                            val packageName = key.removePrefix("usage_")
                            usageCache[packageName] = value
                        }
                    }
                    
                    // Build label cache in background
                    val labelCache = mutableMapOf<String, String>()
                    fullAppList.forEach { app ->
                        val packageName = app.activityInfo.packageName
                        if (!labelCache.containsKey(packageName)) {
                            try {
                                labelCache[packageName] = app.loadLabel(packageManager).toString().lowercase()
                            } catch (e: Exception) {
                                labelCache[packageName] = packageName.lowercase()
                            }
                        }
                    }
                    
                    filteredOrSortedApps = fullAppList.sortedWith(
                        compareByDescending<ResolveInfo> {
                            usageCache[it.activityInfo.packageName] ?: 0
                        }.thenBy {
                            labelCache[it.activityInfo.packageName] ?: it.activityInfo.packageName.lowercase()
                        }
                    ).toMutableList()
                }

                // Update UI on main thread
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    
                    appList.clear()
                    appList.addAll(filteredOrSortedApps)

                    if (::searchBox.isInitialized && ::voiceSearchButton.isInitialized) {
                        if (isFocusMode) {
                            searchBox.visibility = android.view.View.GONE
                            voiceSearchButton.visibility = android.view.View.GONE
                        } else {
                            searchBox.visibility = android.view.View.VISIBLE
                            voiceSearchButton.visibility = android.view.View.VISIBLE
                        }
                    }

                    if (::adapter.isInitialized) {
                        adapter.updateAppList(appList)
                    }

                    // Update search manager with new app list
                    if (::appSearchManager.isInitialized || !isFinishing) {
                        appSearchManager = AppSearchManager(
                            packageManager,
                            appList,
                            fullAppList,
                            adapter,
                            searchBox,
                            contactsList
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error applying focus mode", e)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && ::adapter.isInitialized) {
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun loadWeeklyUsageData() {
        // This function is already called from background threads, but ensure UI updates are on main thread
        if (usageStatsManager.hasUsageStatsPermission()) {
            val weeklyData = usageStatsManager.getWeeklyUsageData()
            val appUsageData = usageStatsManager.getWeeklyAppUsageData()
            
            runOnUiThread {
                if (::weeklyUsageGraph.isInitialized) {
                    weeklyUsageGraph.setUsageData(weeklyData)
                    weeklyUsageGraph.setAppUsageData(appUsageData)
                }
            }
        }
    }
    
    private fun showDailyUsageDialog(day: String, appUsages: Map<String, Long>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_daily_usage, null)
        val dayTitle = dialogView.findViewById<TextView>(R.id.day_title)
        val totalTime = dialogView.findViewById<TextView>(R.id.total_time)
        val pieChart = dialogView.findViewById<DailyUsagePieView>(R.id.daily_pie_chart)
        val appUsageList = dialogView.findViewById<RecyclerView>(R.id.app_usage_list)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)
        
        // Set day title
        dayTitle.text = day
        
        // Calculate and display total time
        val totalUsage = appUsages.values.sum()
        val totalTimeText = formatUsageTimeForDialog(totalUsage)
        totalTime.text = "Total: $totalTimeText"
        
        // Set pie chart data
        pieChart.setAppUsageData(appUsages)
        
        // Setup RecyclerView with app usage list
        val sortedApps = appUsages.toList().sortedByDescending { it.second }
        val totalUsageFloat = totalUsage.toFloat()
        val appUsageItems = sortedApps.mapIndexed { index, (appName, usage) ->
            val percentage = if (totalUsageFloat > 0) (usage.toFloat() / totalUsageFloat * 100f) else 0f
            val color = pieChart.getColorForApp(index)
            AppUsageItem(appName, usage, percentage, color)
        }
        
        appUsageList.layoutManager = LinearLayoutManager(this)
        appUsageList.adapter = AppUsageAdapter(appUsageItems)
        
        // Create and show dialog
        val dialog = android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun formatUsageTimeForDialog(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0m"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    private fun updateWeather() {
        weatherManager.updateWeather(weatherIcon, weatherText)
    }

    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }

    private fun checkDateChangeAndRefreshUsage() {
        val currentDate = getCurrentDateString()
        if (currentDate != lastUpdateDate) {
            lastUpdateDate = currentDate
            refreshUsageStats()
        }
    }

    private lateinit var appAdapter: AppAdapter
    private lateinit var usageStatsTextView: TextView

    private fun refreshUsageStats() {
        // Clear adapter cache to force refresh
        adapter?.clearUsageCache()
        // Only refresh visible items for better performance
        handler.postDelayed({
            val layoutManager = recyclerView.layoutManager
            if (layoutManager is LinearLayoutManager) {
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                    adapter?.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                } else {
                    adapter?.notifyDataSetChanged()
                }
            } else {
                adapter?.notifyDataSetChanged()
            }
        }, 100)
    }

    private fun setupWeather() {
        val weatherIcon = findViewById<ImageView>(R.id.weather_icon)
        val weatherText = findViewById<TextView>(R.id.weather_text)

        // Don't request permissions automatically - only when user taps weather widget
        // Just update weather with existing stored location or show unavailable
        weatherManager.updateWeather(weatherIcon, weatherText)
    }

    private fun setupBatteryAndUsage() {
        // Assuming you have TextViews in your layout with these IDs
        val batteryPercentageTextView = findViewById<TextView>(R.id.battery_percentage)
        val screenTimeTextView = findViewById<TextView>(R.id.screen_time)

        // Get battery percentage using BatteryManager
        val batteryManager = BatteryManager(this)
        batteryPercentageTextView?.let { batteryManager.updateBatteryInfo(it) }

        // Get screen time usage in minutes for today
        val calendar = Calendar.getInstance()
        // Set to start of current day
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val screenTimeMillis = usageStatsManager.getTotalUsageForPeriod(startTime, endTime)
        val formattedTime = usageStatsManager.formatUsageTime(screenTimeMillis)
        screenTimeTextView?.text = "Screen Time: $formattedTime"
    }

    private fun setupTodoWidget() {
        //Initialization logic for the todo widget
    }

    /**
     * Request notification permission (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }

    /**
     * Reschedule all todo alarms
     */
    private fun rescheduleTodoAlarms() {
        if (::todoAlarmManager.isInitialized) {
            todoAlarmManager.rescheduleAllAlarms(todoItems)
        }
    }

    private fun showWeatherSettings() {
        val prefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
        val currentApiKey = prefs.getString("weather_api_key", "")

        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        val input = EditText(this)
        input.setText(currentApiKey)
        input.hint = "Enter your OpenWeatherMap API key"

        builder.setTitle("Weather API Settings")
            .setMessage("Update your OpenWeatherMap API key.\n\nGet one free at: openweathermap.org/api")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val apiKey = input.text.toString().trim()
                if (apiKey.isNotEmpty()) {
                    prefs.edit()
                        .putString("weather_api_key", apiKey)
                        .putBoolean("weather_api_key_rejected", false)
                        .apply()
                    Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
                    // Refresh weather
                    setupWeather()
                } else {
                    prefs.edit().remove("weather_api_key").apply()
                    Toast.makeText(this, "API key removed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddTodoDialog() {
        val dialogBuilder = android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
        dialogBuilder.setTitle("Add Todo Item")

        // Create custom layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_todo, null)
        val taskInput = dialogView.findViewById<EditText>(R.id.task_input)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.category_spinner)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.priority_spinner)
        val enableTimeCheckbox = dialogView.findViewById<CheckBox>(R.id.enable_time_checkbox)
        val timePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker)
        val recurringCheckbox = dialogView.findViewById<CheckBox>(R.id.recurring_checkbox)
        val recurrenceTypeGroup = dialogView.findViewById<RadioGroup>(R.id.recurrence_type_group)
        val recurrenceDays = dialogView.findViewById<RadioButton>(R.id.recurrence_days)
        val recurrenceIntervalRadio = dialogView.findViewById<RadioButton>(R.id.recurrence_interval)
        val daysContainer = dialogView.findViewById<LinearLayout>(R.id.days_selection_container)
        val intervalContainer = dialogView.findViewById<LinearLayout>(R.id.interval_selection_container)
        val intervalSpinner = dialogView.findViewById<Spinner>(R.id.interval_spinner)
        val intervalStartTimePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.interval_start_time_picker)

        // Day checkboxes
        val dayCheckboxes = listOf(
            dialogView.findViewById<CheckBox>(R.id.checkbox_sunday),
            dialogView.findViewById<CheckBox>(R.id.checkbox_monday),
            dialogView.findViewById<CheckBox>(R.id.checkbox_tuesday),
            dialogView.findViewById<CheckBox>(R.id.checkbox_wednesday),
            dialogView.findViewById<CheckBox>(R.id.checkbox_thursday),
            dialogView.findViewById<CheckBox>(R.id.checkbox_friday),
            dialogView.findViewById<CheckBox>(R.id.checkbox_saturday)
        )

        // Setup category spinner
        val categories = arrayOf("General", "Work", "Personal", "Health", "Shopping", "Study")
        categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        // Setup priority spinner
        val priorities = TodoItem.Priority.values().map { it.displayName }.toTypedArray()
        prioritySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, priorities)
        prioritySpinner.setSelection(1) // Default to Medium

        // Setup interval spinner
        val intervals = arrayOf(
            "30 minutes",
            "1 hour",
            "2 hours",
            "3 hours",
            "4 hours",
            "6 hours",
            "8 hours",
            "12 hours"
        )
        val intervalValues = arrayOf(30, 60, 120, 180, 240, 360, 480, 720)
        intervalSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)

        // Handle time picker checkbox
        enableTimeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            timePicker.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Handle recurring checkbox
        recurringCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                recurrenceTypeGroup.visibility = View.VISIBLE
                if (recurrenceDays.isChecked) {
                    daysContainer.visibility = View.VISIBLE
                    intervalContainer.visibility = View.GONE
                } else {
                    daysContainer.visibility = View.GONE
                    intervalContainer.visibility = View.VISIBLE
                }
            } else {
                recurrenceTypeGroup.visibility = View.GONE
                daysContainer.visibility = View.GONE
                intervalContainer.visibility = View.GONE
            }
        }

        // Handle recurrence type change
        recurrenceTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.recurrence_days) {
                daysContainer.visibility = View.VISIBLE
                intervalContainer.visibility = View.GONE
            } else {
                daysContainer.visibility = View.GONE
                intervalContainer.visibility = View.VISIBLE
            }
        }

        dialogBuilder.setView(dialogView)

        dialogBuilder.setPositiveButton("Add Task") { _, _ ->
            val todoText = taskInput.text.toString().trim()
            if (todoText.isNotEmpty()) {
                val isRecurring = recurringCheckbox.isChecked
                val selectedDays = if (isRecurring && recurrenceDays.isChecked) {
                    dayCheckboxes.mapIndexedNotNull { index, checkbox ->
                        if (checkbox.isChecked) index + 1 else null // 1=Sunday, 2=Monday, etc.
                    }.toSet()
                } else {
                    emptySet()
                }
                
                val recurrenceInterval = if (isRecurring && recurrenceIntervalRadio.isChecked) {
                    intervalValues[intervalSpinner.selectedItemPosition]
                } else {
                    null
                }
                
                val intervalStartTime = if (isRecurring && recurrenceIntervalRadio.isChecked && recurrenceInterval != null) {
                    val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intervalStartTimePicker.hour
                    } else {
                        @Suppress("DEPRECATION")
                        intervalStartTimePicker.currentHour
                    }
                    val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intervalStartTimePicker.minute
                    } else {
                        @Suppress("DEPRECATION")
                        intervalStartTimePicker.currentMinute
                    }
                    String.format("%02d:%02d", hour, minute)
                } else {
                    null
                }

                val category = categories[categorySpinner.selectedItemPosition]
                val priority = TodoItem.Priority.values()[prioritySpinner.selectedItemPosition]
                val dueTime = if (enableTimeCheckbox.isChecked) {
                    val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        timePicker.hour
                    } else {
                        @Suppress("DEPRECATION")
                        timePicker.currentHour
                    }
                    val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        timePicker.minute
                    } else {
                        @Suppress("DEPRECATION")
                        timePicker.currentMinute
                    }
                    String.format("%02d:%02d", hour, minute)
                } else {
                    null
                }

                addTodoItem(todoText, isRecurring, selectedDays, priority, category, dueTime, recurrenceInterval, intervalStartTime)
            }
        }

        dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = dialogBuilder.create()
        dialog.show()
    }

    private fun addTodoItem(
        text: String,
        isRecurring: Boolean,
        selectedDays: Set<Int> = emptySet(),
        priority: TodoItem.Priority = TodoItem.Priority.MEDIUM,
        category: String = "General",
        dueTime: String? = null,
        recurrenceInterval: Int? = null,
        intervalStartTime: String? = null
    ) {
        val newTodo = TodoItem(text, false, isRecurring, null, selectedDays, priority, category, dueTime, recurrenceInterval, intervalStartTime)
        val index = todoItems.size
        todoItems.add(newTodo)
        todoAdapter.notifyItemInserted(index)
        saveTodoItems()
        
        // Schedule alarm if due time is set or if it's interval-based
        if (::todoAlarmManager.isInitialized) {
            if (dueTime != null || (newTodo.isIntervalBased() && newTodo.intervalStartTime != null)) {
                val requestCode = todoAlarmManager.getRequestCode(newTodo, index)
                todoAlarmManager.scheduleAlarm(newTodo, requestCode)
            }
        }
    }

    private fun removeTodoItem(todoItem: TodoItem) {
        val index = todoItems.indexOf(todoItem)
        if (index != -1) {
            // Cancel alarm before removing
            if (::todoAlarmManager.isInitialized && todoItem.dueTime != null) {
                val requestCode = todoAlarmManager.getRequestCode(todoItem, index)
                todoAlarmManager.cancelAlarm(todoItem, requestCode)
            }
            todoItems.removeAt(index)
            todoAdapter.notifyItemRemoved(index)
            saveTodoItems()
        }
    }

    private fun loadTodoItems() {
        val todoString = sharedPreferences.getString("todo_items", "") ?: ""
        if (todoString.isNotEmpty()) {
            val todoArray = todoString.split("|")
            todoItems.clear()
            for (todoString in todoArray) {
                if (todoString.isNotEmpty()) {
                    val parts = todoString.split(":")
                    if (parts.size >= 7) {
                        // New format with all fields
                        val text = parts[0]
                        val isChecked = parts[1].toBoolean()
                        val isRecurring = parts[2].toBoolean()
                        val lastCompletedDate = if (parts[3].isNotEmpty()) parts[3] else null
                        val selectedDays = if (parts[4].isNotEmpty()) {
                            parts[4].split(",").mapNotNull { it.toIntOrNull() }.toSet()
                        } else {
                            emptySet()
                        }
                        val priority = try {
                            TodoItem.Priority.valueOf(parts[5])
                        } catch (e: Exception) {
                            TodoItem.Priority.MEDIUM
                        }
                        val category = parts[6]
                        val dueTime = if (parts.size > 7 && parts[7].isNotEmpty()) parts[7] else null
                        val recurrenceInterval = if (parts.size > 8 && parts[8].isNotEmpty()) {
                            parts[8].toIntOrNull()
                        } else {
                            null
                        }
                        val intervalStartTime = if (parts.size > 9 && parts[9].isNotEmpty()) {
                            parts[9]
                        } else {
                            null
                        }

                        todoItems.add(TodoItem(text, isChecked, isRecurring, lastCompletedDate, selectedDays, priority, category, dueTime, recurrenceInterval, intervalStartTime))
                    } else if (parts.size >= 3) {
                        // Legacy format support
                        val text = parts[0]
                        val isChecked = parts[1].toBoolean()
                        val isRecurring = parts[2].toBoolean()
                        val lastCompletedDate = if (parts.size > 3) parts[3] else null
                        todoItems.add(TodoItem(text, isChecked, isRecurring, lastCompletedDate))
                    } else if (parts.size == 2) {
                        // Very old legacy format support
                        val text = parts[0]
                        val isChecked = parts[1].toBoolean()
                        todoItems.add(TodoItem(text, isChecked, false))
                    }
                }
            }
            checkRecurringTasks()
            todoAdapter.notifyDataSetChanged()
            
            // Reschedule alarms after loading
            if (::todoAlarmManager.isInitialized) {
                rescheduleTodoAlarms()
            }
        }
    }

    private fun saveTodoItems() {
        val todoString = todoItems.joinToString("|") {
            val selectedDaysString = it.selectedDays.joinToString(",")
            "${it.text}:${it.isChecked}:${it.isRecurring}:${it.lastCompletedDate ?: ""}:${selectedDaysString}:${it.priority.name}:${it.category}:${it.dueTime ?: ""}:${it.recurrenceInterval ?: ""}:${it.intervalStartTime ?: ""}"
        }
        sharedPreferences.edit().putString("todo_items", todoString).apply()
    }

    private fun checkRecurringTasks() {
        val currentDate = getCurrentDateString()
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, etc.
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val currentTimeMillis = System.currentTimeMillis()

        val itemsToRemove = mutableListOf<TodoItem>()

        for (todoItem in todoItems) {
            if (todoItem.isRecurring) {
                if (todoItem.isIntervalBased() && todoItem.recurrenceInterval != null) {
                    // Interval-based recurrence (Pomodoro-style)
                    val lastCompletedDate = todoItem.lastCompletedDate
                    if (lastCompletedDate != null) {
                        try {
                            // Parse last completed timestamp
                            val lastCompletedMillis = lastCompletedDate.toLongOrNull()
                            if (lastCompletedMillis != null) {
                                val elapsedMinutes = (currentTimeMillis - lastCompletedMillis) / (1000 * 60)
                                if (elapsedMinutes >= todoItem.recurrenceInterval) {
                                    // Interval has passed, reset task
                                    todoItem.isChecked = false
                                    todoItem.lastCompletedDate = null
                                }
                            }
                        } catch (e: Exception) {
                            // If parsing fails, treat as date string (legacy format)
                            if (todoItem.lastCompletedDate != currentDate) {
                                todoItem.isChecked = false
                                todoItem.lastCompletedDate = null
                            }
                        }
                    } else if (todoItem.isChecked) {
                        // Task was just completed, set timestamp
                        todoItem.lastCompletedDate = currentTimeMillis.toString()
                    }
                } else if (todoItem.isDayBased() && todoItem.lastCompletedDate != currentDate) {
                    // Day-based recurrence - only reset if today is one of the selected days
                    if (todoItem.selectedDays.contains(currentDayOfWeek)) {
                        todoItem.isChecked = false
                    }
                } else if (!todoItem.isDayBased() && todoItem.lastCompletedDate != currentDate) {
                    // Daily recurring (legacy behavior)
                    todoItem.isChecked = false
                }
            } else if (!todoItem.isRecurring && todoItem.dueTime != null) {
                // Check if non-recurring task with due time is overdue
                val dueTimeParts = todoItem.dueTime.split(":")
                if (dueTimeParts.size == 2) {
                    try {
                        val dueHour = dueTimeParts[0].toInt()
                        val dueMinute = dueTimeParts[1].toInt()
                        val dueTimeInMinutes = dueHour * 60 + dueMinute

                        // If current time has passed the due time, mark for removal
                        if (currentTimeInMinutes > dueTimeInMinutes) {
                            itemsToRemove.add(todoItem)
                        }
                    } catch (e: NumberFormatException) {
                        // Invalid time format, ignore
                    }
                }
            }
        }

        // Remove overdue non-recurring items
        for (item in itemsToRemove) {
            val index = todoItems.indexOf(item)
            if (index != -1) {
                todoItems.removeAt(index)
                todoAdapter.notifyItemRemoved(index)
            }
        }

        if (itemsToRemove.isNotEmpty()) {
            saveTodoItems()
        }
    }

    private fun showTransactionHistory() {
        val allPrefs = sharedPreferences.all
        val currencySymbol = financeManager.getCurrency()
        
        // Parse transactions with timestamps from SharedPreferences
        val transactionList = mutableListOf<Transaction>()
        allPrefs.keys.filter { it.startsWith("transaction_") }.forEach { key ->
            val transactionData = sharedPreferences.getString(key, "") ?: ""
            val parts = transactionData.split(":")
            if (parts.size >= 3) {
                val type = parts[0]
                val amount = parts[1].toDoubleOrNull() ?: 0.0
                val timestamp = key.substringAfter("transaction_").toLongOrNull() ?: 0L
                val description = if (parts.size > 3) parts[3] else ""
                transactionList.add(Transaction(type, amount, description, timestamp))
            }
        }
        
        // Sort by timestamp descending (newest first)
        val sortedTransactions = transactionList.sortedByDescending { it.timestamp }

        if (sortedTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions found", Toast.LENGTH_SHORT).show()
            return
        }

        // Create custom dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_transaction_history, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.transaction_recycler_view)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = TransactionAdapter(sortedTransactions, currencySymbol)
        recyclerView.adapter = adapter

        val dialog = android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupFinanceWidget() {
        findViewById<Button>(R.id.add_income_btn).setOnClickListener {
            addTransaction(true)
        }

        findViewById<Button>(R.id.add_expense_btn).setOnClickListener {
            addTransaction(false)
        }

        // Click on balance text or card to show transaction history
        balanceText.setOnClickListener {
            showTransactionHistory()
        }
        
        // Also make the balance card clickable
        findViewById<LinearLayout>(R.id.balance_card)?.setOnClickListener {
            showTransactionHistory()
        }
    }

    private fun addTransaction(isIncome: Boolean) {
        val amountText = amountInput.text.toString()
        val description = descriptionInput.text.toString().trim()

        if (amountText.isNotEmpty()) {
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {
                // 🔧 Use addIncome or addExpense instead of addTransaction
                if (isIncome) {
                    financeManager.addIncome(amount, description)
                } else {
                    financeManager.addExpense(amount, description)
                }

                // Clear inputs after adding transaction
                amountInput.text.clear()
                descriptionInput.text.clear()

                updateFinanceDisplay()

                val currencySymbol = financeManager.getCurrency()
                val action = if (isIncome) "Income" else "Expense"
                val message = if (description.isNotEmpty()) {
                    "$action of $currencySymbol$amount added: $description"
                } else {
                    "$action of $currencySymbol$amount added"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFinanceDisplay() {
        val currencySymbol = financeManager.getCurrency()
        val balance = financeManager.getBalance()
        val monthlyExpenses = financeManager.getMonthlyExpenses()
        val monthlyIncome = financeManager.getMonthlyIncome()
        val netSavings = monthlyIncome - monthlyExpenses
        
        // Format balance with 2 decimal places
        balanceText.text = String.format("%s%.2f", currencySymbol, balance)
        
        // Show net savings for the month (income - expenses) with neutral color
        val netText = if (netSavings >= 0) {
            "This Month: +$currencySymbol${String.format("%.2f", netSavings)}"
        } else {
            "This Month: -$currencySymbol${String.format("%.2f", kotlin.math.abs(netSavings))}"
        }
        monthlySpentText.text = netText
        monthlySpentText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    fun applyPowerSaverMode(isEnabled: Boolean) {
        isInPowerSaverMode = isEnabled // Track the state

        if (isEnabled) {
            // Fast operations first - update UI immediately
            setPitchBlackBackground()
            hideNonEssentialWidgets()
            
            // Stop or slow down background updates to save battery
            stopBackgroundUpdates()
            
            // Reduce animation duration (if supported)
            window?.setWindowAnimations(android.R.style.Animation_Toast)
            
            // Refresh adapter efficiently (only visible items)
            if (::adapter.isInitialized) {
                // Use efficient update that only refreshes visible items
                handler.post {
                    val layoutManager = recyclerView.layoutManager
                    if (layoutManager is LinearLayoutManager) {
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                            adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                        }
                    }
                }
            }
            
        } else {
            // Fast operations first - show widgets immediately
            showNonEssentialWidgets()
            
            // Restore background and reload wallpaper asynchronously (non-blocking)
            handler.post {
                restoreOriginalBackground()
                // Reload wallpaper in background to avoid blocking
                setWallpaperBackground()
            }
            
            // Resume background updates (already non-blocking)
            resumeBackgroundUpdates()
            
            // Restore normal animations
            window?.setWindowAnimations(0)
            
            // Refresh adapter efficiently with small delay to let UI render first
            if (::adapter.isInitialized) {
                handler.postDelayed({
                    val layoutManager = recyclerView.layoutManager
                    if (layoutManager is LinearLayoutManager) {
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        if (firstVisible != RecyclerView.NO_POSITION && lastVisible != RecyclerView.NO_POSITION) {
                            adapter.notifyItemRangeChanged(firstVisible, lastVisible - firstVisible + 1)
                        } else {
                            // Fallback to full update if positions not available
                            adapter.notifyDataSetChanged()
                        }
                    } else {
                        adapter.notifyDataSetChanged()
                    }
                }, 100) // Small delay to ensure UI is responsive
            }
        }
    }
    
    private fun stopBackgroundUpdates() {
        // Stop the frequent update runnable (time/date updates every second)
        handler.removeCallbacks(updateRunnable)
        handler.removeCallbacks(powerSaverUpdateRunnable)
        
        // Start a slower update runnable for power saver mode (update every 30 seconds instead of 1 second)
        handler.post(powerSaverUpdateRunnable)
    }
    
    private fun resumeBackgroundUpdates() {
        // Stop power saver update runnable
        handler.removeCallbacks(powerSaverUpdateRunnable)
        
        // Resume normal update runnable
        handler.post(updateRunnable)
        
        // Resume battery and usage updates
        updateBatteryInBackground()
        updateUsageInBackground()
    }
    
    // Slower update runnable for power saver mode (updates every 30 seconds instead of 1 second)
    private val powerSaverUpdateRunnable = object : Runnable {
        override fun run() {
            if (isInPowerSaverMode) {
                // Only update time and date, skip usage checks
                updateTime()
                updateDate()
                // Update every 30 seconds instead of 1 second to save battery
                handler.postDelayed(this, 30000)
            }
        }
    }

    private fun hideNonEssentialWidgets() {
        // Don't modify views if activity is finishing or stopped
        if (isFinishing || isDestroyed) {
            return
        }
        
        // Hide weather, battery, usage stats, todo, finance widgets
        findViewById<View>(R.id.weather_widget)?.visibility = View.GONE
        findViewById<TextView>(R.id.battery_percentage)?.visibility = View.GONE
        findViewById<TextView>(R.id.screen_time)?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.finance_widget)?.visibility = View.GONE
        if (::weeklyUsageGraph.isInitialized) {
            weeklyUsageGraph.visibility = View.GONE
        }
        // Hide the entire weekly usage widget container
        findViewById<View>(R.id.weekly_usage_widget)?.visibility = View.GONE

        // Hide the wallpaper background in power saver mode
        findViewById<ImageView>(R.id.wallpaper_background)?.visibility = View.GONE
        findViewById<ImageView>(R.id.drawer_wallpaper_background)?.visibility = View.GONE

        // Hide the todo widget (the LinearLayout containing todo list)
        if (::todoRecyclerView.isInitialized) {
            todoRecyclerView.parent?.let { parent ->
                if (parent is View) {
                    parent.visibility = View.GONE
                }
            }
        }
    }

    private fun showNonEssentialWidgets() {
        // Don't modify views if activity is finishing or stopped
        if (isFinishing || isDestroyed) {
            return
        }
        
        // Fast operations - show widgets immediately (non-blocking)
        findViewById<View>(R.id.weather_widget)?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.battery_percentage)?.visibility = View.VISIBLE
        findViewById<TextView>(R.id.screen_time)?.visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.finance_widget)?.visibility = View.VISIBLE
        if (::weeklyUsageGraph.isInitialized) {
            weeklyUsageGraph.visibility = View.VISIBLE
            // Defer loading expensive weekly usage data - load it asynchronously after UI is shown
            handler.postDelayed({
                if (!isFinishing && !isDestroyed && ::weeklyUsageGraph.isInitialized && weeklyUsageGraph.visibility == View.VISIBLE) {
                    loadWeeklyUsageGraphData()
                }
            }, 300) // Small delay to let UI render first
        }
        // Show the entire weekly usage widget container
        findViewById<View>(R.id.weekly_usage_widget)?.visibility = View.VISIBLE

        // Show the wallpaper background when power saver mode is disabled
        // (Wallpaper will be reloaded asynchronously by setWallpaperBackground() called from applyPowerSaverMode)
        findViewById<ImageView>(R.id.wallpaper_background)?.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.drawer_wallpaper_background)?.visibility = View.VISIBLE

        // Show the todo widget (the LinearLayout containing todo list)
        if (::todoRecyclerView.isInitialized) {
            todoRecyclerView.parent?.let { parent ->
                if (parent is View) {
                    parent.visibility = View.VISIBLE
                }
            }
        }
    }

    fun setPitchBlackBackground() {
        // Don't modify root view if activity is finishing or destroyed
        if (isFinishing || isDestroyed) {
            return
        }
        findViewById<android.view.View>(android.R.id.content)?.setBackgroundColor(android.graphics.Color.BLACK)
    }

    fun restoreOriginalBackground() {
        // Don't modify root view if activity is finishing or destroyed
        if (isFinishing || isDestroyed) {
            return
        }
        findViewById<android.view.View>(android.R.id.content)?.setBackgroundResource(R.drawable.wallpaper_background)
    }

    override fun onBackPressed() {
        // Close drawer if it's open, otherwise handle back button normally
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}