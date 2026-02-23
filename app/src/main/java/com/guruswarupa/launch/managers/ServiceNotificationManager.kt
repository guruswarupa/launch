package com.guruswarupa.launch.managers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.guruswarupa.launch.MainActivity
import com.guruswarupa.launch.R

/**
 * Manages a single consolidated notification for all Launch background services.
 */
object ServiceNotificationManager {
    private const val CHANNEL_ID = "launch_services_channel"
    private const val CHANNEL_NAME = "Launch Background Services"
    const val NOTIFICATION_ID = 1000

    private val activeServices = mutableSetOf<String>()

    fun updateServiceStatus(context: Context, serviceName: String, isRunning: Boolean): Notification {
        if (isRunning) {
            activeServices.add(serviceName)
        } else {
            activeServices.remove(serviceName)
        }

        createNotificationChannel(context)
        val notification = createNotification(context)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        return notification
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows the status of running Launch background services"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun createNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (activeServices.isEmpty()) {
            "No background services running"
        } else {
            activeServices.joinToString(", ")
        }

        val title = if (activeServices.size == 1) {
            "Launch Service Active"
        } else if (activeServices.size > 1) {
            "Launch Services Active (${activeServices.size})"
        } else {
            "Launch Services"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }
}
