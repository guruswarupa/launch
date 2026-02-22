package com.guruswarupa.launch.ui.adapters

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
data class AppPrivacyInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val grantedPermissions: List<String>,
    val severity: Int, // 0: Trace/Low, 1: Medium, 2: High
    val isSideloaded: Boolean = false,
    var isExpanded: Boolean = false
)

class PrivacyDashboardAdapter(
    private var apps: List<AppPrivacyInfo>
) : RecyclerView.Adapter<PrivacyDashboardAdapter.ViewHolder>() {

    @SuppressLint("InlinedApi")
    private val permissionLabels = mapOf(
        Manifest.permission.CAMERA to R.string.perm_camera,
        Manifest.permission.RECORD_AUDIO to R.string.perm_microphone,
        Manifest.permission.READ_CONTACTS to R.string.perm_contacts,
        Manifest.permission.ACCESS_FINE_LOCATION to R.string.perm_location,
        Manifest.permission.ACCESS_COARSE_LOCATION to R.string.perm_location_approx,
        Manifest.permission.READ_EXTERNAL_STORAGE to R.string.perm_storage,
        Manifest.permission.WRITE_EXTERNAL_STORAGE to R.string.perm_storage_write,
        Manifest.permission.READ_SMS to R.string.perm_sms,
        Manifest.permission.CALL_PHONE to R.string.perm_phone,
        Manifest.permission.POST_NOTIFICATIONS to R.string.perm_notifications,
        "android.permission.READ_MEDIA_IMAGES" to R.string.perm_photos,
        "android.permission.READ_MEDIA_VIDEO" to R.string.perm_videos,
        "android.permission.READ_MEDIA_AUDIO" to R.string.perm_audio
    )

    @SuppressLint("InlinedApi")
    private val permissionDescriptions = mapOf(
        Manifest.permission.CAMERA to R.string.desc_camera,
        Manifest.permission.RECORD_AUDIO to R.string.desc_microphone,
        Manifest.permission.READ_CONTACTS to R.string.desc_contacts,
        Manifest.permission.ACCESS_FINE_LOCATION to R.string.desc_location,
        Manifest.permission.ACCESS_COARSE_LOCATION to R.string.desc_location_approx,
        Manifest.permission.READ_EXTERNAL_STORAGE to R.string.desc_storage,
        Manifest.permission.WRITE_EXTERNAL_STORAGE to R.string.desc_storage_write,
        Manifest.permission.READ_SMS to R.string.desc_sms,
        Manifest.permission.CALL_PHONE to R.string.desc_phone,
        Manifest.permission.POST_NOTIFICATIONS to R.string.desc_notifications,
        "android.permission.READ_MEDIA_IMAGES" to R.string.desc_photos,
        "android.permission.READ_MEDIA_VIDEO" to R.string.desc_videos,
        "android.permission.READ_MEDIA_AUDIO" to R.string.desc_audio
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
        val packageName: TextView = view.findViewById(R.id.package_name)
        val permissionsSummary: TextView = view.findViewById(R.id.permissions_summary)
        val permissionCount: TextView = view.findViewById(R.id.permission_count)
        val detailsContainer: LinearLayout = view.findViewById(R.id.details_container)
        val appDetails: TextView = view.findViewById(R.id.app_details)
        val managePermissionsButton: Button = view.findViewById(R.id.manage_permissions_button)
        val sideloadedBadge: TextView = view.findViewById(R.id.sideloaded_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_privacy_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        val context = holder.itemView.context
        
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        
        holder.sideloadedBadge.visibility = if (app.isSideloaded) View.VISIBLE else View.GONE

        if (app.grantedPermissions.isEmpty()) {
            holder.permissionsSummary.text = context.getString(R.string.no_sensitive_permissions)
            holder.permissionsSummary.alpha = 0.5f
            holder.permissionCount.visibility = View.GONE
        } else {
            val names = app.grantedPermissions.map { 
                val resId = permissionLabels[it]
                if (resId != null) context.getString(resId) else it.split(".").last()
            }
            holder.permissionsSummary.text = context.getString(R.string.access_to_format, names.joinToString(", "))
            holder.permissionsSummary.alpha = 1.0f
            
            holder.permissionCount.visibility = View.VISIBLE
            holder.permissionCount.text = app.grantedPermissions.size.toString()
            
            val color = when (app.severity) {
                2 -> 0xFFFF4444.toInt() // High - Red
                1 -> 0xFFFFBB33.toInt() // Medium - Orange/Yellow
                else -> 0xFF99CC00.toInt() // Low - Green
            }
            holder.permissionCount.setTextColor(color)
        }

        holder.detailsContainer.visibility = if (app.isExpanded) View.VISIBLE else View.GONE
        
        if (app.isExpanded) {
            val details = StringBuilder()
            if (app.grantedPermissions.isEmpty()) {
                details.append(context.getString(R.string.no_sensitive_permissions_granted))
            } else {
                details.append(context.getString(R.string.detailed_analysis))
                app.grantedPermissions.forEach { perm ->
                    val labelRes = permissionLabels[perm]
                    val label = if (labelRes != null) context.getString(labelRes) else perm.split(".").last()
                    
                    val descRes = permissionDescriptions[perm]
                    val desc = if (descRes != null) {
                        context.getString(descRes)
                    } else {
                        context.getString(R.string.default_permission_description, label)
                    }
                    
                    details.append(context.getString(R.string.permission_detail_item, label, desc))
                }
            }
            holder.appDetails.text = details.toString().trim()
            
            holder.managePermissionsButton.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        holder.itemView.setOnClickListener {
            app.isExpanded = !app.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = apps.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newApps: List<AppPrivacyInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
