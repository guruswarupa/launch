
package com.guruswarupa.launch

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

class AppLockManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "app_lock_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREF_PIN_HASH = "pin_hash"
        private const val PREF_LOCKED_APPS = "locked_apps"
        private const val PREF_IS_APP_LOCK_ENABLED = "is_app_lock_enabled"
        private const val PREF_LAST_AUTH_TIME = "last_auth_time"
        private const val AUTH_TIMEOUT = 2 * 60 * 1000L //2 min
    }

    // Hash the PIN for secure storage
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // Set up PIN for the first time
    fun setupPin(callback: (Boolean) -> Unit) {
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-6 digit PIN"
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Set App Lock PIN")
            .setMessage("Choose a PIN to protect your apps")
            .setView(pinInput)
            .setPositiveButton("Set PIN") { _, _ ->
                val pin = pinInput.text.toString()
                if (pin.length >= 4 && pin.length <= 6 && pin.all { it.isDigit() }) {
                    val confirmInput = EditText(context).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        hint = "Confirm PIN"
                    }

                    AlertDialog.Builder(context, R.style.CustomDialogTheme)
                        .setTitle("Confirm PIN")
                        .setView(confirmInput)
                        .setPositiveButton("Confirm") { _, _ ->
                            val confirmPin = confirmInput.text.toString()
                            if (pin == confirmPin) {
                                sharedPreferences.edit()
                                    .putString(PREF_PIN_HASH, hashPin(pin))
                                    .putBoolean(PREF_IS_APP_LOCK_ENABLED, true)
                                    .apply()
                                Toast.makeText(context, "App Lock PIN set successfully!", Toast.LENGTH_SHORT).show()
                                callback(true)
                            } else {
                                Toast.makeText(context, "PINs don't match. Try again.", Toast.LENGTH_SHORT).show()
                                callback(false)
                            }
                        }
                        .setNegativeButton("Cancel") { _, _ -> callback(false) }
                        .show()
                } else {
                    Toast.makeText(context, "PIN must be 4-6 digits", Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> callback(false) }
            .show()
    }

    // Verify PIN
    fun verifyPin(callback: (Boolean) -> Unit) {
        if (!isPinSet()) {
            callback(true)
            return
        }

        // Check if recently authenticated (within timeout)
        val lastAuthTime = sharedPreferences.getLong(PREF_LAST_AUTH_TIME, 0)
        if (System.currentTimeMillis() - lastAuthTime < AUTH_TIMEOUT) {
            callback(true)
            return
        }

        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter PIN"
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Enter PIN")
            .setMessage("Enter your PIN to unlock this app")
            .setView(pinInput)
            .setPositiveButton("Unlock") { _, _ ->
                val enteredPin = pinInput.text.toString()
                val storedPinHash = sharedPreferences.getString(PREF_PIN_HASH, "")

                if (hashPin(enteredPin) == storedPinHash) {
                    // Update last auth time
                    sharedPreferences.edit()
                        .putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                        .apply()
                    callback(true)
                } else {
                    Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> callback(false) }
            .setCancelable(false)
            .show()
    }

    // Check if PIN is set
    fun isPinSet(): Boolean {
        return sharedPreferences.getString(PREF_PIN_HASH, "")!!.isNotEmpty()
    }

    // Check if app lock is enabled
    fun isAppLockEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_IS_APP_LOCK_ENABLED, false) && isPinSet()
    }

    // Enable/disable app lock
    fun setAppLockEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(PREF_IS_APP_LOCK_ENABLED, enabled)
            .apply()
    }

    // Add app to locked apps list
    fun lockApp(packageName: String) {
        val lockedApps = getLockedApps().toMutableSet()
        lockedApps.add(packageName)
        sharedPreferences.edit()
            .putStringSet(PREF_LOCKED_APPS, lockedApps)
            .apply()
    }

    // Remove app from locked apps list
    fun unlockApp(packageName: String) {
        val lockedApps = getLockedApps().toMutableSet()
        lockedApps.remove(packageName)
        sharedPreferences.edit()
            .putStringSet(PREF_LOCKED_APPS, lockedApps)
            .apply()
    }

    // Get list of locked apps
    fun getLockedApps(): Set<String> {
        return sharedPreferences.getStringSet(PREF_LOCKED_APPS, emptySet()) ?: emptySet()
    }

    // Check if app is locked
    fun isAppLocked(packageName: String): Boolean {
        return isAppLockEnabled() && getLockedApps().contains(packageName)
    }

    // Change PIN
    fun changePin(callback: (Boolean) -> Unit) {
        if (!isPinSet()) {
            setupPin(callback)
            return
        }

        val oldPinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter current PIN"
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Change PIN")
            .setMessage("Enter your current PIN")
            .setView(oldPinInput)
            .setPositiveButton("Continue") { _, _ ->
                val oldPin = oldPinInput.text.toString()
                val storedPinHash = sharedPreferences.getString(PREF_PIN_HASH, "")

                if (hashPin(oldPin) == storedPinHash) {
                    setupPin(callback)
                } else {
                    Toast.makeText(context, "Incorrect current PIN", Toast.LENGTH_SHORT).show()
                    callback(false)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> callback(false) }
            .show()
    }

    // Reset app lock (remove PIN and all locked apps)
    fun resetAppLock() {
        sharedPreferences.edit()
            .remove(PREF_PIN_HASH)
            .remove(PREF_LOCKED_APPS)
            .remove(PREF_IS_APP_LOCK_ENABLED)
            .remove(PREF_LAST_AUTH_TIME)
            .apply()
    }

    // Show app lock management dialog
    fun showAppLockManagementDialog(packageName: String, appName: String) {
        val isCurrentlyLocked = isAppLocked(packageName)
        val options = if (isCurrentlyLocked) {
            arrayOf("Remove Lock", "Cancel")
        } else {
            arrayOf("Add Lock", "Cancel")
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("App Lock - $appName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (isCurrentlyLocked) {
                            unlockApp(packageName)
                            Toast.makeText(context, "Lock removed from $appName", Toast.LENGTH_SHORT).show()
                        } else {
                            lockApp(packageName)
                            Toast.makeText(context, "Lock added to $appName", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }
}
