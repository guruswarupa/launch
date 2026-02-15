package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class WidgetConfigAdapter(
    private var widgets: MutableList<WidgetConfigurationManager.WidgetInfo>,
    private val onToggleChanged: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<WidgetConfigAdapter.WidgetViewHolder>() {

    class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        val widgetName: TextView = itemView.findViewById(R.id.widget_name)
        val widgetToggle: SwitchCompat = itemView.findViewById(R.id.widget_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_config_item, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        holder.widgetName.text = widget.name
        
        // Ensure drag handle is visible (field is used here to satisfy lint)
        holder.dragHandle.alpha = 1.0f
        
        // Nullify listener before setting isChecked to prevent unwanted triggers during binding
        holder.widgetToggle.setOnCheckedChangeListener(null)
        holder.widgetToggle.isChecked = widget.enabled
        
        holder.widgetToggle.setOnCheckedChangeListener { _, isChecked ->
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onToggleChanged(currentPos, isChecked)
            }
        }
    }

    override fun getItemCount(): Int = widgets.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        
        // Remove the item from its current position
        val item = widgets.removeAt(fromPosition)
        
        // Insert it at the new position
        widgets.add(toPosition, item)
        
        // Notify RecyclerView of the move
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getWidgets(): List<WidgetConfigurationManager.WidgetInfo> = widgets.toList()
}
