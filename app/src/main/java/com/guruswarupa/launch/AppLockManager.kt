package com.guruswarupa.launch

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class AppLockManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "app_lock_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREF_PIN_HASH = "pin_hash"
        private const val PREF_LOCKED_APPS = "locked_apps"
        private const val PREF_IS_APP_LOCK_ENABLED = "is_app_lock_enabled"
        private const val PREF_LAST_AUTH_TIME = "last_auth_time"
        private const val PREF_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val AUTH_TIMEOUT = 1 * 60 * 1000L // 1 min
    }

    // Hash the PIN for secure storage
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // Set up PIN for the first time
    fun setupPin(callback: (Boolean) -> Unit) {
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter 4-6 digit PIN"
            setTextColor(textColor)
            setHintTextColor(secondaryTextColor)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Set App Lock PIN")
            .setMessage("Choose a PIN to protect your apps")
            .setView(pinInput)
            .setPositiveButton("Set PIN") { _, _ ->
                val pin = pinInput.text.toString()
                if (pin.length in 4..6 && pin.all { it.isDigit() }) {
                    val confirmInput = EditText(context).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        hint = "Confirm PIN"
                        setTextColor(textColor)
                        setHintTextColor(secondaryTextColor)
                    }

                    AlertDialog.Builder(context, R.style.CustomDialogTheme)
                        .setTitle("Confirm PIN")
                        .setView(confirmInput)
                        .setPositiveButton("Confirm") { _, _ ->
                            val confirmPin = confirmInput.text.toString()
                            if (pin == confirmPin) {
                                sharedPreferences.edit {
                                    putString(PREF_PIN_HASH, hashPin(pin))
                                    putBoolean(PREF_IS_APP_LOCK_ENABLED, true)
                                }
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

        // Check for biometric availability and user preference
        if (isFingerprintEnabled()) {
            showBiometricPrompt(callback)
        } else {
            showPinPrompt(callback)
        }
    }

    private fun showBiometricPrompt(callback: (Boolean) -> Unit) {
        // Check if context is FragmentActivity, if not fall back to PIN
        val activity = context as? FragmentActivity
        if (activity == null) {
            Toast.makeText(context, "Biometric authentication not available in this context. Using PIN.", Toast.LENGTH_SHORT).show()
            showPinPrompt(callback)
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setDescription("Authenticate with your fingerprint to unlock")
            .setNegativeButtonText("Use PIN")
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    sharedPreferences.edit {
                        putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                    }
                    callback(true)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(context, "Biometric authentication failed", Toast.LENGTH_SHORT).show()
                    // Don't call callback(false) here - let user retry
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        // User chose to use PIN or canceled
                        showPinPrompt(callback)
                    } else {
                        Toast.makeText(context, "Biometric authentication error: $errString", Toast.LENGTH_SHORT).show()
                        callback(false)
                    }
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun showPinPrompt(callback: (Boolean) -> Unit) {
        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        
        val pinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter PIN"
            setTextColor(textColor)
            setHintTextColor(secondaryTextColor)
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
                    sharedPreferences.edit {
                        putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis())
                    }
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
        return sharedPreferences.getString(PREF_PIN_HASH, null)?.isNotEmpty() == true
    }

    // Check if fingerprint authentication is available on the device
    fun isFingerprintAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    // Check if fingerprint authentication is enabled
    fun isFingerprintEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_FINGERPRINT_ENABLED, false) && isFingerprintAvailable()
    }

    // Enable/disable fingerprint authentication
    fun setFingerprintEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_FINGERPRINT_ENABLED, enabled) }
    }

    // Check if app lock is enabled
    fun isAppLockEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_IS_APP_LOCK_ENABLED, false) && isPinSet()
    }

    // Enable/disable app lock
    fun setAppLockEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(PREF_IS_APP_LOCK_ENABLED, enabled) }
    }

    // Add app to locked apps list
    fun lockApp(packageName: String) {
        val lockedApps = getLockedApps().toMutableSet()
        lockedApps.add(packageName)
        sharedPreferences.edit { putStringSet(PREF_LOCKED_APPS, lockedApps) }
    }

    // Remove app from locked apps list
    fun unlockApp(packageName: String) {
        val lockedApps = getLockedApps().toMutableSet()
        lockedApps.remove(packageName)
        sharedPreferences.edit { putStringSet(PREF_LOCKED_APPS, lockedApps) }
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

        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        
        val oldPinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter current PIN"
            setTextColor(textColor)
            setHintTextColor(secondaryTextColor)
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
    fun resetAppLock(callback: (Boolean) -> Unit) {
        if (!isPinSet()) {
            // If PIN is not set, directly reset
            resetAppLockData()
            callback(true) // Indicate success
            return
        }

        val textColor = ContextCompat.getColor(context, R.color.text)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        
        val oldPinInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Enter current PIN"
            setTextColor(textColor)
            setHintTextColor(secondaryTextColor)
        }

        AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setTitle("Reset App Lock")
            .setMessage("Enter your current PIN to reset App Lock")
            .setView(oldPinInput)
            .setPositiveButton("Continue") { _, _ ->
                val oldPin = oldPinInput.text.toString()
                val storedPinHash = sharedPreferences.getString(PREF_PIN_HASH, "")

                if (hashPin(oldPin) == storedPinHash) {
                    // Reset app lock data if PIN is correct
                    resetAppLockData()
                    Toast.makeText(context, "App Lock reset successfully!", Toast.LENGTH_SHORT).show()
                    callback(true) // Indicate success
                } else {
                    Toast.makeText(context, "Incorrect current PIN", Toast.LENGTH_SHORT).show()
                    callback(false) // Indicate failure
                }
            }
            .setNegativeButton("Cancel") { _, _ -> callback(false) }
            .show()
    }

    private fun resetAppLockData() {
        sharedPreferences.edit {
            remove(PREF_PIN_HASH)
            remove(PREF_LOCKED_APPS)
            remove(PREF_IS_APP_LOCK_ENABLED)
            remove(PREF_LAST_AUTH_TIME)
            remove(PREF_FINGERPRINT_ENABLED)
        }
    }
    
    // Clear authentication timeout - forces re-authentication on next verifyPin call
    fun clearAuthTimeout() {
        sharedPreferences.edit { remove(PREF_LAST_AUTH_TIME) }
    }
}
