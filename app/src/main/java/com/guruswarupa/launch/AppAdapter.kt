package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import kotlin.apply

import android.provider.Settings
import android.view.Gravity
import android.widget.PopupMenu
import java.util.concurrent.Executors
import android.app.Activity
import androidx.core.content.ContextCompat

class AppAdapter(
    private val activity: MainActivity,
    var appList: MutableList<ResolveInfo>,
    private val searchBox: EditText,
    private val isGridMode: Boolean,
    private val context: Context // Added context
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private val usageStatsManager = AppUsageStatsManager(activity)
    private val usageCache = mutableMapOf<String, Pair<Long, Long>>() // packageName to (usageTime, timestamp)
    private val iconCache = mutableMapOf<String, Drawable>() // packageName to icon
    private val labelCache = mutableMapOf<String, String>() // packageName to label
    private val packageValidityCache = mutableMapOf<String, Boolean>() // Cache for app validity checks
    private val specialAppIconCache = mutableMapOf<String, Drawable>() // Cache for special app icons (Play Store, Maps, YouTube)
    private val CACHE_DURATION = 30000L // 30 seconds cache (reduced for more frequent updates)
    private val executor = Executors.newSingleThreadExecutor() // Executor for background tasks
    private var itemsRendered = 0 // Track how many items have been rendered
    private val iconPreloadExecutor = Executors.newFixedThreadPool(2) // Separate thread pool for icon preloading
    
    /**
     * Clear usage cache to force refresh of usage times
     */
    fun clearUsageCache() {
        usageCache.clear()
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView? = view.findViewById(R.id.app_name)
        val appUsageTime: TextView? = view.findViewById(R.id.app_usage_time)
    }

    fun updateAppList(newAppList: List<ResolveInfo>) {
        // OPTIMIZATION: Update UI immediately on main thread for instant feedback
        // Use executor only for heavy preloading work
        val newItems = ArrayList(newAppList)
        val isFirstLoad = itemsRendered == 0

        // Populate label cache from CacheManager's metadata cache before updating UI
        // This ensures labels are available immediately and prevents showing package names
        try {
            val metadataCache = activity.cacheManager.getMetadataCache()
            for (app in newItems) {
                val packageName = app.activityInfo.packageName
                val cachedMetadata = metadataCache[packageName]
                if (cachedMetadata != null && !labelCache.containsKey(packageName)) {
                    labelCache[packageName] = cachedMetadata.label
                }
            }
        } catch (e: Exception) {
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
     * Optimized to preload visible items first, then next batch
     */
    private fun preloadIcons(apps: List<ResolveInfo>) {
        iconPreloadExecutor.execute {
            // Pre-load first 30 icons immediately (visible + near-visible)
            val immediateLoad = apps.take(30)
            for (app in immediateLoad) {
                val packageName = app.activityInfo.packageName
                
                // Skip special entries
                if (packageName in listOf("contact_search", "whatsapp_contact", "sms_contact", 
                        "play_store_search", "maps_search", "yt_search", "browser_search", "math_result")) {
                    continue
                }
                
                // Pre-load icon if not cached
                if (!iconCache.containsKey(packageName)) {
                    try {
                        val isValidApp = packageValidityCache.getOrPut(packageName) {
                            app.activityInfo?.applicationInfo != null
                        }
                        
                        if (isValidApp) {
                            val icon = app.loadIcon(activity.packageManager)
                            iconCache[packageName] = icon
                        }
                    } catch (e: Exception) {
                        // Ignore errors during pre-loading
                    }
                }
                
                // Pre-load label if not cached
                if (!labelCache.containsKey(packageName)) {
                    try {
                        val isValidApp = packageValidityCache.getOrPut(packageName) {
                            app.activityInfo?.applicationInfo != null
                        }
                        
                        if (isValidApp) {
                            val label = app.loadLabel(activity.packageManager)?.toString()
                                ?: app.activityInfo.packageName
                            labelCache[packageName] = label
                        }
                    } catch (e: Exception) {
                        // Ignore errors during pre-loading
                    }
                }
            }
            
            // Pre-load remaining icons in batches
            val remainingApps = apps.drop(30)
            for (batch in remainingApps.chunked(20)) {
                for (app in batch) {
                    val packageName = app.activityInfo.packageName
                    
                    if (packageName in listOf("contact_search", "whatsapp_contact", "sms_contact", 
                            "play_store_search", "maps_search", "yt_search", "browser_search", "math_result")) {
                        continue
                    }
                    
                    if (!iconCache.containsKey(packageName)) {
                        try {
                            val isValidApp = packageValidityCache.getOrPut(packageName) {
                                app.activityInfo?.applicationInfo != null
                            }
                            
                            if (isValidApp) {
                                val icon = app.loadIcon(activity.packageManager)
                                iconCache[packageName] = icon
                            }
                        } catch (e: Exception) {
                            // Ignore errors
                        }
                    }
                }
                // Small delay between batches to avoid blocking
                Thread.sleep(10)
            }
        }
    }
    
    /**
     * Pre-load next N icons starting from position
     */
    private fun preloadNextIcons(startPosition: Int, endPosition: Int) {
        if (startPosition >= appList.size) return
        
        iconPreloadExecutor.execute {
            val appsToPreload = appList.subList(startPosition, minOf(endPosition, appList.size))
            for (app in appsToPreload) {
                val packageName = app.activityInfo.packageName
                
                if (packageName in listOf("contact_search", "whatsapp_contact", "sms_contact", 
                        "play_store_search", "maps_search", "yt_search", "browser_search", "math_result")) {
                    continue
                }
                
                if (!iconCache.containsKey(packageName)) {
                    try {
                        val icon = app.loadIcon(activity.packageManager)
                        iconCache[packageName] = icon
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                }
            }
        }
    }

    private fun getUsageTimeWithCache(packageName: String): Long {
        // Skip usage queries in power saver mode to save battery
        if (activity.appDockManager.isPowerSaverActive()) {
            return 0L
        }
        
        val currentTime = System.currentTimeMillis()
        val cached = usageCache[packageName]

        return if (cached != null && (currentTime - cached.second) < CACHE_DURATION) {
            cached.first
        } else {
            val usageTime = usageStatsManager.getAppUsageTime(packageName)
            usageCache[packageName] = Pair(usageTime, currentTime)
            usageTime
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName
        val isPowerSaverActive = activity.appDockManager.isPowerSaverActive()

        // Handle pitch black UI for app items in power saver mode
        if (isPowerSaverActive) {
            holder.itemView.background = null
            holder.itemView.elevation = 0f
            holder.appIcon.background = null
        } else {
            if (!isGridMode) {
                holder.itemView.setBackgroundResource(R.drawable.rounded_background)
                holder.itemView.elevation = activity.resources.getDimension(R.dimen.widget_elevation)
            } else {
                holder.itemView.setBackgroundResource(android.R.drawable.list_selector_background)
            }
            holder.appIcon.setBackgroundResource(R.drawable.circular_background)
        }

        // Always show the name in both grid and list mode
        holder.appName?.visibility = View.VISIBLE

        // Show usage time only in list mode and when power saver is disabled
        // Hide usage time in power saver mode to save battery (no usage queries)
        // Defer usage stats loading on initial render for better performance
        
        if (!isGridMode && holder.appUsageTime != null && !isPowerSaverActive) {
            // OPTIMIZATION: Always defer usage stats loading for first 30 items to improve initial render
            // This prevents blocking the UI thread during initial load
            if (position < 30 && itemsRendered < 30) {
                holder.appUsageTime?.text = ""
                holder.appUsageTime?.visibility = View.VISIBLE
                // Load usage time asynchronously after initial render
                executor.execute {
                    val usageTime = getUsageTimeWithCache(packageName)
                    val formattedTime = usageStatsManager.formatUsageTime(usageTime)
                    (context as? Activity)?.runOnUiThread {
                        // Only update if this holder still shows the same app
                        if (holder.bindingAdapterPosition == position) {
                            holder.appUsageTime?.text = formattedTime
                        }
                    }
                }
            } else {
                // Use cached usage time (cache lookup is fast, actual query only if cache expired)
                val usageTime = getUsageTimeWithCache(packageName)
                val formattedTime = usageStatsManager.formatUsageTime(usageTime)
                holder.appUsageTime?.text = formattedTime
                holder.appUsageTime?.visibility = View.VISIBLE
            }
        } else {
            holder.appUsageTime?.visibility = View.GONE
        }

        when (packageName) {
            "contact_search" -> {
                // Display contact with phone icon
                holder.appIcon.setImageResource(R.drawable.ic_phone) // Ensure ic_phone is in res/drawable
                holder.appName?.text = appInfo.activityInfo.name // Contact name
                holder.itemView.setOnClickListener {
                    showCallConfirmationDialog(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "whatsapp_contact" -> {
                holder.appIcon.setImageResource(R.drawable.ic_whatsapp) // WhatsApp icon
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    activity.openWhatsAppChat(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "sms_contact" -> {
                // Display SMS option
                holder.appIcon.setImageResource(R.drawable.ic_message)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    activity.openSMSChat(appInfo.activityInfo.name)
                    searchBox.text.clear()
                }
            }

            "play_store_search" -> {
                // Display Play Store search option
                val cachedIcon = specialAppIconCache["com.android.vending"]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    // Load icon asynchronously to avoid blocking
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
                        } catch (e: Exception) {
                            // Icon already set to default, no need to update
                        }
                    }
                }
                holder.appName?.text = "Search ${appInfo.activityInfo.name} on Play Store"
                holder.itemView.setOnClickListener {
                    val encodedQuery = Uri.encode(appInfo.activityInfo.name)
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/search?q=$encodedQuery")
                        )
                    )
                    searchBox.text.clear()
                }
            }

            "maps_search" -> {
                // Set the Google Maps icon with error handling
                val cachedIcon = specialAppIconCache["com.google.android.apps.maps"]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    // Load icon asynchronously to avoid blocking
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
                        } catch (e: Exception) {
                            // Icon already set to default, no need to update
                        }
                    }
                }
                holder.appName?.text = "Search ${appInfo.activityInfo.name} in Google Maps"
                holder.itemView.setOnClickListener {
                    // Create an Intent to open Google Maps
                    val gmmIntentUri =
                        Uri.parse("geo:0,0?q=${Uri.encode(appInfo.activityInfo.name)}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        activity.startActivity(mapIntent)
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Google Maps not installed.", Toast.LENGTH_SHORT)
                            .show()
                    }
                    searchBox.text.clear()
                }
            }

            "yt_search" -> {
                // Set the YouTube icon with error handling (prefer Revanced, then regular YouTube)
                val cachedRevanced = specialAppIconCache["app.revanced.android.youtube"]
                val cachedYouTube = specialAppIconCache["com.google.android.youtube"]
                val cachedIcon = cachedRevanced ?: cachedYouTube
                
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    // Load icon asynchronously to avoid blocking
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            // Try Revanced first
                            try {
                                val icon = activity.packageManager.getApplicationIcon("app.revanced.android.youtube")
                                specialAppIconCache["app.revanced.android.youtube"] = icon
                                (context as? Activity)?.runOnUiThread {
                                    if (holder.bindingAdapterPosition == position) {
                                        holder.appIcon.setImageDrawable(icon)
                                    }
                                }
                            } catch (e: Exception) {
                                // Fall back to regular YouTube
                                try {
                                    val icon = activity.packageManager.getApplicationIcon("com.google.android.youtube")
                                    specialAppIconCache["com.google.android.youtube"] = icon
                                    (context as? Activity)?.runOnUiThread {
                                        if (holder.bindingAdapterPosition == position) {
                                            holder.appIcon.setImageDrawable(icon)
                                        }
                                    }
                                } catch (e2: Exception) {
                                    // Icon already set to default, no need to update
                                }
                            }
                        } catch (e: Exception) {
                            // Icon already set to default, no need to update
                        }
                    }
                }
                holder.appName?.text = "Search ${appInfo.activityInfo.name} on YouTube"
                holder.itemView.setOnClickListener {
                    // Create an Intent to open YouTube search
                    val ytIntentUri =
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(appInfo.activityInfo.name)}")
                    val ytIntent = Intent(Intent.ACTION_VIEW, ytIntentUri)

                    // Try Revanced first, then regular YouTube
                    var appOpened = false
                    try {
                        ytIntent.setPackage("app.revanced.android.youtube")
                        activity.startActivity(ytIntent)
                        appOpened = true
                    } catch (e: Exception) {
                        try {
                            ytIntent.setPackage("com.google.android.youtube")
                            activity.startActivity(ytIntent)
                            appOpened = true
                        } catch (e: Exception) {
                            // Neither app is installed
                        }
                    }

                    if (!appOpened) {
                        Toast.makeText(
                            activity,
                            "YouTube app not installed. Opening in browser.",
                            Toast.LENGTH_SHORT
                        ).show()
                        activity.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                ytIntentUri
                            )
                        ) // Open in browser as fallback
                    }
                    searchBox.text.clear()
                }
            }

            "browser_search" -> {
                // Display browser search option
                holder.appIcon.setImageResource(R.drawable.ic_browser)
                holder.appName?.text = "Search ${appInfo.activityInfo.name} in Browser"
                holder.itemView.setOnClickListener {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.google.com/search?q=${appInfo.activityInfo.name}")
                        )
                    )
                    searchBox.text.clear()
                }
            }

            "math_result" -> {
                holder.appIcon.setImageResource(R.drawable.ic_calculator) // Ensure ic_calculator is in res/drawable
                holder.appName?.text = appInfo.activityInfo.name
            }

            else -> {
                // For real apps, use aggressive caching to improve performance
                // Check app validity once and cache result
                val isValidApp = packageValidityCache.getOrPut(packageName) {
                    appInfo.activityInfo?.applicationInfo != null
                }

                // Use cached label first (fast path)
                val cachedLabel = labelCache[packageName]
                if (cachedLabel != null) {
                    holder.appName?.text = cachedLabel
                } else {
                    // Try to load label synchronously for visible items (first 50) to avoid showing package name
                    // For items beyond position 50, load asynchronously
                    if (position < 50 && isValidApp) {
                        try {
                            val label = appInfo.loadLabel(activity.packageManager)?.toString()
                                ?: appInfo.activityInfo.packageName
                            labelCache[packageName] = label
                            holder.appName?.text = label
                        } catch (e: Exception) {
                            // If synchronous load fails, fall back to async loading
                            holder.appName?.text = appInfo.activityInfo?.packageName ?: "Loading..."
                            loadLabelAsync(holder, position, appInfo, packageName, isValidApp)
                        }
                    } else {
                        // For items beyond position 50, load asynchronously
                        holder.appName?.text = appInfo.activityInfo?.packageName ?: "Loading..."
                        loadLabelAsync(holder, position, appInfo, packageName, isValidApp)
                    }
                }

                // OPTIMIZATION: Use cached icon first (fast path)
                val cachedIcon = iconCache[packageName]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    // Show placeholder immediately to prevent UI freeze
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    // Load icon asynchronously in background with priority for visible items
                    val isVisible = position < 50 // Prioritize first 50 items
                    val executorToUse = if (isVisible) iconPreloadExecutor else executor
                    
                    executorToUse.execute {
                        try {
                            val icon = if (isValidApp) {
                                appInfo.loadIcon(activity.packageManager)
                            } else {
                                activity.getDrawable(R.drawable.ic_default_app_icon)
                            } as? Drawable ?: activity.getDrawable(R.drawable.ic_default_app_icon)!!
                            iconCache[packageName] = icon
                            // Update UI on main thread
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) {
                                    holder.appIcon.setImageDrawable(icon)
                                }
                            }
                        } catch (e: Exception) {
                            val fallbackIcon = activity.getDrawable(R.drawable.ic_default_app_icon)!!
                            iconCache[packageName] = fallbackIcon
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) {
                                    holder.appIcon.setImageDrawable(fallbackIcon)
                                }
                            }
                        }
                    }
                }
                
                // Pre-load next 10 icons for smooth scrolling
                if (position < appList.size - 1 && position % 5 == 0) {
                    preloadNextIcons(position + 1, minOf(position + 11, appList.size))
                }

                // Debounce clicks to prevent double-tap issues
                var lastClickTime = 0L
                val CLICK_DEBOUNCE_DELAY = 500L // 500ms debounce
                
                holder.itemView.setOnClickListener {
                    val currentTime = System.currentTimeMillis()
                    // Ignore clicks that are too close together (double-tap prevention)
                    if (currentTime - lastClickTime < CLICK_DEBOUNCE_DELAY) {
                        return@setOnClickListener
                    }
                    lastClickTime = currentTime
                    
                    val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        val prefs = activity.getSharedPreferences("com.guruswarupa.launch.PREFS", Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("usage_$packageName", 0)
                        prefs.edit().putInt("usage_$packageName", currentCount + 1).apply()

                        // Use cached label or fallback to package name (avoid blocking loadLabel call)
                        val appName = labelCache[packageName] ?: appInfo.activityInfo?.packageName ?: packageName

                        // Show timer dialog only for social media and entertainment apps
                        val shouldShowTimer = activity.appCategoryManager.shouldShowTimer(packageName, appName)
                        
                        if (shouldShowTimer) {
                            // Show timer dialog before launching app
                            activity.appTimerManager.showTimerDialog(packageName, appName) { timerDuration ->
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
                            // Launch directly without timer for productivity and other apps
                            if (activity.appLockManager.isAppLocked(packageName)) {
                                activity.appLockManager.verifyPin { isAuthenticated ->
                                    if (isAuthenticated) {
                                        activity.startActivity(intent)
                                    }
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
                        Toast.makeText(activity, "Cannot launch app", Toast.LENGTH_SHORT).show()
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

    /**
     * Loads app label asynchronously and updates the view.
     * Uses notifyItemChanged to ensure the view updates even if recycled.
     */
    private fun loadLabelAsync(
        holder: ViewHolder,
        position: Int,
        appInfo: ResolveInfo,
        packageName: String,
        isValidApp: Boolean
    ) {
        executor.execute {
            try {
                val label = if (isValidApp) {
                    appInfo.loadLabel(activity.packageManager)?.toString()
                        ?: appInfo.activityInfo.packageName
                } else {
                    appInfo.activityInfo?.name ?: "Unknown"
                }
                labelCache[packageName] = label
                
                // Update metadata cache if available
                try {
                    activity.cacheManager.updateMetadataCache(
                        packageName,
                        AppMetadata(
                            packageName = packageName,
                            activityName = appInfo.activityInfo.name,
                            label = label,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    // Ignore cache update errors (cacheManager might not be initialized)
                }
                
                // Update UI on main thread - use notifyItemChanged to ensure update even if recycled
                (context as? Activity)?.runOnUiThread {
                    // Check if this holder still shows the same item
                    if (holder.bindingAdapterPosition == position && 
                        position < appList.size && 
                        appList[position].activityInfo.packageName == packageName) {
                        holder.appName?.text = label
                    } else {
                        // If view was recycled, notify the adapter to refresh this position
                        // This ensures the label is shown even if the view was recycled
                        notifyItemChanged(position)
                    }
                }
            } catch (e: Exception) {
                val fallbackLabel = appInfo.activityInfo?.packageName ?: "Unknown"
                labelCache[packageName] = fallbackLabel
                (context as? Activity)?.runOnUiThread {
                    if (holder.bindingAdapterPosition == position && 
                        position < appList.size && 
                        appList[position].activityInfo.packageName == packageName) {
                        holder.appName?.text = fallbackLabel
                    } else {
                        // If view was recycled, notify the adapter to refresh this position
                        notifyItemChanged(position)
                    }
                }
            }
        }
    }

    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)

        // Update favorite menu item text
        val favoriteMenuItem = popupMenu.menu.findItem(R.id.toggle_favorite)
        if (favoriteMenuItem != null) {
            val isFavorite = activity.favoriteAppManager.isFavoriteApp(packageName)
            favoriteMenuItem.title = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
            // Force white text color using SpannableString
            val whiteColor = ContextCompat.getColor(activity, android.R.color.white)
            val spannable = android.text.SpannableString(favoriteMenuItem.title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(whiteColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            favoriteMenuItem.title = spannable
        }

        // Update hide menu item text
        val hideMenuItem = popupMenu.menu.findItem(R.id.toggle_hide)
        if (hideMenuItem != null) {
            try {
                val isHidden = activity.hiddenAppManager.isAppHidden(packageName)
                hideMenuItem.title = if (isHidden) "Unhide App" else "Hide App"
                // Force white text color using SpannableString
                val whiteColor = ContextCompat.getColor(activity, android.R.color.white)
                val spannable = android.text.SpannableString(hideMenuItem.title)
                spannable.setSpan(android.text.style.ForegroundColorSpan(whiteColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                hideMenuItem.title = spannable
            } catch (e: UninitializedPropertyAccessException) {
                hideMenuItem.isVisible = false
            }
        }
        
        // Force white text for all menu items
        val whiteColor = ContextCompat.getColor(activity, android.R.color.white)
        for (i in 0 until popupMenu.menu.size()) {
            val item = popupMenu.menu.getItem(i)
            val title = item.title?.toString() ?: continue
            val spannable = android.text.SpannableString(title)
            spannable.setSpan(android.text.style.ForegroundColorSpan(whiteColor), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.title = spannable
        }

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.app_info -> {
                    showAppInfo(packageName)
                    true
                }
                R.id.share_app -> {
                    shareApp(packageName, appInfo)
                    true
                }
                R.id.uninstall_app -> {
                    uninstallApp(packageName)
                    true
                }
                R.id.toggle_favorite -> {
                    toggleFavoriteApp(packageName, appInfo)
                    true
                }
                R.id.toggle_hide -> {
                    toggleHideApp(packageName, appInfo)
                    true
                }
                else -> false
            }
        }

        // Show the menu and then fix text colors
        popupMenu.show()
        
        // Fix text colors after menu is shown using reflection
        fixPopupMenuTextColors(popupMenu)
    }
    
    private fun fixPopupMenuTextColors(popupMenu: PopupMenu) {
        try {
            val whiteColor = ContextCompat.getColor(activity, android.R.color.white)
            
            // Try to get the ListView from the popup menu using reflection
            val popupField = popupMenu.javaClass.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popupMenu)
            val menuPopupHelperClass = menuPopupHelper?.javaClass
            
            // Try different field names for different Android versions
            val listViewFieldNames = arrayOf("mDropDownList", "mPopup", "mListView")
            var listView: android.widget.ListView? = null
            
            for (fieldName in listViewFieldNames) {
                try {
                    val listViewField = menuPopupHelperClass?.getDeclaredField(fieldName)
                    listViewField?.isAccessible = true
                    val result = listViewField?.get(menuPopupHelper)
                    if (result is android.widget.ListView) {
                        listView = result
                        break
                    }
                } catch (e: NoSuchFieldException) {
                    // Try next field name
                }
            }
            
            // Function to fix text colors in a view
            fun fixTextColors(view: View) {
                if (view is TextView) {
                    view.setTextColor(whiteColor)
                } else if (view is ViewGroup) {
                    findTextViewsAndSetColor(view, whiteColor)
                }
            }
            
            // If we got the ListView, fix text colors immediately and with delays
            listView?.let { lv ->
                // Fix immediately
                try {
                    for (i in 0 until lv.childCount) {
                        fixTextColors(lv.getChildAt(i))
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                
                // Fix after a short delay (items might not be rendered yet)
                lv.post {
                    try {
                        for (i in 0 until lv.childCount) {
                            fixTextColors(lv.getChildAt(i))
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                
                // Fix after a longer delay (for async rendering)
                lv.postDelayed({
                    try {
                        for (i in 0 until lv.childCount) {
                            fixTextColors(lv.getChildAt(i))
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }, 50)
                
                // Fix after an even longer delay (for very slow rendering)
                lv.postDelayed({
                    try {
                        for (i in 0 until lv.childCount) {
                            fixTextColors(lv.getChildAt(i))
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }, 150)
                
                // Also set a global layout listener to catch any late-rendered items
                lv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try {
                            for (i in 0 until lv.childCount) {
                                fixTextColors(lv.getChildAt(i))
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                        // Remove listener after first layout to avoid performance issues
                        lv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                })
            }
        } catch (e: Exception) {
            // If reflection fails, the style should still apply
            e.printStackTrace()
        }
    }
    
    private fun findTextViewsAndSetColor(viewGroup: ViewGroup, color: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(color)
            } else if (child is ViewGroup) {
                findTextViewsAndSetColor(child, color)
            }
        }
    }

    private fun showAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    private fun shareApp(packageName: String, appInfo: ResolveInfo) {
        // Use cached label or load async to avoid blocking
        val appName = labelCache[packageName] ?: run {
            try {
                appInfo.loadLabel(activity.packageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                packageName
            }
        }
        val shareManager = ShareManager(activity)
        shareManager.shareApk(packageName, appName)
    }

    private fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
            data = Uri.parse("package:$packageName")
        }
        activity.startActivity(intent)
    }

    private fun toggleFavoriteApp(packageName: String, appInfo: ResolveInfo) {
        // Use cached label or fallback to avoid blocking
        val appName = labelCache[packageName] ?: run {
            try {
                appInfo.loadLabel(activity.packageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                packageName
            }
        }
        val isFavorite = activity.favoriteAppManager.isFavoriteApp(packageName)
        
        if (isFavorite) {
            activity.favoriteAppManager.removeFavoriteApp(packageName)
            Toast.makeText(activity, "Removed $appName from favorites", Toast.LENGTH_SHORT).show()
        } else {
            activity.favoriteAppManager.addFavoriteApp(packageName)
            Toast.makeText(activity, "Added $appName to favorites", Toast.LENGTH_SHORT).show()
        }
        
        // Optimize: Filter existing list instead of reloading everything
        activity.filterAppsWithoutReload()
        // Refresh dock toggle icon
        activity.appDockManager.refreshFavoriteToggle()
    }

    private fun toggleHideApp(packageName: String, appInfo: ResolveInfo) {
        try {
            // Use cached label or fallback to avoid blocking
        val appName = labelCache[packageName] ?: run {
            try {
                appInfo.loadLabel(activity.packageManager)?.toString() ?: packageName
            } catch (e: Exception) {
                packageName
            }
        }
        val isHidden = activity.hiddenAppManager.isAppHidden(packageName)
        
        if (isHidden) {
            activity.hiddenAppManager.unhideApp(packageName)
            Toast.makeText(activity, "Unhid $appName", Toast.LENGTH_SHORT).show()
        } else {
            activity.hiddenAppManager.hideApp(packageName)
            Toast.makeText(activity, "Hid $appName", Toast.LENGTH_SHORT).show()
        }
        
        // Reload app list to reflect changes
        activity.filterAppsWithoutReload()
        } catch (e: UninitializedPropertyAccessException) {
            Toast.makeText(activity, "Hidden apps feature not available", Toast.LENGTH_SHORT).show()
        }
    }

    fun showCallConfirmationDialog(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName) // Fetch phone number for the contact

        AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle("Call $contactName?")
            .setMessage("Phone: $phoneNumber\nDo you want to proceed?")
            .setPositiveButton("Call") { _, _ ->
                call(phoneNumber)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun call(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        activity.startActivity(intent)
    }

    private fun getPhoneNumberForContact(contactName: String): String {
        val contentResolver: ContentResolver = activity.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(contactName)

        val cursor: Cursor? = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        var phoneNumber: String? = null

        cursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    phoneNumber = it.getString(numberIndex)
                }
            }
        }

        return phoneNumber ?: "Not found"
    }
}