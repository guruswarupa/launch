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
import com.guruswarupa.launch.managers.WebAppIconFetcher
import com.guruswarupa.launch.managers.WebAppManager
import com.guruswarupa.launch.utils.AppDisplayHelper

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

        holder.itemView.tag = packageName
        holder.appName.text = AppDisplayHelper.getLabel(app, packageManager)

        if (WebAppManager.isWebAppPackage(packageName)) {
            holder.appIcon.setImageResource(R.drawable.ic_browser)
            val siteUrl = app.activityInfo.nonLocalizedLabel?.toString().orEmpty()
            if (siteUrl.isNotBlank()) {
                WebAppIconFetcher.loadIcon(holder.itemView.context, siteUrl) { drawable ->
                    if (holder.itemView.tag == packageName && drawable != null) {
                        holder.appIcon.setImageDrawable(drawable)
                    }
                }
            }
        } else {
            try {
                holder.appIcon.setImageDrawable(app.loadIcon(packageManager))
            } catch (_: Exception) {
                holder.appIcon.setImageDrawable(null)
            }
        }

        holder.appCheckbox.setOnCheckedChangeListener(null)
        holder.appCheckbox.isChecked = selectedPackages.contains(packageName)

        holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (selectedPackages.contains(packageName) != isChecked) {
                onSelectionChanged(packageName, isChecked)
            }
        }

        holder.itemView.setOnClickListener {
            val newState = !holder.appCheckbox.isChecked
            holder.appCheckbox.isChecked = newState
        }
    }

    override fun getItemCount(): Int = apps.size
}
