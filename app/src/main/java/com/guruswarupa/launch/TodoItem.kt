package com.guruswarupa.launch

data class TodoItem(
    val text: String,
    var isChecked: Boolean,
    val isRecurring: Boolean = false,
    var lastCompletedDate: String? = null
)
