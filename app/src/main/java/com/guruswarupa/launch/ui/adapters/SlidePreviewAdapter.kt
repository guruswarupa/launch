package com.guruswarupa.launch.ui.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R

class SlidePreviewAdapter(
    private val slides: List<String>,
    private var currentSlideIndex: Int,
    private val onSlideClick: (Int) -> Unit
) : RecyclerView.Adapter<SlidePreviewAdapter.SlidePreviewViewHolder>() {

    fun updateCurrentSlide(index: Int) {
        currentSlideIndex = index
        notifyDataSetChanged()
    }

    class SlidePreviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val webView: WebView = view.findViewById(R.id.preview_webview)
        val slideNumber: TextView = view.findViewById(R.id.slide_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlidePreviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_slide_preview, parent, false)
        return SlidePreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlidePreviewViewHolder, position: Int) {
        val slideContent = slides[position]
        val isCurrent = position == currentSlideIndex

        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true

        val previewHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background: #FFFFFF;
                        color: #333;
                        padding: 8px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        min-height: 100px;
                    }
                    .slide {
                        background: #FFFFFF;
                        border: 2px solid ${if (isCurrent) "#d08770" else "#e0e0e0"};
                        border-radius: 4px;
                        padding: 12px;
                        width: 100%;
                        height: 100%;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                    }
                    .slide h3 {
                        color: #1a1a1a;
                        margin: 0 0 8px 0;
                        font-size: 10px;
                        border-bottom: 1px solid #f0f0f0;
                        padding-bottom: 4px;
                        font-weight: 600;
                        text-align: center;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        white-space: nowrap;
                    }
                    .slide p {
                        margin: 4px 0;
                        line-height: 1.2;
                        color: #333;
                        font-size: 7px;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        display: -webkit-box;
                        -webkit-line-clamp: 2;
                        -webkit-box-orient: vertical;
                    }
                    .empty { opacity: 0.5; font-style: italic; color: #666; font-size: 6px; }
                </style>
            </head>
            <body>
                <div class="slide">
                    $slideContent
                </div>
            </body>
            </html>
        """.trimIndent()

        holder.webView.loadDataWithBaseURL(null, previewHtml, "text/html", "UTF-8", null)
        holder.slideNumber.text = "${position + 1}"

        holder.webView.isClickable = false
        holder.webView.isLongClickable = false
        holder.webView.isFocusable = false
        holder.webView.isFocusableInTouchMode = false
        holder.webView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                holder.itemView.performClick()
            }
            true
        }

        holder.itemView.setOnClickListener {
            onSlideClick(position)
        }
    }

    override fun getItemCount() = slides.size

    override fun onViewRecycled(holder: SlidePreviewViewHolder) {
        super.onViewRecycled(holder)
        holder.webView.loadUrl("about:blank")
    }
}
