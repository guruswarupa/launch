package com.guruswarupa.launch.widgets

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R
import com.guruswarupa.launch.services.LaunchNotificationListenerService
import java.text.SimpleDateFormat
import java.util.*

data class NotificationAction(
    val title: String,
    val actionIntent: PendingIntent?,
    val remoteInputs: Array<RemoteInput>?,
    val icon: Drawable?,
    val androidRemoteInputs: Array<android.app.RemoteInput>? = null 
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
    val mergedKeys: List<String> = emptyList(), 
    val isMediaPlayer: Boolean = false 
)

class NotificationsWidget(rootView: View) {
    private val context: Context = rootView.context
    private val notificationRecyclerView: RecyclerView = rootView.findViewById(R.id.notifications_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.notifications_empty_state)
    private val emptyMessage: TextView = rootView.findViewById(R.id.notifications_empty_message)
    private val permissionButton: Button = rootView.findViewById(R.id.request_notification_permission_button)
    private val countBadge: TextView = rootView.findViewById(R.id.notification_count_badge)
    
    private val notifications: MutableList<NotificationItem> = mutableListOf()
    private val adapter: NotificationAdapter
    private val handler = Handler(Looper.getMainLooper())
    
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
                    
                }
            }
        }
    }
    
    init {
        adapter = createAdapter()
        notificationRecyclerView.layoutManager = LinearLayoutManager(context)
        notificationRecyclerView.adapter = adapter
        
        setupSwipeToDismiss()
        
        permissionButton.setOnClickListener {
            openNotificationSettings()
        }
        
        updateNotifications()
    }
    
    private fun setupSwipeToDismiss() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, 
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT 
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false 
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position < notifications.size) {
                    val item = notifications[position]
                    
                    notifications.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    
                    dismissNotificationImmediate(item)
                    
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
                if (item.mergedKeys.isNotEmpty()) {
                    item.mergedKeys.forEach { key ->
                        service.dismissNotificationByKey(key)
                    }
                } else {
                    service.dismissNotificationByKey(item.key)
                }
            }
        } catch (_: Exception) {
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
        
        if (notifications.isEmpty()) {
            showEmptyState("No notifications")
        }
    }
    
    @SuppressLint("NotifyDataSetChanged")
    fun updateNotifications() {
        if (!isNotificationListenerEnabled()) {
            showEmptyState("To see notifications here, please enable Notification Access for Launch in settings.", showPermissionButton = true)
            countBadge.visibility = View.GONE
            return
        }
        
        try {
            val service = LaunchNotificationListenerService.instance
            val activeNotifications = service?.activeNotifications?.toList() ?: emptyList()
            
            val tempNotifications = mutableListOf<NotificationItem>()
            val seenKeys = mutableSetOf<String>()
            val notificationGroups = mutableMapOf<String, MutableList<StatusBarNotification>>()
            
            activeNotifications.forEach { sbn ->
                val notificationKey = sbn.key
                if (seenKeys.contains(notificationKey)) {
                    return@forEach
                }
                seenKeys.add(notificationKey)
                
                val notification = sbn.notification
                val extras = notification.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                
                val groupKey = title.ifEmpty {
                    val group = notification.group
                    group ?: sbn.tag ?: "default"
                }.let { "${sbn.packageName}|$it" }
                
                notificationGroups.getOrPut(groupKey) { mutableListOf() }.add(sbn)
            }
            
            notificationGroups.forEach { (_, groupNotifications) ->
                val sbn = if (groupNotifications.size == 1) {
                    groupNotifications[0]
                } else {
                    val withText = groupNotifications.filter { groupSbn ->
                        val notif = groupSbn.notification
                        val extras = notif.extras
                        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        text.isNotEmpty()
                    }
                    
                    if (withText.isNotEmpty()) {
                        withText.maxByOrNull { groupSbn ->
                            val notif = groupSbn.notification
                            val extras = notif.extras
                            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                            text.length
                        } ?: withText[0]
                    } else {
                        groupNotifications[0]
                    }
                }
                
                val notification = sbn.notification
                val extras = notification.extras
                val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                
                val largeIcon = try {
                    val largeIconObj = notification.getLargeIcon()
                    if (largeIconObj != null) {
                        val remoteContext = try {
                            context.createPackageContext(sbn.packageName, 0)
                        } catch (_: Exception) {
                            context
                        }
                        val drawable = largeIconObj.loadDrawable(remoteContext)
                        if (drawable != null) {
                            convertDrawableToBitmap(drawable)
                        } else {
                            null
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val bitmap = extras?.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap
                        bitmap
                    }
                } catch (_: Throwable) {
                    null
                }
                
                @Suppress("DEPRECATION")
                val bigPicture: Bitmap? = extras?.getParcelable(Notification.EXTRA_PICTURE)
                    ?: extras?.getParcelable(NotificationCompat.EXTRA_PICTURE)
                
                val actions = mutableListOf<NotificationAction>()
                val allActions = mutableSetOf<String>() 
                
                groupNotifications.forEach { groupSbn ->
                    val groupNotification = groupSbn.notification
                    if (groupNotification.actions != null) {
                        groupNotification.actions.forEach { action ->
                        val actionTitle = action.title?.toString()?.trim() ?: ""
                        val actionIntent = action.actionIntent
                        val androidRemoteInputs = action.remoteInputs
                        
                        val remoteInputs: Array<RemoteInput>? = if (androidRemoteInputs != null && androidRemoteInputs.isNotEmpty()) {
                            try {
                                androidRemoteInputs.mapNotNull { androidRemoteInput: android.app.RemoteInput ->
                                    try {
                                        RemoteInput.Builder(androidRemoteInput.resultKey).apply {
                                            setLabel(androidRemoteInput.label)
                                            setChoices(androidRemoteInput.choices)
                                            setAllowFreeFormInput(androidRemoteInput.allowFreeFormInput)
                                        }.build()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }.toTypedArray()
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        
                        val icon: Drawable? = try {
                            val iconObj = action.getIcon()
                            if (iconObj != null) {
                                val remoteContext = try {
                                    context.createPackageContext(groupSbn.packageName, 0)
                                } catch (_: Exception) {
                                    context
                                }
                                iconObj.loadDrawable(remoteContext)
                            } else {
                                val iconResId = @Suppress("DEPRECATION") action.icon
                                if (iconResId != 0) {
                                    val remoteContext = try {
                                        context.createPackageContext(groupSbn.packageName, 0)
                                    } catch (_: Exception) {
                                        context
                                    }
                                    ContextCompat.getDrawable(remoteContext, iconResId)
                                } else null
                            }
                        } catch (_: Throwable) {
                            null
                        }
                        
                            if (actionTitle.isNotEmpty() || icon != null || androidRemoteInputs != null) {
                                val finalTitle = actionTitle.ifEmpty {
                                    when {
                                        androidRemoteInputs != null && androidRemoteInputs.isNotEmpty() -> "Reply"
                                        else -> "Action"
                                    }
                                }
                                
                                if (!allActions.contains(finalTitle.lowercase())) {
                                    allActions.add(finalTitle.lowercase())
                                    actions.add(NotificationAction(finalTitle, actionIntent, remoteInputs, icon, androidRemoteInputs))
                                }
                            }
                        }
                    }
                }
                
                val existingItem = notifications.find { it.key == sbn.key }
                val isExpanded = existingItem?.isExpanded ?: false
                val mergedKeys = groupNotifications.map { it.key }
                
                val isMediaPlayer = bigPicture != null || 
                    (actions.any { action ->
                        val actionTitle = action.title.lowercase()
                        actionTitle.contains("play") || actionTitle.contains("pause") || 
                        actionTitle.contains("next") || actionTitle.contains("previous") ||
                        actionTitle.contains("prev")
                    }) ||
                    (notification.category == Notification.CATEGORY_TRANSPORT)
                
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
            
            val finalNotifications = mutableListOf<NotificationItem>()
            val processedKeys = mutableSetOf<String>()
            
            tempNotifications.forEach { item ->
                if (processedKeys.contains(item.key)) {
                    return@forEach
                }
                
                val similarNotifications = tempNotifications.filter { other ->
                    other.packageName == item.packageName &&
                    other.title == item.title &&
                    other.title.isNotEmpty() &&
                    !processedKeys.contains(other.key)
                }
                
                if (similarNotifications.size > 1) {
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
            
            notifications.clear()
            val uniqueNotifications = finalNotifications
                .distinctBy { it.key } 
                .sortedByDescending { it.time }
                .take(10)
            notifications.addAll(uniqueNotifications)
            
            adapter.notifyDataSetChanged()
            
            val totalCount = activeNotifications.size
            if (totalCount > 0) {
                countBadge.text = if (totalCount > 99) "99+" else totalCount.toString()
                countBadge.visibility = View.VISIBLE
            } else {
                countBadge.visibility = View.GONE
            }
            
            if (notifications.isEmpty()) {
                showEmptyState("No notifications")
            } else {
                emptyState.visibility = View.GONE
                notificationRecyclerView.visibility = View.VISIBLE
            }
        } catch (_: SecurityException) {
            showEmptyState("Notification access not granted", showPermissionButton = true)
            countBadge.visibility = View.GONE
        } catch (_: Exception) {
            showEmptyState("Error loading notifications")
            countBadge.visibility = View.GONE
        }
    }
    
    private fun convertDrawableToBitmap(drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        
        return try {
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.applyCanvas {
                drawable.setBounds(0, 0, width, height)
                drawable.draw(this)
            }
            bitmap
        } catch (_: Throwable) {
            createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }
    
    private fun showEmptyState(message: String, showPermissionButton: Boolean = false) {
        emptyState.visibility = View.VISIBLE
        notificationRecyclerView.visibility = View.GONE
        emptyMessage.text = message
        permissionButton.visibility = if (showPermissionButton) View.VISIBLE else View.GONE
    }
    
    private fun dismissNotification(item: NotificationItem) {
        try {
            val service = LaunchNotificationListenerService.instance
            if (service != null) {
                if (item.mergedKeys.isNotEmpty()) {
                    item.mergedKeys.forEach { key ->
                        service.dismissNotificationByKey(key)
                    }
                } else {
                    service.dismissNotificationByKey(item.key)
                }
                
                handler.postDelayed({
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
        } catch (_: Exception) {
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
        
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(item.packageName, 0)
            holder.appIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
        } catch (_: Exception) {
            holder.appIcon.setImageDrawable(null)
        }
        
        holder.largeIconView?.let { view ->
            if (item.largeIcon != null && !item.largeIcon.isRecycled && !isMediaPlayer) {
                view.setImageBitmap(item.largeIcon)
                view.visibility = View.VISIBLE
            } else {
                view.setImageDrawable(null)
                view.visibility = View.GONE
            }
        }
        
        holder.bigPictureView?.let { view ->
            val bitmap = item.bigPicture ?: (if (isMediaPlayer) item.largeIcon else null)
            if (bitmap != null && !bitmap.isRecycled) {
                view.setImageBitmap(bitmap)
                view.scaleType = ImageView.ScaleType.CENTER_CROP
                view.visibility = View.VISIBLE
                
                if (isMediaPlayer) {
                    view.post {
                        val parent = view.parent as? View
                        val availableWidth = parent?.width ?: 0
                        if (availableWidth > 0) {
                            val horizontalPadding = 128.dpToPx(context) + 24.dpToPx(context) 
                            val squareSize = availableWidth - horizontalPadding
                            val layoutParams = view.layoutParams
                            layoutParams.width = squareSize
                            layoutParams.height = squareSize
                            view.layoutParams = layoutParams
                            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        }
                    }
                }
            } else {
                view.setImageDrawable(null)
                view.visibility = View.GONE
            }
        }
        
        if (isMediaPlayer && item.text.isNotEmpty()) {
            holder.textText.visibility = View.VISIBLE
        } else if (!isMediaPlayer) {
            holder.textText.visibility = if (item.text.isNotEmpty()) View.VISIBLE else View.GONE
        }
        
        holder.actionsContainer?.let { container ->
            container.removeAllViews()
            
            if (item.actions.isNotEmpty()) {
                container.visibility = View.VISIBLE
                
                if (isMediaPlayer) {
                    container.gravity = android.view.Gravity.CENTER
                }
                
                item.actions.forEachIndexed { index, action ->
                    val iconDrawable: Drawable? = action.icon ?: getIconForActionTitle(action.title, context)
                    val buttonSize = if (isMediaPlayer) 40.dpToPx(context) else 48.dpToPx(context)
                    val paddingSize = if (isMediaPlayer) 10.dpToPx(context) else 12.dpToPx(context)
                    
                    val actionButton = if (iconDrawable != null) {
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
                            setPadding(paddingSize, paddingSize, paddingSize, paddingSize)
                            
                            if (action.icon != null) {
                                colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.white), PorterDuff.Mode.SRC_IN)
                            }
                        }
                    } else {
                        TextView(context).apply {
                            text = action.title.ifEmpty { "Action" }
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
                            holder.replyContainer?.visibility = View.VISIBLE
                            holder.replyEditText?.requestFocus()

                            holder.replySendButton?.setOnClickListener {
                                sendReply(action, holder.replyEditText?.text.toString(), holder)
                            }

                            holder.replyEditText?.setOnEditorActionListener { _, actionId, _ ->
                                if (actionId == EditorInfo.IME_ACTION_SEND) {
                                    sendReply(action, holder.replyEditText.text.toString(), holder)
                                    true
                                } else false
                            }
                        } else {
                            try {
                                action.actionIntent?.send()
                            } catch (_: Exception) {
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

            holder.replyContainer?.visibility = View.GONE

            holder.itemView.setOnClickListener {
                try {
                    val pm = context.packageManager
                    val intent = pm.getLaunchIntentForPackage(item.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open app", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun sendReply(action: NotificationAction, replyText: String, holder: NotificationViewHolder) {
        if (replyText.isEmpty()) {
            Toast.makeText(context, "Please enter a reply", Toast.LENGTH_SHORT).show()
            return
        }

        try {
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
            } else {
                val androidRemoteInputs = action.androidRemoteInputs
                if (androidRemoteInputs != null && androidRemoteInputs.isNotEmpty()) {
                    val results = Bundle()
                    androidRemoteInputs.forEach { androidRemoteInput ->
                        results.putCharSequence(androidRemoteInput.resultKey, replyText)
                    }

                    val intent = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    android.app.RemoteInput.addResultsToIntent(androidRemoteInputs, intent, results)

                    action.actionIntent?.send(context, 0, intent)
                    Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Could not send reply", Toast.LENGTH_SHORT).show()
        }
        holder.replyEditText?.text?.clear()
        holder.replyContainer?.visibility = View.GONE
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


private fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}


private fun getIconForActionTitle(title: String, context: Context): Drawable? {
    val lowerTitle = title.lowercase().trim()
    val iconResId = when {
        lowerTitle == "pause" || lowerTitle.contains("pause") -> R.drawable.ic_pause
        lowerTitle == "play" || (lowerTitle.contains("play") && !lowerTitle.contains("pause")) -> R.drawable.ic_play
        lowerTitle == "next" || lowerTitle.contains("next") || lowerTitle.contains("skip forward") || lowerTitle.contains("skip") -> R.drawable.ic_next
        lowerTitle == "previous" || lowerTitle.contains("prev") || lowerTitle.contains("previous") || lowerTitle.contains("prev") || lowerTitle.contains("skip back") || lowerTitle.contains("back") -> R.drawable.ic_previous
        lowerTitle == "dislike" || lowerTitle.contains("dislike") || lowerTitle.contains("thumbs down") -> R.drawable.ic_thumbs_down
        lowerTitle == "like" || lowerTitle.contains("like") || lowerTitle.contains("thumbs up") -> R.drawable.ic_thumbs_up
        lowerTitle.contains("favorite") || lowerTitle.contains("favourite") || lowerTitle.contains("love") -> R.drawable.ic_heart
        lowerTitle.contains("reply") || lowerTitle.contains("answer") -> R.drawable.ic_message
        lowerTitle.contains("mark as read") || lowerTitle.contains("read") -> android.R.drawable.ic_menu_view
        lowerTitle.contains("mute") || lowerTitle.contains("silence") -> android.R.drawable.ic_lock_silent_mode
        lowerTitle.contains("archive") -> R.drawable.ic_archive
        lowerTitle.contains("delete") || lowerTitle.contains("remove") || lowerTitle.contains("dismiss") -> R.drawable.ic_delete
        else -> null
    }
    
    return iconResId?.let { 
        try {
            val drawable = ContextCompat.getDrawable(context, it)
            drawable?.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.white), PorterDuff.Mode.SRC_IN)
            drawable
        } catch (_: Exception) {
            null
        }
    }
}
