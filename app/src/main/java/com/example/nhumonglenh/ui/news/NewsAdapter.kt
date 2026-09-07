package com.example.nhumonglenh.ui.news

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.databinding.ItemNewsBinding

class NewsAdapter(
    private val onClick: (News) -> Unit
) : RecyclerView.Adapter<NewsAdapter.NewsVH>() {

    private val items = mutableListOf<News>()

    fun submit(list: List<News>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class NewsVH(val b: ItemNewsBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsVH {
        val b = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsVH(b)
    }

    override fun onBindViewHolder(holder: NewsVH, position: Int) {
        val n = items[position]
        holder.b.tvTitle.text = n.title
        holder.b.tvSummary.text = n.summary
        holder.b.tvSource.text = n.source
        holder.b.tvConfidence.text = "${n.confidence}% tin cậy"

        // Ngày đăng
        holder.b.tvDate.text = n.publishedAt

        // Tác giả
        holder.b.tvAuthor.text = "Tác giả: " + (n.author ?: "Ẩn danh")

        // Link bài báo
        holder.b.tvLink.setOnClickListener {
            if (!n.link.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(n.link))
                it.context.startActivity(intent)
            }
        }

        val (label, color) = when (n.sentiment) {
            "bullish" -> "BULLISH" to Color.parseColor("#2E7D32")
            "bearish" -> "BEARISH" to Color.parseColor("#C62828")
            else      -> "NEUTRAL" to Color.parseColor("#F9A825")
        }
        holder.b.tvSentiment.text = label
        holder.b.tvSentiment.setBackgroundColor(color)

        // Bullet points động
        holder.b.bulletContainer.removeAllViews()
        n.bulletPoints.take(3).forEach { point ->
            val row = LinearLayout(holder.itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
            }
            val dot = TextView(holder.itemView.context).apply {
                text = "•"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
            }
            val text = TextView(holder.itemView.context).apply {
                text = point
                textSize = 12f
                setTextColor(Color.parseColor("#444444"))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply { marginStart = 8 }
            }
            row.addView(dot)
            row.addView(text)
            holder.b.bulletContainer.addView(row)
        }

        holder.itemView.setOnClickListener { onClick(n) }
    }

    override fun getItemCount() = items.size
}
