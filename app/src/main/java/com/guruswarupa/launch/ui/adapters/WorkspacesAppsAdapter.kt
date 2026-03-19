package com.guruswarupa.launch.ui.adapters

import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R

class WorkspacesAppsAdapter(
    private val apps: List<ResolveInfo>,
    private val selectedPackages: MutableSet<String>,
    private val onSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<WorkspacesAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
        val appCheckbox: CheckBox = view.findViewById(R.id.app_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.focus_mode_app_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val packageName = app.activityInfo.packageName
        val packageManager = holder.itemView.context.packageManager
        
        // Set app name
        try {
            holder.appName.text = app.loadLabel(packageManager).toString()
        } catch (_: Exception) {
            holder.appName.text = packageName
        }
        
        // Set app icon
        try {
            holder.appIcon.setImageDrawable(app.loadIcon(packageManager))
        } catch (_: Exception) {
            holder.appIcon.setImageDrawable(null)
        }
        
        // Remove listener before setting state to avoid triggering during recycling
        holder.appCheckbox.setOnCheckedChangeListener(null)
        
        // Set checkbox state based on current selection
        holder.appCheckbox.isChecked = selectedPackages.contains(packageName)
        
        // Set listener after state is set
        holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (selectedPackages.contains(packageName) != isChecked) {
                onSelectionChanged(packageName, isChecked)
            }
        }
        
        // Handle item click (toggle checkbox)
        holder.itemView.setOnClickListener {
            val newState = !holder.appCheckbox.isChecked
            holder.appCheckbox.isChecked = newState
        }
    }

    override fun getItemCount(): Int = apps.size
}
