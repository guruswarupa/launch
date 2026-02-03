package com.guruswarupa.launch

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Handles voice search functionality.
 * Extracted from MainActivity to reduce complexity.
 */
class VoiceSearchManager(
    private val activity: FragmentActivity,
    private val packageManager: android.content.pm.PackageManager,
    private val searchBox: EditText,
    private val permissionManager: PermissionManager
) {
    fun startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PermissionManager.VOICE_SEARCH_REQUEST
            )
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        if (intent.resolveActivity(packageManager) != null) {
            try {
                activity.startActivityForResult(intent, PermissionManager.VOICE_SEARCH_REQUEST)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, "Voice recognition not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, "Voice recognition not available", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Triggers the system's voice command assistant (Google Assistant or Gemini).
     * This is the "free" integration that leverages the system's built-in capabilities.
     */
    fun triggerSystemAssistant() {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        if (intent.resolveActivity(packageManager) != null) {
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(activity, "Could not launch system assistant", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Fallback for devices without a dedicated voice command activity
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (searchIntent.resolveActivity(packageManager) != null) {
                activity.startActivity(searchIntent)
            } else {
                Toast.makeText(activity, "No voice assistant found on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
