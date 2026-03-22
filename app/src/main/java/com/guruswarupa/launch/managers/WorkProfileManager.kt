package com.guruswarupa.launch.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.guruswarupa.launch.receivers.WorkProfileProvisioningReceiver

class WorkProfileManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        private const val TAG = "WorkProfileManager"
        private const val WORK_PROFILE_ENABLED_KEY = "work_profile_enabled"
        private const val WORK_PROFILE_APPS_KEY = "work_profile_apps"
        const val REQUEST_CODE_CREATE_WORK_PROFILE = 2000
        const val WORK_PROFILE_WORKSPACE_ID = "work_profile_workspace"
    }
    
    private val provisioningReceiverComponent = ComponentName(
        context, 
        WorkProfileProvisioningReceiver::class.java
    )
    
    fun isWorkProfileSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    }

    fun hasActualWorkProfile(): Boolean {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val myUserHandle = Process.myUserHandle()
            launcherApps.profiles.any { it != myUserHandle }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for actual work profile", e)
            false
        }
    }
    
    fun isWorkProfileEnabled(): Boolean {
        return sharedPreferences.getBoolean(WORK_PROFILE_ENABLED_KEY, false)
    }
    
    fun getWorkProfileApps(): Set<String> {
        val appsJson = sharedPreferences.getString(WORK_PROFILE_APPS_KEY, "[]") ?: "[]"
        return try {
            org.json.JSONArray(appsJson).let { jsonArray ->
                (0 until jsonArray.length()).map { jsonArray.getString(it) }.toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    fun setWorkProfileApps(apps: Set<String>) {
        val jsonArray = org.json.JSONArray()
        apps.forEach { jsonArray.put(it) }
        sharedPreferences.edit { putString(WORK_PROFILE_APPS_KEY, jsonArray.toString()) }
    }
    
    fun addAppToWorkProfile(packageName: String) {
        val currentApps = getWorkProfileApps().toMutableSet()
        currentApps.add(packageName)
        setWorkProfileApps(currentApps)
    }
    
    fun removeAppFromWorkProfile(packageName: String) {
        val currentApps = getWorkProfileApps().toMutableSet()
        currentApps.remove(packageName)
        setWorkProfileApps(currentApps)
    }
    
    fun isAppInWorkProfile(packageName: String): Boolean {
        return getWorkProfileApps().contains(packageName)
    }
    
    @RequiresApi(Build.VERSION_CODES.P)
    fun createWorkProfile(activity: androidx.activity.ComponentActivity) {
        if (!isWorkProfileSupported()) {
            Log.e(TAG, "Work profile is not supported on this device")
            return
        }
        
        try {
            val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, provisioningReceiverComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, "")
            }
            
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_CODE_CREATE_WORK_PROFILE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start work profile provisioning", e)
        }
    }
    
    fun setWorkProfileEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(WORK_PROFILE_ENABLED_KEY, enabled) }
    }
    
    fun deleteWorkProfile() {
        setWorkProfileEnabled(false)
        setWorkProfileApps(emptySet())
    }
}
