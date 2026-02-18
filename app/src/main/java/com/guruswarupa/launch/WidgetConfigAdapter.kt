package com.guruswarupa.launch

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class WidgetConfigAdapter(
    private val context: Context,
    private var widgets: MutableList<WidgetConfigurationManager.WidgetInfo>,
    private val previewManager: WidgetPreviewManager,
    private val onToggleChanged: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<WidgetConfigAdapter.WidgetViewHolder>() {

    class WidgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val previewImage: ImageView = itemView.findViewById(R.id.widget_preview_image)
        val loadingProgress: ProgressBar = itemView.findViewById(R.id.preview_loading_progress)
        val disabledOverlay: View = itemView.findViewById(R.id.disabled_overlay)
        val disabledText: TextView = itemView.findViewById(R.id.disabled_text)
        val widgetName: TextView = itemView.findViewById(R.id.widget_name)
        val widgetDescription: TextView = itemView.findViewById(R.id.widget_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.widget_preview_item, parent, false)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        holder.widgetName.text = widget.name
        holder.widgetDescription.text = getWidgetDescription(widget.id)
        
        // Update UI based on enabled state
        updateWidgetState(holder, widget.enabled)
        
        // Load preview image
        loadPreviewImage(holder, widget)
        
        // No toggle functionality - widgets are enabled via preview dialog
        
        // Make the entire item clickable to show preview
        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                showWidgetPreview(widgets[currentPos])
            }
        }
    }

    override fun getItemCount(): Int = widgets.size

    // Remove this method as ordering is now handled in the activity
    // The moveItem functionality is moved to the Activity level

    fun getWidgets(): List<WidgetConfigurationManager.WidgetInfo> = widgets.toList()
    
    fun updateWidgets(newWidgets: MutableList<WidgetConfigurationManager.WidgetInfo>) {
        widgets = newWidgets
    }
    
    private fun updateWidgetState(holder: WidgetViewHolder, isEnabled: Boolean) {
        holder.disabledOverlay.visibility = if (isEnabled) View.GONE else View.VISIBLE
        holder.disabledText.visibility = if (isEnabled) View.GONE else View.VISIBLE
        holder.widgetName.alpha = if (isEnabled) 1.0f else 0.5f
        holder.widgetDescription.alpha = if (isEnabled) 1.0f else 0.5f
        holder.previewImage.alpha = if (isEnabled) 1.0f else 0.4f
    }
    
    private fun loadPreviewImage(holder: WidgetViewHolder, widget: WidgetConfigurationManager.WidgetInfo) {
        // Show loading state
        holder.previewImage.setImageResource(android.R.color.transparent)
        holder.loadingProgress.visibility = View.VISIBLE
        
        previewManager.generatePreview(widget.id, widget.name) { bitmap ->
            holder.loadingProgress.visibility = View.GONE
            if (bitmap != null) {
                holder.previewImage.setImageBitmap(bitmap)
            } else {
                // Show placeholder if preview generation failed
                holder.previewImage.setImageResource(R.drawable.ic_widget_placeholder)
            }
        }
    }
    
    private fun getWidgetDescription(widgetId: String): String {
        return when (widgetId) {
            "calculator_widget_container" -> "Calculator & converter"
            "compass_widget_container" -> "Digital compass"
            "notifications_widget_container" -> "Recent notifications"
            "calendar_events_widget_container" -> "Calendar events"
            "countdown_widget_container" -> "Countdown timer"
            "physical_activity_widget_container" -> "Step counter"
            "pressure_widget_container" -> "Pressure monitor"
            "proximity_widget_container" -> "Proximity sensor"
            "temperature_widget_container" -> "Temperature sensor"
            "noise_decibel_widget_container" -> "Sound level meter"
            "workout_widget_container" -> "Workout tracker"
            "todo_recycler_view" -> "Task manager"
            "finance_widget" -> "Finance tracker"
            "weekly_usage_widget" -> "Usage stats"
            "network_stats_widget_container" -> "Network monitor"
            "device_info_widget_container" -> "Device info"
            else -> "Widget"
        }
    }
    
    private fun showWidgetPreview(widget: WidgetConfigurationManager.WidgetInfo) {
        // Show preview dialog when item is clicked
        val previewManager = (context as? androidx.appcompat.app.AppCompatActivity)?.let {
            WidgetPreviewManager(it)
        }
        previewManager?.let {
            WidgetPreviewDialog.show(context, widget, it) { shouldEnable ->
                // Toggle the widget state
                val position = widgets.indexOfFirst { w -> w.id == widget.id }
                if (position >= 0) {
                    widgets[position] = widgets[position].copy(enabled = shouldEnable)
                    notifyItemChanged(position)
                    val action = if (shouldEnable) "enabled" else "disabled"
                    android.widget.Toast.makeText(context, "${widget.name} $action!", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // Also update the main lists in the activity
                    if (context is androidx.appcompat.app.AppCompatActivity) {
                        val activity = context as androidx.appcompat.app.AppCompatActivity
                        // This will trigger the activity's update mechanism
                        (activity as? WidgetConfigurationActivity)?.updateWidgetState(widget.id, shouldEnable)
                    }
                }
            }
        }
    }
}
