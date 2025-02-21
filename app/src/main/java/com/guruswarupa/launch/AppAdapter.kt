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
import android.database.Cursor
import android.provider.ContactsContract

class AppAdapter(
    private val activity: MainActivity,
    var appList: MutableList<ResolveInfo>,
    private val searchBox: EditText,
    private val isGridMode: Boolean
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View, isGrid: Boolean) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView? = view.findViewById(R.id.app_name)
    }

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

        // Always show the name in both grid and list mode
        holder.appName?.visibility = View.VISIBLE

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
                    openWhatsAppChat(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }
            "sms_contact" -> {
                // Display SMS option
                holder.appIcon.setImageResource(R.drawable.ic_message)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    openSMSChat(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }
            "play_store_search" -> {
                // Display Play Store search option
                holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.android.vending"))
                holder.appName?.text = "Search ${appInfo.activityInfo.name} on Play Store"
                holder.itemView.setOnClickListener {
                    val encodedQuery = Uri.encode(appInfo.activityInfo.name)
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encodedQuery")))
                    searchBox.text.clear()
                }
            }
            "maps_search" -> {
                // Set the Google Maps icon
                holder.appIcon.setImageDrawable(activity.packageManager.getApplicationIcon("com.google.android.apps.maps"))
                holder.appName?.text = "Search ${appInfo.activityInfo.name} in Google Maps"
                holder.itemView.setOnClickListener {
                    // Create an Intent to open Google Maps
                    val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(appInfo.activityInfo.name)}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        activity.startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Google Maps not installed.", Toast.LENGTH_SHORT).show()
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
                    val ytIntentUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(appInfo.activityInfo.name)}")
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytIntentUri)
                    ytIntent.setPackage("com.google.android.youtube") // Open in YouTube app if installed
                    try {
                        activity.startActivity(ytIntent)
                    } catch (e: Exception) {
                        Toast.makeText(activity, "YouTube app not installed. Opening in browser.", Toast.LENGTH_SHORT).show()
                        activity.startActivity(Intent(Intent.ACTION_VIEW, ytIntentUri)) // Open in browser as fallback
                    }
                    searchBox.text.clear()
                }
            }
            "browser_search" -> {
                // Display browser search option
                holder.appIcon.setImageResource(R.drawable.ic_browser)
                holder.appName?.text = "Search ${appInfo.activityInfo.name} in Browser"
                holder.itemView.setOnClickListener {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${appInfo.activityInfo.name}")))
                    searchBox.text.clear()
                }
            }
            else -> {
                // Display installed app
                holder.appIcon.setImageDrawable(appInfo.loadIcon(activity.packageManager))
                holder.appName?.text = appInfo.loadLabel(activity.packageManager) // Display app name

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
    }

    override fun getItemCount(): Int = appList.size

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    private fun showCallConfirmationDialog(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName) // Fetch phone number for the contact

        AlertDialog.Builder(activity)
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

    private fun openWhatsAppChat(contactName: String) {
        val contentResolver: ContentResolver = activity.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                var phoneNumber = it.getString(0).replace(" ", "").replace("-", "").replace("(", "").replace(")", "")

                // Ensure it has a country code (modify +91 to your country code if needed)
                if (!phoneNumber.startsWith("+")) {
                    phoneNumber = "+91$phoneNumber"  // Change +91 to your country code
                }

                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("https://wa.me/${Uri.encode(phoneNumber)}")
                    intent.setPackage("com.whatsapp")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(activity, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openSMSChat(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber") // Ensures only SMS apps respond
            putExtra("sms_body", "") // Optional: You can pre-fill the message here
        }

        try {
            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "No SMS app installed!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to open messaging app.", Toast.LENGTH_SHORT).show()
        }
    }


    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }
}
