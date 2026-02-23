package com.guruswarupa.launch.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import com.guruswarupa.launch.R

data class AppUsageItem(
    val appName: String,
    val usageTime: Long,
    val percentage: Float,
    val color: Int
)

class AppUsageAdapter(
    private val appUsages: List<AppUsageItem>
) : RecyclerView.Adapter<AppUsageAdapter.AppUsageViewHolder>() {

    class AppUsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorIndicator: View = itemView.findViewById(R.id.color_indicator)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val usageTime: TextView = itemView.findViewById(R.id.usage_time)
        val usagePercentage: TextView = itemView.findViewById(R.id.usage_percentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return AppUsageViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        val item = appUsages[position]
        
        // Set color indicator
        holder.colorIndicator.setBackgroundColor(item.color)
        
        // Set app name
        holder.appName.text = item.appName
        
        // Set usage time
        holder.usageTime.text = formatUsageTime(item.usageTime)
        
        // Set percentage
        holder.usagePercentage.text = String.format(Locale.getDefault(), "%.1f%%", item.percentage)
    }

    override fun getItemCount(): Int = appUsages.size

    private fun formatUsageTime(timeInMillis: Long): String {
        if (timeInMillis <= 0) return "0m"

        val minutes = timeInMillis / (1000 * 60)
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
