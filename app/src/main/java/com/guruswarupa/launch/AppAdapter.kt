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
        executor.execute {
            val newItems = ArrayList(newAppList)
            val isFirstLoad = itemsRendered == 0

            // Update UI on main thread with DiffUtil for efficient updates
            (context as? Activity)?.runOnUiThread {
                val diffCallback = AppListDiffCallback(appList, newItems)
                val diffResult = DiffUtil.calculateDiff(diffCallback)
                
                appList.clear()
                appList.addAll(newItems)
                diffResult.dispatchUpdatesTo(this)
                
                // Pre-load icons asynchronously after initial render (only on first load)
                if (isFirstLoad && newItems.isNotEmpty()) {
                    itemsRendered = newItems.size
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

        // Always show the name in both grid and list mode
        holder.appName?.visibility = View.VISIBLE

        // Show usage time only in list mode and when power saver is disabled
        // Hide usage time in power saver mode to save battery (no usage queries)
        // Defer usage stats loading on initial render for better performance
        val isPowerSaverActive = activity.appDockManager.isPowerSaverActive()
        
        if (!isGridMode && holder.appUsageTime != null && !isPowerSaverActive) {
            // On first 20 items, defer usage stats to improve initial render speed
            // After that, load immediately for better UX
            if (position < 20 && itemsRendered < 20) {
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
                    // Show placeholder immediately
                    holder.appName?.text = appInfo.activityInfo?.packageName ?: "Loading..."
                    // Load label asynchronously in background
                    executor.execute {
                        try {
                            val label = if (isValidApp) {
                                appInfo.loadLabel(activity.packageManager)?.toString()
                                    ?: appInfo.activityInfo.packageName
                            } else {
                                appInfo.activityInfo?.name ?: "Unknown"
                            }
                            labelCache[packageName] = label
                            // Update UI on main thread
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) {
                                    holder.appName?.text = label
                                }
                            }
                        } catch (e: Exception) {
                            val fallbackLabel = appInfo.activityInfo?.packageName ?: "Unknown"
                            labelCache[packageName] = fallbackLabel
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) {
                                    holder.appName?.text = fallbackLabel
                                }
                            }
                        }
                    }
                }

                // Use cached icon first (fast path)
                val cachedIcon = iconCache[packageName]
                if (cachedIcon != null) {
                    holder.appIcon.setImageDrawable(cachedIcon)
                } else {
                    // Show placeholder immediately
                    holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
                    // Load icon asynchronously in background
                    iconPreloadExecutor.execute {
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

    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)

        // Update favorite menu item text
        val favoriteMenuItem = popupMenu.menu.findItem(R.id.toggle_favorite)
        if (favoriteMenuItem != null) {
            val isFavorite = activity.favoriteAppManager.isFavoriteApp(packageName)
            favoriteMenuItem.title = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
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
            val listViewFieldNames = arrayOf("mDropDownList", "mPopup")
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
            
            // If we got the ListView, fix text colors
            listView?.post {
                try {
                    for (i in 0 until listView.childCount) {
                        val itemView = listView.getChildAt(i)
                        if (itemView is TextView) {
                            itemView.setTextColor(whiteColor)
                        } else if (itemView is ViewGroup) {
                            findTextViewsAndSetColor(itemView, whiteColor)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Also try after a small delay in case items load asynchronously
            listView?.postDelayed({
                try {
                    for (i in 0 until listView.childCount) {
                        val itemView = listView.getChildAt(i)
                        if (itemView is TextView) {
                            itemView.setTextColor(whiteColor)
                        } else if (itemView is ViewGroup) {
                            findTextViewsAndSetColor(itemView, whiteColor)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 50)
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