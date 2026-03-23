package com.guruswarupa.launch.models

data class TodoItem(
    val text: String,
    var isChecked: Boolean,
    val isRecurring: Boolean = false,
    var lastCompletedDate: String? = null,
    val selectedDays: Set<Int> = emptySet(), 
    val priority: Priority = Priority.MEDIUM,
    val category: String = "General",
    val dueTime: String? = null, 
    val recurrenceInterval: Int? = null, 
    val intervalStartTime: String? = null 
) {
    enum class Priority(val displayName: String, val color: String) {
        HIGH("High", "#FF5722"),
        MEDIUM("Medium", "#FF9800"),
        LOW("Low", "#4CAF50")
    }
    
    fun isIntervalBased(): Boolean = recurrenceInterval != null && recurrenceInterval > 0
    fun isDayBased(): Boolean = selectedDays.isNotEmpty()
}
