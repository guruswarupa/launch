package com.guruswarupa.launch

import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.Toast

/**
 * Handles voice search commands and executes appropriate actions
 */
class VoiceCommandHandler(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val packageManager: android.content.pm.PackageManager,
    private val contentResolver: ContentResolver,
    private val searchBox: EditText,
    private val appList: List<ResolveInfo>
) {
    
    /**
     * Handle voice command and execute appropriate action
     */
    fun handleCommand(command: String) {
        when {
            command.startsWith("WhatsApp ", ignoreCase = true) -> {
                val contactName = command.substringAfter("WhatsApp ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    openWhatsAppChat(contactName)
                    searchBox.text.clear()
                }
            }
            command.startsWith("send ", ignoreCase = true) && command.contains(" to ", ignoreCase = true) -> {
                val parts = command.split(" to ", ignoreCase = true)
                if (parts.size == 2) {
                    val message = parts[0].substringAfter("send ").trim()
                    val contactName = parts[1].trim()
                    val phoneNumber = getPhoneNumberForContact(contactName)
                    
                    phoneNumber?.let {
                        sendWhatsAppMessage(it, message)
                        searchBox.text.clear()
                    } ?: Toast.makeText(activity, "Contact not found", Toast.LENGTH_SHORT).show()
                }
            }
            command.startsWith("message ", ignoreCase = true) -> {
                val contactName = command.substringAfter("message ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    openSMSChat(contactName)
                    searchBox.text.clear()
                }
            }
            command.startsWith("call ", ignoreCase = true) -> {
                val contactName = command.substringAfter("call ", "").trim()
                val phoneNumber = getPhoneNumberForContact(contactName)
                phoneNumber?.let {
                    val callIntent = Intent(Intent.ACTION_CALL)
                    callIntent.data = Uri.parse("tel:$it")
                    activity.startActivity(callIntent)
                    searchBox.text.clear()
                }
            }
            command.startsWith("search ", ignoreCase = true) -> {
                val query = command.substringAfter("search ", "").trim()
                val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                activity.startActivity(searchIntent)
                searchBox.text.clear()
            }
            command.startsWith("open ", ignoreCase = true) -> {
                val appName = command.substringAfter("open ", "").trim()
                if (appName.isNotEmpty()) {
                    val matchingApp = appList.find { resolveInfo ->
                        try {
                            val label = resolveInfo.activityInfo?.applicationInfo?.let {
                                resolveInfo.loadLabel(packageManager)?.toString()
                            }
                            label?.contains(appName, ignoreCase = true) ?: false
                        } catch (e: Exception) {
                            false
                        }
                    }
                    matchingApp?.let {
                        val intent = packageManager.getLaunchIntentForPackage(it.activityInfo.packageName)
                        intent?.let { activity.startActivity(it) }
                        searchBox.text.clear()
                    } ?: searchBox.setText(command)
                } else {
                    searchBox.setText(command)
                }
            }
            command.startsWith("uninstall ", ignoreCase = true) -> {
                val appName = command.substringAfter("uninstall ", "").trim()
                if (appName.isNotEmpty()) {
                    val matchingApp = appList.find { resolveInfo ->
                        try {
                            val label = resolveInfo.activityInfo?.applicationInfo?.let {
                                resolveInfo.loadLabel(packageManager)?.toString()
                            }
                            label?.contains(appName, ignoreCase = true) ?: false
                        } catch (e: Exception) {
                            false
                        }
                    }
                    
                    matchingApp?.let {
                        val packageName = it.activityInfo.packageName
                        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        activity.startActivity(intent)
                        searchBox.text.clear()
                    } ?: searchBox.setText(command)
                } else {
                    searchBox.setText(command)
                }
            }
            command.contains(" to ", ignoreCase = true) -> {
                val locations = command.split(" to ", ignoreCase = true)
                if (locations.size == 2) {
                    val origin = locations[0].trim()
                    val destination = locations[1].trim()
                    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(destination)}&travelmode=driving")
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    try {
                        activity.startActivity(mapIntent)
                        searchBox.text.clear()
                    } catch (e: Exception) {
                        Toast.makeText(activity, "Google Maps not installed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    private fun getPhoneNumberForContact(contactName: String): String? {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )
        
        fun normalize(name: String): List<String> {
            return name.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .split(" ")
                .filter { it.isNotBlank() }
        }
        
        val inputParts = normalize(contactName)
        val seenNames = mutableSetOf<String>()
        val matches = mutableListOf<Pair<String, String>>()
        
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)?.trim() ?: continue
                val number = it.getString(1)?.trim() ?: continue
                
                if (number.isEmpty() || !number.any { it.isDigit() }) continue
                
                val nameParts = normalize(name)
                
                if (inputParts.any { input -> nameParts.any { part -> part.contains(input) } }) {
                    if (!seenNames.contains(name.lowercase())) {
                        matches.add(name to number)
                        seenNames.add(name.lowercase())
                    }
                }
            }
        }
        
        return matches.minByOrNull { (name, _) ->
            val norm = normalize(name).joinToString(" ")
            when {
                norm == inputParts.joinToString(" ") -> 0
                norm.startsWith(inputParts.joinToString(" ")) -> 1
                else -> 2
            }
        }?.second
    }
    
    private fun sendWhatsAppMessage(phoneNumber: String, message: String) {
        try {
            val formattedPhoneNumber = phoneNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/${Uri.encode(formattedPhoneNumber)}?text=${Uri.encode(message)}")
                setPackage("com.whatsapp")
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(activity, "WhatsApp not installed or failed to open message.", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openWhatsAppChat(contactName: String) {
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(contactName),
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                var phoneNumber = it.getString(0)
                    .replace(" ", "")
                    .replace("-", "")
                    .replace("(", "")
                    .replace(")", "")
                
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/${Uri.encode(phoneNumber)}")
                        setPackage("com.whatsapp")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(activity, "WhatsApp not installed.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "Contact not found", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun openSMSChat(contactName: String) {
        val phoneNumber = getPhoneNumberForContact(contactName)
        
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", "")
        }
        
        try {
            if (intent.resolveActivity(packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, "No SMS app installed!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to open messaging app.", Toast.LENGTH_SHORT).show()
        }
    }
}
