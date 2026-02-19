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
    }

    override fun onDestroy() {
        try {
            // Clear instance before calling super to prevent issues
            instance = null
            isListenerConnected = false
        } catch (_: Exception) {
        } finally {
            super.onDestroy()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListenerConnected = true
    }
    
    override fun onListenerDisconnected() {
        isListenerConnected = false
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
        } catch (_: Exception) {
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (isListenerConnected) {
                super.onNotificationRemoved(sbn)
                // Notify widget to update
                updateWidget()
            }
        } catch (_: Exception) {
        }
    }
    
    private fun updateWidget() {
        val intent = android.content.Intent("com.guruswarupa.launch.NOTIFICATIONS_UPDATED")
        intent.setPackage(packageName)
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
        } catch (_: Exception) {
            emptyArray()
        }
    }
    
    // Helper method to cancel notification using package, tag and id
    @Suppress("unused", "DEPRECATION")
    fun dismissNotification(pkg: String, tag: String?, id: Int) {
        try {
            if (isListenerConnected) {
                // Use cancelNotification to dismiss from system
                cancelNotification(pkg, tag, id)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException dismissing notification", e)
        } catch (_: Exception) {
        }
    }
    
    // Reliable method using notification key
    fun dismissNotificationByKey(key: String) {
        try {
            if (isListenerConnected) {
                cancelNotification(key)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException dismissing notification by key", e)
        } catch (_: Exception) {
        }
    }
}
