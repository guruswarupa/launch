package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AppSearchManager(
    private val packageManager: PackageManager,
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val recyclerView: RecyclerView,
    private val searchBox: EditText,
    private val contactsList: List<String> // List of contact names or numbers
) {

    init {
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAppsAndContacts(s.toString())
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBox.setOnLongClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                searchBox.context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(searchBox.context, "No browser found!", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    fun filterAppsAndContacts(query: String) {
        val newFilteredList = mutableListOf<ResolveInfo>()

        if (query.isNotEmpty()) {
            // Filter installed apps
            newFilteredList.addAll(fullAppList.filter {
                it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
            })

            // Filter contacts
            val filteredContacts = contactsList.filter {
                it.contains(query, ignoreCase = true)
            }

            filteredContacts.forEach { contact ->
                newFilteredList.add(createContactOption(contact))
            }

            if (newFilteredList.isEmpty()) {
                newFilteredList.add(createPlayStoreSearchOption(query))
                newFilteredList.add(createBrowserSearchOption(query))
            }
        } else {
            newFilteredList.addAll(fullAppList.sortedBy { it.loadLabel(packageManager).toString().lowercase() })
        }

        // Update the actual list
        appList.clear()
        appList.addAll(newFilteredList)
        adapter.notifyDataSetChanged() // Ensure UI updates
    }

    private fun createPlayStoreSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "play_store_search"
                name = "Search Play Store for \"$query\""
            }
        }
    }

    private fun createBrowserSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "browser_search"
                name = "Search \"$query\" in Browser"
            }
        }
    }

    private fun createContactOption(contact: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "contact_search"
                name = contact  // Display contact name or number
            }
        }
    }
}
