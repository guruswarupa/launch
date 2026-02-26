package com.guruswarupa.launch

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.PopupMenu
import android.graphics.drawable.Drawable
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import java.io.File
import java.util.concurrent.*
import androidx.core.content.FileProvider

import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.models.AppMetadata
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.ui.activities.SettingsActivity

class AppAdapter(
    private val activity: MainActivity,
    var appList: MutableList<ResolveInfo>,
    private val searchBox: AutoCompleteTextView,
    private var isGridMode: Boolean,
    private val context: Context // Added context
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    /**
     * Updates the view mode (grid vs list) without recreating the adapter.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateViewMode(isGrid: Boolean) {
        if (this.isGridMode != isGrid) {
            this.isGridMode = isGrid
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private val SPECIAL_PACKAGE_NAMES = setOf(
            "contact_unified", "play_store_search", "maps_search", "yt_search", "browser_search", "math_result",
            "file_result", "settings_result"
        )
        
        // Priority levels for progressive loading
        private const val PRIORITY_HIGH = 100    // Visible items
        private const val PRIORITY_MEDIUM = 50  // Near-visible items
        private const val PRIORITY_LOW = 10     // Initial preload (first batch)
        private const val PRIORITY_BACKGROUND = 0 // Remaining items
    }

    private val usageStatsManager = AppUsageStatsManager(activity)
    private val iconCache = ConcurrentHashMap<String, Drawable>() // packageName to icon
    private val labelCache = ConcurrentHashMap<String, String>() // packageName to label
    private val specialAppIconCache = ConcurrentHashMap<String, Drawable>() // Cache for special app icons (Play Store, Maps, YouTube)
    private val usageCache = ConcurrentHashMap<String, String>() // packageName to formatted usage string
    private val executor = Executors.newSingleThreadExecutor() // Executor for background tasks
    private var itemsRendered = 0 // Track how many items have been rendered

    /**
     * Custom Runnable with priority for use in PriorityBlockingQueue
     */
    private class PriorityRunnable(val priority: Int, val action: Runnable) : Runnable, Comparable<PriorityRunnable> {
        override fun run() = action.run()
        override fun compareTo(other: PriorityRunnable): Int = other.priority.compareTo(this.priority) // Higher priority first
    }

    // Thread pool with priority queue for progressive icon loading
    private val iconPreloadExecutor = ThreadPoolExecutor(
        2, 2, 0L, TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>()
    )
    
    /**
     * Clear usage cache to force refresh of usage times
     */
    fun clearUsageCache() {
        usageCache.clear()
    }

    /**
     * Get the label for an app at a given position.
     * Uses cache if available, otherwise falls back to activity name or package name.
     */
    fun getAppLabel(position: Int): String {
        if (position < 0 || position >= appList.size) return ""
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName
        
        // Special entries
        if (packageName in SPECIAL_PACKAGE_NAMES) {
            return appInfo.activityInfo.name ?: ""
        }
        
        return labelCache[packageName] ?: appInfo.activityInfo.name ?: packageName
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView? = view.findViewById(R.id.app_name)
        val appUsageTime: TextView? = view.findViewById(R.id.app_usage_time)
        var lastClickTime = 0L
    }

    fun updateAppList(newAppList: List<ResolveInfo>) {
        // OPTIMIZATION: Update UI immediately on main thread for instant feedback
        val newItems = ArrayList(newAppList)
        val isFirstLoad = itemsRendered == 0

        // Populate label cache from CacheManager's metadata cache before updating UI
        try {
            val metadataCache = activity.cacheManager.getMetadataCache()
            for (app in newItems) {
                val packageName = app.activityInfo.packageName
                val cachedMetadata = metadataCache[packageName]
                if (cachedMetadata != null && !labelCache.containsKey(packageName)) {
                    labelCache[packageName] = cachedMetadata.label
                }
            }
        } catch (_: Exception) {
            // If cache is not available, labels will be loaded on-demand
        }

        // Update UI immediately on main thread (fast path)
        (context as? Activity)?.runOnUiThread {
            val diffCallback = AppListDiffCallback(appList, newItems)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            
            appList.clear()
            appList.addAll(newItems)
            diffResult.dispatchUpdatesTo(this)
            
            // Pre-load icons asynchronously after initial render (only on first load)
            if (isFirstLoad && newItems.isNotEmpty()) {
                itemsRendered = newItems.size
                // Defer icon preloading to avoid blocking UI
                executor.execute {
                    preloadIcons(newItems)
                }
            }
        }
    }
    
    /**
     * DiffUtil callback for efficient RecyclerView updates
     */
    private class AppListDiffCallback(
        private val oldList: List<ResolveInfo>,
        private val newList: List<ResolveInfo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        
        override fun getNewListSize(): Int = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPackage = oldList[oldItemPosition].activityInfo.packageName
            val newPackage = newList[newItemPosition].activityInfo.packageName
            return oldPackage == newPackage
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName &&
                   oldItem.activityInfo.name == newItem.activityInfo.name
        }
    }
    
    /**
     * Pre-loads icons in background to improve scroll performance
     * Optimized to preload visible items first, then next batch using priority queue
     */
    private fun preloadIcons(apps: List<ResolveInfo>) {
        // Pre-load first 30 icons with low priority (they will be bumped to high if visible)
        val immediateLoad = apps.take(30)
        for (app in immediateLoad) {
            submitIconLoadTask(app, PRIORITY_LOW)
        }
        
        // Pre-load remaining icons in background
        val remainingApps = apps.drop(30)
        executor.execute {
            for (batch in remainingApps.chunked(20)) {
                for (app in batch) {
                    submitIconLoadTask(app, PRIORITY_BACKGROUND)
                }
                // Small delay between batches to avoid flooding the queue and allow UI tasks
                try { Thread.sleep(50) } catch (_: InterruptedException) {}
            }
        }
    }
    
    private fun submitIconLoadTask(app: ResolveInfo, priority: Int, holder: ViewHolder? = null, position: Int = -1) {
        val packageName = app.activityInfo.packageName
        
        // Skip special entries or already cached icons
        if (packageName in SPECIAL_PACKAGE_NAMES || iconCache.containsKey(packageName)) {
            // If it's already cached but we have a holder, update it
            if (holder != null && iconCache.containsKey(packageName)) {
                val icon = iconCache[packageName]
                (context as? Activity)?.runOnUiThread {
                    if (holder.bindingAdapterPosition == position) {
                        holder.appIcon.setImageDrawable(icon)
                        activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon)
                    }
                }
            }
            return
        }
        
        iconPreloadExecutor.execute(PriorityRunnable(priority) {
            try {
                // Double check cache before loading
                if (!iconCache.containsKey(packageName)) {
                    val icon = app.loadIcon(activity.packageManager)
                    iconCache[packageName] = icon
                    
                    // Update UI if holder is provided
                    if (holder != null) {
                        (context as? Activity)?.runOnUiThread {
                            if (holder.bindingAdapterPosition == position) {
                                holder.appIcon.setImageDrawable(icon)
                                activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Handle errors silently in background loading
            }
        })
    }

    /**
     * Pre-load next N icons starting from position with medium priority
     */
    private fun preloadNextIcons(startPosition: Int, endPosition: Int) {
        val size = appList.size
        if (startPosition >= size) return
        
        val appsToPreload = try {
            ArrayList(appList.subList(startPosition, minOf(endPosition, size)))
        } catch (_: Exception) {
            return
        }
        
        for (app in appsToPreload) {
            submitIconLoadTask(app, PRIORITY_MEDIUM)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) R.layout.app_item_grid else R.layout.app_item
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName

        // Clear backgrounds for both modes to support transparency/shadow look
        holder.itemView.background = null
        holder.appIcon.background = null
        holder.itemView.elevation = 0f

        // Always show the name in both grid and list mode
        holder.appName?.visibility = View.VISIBLE

        // Always hide the on-item usage display
        holder.appUsageTime?.visibility = View.GONE

        when (packageName) {
            "contact_unified" -> {
                holder.appIcon.setImageResource(R.drawable.ic_person)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    showContactChoiceDialog(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "play_store_search" -> {
                val cachedIcon = specialAppIconCache["com.android.vending"]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            val icon = activity.packageManager.getApplicationIcon("com.android.vending")
                            specialAppIconCache["com.android.vending"] = icon
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) {
                                    holder.appIcon.setImageDrawable(icon)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                holder.appName?.text = activity.getString(R.string.search_on_play_store, appInfo.activityInfo.name)
                holder.itemView.setOnClickListener {
                    val encodedQuery = Uri.encode(appInfo.activityInfo.name)
                    activity.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=$encodedQuery".toUri()))
                    searchBox.text.clear()
                }
            }

            "maps_search" -> {
                val cachedIcon = specialAppIconCache["com.google.android.apps.maps"]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            val icon = activity.packageManager.getApplicationIcon("com.google.android.apps.maps")
                            specialAppIconCache["com.google.android.apps.maps"] = icon
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) {
                                    holder.appIcon.setImageDrawable(icon)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                holder.appName?.text = activity.getString(R.string.search_in_google_maps, appInfo.activityInfo.name)
                holder.itemView.setOnClickListener {
                    val gmmIntentUri = "geo:0,0?q=${Uri.encode(appInfo.activityInfo.name)}".toUri()
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        activity.startActivity(mapIntent)
                    } catch (_: Exception) {
                        Toast.makeText(activity, activity.getString(R.string.google_maps_not_installed), Toast.LENGTH_SHORT).show()
                    }
                    searchBox.text.clear()
                }
            }

            "yt_search" -> {
                val cachedRevanced = specialAppIconCache["app.revanced.android.youtube"]
                val cachedYouTube = specialAppIconCache["com.google.android.youtube"]
                val cachedIcon = cachedRevanced ?: cachedYouTube
                
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            try {
                                val icon = activity.packageManager.getApplicationIcon("app.revanced.android.youtube")
                                specialAppIconCache["app.revanced.android.youtube"] = icon
                                (context as? Activity)?.runOnUiThread {
                                    if (holder.bindingAdapterPosition == position) holder.appIcon.setImageDrawable(icon)
                                }
                            } catch (_: Exception) {
                                try {
                                    val icon = activity.packageManager.getApplicationIcon("com.google.android.youtube")
                                    specialAppIconCache["com.google.android.youtube"] = icon
                                    (context as? Activity)?.runOnUiThread {
                                        if (holder.bindingAdapterPosition == position) holder.appIcon.setImageDrawable(icon)
                                    }
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }
                }
                holder.appName?.text = activity.getString(R.string.search_on_youtube, appInfo.activityInfo.name)
                holder.itemView.setOnClickListener {
                    val ytIntentUri = "https://www.youtube.com/results?search_query=${Uri.encode(appInfo.activityInfo.name)}".toUri()
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytIntentUri)
                    var appOpened = false
                    try {
                        ytIntent.setPackage("app.revanced.android.youtube")
                        activity.startActivity(ytIntent)
                        appOpened = true
                    } catch (_: Exception) {
                        try {
                            ytIntent.setPackage("com.google.android.youtube")
                            activity.startActivity(ytIntent)
                            appOpened = true
                        } catch (_: Exception) {}
                    }

                    if (!appOpened) {
                        Toast.makeText(activity, activity.getString(R.string.youtube_not_installed_opening_browser), Toast.LENGTH_SHORT).show()
                        activity.startActivity(Intent(Intent.ACTION_VIEW, ytIntentUri))
                    }
                    searchBox.text.clear()
                }
            }

            "browser_search" -> {
                holder.appIcon.setImageResource(R.drawable.ic_browser)
                holder.appName?.text = activity.getString(R.string.search_in_browser, appInfo.activityInfo.name)
                holder.itemView.setOnClickListener {
                    val prefs = activity.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
                    val engine = prefs.getString(Constants.Prefs.SEARCH_ENGINE, "Google")
                    val baseUrl = when (engine) {
                        "Bing" -> "https://www.bing.com/search?q="
                        "DuckDuckGo" -> "https://duckduckgo.com/?q="
                        "Ecosia" -> "https://www.ecosia.org/search?q="
                        "Brave" -> "https://search.brave.com/search?q="
                        "Startpage" -> "https://www.startpage.com/sp/search?query="
                        "Yahoo" -> "https://search.yahoo.com/search?p="
                        "Qwant" -> "https://www.qwant.com/?q="
                        else -> "https://www.google.com/search?q="
                    }
                    activity.startActivity(Intent(Intent.ACTION_VIEW, "$baseUrl${Uri.encode(appInfo.activityInfo.name)}".toUri()))
                    searchBox.text.clear()
                }
            }

            "settings_result" -> {
                holder.appIcon.setImageResource(R.drawable.ic_settings)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    val intent = Intent(activity, SettingsActivity::class.java)
                    activity.startActivity(intent)
                    searchBox.text.clear()
                }
            }

            "file_result" -> {
                holder.appIcon.setImageResource(R.drawable.ic_file)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    val filePath = appInfo.activityInfo.nonLocalizedLabel.toString()
                    val file = File(filePath)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, activity.contentResolver.getType(uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            activity.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(activity, "No app found to open this file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    searchBox.text.clear()
                }
            }

            "math_result" -> {
                holder.appIcon.setImageResource(R.drawable.ic_calculator)
                holder.appName?.text = appInfo.activityInfo.name
            }

            else -> {
                val cachedLabel = labelCache[packageName]
                if (cachedLabel != null) {
                    holder.appName?.text = cachedLabel
                } else {
                    if (position < 50) {
                        try {
                            val label = appInfo.loadLabel(activity.packageManager).toString()
                            labelCache[packageName] = label
                            holder.appName?.text = label
                        } catch (_: Exception) {
                            holder.appName?.text = appInfo.activityInfo.packageName
                            loadLabelAsync(holder, position, appInfo, packageName)
                        }
                    } else {
                        holder.appName?.text = appInfo.activityInfo.packageName
                        loadLabelAsync(holder, position, appInfo, packageName)
                    }
                }

                val cachedIcon = iconCache[packageName]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                    activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon)
                } else {
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    submitIconLoadTask(appInfo, PRIORITY_HIGH, holder, position)
                }
                
                if (position < appList.size - 1 && position % 5 == 0) {
                    preloadNextIcons(position + 1, minOf(position + 11, appList.size))
                }

                val clickDebounceDelay = 500L
                holder.itemView.setOnClickListener {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - holder.lastClickTime < clickDebounceDelay) return@setOnClickListener
                    holder.lastClickTime = currentTime
                    
                    if (activity.appTimerManager.isAppOverDailyLimit(packageName)) {
                        val appName = labelCache[packageName] ?: appInfo.activityInfo.packageName
                        Toast.makeText(activity, "Daily limit reached for $appName", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        val prefs = activity.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("usage_$packageName", 0)
                        prefs.edit { putInt("usage_$packageName", currentCount + 1) }

                        val appName = labelCache[packageName] ?: appInfo.activityInfo.packageName
                        val isSessionTimerEnabled = activity.appTimerManager.isSessionTimerEnabled(packageName)
                        
                        if (isSessionTimerEnabled) {
                            activity.appTimerManager.showTimerDialog(appName) { timerDuration ->
                                if (activity.appLockManager.isAppLocked(packageName)) {
                                    activity.appLockManager.verifyPin { isAuthenticated ->
                                        if (isAuthenticated) {
                                            activity.startActivity(intent)
                                            activity.appTimerManager.startTimer(packageName, timerDuration)
                                        }
                                    }
                                } else {
                                    activity.startActivity(intent)
                                    activity.appTimerManager.startTimer(packageName, timerDuration)
                                }
                                activity.runOnUiThread {
                                    searchBox.text.clear()
                                    activity.appSearchManager.filterAppsAndContacts("")
                                }
                            }
                        } else {
                            if (activity.appLockManager.isAppLocked(packageName)) {
                                activity.appLockManager.verifyPin { isAuthenticated ->
                                    if (isAuthenticated) activity.startActivity(intent)
                                }
                            } else {
                                activity.startActivity(intent)
                            }
                            activity.runOnUiThread {
                                searchBox.text.clear()
                                activity.appSearchManager.filterAppsAndContacts("")
                            }
                        }
                    } else {
                        Toast.makeText(activity, activity.getString(R.string.cannot_launch_app), Toast.LENGTH_SHORT).show()
                    }
                }

                holder.itemView.setOnLongClickListener {
                    showAppContextMenu(holder.itemView, packageName, appInfo)
                    true
                }
            }
        }
    }

    override fun getItemCount(): Int = appList.size

    private fun loadLabelAsync(holder: ViewHolder, position: Int, appInfo: ResolveInfo, packageName: String) {
        executor.execute {
            try {
                val label = appInfo.loadLabel(activity.packageManager).toString()
                labelCache[packageName] = label
                try {
                    activity.cacheManager.updateMetadataCache(packageName,
                        AppMetadata(
                            packageName,
                            appInfo.activityInfo.name,
                            label,
                            System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}
                
                (context as? Activity)?.runOnUiThread {
                    if (holder.bindingAdapterPosition == position) holder.appName?.text = label else notifyItemChanged(position)
                }
            } catch (_: Exception) {
                val fallbackLabel = appInfo.activityInfo.packageName
                labelCache[packageName] = fallbackLabel
                (context as? Activity)?.runOnUiThread {
                    if (holder.bindingAdapterPosition == position) holder.appName?.text = fallbackLabel else notifyItemChanged(position)
                }
            }
        }
    }

    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)
        val textColor = ContextCompat.getColor(activity, R.color.text)
        val appName = labelCache[packageName] ?: appInfo.activityInfo.packageName
        
        val dailyLimitItem = popupMenu.menu.add(0, 100, 0, "Set Daily Limit")
        val limitSpannable = android.text.SpannableString(dailyLimitItem.title)
        limitSpannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, limitSpannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        dailyLimitItem.title = limitSpannable

        val usageHeader = popupMenu.menu.findItem(R.id.usage_header)
        if (usageHeader != null) {
            executor.execute {
                val usageTime = usageStatsManager.getAppUsageTime(packageName)
                val formattedTime = usageStatsManager.formatUsageTime(usageTime)
                activity.runOnUiThread {
                    usageHeader.title = "Usage: $formattedTime"
                    val spannable = android.text.SpannableString(usageHeader.title)
                    spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    usageHeader.title = spannable
                }
            }
        }

        val toggleSessionTimerItem = popupMenu.menu.findItem(R.id.toggle_session_timer)
        if (toggleSessionTimerItem != null) {
            val isEnabled = activity.appTimerManager.isSessionTimerEnabled(packageName)
            toggleSessionTimerItem.title = if (isEnabled) "Disable Session Timer" else "Enable Session Timer"
            val spannable = android.text.SpannableString(toggleSessionTimerItem.title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            toggleSessionTimerItem.title = spannable
        }

        val favoriteMenuItem = popupMenu.menu.findItem(R.id.toggle_favorite)
        if (favoriteMenuItem != null) {
            val isFavorite = activity.favoriteAppManager.isFavoriteApp(packageName)
            favoriteMenuItem.title = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
            val spannable = android.text.SpannableString(favoriteMenuItem.title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            favoriteMenuItem.title = spannable
        }

        val hideMenuItem = popupMenu.menu.findItem(R.id.toggle_hide)
        if (hideMenuItem != null) {
            try {
                val isHidden = activity.hiddenAppManager.isAppHidden(packageName)
                hideMenuItem.title = if (isHidden) "Unhide App" else "Hide App"
                val spannable = android.text.SpannableString(hideMenuItem.title)
                spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hideMenuItem.title = spannable
            } catch (_: UninitializedPropertyAccessException) {
                hideMenuItem.isVisible = false
            }
        }
        
        for (i in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(i)
            val itemTitle = item.title?.toString() ?: continue
            val spannable = android.text.SpannableString(itemTitle)
            spannable.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.title = spannable
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                100 -> {
                    activity.appTimerManager.showDailyLimitDialog(appName, packageName) { notifyDataSetChanged() }
                    true
                }
                R.id.toggle_session_timer -> {
                    val isEnabled = activity.appTimerManager.isSessionTimerEnabled(packageName)
                    activity.appTimerManager.setSessionTimerEnabled(packageName, !isEnabled)
                    val status = if (!isEnabled) "enabled" else "disabled"
                    Toast.makeText(activity, "Session timer $status for $appName", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.app_info -> { showAppInfo(packageName); true }
                R.id.share_app -> { shareApp(packageName, appInfo); true }
                R.id.uninstall_app -> { uninstallApp(packageName); true }
                R.id.toggle_favorite -> { toggleFavoriteApp(packageName, appInfo); true }
                R.id.toggle_hide -> { toggleHideApp(packageName, appInfo); true }
                else -> false
            }
        }
        popupMenu.show()
        fixPopupMenuTextColors(popupMenu)
    }
    
    @SuppressLint("DiscouragedPrivateApi")
    private fun fixPopupMenuTextColors(popupMenu: PopupMenu) {
        try {
            val textColor = ContextCompat.getColor(activity, R.color.text)
            val popupField = popupMenu.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popupMenu)
            val menuPopupHelperClass = menuPopupHelper?.javaClass
            val listViewFieldNames = arrayOf("mDropDownList", "mPopup", "mListView")
            var listView: android.widget.ListView? = null
            
            for (fieldName in listViewFieldNames) {
                try {
                    val listViewField = menuPopupHelperClass?.getDeclaredField(fieldName)
                    listViewField?.isAccessible = true
                    val result = listViewField?.get(menuPopupHelper)
                    if (result is android.widget.ListView) { listView = result; break }
                } catch (_: NoSuchFieldException) {}
            }
            
            fun fixTextColors(view: View) {
                if (view is TextView) view.setTextColor(textColor) else if (view is ViewGroup) findTextViewsAndSetColor(view, textColor)
            }
            
            listView?.let { lv ->
                try { for (i in 0 until lv.childCount) fixTextColors(lv.getChildAt(i)) } catch (_: Exception) {}
                lv.post { try { for (i in 0 until lv.childCount) fixTextColors(lv.getChildAt(i)) } catch (_: Exception) {} }
                lv.postDelayed({ try { for (i in 0 until lv.childCount) fixTextColors(lv.getChildAt(i)) } catch (_: Exception) {} }, 50)
                lv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try { if (lv.childCount > 0) fixTextColors(lv.getChildAt(lv.childCount - 1)) } catch (_: Exception) {}
                        try { for (i in 0 until lv.childCount) fixTextColors(lv.getChildAt(i)) } catch (_: Exception) {}
                        lv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                })
            }
        } catch (_: Exception) {}
    }
    
    private fun findTextViewsAndSetColor(viewGroup: ViewGroup, color: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) child.setTextColor(color) else if (child is ViewGroup) findTextViewsAndSetColor(child, color)
        }
    }

    private fun showAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = "package:$packageName".toUri() }
        activity.startActivity(intent)
    }

    private fun shareApp(packageName: String, appInfo: ResolveInfo) {
        val appName = labelCache[packageName] ?: try { appInfo.loadLabel(activity.packageManager).toString() } catch (_: Exception) { packageName }
        ShareManager(activity).shareApk(packageName, appName)
    }

    private fun uninstallApp(packageName: String) {
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = "package:$packageName".toUri() }
        activity.startActivity(intent)
    }

    private fun toggleFavoriteApp(packageName: String, appInfo: ResolveInfo) {
        val appName = labelCache[packageName] ?: try { appInfo.loadLabel(activity.packageManager).toString() } catch (_: Exception) { packageName }
        if (activity.favoriteAppManager.isFavoriteApp(packageName)) {
            activity.favoriteAppManager.removeFavoriteApp(packageName)
            Toast.makeText(activity, activity.getString(R.string.removed_from_favorites, appName), Toast.LENGTH_SHORT).show()
        } else {
            activity.favoriteAppManager.addFavoriteApp(packageName)
            Toast.makeText(activity, activity.getString(R.string.added_to_favorites, appName), Toast.LENGTH_SHORT).show()
        }
        activity.filterAppsWithoutReload()
        activity.appDockManager.refreshFavoriteToggle()
    }

    private fun toggleHideApp(packageName: String, appInfo: ResolveInfo) {
        try {
            val appName = labelCache[packageName] ?: try { appInfo.loadLabel(activity.packageManager).toString() } catch (_: Exception) { packageName }
            if (activity.hiddenAppManager.isAppHidden(packageName)) {
                activity.hiddenAppManager.unhideApp(packageName)
                Toast.makeText(activity, activity.getString(R.string.unhid_app, appName), Toast.LENGTH_SHORT).show()
            } else {
                activity.hiddenAppManager.hideApp(packageName)
                Toast.makeText(activity, activity.getString(R.string.hid_app, appName), Toast.LENGTH_SHORT).show()
            }
            activity.filterAppsWithoutReload()
        } catch (_: UninitializedPropertyAccessException) {
            Toast.makeText(activity, activity.getString(R.string.hidden_apps_feature_not_available), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showContactChoiceDialog(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)
        val photoUri = getPhotoUriForContact(contactName)
        
        val options = listOf(
            activity.getString(R.string.call_button) to R.drawable.ic_phone,
            activity.getString(R.string.whatsapp) to R.drawable.ic_whatsapp,
            activity.getString(R.string.sms) to R.drawable.ic_message
        )
        
        val adapter = object : ArrayAdapter<Pair<String, Int>>(activity, R.layout.dialog_contact_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.dialog_contact_item, parent, false)
                val item = getItem(position)!!
                
                view.findViewById<ImageView>(R.id.option_icon).setImageResource(item.second)
                view.findViewById<TextView>(R.id.option_text).text = item.first
                
                return view
            }
        }

        val builder = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
        
        // Custom title view for premium look
        val titleView = LayoutInflater.from(activity).inflate(R.layout.dialog_contact_title, null)
        titleView.findViewById<TextView>(R.id.contact_name).text = contactName
        titleView.findViewById<TextView>(R.id.contact_number).text = phoneNumber
        
        val photoImageView = titleView.findViewById<ImageView>(R.id.contact_photo)
        if (photoUri != null) {
            try {
                val inputStream = activity.contentResolver.openInputStream(photoUri.toUri())
                val drawable = Drawable.createFromStream(inputStream, photoUri)
                if (drawable != null) photoImageView.setImageDrawable(drawable)
                else photoImageView.setImageResource(R.drawable.ic_person)
            } catch (_: Exception) {
                photoImageView.setImageResource(R.drawable.ic_person)
            }
        } else {
            photoImageView.setImageResource(R.drawable.ic_person)
        }
        
        builder.setCustomTitle(titleView)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showCallConfirmationDialog(contactName)
                    1 -> activity.openWhatsAppChat(contactName)
                    2 -> activity.openSMSChat(contactName)
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()
    }

    private fun getPhotoUriForContact(contactName: String): String? {
        val cursor = activity.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI),
            "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )
        var photoUri: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                if (index != -1) photoUri = it.getString(index)
            }
        }
        return photoUri
    }

    fun showCallConfirmationDialog(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)
        AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.call_contact_title, contactName))
            .setMessage(activity.getString(R.string.call_contact_message, phoneNumber))
            .setPositiveButton(activity.getString(R.string.call_button)) { _, _ -> call(phoneNumber) }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()
    }

    private fun call(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply { data = "tel:$phoneNumber".toUri() }
        activity.startActivity(intent)
    }

    private fun getPhoneNumberForContact(contactName: String): String {
        val cursor: Cursor? = activity.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(contactName), null)
        var phoneNumber: String? = null
        cursor?.use { if (it.moveToFirst()) { val index = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); if (index != -1) phoneNumber = it.getString(index) } }
        return phoneNumber ?: "Not found"
    }
}
