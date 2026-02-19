package com.guruswarupa.launch

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import net.objecthunter.exp4j.ExpressionBuilder
import java.util.concurrent.Executors

class AppSearchManager(
    private val packageManager: PackageManager,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val searchBox: EditText,
    private val contactsList: List<String>,
    private val appMetadataCache: Map<String, AppMetadata>? = null,
    private val isAppFiltered: ((String) -> Boolean)? = null
) {
    private val handler = Handler(Looper.getMainLooper())
    private val searchExecutor = Executors.newSingleThreadExecutor() // Background thread for search operations
    private val debounceRunnable = Runnable {
        // Run search on background thread
        val query = searchBox.text.toString()
        searchExecutor.execute {
            filterAppsAndContacts(query)
        }
    }

    private val appLabelCache = mutableMapOf<String, String>()

    private var appLabelMap: Map<ResolveInfo, String> = emptyMap()
    private val searchCache = mutableMapOf<String, List<ResolveInfo>>()

    init {
        // Load labels in background thread
        searchExecutor.execute {
            refreshAppList()
        }
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(debounceRunnable)
                handler.postDelayed(debounceRunnable, 30)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    fun updateContactsList() {
        // Update contacts list and clear search cache
        searchCache.clear()
        // Refresh app list in background
        searchExecutor.execute {
            refreshAppList()
        }
    }

    private fun refreshAppList() {
        // Filter out apps that should be hidden (e.g., in focus mode)
        val filteredAppList = if (isAppFiltered != null) {
            fullAppList.filter { !isAppFiltered(it.activityInfo.packageName) }
        } else {
            fullAppList
        }
        
        appLabelMap = filteredAppList.associateWith { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // Use pre-loaded metadata cache if available
            val cachedMetadata = appMetadataCache?.get(packageName)
            cachedMetadata?.label?.lowercase()
                ?: appLabelCache.getOrPut(packageName) {
                    try {
                        resolveInfo.loadLabel(packageManager).toString().lowercase()
                    } catch (_: Exception) {
                        packageName.lowercase()
                    }
                }
        }
        searchCache.clear()
    }

    /**
     * Generates a sort key that puts numbers and '#' at the end.
     */
    private fun getSortKey(label: String): String {
        if (label.isEmpty()) return label
        val firstChar = label[0]
        return if (firstChar.isDigit() || firstChar == '#') {
            "\uFFFF$label"
        } else {
            label
        }
    }

    fun filterAppsAndContacts(query: String) {
        // This method is already called from background thread (searchExecutor)
        val queryLower = query.lowercase().trim()

        val newFilteredList = ArrayList<ResolveInfo>()
        
        // Removed unused usageCache population

        if (queryLower.isNotEmpty()) {
            evaluateMathExpression(query)?.let { result ->
                newFilteredList.add(createMathResultOption(query, result))
            } ?: run {
                // Use more efficient filtering with early termination for exact matches
                val exactMatches = ArrayList<ResolveInfo>()
                val partialMatches = ArrayList<ResolveInfo>()

                appLabelMap.forEach { (info, label) ->
                    when {
                        label == queryLower -> exactMatches.add(info)
                        label.startsWith(queryLower) -> partialMatches.add(0, info)
                        label.contains(queryLower) -> partialMatches.add(info)
                    }
                }

                // Filter out apps that should be hidden (e.g., in focus mode)
                val filteredExactMatches = if (isAppFiltered != null) {
                    exactMatches.filter { !isAppFiltered(it.activityInfo.packageName) }
                } else {
                    exactMatches
                }
                val filteredPartialMatches = if (isAppFiltered != null) {
                    partialMatches.filter { !isAppFiltered(it.activityInfo.packageName) }
                } else {
                    partialMatches
                }

                // Sort alphabetically for matches found
                val sortedExact = filteredExactMatches.sortedBy {
                    getSortKey(appLabelMap[it]?.lowercase() ?: it.activityInfo.packageName.lowercase())
                }
                val sortedPartial = filteredPartialMatches.sortedBy {
                    getSortKey(appLabelMap[it]?.lowercase() ?: it.activityInfo.packageName.lowercase())
                }

                // 1. Add apps first
                newFilteredList.addAll(sortedExact)
                newFilteredList.addAll(sortedPartial)

                // 2. Add contacts second
                contactsList.asSequence()
                    .filter { it.contains(query, ignoreCase = true) }
                    .take(5) // Limit contact results
                    .forEach { contact ->
                        newFilteredList.add(createWhatsAppContactOption(contact))
                        newFilteredList.add(createSmsOption(contact))
                        newFilteredList.add(createContactOption(contact))
                    }

                // 3. Always add search options at the end (Play Store, Maps, YouTube, Browser)
                newFilteredList.add(createPlayStoreSearchOption(query))
                newFilteredList.add(createGoogleMapsSearchOption(query))
                newFilteredList.add(createYoutubeSearchOption(query))
                newFilteredList.add(createBrowserSearchOption(query))
            }
        } else {
            // Cache sorted app list for empty queries
            val emptyQueryKey = ""
            val cachedEmpty = searchCache[emptyQueryKey]
            if (cachedEmpty != null) {
                newFilteredList.addAll(cachedEmpty)
            } else {
                // Ensure appLabelMap is ready before sorting
                if (appLabelMap.isEmpty() && fullAppList.isNotEmpty()) {
                    refreshAppList()
                }
                
                // Filter out apps that should be hidden (e.g., in focus mode)
                val appsToSort = if (isAppFiltered != null) {
                    fullAppList.filter { !isAppFiltered(it.activityInfo.packageName) }
                } else {
                    fullAppList
                }
                
                // Sort alphabetically by app name
                val sorted = appsToSort.sortedBy {
                    val label = appLabelMap[it]?.lowercase() ?: run {
                        // Use pre-loaded metadata cache if available
                        val cachedMetadata = appMetadataCache?.get(it.activityInfo.packageName)
                        cachedMetadata?.label?.lowercase()
                            ?: // Fallback: load label only if not in cache
                            try {
                                appLabelCache.getOrPut(it.activityInfo.packageName) {
                                    it.loadLabel(packageManager).toString().lowercase()
                                }.lowercase()
                            } catch (_: Exception) {
                                it.activityInfo.packageName.lowercase()
                            }
                    }
                    getSortKey(label)
                }
                newFilteredList.addAll(sorted)
                searchCache[emptyQueryKey] = ArrayList(sorted)
            }
        }

        // Update adapter on main thread
        handler.post {
            adapter.updateAppList(newFilteredList)
        }
    }

    private val cachedResolveInfos = mutableMapOf<String, ResolveInfo>()

    private fun evaluateMathExpression(expression: String): String? {
        return try {
            val result = ExpressionBuilder(expression).build().evaluate()
            result.toString()
        } catch (_: Exception) {
            null // Return null if it's not a valid math expression
        }
    }

    private fun createMathResultOption(expression: String, result: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("math_result_$expression") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "math_result"
                    name = "$expression = $result"
                }
            }
        }
    }

    private fun createWhatsAppContactOption(contact: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("whatsapp_contact_$contact") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "whatsapp_contact"
                    name = contact
                }
            }
        }
    }

    private fun createGoogleMapsSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("maps_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "maps_search"
                    name = query
                }
            }
        }
    }

    private fun createYoutubeSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("yt_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "yt_search"
                    name = query
                }
            }
        }
    }

    private fun createPlayStoreSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("play_store_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "play_store_search"
                    name = query
                }
            }
        }
    }

    private fun createBrowserSearchOption(query: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("browser_search_$query") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "browser_search"
                    name = query
                }
            }
        }
    }

    private fun createSmsOption(contact: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("sms_contact_$contact") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "sms_contact"
                    name = contact
                }
            }
        }
    }

    private fun createContactOption(contact: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("contact_search_$contact") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "contact_search"
                    name = contact
                }
            }
        }
    }
    
    /**
     * Cleanup method to shutdown executor when done
     */
    fun cleanup() {
        searchExecutor.shutdown()
    }
}
