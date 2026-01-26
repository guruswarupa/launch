package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WidgetConfigAdapter(
    private var widgets: MutableList<WidgetConfigurationManager.WidgetInfo>,
    private val onToggleChanged: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<WidgetConfigAdapter.WidgetViewHolder>() {

    class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
        val widgetName: TextView = itemView.findViewById(R.id.widget_name)
        val widgetToggle: Switch = itemView.findViewById(R.id.widget_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_config_item, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        holder.widgetName.text = widget.name
        holder.widgetToggle.isChecked = widget.enabled
        
        holder.widgetToggle.setOnCheckedChangeListener { _, isChecked ->
            onToggleChanged(position, isChecked)
        }
    }

    override fun getItemCount(): Int = widgets.size

    fun updateWidgets(newWidgets: List<WidgetConfigurationManager.WidgetInfo>) {
        widgets.clear()
        widgets.addAll(newWidgets)
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                val temp = widgets[i]
                widgets[i] = widgets[i + 1]
                widgets[i + 1] = temp
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                val temp = widgets[i]
                widgets[i] = widgets[i - 1]
                widgets[i - 1] = temp
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getWidgets(): List<WidgetConfigurationManager.WidgetInfo> = widgets.toList()
}
