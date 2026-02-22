package com.guruswarupa.launch.models

data class TodoItem(
    val text: String,
    var isChecked: Boolean,
    val isRecurring: Boolean = false,
    var lastCompletedDate: String? = null,
    val selectedDays: Set<Int> = emptySet(), // Days of week (1=Sunday, 2=Monday, etc.)
    val priority: Priority = Priority.MEDIUM,
    val category: String = "General",
    val dueTime: String? = null, // Optional time in HH:mm format
    val recurrenceInterval: Int? = null, // Interval in minutes (e.g., 30, 60, 120 for Pomodoro-style)
    val intervalStartTime: String? = null // Start time for interval-based todos in HH:mm format
) {
    enum class Priority(val displayName: String, val color: String) {
        HIGH("High", "#FF5722"),
        MEDIUM("Medium", "#FF9800"),
        LOW("Low", "#4CAF50")
    }
    
    fun isIntervalBased(): Boolean = recurrenceInterval != null && recurrenceInterval > 0
    fun isDayBased(): Boolean = selectedDays.isNotEmpty()
}
