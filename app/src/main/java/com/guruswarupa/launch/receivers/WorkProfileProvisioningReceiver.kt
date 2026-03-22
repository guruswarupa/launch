package com.guruswarupa.launch.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver for handling work profile provisioning events.
 * This runs in the context of the work profile once it's created.
 */
class WorkProfileProvisioningReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "WorkProfileReceiver"
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Work profile admin enabled")
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Work profile admin disabled")
    }
    
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Work profile provisioning complete")
        
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(context, WorkProfileProvisioningReceiver::class.java)
            
            // Critical: Enable the profile to make its apps visible to the launcher
            dpm.setProfileEnabled(adminComponent)
            
            // Set a user-friendly name for the profile
            dpm.setProfileName(adminComponent, "Work Profile")
            
            // Allow common intents to cross profiles if needed
            // dpm.addCrossProfileIntentFilter(...) 

            Log.i(TAG, "Work profile successfully initialized and enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize work profile setup: ${e.message}", e)
        }
    }
}
