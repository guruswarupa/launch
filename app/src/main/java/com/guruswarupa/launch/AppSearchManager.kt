package com.guruswarupa.launch

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import net.objecthunter.exp4j.ExpressionBuilder

class AppSearchManager(
    private val packageManager: PackageManager,
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val searchBox: EditText,
    private val contactsList: List<String>
) {
    private val handler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable {
        filterAppsAndContacts(searchBox.text.toString())
    }

    private val appLabelCache = mutableMapOf<String, String>()

    private val contactNames: Set<String> by lazy {
        contactsList.map { it.lowercase() }.toSet()
    }

    private var appLabelMap: Map<ResolveInfo, String> = emptyMap()
    private val searchCache = mutableMapOf<String, List<ResolveInfo>>()

    init {
        refreshAppList()
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
    }

    private fun refreshAppList() {
        appLabelMap = fullAppList.associateWith { resolveInfo ->
            appLabelCache.getOrPut(resolveInfo.activityInfo.packageName) {
                resolveInfo.loadLabel(packageManager).toString().lowercase()
            }
        }
        searchCache.clear()
    }

    fun filterAppsAndContacts(query: String) {
        val queryLower = query.lowercase().trim()

        // Use cached results for repeated queries
        val cachedResult = searchCache[queryLower]
        if (cachedResult != null) {
            appList.clear()
            appList.addAll(cachedResult)
            adapter.notifyDataSetChanged()
            return
        }

        val newFilteredList = ArrayList<ResolveInfo>()
        val prefs = searchBox.context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)

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

                // Sort by usage count only for matches found
                val sortedExact = exactMatches.sortedByDescending {
                    prefs.getInt("usage_${it.activityInfo.packageName}", 0)
                }
                val sortedPartial = partialMatches.sortedByDescending {
                    prefs.getInt("usage_${it.activityInfo.packageName}", 0)
                }

                newFilteredList.addAll(sortedExact)
                newFilteredList.addAll(sortedPartial)

                // Optimize contact filtering
                contactsList.asSequence()
                    .filter { it.contains(query, ignoreCase = true) }
                    .take(5) // Limit contact results
                    .forEach { contact ->
                        newFilteredList.add(createWhatsAppContactOption(contact))
                        newFilteredList.add(createSmsOption(contact))
                        newFilteredList.add(createContactOption(contact))
                    }

                if (newFilteredList.isEmpty()) {
                    // Defer suggestions to avoid blocking main UI
                    handler.postDelayed({
                        val suggestions = ArrayList<ResolveInfo>().apply {
                            add(createPlayStoreSearchOption(query))
                            add(createGoogleMapsSearchOption(query))
                            add(createYoutubeSearchOption(query))
                            add(createBrowserSearchOption(query))
                        }
                        appList.clear()
                        appList.addAll(suggestions)
                        adapter.notifyDataSetChanged()
                    }, 50)
                    return
                }
            }
        } else {
            // Cache sorted app list for empty queries
            val emptyQueryKey = ""
            val cachedEmpty = searchCache[emptyQueryKey]
            if (cachedEmpty != null) {
                newFilteredList.addAll(cachedEmpty)
            } else {
                val sorted = fullAppList.sortedWith(
                    compareByDescending<ResolveInfo> {
                        prefs.getInt("usage_${it.activityInfo.packageName}", 0)
                    }.thenBy {
                        appLabelMap[it]?.lowercase() ?: it.loadLabel(packageManager).toString().lowercase()
                    }
                )
                newFilteredList.addAll(sorted)
                searchCache[emptyQueryKey] = ArrayList(sorted)
            }
        }

        // Cache the result for future use
        searchCache[queryLower] = ArrayList(newFilteredList)

        // Use more efficient adapter update method
        adapter.updateAppList(newFilteredList)
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
}