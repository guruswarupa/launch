package com.guruswarupa.launch.ui.views

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.models.WorkoutExercise
import java.text.SimpleDateFormat
import java.util.*
import com.guruswarupa.launch.R

class WorkoutCalendarView(
    rootView: View,
    exercises: List<WorkoutExercise>,
    onDayClick: ((String, List<Pair<WorkoutExercise, Int>>) -> Unit)? = null
) {
    private val context: Context = rootView.context
    private val calendarRecyclerView: RecyclerView = rootView.findViewById(R.id.calendar_recycler_view)
    private val monthYearText: TextView = rootView.findViewById(R.id.month_year_text)
    private val prevMonthButton: View = rootView.findViewById(R.id.prev_month_button)
    private val nextMonthButton: View = rootView.findViewById(R.id.next_month_button)
    
    private var currentCalendar = Calendar.getInstance()
    private val adapter = CalendarAdapter(currentCalendar, exercises, onDayClick)
    
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
    
    fun updateExercises(newExercises: List<WorkoutExercise>) {
        adapter.updateExercises(newExercises)
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

class CalendarAdapter(
    private var calendar: Calendar,
    private var exercises: List<WorkoutExercise>,
    private val onDayClick: ((String, List<Pair<WorkoutExercise, Int>>) -> Unit)? = null
) : RecyclerView.Adapter<CalendarAdapter.DayViewHolder>() {
    
    private val days = mutableListOf<DayItem>()
    
    init {
        updateCalendar(calendar)
    }
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateCalendar(newCalendar: Calendar) {
        calendar = newCalendar.clone() as Calendar
        updateDays()
        notifyDataSetChanged()
    }
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateExercises(newExercises: List<WorkoutExercise>) {
        exercises = newExercises
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
        // We want Sunday to be first column (index 0)
        val startOffset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
        repeat(startOffset) {
            days.add(DayItem(day = null, hasWorkout = false, isToday = false, dateString = null))
        }
        
        // Get all workout dates from all exercises
        val allWorkoutDates = exercises.flatMap { it.workoutDates }.toSet()
        
        // Add days of month
        val currentDate = Calendar.getInstance()
        val today = currentDate.get(Calendar.DAY_OF_MONTH)
        val currentMonth = currentDate.get(Calendar.MONTH)
        val currentYear = currentDate.get(Calendar.YEAR)
        
        for (day in 1..daysInMonth) {
            val dateStr = formatDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, day)
            val hasWorkout = allWorkoutDates.contains(dateStr)
            val isToday = day == today && 
                          calendar.get(Calendar.MONTH) == currentMonth && 
                          calendar.get(Calendar.YEAR) == currentYear
            
            days.add(DayItem(day, hasWorkout, isToday, dateStr))
        }
    }
    
    private fun formatDate(year: Int, month: Int, day: Int): String {
        return String.format(Locale.getDefault(), "%d-%d-%d", year, month, day)
    }
    
    data class DayItem(
        val day: Int?,
        val hasWorkout: Boolean = false,
        val isToday: Boolean = false,
        val dateString: String? = null
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
            
            // Show workout indicator
            if (dayItem.hasWorkout) {
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
            
            // Make clickable if it has workout data
            if (dayItem.hasWorkout && dayItem.dateString != null) {
                holder.itemView.setOnClickListener {
                    val dateStr = dayItem.dateString
                    val dayExercises = exercises.mapNotNull { exercise ->
                        val count = exercise.getCountForDate(dateStr)
                        if (count > 0) {
                            exercise to count
                        } else null
                    }
                    onDayClick?.invoke(dateStr, dayExercises)
                }
            } else {
                holder.itemView.setOnClickListener(null)
            }
        }
    }
    
    override fun getItemCount() = days.size
}
