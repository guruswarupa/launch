package com.guruswarupa.launch.ui.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.EncryptedFolderManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.Charset

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
        
        noteTitle = findViewById(R.id.note_title)
        noteContent = findViewById(R.id.note_content)
        
        findViewById<ImageView>(R.id.back_button).setOnClickListener {
            finish()
        }
        
        findViewById<ImageView>(R.id.save_button).setOnClickListener {
            saveNote()
        }
        
        // Check if we're editing an existing note
        fileName = intent.getStringExtra("FILE_NAME")
        isEditing = !fileName.isNullOrEmpty()
        
        if (isEditing) {
            findViewById<TextView>(R.id.title_text).text = "Edit Note"
            loadExistingNote()
        }
    }
    
    private fun loadExistingNote() {
        try {
            val tempFile = vaultManager.decryptToCache(fileName!!)
            val fileContent = tempFile.readBytes()
            
            // Parse the stored note format (title\n\ncontent)
            val contentStr = fileContent.toString(Charset.forName("UTF-8"))
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
            e.printStackTrace()
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
            // Format: title\n\ncontent
            val noteData = if (title.isNotEmpty()) {
                "$title\n\n$content"
            } else {
                "Untitled Note\n\n$content"
            }
            
            val fileNameToUse = if (isEditing) {
                fileName!!
            } else {
                val timestamp = System.currentTimeMillis()
                "note_$timestamp.txt"
            }
            
            // Create a temporary URI from the string content
            val inputStream = ByteArrayInputStream(noteData.toByteArray())
            
            // Create a temporary file to simulate a content URI
            val tempFile = createTempFile("temp_note", ".txt")
            tempFile.writeBytes(noteData.toByteArray())
            
            // Encrypt the file using the vault manager
            vaultManager.encryptFile(android.net.Uri.fromFile(tempFile), fileNameToUse)
            
            // Clean up the temporary file
            tempFile.delete()
            
            Toast.makeText(this, "Note saved successfully", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save note: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}