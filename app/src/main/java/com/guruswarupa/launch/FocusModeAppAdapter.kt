package com.guruswarupa.launch

import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FocusModeAppAdapter(
    private val appList: List<ResolveInfo>,
    focusModeManager: FocusModeManager
) : RecyclerView.Adapter<FocusModeAppAdapter.ViewHolder>() {

    private val selectedApps = focusModeManager.getAllowedApps().toMutableSet()

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
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName
        val packageManager = holder.itemView.context.packageManager

        holder.appIcon.setImageDrawable(appInfo.loadIcon(packageManager))
        holder.appName.text = appInfo.loadLabel(packageManager)
        
        // CRITICAL: Remove listener before setting state to prevent recycle glitch
        holder.appCheckbox.setOnCheckedChangeListener(null)
        holder.appCheckbox.isChecked = selectedApps.contains(packageName)

        holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedApps.add(packageName)
            } else {
                selectedApps.remove(packageName)
            }
        }

        holder.itemView.setOnClickListener {
            val newState = !holder.appCheckbox.isChecked
            holder.appCheckbox.isChecked = newState
            // Listener above will handle the set update
        }
    }

    fun getSelectedApps(): Set<String> = selectedApps

    override fun getItemCount() = appList.size
}
