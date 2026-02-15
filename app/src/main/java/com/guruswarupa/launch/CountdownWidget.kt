package com.guruswarupa.launch

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

data class CountdownItem(
    val id: String,
    val title: String,
    val targetTime: Long,
    val isFromCalendar: Boolean = false,
    val calendarEventId: Long? = null
) {
    fun getRemainingTime(): Long {
        val now = System.currentTimeMillis()
        return targetTime - now
    }
    
    fun isExpired(): Boolean {
        return getRemainingTime() <= 0
    }
    
    fun formatRemainingTime(): String {
        val remaining = getRemainingTime()
        if (remaining <= 0) {
            return "Expired"
        }
        
        val days = remaining / (24 * 60 * 60 * 1000)
        val hours = (remaining % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        val minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000)
        val seconds = (remaining % (60 * 1000)) / 1000
        
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("targetTime", targetTime)
            put("isFromCalendar", isFromCalendar)
            if (calendarEventId != null) {
                put("calendarEventId", calendarEventId)
            }
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): CountdownItem {
            return CountdownItem(
                id = json.getString("id"),
                title = json.getString("title"),
                targetTime = json.getLong("targetTime"),
                isFromCalendar = json.optBoolean("isFromCalendar", false),
                calendarEventId = if (json.has("calendarEventId")) json.getLong("calendarEventId") else null
            )
        }
    }
}

class CountdownWidget(
    private val context: Context,
    private val container: LinearLayout,
    private val sharedPreferences: android.content.SharedPreferences
) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var countdownsRecyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var addButton: Button
    private lateinit var widgetContainer: LinearLayout
    private lateinit var widgetView: View
    
    private val countdowns: MutableList<CountdownItem> = mutableListOf()
    private lateinit var adapter: CountdownAdapter
    
    companion object {
        private const val PREFS_COUNTDOWNS_KEY = "countdown_widget_items"
        const val REQUEST_CODE_CALENDAR_PERMISSION = 107
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized) {
                updateCountdowns()
                // Update every second for active countdowns
                handler.postDelayed(this, 1000)
            }
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.countdown_widget, container, false)
        container.addView(widgetView)
        
        countdownsRecyclerView = widgetView.findViewById(R.id.countdowns_recycler_view)
        emptyState = widgetView.findViewById(R.id.countdown_empty_state)
        addButton = widgetView.findViewById(R.id.add_countdown_button)
        widgetContainer = widgetView.findViewById(R.id.countdown_widget_container)
        
        adapter = CountdownAdapter(countdowns, 
            onCountdownClick = { countdown ->
                // Only allow editing custom countdowns on click
                if (!countdown.isFromCalendar) {
                    showEditCountdownDialog(countdown)
                }
            },
            onCountdownLongPress = { countdown ->
                showCountdownOptionsDialog(countdown)
            }
        )
        countdownsRecyclerView.layoutManager = LinearLayoutManager(context)
        countdownsRecyclerView.adapter = adapter
        
        // Enable swipe to delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val countdown = countdowns[position]
                    deleteCountdown(countdown)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(countdownsRecyclerView)
        
        addButton.setOnClickListener {
            showAddCountdownDialog()
        }
        
        loadCountdowns()
        updateCountdowns()
        
        isInitialized = true
        handler.post(updateRunnable)
    }
    
    private fun loadCountdowns() {
        try {
            val countdownsJson = sharedPreferences.getString(PREFS_COUNTDOWNS_KEY, null)
            if (countdownsJson != null) {
                val jsonArray = JSONArray(countdownsJson)
                countdowns.clear()
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    countdowns.add(CountdownItem.fromJson(json))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveCountdowns() {
        try {
            val jsonArray = JSONArray()
            countdowns.forEach { countdown ->
                jsonArray.put(countdown.toJson())
            }
            sharedPreferences.edit {
                putString(PREFS_COUNTDOWNS_KEY, jsonArray.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun addCountdown(countdown: CountdownItem) {
        countdowns.add(countdown)
        saveCountdowns()
        updateCountdowns()
    }
    
    fun updateCountdown(countdown: CountdownItem) {
        val index = countdowns.indexOfFirst { it.id == countdown.id }
        if (index != -1) {
            countdowns[index] = countdown
            saveCountdowns()
            updateCountdowns()
        }
    }
    
    fun deleteCountdown(countdown: CountdownItem) {
        countdowns.removeAll { it.id == countdown.id }
        saveCountdowns()
        updateCountdowns()
    }
    
    fun showCountdownOptionsDialog(countdown: CountdownItem) {
        val options = mutableListOf<String>()
        
        // Only show edit option for custom countdowns
        if (!countdown.isFromCalendar) {
            options.add("Edit")
        }
        options.add("Delete")
        
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(countdown.title)
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    "Edit" -> {
                        if (!countdown.isFromCalendar) {
                            showEditCountdownDialog(countdown)
                        }
                    }
                    "Delete" -> {
                        AlertDialog.Builder(context, R.style.CustomDialogTheme)
                            .setTitle("Delete Countdown")
                            .setMessage("Are you sure you want to delete \"${countdown.title}\"?")
                            .setPositiveButton("Delete") { _, _ ->
                                deleteCountdown(countdown)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        // Fix dialog text colors for dark mode consistency
        fixDialogTextColors(dialog)
    }
    
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(context, R.color.text)
            val listView = dialog.listView
            listView?.post {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(textColor)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    
    @SuppressLint("NotifyDataSetChanged")
    private fun updateCountdowns() {
        // Remove expired countdowns
        val expiredCountdowns = countdowns.filter { it.isExpired() }
        countdowns.removeAll(expiredCountdowns)
        if (expiredCountdowns.isNotEmpty()) {
            saveCountdowns()
        }
        
        adapter.notifyDataSetChanged()
        
        if (countdowns.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            countdownsRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            countdownsRecyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun showAddCountdownDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.countdown_config_dialog, null)
        val titleInput: EditText = dialogView.findViewById(R.id.countdown_title_input)
        val dateInput: EditText = dialogView.findViewById(R.id.countdown_date_input)
        val timeInput: EditText = dialogView.findViewById(R.id.countdown_time_input)
        val fromCalendarButton: Button = dialogView.findViewById(R.id.from_calendar_button)
        
        // Fix input colors
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        titleInput.setTextColor(textColor)
        titleInput.setHintTextColor(secondaryTextColor)
        dateInput.setTextColor(textColor)
        dateInput.setHintTextColor(secondaryTextColor)
        timeInput.setTextColor(textColor)
        timeInput.setHintTextColor(secondaryTextColor)
        
        // Set up date picker
        val calendar = Calendar.getInstance()
        dateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            
            android.app.DatePickerDialog(
                context,
                { _, selectedYear, selectedMonth, selectedDay ->
                    calendar.set(selectedYear, selectedMonth, selectedDay)
                    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    dateInput.setText(dateFormat.format(calendar.time))
                },
                year, month, day
            ).show()
        }
        
        // Set up time picker
        timeInput.setOnClickListener {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            android.app.TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                    calendar.set(Calendar.MINUTE, selectedMinute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    timeInput.setText(timeFormat.format(calendar.time))
                },
                hour, minute, false
            ).show()
        }
        
        // Set default date/time to now
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        dateInput.setText(dateFormat.format(calendar.time))
        timeInput.setText(timeFormat.format(calendar.time))
        
        val addDialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Add Countdown")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val targetTime = calendar.timeInMillis
                if (targetTime <= System.currentTimeMillis()) {
                    Toast.makeText(context, "Please select a future date and time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val countdown = CountdownItem(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    targetTime = targetTime,
                    isFromCalendar = false
                )
                addCountdown(countdown)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        fromCalendarButton.setOnClickListener {
            addDialog.dismiss()
            showCalendarEventPicker()
        }
        
        addDialog.show()
    }
    
    private fun showEditCountdownDialog(countdown: CountdownItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.countdown_config_dialog, null)
        val titleInput: EditText = dialogView.findViewById(R.id.countdown_title_input)
        val dateInput: EditText = dialogView.findViewById(R.id.countdown_date_input)
        val timeInput: EditText = dialogView.findViewById(R.id.countdown_time_input)
        val fromCalendarButton: Button = dialogView.findViewById(R.id.from_calendar_button)
        
        // Fix input colors
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        titleInput.setTextColor(textColor)
        titleInput.setHintTextColor(secondaryTextColor)
        dateInput.setTextColor(textColor)
        dateInput.setHintTextColor(secondaryTextColor)
        timeInput.setTextColor(textColor)
        timeInput.setHintTextColor(secondaryTextColor)
        
        titleInput.setText(countdown.title)
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = countdown.targetTime
        }
        
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        dateInput.setText(dateFormat.format(calendar.time))
        timeInput.setText(timeFormat.format(calendar.time))
        
        // Set up date picker
        dateInput.setOnClickListener {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            
            android.app.DatePickerDialog(
                context,
                { _, selectedYear, selectedMonth, selectedDay ->
                    calendar.set(selectedYear, selectedMonth, selectedDay)
                    dateInput.setText(dateFormat.format(calendar.time))
                },
                year, month, day
            ).show()
        }
        
        // Set up time picker
        timeInput.setOnClickListener {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            
            android.app.TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                    calendar.set(Calendar.MINUTE, selectedMinute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    timeInput.setText(timeFormat.format(calendar.time))
                },
                hour, minute, false
            ).show()
        }
        
        val editDialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Edit Countdown")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val targetTime = calendar.timeInMillis
                if (targetTime <= System.currentTimeMillis()) {
                    Toast.makeText(context, "Please select a future date and time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val updatedCountdown = countdown.copy(
                    title = title,
                    targetTime = targetTime
                )
                updateCountdown(updatedCountdown)
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        fromCalendarButton.setOnClickListener {
            editDialog.dismiss()
            showCalendarEventPicker()
        }
        
        editDialog.show()
    }
    
    private fun showCalendarEventPicker() {
        if (!hasCalendarPermission()) {
            requestCalendarPermission()
            return
        }
        
        val events = loadUpcomingCalendarEvents()
        if (events.isEmpty()) {
            Toast.makeText(context, "No upcoming calendar events found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val eventTitles = events.map { event ->
            val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            "${event.title} - ${dateFormat.format(Date(event.startTime))}"
        }
        
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Select Calendar Event")
            .setItems(eventTitles.toTypedArray()) { _, which ->
                val selectedEvent = events[which]
                val countdown = CountdownItem(
                    id = UUID.randomUUID().toString(),
                    title = selectedEvent.title,
                    targetTime = selectedEvent.startTime,
                    isFromCalendar = true,
                    calendarEventId = selectedEvent.id
                )
                addCountdown(countdown)
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        // Fix dialog text colors
        fixDialogTextColors(dialog)
    }
    
    private fun loadUpcomingCalendarEvents(): List<CalendarEvent> {
        if (!hasCalendarPermission()) {
            return emptyList()
        }
        
        val eventsList = mutableListOf<CalendarEvent>()
        val seenEvents = mutableSetOf<String>() // For deduplication
        
        val now = System.currentTimeMillis()
        val oneYearFromNow = now + (365 * 24 * 60 * 60 * 1000L)
        
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )
        
        val uri: Uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(oneYearFromNow.toString())
            .build()
        
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
            
            cursor?.let {
                val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val startIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
                val locationIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val allDayIndex = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calendarIdIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)

                while (it.moveToNext()) {
                    val eventId = it.getLong(idIndex)
                    val title = it.getString(titleIndex) ?: "No Title"
                    var startTime = it.getLong(startIndex)
                    var endTime = it.getLong(endIndex)
                    val location = it.getString(locationIndex)
                    val allDay = it.getInt(allDayIndex) == 1
                    val calendarId = it.getLong(calendarIdIndex)

                    // Only include future events
                    if (startTime < now) continue
                    
                    // Handle all-day events
                    if (allDay) {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = startTime
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        startTime = calendar.timeInMillis
                        
                        // End time is start of next day
                        calendar.add(Calendar.DAY_OF_MONTH, 1)
                        endTime = calendar.timeInMillis
                    }
                    
                    // Create unique key for deduplication (title + date)
                    // Festivals often appear in multiple calendars with different event IDs
                    // So we deduplicate by title + date instead of event ID
                    val cal = Calendar.getInstance().apply { timeInMillis = startTime }
                    val dateKey = "${title.lowercase().trim()}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}_${cal.get(Calendar.DAY_OF_MONTH)}"
                    
                    // Skip if we've already seen this exact event (same title on same date)
                    if (!seenEvents.contains(dateKey)) {
                        seenEvents.add(dateKey)
                        eventsList.add(
                            CalendarEvent(
                                id = eventId,
                                title = title,
                                startTime = startTime,
                                endTime = endTime,
                                location = location,
                                allDay = allDay,
                                calendarId = calendarId
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission denied
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        
        // Return all events (no limit) - showing all events in a year
        return eventsList
    }
    
    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCalendarPermission() {
        if (context is android.app.Activity) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALENDAR
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle("Calendar Permission")
                    .setMessage("This permission allows you to create countdowns from your calendar events.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        if (context is androidx.fragment.app.FragmentActivity) {
                            androidx.core.app.ActivityCompat.requestPermissions(
                                context,
                                arrayOf(Manifest.permission.READ_CALENDAR),
                                REQUEST_CODE_CALENDAR_PERMISSION
                            )
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                
                fixDialogTextColors(dialog)
            }
        }
    }
    
    fun onResume() {
        if (isInitialized) {
            handler.post(updateRunnable)
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
        }
    }
    
    fun onPermissionGranted() {
        // Refresh if needed
    }
    
    fun refresh() {
        if (isInitialized) {
            updateCountdowns()
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
    }
}

class CountdownAdapter(
    private val countdowns: List<CountdownItem>,
    private val onCountdownClick: (CountdownItem) -> Unit,
    private val onCountdownLongPress: (CountdownItem) -> Unit
) : RecyclerView.Adapter<CountdownAdapter.CountdownViewHolder>() {
    
    class CountdownViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.countdown_title)
        val timeText: TextView = itemView.findViewById(R.id.countdown_time)
        val remainingText: TextView = itemView.findViewById(R.id.countdown_remaining)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountdownViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.countdown_item, parent, false)
        return CountdownViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CountdownViewHolder, position: Int) {
        val countdown = countdowns[position]
        
        holder.titleText.text = countdown.title
        
        val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
        holder.timeText.text = dateFormat.format(Date(countdown.targetTime))
        
        holder.remainingText.text = countdown.formatRemainingTime()
        
        // Change color if expired
        val color = if (countdown.isExpired()) {
            ContextCompat.getColor(holder.itemView.context, R.color.nord11) // Red
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.nord9) // Blue
        }
        holder.remainingText.setTextColor(color)
        
        holder.itemView.setOnClickListener {
            onCountdownClick(countdown)
        }
        
        holder.itemView.setOnLongClickListener {
            onCountdownLongPress(countdown)
            true
        }
    }
    
    override fun getItemCount() = countdowns.size
}
