
package com.guruswarupa.launch

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * This extension function adds a chat button to the MainActivity
 */
fun MainActivity.addChatButton() {
    val rootView = findViewById<ViewGroup>(android.R.id.content)
    val chatFab = layoutInflater.inflate(R.layout.chat_fab, rootView, false) as FloatingActionButton

    // Set visibility and position
    chatFab.visibility = View.VISIBLE
    chatFab.elevation = 8f

    // Add layout parameters to position it at the bottom right
    val params = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    chatFab.layoutParams = params

    chatFab.setOnClickListener {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }

    rootView.addView(chatFab)
}