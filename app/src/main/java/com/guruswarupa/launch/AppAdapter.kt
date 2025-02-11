package com.guruswarupa.launch

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val activity: MainActivity,
    var appList: MutableList<ResolveInfo>,
    private val searchBox: EditText,
    private val isGridMode: Boolean
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    // ViewHolder class
    class ViewHolder(view: View, isGrid: Boolean) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView? = if (!isGrid) view.findViewById(R.id.app_name) else null // Hide in grid mode
    }

    // Determine layout type based on isGridMode
    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) R.layout.app_item_grid else R.layout.app_item
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view, isGridMode)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName

        if (packageName == "play_store_search") {
            holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.android.vending"))
            holder.appName?.text = "Search ${appInfo.activityInfo.name} on Play Store"
            holder.itemView.setOnClickListener {
                val encodedQuery = Uri.encode(appInfo.activityInfo.name)
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encodedQuery")))
                searchBox.text.clear()
            }
        } else if (packageName == "browser_search") {
            holder.appIcon.setImageResource(R.drawable.ic_browser)
            holder.appName?.text = "Search ${appInfo.activityInfo.name} in Browser"
            holder.itemView.setOnClickListener {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${appInfo.activityInfo.name}")))
                searchBox.text.clear()
            }
        } else {
            holder.appIcon.setImageDrawable(appInfo.loadIcon(activity.packageManager))
            holder.appName?.text = appInfo.loadLabel(activity.packageManager) // Only if in list mode

            holder.itemView.setOnClickListener {
                val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    activity.startActivity(intent)
                    searchBox.text.clear()
                } else {
                    Toast.makeText(activity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
            }

            holder.itemView.setOnLongClickListener {
                uninstallApp(packageName)
                true
            }
        }
    }

    override fun getItemCount(): Int = appList.size

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }
}
