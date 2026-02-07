package com.guruswarupa.launch

import android.content.SharedPreferences
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

/**
 * Manages todo items: loading, saving, adding, removing, and recurring task logic
 */
class TodoManager(
    private val activity: MainActivity,
    private val sharedPreferences: SharedPreferences,
    private val todoRecyclerView: RecyclerView,
    private val addTodoButton: ImageButton,
    private val todoAlarmManager: TodoAlarmManager
) {
    private val todoItems = mutableListOf<TodoItem>()
    private lateinit var todoAdapter: TodoAdapter
    
    fun initialize() {
        todoRecyclerView.layoutManager = LinearLayoutManager(activity)
        // Disable animations to prevent "Tmp detached view" crash during rapid updates
        todoRecyclerView.itemAnimator = null
        
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
        rescheduleTodoAlarms()
    }
    
    fun getTodoItems(): MutableList<TodoItem> = todoItems
    
    fun showAddTodoDialog() {
        val dialogBuilder = android.app.AlertDialog.Builder(activity, R.style.CustomDialogTheme)
        dialogBuilder.setTitle("Add Todo Item")

        // Create custom layout
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_add_todo, null)
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
        categorySpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, categories)

        // Setup priority spinner
        val priorities = TodoItem.Priority.values().map { it.displayName }.toTypedArray()
        prioritySpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, priorities)
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
        intervalSpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, intervals)

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
        if (dueTime != null || (newTodo.isIntervalBased() && newTodo.intervalStartTime != null)) {
            val requestCode = todoAlarmManager.getRequestCode(newTodo, index)
            todoAlarmManager.scheduleAlarm(newTodo, requestCode)
        }
    }

    private fun removeTodoItem(todoItem: TodoItem) {
        val index = todoItems.indexOf(todoItem)
        if (index != -1) {
            // Cancel alarm before removing
            if (todoItem.dueTime != null) {
                val requestCode = todoAlarmManager.getRequestCode(todoItem, index)
                todoAlarmManager.cancelAlarm(todoItem, requestCode)
            }
            todoItems.removeAt(index)
            todoAdapter.notifyItemRemoved(index)
            saveTodoItems()
        }
    }

    fun loadTodoItems() {
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
            rescheduleTodoAlarms()
        }
    }

    fun saveTodoItems() {
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
    
    fun rescheduleTodoAlarms() {
        todoAlarmManager.rescheduleAllAlarms(todoItems)
    }
    
    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }
}
