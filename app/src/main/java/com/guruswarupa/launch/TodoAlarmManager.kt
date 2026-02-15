package com.guruswarupa.launch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

class TodoAlarmManager(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule an alarm for a todo item
     * @param todoItem The todo item to schedule
     * @param requestCode Unique request code for this alarm (based on todo item hash)
     */
    fun scheduleAlarm(todoItem: TodoItem, requestCode: Int) {
        if (todoItem.isChecked) {
            return // Don't schedule if already completed
        }

        // Handle interval-based todos
        if (todoItem.isIntervalBased() && todoItem.intervalStartTime != null && todoItem.recurrenceInterval != null) {
            scheduleIntervalAlarm(todoItem, requestCode)
            return
        }

        // Handle day-based or one-time todos with due time
        if (todoItem.dueTime == null) {
            return // Don't schedule if no due time
        }

        val (hour, minute) = parseTime(todoItem.dueTime) ?: return

        if (todoItem.isRecurring && todoItem.selectedDays.isNotEmpty()) {
            // Schedule for each selected day
            todoItem.selectedDays.forEach { dayOfWeek ->
                scheduleRecurringAlarm(todoItem, dayOfWeek, hour, minute, requestCode + dayOfWeek)
            }
        } else {
            // Schedule one-time alarm for today if time hasn't passed, otherwise tomorrow
            scheduleOneTimeAlarm(todoItem, hour, minute, requestCode)
        }
    }
    
    /**
     * Schedule interval-based alarm (Pomodoro-style)
     */
    private fun scheduleIntervalAlarm(todoItem: TodoItem, requestCode: Int) {
        val intervalStartTime = todoItem.intervalStartTime ?: return
        val recurrenceInterval = todoItem.recurrenceInterval ?: return
        val (startHour, startMinute) = parseTime(intervalStartTime) ?: return

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute

        // Calculate next alarm time
        var nextAlarmTimeInMinutes = startTimeInMinutes
        
        // If start time has passed today, calculate next occurrence
        if (currentTimeInMinutes >= startTimeInMinutes) {
            // Find next interval occurrence
            val elapsedSinceStart = currentTimeInMinutes - startTimeInMinutes
            val intervalsPassed = (elapsedSinceStart / recurrenceInterval) + 1
            nextAlarmTimeInMinutes = startTimeInMinutes + (intervalsPassed * recurrenceInterval)
        }

        // If next alarm is tomorrow, add a day
        if (nextAlarmTimeInMinutes >= 24 * 60) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            nextAlarmTimeInMinutes -= 24 * 60
        }

        calendar.set(Calendar.HOUR_OF_DAY, nextAlarmTimeInMinutes / 60)
        calendar.set(Calendar.MINUTE, nextAlarmTimeInMinutes % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = createAlarmIntent(todoItem, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    /**
     * Schedule a recurring alarm for a specific day of the week
     */
    private fun scheduleRecurringAlarm(
        todoItem: TodoItem,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        // Calculate days until next occurrence
        var daysUntil = dayOfWeek - currentDayOfWeek
        if (daysUntil < 0) {
            daysUntil += 7 // Next week
        } else if (daysUntil == 0) {
            // Same day - check if time has passed
            val currentTimeInMinutes = currentHour * 60 + currentMinute
            val dueTimeInMinutes = hour * 60 + minute
            if (currentTimeInMinutes >= dueTimeInMinutes) {
                daysUntil = 7 // Next week
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysUntil)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = createAlarmIntent(todoItem, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    /**
     * Schedule a one-time alarm
     */
    private fun scheduleOneTimeAlarm(
        todoItem: TodoItem,
        hour: Int,
        minute: Int,
        requestCode: Int
    ) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        // If time has passed today, schedule for tomorrow
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val dueTimeInMinutes = hour * 60 + minute

        if (currentTimeInMinutes >= dueTimeInMinutes) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val intent = createAlarmIntent(todoItem, requestCode)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    /**
     * Cancel an alarm for a todo item
     */
    fun cancelAlarm(todoItem: TodoItem, requestCode: Int) {
        if (todoItem.isRecurring && todoItem.selectedDays.isNotEmpty()) {
            // Cancel alarms for each selected day
            todoItem.selectedDays.forEach { dayOfWeek ->
                cancelAlarmForRequestCode(requestCode + dayOfWeek)
            }
        } else {
            cancelAlarmForRequestCode(requestCode)
        }
    }

    /**
     * Cancel alarm for a specific request code
     */
    private fun cancelAlarmForRequestCode(requestCode: Int) {
        val intent = Intent(context, TodoAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Cancel all alarms for all todo items
     */
    fun cancelAllAlarms(todoItems: List<TodoItem>) {
        todoItems.forEachIndexed { index, todoItem ->
            val requestCode = getRequestCode(todoItem, index)
            cancelAlarm(todoItem, requestCode)
        }
    }

    /**
     * Reschedule all alarms for all todo items
     */
    fun rescheduleAllAlarms(todoItems: List<TodoItem>) {
        cancelAllAlarms(todoItems)
        todoItems.forEachIndexed { index, todoItem ->
            if (!todoItem.isChecked) {
                // Schedule if has due time or is interval-based with start time
                if (todoItem.dueTime != null || (todoItem.isIntervalBased() && todoItem.intervalStartTime != null)) {
                    val requestCode = getRequestCode(todoItem, index)
                    scheduleAlarm(todoItem, requestCode)
                }
            }
        }
    }

    /**
     * Create an Intent for the alarm
     */
    private fun createAlarmIntent(todoItem: TodoItem, requestCode: Int): Intent {
        return Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("todo_text", todoItem.text)
            putExtra("todo_category", todoItem.category)
            putExtra("todo_priority", todoItem.priority.name)
            putExtra("request_code", requestCode)
            putExtra("is_interval_based", todoItem.isIntervalBased())
            if (todoItem.isIntervalBased()) {
                putExtra("interval_minutes", todoItem.recurrenceInterval ?: 0)
                putExtra("interval_start_time", todoItem.intervalStartTime ?: "")
            }
        }
    }

    /**
     * Generate a unique request code for a todo item
     */
    fun getRequestCode(todoItem: TodoItem, index: Int): Int {
        // Use hash of text + index to generate unique request code
        return (todoItem.text.hashCode() + index * 1000) and 0x7FFFFFFF
    }

    /**
     * Parse time string (HH:mm) into hour and minute
     */
    private fun parseTime(timeString: String): Pair<Int, Int>? {
        val parts = timeString.split(":")
        if (parts.size != 2) return null

        return try {
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            if (hour in 0..23 && minute in 0..59) {
                Pair(hour, minute)
            } else {
                null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
