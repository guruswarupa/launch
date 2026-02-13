package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class WorkoutWidget(private val rootView: View) {
    private val context: Context = rootView.context
    private val prefs: SharedPreferences = context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
    private val exercisesRecyclerView: RecyclerView = rootView.findViewById(R.id.workout_exercises_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.workout_empty_state)
    private val addExerciseButton: View = rootView.findViewById(R.id.add_exercise_button)
    private val viewToggleButton: View = rootView.findViewById(R.id.view_toggle_button)
    private val statsContainer: View = rootView.findViewById(R.id.workout_stats_container)
    private val totalTodayText: TextView = rootView.findViewById(R.id.total_today_text)
    private val exercisesCountText: TextView = rootView.findViewById(R.id.exercises_count_text)
    private val streakText: TextView = rootView.findViewById(R.id.streak_text)
    private val weeklyTotalText: TextView = rootView.findViewById(R.id.weekly_total_text)
    private val listViewContainer: View = rootView.findViewById(R.id.list_view_container)
    private val calendarViewContainer: ViewGroup = rootView.findViewById(R.id.calendar_view_container)
    
    private val exercises: MutableList<WorkoutExercise> = mutableListOf()
    private val adapter = WorkoutAdapter(exercises, this) { exercise, amount ->
        incrementExerciseCount(exercise, amount)
    }
    
    private var isCalendarView = false
    private var calendarView: WorkoutCalendarView? = null
    
    companion object {
        private const val EXERCISES_KEY = "workout_exercises"
        private const val LAST_RESET_DATE_KEY = "workout_last_reset_date"
        private const val STREAK_KEY = "workout_streak"
        private const val LAST_STREAK_DATE_KEY = "workout_last_streak_date"
    }
    
    init {
        exercisesRecyclerView.layoutManager = LinearLayoutManager(context)
        exercisesRecyclerView.adapter = adapter
        
        // Setup swipe to delete and drag to reorder
        setupSwipeToDelete()
        setupDragToReorder()
        
        addExerciseButton.setOnClickListener {
            showAddExerciseDialog()
        }
        
        viewToggleButton.setOnClickListener {
            toggleView()
        }
        
        loadExercises()
        checkAndResetDailyCounts()
        initializeCalendarView()
        updateUI()
    }
    
    private fun initializeCalendarView() {
        val calendarViewLayout = LayoutInflater.from(context)
            .inflate(R.layout.workout_calendar_view, calendarViewContainer, false)
        calendarViewContainer.addView(calendarViewLayout)
        calendarView = WorkoutCalendarView(calendarViewLayout, exercises) { date, dayExercises ->
            showDayWorkoutDetails(date, dayExercises)
        }
    }
    
    private fun showDayWorkoutDetails(date: String, dayExercises: List<Pair<WorkoutExercise, Int>>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        
        val parsedDate = try {
            dateFormat.parse(date)
        } catch (e: Exception) {
            null
        }
        
        val displayDate = parsedDate?.let { displayFormat.format(it) } ?: date
        
        val dialogView = LayoutInflater.from(context).inflate(R.layout.workout_day_details, null)
        val dateText = dialogView.findViewById<TextView>(R.id.day_date_text)
        val exercisesList = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.day_events_list)
        val emptyState = dialogView.findViewById<View>(R.id.day_empty_state)
        
        dateText.text = displayDate
        
        if (dayExercises.isEmpty()) {
            exercisesList.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            exercisesList.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            
            exercisesList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            exercisesList.adapter = DayExercisesAdapter(dayExercises)
        }
        
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Workout Details")
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
            (viewToggleButton as android.widget.Button).text = "List"
            calendarView?.updateExercises(exercises)
        } else {
            // Show list, hide calendar
            listViewContainer.visibility = View.VISIBLE
            calendarViewContainer.visibility = View.GONE
            (viewToggleButton as android.widget.Button).text = "Calendar"
        }
    }
    
    private fun checkAndResetDailyCounts() {
        val today = getCurrentDate()
        val lastReset = prefs.getString(LAST_RESET_DATE_KEY, null)
        
        if (lastReset != today && lastReset != null) {
            // Reset all exercises for new day
            exercises.forEach { it.resetToday() }
            prefs.edit().putString(LAST_RESET_DATE_KEY, today).apply()
            saveExercises()
            adapter.notifyDataSetChanged()
            updateStats()
        } else if (lastReset == null) {
            // First time - just set the date
            prefs.edit().putString(LAST_RESET_DATE_KEY, today).apply()
        }
    }
    
    private fun getCurrentDate(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }
    
    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false
                }
                if (fromPos < toPos) {
                    for (i in fromPos until toPos) {
                        Collections.swap(exercises, i, i + 1)
                    }
                } else {
                    for (i in fromPos downTo toPos + 1) {
                        Collections.swap(exercises, i, i - 1)
                    }
                }
                adapter.notifyItemMoved(fromPos, toPos)
                saveExercises()
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION && position < exercises.size) {
                    val exercise = exercises[position]
                    showDeleteConfirmDialog(exercise) {
                        deleteExercise(exercise)
                    }
                    adapter.notifyItemChanged(position) // Restore view
                }
            }
            
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                    // Disable menu button during drag
                    if (viewHolder is WorkoutAdapter.WorkoutViewHolder) {
                        viewHolder.menuButton.isEnabled = false
                    }
                }
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                // Re-enable menu button after drag
                if (viewHolder is WorkoutAdapter.WorkoutViewHolder) {
                    viewHolder.menuButton.isEnabled = true
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(exercisesRecyclerView)
    }
    
    private fun setupDragToReorder() {
        // Already handled in setupSwipeToDelete with onMove
    }
    
    private fun showAddExerciseDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.workout_add_dialog, null)
        val customInput = dialogView.findViewById<EditText>(R.id.custom_exercise_input)
        val presetsContainer = dialogView.findViewById<ViewGroup>(R.id.presets_container)
        val typeReps = dialogView.findViewById<android.widget.RadioButton>(R.id.type_reps)
        val typeTime = dialogView.findViewById<android.widget.RadioButton>(R.id.type_time)
        
        // Fix input colors
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        customInput.setTextColor(textColor)
        customInput.setHintTextColor(secondaryTextColor)
        
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Add Exercise")
            .setView(dialogView)
            .setPositiveButton("Add Custom") { _, _ ->
                val exerciseName = customInput.text.toString().trim()
                if (exerciseName.isNotEmpty()) {
                    val type = if (typeTime.isChecked) ExerciseType.TIME else ExerciseType.REPS
                    addExercise(exerciseName, type)
                } else {
                    Toast.makeText(context, "Please enter an exercise name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        fun updatePresets() {
            presetsContainer.removeAllViews()
            val exerciseType = if (typeTime.isChecked) ExerciseType.TIME else ExerciseType.REPS
            val presets = WorkoutExercise.getPresets(exerciseType)
            
            presets.forEach { preset ->
                val presetButton = android.widget.Button(context).apply {
                    text = preset
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 8)
                    }
                    background = context.getDrawable(R.drawable.button_neutral_ripple)
                    setPadding(16, 12, 16, 12)
                    // Set preset button text color based on theme
                    setTextColor(textColor)
                    setOnClickListener {
                        val type = if (typeTime.isChecked) ExerciseType.TIME else ExerciseType.REPS
                        addExercise(preset, type)
                        dialog.dismiss()
                    }
                }
                presetsContainer.addView(presetButton)
            }
        }
        
        val typeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.exercise_type_group)
        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            updatePresets()
        }
        
        updatePresets()
        dialog.show()
    }
    
    private fun addExercise(name: String, type: ExerciseType = ExerciseType.REPS) {
        val exercise = WorkoutExercise(
            id = "exercise_${System.currentTimeMillis()}",
            name = name,
            type = type,
            todayCount = 0,
            totalCount = 0,
            bestDay = 0
        )
        exercises.add(exercise)
        saveExercises()
        updateUI()
        adapter.notifyItemInserted(exercises.size - 1)
        exercisesRecyclerView.smoothScrollToPosition(exercises.size - 1)
        // Update calendar view if visible
        if (isCalendarView) {
            calendarView?.updateExercises(exercises)
        }
    }
    
    private fun incrementExerciseCount(exercise: WorkoutExercise, amount: Int) {
        exercise.increment(amount)
        saveExercises()
        val index = exercises.indexOf(exercise)
        if (index != -1) {
            adapter.notifyItemChanged(index)
            updateStats()
            // Update calendar view if visible
            if (isCalendarView) {
                calendarView?.updateExercises(exercises)
            }
        }
    }
    
    fun deleteExercise(exercise: WorkoutExercise) {
        val index = exercises.indexOf(exercise)
        if (index != -1) {
            exercises.removeAt(index)
            saveExercises()
            updateUI()
            adapter.notifyItemRemoved(index)
            updateStats()
            // Update calendar view if visible
            if (isCalendarView) {
                calendarView?.updateExercises(exercises)
            }
        }
    }
    
    fun resetExerciseToday(exercise: WorkoutExercise) {
        exercise.resetToday()
        saveExercises()
        val index = exercises.indexOf(exercise)
        if (index != -1) {
            adapter.notifyItemChanged(index)
            updateStats()
        }
    }
    
    fun showExerciseOptions(exercise: WorkoutExercise) {
        val options = arrayOf("Reset Today", "Delete Exercise")
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle(exercise.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        AlertDialog.Builder(context, R.style.CustomDialogTheme)
                            .setTitle("Reset Today")
                            .setMessage("Reset today's count for ${exercise.name}?")
                            .setPositiveButton("Reset") { _, _ ->
                                resetExerciseToday(exercise)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    1 -> showDeleteConfirmDialog(exercise) {
                        deleteExercise(exercise)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        
        fixDialogTextColors(dialog)
    }
    
    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(context, R.color.text)
            val listView = dialog.listView
            if (listView != null) {
                listView.post {
                    for (i in 0 until listView.childCount) {
                        val child = listView.getChildAt(i)
                        if (child is TextView) {
                            child.setTextColor(textColor)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    
    private fun showDeleteConfirmDialog(exercise: WorkoutExercise, onConfirm: () -> Unit) {
        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Delete Exercise")
            .setMessage("Delete ${exercise.name}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateUI() {
        if (exercises.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            exercisesRecyclerView.visibility = View.GONE
            statsContainer.visibility = View.GONE
            calendarViewContainer.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            statsContainer.visibility = View.VISIBLE
            
            if (isCalendarView) {
                listViewContainer.visibility = View.GONE
                calendarViewContainer.visibility = View.VISIBLE
                calendarView?.updateExercises(exercises)
            } else {
                listViewContainer.visibility = View.VISIBLE
                calendarViewContainer.visibility = View.GONE
                exercisesRecyclerView.visibility = View.VISIBLE
            }
            
            updateStats()
        }
    }
    
    private fun updateStats() {
        // For stats, show total reps for reps exercises, and total time for time exercises
        val repsExercises = exercises.filter { it.type == ExerciseType.REPS }
        val timeExercises = exercises.filter { it.type == ExerciseType.TIME }
        
        val totalReps = repsExercises.sumOf { it.todayCount }
        val totalTime = timeExercises.sumOf { it.todayCount }
        
        val statsText = buildString {
            if (totalReps > 0) {
                append("$totalReps reps")
            }
            if (totalReps > 0 && totalTime > 0) {
                append(" • ")
            }
            if (totalTime > 0) {
                val timeStr = formatTime(totalTime)
                append(timeStr)
            }
            if (totalReps == 0 && totalTime == 0) {
                append("0")
            }
        }
        
        totalTodayText.text = statsText
        exercisesCountText.text = exercises.size.toString()
        
        // Update streak
        updateStreak()
        
        // Update weekly total
        updateWeeklyTotal()
    }
    
    private fun updateStreak() {
        val today = getCurrentDate()
        val lastStreakDate = prefs.getString(LAST_STREAK_DATE_KEY, null)
        var currentStreak = prefs.getInt(STREAK_KEY, 0)
        
        // Check if user worked out today
        val workedOutToday = exercises.any { it.todayCount > 0 }
        
        if (lastStreakDate == today) {
            // Already updated today, keep current streak
        } else if (lastStreakDate != null) {
            // Check if yesterday was worked out (for streak continuation)
            val yesterday = getYesterdayDate()
            val workedOutYesterday = exercises.any { 
                it.workoutDates.contains(yesterday) || it.lastWorkoutDate == yesterday
            }
            
            if (workedOutToday) {
                if (workedOutYesterday || lastStreakDate == yesterday) {
                    // Continue streak
                    currentStreak++
                } else {
                    // New streak starting
                    currentStreak = 1
                }
                prefs.edit()
                    .putInt(STREAK_KEY, currentStreak)
                    .putString(LAST_STREAK_DATE_KEY, today)
                    .apply()
            } else if (lastStreakDate != yesterday) {
                // Streak broken
                currentStreak = 0
                prefs.edit()
                    .putInt(STREAK_KEY, 0)
                    .putString(LAST_STREAK_DATE_KEY, today)
                    .apply()
            }
        } else {
            // First time - initialize
            if (workedOutToday) {
                currentStreak = 1
                prefs.edit()
                    .putInt(STREAK_KEY, 1)
                    .putString(LAST_STREAK_DATE_KEY, today)
                    .apply()
            }
        }
        
        streakText.text = "$currentStreak ${if (currentStreak == 1) "day" else "days"}"
    }
    
    private fun getYesterdayDate(): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }
    
    private fun updateWeeklyTotal() {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_WEEK)
        val startOfWeek = calendar.clone() as Calendar
        startOfWeek.add(Calendar.DAY_OF_WEEK, -(today - calendar.firstDayOfWeek))
        startOfWeek.set(Calendar.HOUR_OF_DAY, 0)
        startOfWeek.set(Calendar.MINUTE, 0)
        startOfWeek.set(Calendar.SECOND, 0)
        
        val weekDates = mutableListOf<String>()
        for (i in 0..6) {
            val date = startOfWeek.clone() as Calendar
            date.add(Calendar.DAY_OF_MONTH, i)
            weekDates.add(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date.time))
        }
        
        val weeklyReps = exercises.filter { it.type == ExerciseType.REPS }
            .sumOf { exercise ->
                weekDates.sumOf { date ->
                    if (exercise.workoutDates.contains(date)) {
                        // For weekly total, we'll use today's count if it's today, otherwise estimate
                        if (date == getCurrentDate()) exercise.todayCount else exercise.bestDay
                    } else 0
                }
            }
        
        val weeklyTime = exercises.filter { it.type == ExerciseType.TIME }
            .sumOf { exercise ->
                weekDates.sumOf { date ->
                    if (exercise.workoutDates.contains(date)) {
                        if (date == getCurrentDate()) exercise.todayCount else exercise.bestDay
                    } else 0
                }
            }
        
        val weeklyText = buildString {
            if (weeklyReps > 0) {
                append("$weeklyReps")
            }
            if (weeklyReps > 0 && weeklyTime > 0) {
                append(" + ")
            }
            if (weeklyTime > 0) {
                append(formatTime(weeklyTime))
            }
            if (weeklyReps == 0 && weeklyTime == 0) {
                append("0")
            }
        }
        
        weeklyTotalText.text = weeklyText
    }
    
    private fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %dm", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm %ds", minutes, secs)
            else -> String.format(Locale.getDefault(), "%ds", secs)
        }
    }
    
    private fun saveExercises() {
        try {
            val jsonArray = JSONArray()
            exercises.forEach { exercise ->
                jsonArray.put(exercise.toJson())
            }
            prefs.edit().putString(EXERCISES_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadExercises() {
        try {
            val exercisesJson = prefs.getString(EXERCISES_KEY, null) ?: return
            val jsonArray = JSONArray(exercisesJson)
            
            exercises.clear()
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getString(i)
                WorkoutExercise.fromJson(json)?.let { exercise ->
                    exercises.add(exercise)
                }
            }
            
            adapter.notifyDataSetChanged()
            updateStats()
            
            // Update calendar view if it exists
            calendarView?.updateExercises(exercises)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class WorkoutAdapter(
    private val exercises: List<WorkoutExercise>,
    private val workoutWidget: WorkoutWidget,
    private val onIncrementClick: (WorkoutExercise, Int) -> Unit
) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {
    
    class WorkoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val exerciseNameText: TextView = itemView.findViewById(R.id.exercise_name)
        val exerciseTypeLabel: TextView = itemView.findViewById(R.id.exercise_type_label)
        val todayCountText: TextView = itemView.findViewById(R.id.today_count_text)
        val totalCountText: TextView = itemView.findViewById(R.id.total_count_text)
        val bestDayText: TextView = itemView.findViewById(R.id.best_day_text)
        val incrementButton: View = itemView.findViewById(R.id.increment_button)
        val increment5Button: View = itemView.findViewById(R.id.increment_5_button)
        val menuButton: TextView = itemView.findViewById(R.id.exercise_menu_button)
        val cardView: View = itemView.findViewById(R.id.exercise_card)
        
        // Time-based views
        val repsLayout: View = itemView.findViewById(R.id.reps_layout)
        val timeLayout: View = itemView.findViewById(R.id.time_layout)
        val stopwatchDisplay: TextView = itemView.findViewById(R.id.stopwatch_display)
        val todayTimeText: TextView = itemView.findViewById(R.id.today_time_text)
        val bestTimeText: TextView = itemView.findViewById(R.id.best_time_text)
        val stopwatchStartStop: android.widget.Button = itemView.findViewById(R.id.stopwatch_start_stop)
        val stopwatchReset: android.widget.Button = itemView.findViewById(R.id.stopwatch_reset)
        
        var stopwatch: WorkoutStopwatch? = null
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.workout_exercise_item, parent, false)
        return WorkoutViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        val exercise = exercises[position]
        
        holder.exerciseNameText.text = exercise.name
        
        // Clean up previous stopwatch
        holder.stopwatch?.cleanup()
        holder.stopwatch = null
        
        if (exercise.type == ExerciseType.TIME) {
            // Show time-based layout
            holder.repsLayout.visibility = View.GONE
            holder.timeLayout.visibility = View.VISIBLE
            
            holder.exerciseTypeLabel.text = "TIME"
            holder.exerciseTypeLabel.visibility = View.VISIBLE
            
            holder.todayTimeText.text = "Recorded: ${exercise.getDisplayValue()}"
            holder.totalCountText.text = "Total: ${exercise.getTotalDisplayValue()}"
            holder.bestTimeText.text = "Best: ${exercise.getBestDisplayValue()}"
            
            // Setup stopwatch
            holder.stopwatch = WorkoutStopwatch(
                holder.stopwatchDisplay,
                holder.stopwatchStartStop,
                holder.stopwatchReset
            ) { seconds ->
                onIncrementClick(exercise, seconds)
            }
        } else {
            // Show reps-based layout
            holder.repsLayout.visibility = View.VISIBLE
            holder.timeLayout.visibility = View.GONE
            
            holder.exerciseTypeLabel.visibility = View.GONE
            
            holder.todayCountText.text = exercise.getDisplayValue()
            holder.totalCountText.text = "Total: ${exercise.getTotalDisplayValue()}"
            holder.bestDayText.text = "Best: ${exercise.getBestDisplayValue()}"
            
            // Animate count change
            val animation = AnimationUtils.loadAnimation(holder.itemView.context, android.R.anim.fade_in)
            holder.todayCountText.startAnimation(animation)
            
            holder.incrementButton.setOnClickListener {
                onIncrementClick(exercise, 1)
                val scaleAnimation = AnimationUtils.loadAnimation(holder.itemView.context, android.R.anim.fade_in)
                holder.todayCountText.startAnimation(scaleAnimation)
            }
            
            holder.increment5Button.setOnClickListener {
                onIncrementClick(exercise, 5)
                val scaleAnimation = AnimationUtils.loadAnimation(holder.itemView.context, android.R.anim.fade_in)
                holder.todayCountText.startAnimation(scaleAnimation)
            }
        }
        
        // Menu button click instead of long-press
        holder.menuButton.setOnClickListener {
            workoutWidget.showExerciseOptions(exercise)
        }
    }
    
    override fun onViewRecycled(holder: WorkoutViewHolder) {
        super.onViewRecycled(holder)
        holder.stopwatch?.cleanup()
        holder.stopwatch = null
    }
    
    override fun getItemCount() = exercises.size
}
