package com.guruswarupa.launch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

data class NotificationItem(
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val key: String,
    val tag: String?,
    val id: Int
)

class NotificationsWidget(private val rootView: View) {
    private val context: Context = rootView.context
    private val notificationRecyclerView: RecyclerView = rootView.findViewById(R.id.notifications_recycler_view)
    private val emptyState: View = rootView.findViewById(R.id.notifications_empty_state)
    private val permissionPrompt: TextView = rootView.findViewById(R.id.notification_permission_prompt)
    private val countBadge: TextView = rootView.findViewById(R.id.notification_count_badge)
    
    private val notifications: MutableList<NotificationItem> = mutableListOf()
    private val adapter = NotificationAdapter(notifications) { item ->
        // Handle notification click - dismiss notification
        dismissNotification(item)
    }
    
    init {
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
                // Use the three-parameter version: package, tag, id
                service.dismissNotification(item.packageName, item.tag, item.id)
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
            
            activeNotifications.forEach { sbn ->
                val notification = sbn.notification
                val extras = notification.extras
                
                val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                
                if (title.isNotEmpty() || text.isNotEmpty()) {
                    tempNotifications.add(
                        NotificationItem(
                            packageName = sbn.packageName,
                            title = title.ifEmpty { "Notification" },
                            text = text,
                            time = sbn.postTime,
                            key = sbn.key,
                            tag = sbn.tag,
                            id = sbn.id
                        )
                    )
                }
            }
            
            // Sort by time (newest first) and limit to 10 most recent
            notifications.clear()
            notifications.addAll(tempNotifications.sortedByDescending { it.time }.take(10))
            
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
    
    private fun showEmptyState(message: String) {
        emptyState.visibility = View.VISIBLE
        notificationRecyclerView.visibility = View.GONE
        permissionPrompt.text = message
    }
    
    private fun dismissNotification(item: NotificationItem) {
        try {
            val service = LaunchNotificationListenerService.instance
            if (service != null) {
                // Use the three-parameter version: package, tag, id
                service.dismissNotification(item.packageName, item.tag, item.id)
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

class NotificationAdapter(
    private val notifications: List<NotificationItem>,
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {
    
    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.notification_title)
        val textText: TextView = itemView.findViewById(R.id.notification_text)
        val timeText: TextView = itemView.findViewById(R.id.notification_time)
        val appIcon: ImageView = itemView.findViewById(R.id.notification_app_icon)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return NotificationViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = notifications[position]
        
        holder.titleText.text = item.title
        holder.textText.text = item.text
        holder.timeText.text = formatTime(item.time)
        
        // Try to load app icon
        try {
            val pm = holder.itemView.context.packageManager
            val appInfo = pm.getApplicationInfo(item.packageName, 0)
            holder.appIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
        } catch (e: Exception) {
            // Use default icon if app not found
            holder.appIcon.setImageResource(R.drawable.ic_default_app_icon)
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(item)
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
