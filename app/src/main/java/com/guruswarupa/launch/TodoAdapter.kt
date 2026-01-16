
package com.guruswarupa.launch

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class TodoAdapter(
    private val todoItems: MutableList<TodoItem>,
    private val onDeleteClick: (TodoItem) -> Unit,
    private val onTaskStateChanged: () -> Unit = {}
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val todoCheckBox: CheckBox = view.findViewById(R.id.todo_checkbox)
        val todoText: TextView = view.findViewById(R.id.todo_text)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_todo_button)
        val priorityIndicator: View = view.findViewById(R.id.priority_indicator)
        val categoryText: TextView = view.findViewById(R.id.category_text)
        val dueTimeText: TextView = view.findViewById(R.id.due_time_text)
        val intervalText: TextView = view.findViewById(R.id.interval_text)
        val daysContainer: LinearLayout = view.findViewById(R.id.days_container)

        val dayViews = listOf(
            view.findViewById<TextView>(R.id.day_sun),
            view.findViewById<TextView>(R.id.day_mon),
            view.findViewById<TextView>(R.id.day_tue),
            view.findViewById<TextView>(R.id.day_wed),
            view.findViewById<TextView>(R.id.day_thu),
            view.findViewById<TextView>(R.id.day_fri),
            view.findViewById<TextView>(R.id.day_sat)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.todo_item, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todoItem = todoItems[position]

        holder.todoCheckBox.isChecked = todoItem.isChecked

        // Set priority indicator drawable
        val priorityDrawable = when (todoItem.priority) {
            TodoItem.Priority.HIGH -> ContextCompat.getDrawable(holder.itemView.context, R.drawable.priority_high)
            TodoItem.Priority.MEDIUM -> ContextCompat.getDrawable(holder.itemView.context, R.drawable.priority_medium)
            TodoItem.Priority.LOW -> ContextCompat.getDrawable(holder.itemView.context, R.drawable.priority_low)
        }
        holder.priorityIndicator.background = priorityDrawable

        // Show task text with strikethrough if completed
        holder.todoText.text = todoItem.text
        if (todoItem.isChecked) {
            holder.todoText.paintFlags = holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.todoText.paintFlags = holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Show category with better formatting
        if (todoItem.category.isNotEmpty() && todoItem.category != "General") {
            holder.categoryText.text = todoItem.category
            holder.categoryText.visibility = View.VISIBLE
        } else {
            holder.categoryText.visibility = View.GONE
        }

        // Show due time if set
        if (todoItem.dueTime != null) {
            holder.dueTimeText.text = "Due: ${todoItem.dueTime}"
            holder.dueTimeText.visibility = View.VISIBLE
        } else {
            holder.dueTimeText.visibility = View.GONE
        }

        // Show interval info if interval-based
        if (todoItem.isIntervalBased() && todoItem.recurrenceInterval != null) {
            val intervalText = when (todoItem.recurrenceInterval) {
                30 -> "Every 30 min"
                60 -> "Every 1 hr"
                120 -> "Every 2 hrs"
                180 -> "Every 3 hrs"
                240 -> "Every 4 hrs"
                360 -> "Every 6 hrs"
                480 -> "Every 8 hrs"
                720 -> "Every 12 hrs"
                else -> "Every ${todoItem.recurrenceInterval} min"
            }
            val startTimeText = if (todoItem.intervalStartTime != null) {
                " from ${todoItem.intervalStartTime}"
            } else {
                ""
            }
            holder.intervalText.text = "$intervalText$startTimeText"
            holder.intervalText.visibility = View.VISIBLE
        } else {
            holder.intervalText.visibility = View.GONE
        }

        // Show days of week for recurring tasks with cleaner styling
        if (todoItem.isRecurring && todoItem.selectedDays.isNotEmpty()) {
            holder.daysContainer.visibility = View.VISIBLE

            holder.dayViews.forEachIndexed { index, dayView ->
                val dayOfWeek = index + 1 // 1=Sunday, 2=Monday, etc.
                if (todoItem.selectedDays.contains(dayOfWeek)) {
                    dayView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.nord8))
                    dayView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
                } else {
                    dayView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.nord2))
                    dayView.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                }
            }
        } else {
            holder.daysContainer.visibility = View.GONE
        }

        // Handle checkbox changes
        holder.todoCheckBox.setOnCheckedChangeListener { _, isChecked ->
            todoItem.isChecked = isChecked

            if (isChecked && todoItem.isRecurring) {
                if (todoItem.isIntervalBased()) {
                    // For interval-based, store timestamp
                    todoItem.lastCompletedDate = System.currentTimeMillis().toString()
                } else {
                    // For day-based, store date string
                    todoItem.lastCompletedDate = getCurrentDateString()
                }
            } else if (!isChecked && todoItem.isRecurring && todoItem.isIntervalBased()) {
                // Reset timestamp when unchecked for interval-based tasks
                todoItem.lastCompletedDate = null
            }

            // Update strike-through text immediately
            if (isChecked) {
                holder.todoText.paintFlags = holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.todoText.paintFlags = holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            onTaskStateChanged()
        }

        // Handle delete button
        holder.deleteButton.setOnClickListener {
            onDeleteClick(todoItem)
        }
    }

    private fun getCurrentDateString(): String {
        val calendar = Calendar.getInstance()
        return "${calendar.get(Calendar.DAY_OF_YEAR)}-${calendar.get(Calendar.YEAR)}"
    }

    override fun getItemCount(): Int = todoItems.size
}
