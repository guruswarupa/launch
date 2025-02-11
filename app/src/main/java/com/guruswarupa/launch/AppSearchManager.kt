package com.guruswarupa.launch

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.recyclerview.widget.RecyclerView

class AppSearchManager(
    private val packageManager: android.content.pm.PackageManager,
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val recyclerView: RecyclerView,
    private val searchBox: EditText
) {

    init {
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBox.setOnLongClickListener {
            // Create an Intent to open a new tab in the default browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Ensures the browser opens in a new task
            try {
                // Use the context from searchBox to call startActivity
                searchBox.context.startActivity(intent)  // Launch the default browser
            } catch (e: Exception) {
                Toast.makeText(searchBox.context, "No browser found!", Toast.LENGTH_SHORT).show()
            }
            true  // Consume the long press event
        }
    }

    fun filterApps(query: String) {
        if (query.isNotEmpty()) {
            // Filter the apps based on the query
            val filteredList = fullAppList.filter {
                it.loadLabel(packageManager).toString().contains(query, ignoreCase = true)
            }.toMutableList()

            appList.clear()
            appList.addAll(filteredList)

            if (filteredList.isEmpty()) {
                // Show two options if no results found
                appList.add(createPlayStoreSearchOption(query))
                appList.add(createBrowserSearchOption(query))
            }
        } else {
            // When search is cleared, reset and sort alphabetically
            appList.clear()
            appList.addAll(fullAppList.sortedBy { it.loadLabel(packageManager).toString().lowercase() })
        }

        adapter.notifyDataSetChanged()
    }

    private fun createPlayStoreSearchOption(query: String): ResolveInfo {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "play_store_search"
                name = query
            }
        }
        return resolveInfo
    }

    private fun createBrowserSearchOption(query: String): ResolveInfo {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "browser_search"
                name = query
            }
        }
        return resolveInfo
    }
}
