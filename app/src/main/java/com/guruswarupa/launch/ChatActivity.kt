
package com.guruswarupa.launch

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.api.Message
import com.guruswarupa.launch.repository.ChatRepository
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var chatRepository: ChatRepository
    private lateinit var sharedPreferences: SharedPreferences
    
    private val chatHistory = mutableListOf<Message>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_interface)
        
        sharedPreferences = getSharedPreferences("GroqApiPrefs", Context.MODE_PRIVATE)
        chatRepository = ChatRepository()
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupSendButton()
        
        // Add a welcome message
        addBotMessage("Hello! I'm your AI assistant. How can I help you today?")
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.chatToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }
    
    private fun setupViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        
        messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }
    
    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }
    
    private fun setupSendButton() {
        sendButton.setOnClickListener {
            sendMessage()
        }
    }
    
    private fun sendMessage() {
        val messageText = messageEditText.text.toString().trim()
        if (messageText.isNotEmpty()) {
            addUserMessage(messageText)
            messageEditText.text.clear()
            
            // Get API key
            val apiKey = sharedPreferences.getString("GROQ_API_KEY", "")
            if (apiKey.isNullOrEmpty()) {
                showApiKeyDialog()
                return
            }
            
            // Add user message to chat history
            chatHistory.add(Message("user", messageText))
            
            // Get response from Groq AI
            lifecycleScope.launch {
                try {
                    val result = chatRepository.sendMessage(apiKey, chatHistory)
                    result.onSuccess { response ->
                        // Add AI response to chat history
                        chatHistory.add(Message("assistant", response))
                        addBotMessage(response)
                    }.onFailure { error ->
                        addBotMessage("Sorry, I encountered an error: ${error.message}")
                    }
                } catch (e: Exception) {
                    addBotMessage("Sorry, I encountered an error: ${e.message}")
                }
            }
        }
    }
    
    private fun addUserMessage(message: String) {
        chatAdapter.addMessage(ChatMessage(message, true))
        scrollToBottom()
    }
    
    private fun addBotMessage(message: String) {
        chatAdapter.addMessage(ChatMessage(message, false))
        scrollToBottom()
    }
    
    private fun scrollToBottom() {
        chatRecyclerView.post {
            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
    
    private fun showApiKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_api_key, null)
        val apiKeyEditText = dialogView.findViewById<EditText>(R.id.apiKeyEditText)
        
        // Pre-fill with existing API key if available
        apiKeyEditText.setText(sharedPreferences.getString("GROQ_API_KEY", ""))
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Groq API Key Required")
            .setMessage("Please enter your Groq API key to use the chat functionality.")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val apiKey = apiKeyEditText.text.toString().trim()
                if (apiKey.isNotEmpty()) {
                    sharedPreferences.edit().putString("GROQ_API_KEY", apiKey).apply()
                    Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
