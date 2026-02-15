package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Manages all permission requests for the launcher
 */
class PermissionManager(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        const val CONTACTS_PERMISSION_REQUEST = 100
        const val REQUEST_CODE_CALL_PHONE = 200
        const val SMS_PERMISSION_REQUEST = 300
        const val VOICE_SEARCH_REQUEST = 500
        const val USAGE_STATS_REQUEST = 600
        const val NOTIFICATION_PERMISSION_REQUEST = 900
        const val DEVICE_ADMIN_REQUEST = 1000
    }
    
    /**
     * Request contacts permission
     */
    fun requestContactsPermission(onGranted: () -> Unit = {}) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS) ||
                !sharedPreferences.getBoolean("contacts_permission_denied", false)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    CONTACTS_PERMISSION_REQUEST
                )
            }
        } else {
            onGranted()
        }
    }
    
    /**
     * Request SMS permission
     */
    fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS) ||
                !sharedPreferences.getBoolean("sms_permission_denied", false)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.SEND_SMS),
                    SMS_PERMISSION_REQUEST
                )
            }
        }
    }
    
    /**
     * Request call phone permission
     */
    @Suppress("unused")
    fun requestCallPhonePermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CODE_CALL_PHONE
            )
        }
    }
    
    /**
     * Request usage stats permission
     */
    fun requestUsageStatsPermission(usageStatsManager: AppUsageStatsManager) {
        if (!usageStatsManager.hasUsageStatsPermission()) {
            if (!sharedPreferences.getBoolean("usage_stats_permission_denied", false)) {
                val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
                    .setTitle("Usage Stats Permission")
                    .setMessage("To show app usage time, please grant usage access permission in the next screen.")
                    .setPositiveButton("Grant") { _, _ ->
                        @Suppress("DEPRECATION")
                        activity.startActivityForResult(
                            usageStatsManager.requestUsageStatsPermission(),
                            USAGE_STATS_REQUEST
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        sharedPreferences.edit { putBoolean("usage_stats_permission_denied", true) }
                    }
                    .show()
                
                fixDialogTextColors(dialog)
            }
        }
    }
    
    /**
     * Checks if Device Admin is enabled for screen off functionality
     */
    fun isDeviceAdminActive(): Boolean {
        val devicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(activity, ScreenOffAdminReceiver::class.java)
        return devicePolicyManager.isAdminActive(componentName)
    }

    /**
     * Request Device Admin permission
     */
    fun requestDeviceAdminPermission() {
        val dialog = AlertDialog.Builder(activity, R.style.CustomDialogTheme)
            .setTitle(activity.getString(R.string.request_device_admin_title))
            .setMessage(activity.getString(R.string.request_device_admin_message))
            .setPositiveButton(activity.getString(R.string.grant_permission)) { _, _ ->
                val componentName = ComponentName(activity, ScreenOffAdminReceiver::class.java)
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.device_admin_description))
                }
                @Suppress("DEPRECATION")
                activity.startActivityForResult(intent, DEVICE_ADMIN_REQUEST)
            }
            .setNegativeButton(activity.getString(R.string.cancel_button), null)
            .show()
        
        fixDialogTextColors(dialog)
    }

    private fun fixDialogTextColors(dialog: AlertDialog) {
        try {
            val textColor = ContextCompat.getColor(activity, R.color.text)
            dialog.findViewById<TextView>(android.R.id.title)?.setTextColor(textColor)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(textColor)
        } catch (_: Exception) {}
    }
    
    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST
                )
            }
        }
    }
    
    /**
     * Request microphone permission for audio recording (used by noise decibel widget)
     */
    @Suppress("unused")
    fun requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                VOICE_SEARCH_REQUEST // Reuse voice search request code since it's the same permission
            )
        }
    }
    
    /**
     * Handle permission result
     */
    fun handlePermissionResult(
        requestCode: Int,
        @Suppress("unused") permissions: Array<String>,
        grantResults: IntArray,
        onContactsGranted: () -> Unit = {},
        onCallPhoneGranted: () -> Unit = {},
        onNotificationGranted: () -> Unit = {}
    ) {
        when (requestCode) {
            CONTACTS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit { putBoolean("contacts_permission_denied", false) }
                    onContactsGranted()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
                        sharedPreferences.edit { putBoolean("contacts_permission_denied", true) }
                    }
                }
            }
            REQUEST_CODE_CALL_PHONE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onCallPhoneGranted()
                }
            }
            SMS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit { putBoolean("sms_permission_denied", false) }
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)) {
                        sharedPreferences.edit { putBoolean("sms_permission_denied", true) }
                    }
                }
            }
            VOICE_SEARCH_REQUEST -> {
                // Permission handled by the caller who requested it
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onNotificationGranted()
                }
            }
        }
    }
}
