package com.guruswarupa.launch

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.widget.EditText
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.File
import java.util.concurrent.Executors

class AppSearchManager(
    private val packageManager: PackageManager,
    private val fullAppList: MutableList<ResolveInfo>,
    private val adapter: AppAdapter,
    private val searchBox: EditText,
    private val contactsList: List<String>,
    private val context: android.content.Context,
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
    private val searchCache = mutableMapOf<String, List<ResolveInfo>>()

    init {
        // Optimization #4: Avoid rebuilding label map in background. 
        // We'll use appMetadataCache directly during search.
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(debounceRunnable)
                // Reduced from 30ms to 10ms for better responsiveness
                handler.postDelayed(debounceRunnable, 10)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    fun updateContactsList() {
        // Update contacts list and clear search cache
        searchCache.clear()
        // Contacts list is refreshed by the caller, no need for background task here
    }

    private fun getAppLabel(info: ResolveInfo): String {
        val packageName = info.activityInfo.packageName
        // Use pre-loaded metadata cache if available
        return appMetadataCache?.get(packageName)?.label?.lowercase()
            ?: appLabelCache.getOrPut(packageName) {
                try {
                    info.loadLabel(packageManager).toString().lowercase()
                } catch (_: Exception) {
                    packageName.lowercase()
                }
            }
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

                fullAppList.forEach { info ->
                    val packageName = info.activityInfo.packageName
                    if (isAppFiltered?.invoke(packageName) == true) return@forEach
                    
                    val label = getAppLabel(info)
                    when {
                        label == queryLower -> exactMatches.add(info)
                        label.startsWith(queryLower) -> partialMatches.add(0, info)
                        label.contains(queryLower) -> partialMatches.add(info)
                    }
                }

                // Sort alphabetically for matches found
                val sortedExact = exactMatches.sortedBy { getSortKey(getAppLabel(it)) }
                val sortedPartial = partialMatches.sortedBy { getSortKey(getAppLabel(it)) }

                // 1. Add apps first
                newFilteredList.addAll(sortedExact)
                newFilteredList.addAll(sortedPartial)

                // 2. Add settings matches
                getSettingsMatches(queryLower).forEach { setting ->
                    newFilteredList.add(createSettingsOption(setting))
                }

                // 3. Add contacts second
                contactsList.asSequence()
                    .filter { it.contains(query, ignoreCase = true) }
                    .take(5) // Limit contact results
                    .forEach { contact ->
                        newFilteredList.add(createUnifiedContactOption(contact))
                    }

                // 4. Add file matches
                getFileMatches(queryLower).forEach { file ->
                    newFilteredList.add(createFileOption(file))
                }

                // 5. Always add search options at the end (Play Store, Maps, YouTube, Browser)
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
                // Filter out apps that should be hidden (e.g., in focus mode)
                val appsToSort = if (isAppFiltered != null) {
                    fullAppList.filter { !isAppFiltered.invoke(it.activityInfo.packageName) }
                } else {
                    fullAppList
                }
                
                // Sort alphabetically by app name
                val sorted = appsToSort.sortedBy { getSortKey(getAppLabel(it)) }
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

    private fun getSettingsMatches(query: String): List<String> {
        val settings = listOf(
            "Display Style", "Wallpaper", "App Lock", "Hidden Apps", "Permissions",
            "Privacy Dashboard", "Tutorial", "Shake to Torch", "Screen Dimmer",
            "Night Mode", "Flip to DND", "Back Tap"
        )
        return settings.filter { it.contains(query, ignoreCase = true) }.take(3)
    }

    private fun createSettingsOption(setting: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("settings_result_$setting") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "settings_result"
                    name = setting
                }
            }
        }
    }

    private fun getFileMatches(query: String): List<java.io.File> {
        val results = mutableListOf<java.io.File>()
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Files.FileColumns.DATA,
                android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
            )
            
            val selection = "${android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")
            
            val queryUri = android.provider.MediaStore.Files.getContentUri("external")
            
            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                "${android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext() && results.size < 10) {
                    val path = cursor.getString(dataColumn)
                    if (path != null) {
                        val file = java.io.File(path)
                        if (file.exists() && !file.isDirectory) {
                            results.add(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to basic search if MediaStore fails or permission is missing
            try {
                val dirsToSearch = listOf(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    android.os.Environment.DIRECTORY_DOCUMENTS,
                    android.os.Environment.DIRECTORY_DCIM,
                    android.os.Environment.DIRECTORY_PICTURES
                )
                for (dirName in dirsToSearch) {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(dirName)
                    if (dir.exists() && dir.isDirectory) {
                        searchFilesRecursively(dir, query, results, 0)
                        if (results.size >= 10) break
                    }
                }
            } catch (_: Exception) {}
        }
        return results
    }

    private fun searchFilesRecursively(dir: java.io.File, query: String, results: MutableList<java.io.File>, depth: Int) {
        if (depth > 2 || results.size >= 10) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                searchFilesRecursively(file, query, results, depth + 1)
            } else if (file.name.contains(query, ignoreCase = true)) {
                results.add(file)
            }
            if (results.size >= 10) return
        }
    }

    private fun createFileOption(file: java.io.File): ResolveInfo {
        return cachedResolveInfos.getOrPut("file_result_${file.absolutePath}") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "file_result"
                    name = file.name
                    // Store path in a field we can retrieve
                    nonLocalizedLabel = file.absolutePath
                }
            }
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

    private fun createUnifiedContactOption(contact: String): ResolveInfo {
        return cachedResolveInfos.getOrPut("contact_unified_$contact") {
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "contact_unified"
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
    
    /**
     * Cleanup method to shutdown executor when done
     */
    fun cleanup() {
        searchExecutor.shutdown()
    }
}
