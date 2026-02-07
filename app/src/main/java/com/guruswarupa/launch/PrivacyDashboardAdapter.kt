package com.guruswarupa.launch

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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

    private val permissionLabels = mapOf(
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.RECORD_AUDIO to "Microphone",
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.ACCESS_FINE_LOCATION to "Location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Location (Approx)",
        Manifest.permission.READ_EXTERNAL_STORAGE to "Storage",
        Manifest.permission.WRITE_EXTERNAL_STORAGE to "Storage (Write)",
        Manifest.permission.READ_SMS to "SMS",
        Manifest.permission.CALL_PHONE to "Phone",
        Manifest.permission.POST_NOTIFICATIONS to "Notifications",
        "android.permission.READ_MEDIA_IMAGES" to "Photos",
        "android.permission.READ_MEDIA_VIDEO" to "Videos",
        "android.permission.READ_MEDIA_AUDIO" to "Audio"
    )

    private val permissionDescriptions = mapOf(
        Manifest.permission.CAMERA to "Allows the app to take pictures and record videos. This can be used for scanning QR codes, taking profile pictures, or making video calls.",
        Manifest.permission.RECORD_AUDIO to "Allows the app to record audio using the microphone. This is used for voice messages, voice search, or recording video with sound. Be careful if an app accesses this without reason.",
        Manifest.permission.READ_CONTACTS to "Allows the app to read your contact list. This helps in finding friends, suggesting contacts to message, or showing contact names.",
        Manifest.permission.ACCESS_FINE_LOCATION to "Allows the app to access your precise location (GPS). Highly sensitive data that reveals exactly where you are.",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Allows the app to access your approximate location. Less sensitive than precise location but still reveals your general area.",
        Manifest.permission.READ_EXTERNAL_STORAGE to "Allows the app to read files from your storage. Apps can access your personal documents and files with this.",
        Manifest.permission.WRITE_EXTERNAL_STORAGE to "Allows the app to save or modify files in your storage.",
        Manifest.permission.READ_SMS to "Allows the app to read your text messages. This is highly sensitive as it can include OTPs and private conversations.",
        Manifest.permission.CALL_PHONE to "Allows the app to make phone calls. This could lead to unexpected charges if abused.",
        Manifest.permission.POST_NOTIFICATIONS to "Allows the app to show you notifications. Can be used for spam if not managed.",
        "android.permission.READ_MEDIA_IMAGES" to "Allows the app to specifically access your photos.",
        "android.permission.READ_MEDIA_VIDEO" to "Allows the app to specifically access your videos.",
        "android.permission.READ_MEDIA_AUDIO" to "Allows the app to specifically access your audio files."
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
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        holder.packageName.text = app.packageName
        
        holder.sideloadedBadge.visibility = if (app.isSideloaded) View.VISIBLE else View.GONE

        if (app.grantedPermissions.isEmpty()) {
            holder.permissionsSummary.text = "No sensitive permissions accessed."
            holder.permissionsSummary.alpha = 0.5f
            holder.permissionCount.visibility = View.GONE
        } else {
            val names = app.grantedPermissions.map { permissionLabels[it] ?: it.split(".").last() }
            holder.permissionsSummary.text = "Access to: ${names.joinToString(", ")}"
            holder.permissionsSummary.alpha = 1.0f
            
            holder.permissionCount.visibility = View.VISIBLE
            holder.permissionCount.text = app.grantedPermissions.size.toString()
            
            val color = when (app.severity) {
                2 -> 0xFFFF4444.toInt() // High - Red
                1 -> 0xFFFFBB33.toInt() // Medium - Orange/Yellow
                else -> 0xFF99CC00.toInt() // Low - Green
            }
            holder.permissionCount.setTextColor(color)
            // Also tint the background if possible, or just the text
        }

        holder.detailsContainer.visibility = if (app.isExpanded) View.VISIBLE else View.GONE
        
        if (app.isExpanded) {
            val details = StringBuilder()
            if (app.grantedPermissions.isEmpty()) {
                details.append("This app does not seem to have any sensitive permissions granted.")
            } else {
                details.append("Detailed analysis:\n\n")
                app.grantedPermissions.forEach { perm ->
                    val label = permissionLabels[perm] ?: perm.split(".").last()
                    val desc = permissionDescriptions[perm] ?: "Allows access to $label."
                    details.append("• $label: $desc\n\n")
                }
            }
            holder.appDetails.text = details.toString().trim()
            
            holder.managePermissionsButton.setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", app.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                holder.itemView.context.startActivity(intent)
            }
        }

        holder.itemView.setOnClickListener {
            app.isExpanded = !app.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = apps.size

    fun updateData(newApps: List<AppPrivacyInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
