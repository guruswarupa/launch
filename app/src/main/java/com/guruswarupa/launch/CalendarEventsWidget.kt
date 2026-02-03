package com.guruswarupa.launch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String?,
    val allDay: Boolean,
    val calendarId: Long,
    val isFestival: Boolean = false
)

class CalendarEventsWidget(
    private val context: Context,
    private val container: LinearLayout,
    @Suppress("UNUSED_PARAMETER") private val sharedPreferences: android.content.SharedPreferences
) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var isInitialized = false
    
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var permissionButton: Button
    @Suppress("unused")
    private lateinit var widgetContainer: LinearLayout
    private lateinit var widgetView: View
    private lateinit var viewToggleButton: Button
    private lateinit var listViewContainer: View
    private lateinit var calendarViewContainer: ViewGroup
    
    private val events: MutableList<CalendarEvent> = mutableListOf()
    private val allEvents: MutableList<CalendarEvent> = mutableListOf() // All events for calendar view
    private lateinit var adapter: CalendarEventAdapter
    private var isCalendarView = false
    private var calendarView: CalendarEventsCalendarView? = null
    
    companion object {
        const val REQUEST_CODE_CALENDAR_PERMISSION = 106
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isInitialized && hasCalendarPermission()) {
                updateEvents()
            }
            // Update every 5 minutes
            handler.postDelayed(this, 300000)
        }
    }
    
    fun initialize() {
        if (isInitialized) return
        
        val inflater = LayoutInflater.from(context)
        widgetView = inflater.inflate(R.layout.calendar_events_widget, container, false)
        container.addView(widgetView)
        
        eventsRecyclerView = widgetView.findViewById(R.id.calendar_events_recycler_view)
        emptyState = widgetView.findViewById(R.id.calendar_empty_state)
        permissionButton = widgetView.findViewById(R.id.request_calendar_permission_button)
        widgetContainer = widgetView.findViewById(R.id.calendar_widget_container)
        viewToggleButton = widgetView.findViewById(R.id.calendar_view_toggle_button)
        listViewContainer = widgetView.findViewById(R.id.calendar_list_view_container)
        calendarViewContainer = widgetView.findViewById(R.id.calendar_view_container)
        
        adapter = CalendarEventAdapter(events) { _ ->
            openCalendarApp()
        }
        eventsRecyclerView.layoutManager = LinearLayoutManager(context)
        eventsRecyclerView.adapter = adapter
        
        permissionButton.setOnClickListener {
            requestCalendarPermission()
        }
        
        viewToggleButton.setOnClickListener {
            toggleView()
        }
        
        // Initialize calendar view
        initializeCalendarView()
        
        // Widget visibility is controlled by the widget configuration system
        // Always show content when initialized (container visibility is controlled externally)
        if (hasCalendarPermission()) {
            setupWithPermission()
        } else {
            setupWithoutPermission()
        }
        
        isInitialized = true
    }
    
    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.calendar_events_calendar_view, calendarViewContainer, false)
        calendarViewContainer.addView(calendarViewLayout)
        calendarView = CalendarEventsCalendarView(calendarViewLayout, allEvents) { date, dayEvents ->
            showDayEventDetails(date, dayEvents)
        }
    }
    
    private fun showDayEventDetails(date: String, dayEvents: List<CalendarEvent>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        
        val parsedDate = try {
            dateFormat.parse(date)
        } catch (_: Exception) {
            null
        }
        
        val displayDate = parsedDate?.let { displayFormat.format(it) } ?: date
        
        // Deduplicate events by title and date to avoid showing same event multiple times
        // Festivals often appear in multiple calendars (Google, Samsung, etc.) with different event IDs
        // So we deduplicate by title + date instead of event ID
        val uniqueEvents = dayEvents.distinctBy { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.startTime }
            // Use title + date as unique key (normalize title to handle case differences)
            "${event.title.lowercase().trim()}_${cal.get(Calendar.YEAR)}_${cal.get(Calendar.MONTH)}_${cal.get(Calendar.DAY_OF_MONTH)}"
        }
        
        val dialogView = LayoutInflater.from(context).inflate(R.layout.calendar_day_events_dialog, null)
        val dateText = dialogView.findViewById<TextView>(R.id.day_date_text)
        val eventsList = dialogView.findViewById<RecyclerView>(R.id.day_events_list)
        val emptyStateView = dialogView.findViewById<View>(R.id.day_empty_state)
        
        dateText.text = displayDate
        
        if (uniqueEvents.isEmpty()) {
            eventsList.visibility = View.GONE
            emptyStateView.visibility = View.VISIBLE
        } else {
            eventsList.visibility = View.VISIBLE
            emptyStateView.visibility = View.GONE
            
            eventsList.layoutManager = LinearLayoutManager(context)
            eventsList.adapter = CalendarEventAdapter(uniqueEvents) { _ ->
                openCalendarApp()
            }
        }
        
        android.app.AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Events")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }
    
    private fun toggleView() {
        isCalendarView = !isCalendarView
        
        if (isCalendarView) {
            // Show calendar, hide list
            listViewContainer.visibility = View.GONE
            calendarViewContainer.visibility = View.VISIBLE
            viewToggleButton.text = "List"
            calendarView?.updateEvents(allEvents)
        } else {
            // Show list, hide calendar
            listViewContainer.visibility = View.VISIBLE
            calendarViewContainer.visibility = View.GONE
            viewToggleButton.text = "Calendar"
        }
    }
    
    private fun setupWithPermission() {
        permissionButton.visibility = View.GONE
        updateEvents()
        handler.post(updateRunnable)
    }
    
    private fun setupWithoutPermission() {
        permissionButton.visibility = View.VISIBLE
        emptyState.visibility = View.VISIBLE
        eventsRecyclerView.visibility = View.GONE
        events.clear()
        adapter.notifyDataSetChanged()
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
                // Show explanation dialog
                android.app.AlertDialog.Builder(context, R.style.CustomDialogTheme)
                    .setTitle("Calendar Permission")
                    .setMessage("This permission allows the launcher to display your upcoming calendar events. The data is only used locally on your device.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        // Permission request will be handled by the activity
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
            }
        } else {
            // If not an activity, open settings
            openSettings()
        }
    }
    
    private fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateEvents() {
        if (!hasCalendarPermission()) {
            setupWithoutPermission()
            return
        }
        
        try {
            // Load all events for calendar view (next 3 months) - this includes everything
            val allNewEvents = loadAllEvents()
            allEvents.clear()
            allEvents.addAll(allNewEvents)
            
            // Filter upcoming events for list view (next 2 days) from all events
            val now = System.currentTimeMillis()
            val twoDaysFromNow = now + (2 * 24 * 60 * 60 * 1000)
            val upcomingEvents = allNewEvents.filter { event ->
                event.startTime in now..twoDaysFromNow
            }.sortedBy { it.startTime }.take(5)
            
            events.clear()
            events.addAll(upcomingEvents)
            adapter.notifyDataSetChanged()
            
            // Update calendar view
            calendarView?.updateEvents(allEvents)
            
            if (events.isEmpty() && !isCalendarView) {
                emptyState.visibility = View.VISIBLE
                eventsRecyclerView.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                if (!isCalendarView) {
                    eventsRecyclerView.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error loading calendar events: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun loadAllEvents(): List<CalendarEvent> {
        val eventsList = mutableListOf<CalendarEvent>()
        val seenEvents = mutableSetOf<String>() // For deduplication
        
        // Use Instances table to properly handle recurring events
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
        
        val now = System.currentTimeMillis()
        // Load events from 1 year ago to 2 years ahead to show all festivals and events
        val oneYearAgo = now - (365 * 24 * 60 * 60 * 1000L) // 1 year ago
        val twoYearsFromNow = now + (730 * 24 * 60 * 60 * 1000L) // 2 years in milliseconds
        
        // When using Instances URI with appendPath, time filtering is already done
        // No need for selection/selectionArgs for time range
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"
        
        val uri: Uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(oneYearAgo.toString())
            .appendPath(twoYearsFromNow.toString())
            .build()
        
        var cursor: Cursor? = null
        try {
            // Query all calendars including birthdays and custom calendars
            cursor = context.contentResolver.query(
                uri,
                projection,
                null, // No selection - get all events from all calendars
                null, // No selection args
                sortOrder
            )
            
            cursor?.let {
                val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val startIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
                val locationIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val allDayIndex = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calendarIdIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val calendarDisplayNameIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                
                while (it.moveToNext()) {
                    val eventId = it.getLong(idIndex)
                    val title = it.getString(titleIndex) ?: "No Title"
                    var startTime = it.getLong(startIndex)
                    var endTime = it.getLong(endIndex)
                    val location = it.getString(locationIndex)
                    val allDay = it.getInt(allDayIndex) == 1
                    val calendarId = it.getLong(calendarIdIndex)
                    val calendarDisplayName = it.getString(calendarDisplayNameIndex) ?: ""
                    
                    // Detect festivals - check if calendar name contains festival/holiday keywords
                    val isFestival = calendarDisplayName.lowercase().let { name ->
                        name.contains("festival") || name.contains("holiday") || 
                        name.contains("holidays") || name.contains("festivals") ||
                        name.contains("religious") || name.contains("indian") ||
                        name.contains("hindu") || name.contains("muslim") ||
                        name.contains("christian") || name.contains("national")
                    }
                    
                    // Handle all-day events (birthdays)
                    // Instances table returns all-day events in local timezone already
                    // Just ensure they're at midnight for consistent display
                    if (allDay) {
                        // All-day events from Instances are already in local timezone
                        // Just normalize to start of day (midnight) for consistent display
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = startTime
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        startTime = cal.timeInMillis
                        
                        // End time is start of next day
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        endTime = cal.timeInMillis
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
                                calendarId = calendarId,
                                isFestival = isFestival
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission denied
            setupWithoutPermission()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        
        return eventsList
    }
    
    private fun openCalendarApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "time/epoch"
            }
            val calendarIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            val resolvedIntent = calendarIntent.resolveActivity(context.packageManager)
            if (resolvedIntent != null) {
                context.startActivity(calendarIntent)
            } else {
                // Fallback to generic calendar view
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open calendar", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun onResume() {
        if (isInitialized && hasCalendarPermission()) {
            updateEvents()
            handler.post(updateRunnable)
        }
    }
    
    fun onPause() {
        if (isInitialized) {
            handler.removeCallbacks(updateRunnable)
        }
    }
    
    fun onPermissionGranted() {
        if (isInitialized) {
            setupWithPermission()
        }
    }
    
    fun refresh() {
        if (isInitialized) {
            if (hasCalendarPermission()) {
                updateEvents()
                if (isCalendarView) {
                    calendarView?.updateEvents(allEvents)
                }
            } else {
                setupWithoutPermission()
            }
        }
    }
    
    fun cleanup() {
        handler.removeCallbacks(updateRunnable)
    }
}

class CalendarEventAdapter(
    private val events: List<CalendarEvent>,
    private val onEventClick: (CalendarEvent) -> Unit
) : RecyclerView.Adapter<CalendarEventAdapter.EventViewHolder>() {
    
    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.event_title)
        val timeText: TextView = itemView.findViewById(R.id.event_time)
        val locationText: TextView = itemView.findViewById(R.id.event_location)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calendar_event_item, parent, false)
        return EventViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        
        holder.titleText.text = event.title
        
        // Set color: red for festivals, blue for others
        val titleColor = if (event.isFestival) {
            ContextCompat.getColor(holder.itemView.context, R.color.nord11) // Red
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.nord9) // Blue
        }
        holder.titleText.setTextColor(titleColor)
        
        val timeFormat = if (event.allDay) {
            SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        } else {
            SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        }
        
        val startTime = Date(event.startTime)
        val timeString = timeFormat.format(startTime)
        
        // Show relative time for today/tomorrow
        val now = Calendar.getInstance()
        val eventDate = Calendar.getInstance().apply { time = startTime }
        
        val timeDisplay = when {
            isSameDay(now, eventDate) -> {
                if (event.allDay) {
                    "Today (All Day)"
                } else {
                    val timeOnly = SimpleDateFormat("h:mm a", Locale.getDefault()).format(startTime)
                    "Today at $timeOnly"
                }
            }
            isTomorrow(now, eventDate) -> {
                if (event.allDay) {
                    "Tomorrow (All Day)"
                } else {
                    val timeOnly = SimpleDateFormat("h:mm a", Locale.getDefault()).format(startTime)
                    "Tomorrow at $timeOnly"
                }
            }
            else -> timeString
        }
        
        holder.timeText.text = timeDisplay
        
        if (!event.location.isNullOrEmpty()) {
            holder.locationText.text = event.location
            holder.locationText.visibility = View.VISIBLE
        } else {
            holder.locationText.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onEventClick(event)
        }
    }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun isTomorrow(cal1: Calendar, cal2: Calendar): Boolean {
        val tomorrow = cal1.clone() as Calendar
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        return tomorrow.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                tomorrow.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    
    override fun getItemCount() = events.size
}
