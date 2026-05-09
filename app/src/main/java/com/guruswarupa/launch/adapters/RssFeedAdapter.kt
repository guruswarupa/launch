package com.guruswarupa.launch.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.guruswarupa.launch.R
import com.guruswarupa.launch.managers.RssArticle
import com.guruswarupa.launch.ui.activities.WebAppActivity
import java.text.DateFormat
import java.util.Date

class RssFeedAdapter(
    private val articles: MutableList<RssArticle> = mutableListOf()
) : RecyclerView.Adapter<RssFeedAdapter.RssArticleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RssArticleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rss_article, parent, false)
        return RssArticleViewHolder(view)
    }

    override fun onBindViewHolder(holder: RssArticleViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    fun submitArticles(items: List<RssArticle>) {
        articles.clear()
        articles.addAll(items)
        notifyDataSetChanged()
    }

    class RssArticleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail = itemView.findViewById<ImageView>(R.id.article_thumbnail)
        private val title = itemView.findViewById<TextView>(R.id.article_title)
        private val meta = itemView.findViewById<TextView>(R.id.article_meta)
        private val description = itemView.findViewById<TextView>(R.id.article_description)

        fun bind(article: RssArticle) {
            title.text = article.title
            val dateLabel = if (article.pubDate > 0L) {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(article.pubDate))
            } else {
                "Latest"
            }
            meta.text = "${article.source}  •  $dateLabel"
            description.text = article.description.ifBlank { article.link }

            if (article.imageUrl.isNullOrBlank()) {
                thumbnail.visibility = View.GONE
                Glide.with(itemView).clear(thumbnail)
            } else {
                thumbnail.visibility = View.VISIBLE
                Glide.with(itemView)
                    .load(article.imageUrl)
                    .centerCrop()
                    .into(thumbnail)
            }

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, WebAppActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                    putExtra(WebAppActivity.EXTRA_WEB_APP_NAME, article.title.ifBlank { article.source })
                    putExtra(WebAppActivity.EXTRA_WEB_APP_URL, article.link)
                    // RSS links should always block redirects for security
                    putExtra(WebAppActivity.EXTRA_BLOCK_REDIRECTS, true)
                }
                itemView.context.startActivity(intent)
            }
        }
    }
}
