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
    private val act: MainActivity,
    var apps: MutableList<ResolveInfo>,
    private val search: EditText,
    private val isGrid: Boolean
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View, isGrid: Boolean) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView? = view.findViewById(R.id.app_name)
    }

    override fun getItemViewType(pos: Int) = if (isGrid) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(LayoutInflater.from(parent.context).inflate(if (viewType == 1) R.layout.app_item_grid else R.layout.app_item, parent, false), isGrid)

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val app = apps[pos]
        val pkg = app.activityInfo.packageName
        holder.name?.visibility = View.VISIBLE

        when (pkg) {
            "contact_search" -> {
                holder.icon.setImageResource(R.drawable.ic_phone); holder.name?.text = app.activityInfo.name
                holder.itemView.setOnClickListener { showCallDialog(app.activityInfo.name); search.text.clear() }
            }
            "whatsapp_contact" -> {
                holder.icon.setImageResource(R.drawable.ic_whatsapp); holder.name?.text = app.activityInfo.name
                holder.itemView.setOnClickListener { openWhatsApp(app.activityInfo.name); search.text.clear() }
            }
            "sms_contact" -> {
                holder.icon.setImageResource(R.drawable.ic_message); holder.name?.text = app.activityInfo.name
                holder.itemView.setOnClickListener { openSms(app.activityInfo.name); search.text.clear() }
            }
            "play_store_search" -> {
                holder.icon.setImageDrawable(act.packageManager.getApplicationIcon("com.android.vending")); holder.name?.text = "Search ${app.activityInfo.name} on Play Store"
                holder.itemView.setOnClickListener { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(app.activityInfo.name)}"))); search.text.clear() }
            }
            "maps_search" -> {
                holder.icon.setImageDrawable(act.packageManager.getApplicationIcon("com.google.android.apps.maps")); holder.name?.text = "Search ${app.activityInfo.name} in Google Maps"
                holder.itemView.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(app.activityInfo.name)}")).setPackage("com.google.android.apps.maps")
                    try { act.startActivity(intent) } catch (e: Exception) { Toast.makeText(act, "Maps not installed.", Toast.LENGTH_SHORT).show() }
                    search.text.clear()
                }
            }
            "yt_search" -> {
                holder.icon.setImageDrawable(act.packageManager.getApplicationIcon("com.google.android.youtube")); holder.name?.text = "Search ${app.activityInfo.name} on YouTube"
                holder.itemView.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(app.activityInfo.name)}")).setPackage("com.google.android.youtube")
                    try { act.startActivity(intent) } catch (e: Exception) { Toast.makeText(act, "YouTube not installed. Browser opened.", Toast.LENGTH_SHORT).show(); act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(app.activityInfo.name)}"))) }
                    search.text.clear()
                }
            }
            "browser_search" -> {
                holder.icon.setImageResource(R.drawable.ic_browser); holder.name?.text = "Search ${app.activityInfo.name} in Browser"
                holder.itemView.setOnClickListener { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${app.activityInfo.name}"))); search.text.clear() }
            }
            else -> {
                holder.icon.setImageDrawable(app.loadIcon(act.packageManager)); holder.name?.text = app.loadLabel(act.packageManager)
                holder.itemView.setOnClickListener {
                    act.packageManager.getLaunchIntentForPackage(pkg)?.let { act.startActivity(it); search.text.clear() } ?: Toast.makeText(act, "Cannot launch app", Toast.LENGTH_SHORT).show()
                }
                holder.itemView.setOnLongClickListener { uninstall(pkg); true }
            }
        }
    }

    override fun getItemCount() = apps.size

    private fun uninstall(pkg: String) {
        act.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")))
        apps.removeAll { it.activityInfo.packageName == pkg }; notifyDataSetChanged()
    }

    private fun showCallDialog(name: String) {
        val num = getNum(name)
        AlertDialog.Builder(act).setTitle("Call $name?").setMessage("Phone: $num\nDo you want to proceed?").setPositiveButton("Call") { _, _ -> call(num) }.setNegativeButton("Cancel", null).show()
    }

    private fun call(num: String) = act.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")))

    private fun getNum(name: String): String {
        var num = "Not found"
        act.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )?.use { cursor -> // Use 'use' to ensure cursor is closed
            if (cursor.moveToFirst()) {
                num = cursor.getString(0)
            }
        }
        return num
    }

    private fun openWhatsApp(name: String) {
        act.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )?.use { cursor -> // Use 'use' to ensure cursor is closed
            if (cursor.moveToFirst()) {
                var num = cursor.getString(0).replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
                if (!num.startsWith("+")) num = "+91$num"
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${Uri.encode(num)}")).setPackage("com.whatsapp"))
                } catch (e: Exception) {
                    Toast.makeText(act, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openSms(name: String) {
        val num = getNum(name)
        act.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", "").apply { if (resolveActivity(act.packageManager) == null) Toast.makeText(act, "No SMS app installed!", Toast.LENGTH_SHORT).show() })
    }
}