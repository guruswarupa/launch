
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
    private val focusModeManager: FocusModeManager
) : RecyclerView.Adapter<FocusModeAppAdapter.ViewHolder>() {

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
        holder.appCheckbox.isChecked = focusModeManager.getAllowedApps().contains(packageName)

        holder.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                focusModeManager.addAllowedApp(packageName)
            } else {
                focusModeManager.removeAllowedApp(packageName)
            }
        }

        holder.itemView.setOnClickListener {
            holder.appCheckbox.isChecked = !holder.appCheckbox.isChecked
        }
    }

    override fun getItemCount() = appList.size
}
