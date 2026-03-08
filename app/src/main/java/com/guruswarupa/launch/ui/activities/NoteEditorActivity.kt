package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import java.io.File


class NoteEditorActivity : VaultBaseActivity() {
    private lateinit var noteTitle: EditText
    private lateinit var noteContent: EditText
    private lateinit var vaultManager: EncryptedFolderManager
    private var isEditing = false
    private var fileName: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)
        
        vaultManager = EncryptedFolderManager(this)
        
        // If vault is locked, we can't do anything here.
        // Usually we are started from EncryptedVaultActivity which handles unlocking.
        if (!vaultManager.isUnlocked()) {
            Toast.makeText(this, "Vault is locked", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        noteTitle = findViewById(R.id.note_title)
        noteContent = findViewById(R.id.note_content)
        
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        findViewById<ImageView>(R.id.save_button).setOnClickListener {
            saveNote()
        }
        
        fileName = intent.getStringExtra("FILE_NAME")
        isEditing = !fileName.isNullOrEmpty()
        
        if (isEditing) {
            findViewById<TextView>(R.id.title_text).text = "Edit Note"
            loadExistingNote()
        }
    }
    
    private fun loadExistingNote() {
        val fileNameToLoad = fileName
        if (fileNameToLoad.isNullOrBlank()) {
            Toast.makeText(this, "Invalid note file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        var tempFile: File? = null
        try {
            tempFile = vaultManager.decryptToCache(fileNameToLoad)
            val contentStr = tempFile.readText(Charsets.UTF_8)
            
            val parts = contentStr.split("\n\n", limit = 2)
            if (parts.size >= 2) {
                noteTitle.setText(parts[0])
                noteContent.setText(parts[1])
            } else {
                noteTitle.setText("")
                noteContent.setText(contentStr)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load note: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            tempFile?.delete()
        }
    }
    
    private fun saveNote() {
        val title = noteTitle.text.toString().trim()
        val content = noteContent.text.toString()
        
        if (content.isEmpty()) {
            Toast.makeText(this, "Cannot save empty note", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val noteData = if (title.isNotEmpty()) "$title\n\n$content" else "Untitled Note\n\n$content"
            val fileNameToUse = if (isEditing) {
                val existingFileName = fileName
                if (existingFileName.isNullOrBlank()) {
                    Toast.makeText(this, "Invalid note file", Toast.LENGTH_SHORT).show()
                    return
                }
                existingFileName
            } else {
                "note_${System.currentTimeMillis()}.txt"
            }
            
            val tempFile = File.createTempFile("note_", ".txt", cacheDir)
            try {
                tempFile.writeText(noteData, Charsets.UTF_8)
                vaultManager.encryptFile(Uri.fromFile(tempFile), fileNameToUse)
            } finally {
                tempFile.delete()
            }
            
            Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save note: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
