package com.guruswarupa.launch.managers

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.widget.EditText
import com.guruswarupa.launch.AppAdapter
import com.guruswarupa.launch.models.AppMetadata
import net.objecthunter.exp4j.ExpressionBuilder
import java.io.File
import java.util.concurrent.Executors

class AppSearchManager(
    private val packageManager: PackageManager,
    private val fullAppList: MutableList<ResolveInfo>,
    private var homeAppList: List<ResolveInfo>,
    private val adapter: AppAdapter,
    private val searchBox: EditText,
    private var contactsList: List<String>,
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
    private val dataLock = Any()

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
        synchronized(dataLock) {
            // Update contacts list and clear search cache
            searchCache.clear()
        }
        // Contacts list is refreshed by the caller via updateData
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

    enum class SearchMode {
        ALL, APPS, CONTACTS, FILES, MAPS, WEB, PLAYSTORE, YOUTUBE
    }

    private var currentSearchMode = SearchMode.ALL

    fun setSearchMode(mode: SearchMode) {
        currentSearchMode = mode
        refreshSearch()
    }

    fun updateData(newFullAppList: List<ResolveInfo>, newHomeAppList: List<ResolveInfo>, newContactsList: List<String>) {
        synchronized(dataLock) {
            // Update data without resetting currentSearchMode
            fullAppList.clear()
            fullAppList.addAll(newFullAppList)
            homeAppList = ArrayList(newHomeAppList)
            contactsList = newContactsList
            searchCache.clear()
        }
        refreshSearch()
    }

    private fun refreshSearch() {
        handler.removeCallbacks(debounceRunnable)
        handler.post(debounceRunnable)
    }

    fun filterAppsAndContacts(query: String) {
        val queryLower = query.lowercase().trim()
        val newFilteredList = ArrayList<ResolveInfo>()
        
        // Take snapshots under lock to prevent ConcurrentModificationException
        val fullAppListSnapshot: List<ResolveInfo>
        val homeAppListSnapshot: List<ResolveInfo>
        val contactsListSnapshot: List<String>
        
        synchronized(dataLock) {
            fullAppListSnapshot = ArrayList(fullAppList)
            homeAppListSnapshot = ArrayList(homeAppList)
            contactsListSnapshot = ArrayList(contactsList)
        }
        
        if (queryLower.isNotEmpty()) {
            evaluateMathExpression(query)?.let { result ->
                newFilteredList.add(createMathResultOption(query, result))
            } ?: run {
                val exactMatches = ArrayList<ResolveInfo>()
                val partialMatches = ArrayList<ResolveInfo>()

                // 1. Search Apps
                if (currentSearchMode == SearchMode.ALL || currentSearchMode == SearchMode.APPS) {
                    fullAppListSnapshot.forEach { info ->
                        val packageName = info.activityInfo.packageName
                        // Always respect filtering for Focus Mode or Hidden Apps even during search
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

                    newFilteredList.addAll(sortedExact)
                    newFilteredList.addAll(sortedPartial)

                    // Add settings matches (only in ALL or APPS mode)
                    getSettingsMatches(queryLower).forEach { setting ->
                        newFilteredList.add(createSettingsOption(setting))
                    }
                    
                    if (currentSearchMode == SearchMode.APPS) return@run // STRICT
                }

                // 2. Search Contacts
                if (currentSearchMode == SearchMode.ALL || currentSearchMode == SearchMode.CONTACTS) {
                    contactsListSnapshot.asSequence()
                        .filter { it.contains(query, ignoreCase = true) }
                        .take(if (currentSearchMode == SearchMode.ALL) 5 else 20)
                        .forEach { contact ->
                            newFilteredList.add(createUnifiedContactOption(contact))
                        }
                    
                    if (currentSearchMode == SearchMode.CONTACTS) return@run // STRICT
                }

                // 3. Search Files
                if (currentSearchMode == SearchMode.ALL || currentSearchMode == SearchMode.FILES) {
                    // This is the heavy part, so we only do it if requested
                    getFileMatches(queryLower).forEach { file ->
                        newFilteredList.add(createFileOption(file))
                    }
                    
                    if (currentSearchMode == SearchMode.FILES) return@run // STRICT
                }

                // 4. Platform-specific searches (STRICT FILTERING)
                if (currentSearchMode == SearchMode.MAPS) {
                    newFilteredList.add(createGoogleMapsSearchOption(query))
                    return@run // Exit early to only show Maps
                }
                
                if (currentSearchMode == SearchMode.PLAYSTORE) {
                    newFilteredList.add(createPlayStoreSearchOption(query))
                    return@run
                }
                
                if (currentSearchMode == SearchMode.YOUTUBE) {
                    newFilteredList.add(createYoutubeSearchOption(query))
                    return@run
                }
                
                if (currentSearchMode == SearchMode.WEB) {
                    newFilteredList.add(createBrowserSearchOption(query))
                    return@run
                }

                // If in ALL mode, add specialized ones at the bottom
                if (currentSearchMode == SearchMode.ALL) {
                    newFilteredList.add(createGoogleMapsSearchOption(query))
                    newFilteredList.add(createPlayStoreSearchOption(query))
                    newFilteredList.add(createYoutubeSearchOption(query))
                    newFilteredList.add(createBrowserSearchOption(query))
                }
            }
        } else {
            // EMPTY QUERY: Show exactly what\'s on the home screen (filtered for focus mode, favorites, etc.)
            newFilteredList.addAll(homeAppListSnapshot)
        }

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

    private fun getFileMatches(query: String): List<File> {
        val results = mutableListOf<File>()
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
                        val file = File(path)
                        if (file.exists() && !file.isDirectory) {
                            results.add(file)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to basic search if MediaStore fails or permission is missing
            try {
                val dirsToSearch = listOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_PICTURES
                )
                for (dirName in dirsToSearch) {
                    val dir = Environment.getExternalStoragePublicDirectory(dirName)
                    if (dir.exists() && dir.isDirectory) {
                        searchFilesRecursively(dir, query, results, 0)
                        if (results.size >= 10) break
                    }
                }
            } catch (_: Exception) {}
        }
        return results
    }

    private fun searchFilesRecursively(dir: File, query: String, results: MutableList<File>, depth: Int) {
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

    private fun createFileOption(file: File): ResolveInfo {
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
