package com.guruswarupa.launch.ui.adapters

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.R

class PdfPageAdapterLazy(
    private val pdfRenderer: PdfRenderer,
    private val totalPages: Int
) : RecyclerView.Adapter<PdfPageAdapterLazy.PageViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.pdf_page_image)
        val pageNumber: TextView = view.findViewById(R.id.pdf_page_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        try {
            val page = pdfRenderer.openPage(position)
            val scale = 2.0f
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            holder.imageView.setImageBitmap(bitmap)
            holder.pageNumber.text = "Page ${position + 1}"
        } catch (_: Exception) {
            holder.imageView.setImageResource(android.R.drawable.ic_dialog_alert)
            holder.pageNumber.text = "Error loading page ${position + 1}"
        }
    }

    override fun getItemCount() = totalPages

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.imageView.setImageBitmap(null)
    }
}
