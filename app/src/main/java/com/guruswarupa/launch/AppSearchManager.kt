package com.guruswarupa.launch

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

            // Group contacts by name
            val groupedContacts = contactsList.filter {
                it.contains(query, ignoreCase = true)
            }.groupBy { it }

            // Create contact options
            groupedContacts.forEach { (contactName, contactList) ->
                // Create WhatsApp contacts for each contact in the group
                val filteredWhatsAppContacts = contactList.map { createWhatsAppContactOption(it) }

                // Add WhatsApp contacts first
                newFilteredList.addAll(filteredWhatsAppContacts)

                // Add SMS and phone options for each contact
                contactList.forEach { contact ->
                    newFilteredList.add(createContactOption(contact))
                    newFilteredList.add(createSmsOption(contact))
                }
            }

            // Fallback if no results found
            if (newFilteredList.isEmpty()) {
                newFilteredList.add(createPlayStoreSearchOption(query))
                newFilteredList.add(createGoogleMapsSearchOption(query))
                newFilteredList.add(createYoutubeSearchOption(query))
                newFilteredList.add(createBrowserSearchOption(query))
            }
        } else {
            // If query is empty, show all apps sorted
            newFilteredList.addAll(fullAppList.sortedBy { it.loadLabel(packageManager).toString().lowercase() })
        }

        // Update the actual list
        appList.clear()
        appList.addAll(newFilteredList)
        adapter.notifyDataSetChanged() // Ensure UI updates
    }

    private fun createWhatsAppContactOption(contact: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "whatsapp_contact"
                name = contact  // Display contact name
            }
        }
    }

    private fun createGoogleMapsSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "maps_search"
                name = query
            }
        }
    }

    private fun createYoutubeSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "yt_search"
                name = query
            }
        }
    }

    private fun createFileSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "file_search"
                name = query
            }
        }
    }

    private fun createPlayStoreSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "play_store_search"
                name = query
            }
        }
    }

    private fun createBrowserSearchOption(query: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "browser_search"
                name = query

            }
        }
    }

    private fun createSmsOption(contact: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "sms_contact"
                name = contact
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