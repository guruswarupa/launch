
package com.guruswarupa.launch

import android.content.Intent
import android.view.ViewGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * This extension function adds a chat button to the MainActivity
 */
fun MainActivity.addChatButton() {
    val rootView = findViewById<ViewGroup>(android.R.id.content)
    val chatFab = layoutInflater.inflate(R.layout.chat_fab, rootView, false) as FloatingActionButton
    
    chatFab.setOnClickListener {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }
    
    rootView.addView(chatFab)
}
