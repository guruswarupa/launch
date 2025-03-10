
package com.guruswarupa.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderTextView: TextView = itemView.findViewById(R.id.senderTextView)
        val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_bubble, parent, false)
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val chatMessage = messages[position]
        
        holder.senderTextView.text = if (chatMessage.isUser) "You" else "AI Assistant"
        holder.messageTextView.text = chatMessage.message
        
        // Set different backgrounds for user and bot messages
        holder.messageTextView.setBackgroundResource(
            if (chatMessage.isUser) R.drawable.user_message_background
            else R.drawable.bot_message_background
        )
        
        // Align messages to left or right
        val params = holder.messageTextView.layoutParams as ViewGroup.MarginLayoutParams
        if (chatMessage.isUser) {
            params.marginStart = 100
            params.marginEnd = 0
            holder.senderTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        } else {
            params.marginStart = 0
            params.marginEnd = 100
            holder.senderTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        }
        holder.messageTextView.layoutParams = params
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }
}

data class ChatMessage(
    val message: String,
    val isUser: Boolean
)
