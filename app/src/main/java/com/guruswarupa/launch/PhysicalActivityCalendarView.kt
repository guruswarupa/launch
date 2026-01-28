package com.guruswarupa.launch

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class PhysicalActivityCalendarView(
    private val rootView: View,
    private val activityManager: PhysicalActivityManager,
    private val onDayClick: ((String, ActivityData) -> Unit)? = null
) {
    private val context: Context = rootView.context
    private val calendarRecyclerView: RecyclerView = rootView.findViewById(R.id.calendar_recycler_view)
    private val monthYearText: TextView = rootView.findViewById(R.id.month_year_text)
    private val prevMonthButton: View = rootView.findViewById(R.id.prev_month_button)
    private val nextMonthButton: View = rootView.findViewById(R.id.next_month_button)
    
    private var currentCalendar = Calendar.getInstance()
    private val adapter = ActivityCalendarAdapter(currentCalendar, activityManager, onDayClick)
    
    init {
        calendarRecyclerView.layoutManager = GridLayoutManager(context, 7)
        calendarRecyclerView.adapter = adapter
        
        prevMonthButton.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateCalendar()
        }
        
        nextMonthButton.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateCalendar()
        }
        
        updateCalendar()
    }
    
    fun refreshData() {
        adapter.refreshData()
    }
    
    private fun updateCalendar() {
        adapter.updateCalendar(currentCalendar)
        updateMonthYearText()
    }
    
    private fun updateMonthYearText() {
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthYearText.text = monthYearFormat.format(currentCalendar.time)
    }
}

class ActivityCalendarAdapter(
    private var calendar: Calendar,
    private val activityManager: PhysicalActivityManager,
    private val onDayClick: ((String, ActivityData) -> Unit)? = null
) : RecyclerView.Adapter<ActivityCalendarAdapter.DayViewHolder>() {
    
    private val days = mutableListOf<DayItem>()
    private var monthlyData: Map<String, ActivityData> = emptyMap()
    
    init {
        updateCalendar(calendar)
    }
    
    fun updateCalendar(newCalendar: Calendar) {
        calendar = newCalendar.clone() as Calendar
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        monthlyData = activityManager.getMonthlyActivity(year, month)
        updateDays()
        notifyDataSetChanged()
    }
    
    fun refreshData() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        monthlyData = activityManager.getMonthlyActivity(year, month)
        updateDays()
        notifyDataSetChanged()
    }
    
    private fun updateDays() {
        days.clear()
        
        // Get first day of month
        val firstDayOfMonth = calendar.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
        
        // Get number of days in month
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Calculate offset - Sunday = 1, Monday = 2, etc.
        val startOffset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
        for (i in 0 until startOffset) {
            days.add(DayItem(null, false, false, null, null))
        }
        
        // Add days of month
        val currentDate = Calendar.getInstance()
        val today = currentDate.get(Calendar.DAY_OF_MONTH)
        val currentMonth = currentDate.get(Calendar.MONTH)
        val currentYear = currentDate.get(Calendar.YEAR)
        
        for (day in 1..daysInMonth) {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val dateStr = String.format(Locale.getDefault(), "%d-%02d-%02d", year, month, day)
            val activityData = monthlyData[dateStr] ?: ActivityData(0, 0.0, dateStr)
            val hasActivity = activityData.steps > 0
            val isToday = day == today && 
                          calendar.get(Calendar.MONTH) == currentMonth && 
                          calendar.get(Calendar.YEAR) == currentYear
            
            days.add(DayItem(day, hasActivity, isToday, dateStr, activityData))
        }
    }
    
    data class DayItem(
        val day: Int?,
        val hasActivity: Boolean = false,
        val isToday: Boolean = false,
        val dateString: String? = null,
        val activityData: ActivityData? = null
    )
    
    class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayText: TextView = itemView.findViewById(R.id.calendar_day_text)
        val workoutIndicator: View = itemView.findViewById(R.id.workout_indicator)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.calendar_day_item, parent, false)
        return DayViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayItem = days[position]
        
        if (dayItem.day == null) {
            // Empty cell
            holder.dayText.text = ""
            holder.dayText.visibility = View.INVISIBLE
            holder.workoutIndicator.visibility = View.GONE
            holder.itemView.background = null
            holder.itemView.setOnClickListener(null)
        } else {
            holder.dayText.text = dayItem.day.toString()
            holder.dayText.visibility = View.VISIBLE
            
            // Show activity indicator
            if (dayItem.hasActivity) {
                holder.workoutIndicator.visibility = View.VISIBLE
            } else {
                holder.workoutIndicator.visibility = View.GONE
            }
            
            // Highlight today with a nice colored border
            if (dayItem.isToday) {
                holder.itemView.setBackgroundResource(R.drawable.today_highlight)
            } else {
                holder.itemView.background = null
            }
            
            // Make clickable if it has activity data
            if (dayItem.hasActivity && dayItem.dateString != null && dayItem.activityData != null) {
                holder.itemView.setOnClickListener {
                    onDayClick?.invoke(dayItem.dateString, dayItem.activityData)
                }
            } else {
                holder.itemView.setOnClickListener(null)
            }
        }
    }
    
    override fun getItemCount() = days.size
}
