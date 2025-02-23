package com.guruswarupa.launch

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class AppSearchManager(
    private val pm: PackageManager,
    private val apps: MutableList<ResolveInfo>,
    private var allApps: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val rv: RecyclerView,
    private val search: EditText,
    private val contacts: List<String>
) {
    private var searchJob: Job? = null
    private val searchScope = CoroutineScope(Dispatchers.Main)
    private var currentQuery: String = ""
    private val appLabels = ConcurrentHashMap<ResolveInfo, String>() // Cache app labels

    init {
        // Pre-cache app labels for faster lookup
        CoroutineScope(Dispatchers.IO).launch {
            allApps.forEach { app ->
                appLabels[app] = app.loadLabel(pm).toString().lowercase()
            }
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Implementation (can be empty if not needed)
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newQuery = s.toString()
                if (newQuery != currentQuery) {
                    currentQuery = newQuery
                    searchJob?.cancel() // Cancel previous job
                    searchJob = searchScope.launch {
                        delay(300) // Debounce delay
                        filter(newQuery)
                    }
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {
            }
        })

        search.setOnLongClickListener {
            try {
                search.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Toast.makeText(search.context, "Browser not found!", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    fun updateAppLabels() {
        appLabels.clear()
        CoroutineScope(Dispatchers.IO).launch {
            allApps.forEach { app ->
                appLabels[app] = app.loadLabel(pm).toString().lowercase()
            }
        }
    }

    private suspend fun filter(query: String) = withContext(Dispatchers.Default) {
        val filtered = mutableListOf<ResolveInfo>()
        if (query.isNotEmpty()) {
            val lowerQuery = query.lowercase()

            val validApps = allApps.filter { appLabels[it]?.contains(lowerQuery) == true } //Filter valid apps.
            filtered.addAll(validApps)

            contacts.filter { it.lowercase().contains(lowerQuery) }.groupBy { it }.forEach { (_, list) ->
                list.map { createWhatsApp(it) }.let { filtered.addAll(it) }
                list.forEach { filtered.add(createContact(it)); filtered.add(createSms(it)) }
            }
            if (filtered.isEmpty()) {
                filtered.add(createPlayStore(query)); filtered.add(createMaps(query)); filtered.add(createYoutube(query)); filtered.add(createBrowser(query))
            }
        } else {
            // Show only the app list when search is empty
            filtered.addAll(allApps.sortedBy { appLabels[it] })
        }

        withContext(Dispatchers.Main) {
            apps.clear()
            apps.addAll(filtered)
            adapter.notifyDataSetChanged()
        }
    }

    fun removeInvalidApps() {
        val iterator = allApps.iterator()
        while (iterator.hasNext()) {
            val app = iterator.next()
            try {
                pm.getApplicationLabel(app.activityInfo.applicationInfo)
            } catch (e: Exception) {
                iterator.remove()
            }
        }
        updateAppLabels()
    }

    private fun createWhatsApp(contact: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "whatsapp_contact"; name = contact } }
    private fun createMaps(query: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "maps_search"; name = query } }
    private fun createYoutube(query: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "yt_search"; name = query } }
    private fun createPlayStore(query: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "play_store_search"; name = query } }
    private fun createBrowser(query: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "browser_search"; name = query } }
    private fun createSms(contact: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "sms_contact"; name = contact } }
    private fun createContact(contact: String) = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "contact_search"; name = contact } }
}