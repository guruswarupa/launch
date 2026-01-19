package com.guruswarupa.launch

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class LaunchNotificationListenerService : NotificationListenerService() {
    
    companion object {
        private const val TAG = "LaunchNotificationListener"
        var instance: LaunchNotificationListenerService? = null
            private set
    }
    
    private var isListenerConnected = false
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Service created")
    }
    
    override fun onDestroy() {
        try {
            // Clear instance before calling super to prevent issues
            instance = null
            isListenerConnected = false
            Log.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
        Log.d(TAG, "Listener connected")
    }
    
    override fun onListenerDisconnected() {
        isListenerConnected = false
        Log.d(TAG, "Listener disconnected")
        try {
            super.onListenerDisconnected()
        } catch (e: Exception) {
            // Ignore errors during disconnection - system is managing the lifecycle
            Log.w(TAG, "Error during listener disconnection", e)
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationPosted(sbn)
                // Notify widget to update
                updateWidget()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted", e)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationRemoved(sbn)
                // Notify widget to update
                updateWidget()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationRemoved", e)
        }
    }
    
    private fun updateWidget() {
        // Send broadcast to update widget
        val intent = android.content.Intent("com.guruswarupa.launch.NOTIFICATIONS_UPDATED")
        sendBroadcast(intent)
    }
    
    override fun getActiveNotifications(): Array<StatusBarNotification> {
        return try {
            if (isListenerConnected) {
                super.getActiveNotifications()
            } else {
                emptyArray()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException getting active notifications", e)
            emptyArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active notifications", e)
            emptyArray()
        }
    }
    
    // Helper method to cancel notification - just calls parent method
    // We use this to avoid confusion, but it directly calls the parent's cancelNotification
    fun dismissNotification(pkg: String, tag: String?, id: Int) {
        try {
            if (isListenerConnected) {
                cancelNotification(pkg, tag, id)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException dismissing notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing notification", e)
        }
    }
}
