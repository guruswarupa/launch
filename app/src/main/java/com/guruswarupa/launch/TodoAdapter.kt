
package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TodoAdapter(
    private val todoItems: MutableList<TodoItem>,
    private val onDeleteClick: (TodoItem) -> Unit,
    private val onTaskStateChanged: () -> Unit = {}
) : RecyclerView.Adapter<TodoAdapter.TodoViewHolder>() {

    class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val todoCheckBox: CheckBox = view.findViewById(R.id.todo_checkbox)
        val todoText: TextView = view.findViewById(R.id.todo_text)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_todo_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.todo_item, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        val todoItem = todoItems[position]

        holder.todoCheckBox.isChecked = todoItem.isChecked

        // Show task type indicator
        val taskText = if (todoItem.isRecurring) {
            "🔄 ${todoItem.text}"
        } else {
            todoItem.text
        }
        holder.todoText.text = taskText

        // Apply strikethrough effect if checked
        if (todoItem.isChecked) {
            holder.todoText.paintFlags = holder.todoText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.todoText.alpha = 0.6f
        } else {
            holder.todoText.paintFlags = holder.todoText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.todoText.alpha = 1.0f
        }

        holder.todoCheckBox.setOnCheckedChangeListener { _, isChecked ->
            todoItem.isChecked = isChecked

            // For recurring tasks, mark completion date
            if (isChecked && todoItem.isRecurring) {
                todoItem.lastCompletedDate = getCurrentDateString()
            }

            // Update the visual state
            if (isChecked) {
                holder.todoText.paintFlags = holder.todoText.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.todoText.alpha = 0.6f
            } else {
                holder.todoText.paintFlags = holder.todoText.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.todoText.alpha = 1.0f
            }

            onTaskStateChanged()
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(todoItem)
        }
    }

    private fun getCurrentDateString(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.DAY_OF_YEAR)}-${calendar.get(java.util.Calendar.YEAR)}"
    }

    override fun getItemCount(): Int = todoItems.size
}
