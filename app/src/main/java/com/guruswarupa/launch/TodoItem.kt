package com.guruswarupa.launch

data class TodoItem(
    val text: String,
    var isChecked: Boolean,
    val isRecurring: Boolean = false,
    var lastCompletedDate: String? = null,
    val selectedDays: Set<Int> = emptySet(), // Days of week (1=Sunday, 2=Monday, etc.)
    val priority: Priority = Priority.MEDIUM,
    val category: String = "General",
    val dueTime: String? = null // Optional time in HH:mm format
) {
    enum class Priority(val displayName: String, val color: String) {
        HIGH("High", "#FF5722"),
        MEDIUM("Medium", "#FF9800"),
        LOW("Low", "#4CAF50")
    }
}
