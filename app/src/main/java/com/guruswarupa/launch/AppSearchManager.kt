package com.guruswarupa.launch

import android.content.Context
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
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val searchBox: EditText,
    private val contactsList: List<String>,
    private val appMetadataCache: Map<String, MainActivity.AppMetadata>? = null
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

    private val contactNames: Set<String> by lazy {
        contactsList.map { it.lowercase() }.toSet()
    }

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

    fun updateContactsList(newContactsList: List<String>) {
        // Update contacts list and clear search cache
        searchCache.clear()
        // Refresh app list in background
        searchExecutor.execute {
            refreshAppList()
        }
    }

    private fun refreshAppList() {
        appLabelMap = fullAppList.associateWith { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            // Use pre-loaded metadata cache if available
            val cachedMetadata = appMetadataCache?.get(packageName)
            if (cachedMetadata != null) {
                cachedMetadata.label.lowercase()
            } else {
                appLabelCache.getOrPut(packageName) {
                    try {
                        resolveInfo.loadLabel(packageManager).toString().lowercase()
                    } catch (e: Exception) {
                        packageName.lowercase()
                    }
                }
            }
        }
        searchCache.clear()
    }

    fun filterAppsAndContacts(query: String) {
        // This method is already called from background thread (searchExecutor)
        val queryLower = query.lowercase().trim()

        val newFilteredList = ArrayList<ResolveInfo>()
        
        // Pre-load all SharedPreferences values in one call for better performance
        val prefs = searchBox.context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        val allPrefs = prefs.all
        val usageCache = mutableMapOf<String, Int>()
        for ((key, value) in allPrefs) {
            if (key.startsWith("usage_") && value is Int) {
                val packageName = key.removePrefix("usage_")
                usageCache[packageName] = value
            }
        }

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

                // Sort by usage count only for matches found (using pre-loaded cache)
                val sortedExact = exactMatches.sortedByDescending {
                    usageCache[it.activityInfo.packageName] ?: 0
                }
                val sortedPartial = partialMatches.sortedByDescending {
                    usageCache[it.activityInfo.packageName] ?: 0
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
                
                val sorted = fullAppList.sortedWith(
                    compareByDescending<ResolveInfo> {
                        usageCache[it.activityInfo.packageName] ?: 0
                    }.thenBy {
                        appLabelMap[it]?.lowercase() ?: run {
                            // Use pre-loaded metadata cache if available
                            val cachedMetadata = appMetadataCache?.get(it.activityInfo.packageName)
                            if (cachedMetadata != null) {
                                cachedMetadata.label.lowercase()
                            } else {
                                // Fallback: load label only if not in cache
                                try {
                                    appLabelCache.getOrPut(it.activityInfo.packageName) {
                                        it.loadLabel(packageManager).toString().lowercase()
                                    }.lowercase()
                                } catch (e: Exception) {
                                    it.activityInfo.packageName.lowercase()
                                }
                            }
                        }
                    }
                )
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
        } catch (e: Exception) {
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