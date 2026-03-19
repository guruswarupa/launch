package com.guruswarupa.launch.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.managers.WidgetConfigurationManager
import com.guruswarupa.launch.utils.WidgetPreviewManager
import com.guruswarupa.launch.R
import com.guruswarupa.launch.ui.activities.WidgetConfigurationActivity
import com.guruswarupa.launch.utils.WidgetPreviewDialog
import com.guruswarupa.launch.managers.TypographyManager

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
        TypographyManager.applyToView(view)
        return WidgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val widget = widgets[position]
        holder.widgetName.text = widget.name
        holder.widgetDescription.text = getWidgetDescription(widget)
        
        // Update UI based on enabled state
        // If it's a provider (not yet added), it's "disabled" but maybe with different text
        if (widget.isProvider) {
            holder.disabledOverlay.visibility = View.VISIBLE
            holder.disabledText.visibility = View.VISIBLE
            holder.disabledText.text = "TAP TO ADD"
            holder.widgetName.alpha = 0.6f
            holder.widgetDescription.alpha = 0.6f
            holder.previewImage.alpha = 0.4f
        } else {
            updateWidgetState(holder, widget.enabled)
            holder.disabledText.text = "DISABLED"
        }
        
        // Load preview image
        loadPreviewImage(holder, widget)
        
        // Make the entire item clickable to show preview
        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                showWidgetPreview(widgets[currentPos])
            }
        }
    }

    override fun getItemCount(): Int = widgets.size

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
        // Show loading state - clear drawable completely
        holder.previewImage.setImageDrawable(null)
        holder.loadingProgress.visibility = View.VISIBLE
        
        previewManager.generatePreview(widget.id, widget.name) { bitmap ->
            holder.loadingProgress.visibility = View.GONE
            if (bitmap != null && !bitmap.isRecycled) {
                holder.previewImage.setImageBitmap(bitmap)
            } else {
                // Show placeholder if preview generation failed
                holder.previewImage.setImageResource(R.drawable.ic_widget_placeholder)
            }
        }
    }
    
    private fun getWidgetDescription(widget: WidgetConfigurationManager.WidgetInfo): String {
        if (widget.isSystemWidget) {
            return "System widget from ${widget.providerPackage ?: "unknown"}"
        }
        return when (widget.id) {
            "calculator_widget_container" -> "Calculator & converter"
            "compass_widget_container" -> "Digital compass"
            "notifications_widget_container" -> "Recent notifications"
            "calendar_events_widget_container" -> "Calendar events"
            "countdown_widget_container" -> "Countdown timer"
            "physical_activity_widget_container" -> "Step counter"
            "pressure_widget_container" -> "Pressure monitor"
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
        val activity = context as? WidgetConfigurationActivity ?: return
        
        WidgetPreviewDialog.show(context, widget, previewManager) { shouldEnable ->
            if (widget.isProvider) {
                if (shouldEnable) {
                    activity.addSystemWidgetProvider(widget)
                }
            } else {
                // Toggle the widget state
                val position = widgets.indexOfFirst { w -> w.id == widget.id }
                if (position >= 0) {
                    widgets[position] = widgets[position].copy(enabled = shouldEnable)
                    notifyItemChanged(position)
                    
                    // Also update the main lists in the activity
                    activity.updateWidgetState(widget.id, shouldEnable)
                }
            }
        }
    }
}
