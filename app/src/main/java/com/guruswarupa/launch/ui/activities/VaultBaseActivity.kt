package com.guruswarupa.launch.ui.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.guruswarupa.launch.models.Constants
import com.guruswarupa.launch.managers.EncryptedFolderManager

abstract class VaultBaseActivity : AppCompatActivity() {
    companion object {
        private var activeVaultActivities = 0
    }

    private var lastInteractionTime: Long = 0
    private val inactiveTimeoutRunnable = Runnable {
        // Auto-lock after inactivity timeout
        EncryptedFolderManager(this).lock()
        finish()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var screenOffReceiver: BroadcastReceiver? = null
    
    private val prefs by lazy { getSharedPreferences(Constants.Prefs.PREFS_NAME, MODE_PRIVATE) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeVaultActivities++
        initializeAutoLock()
    }
    
    private fun initializeAutoLock() {
        updateLastInteractionTime()
        
        // Register receiver to detect screen off events
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    EncryptedFolderManager(this@VaultBaseActivity).lock()
                    finish() // Lock vault when screen turns off
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }
    
    private fun updateLastInteractionTime() {
        lastInteractionTime = System.currentTimeMillis()
        
        // Cancel any pending timeout checks
        handler.removeCallbacks(inactiveTimeoutRunnable)
        
        // Schedule a new timeout check if auto-lock is enabled
        if (isAutoLockEnabled()) {
            val timeoutDuration = getTimeoutDurationMinutes() * 60 * 1000L // Convert minutes to milliseconds
            handler.postDelayed(inactiveTimeoutRunnable, timeoutDuration)
        }
    }
    
    private fun isAutoLockEnabled(): Boolean {
        return prefs.getBoolean(Constants.Prefs.VAULT_TIMEOUT_ENABLED, false)
    }
    
    private fun getTimeoutDurationMinutes(): Int {
        return prefs.getInt(Constants.Prefs.VAULT_TIMEOUT_DURATION, 1) // Default to 1 minute
    }
    
    override fun onResume() {
        super.onResume()
        updateLastInteractionTime()
    }
    
    override fun onPause() {
        super.onPause()
        // Don't remove the callback here since we want the timeout to continue running
    }
    
    override fun onDestroy() {
        super.onDestroy()
        activeVaultActivities--
        
        // If this is the last vault activity and it's finishing (not rotating), lock the vault
        if (activeVaultActivities <= 0 && isFinishing) {
            EncryptedFolderManager(this).lock()
        }
        
        handler.removeCallbacks(inactiveTimeoutRunnable)
        screenOffReceiver?.let { unregisterReceiver(it) }
    }
    
    // Method to be called when user interacts with the UI
    override fun onUserInteraction() {
        super.onUserInteraction()
        updateLastInteractionTime()
    }
}
