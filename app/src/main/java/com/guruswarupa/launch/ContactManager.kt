package com.guruswarupa.launch

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

/**
 * Manages contact loading and caching.
 * Extracted from MainActivity to reduce complexity.
 */
class ContactManager(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val backgroundExecutor: Executor
) {
    private val contactsList: MutableList<String> = mutableListOf()
    
    /**
     * Loads contacts from the device if permission is granted.
     * @param onComplete Callback with the loaded contacts list
     */
    fun loadContacts(onComplete: ((List<String>) -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            onComplete?.invoke(contactsList)
            return
        }
        
        backgroundExecutor.execute {
            try {
                val tempContactsList = mutableListOf<String>()
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                        if (name != null && !tempContactsList.contains(name)) {
                            tempContactsList.add(name)
                        }
                    }
                }
                
                // Sort contacts in background thread
                tempContactsList.sort()
                
                contactsList.clear()
                contactsList.addAll(tempContactsList)
                
                onComplete?.invoke(contactsList)
            } catch (e: Exception) {
                // Handle error silently or log
                onComplete?.invoke(contactsList)
            }
        }
    }
    
    fun getContactsList(): List<String> = contactsList.toList()
    
    fun updateContactsList(newList: List<String>) {
        contactsList.clear()
        contactsList.addAll(newList)
    }
}
