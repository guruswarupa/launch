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
import android.content.ComponentName

import com.guruswarupa.launch.managers.AppUsageStatsManager
import com.guruswarupa.launch.managers.TypographyManager
import com.guruswarupa.launch.core.ShareManager
import com.guruswarupa.launch.models.AppMetadata
import com.guruswarupa.launch.models.Constants
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.CornerSize

class AppAdapter(
    private val activity: MainActivity,
    var appList: MutableList<ResolveInfo>,
    private val searchBox: AutoCompleteTextView,
    private var isGridMode: Boolean,
    private val context: Context
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateViewMode(isGrid: Boolean) {
        if (this.isGridMode != isGrid) {
            this.isGridMode = isGrid
            notifyDataSetChanged()
        }
    }

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1
        const val VIEW_TYPE_SEPARATOR = 2
        const val SEPARATOR_PACKAGE = "com.guruswarupa.launch.SEPARATOR"
        
        private val SPECIAL_PACKAGE_NAMES = setOf(
            "contact_unified", "play_store_search", "maps_search", "yt_search", "browser_search", "math_result",
            "file_result", "settings_result", "system_settings_result"
        )
        
        private const val PRIORITY_HIGH = 100
        private const val PRIORITY_MEDIUM = 50
        private const val PRIORITY_LOW = 10
        private const val PRIORITY_BACKGROUND = 0
    }

    private val usageStatsManager = AppUsageStatsManager(activity)
    
    // Optimized cache sizes for minimal RAM usage - only cache visible + nearby apps
    private val iconCache = object : LinkedHashMap<String, Drawable>(40, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean {
            return size > 40 // Keep only most recently used icons
        }
    }
    private val labelCache = object : LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 100 // Labels are small, but still limit
        }
    }
    private val specialAppIconCache = object : LinkedHashMap<String, Drawable>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean {
            return size > 10 // Only ~10 special apps max
        }
    }
    private val contactPhotoCache = object : LinkedHashMap<String, Drawable>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean {
            return size > 20 // Contacts have photos, keep minimal
        }
    }
    private val usageCache = object : LinkedHashMap<String, String>(30, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 30 // Usage stats only for visible apps
        }
    }
    
    private val executor = Executors.newSingleThreadExecutor()
    private var itemsRendered = 0
    private var isDestroyed = false
    
    private val prefs = context.getSharedPreferences(Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE)
    private var currentIconStyle = prefs.getString(Constants.Prefs.ICON_STYLE, "squircle") ?: "round"
    private var currentIconSize = prefs.getInt(Constants.Prefs.ICON_SIZE, 40)

    private class PriorityRunnable(val priority: Int, val action: Runnable) : Runnable, Comparable<PriorityRunnable> {
        override fun run() = action.run()
        override fun compareTo(other: PriorityRunnable): Int = other.priority.compareTo(this.priority)
    }
    
    // Wrapper task that can be tracked and cancelled - implements Comparable for PriorityBlockingQueue
    private class TrackedTask(
        private val priorityRunnable: PriorityRunnable,
        private val packageName: String
    ) : Runnable, Comparable<TrackedTask>, java.util.concurrent.Future<Boolean> {
        @Volatile private var isDone = false
        @Volatile private var isCancelled = false
        
        override fun run() {
            try {
                priorityRunnable.run()
            } finally {
                isDone = true
            }
        }
        
        override fun compareTo(other: TrackedTask): Int {
            // Delegate comparison to the wrapped PriorityRunnable
            return this.priorityRunnable.compareTo(other.priorityRunnable)
        }
        
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            if (isDone || isCancelled) return false
            isCancelled = true
            return true
        }
        
        override fun isCancelled(): Boolean = isCancelled
        override fun isDone(): Boolean = isDone
        
        // These methods are not needed for our use case but required by Future interface
        override fun get(): Boolean? = null
        override fun get(timeout: Long, unit: TimeUnit): Boolean? = null
    }
    
    // Track pending tasks per package to avoid redundant work and save RAM
    private val pendingIconTasks = ConcurrentHashMap<String, TrackedTask>()
    private val pendingLabelTasks = ConcurrentHashMap<String, java.util.concurrent.Future<*>>()

    private val iconPreloadExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, // Reduced from 2, 2 to 1, 1 to save threads and memory
        PriorityBlockingQueue<Runnable>()
    ) { runnable ->
        Thread(runnable, "IconPreload-${System.identityHashCode(runnable)}").apply {
            priority = Thread.MIN_PRIORITY
        }
    }
    
    fun clearUsageCache() {
        usageCache.clear()
    }

    fun clearContactPhotoCache() {
        contactPhotoCache.clear()
    }
    
    /**
     * Clears some caches to free memory without destroying the adapter.
     */
    fun onTrimMemory() {
        iconCache.clear()
        contactPhotoCache.clear()
        specialAppIconCache.clear()
        usageCache.clear()
        // Cancel pending tasks to free resources
        pendingIconTasks.values.forEach { it.cancel(true) }
        pendingIconTasks.clear()
        pendingLabelTasks.values.forEach { it.cancel(true) }
        pendingLabelTasks.clear()
    }

    /**
     * Clears all caches to free memory. Call when adapter is no longer needed.
     */
    fun destroy() {
        isDestroyed = true
        iconCache.clear()
        labelCache.clear()
        specialAppIconCache.clear()
        contactPhotoCache.clear()
        usageCache.clear()
        // Cancel all pending tasks
        pendingIconTasks.values.forEach { it.cancel(true) }
        pendingIconTasks.clear()
        pendingLabelTasks.values.forEach { it.cancel(true) }
        pendingLabelTasks.clear()
        executor.shutdown()
        iconPreloadExecutor.shutdown()
        try {
            if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow()
            }
            if (!iconPreloadExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                iconPreloadExecutor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            iconPreloadExecutor.shutdownNow()
        }
    }

    fun updateIconStyle(style: String) {
        currentIconStyle = style
        (context as? Activity)?.runOnUiThread {
            notifyDataSetChanged()
        }
    }

    fun updateIconSize(size: Int) {
        currentIconSize = size
        (context as? Activity)?.runOnUiThread {
            notifyDataSetChanged()
        }
    }

    private fun getShapeAppearanceModel(): ShapeAppearanceModel {
        val density = context.resources.displayMetrics.density
        val builder = ShapeAppearanceModel.builder()
        
        when (currentIconStyle) {
            "round" -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.5f) as CornerSize)
            }
            "squircle" -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.2f) as CornerSize)
            }
            "squared" -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 4f * density)
            }
            "teardrop" -> {
                builder.setTopLeftCorner(CornerFamily.ROUNDED, 0f)
                builder.setTopLeftCornerSize(RelativeCornerSize(0.5f) as CornerSize)
                builder.setTopRightCorner(CornerFamily.ROUNDED, 0f)
                builder.setTopRightCornerSize(RelativeCornerSize(0.5f) as CornerSize)
                builder.setBottomLeftCorner(CornerFamily.ROUNDED, 0f)
                builder.setBottomLeftCornerSize(RelativeCornerSize(0.5f) as CornerSize)
                builder.setBottomRightCorner(CornerFamily.ROUNDED, 0f)
                builder.setBottomRightCornerSize(RelativeCornerSize(0.1f) as CornerSize)
            }
            "vortex" -> {
                builder.setAllCorners(CornerFamily.CUT, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.2f) as CornerSize)
            }
            "overlay" -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 12f * density)
            }
            else -> {
                builder.setAllCorners(CornerFamily.ROUNDED, 0f)
                builder.setAllCornerSizes(RelativeCornerSize(0.5f) as CornerSize)
            }
        }
        return builder.build()
    }

    fun getAppLabel(position: Int): String {
        if (position < 0 || position >= appList.size) return ""
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName
        if (packageName == SEPARATOR_PACKAGE) return ""
        if (packageName in SPECIAL_PACKAGE_NAMES) {
            return appInfo.activityInfo.name ?: ""
        }
        return labelCache[packageName] ?: appInfo.activityInfo.name ?: packageName
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: com.google.android.material.imageview.ShapeableImageView? = view.findViewById(R.id.app_icon)
        val appName: TextView? = view.findViewById(R.id.app_name)
        val appUsageTime: TextView? = view.findViewById(R.id.app_usage_time)
        val container: View? = view.findViewById(R.id.app_item_container)
        var lastClickTime = 0L
    }

    fun updateAppList(newAppList: List<ResolveInfo>) {
        if (isDestroyed) return
        
        val newItems = ArrayList(newAppList)
        val isFirstLoad = itemsRendered == 0

        try {
            val metadataCache = activity.cacheManager.getMetadataCache()
            for (app in newItems) {
                val packageName = app.activityInfo.packageName
                if (packageName == SEPARATOR_PACKAGE) continue
                val cachedMetadata = metadataCache[packageName]
                if (cachedMetadata != null && !labelCache.containsKey(packageName)) {
                    labelCache[packageName] = cachedMetadata.label
                }
            }
        } catch (_: Exception) {}

        (context as? Activity)?.runOnUiThread {
            val diffCallback = AppListDiffCallback(appList, newItems)
            val diffResult = DiffUtil.calculateDiff(diffCallback)
            
            appList.clear()
            appList.addAll(newItems)
            diffResult.dispatchUpdatesTo(this)
            
            if (isFirstLoad && newItems.isNotEmpty()) {
                itemsRendered = newItems.size
                executor.execute { preloadIcons(newItems.filter { it.activityInfo.packageName != SEPARATOR_PACKAGE }) }
            }
        }
    }
    
    private class AppListDiffCallback(
        private val oldList: List<ResolveInfo>,
        private val newList: List<ResolveInfo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            if (oldItem.activityInfo.packageName == SEPARATOR_PACKAGE && newItem.activityInfo.packageName == SEPARATOR_PACKAGE) {
                return oldItem.activityInfo.name == newItem.activityInfo.name
            }
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.activityInfo.packageName == newItem.activityInfo.packageName &&
                   oldItem.activityInfo.name == newItem.activityInfo.name
        }
    }
    
    private fun preloadIcons(apps: List<ResolveInfo>) {
        if (isDestroyed) return
        
        // Load only visible apps immediately (first screen typically shows ~15-20 apps)
        val immediateLoad = apps.take(15)
        for (app in immediateLoad) {
            submitIconLoadTask(app, PRIORITY_MEDIUM) // Higher priority for visible apps
        }
        
        // Preload next screen worth of apps with delay
        val remainingApps = apps.drop(15).take(20) // Limit total preload to save RAM
        if (!executor.isShutdown && remainingApps.isNotEmpty()) {
            executor.execute {
                // Longer delays to reduce CPU/RAM pressure
                for ((index, app) in remainingApps.withIndex()) {
                    submitIconLoadTask(app, PRIORITY_BACKGROUND)
                    if (index % 5 == 4) { // Sleep every 5 apps
                        try { Thread.sleep(200) } catch (_: InterruptedException) {}
                    }
                }
            }
        }
        // Don't load all apps - let on-demand loading handle the rest
    }
    
    private fun submitIconLoadTask(app: ResolveInfo, priority: Int, holder: ViewHolder? = null, position: Int = -1) {
        if (isDestroyed) return
        
        val packageName = app.activityInfo.packageName
        if (packageName == SEPARATOR_PACKAGE) return
        
        if (packageName in SPECIAL_PACKAGE_NAMES || iconCache.containsKey(packageName)) {
            if (holder != null && holder.appIcon != null && iconCache.containsKey(packageName)) {
                val icon = iconCache[packageName]
                (context as? Activity)?.runOnUiThread {
                    if (holder.bindingAdapterPosition == position && holder.itemView.tag.toString() == packageName) {
                        holder.appIcon.setImageDrawable(icon)
                        activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon)
                    }
                }
            }
            return
        }
        
        // Cancel any pending task for this package to avoid redundant work
        pendingIconTasks[packageName]?.cancel(true)
        
        // Use execute instead of submit to avoid FutureTask wrapping (PriorityBlockingQueue needs Comparable)
        val priorityRunnable = PriorityRunnable(priority) {
            try {
                if (!iconCache.containsKey(packageName)) {
                    val icon = app.loadIcon(activity.packageManager)
                    iconCache[packageName] = icon
                    
                    if (holder != null && holder.appIcon != null) {
                        (context as? Activity)?.runOnUiThread {
                            // Verify ViewHolder still represents the same app by checking tag
                            if (holder.itemView.tag.toString() == packageName) {
                                holder.appIcon.setImageDrawable(icon)
                                activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        
        // Track with a wrapper that we can cancel
        val trackedTask = TrackedTask(priorityRunnable, packageName)
        iconPreloadExecutor.execute(trackedTask)
        pendingIconTasks[packageName] = trackedTask
        
        // Clean up completed/cancelled tasks periodically
        if (pendingIconTasks.size > 100) {
            pendingIconTasks.entries.removeAll { it.value.isDone }
        }
    }

    private fun preloadNextIcons(startPosition: Int, endPosition: Int) {
        if (isDestroyed) return
        
        val size = appList.size
        if (startPosition >= size) return
        
        // Only preload a small window ahead to save RAM
        val actualEnd = minOf(startPosition + 5, size) // Reduced from 9 to 5
        if (actualEnd <= startPosition) return
        
        val appsToPreload = try {
            ArrayList(appList.subList(startPosition, actualEnd))
        } catch (_: Exception) { return }
        
        // Use lower priority and only load if not already cached
        for (app in appsToPreload) { 
            if (app.activityInfo.packageName != SEPARATOR_PACKAGE && !iconCache.containsKey(app.activityInfo.packageName)) {
                submitIconLoadTask(app, PRIORITY_LOW) // Lowest priority for background preload
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (appList[position].activityInfo.packageName == SEPARATOR_PACKAGE) return VIEW_TYPE_SEPARATOR
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = when (viewType) {
            VIEW_TYPE_SEPARATOR -> R.layout.item_app_separator
            VIEW_TYPE_GRID -> R.layout.app_item_grid
            else -> R.layout.app_item
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        TypographyManager.applyToView(view)
        return ViewHolder(view)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = appList[position]
        val packageName = appInfo.activityInfo.packageName

        if (packageName == SEPARATOR_PACKAGE) {
            return
        }
        
        // Only apply expensive operations if view is being recycled (has old data)
        val needsRefresh = holder.itemView.tag != null && holder.itemView.tag != packageName
        
        if (needsRefresh) {
            // Re-apply typography only when needed
            TypographyManager.applyToView(holder.itemView)
            
            // Apply icon shape style programmatically
            holder.appIcon?.shapeAppearanceModel = getShapeAppearanceModel()
            
            // Apply icon size - avoid layout param changes unless necessary
            val sizeInPx = (currentIconSize * context.resources.displayMetrics.density).toInt()
            val currentParams = holder.appIcon?.layoutParams
            if (currentParams != null && (currentParams.width != sizeInPx || currentParams.height != sizeInPx)) {
                currentParams.width = sizeInPx
                currentParams.height = sizeInPx
                holder.appIcon?.layoutParams = currentParams
            }
        }
        
        holder.appIcon?.background = null
        holder.itemView.elevation = 0f
        // Tag the view with package name to track correct icon assignment during recycling
        holder.itemView.tag = packageName

        holder.appName?.visibility = View.VISIBLE
        holder.appUsageTime?.visibility = View.GONE

        when (packageName) {
            "contact_unified" -> {
                val contactName = appInfo.activityInfo.name
                val cachedPhoto = contactPhotoCache[contactName]
                if (cachedPhoto != null) {
                    holder.appIcon?.setImageDrawable(cachedPhoto)
                } else if (holder.itemView.tag.toString() != contactName) { // Avoid duplicate loads
                    holder.appIcon?.setImageResource(R.drawable.ic_person)
                    executor.execute {
                        try {
                            val photoUri = getPhotoUriForContact(contactName)
                            if (photoUri != null) {
                                val drawable = activity.contentResolver.openInputStream(photoUri.toUri())?.use { inputStream ->
                                    Drawable.createFromStream(inputStream, photoUri)
                                }
                                if (drawable != null) {
                                    contactPhotoCache[contactName] = drawable
                                    (context as? Activity)?.runOnUiThread {
                                        if (holder.itemView.tag.toString() == contactName) { // Verify with tag
                                            holder.appIcon?.setImageDrawable(drawable)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                holder.appName?.text = contactName
                holder.itemView.setOnClickListener {
                    showContactChoiceDialog(contactName)
                    searchBox.text.clear()
                }
            }

            "play_store_search" -> {
                val cachedIcon = specialAppIconCache["com.android.vending"]
                if (cachedIcon != null) {
                    holder.appIcon?.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon?.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            val icon = activity.packageManager.getApplicationIcon("com.android.vending")
                            specialAppIconCache["com.android.vending"] = icon
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) holder.appIcon?.setImageDrawable(icon)
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
                    holder.appIcon?.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon?.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            val icon = activity.packageManager.getApplicationIcon("com.google.android.apps.maps")
                            specialAppIconCache["com.google.android.apps.maps"] = icon
                            (context as? Activity)?.runOnUiThread {
                                if (holder.bindingAdapterPosition == position) holder.appIcon?.setImageDrawable(icon)
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
                    holder.appIcon?.setImageDrawable(cachedIcon)
                } else {
                    holder.appIcon?.setImageResource(R.drawable.ic_default_app_icon)
                    executor.execute {
                        try {
                            try {
                                val icon = activity.packageManager.getApplicationIcon("app.revanced.android.youtube")
                                specialAppIconCache["app.revanced.android.youtube"] = icon
                                (context as? Activity)?.runOnUiThread {
                                    if (holder.bindingAdapterPosition == position) holder.appIcon?.setImageDrawable(icon)
                                }
                            } catch (_: Exception) {
                                try {
                                    val icon = activity.packageManager.getApplicationIcon("com.google.android.youtube")
                                    specialAppIconCache["com.google.android.youtube"] = icon
                                    (context as? Activity)?.runOnUiThread {
                                        if (holder.bindingAdapterPosition == position) holder.appIcon?.setImageDrawable(icon)
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
                holder.appIcon?.setImageResource(R.drawable.ic_browser)
                holder.appName?.text = activity.getString(R.string.search_in_browser, appInfo.activityInfo.name)
                holder.itemView.setOnClickListener {
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
                holder.appIcon?.setImageResource(R.drawable.ic_settings)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    val settingAction = appInfo.activityInfo.nonLocalizedLabel?.toString() ?: ""
                    val intent = com.guruswarupa.launch.utils.AndroidSettingsHelper.createSettingsIntent(activity, settingAction)
                    activity.startActivity(intent ?: Intent(Settings.ACTION_SETTINGS))
                    searchBox.text.clear()
                }
            }
            
            "system_settings_result" -> {
                holder.appIcon?.setImageResource(R.drawable.ic_settings)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    val settingAction = appInfo.activityInfo.nonLocalizedLabel?.toString() ?: ""
                    val intent = com.guruswarupa.launch.utils.AndroidSettingsHelper.createSettingsIntent(activity, settingAction)
                    activity.startActivity(intent ?: Intent(Settings.ACTION_SETTINGS))
                    searchBox.text.clear()
                }
            }

            "file_result" -> {
                holder.appIcon?.setImageResource(R.drawable.ic_file)
                holder.appName?.text = appInfo.activityInfo.name
                holder.itemView.setOnClickListener {
                    val filePath = appInfo.activityInfo.nonLocalizedLabel.toString()
                    val file = File(filePath)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, activity.contentResolver.getType(uri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { activity.startActivity(intent) } catch (_: Exception) {
                            Toast.makeText(activity, "No app found to open this file", Toast.LENGTH_SHORT).show()
                        }
                    }
                    searchBox.text.clear()
                }
            }

            "math_result" -> {
                holder.appIcon?.setImageResource(R.drawable.ic_calculator)
                holder.appName?.text = appInfo.activityInfo.name
            }

            else -> {
                // Special handling for internal launcher activities to ensure correct names
                val activityName = appInfo.activityInfo.name
                if (packageName == activity.packageName) {
                    when {
                        activityName.contains("SettingsActivity") -> {
                            holder.appName?.text = activity.getString(R.string.settings_app_name)
                            holder.appIcon?.setImageResource(R.mipmap.ic_launcher)
                        }
                        activityName.contains("EncryptedVaultActivity") -> {
                            holder.appName?.text = activity.getString(R.string.vault_app_name)
                            holder.appIcon?.setImageResource(R.drawable.ic_vault_icon)
                        }
                        else -> {
                            holder.appName?.text = labelCache[packageName] ?: appInfo.loadLabel(activity.packageManager).toString()
                            val cachedIcon = iconCache[packageName]
                            if (cachedIcon != null) {
                                holder.appIcon?.setImageDrawable(cachedIcon)
                                activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon!!)
                            } else {
                                holder.appIcon?.setImageResource(R.drawable.ic_default_app_icon)
                                submitIconLoadTask(appInfo, PRIORITY_HIGH, holder, position)
                            }
                        }
                    }
                } else {
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
                                holder.appName?.text = packageName
                                loadLabelAsync(holder, position, appInfo, packageName)
                            }
                        } else {
                            holder.appName?.text = packageName
                            loadLabelAsync(holder, position, appInfo, packageName)
                        }
                    }

                    val cachedIcon = iconCache[packageName]
                    if (cachedIcon != null) {
                        holder.appIcon?.setImageDrawable(cachedIcon)
                        activity.appTimerManager.applyGrayscaleIfOverLimit(packageName, holder.appIcon!!)
                    } else {
                        holder.appIcon?.setImageResource(R.drawable.ic_default_app_icon)
                        submitIconLoadTask(appInfo, PRIORITY_HIGH, holder, position)
                    }
                }
                
                // Only preload when scrolling near the end of loaded items (every 12 items instead of 8)
                if (position < appList.size - 1 && position % 12 == 0) {
                    preloadNextIcons(position + 1, position + 6)
                }

                val clickDebounceDelay = 500L
                holder.itemView.setOnClickListener {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - holder.lastClickTime < clickDebounceDelay) return@setOnClickListener
                    holder.lastClickTime = currentTime
                    
                    if (activity.appTimerManager.isAppOverDailyLimit(packageName)) {
                        val appName = labelCache[packageName] ?: packageName
                        Toast.makeText(activity, "Daily limit reached for $appName", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    // Handle internal launcher activities specifically (like SettingsActivity)
                    val intent = if (packageName == activity.packageName) {
                        Intent().apply {
                            component = ComponentName(packageName, appInfo.activityInfo.name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        activity.packageManager.getLaunchIntentForPackage(packageName)
                    }
                    
                    if (intent != null) {
                        val currentCount = prefs.getInt("usage_$packageName", 0)
                        prefs.edit { putInt("usage_$packageName", currentCount + 1) }

                        val appName = labelCache[packageName] ?: packageName
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
        if (isDestroyed || executor.isShutdown) return
        if (labelCache.containsKey(packageName)) return // Already cached
        
        // Cancel any pending label task for this package
        pendingLabelTasks[packageName]?.cancel(true)
        
        // Use a Runnable wrapper that we can track
        val labelTask = object : Runnable, java.util.concurrent.Future<Boolean> {
            @Volatile private var isDone = false
            @Volatile private var isCancelled = false
            
            override fun run() {
                try {
                    val label = appInfo.loadLabel(activity.packageManager).toString()
                    labelCache[packageName] = label
                    try {
                        activity.cacheManager.updateMetadataCache(packageName,
                            AppMetadata(packageName, appInfo.activityInfo.name, label, System.currentTimeMillis())
                        )
                    } catch (_: Exception) {}
                    
                    if (!isDestroyed && !(activity as? Activity)?.isFinishing!!) {
                        (context as? Activity)?.runOnUiThread {
                            // Verify ViewHolder still represents the same app
                            if (holder.itemView.tag.toString() == packageName && holder.bindingAdapterPosition == position) {
                                holder.appName?.text = label
                            }
                        }
                    }
                } catch (_: Exception) {
                    val fallbackLabel = packageName
                    labelCache[packageName] = fallbackLabel
                    if (!isDestroyed && !(activity as? Activity)?.isFinishing!!) {
                        (context as? Activity)?.runOnUiThread {
                            if (holder.itemView.tag.toString() == packageName) {
                                holder.appName?.text = fallbackLabel
                            }
                        }
                    }
                } finally {
                    isDone = true
                }
            }
            
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                if (isDone || isCancelled) return false
                isCancelled = true
                return true
            }
            
            override fun isCancelled(): Boolean = isCancelled
            override fun isDone(): Boolean = isDone
            override fun get(): Boolean? = null
            override fun get(timeout: Long, unit: TimeUnit): Boolean? = null
        }
        
        executor.execute(labelTask)
        pendingLabelTasks[packageName] = labelTask
        
        // Clean up completed tasks periodically
        if (pendingLabelTasks.size > 50) {
            pendingLabelTasks.entries.removeAll { it.value.isDone }
        }
    }

    private fun showAppContextMenu(view: View, packageName: String, appInfo: ResolveInfo) {
        val popupMenu = PopupMenu(activity, view, Gravity.END, 0, R.style.PopupMenuStyle)
        popupMenu.menuInflater.inflate(R.menu.app_context_menu, popupMenu.menu)
        val textColor = ContextCompat.getColor(activity, R.color.text)
        val appName = labelCache[packageName] ?: packageName
        
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
                    Toast.makeText(activity, "Session timer ${if (!isEnabled) "enabled" else "disabled"} for $appName", Toast.LENGTH_SHORT).show()
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
            val textColor = TypographyManager.getConfiguredFontColor(activity) ?: ContextCompat.getColor(activity, R.color.text)
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
                // Apply immediately
                try { for (i in 0 until lv.childCount) fixTextColors(lv.getChildAt(i)) } catch (_: Exception) {}
                // Apply again after layout to catch all items
                lv.post { 
                    try { 
                        for (i in 0 until lv.childCount) { 
                            val itemView = lv.getChildAt(i)
                            if (itemView is TextView) {
                                itemView.setTextColor(textColor)
                            } else if (itemView is ViewGroup) {
                                findTextViewsAndSetColor(itemView, textColor)
                            }
                        }
                    } catch (_: Exception) {} 
                }
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
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = "package:$packageName".toUri() })
    }

    private fun shareApp(packageName: String, appInfo: ResolveInfo) {
        val appName = labelCache[packageName] ?: try { appInfo.loadLabel(activity.packageManager).toString() } catch (_: Exception) { packageName }
        ShareManager(activity).shareApk(packageName, appName)
    }

    private fun uninstallApp(packageName: String) {
        @Suppress("DEPRECATION")
        activity.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply { data = "package:$packageName".toUri() })
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
        val titleView = LayoutInflater.from(activity).inflate(R.layout.dialog_contact_title, null)
        titleView.findViewById<TextView>(R.id.contact_name).text = contactName
        titleView.findViewById<TextView>(R.id.contact_number).text = phoneNumber
        val photoImageView = titleView.findViewById<ImageView>(R.id.contact_photo)
        if (photoUri != null) {
            try {
                activity.contentResolver.openInputStream(photoUri.toUri())?.use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, photoUri)
                    if (drawable != null) photoImageView.setImageDrawable(drawable)
                    else photoImageView.setImageResource(R.drawable.ic_person)
                }
            } catch (_: Exception) { photoImageView.setImageResource(R.drawable.ic_person) }
        } else { photoImageView.setImageResource(R.drawable.ic_person) }
        builder.setCustomTitle(titleView).setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> call(phoneNumber)
                    1 -> activity.contactActionHandler.openWhatsAppChat(contactName)
                    2 -> activity.contactActionHandler.openSMSChat(contactName)
                }
            }.setNegativeButton(activity.getString(R.string.cancel_button), null).show()
    }

    private fun getPhotoUriForContact(contactName: String): String? {
        val cursor = activity.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI), "${ContactsContract.Contacts.DISPLAY_NAME} = ?", arrayOf(contactName), null)
        var photoUri: String? = null
        cursor?.use { if (it.moveToFirst()) { val index = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI); if (index != -1) photoUri = it.getString(index) } }
        return photoUri
    }

    private fun call(phoneNumber: String) {
        activity.startActivity(Intent(Intent.ACTION_DIAL).apply { data = "tel:$phoneNumber".toUri() })
    }

    private fun getPhoneNumberForContact(contactName: String): String {
        val cursor: Cursor? = activity.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?", arrayOf(contactName), null)
        var phoneNumber: String? = null
        cursor?.use { if (it.moveToFirst()) { val index = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); if (index != -1) phoneNumber = it.getString(index) } }
        return phoneNumber ?: "Not found"
    }
}
