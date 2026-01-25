package com.guruswarupa.launch

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
        const val LOCATION_PERMISSION_REQUEST = 700
        const val NOTIFICATION_PERMISSION_REQUEST = 900
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
                AlertDialog.Builder(activity, R.style.CustomDialogTheme)
                    .setTitle("Usage Stats Permission")
                    .setMessage("To show app usage time, please grant usage access permission in the next screen.")
                    .setPositiveButton("Grant") { _, _ ->
                        activity.startActivityForResult(
                            usageStatsManager.requestUsageStatsPermission(),
                            USAGE_STATS_REQUEST
                        )
                    }
                    .setNegativeButton("Skip") { _, _ ->
                        sharedPreferences.edit().putBoolean("usage_stats_permission_denied", true).apply()
                    }
                    .show()
            }
        }
    }
    
    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
     * Handle permission result
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        onContactsGranted: () -> Unit = {},
        onCallPhoneGranted: () -> Unit = {},
        onNotificationGranted: () -> Unit = {}
    ) {
        when (requestCode) {
            CONTACTS_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sharedPreferences.edit().putBoolean("contacts_permission_denied", false).apply()
                    onContactsGranted()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CONTACTS)) {
                        sharedPreferences.edit().putBoolean("contacts_permission_denied", true).apply()
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
                    sharedPreferences.edit().putBoolean("sms_permission_denied", false).apply()
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.SEND_SMS)) {
                        sharedPreferences.edit().putBoolean("sms_permission_denied", true).apply()
                    }
                }
            }
            VOICE_SEARCH_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, caller should handle voice search
                }
            }
            NOTIFICATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onNotificationGranted()
                }
            }
        }
    }
}
