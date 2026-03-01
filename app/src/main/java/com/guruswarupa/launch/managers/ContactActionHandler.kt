package com.guruswarupa.launch.managers

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.widget.AutoCompleteTextView
import androidx.fragment.app.FragmentActivity
import com.guruswarupa.launch.utils.VoiceCommandHandler

/**
 * Handles contact-related actions such as opening WhatsApp or SMS chats.
 * Lazily initializes VoiceCommandHandler to perform these actions.
 */
class ContactActionHandler(
    private val activity: FragmentActivity,
    private val packageManager: PackageManager,
    private val contentResolver: ContentResolver,
    private val searchBox: AutoCompleteTextView,
    private val appList: List<ResolveInfo>,
    private val onHandlerCreated: (VoiceCommandHandler) -> Unit
) {
    private var voiceCommandHandler: VoiceCommandHandler? = null

    /**
     * Returns the existing VoiceCommandHandler or creates a new one if it doesn't exist.
     */
    private fun getHandler(): VoiceCommandHandler {
        return voiceCommandHandler ?: VoiceCommandHandler(
            activity,
            packageManager,
            contentResolver,
            searchBox,
            appList
        ).also {
            voiceCommandHandler = it
            onHandlerCreated(it)
        }
    }

    /**
     * Opens a WhatsApp chat for the specified contact.
     */
    fun openWhatsAppChat(contactName: String) {
        getHandler().openWhatsAppChat(contactName)
    }

    /**
     * Opens an SMS chat for the specified contact.
     */
    fun openSMSChat(contactName: String) {
        getHandler().openSMSChat(contactName)
    }
}
