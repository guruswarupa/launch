package com.guruswarupa.launch

import android.app.AlertDialog
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
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import kotlin.apply

import android.provider.Settings
import android.view.Gravity
import android.widget.PopupMenu
import java.util.concurrent.Executors
import android.app.Activity

class AppAdapter(
    private val activity: MainActivity,
    var appList: MutableList<ResolveInfo>,
    private val searchBox: EditText,
    private val isGridMode: Boolean,
    private val context: Context // Added context
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private val usageStatsManager = AppUsageStatsManager(activity)
    private val usageCache = mutableMapOf<String, Pair<Long, Long>>() // packageName to (usageTime, timestamp)
    private val iconCache = mutableMapOf<String, Drawable>() // packageName to icon
    private val labelCache = mutableMapOf<String, String>() // packageName to label
    private val packageValidityCache = mutableMapOf<String, Boolean>() // Cache for app validity checks
    private val CACHE_DURATION = 60000L // 1 minute cache
    private val executor = Executors.newSingleThreadExecutor() // Executor for background tasks

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView? = view.findViewById(R.id.app_name)
        val appUsageTime: TextView? = view.findViewById(R.id.app_usage_time)
    }

    fun updateAppList(newAppList: List<ResolveInfo>) {
        executor.execute {
            val newItems = mutableListOf<ResolveInfo>()

            // Create a copy of the list to avoid concurrent modification
            val safeCopy = ArrayList(newAppList)
            for (app in safeCopy) {
                newItems.add(app)
            }

            // Update UI on main thread
            (context as? Activity)?.runOnUiThread {
                appList.clear()
                appList.addAll(newItems)
                notifyDataSetChanged()
            }
        }
    }

    private fun getUsageTimeWithCache(packageName: String): Long {
        val currentTime = System.currentTimeMillis()
        val cached = usageCache[packageName]

        return if (cached != null && (currentTime - cached.second) < CACHE_DURATION) {
            cached.first
        } else {
            val usageTime = usageStatsManager.getAppUsageTime(packageName)
            usageCache[packageName] = Pair(usageTime, currentTime)
            usageTime
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) R.layout.app_item_grid else R.layout.app_item
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName

        // Always show the name in both grid and list mode
        holder.appName?.visibility = View.VISIBLE

        // Show usage time only in list mode - defer formatting until needed
        if (!isGridMode && holder.appUsageTime != null) {
            // Use cached formatted time if available
            val usageTime = getUsageTimeWithCache(packageName)
            val formattedTime = usageStatsManager.formatUsageTime(usageTime)
            holder.appUsageTime?.text = formattedTime
            holder.appUsageTime?.visibility = View.VISIBLE
        } else {
            holder.appUsageTime?.visibility = View.GONE
        }

        when (packageName) {
            "contact_search" -> {
                // Display contact with phone icon
                holder.appIcon.setImageResource(R.drawable.ic_phone) // Ensure ic_phone is in res/drawable
                holder.appName?.text = appInfo.activityInfo.name // Contact name
                holder.itemView.setOnClickListener {
                    showCallConfirmationDialog(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "whatsapp_contact" -> {
                holder.appIcon.setImageResource(R.drawable.ic_whatsapp) // WhatsApp icon
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    activity.openWhatsAppChat(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "sms_contact" -> {
                // Display SMS option
                holder.appIcon.setImageResource(R.drawable.ic_message)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    activity.openSMSChat(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "play_store_search" -> {
                // Display Play Store search option
                holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.android.vending"))
                holder.appName?.text = "Search ${appInfo.activityInfo.name} on Play Store"
                holder.itemView.setOnClickListener {
                    val encodedQuery = Uri.encode(appInfo.activityInfo.name)
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/search?q=$encodedQuery")
                        )
                    )
                    searchBox.text.clear()
                }
            }

            "maps_search" -> {
                // Set the Google Maps icon
                holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.google.android.apps.maps"))
                holder.appName?.text = "Search ${appInfo.activityInfo.name} in Google Maps"
                holder.itemView.setOnClickListener {
                    // Create an Intent to open Google Maps
                    val gmmIntentUri =
                        Uri.parse("geo:0,0?q=${Uri.encode(appInfo.activityInfo.name)}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        activity.startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Google Maps not installed.", Toast.LENGTH_SHORT)
                            .show()
                    }
                    searchBox.text.clear()
                }
            }

            "yt_search" -> {
                // Set the YouTube icon
                holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.google.android.youtube"))
                holder.appName?.text = "Search ${appInfo.activityInfo.name} on YouTube"
                holder.itemView.setOnClickListener {
                    // Create an Intent to open YouTube search
                    val ytIntentUri =
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(appInfo.activityInfo.name)}")
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytIntentUri)
                    ytIntent.setPackage("com.google.android.youtube") // Open in YouTube app if installed
                    try {
                        activity.startActivity(ytIntent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            activity,
                            "YouTube app not installed. Opening in browser.",
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                ytIntentUri
                            )
                        ) // Open in browser as fallback
                    }
                    searchBox.text.clear()
                }
            }

            "browser_search" -> {
                // Display browser search option
                holder.appIcon.setImageResource(R.drawable.ic_browser)
                holder.appName?.text = "Search ${appInfo.activityInfo.name} in Browser"
                holder.itemView.setOnClickListener {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=${appInfo.activityInfo.name}")
                        )
                    )
                    searchBox.text.clear()
                }
            }

            "math_result" -> {
                holder.appIcon.setImageResource(R.drawable.ic_calculator) // Ensure ic_calculator is in res/drawable
                holder.appName?.text = appInfo.activityInfo.name
            }

            else -> {
                // For real apps, use aggressive caching to improve performance
                // Check app validity once and cache result
                val isValidApp = packageValidityCache.getOrPut(packageName) {
                    appInfo.activityInfo?.applicationInfo != null
                }

                // Use cached label or load and cache it
                val label = labelCache.getOrPut(packageName) {
                    try {
                        if (isValidApp) {
                            appInfo.loadLabel(activity.packageManager)?.toString()
                                ?: appInfo.activityInfo.packageName
                        } else {
                            // For custom ResolveInfo objects, use the name directly
                            appInfo.activityInfo?.name ?: "Unknown"
                        }
                    } catch (e: Exception) {
                        appInfo.activityInfo?.packageName ?: "Unknown"
                    }
                }
                holder.appName?.text = label

                // Use cached icon or load and cache it
                val icon = iconCache.getOrPut(packageName) {
                    try {
                        if (isValidApp) {
                            appInfo.loadIcon(activity.packageManager)
                        } else {
                            activity.getDrawable(R.drawable.ic_default_app_icon)
                        }
                    } catch (e: Exception) {
                        activity.getDrawable(R.drawable.ic_default_app_icon)
                    } as Drawable
                }
                holder.appIcon.setImageDrawable(icon)

                holder.itemView.setOnClickListener {
                    val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        val prefs = activity.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("usage_$packageName", 0)
                        prefs.edit().putInt("usage_$packageName", currentCount + 1).apply()

                        if (activity.appLockManager.isAppLocked(packageName)) {
                            activity.appLockManager.verifyPin { isAuthenticated ->
                                if (isAuthenticated) {
                                    activity.startActivity(intent)
                                }
                            }
                        } else {
                            activity.startActivity(intent)
                        }
                        activity.runOnUiThread {
                            searchBox.text.clear()
                            activity.appSearchManager.filterAppsAndContacts("")
                        }
                    } else {
                        Toast.makeText(activity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                    }
                }

                holder.itemView.setOnLongClickListener {
                    showAppContextMenu(holder.itemView, packageName, appInfo)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = appList.size

    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.app_info -> {
                    showAppInfo(packageName)
                    true
                }
                R.id.share_app -> {
                    shareApp(packageName, appInfo)
                    true
                }
                R.id.uninstall_app -> {
                    uninstallApp(packageName)
                    true
                }
                R.id.add_to_dock -> {
                    addToDock(packageName, appInfo)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun showAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    private fun shareApp(packageName: String, appInfo: ResolveInfo) {
        val appName = appInfo.loadLabel(activity.packageManager).toString()
        val shareManager = ShareManager(activity)
        shareManager.shareApk(packageName, appName)
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    private fun addToDock(packageName: String, appInfo: ResolveInfo) {
        activity.appDockManager.addAppToDock(packageName)
        Toast.makeText(activity, "Added ${appInfo.loadLabel(activity.packageManager)} to dock", Toast.LENGTH_SHORT).show()
    }

    fun showCallConfirmationDialog(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName) // Fetch phone number for the contact

        AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle("Call $contactName?")
            .setMessage("Phone: $phoneNumber\nDo you want to proceed?")
            .setPositiveButton("Call") { _, _ ->
                call(phoneNumber)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun call(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        activity.startActivity(intent)
    }

    private fun getPhoneNumberForContact(contactName: String): String {
        val contentResolver: ContentResolver = activity.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(contactName)

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        var phoneNumber: String? = null

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    phoneNumber = it.getString(numberIndex)
                }
            }
        }

        return phoneNumber ?: "Not found"
    }
}