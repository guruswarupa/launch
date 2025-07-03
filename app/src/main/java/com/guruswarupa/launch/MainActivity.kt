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
import android.widget.Button
import java.util.Calendar
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.drawable.Drawable
import android.os.Build



class MainActivity : ComponentActivity() {

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
    private val handler = Handler()

    private lateinit var wallpaperBackground: ImageView
    private var currentWallpaperBitmap: Bitmap? = null

    lateinit var appSearchManager: AppSearchManager
    private lateinit var appDockManager: AppDockManager
    private lateinit var usageStatsManager: AppUsageStatsManager
    private var contactsList: List<String> = emptyList()
    private var lastSearchTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 300
    private lateinit var weeklyUsageGraph: WeeklyUsageGraphView // Add this line
    private var lastUpdateDate: String = ""
    private lateinit var weatherManager: WeatherManager // Add this line
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherText: TextView
    private lateinit var quickNoteText: EditText
    private lateinit var voiceSearchButton: ImageButton

    // Finance widget variables
    private lateinit var financeManager: FinanceManager
    private lateinit var balanceText: TextView
    private lateinit var monthlySpentText: TextView
    private lateinit var amountInput: EditText
    private lateinit var descriptionInput: EditText

    companion object {
        private const val CONTACTS_PERMISSION_REQUEST = 100
        private const val REQUEST_CODE_CALL_PHONE = 200
        val SMS_PERMISSION_REQUEST = 300
        private const val WALLPAPER_REQUEST_CODE = 456
        private const val VOICE_SEARCH_REQUEST = 500
        private const val USAGE_STATS_REQUEST = 600
    }

    // Custom QR Scanner launcher
    val QRScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val qrResult = result.data?.getStringExtra(QRScannerActivity.RESULT_QR_CODE)
            qrResult?.let {
                appDockManager.handleQRResult(it)
            }
        }
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

        // Register settings update receiver
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
        quickNoteText = findViewById(R.id.quick_note_text)
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

        // Request necessary permissions
        requestContactsPermission()
        requestSmsPermission()
        requestUsageStatsPermission()

        // Load weekly usage data
        loadWeeklyUsageData()

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
        updateWeather()

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
            searchBox = searchBox,
            contactsList = contactsList
        )

        appDockManager.loadDockApps()
        setWallpaperBackground()

        findViewById<ImageButton>(R.id.voice_search_button).setOnClickListener {
            Toast.makeText(this, "Voice search button clicked", Toast.LENGTH_SHORT).show()
            startVoiceSearch()
        }

        lastUpdateDate = getCurrentDateString()

        quickNoteText = findViewById(R.id.quick_note_text)
        loadQuickNote()
        setupQuickNoteAutoSave()

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

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.guruswarupa.launch.SETTINGS_UPDATED") {
                loadApps() // Refresh apps with new settings
                updateFinanceDisplay() // Refresh finance display after reset
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
    }

    private fun sendWhatsAppMessage(phoneNumber: String, message: String) {
        try {
            val formattedPhoneNumber = phoneNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            if (!formattedPhoneNumber.startsWith("+")) {
                val fullPhoneNumber = "+91$formattedPhoneNumber" // Adjust the country code as needed
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/${Uri.encode(fullPhoneNumber)}?text=${Uri.encode(message)}")
                    setPackage("com.whatsapp")
                }
                startActivity(intent)
            }
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

                if (!phoneNumber.startsWith("+")) {
                    phoneNumber = "+91$phoneNumber"
                }

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

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val packageName = intent.data?.encodedSchemeSpecificPart
                        if (packageName != null) {
                            appList.removeAll { it.activityInfo.packageName == packageName }
                            fullAppList.removeAll { it.activityInfo.packageName == packageName }
                            adapter.appList = appList
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
                Intent.ACTION_PACKAGE_ADDED -> {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        val relaunchIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context?.startActivity(relaunchIntent)
                        if (context is Activity) {
                            context.finish()
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
            checkDateChangeAndRefreshUsage()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh usage stats when returning to launcher
        refreshUsageStats()
        handler.post(updateRunnable)

        setWallpaperBackground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))

        // Reapply focus mode state when returning from apps
        applyFocusMode(appDockManager.getCurrentMode())
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
        unregisterReceiver(packageReceiver)
        unregisterReceiver(wallpaperChangeReceiver)
        saveQuickNote()
    }

    private fun updateTime() {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val currentTime = sdf.format(Date())
        timeTextView.text = currentTime

        // Update weather every 30 minutes (1800000 milliseconds)
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis % 1800000 < 1000) {
            updateWeather()
        }
    }

    private fun updateDate() {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val currentTime = sdf.format(Date())
        dateTextView.text = currentTime
    }

    fun loadApps() {
        val viewPreference = sharedPreferences.getString("view_preference", "list") // Read the latest preference
        val isGridMode = viewPreference == "grid"

        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val unsortedList = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
            .toMutableList()

        fullAppList = unsortedList.toMutableList()

        if (unsortedList.isEmpty()) {
            Toast.makeText(this, "No apps found!", Toast.LENGTH_SHORT).show()
        } else {
            // Apply focus mode filtering using AppDockManager logic
            val filteredApps = if (appDockManager.getCurrentMode()) {
                unsortedList.filter { app ->
                    !appDockManager.isAppHiddenInFocusMode(app.activityInfo.packageName)
                }
            } else {
                unsortedList
            }

            appList = filteredApps.filter { it.activityInfo.packageName != "com.guruswarupa.launch" }
                .sortedWith(
                    compareByDescending<ResolveInfo> { sharedPreferences.getInt("usage_${it.activityInfo.packageName}", 0) }
                        .thenBy { it.loadLabel(packageManager).toString().lowercase() }
                )
                .toMutableList()

            recyclerView.layoutManager = if (isGridMode) {
                GridLayoutManager(this, 4)
            } else {
                LinearLayoutManager(this)
            }

            adapter = AppAdapter(this, appList, searchBox, isGridMode) // pass isGridMode
            recyclerView.adapter = adapter

            // Initialize AppSearchManager
            appSearchManager = AppSearchManager(
                packageManager,
                appList,
                fullAppList,
                adapter,
                searchBox,
                contactsList
            )
        }

        // Set visibility of search bar and voice search button based on focus mode
        if (appDockManager.getCurrentMode()) {
            searchBox.visibility = android.view.View.GONE
            voiceSearchButton.visibility = android.view.View.GONE
        } else {
            searchBox.visibility = android.view.View.VISIBLE
            voiceSearchButton.visibility = android.view.View.VISIBLE
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

    // Request usage stats permission
    private fun requestUsageStatsPermission() {
        if (!usageStatsManager.hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
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
                    contactsList = loadContacts()
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

    fun applyFocusMode(isFocusMode: Boolean) {
        if (isFocusMode) {
            // Filter out hidden apps
            val filteredApps = fullAppList.filter { app ->
                !appDockManager.isAppHiddenInFocusMode(app.activityInfo.packageName)
            }.toMutableList()

            appList.clear()
            appList.addAll(filteredApps)

            searchBox.visibility = android.view.View.GONE
            voiceSearchButton.visibility = android.view.View.GONE

        } else {
            // Restore all apps and sort by usage then alphabetically
            val prefs = getSharedPreferences("app_usage", Context.MODE_PRIVATE)
            val sortedApps = fullAppList.sortedWith(
                compareByDescending<ResolveInfo> {
                    sharedPreferences.getInt("usage_${it.activityInfo.packageName}", 0)
                }.thenBy {
                    it.loadLabel(packageManager).toString().lowercase()
                }
            )
            appList.clear()
            appList.addAll(sortedApps)

            searchBox.visibility = android.view.View.VISIBLE
            voiceSearchButton.visibility = android.view.View.VISIBLE
        }

        adapter.notifyDataSetChanged()

        // Update search manager with new app list
        appSearchManager = AppSearchManager(
            packageManager,
            appList,
            fullAppList,
            adapter,
            searchBox,
            contactsList
        )
    }

    private fun loadWeeklyUsageData() {
        if (usageStatsManager.hasUsageStatsPermission()) {
            val weeklyData = usageStatsManager.getWeeklyUsageData()
            weeklyUsageGraph.setUsageData(weeklyData)

            val appUsageData = usageStatsManager.getWeeklyAppUsageData()
            weeklyUsageGraph.setAppUsageData(appUsageData)
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

    private fun refreshUsageStats() {
        // Refresh adapter usage data
        adapter.notifyDataSetChanged()

        // Refresh weekly usage graph
        loadWeeklyUsageData()
    }

    private fun setupWeather() {
        val weatherIcon = findViewById<ImageView>(R.id.weather_icon)
        val weatherText = findViewById<TextView>(R.id.weather_text)

        weatherManager.updateWeather(weatherIcon, weatherText)
    }

    private fun showWeatherSettings() {
        val prefs = getSharedPreferences("com.guruswarupa.launch.PREFS", MODE_PRIVATE)
        val currentApiKey = prefs.getString("weather_api_key", "")

        val builder = AlertDialog.Builder(this)
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

    private fun setupQuickNoteAutoSave() {
        quickNoteText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                // Auto-save after user stops typing for 1 second
                quickNoteText.removeCallbacks(saveNoteRunnable)
                quickNoteText.postDelayed(saveNoteRunnable, 1000)
            }
        })
    }

    private val saveNoteRunnable = Runnable {
        saveQuickNote()
    }

    private fun loadQuickNote() {
        val savedNote = sharedPreferences.getString("quick_note", "")
        quickNoteText.setText(savedNote)
    }

    private fun saveQuickNote() {
        val noteText = quickNoteText.text.toString()
        sharedPreferences.edit().putString("quick_note", noteText).apply()
    }

    private fun showTransactionHistory() {
        val transactions = financeManager.getTransactionHistory()

        if (transactions.isEmpty()) {
            Toast.makeText(this, "No transactions found", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBuilder = android.app.AlertDialog.Builder(this)
        dialogBuilder.setTitle("Transaction History")

        val transactionList = transactions.take(20).map { (type, amount, description) ->
            val typeText = if (type == "income") "Income" else "Expense"
            val symbol = if (type == "income") "+" else "-"
            val descText = if (description.isNotEmpty()) " - $description" else ""
            "$symbol$amount ($typeText)$descText"
        }.toTypedArray()

        dialogBuilder.setItems(transactionList) { _, _ -> }
        dialogBuilder.setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }

        dialogBuilder.create().show()
    }

    private fun setupFinanceWidget() {
        findViewById<Button>(R.id.add_income_btn).setOnClickListener {
            addTransaction(true)
        }

        findViewById<Button>(R.id.add_expense_btn).setOnClickListener {
            addTransaction(false)
        }

        // Long press on balance text to show transaction history
        balanceText.setOnLongClickListener {
            showTransactionHistory()
            true
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

                val action = if (isIncome) "Income" else "Expense"
                val message = if (description.isNotEmpty()) {
                    "$action of ₹$amount added: $description"
                } else {
                    "$action of ₹$amount added"
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
        balanceText.text = "Balance: ₹${financeManager.getBalance()}"
        monthlySpentText.text = "Monthly Spent: ₹${financeManager.getMonthlyExpenses()}"
    }
}