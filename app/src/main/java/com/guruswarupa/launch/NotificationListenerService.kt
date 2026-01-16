package com.guruswarupa.launch

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LaunchNotificationListenerService : NotificationListenerService() {
    
    companion object {
        var instance: LaunchNotificationListenerService? = null
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        // Notify widget to update
        updateWidget()
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Notify widget to update
        updateWidget()
    }
    
    private fun updateWidget() {
        // Send broadcast to update widget
        val intent = android.content.Intent("com.guruswarupa.launch.NOTIFICATIONS_UPDATED")
        sendBroadcast(intent)
    }
    
    override fun getActiveNotifications(): Array<StatusBarNotification> {
        return try {
            super.getActiveNotifications()
        } catch (e: Exception) {
            emptyArray()
        }
    }
    
    // Helper method to cancel notification - just calls parent method
    // We use this to avoid confusion, but it directly calls the parent's cancelNotification
    fun dismissNotification(pkg: String, tag: String?, id: Int) {
        try {
            cancelNotification(pkg, tag, id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
