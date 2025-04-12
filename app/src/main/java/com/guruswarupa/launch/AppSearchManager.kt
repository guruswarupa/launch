package com.guruswarupa.launch

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import net.objecthunter.exp4j.ExpressionBuilder
import org.json.JSONObject
import java.net.URL
import java.sql.Date

class AppSearchManager(
    private val packageManager: PackageManager,
    private val appList: MutableList<ResolveInfo>,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val recyclerView: RecyclerView,
    private val searchBox: EditText,
    private val contactsList: List<String>
) {
    private val handler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable {
        filterAppsAndContacts(searchBox.text.toString())
    }

    private val appLabels: List<String> by lazy {
        fullAppList.map { it.loadLabel(packageManager).toString().lowercase() }
    }

    private val contactNames: Set<String> by lazy {
        contactsList.map { it.lowercase() }.toSet()
    }

    init {
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(debounceRunnable)
                handler.postDelayed(debounceRunnable, 50)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private val appLabelMap: Map<ResolveInfo, String> by lazy {
        fullAppList.associateWith { it.loadLabel(packageManager).toString().lowercase() }
    }

    fun filterAppsAndContacts(query: String) {
        val newFilteredList = mutableListOf<ResolveInfo>()
        val prefs = searchBox.context.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
        val queryLower = query.lowercase()

        if (query.isNotEmpty()) {
            evaluateMathExpression(query)?.let { result ->
                newFilteredList.add(createMathResultOption(query, result))
            } ?: run {
                val appMatches = appLabelMap
                    .filterValues { it.contains(queryLower) }
                    .toList()
                    .sortedByDescending { (info, _) ->
                        prefs.getInt("usage_${info.activityInfo.packageName}", 0)
                    }
                    .map { it.first }

                newFilteredList.addAll(appMatches)

                contactsList.filter { it.contains(query, ignoreCase = true) }.forEach { contact ->
                    newFilteredList.add(createWhatsAppContactOption(contact))
                    newFilteredList.add(createSmsOption(contact))
                    newFilteredList.add(createContactOption(contact))
                }

                if (newFilteredList.isEmpty()) {
                    // Defer suggestions to avoid blocking main UI
                    handler.postDelayed({
                        newFilteredList.add(createPlayStoreSearchOption(query))
                        newFilteredList.add(createGoogleMapsSearchOption(query))
                        newFilteredList.add(createYoutubeSearchOption(query))
                        newFilteredList.add(createBrowserSearchOption(query))
                        appList.clear()
                        appList.addAll(newFilteredList)
                        adapter.notifyDataSetChanged()
                    }, 100)
                    return
                }
            }
        } else {
            newFilteredList.addAll(fullAppList.sortedByDescending {
                prefs.getInt("usage_${it.activityInfo.packageName}", 0)
            })
        }

        appList.clear()
        appList.addAll(newFilteredList)
        adapter.notifyDataSetChanged()
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