package com.guruswarupa.launch

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class NotificationAction(
    val title: String,
    val actionIntent: PendingIntent?,
    val remoteInputs: Array<RemoteInput>?,
    val icon: Drawable?,
    val androidRemoteInputs: Array<android.app.RemoteInput>? = null // Store original for conversion
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as NotificationAction
        
        if (title != other.title) return false
        if (actionIntent != other.actionIntent) return false
        if (remoteInputs != null) {
            if (other.remoteInputs == null) return false
            if (!remoteInputs.contentEquals(other.remoteInputs)) return false
        } else if (other.remoteInputs != null) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (actionIntent?.hashCode() ?: 0)
        result = 31 * result + (remoteInputs?.contentHashCode() ?: 0)
        return result
    }
}

data class NotificationItem(
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val key: String,
    val tag: String?,
    val id: Int,
    val largeIcon: Bitmap? = null,
    val bigPicture: Bitmap? = null,
    val actions: List<NotificationAction> = emptyList(),
    val isExpanded: Boolean = false,
    val mergedKeys: List<String> = emptyList(), // Store keys of merged notifications for dismissal
    val isMediaPlayer: Boolean = false // Indicates if this is a media player notification
)

class NotificationsWidget(private val rootView: View) {
    private val context: Context = rootView.context
    private val notificationRecyclerView: RecyclerView = rootView.findViewById(R.id.notifications_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.notifications_empty_state)
    private val permissionPrompt: TextView = rootView.findViewById(R.id.notification_permission_prompt)
    private val countBadge: TextView = rootView.findViewById(R.id.notification_count_badge)
    
    private val notifications: MutableList<NotificationItem> = mutableListOf()
    private lateinit var adapter: NotificationAdapter
    
    private fun createAdapter(): NotificationAdapter {
        return NotificationAdapter(notifications, context) { item: NotificationItem, action: ActionType ->
            when (action) {
                ActionType.TOGGLE_EXPAND -> {
                    val index: Int = notifications.indexOfFirst { it.key == item.key }
                    if (index != RecyclerView.NO_POSITION && index < notifications.size) {
                        notifications[index] = item.copy(isExpanded = !item.isExpanded)
                        adapter.notifyItemChanged(index)
                    }
                }
                ActionType.DISMISS -> {
                    dismissNotification(item)
                }
                ActionType.REPLY -> {
                    // Reply action is handled in adapter
                }
            }
        }
    }
    
    init {
        adapter = createAdapter()
        notificationRecyclerView.layoutManager = LinearLayoutManager(context)
        notificationRecyclerView.adapter = adapter
        
        // Setup swipe to dismiss
        setupSwipeToDismiss()
        
        permissionPrompt.setOnClickListener {
            openNotificationSettings()
        }
        
        // Initial update
        updateNotifications()
    }
    
    private fun setupSwipeToDismiss() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, // No drag and drop
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // Allow swipe left and right
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // Not implemented
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION && position < notifications.size) {
                    val item = notifications[position]
                    // Remove from list immediately for instant feedback
                    notifications.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    // Dismiss the actual notification
                    dismissNotificationImmediate(item)
                    // Update badge count
                    updateBadgeCount()
                }
            }
        })
        
        itemTouchHelper.attachToRecyclerView(notificationRecyclerView)
    }
    
    private fun dismissNotificationImmediate(item: NotificationItem) {
        try {
            val service = LaunchNotificationListenerService.instance
            if (service != null) {
                // Dismiss all merged notifications if this is a merged notification
                if (item.mergedKeys.isNotEmpty()) {
                    val activeNotifications = service.getActiveNotifications()
                    item.mergedKeys.forEach { key ->
                        val sbn = activeNotifications.find { it.key == key }
                        if (sbn != null) {
                            try {
                                service.dismissNotification(sbn.packageName, sbn.tag, sbn.id)
                            } catch (e: Exception) {
                                // Continue with other notifications even if one fails
                            }
                        }
                    }
                } else {
                    // Dismiss single notification
                    service.dismissNotification(item.packageName, item.tag, item.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateBadgeCount() {
        val totalCount = notifications.size
        if (totalCount > 0) {
            countBadge.text = if (totalCount > 99) "99+" else totalCount.toString()
            countBadge.visibility = View.VISIBLE
        } else {
            countBadge.visibility = View.GONE
        }
        
        // Show/hide empty state
        if (notifications.isEmpty()) {
            showEmptyState("No notifications")
        }
    }
    
    fun updateNotifications() {
        if (!isNotificationListenerEnabled()) {
            showEmptyState("Enable notification access in settings")
            countBadge.visibility = View.GONE
            return
        }
        
        try {
            val service = LaunchNotificationListenerService.instance
            val activeNotifications = service?.getActiveNotifications()?.toList() ?: emptyList()
            
            val tempNotifications = mutableListOf<NotificationItem>()
            val seenKeys = mutableSetOf<String>()
            // Map to group notifications by package and title for merging
            val notificationGroups = mutableMapOf<String, MutableList<StatusBarNotification>>()
            
            // First pass: group notifications by package and title
            // For messaging apps like WhatsApp, also check notification group key and tag
            activeNotifications.forEach { sbn ->
                val notificationKey = sbn.key
                if (seenKeys.contains(notificationKey)) {
                    return@forEach
                }
                seenKeys.add(notificationKey)
                
                val notification = sbn.notification
                val extras = notification.extras
                val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                
                // For messaging apps, use a more flexible grouping:
                // 1. Group by package + title (same conversation)
                // 2. Also group by notification group key if available (Android notification groups)
                // 3. For same package with same or empty title, group them together
                val groupKey = if (title.isNotEmpty()) {
                    "${sbn.packageName}|${title}"
                } else {
                    // If title is empty, try to use notification group or tag
                    val group = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        notification.group
                    } else {
                        null
                    }
                    "${sbn.packageName}|${group ?: sbn.tag ?: "default"}"
                }
                notificationGroups.getOrPut(groupKey) { mutableListOf() }.add(sbn)
            }
            
            // Second pass: process grouped notifications and merge them
            notificationGroups.forEach { (groupKey, groupNotifications) ->
                // For multiple notifications, prioritize the one with message content
                // If multiple have content, prefer the one with the most text
                val sbn = if (groupNotifications.size == 1) {
                    // Single notification - process normally
                    groupNotifications[0]
                } else {
                    // Multiple notifications - find the best one to display
                    // Priority: 1) Has text content, 2) Has actions, 3) First one
                    val withText = groupNotifications.filter { sbn ->
                        val notif = sbn.notification
                        val extras = notif.extras
                        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                        text.isNotEmpty()
                    }
                    
                    if (withText.isNotEmpty()) {
                        // If multiple have text, pick the one with longest text (most complete message)
                        withText.maxByOrNull { sbn ->
                            val notif = sbn.notification
                            val extras = notif.extras
                            val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                            text.length
                        } ?: withText[0]
                    } else {
                        // No text, pick the first one
                        groupNotifications[0]
                    }
                }
                
                val notification = sbn.notification
                val extras = notification.extras
                
                val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                
                // Extract large icon
                val largeIcon = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val largeIconObj = notification.getLargeIcon()
                        if (largeIconObj != null) {
                            val drawable = largeIconObj.loadDrawable(context)
                            if (drawable != null) {
                                convertDrawableToBitmap(drawable)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } else {
                        // For older APIs, try to get from extras
                        extras?.getParcelable<android.graphics.Bitmap>(Notification.EXTRA_LARGE_ICON)
                    }
                } catch (e: Exception) {
                    null
                }
                
                // Extract big picture
                val bigPicture = extras?.getParcelable<android.graphics.Bitmap>(Notification.EXTRA_PICTURE)
                    ?: extras?.getParcelable<android.graphics.Bitmap>(NotificationCompat.EXTRA_PICTURE)
                
                // Extract actions - merge from all notifications in group if multiple
                val actions = mutableListOf<NotificationAction>()
                val allActions = mutableSetOf<String>() // Track action titles to avoid duplicates
                
                // Collect actions from all notifications in the group
                groupNotifications.forEach { groupSbn ->
                    val groupNotification = groupSbn.notification
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && groupNotification.actions != null) {
                        groupNotification.actions.forEach { action ->
                        val actionTitle = action.title?.toString()?.trim() ?: ""
                        val actionIntent = action.actionIntent
                        val androidRemoteInputs = action.remoteInputs
                        
                        // Convert android.app.RemoteInput to androidx.core.app.RemoteInput
                        val remoteInputs: Array<RemoteInput>? = if (androidRemoteInputs != null && androidRemoteInputs.isNotEmpty()) {
                            try {
                                androidRemoteInputs.mapNotNull { androidRemoteInput: android.app.RemoteInput ->
                                    try {
                                        RemoteInput.Builder(androidRemoteInput.resultKey).apply {
                                            setLabel(androidRemoteInput.label)
                                            setChoices(androidRemoteInput.choices)
                                            setAllowFreeFormInput(androidRemoteInput.allowFreeFormInput)
                                        }.build()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }.toTypedArray()
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        
                        val icon: Drawable? = try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                // On API 23+, icon property returns Icon?
                                try {
                                    @Suppress("DEPRECATION")
                                    val iconObj: Any? = action.icon
                                    if (iconObj is Icon) {
                                        iconObj.loadDrawable(context)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                // On older APIs, icon is an Int resource ID (deprecated)
                                @Suppress("DEPRECATION")
                                val iconResId: Int = try {
                                    action.icon as? Int ?: 0
                                } catch (e: Exception) {
                                    0
                                }
                                if (iconResId != 0) {
                                    try {
                                        ContextCompat.getDrawable(context, iconResId)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            null
                        }
                        
                            // Add action even if title is empty - we'll use icon or default icon
                            if (actionTitle.isNotEmpty() || icon != null || androidRemoteInputs != null) {
                                val finalTitle = if (actionTitle.isEmpty()) {
                                    // Try to infer title from remote input or icon
                                    when {
                                        androidRemoteInputs != null && androidRemoteInputs.isNotEmpty() -> "Reply"
                                        else -> "Action"
                                    }
                                } else {
                                    actionTitle
                                }
                                
                                // Only add if we haven't seen this action title before (avoid duplicates)
                                if (!allActions.contains(finalTitle.lowercase())) {
                                    allActions.add(finalTitle.lowercase())
                                    actions.add(NotificationAction(finalTitle, actionIntent, remoteInputs, icon, androidRemoteInputs))
                                }
                            }
                        }
                    }
                }
                
                // Preserve expanded state if notification already exists
                val existingItem = notifications.find { it.key == sbn.key }
                val isExpanded = existingItem?.isExpanded ?: false
                
                // Collect all keys from merged notifications for dismissal
                val mergedKeys = groupNotifications.map { it.key }
                
                // Detect if this is a media player notification
                // Media players typically have: big picture, play/pause/next/previous actions, or TRANSPORT category
                val isMediaPlayer = bigPicture != null || 
                    (actions.any { action ->
                        val actionTitle = action.title.lowercase()
                        actionTitle.contains("play") || actionTitle.contains("pause") || 
                        actionTitle.contains("next") || actionTitle.contains("previous") ||
                        actionTitle.contains("prev")
                    }) ||
                    (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP && 
                     notification.category == Notification.CATEGORY_TRANSPORT)
                
                if (title.isNotEmpty() || text.isNotEmpty()) {
                    tempNotifications.add(
                        NotificationItem(
                            packageName = sbn.packageName,
                            title = title.ifEmpty { "Notification" },
                            text = text,
                            time = sbn.postTime,
                            key = sbn.key,
                            tag = sbn.tag,
                            id = sbn.id,
                            largeIcon = largeIcon,
                            bigPicture = bigPicture,
                            actions = actions,
                            isExpanded = isExpanded,
                            mergedKeys = mergedKeys,
                            isMediaPlayer = isMediaPlayer
                        )
                    )
                }
            }
            
            // Additional pass: merge notifications from messaging apps that have same package and title
            // This handles cases like WhatsApp where reply notifications might have same title but different keys
            val finalNotifications = mutableListOf<NotificationItem>()
            val processedKeys = mutableSetOf<String>()
            
            tempNotifications.forEach { item ->
                if (processedKeys.contains(item.key)) {
                    return@forEach
                }
                
                // Find other notifications from same package with same title (likely same conversation)
                val similarNotifications = tempNotifications.filter { other ->
                    other.packageName == item.packageName &&
                    other.title == item.title &&
                    other.title.isNotEmpty() &&
                    !processedKeys.contains(other.key)
                }
                
                if (similarNotifications.size > 1) {
                    // Merge them - use the one with most text, merge all actions
                    val merged = similarNotifications.maxByOrNull { it.text.length } ?: similarNotifications[0]
                    val allMergedActions = similarNotifications.flatMap { it.actions }.distinctBy { 
                        "${it.title}|${it.actionIntent?.hashCode()}"
                    }
                    val allMergedKeys = similarNotifications.flatMap { listOf(it.key) + it.mergedKeys }
                    
                    finalNotifications.add(
                        merged.copy(
                            actions = allMergedActions,
                            mergedKeys = allMergedKeys.distinct()
                        )
                    )
                    processedKeys.addAll(similarNotifications.map { it.key })
                } else {
                    finalNotifications.add(item)
                    processedKeys.add(item.key)
                }
            }
            
            // Sort by time (newest first) and limit to 10 most recent
            notifications.clear()
            val uniqueNotifications = finalNotifications
                .distinctBy { it.key } // Remove duplicates by key
                .sortedByDescending { it.time }
                .take(10)
            notifications.addAll(uniqueNotifications)
            
            adapter.notifyDataSetChanged()
            
            // Update badge
            val totalCount = activeNotifications.size
            if (totalCount > 0) {
                countBadge.text = if (totalCount > 99) "99+" else totalCount.toString()
                countBadge.visibility = View.VISIBLE
            } else {
                countBadge.visibility = View.GONE
            }
            
            // Show/hide empty state
            if (notifications.isEmpty()) {
                showEmptyState("No notifications")
            } else {
                emptyState.visibility = View.GONE
                notificationRecyclerView.visibility = View.VISIBLE
            }
        } catch (e: SecurityException) {
            showEmptyState("Notification access not granted")
            countBadge.visibility = View.GONE
        } catch (e: Exception) {
            showEmptyState("Error loading notifications")
            countBadge.visibility = View.GONE
        }
    }
    
    private fun convertDrawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
    
    private fun showEmptyState(message: String) {
        emptyState.visibility = View.VISIBLE
        notificationRecyclerView.visibility = View.GONE
        permissionPrompt.text = message
    }
    
    private fun dismissNotification(item: NotificationItem) {
        try {
            val service = LaunchNotificationListenerService.instance
            if (service != null) {
                // Dismiss all merged notifications if this is a merged notification
                if (item.mergedKeys.isNotEmpty()) {
                    // Get all active notifications to find the ones to dismiss
                    val activeNotifications = service.getActiveNotifications()
                    item.mergedKeys.forEach { key ->
                        val sbn = activeNotifications.find { it.key == key }
                        if (sbn != null) {
                            try {
                                service.dismissNotification(sbn.packageName, sbn.tag, sbn.id)
                            } catch (e: Exception) {
                                // Continue with other notifications even if one fails
                            }
                        }
                    }
                } else {
                    // Dismiss single notification
                    service.dismissNotification(item.packageName, item.tag, item.id)
                }
                // Small delay to let the system process the cancellation
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateNotifications()
                }, 200)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not dismiss notification", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        
        if (flat.isNullOrEmpty()) {
            return false
        }
        
        val names = flat.split(":")
        return names.any { name ->
            val componentName = ComponentName.unflattenFromString(name)
            componentName?.packageName == packageName
        }
    }
    
    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Enable Launch in the list", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }
}

enum class ActionType {
    TOGGLE_EXPAND,
    DISMISS,
    REPLY
}

class NotificationAdapter(
    private val notifications: MutableList<NotificationItem>,
    private val context: Context,
    private val onAction: (NotificationItem, ActionType) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
    
    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.notification_title)
        val textText: TextView = itemView.findViewById(R.id.notification_text)
        val timeText: TextView = itemView.findViewById(R.id.notification_time)
        val appIcon: ImageView = itemView.findViewById(R.id.notification_app_icon)
        val largeIconView: ImageView? = itemView.findViewById(R.id.notification_large_icon)
        val bigPictureView: ImageView? = itemView.findViewById(R.id.notification_big_picture)
        val actionsContainer: LinearLayout? = itemView.findViewById(R.id.notification_actions_container)
        val replyContainer: LinearLayout? = itemView.findViewById(R.id.notification_reply_container)
        val replyEditText: EditText? = itemView.findViewById(R.id.notification_reply_input)
        val replySendButton: TextView? = itemView.findViewById(R.id.notification_reply_send)
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (notifications[position].isMediaPlayer) 1 else 0
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val layoutId = if (viewType == 1) {
            R.layout.notification_item_media
        } else {
            R.layout.notification_item
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return NotificationViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = notifications[position]
        val isMediaPlayer = item.isMediaPlayer
        
        holder.titleText.text = item.title
        holder.textText.text = item.text
        holder.timeText.text = formatTime(item.time)
        
        // Try to load app icon - smaller for media players
        try {
            val pm = holder.itemView.context.packageManager
            val appInfo = pm.getApplicationInfo(item.packageName, 0)
            holder.appIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
            // Make app icon smaller for media players (already 24dp in media layout)
        } catch (e: Exception) {
            // Use default icon if app not found
            holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
        }
        
        // Handle large icon (not used in media layout)
        holder.largeIconView?.let { view ->
            if (item.largeIcon != null && !isMediaPlayer) {
                view.setImageBitmap(item.largeIcon)
                view.visibility = View.VISIBLE
            } else {
                view.visibility = View.GONE
            }
        }
        
        // Handle big picture - always show for media players, conditional for others
        holder.bigPictureView?.let { view ->
            if (item.bigPicture != null || (isMediaPlayer && item.largeIcon != null)) {
                val bitmap = item.bigPicture ?: item.largeIcon
                if (bitmap != null) {
                    // Use high-quality scaling to avoid pixelation
                    view.setImageBitmap(bitmap)
                    view.scaleType = ImageView.ScaleType.CENTER_CROP
                    view.visibility = View.VISIBLE
                    // Make it square for media players with proper scaling and padding
                    if (isMediaPlayer) {
                        view.post {
                            val parent = view.parent as? View
                            val availableWidth = parent?.width ?: 0
                            if (availableWidth > 0) {
                                // Account for margins (64dp on each side = 128dp total) and container padding (12dp on each side = 24dp total)
                                val horizontalPadding = 128.dpToPx(context) + 24.dpToPx(context) // 64dp margin * 2 + 12dp padding * 2
                                val squareSize = availableWidth - horizontalPadding
                                val layoutParams = view.layoutParams
                                layoutParams.width = squareSize
                                layoutParams.height = squareSize
                                view.layoutParams = layoutParams
                                // Force high-quality rendering
                                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            }
                        }
                    }
                } else {
                    view.visibility = View.GONE
                }
            } else if (isMediaPlayer) {
                view.visibility = View.GONE
            } else {
                view.visibility = View.GONE
            }
        }
        
        // Show text for media players (artist name)
        if (isMediaPlayer && item.text.isNotEmpty()) {
            holder.textText.visibility = View.VISIBLE
        } else if (!isMediaPlayer) {
            holder.textText.visibility = if (item.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
        
        // Handle actions - always show them, no expansion needed
        holder.actionsContainer?.let { container ->
            container.removeAllViews()
            // Always show container if there are actions, even if we can't get icons
            if (item.actions.isNotEmpty()) {
                container.visibility = View.VISIBLE
                // For media players, center the actions
                if (isMediaPlayer) {
                    container.gravity = android.view.Gravity.CENTER
                }
                
                item.actions.forEachIndexed { index, action ->
                    // Get icon - use action icon if available, otherwise map from title
                    val iconDrawable: Drawable? = action.icon ?: getIconForActionTitle(action.title, context)
                    
                    // Smaller buttons for media players
                    val buttonSize = if (isMediaPlayer) 40.dpToPx(context) else 48.dpToPx(context)
                    val padding = if (isMediaPlayer) 10.dpToPx(context) else 12.dpToPx(context)
                    
                    // Always create button, even if no icon - we'll show a default or text
                    val actionButton = if (iconDrawable != null) {
                        // Icon-only button
                        ImageView(context).apply {
                            setImageDrawable(iconDrawable)
                            background = ContextCompat.getDrawable(context, R.drawable.notification_action_button)
                            layoutParams = LinearLayout.LayoutParams(
                                buttonSize,
                                buttonSize
                            ).apply {
                                if (index > 0) {
                                    marginStart = if (isMediaPlayer) 6.dpToPx(context) else 8.dpToPx(context)
                                }
                            }
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                            setPadding(padding, padding, padding, padding)
                            // Use white/translucent theme instead of nord7
                            if (action.icon != null) {
                                setColorFilter(ContextCompat.getColor(context, R.color.white))
                            }
                        }
                    } else {
                        // Fallback: text button if no icon available
                        TextView(context).apply {
                            text = action.title.takeIf { it.isNotEmpty() } ?: "Action"
                            setTextColor(ContextCompat.getColor(context, R.color.white))
                            textSize = 12f
                            gravity = android.view.Gravity.CENTER
                            background = ContextCompat.getDrawable(context, R.drawable.notification_action_button)
                            setPadding(16.dpToPx(context), 12.dpToPx(context), 16.dpToPx(context), 12.dpToPx(context))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                48.dpToPx(context)
                            ).apply {
                                if (index > 0) {
                                    marginStart = 8.dpToPx(context)
                                }
                            }
                            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                        }
                    }
                    
                    actionButton.setOnClickListener {
                        if (action.remoteInputs != null && action.remoteInputs.isNotEmpty()) {
                            // Show reply input
                            holder.replyContainer?.visibility = View.VISIBLE
                            holder.replyEditText?.requestFocus()
                            
                            // Handle send button click
                            holder.replySendButton?.setOnClickListener {
                                sendReply(item, action, holder.replyEditText?.text?.toString() ?: "")
                            }
                            
                            // Handle keyboard send action
                            holder.replyEditText?.setOnEditorActionListener { _, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_SEND) {
                                    sendReply(item, action, holder.replyEditText?.text?.toString() ?: "")
                                    true
                                } else {
                                    false
                                }
                            }
                        } else {
                            // Execute action intent
                            try {
                                action.actionIntent?.send()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not execute action", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    
                    container.addView(actionButton)
                }
            } else {
                container.visibility = View.GONE
            }
        }
        
        // Handle reply container
        holder.replyContainer?.visibility = View.GONE
        
        // Click to open app (actions are always visible now)
        holder.itemView.setOnClickListener {
            try {
                val pm = context.packageManager
                val intent = pm.getLaunchIntentForPackage(item.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open app", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sendReply(item: NotificationItem, action: NotificationAction, replyText: String) {
        if (replyText.isEmpty()) {
            Toast.makeText(context, "Please enter a reply", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Use androidx RemoteInputs if available, otherwise use android.app version
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                val results = Bundle()
                remoteInputs.forEach { remoteInput ->
                    results.putCharSequence(remoteInput.resultKey, replyText)
                }
                
                val intent = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                RemoteInput.addResultsToIntent(remoteInputs, intent, results)
                
                action.actionIntent?.send(context, 0, intent)
                Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show()
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                // Fallback to android.app.RemoteInput if androidx version not available
                val androidRemoteInputs = action.androidRemoteInputs
                if (androidRemoteInputs != null && androidRemoteInputs.isNotEmpty()) {
                    val results = Bundle()
                    androidRemoteInputs.forEach { androidRemoteInput ->
                        results.putCharSequence(androidRemoteInput.resultKey, replyText)
                    }
                    
                    val intent = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        android.app.RemoteInput.addResultsToIntent(androidRemoteInputs, intent, results)
                    }
                    
                    action.actionIntent?.send(context, 0, intent)
                    Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not send reply", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun getItemCount() = notifications.size
    
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}

// Extension function to convert dp to pixels
private fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

// Map action titles to icons for music players and common actions
private fun getIconForActionTitle(title: String, context: Context): Drawable? {
    val lowerTitle = title.lowercase().trim()
    val iconResId = when {
        // Music player controls - check pause first to avoid matching "play" in "pause"
        lowerTitle == "pause" || lowerTitle.contains("pause") -> R.drawable.ic_pause
        lowerTitle == "play" || (lowerTitle.contains("play") && !lowerTitle.contains("pause")) -> R.drawable.ic_play
        lowerTitle == "next" || lowerTitle.contains("next") || lowerTitle.contains("skip forward") || lowerTitle.contains("skip") -> R.drawable.ic_next
        lowerTitle == "previous" || lowerTitle == "prev" || lowerTitle.contains("previous") || lowerTitle.contains("prev") || lowerTitle.contains("skip back") || lowerTitle.contains("back") -> R.drawable.ic_previous
        // Like/Dislike actions - check dislike first to avoid matching "like" in "dislike"
        lowerTitle == "dislike" || lowerTitle.contains("dislike") || lowerTitle.contains("thumbs down") -> R.drawable.ic_thumbs_down
        lowerTitle == "like" || lowerTitle.contains("like") || lowerTitle.contains("thumbs up") -> R.drawable.ic_thumbs_up
        lowerTitle.contains("favorite") || lowerTitle.contains("favourite") || lowerTitle.contains("love") -> R.drawable.ic_heart
        // Messaging actions
        lowerTitle.contains("reply") || lowerTitle.contains("answer") -> R.drawable.ic_message
        // Notification actions
        lowerTitle.contains("mark as read") || lowerTitle.contains("read") -> android.R.drawable.ic_menu_view
        lowerTitle.contains("mute") || lowerTitle.contains("silence") -> android.R.drawable.ic_lock_silent_mode
        lowerTitle.contains("archive") -> R.drawable.ic_archive
        lowerTitle.contains("delete") || lowerTitle.contains("remove") || lowerTitle.contains("dismiss") -> R.drawable.ic_delete
        else -> null
    }
    
    return iconResId?.let { 
        try {
            val drawable = ContextCompat.getDrawable(context, it)
            // Apply color filter to match the theme
            drawable?.setColorFilter(
                ContextCompat.getColor(context, R.color.white),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            drawable
        } catch (e: Exception) {
            null
        }
    }
}
