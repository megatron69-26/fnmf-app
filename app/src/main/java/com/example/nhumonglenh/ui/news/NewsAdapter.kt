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
import com.example.nhumonglenh.ui.UiTextLocalizer
import java.text.SimpleDateFormat
import java.util.Locale

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
        val effectiveTitle = n.getEffectiveTitle()
        val effectiveSummary = n.getEffectiveSummary()
        val effectiveSource = n.getEffectivePublisher()
        val effectiveBullets = n.getEffectiveBullets()

        holder.b.tvTitle.text = effectiveTitle
        holder.b.tvTitle.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_PRIMARY)
        holder.b.tvSummary.text = effectiveSummary
        holder.b.tvSource.text = effectiveSource
        holder.b.tvConfidence.text = NewsCardPresentationMapper.formatConfidence(n.confidence)
        holder.b.tvConfidence.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)

        // Dòng metadata: Publisher · DD/MM/YYYY (ẩn nếu rỗng)
        val formattedMeta = NewsCardPresentationMapper.formatMetadata(effectiveSource, n.publishedAt, n.link)
        if (formattedMeta.isNotBlank()) {
            holder.b.tvMetadata.visibility = android.view.View.VISIBLE
            holder.b.tvMetadata.text = formattedMeta
            holder.b.tvMetadata.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)
        } else {
            holder.b.tvMetadata.visibility = android.view.View.GONE
        }

        // Ngày đăng tương thích cũ
        holder.b.tvDate.text = n.publishedAt
        holder.b.tvReleaseDate.text = formatReleaseDate(n.publishedAt)

        // Tác giả
        val formattedAuthor = NewsCardPresentationMapper.formatAuthor(n.author, effectiveSource)
        if (formattedAuthor != null) {
            holder.b.tvAuthor.visibility = android.view.View.VISIBLE
            holder.b.tvAuthor.text = formattedAuthor
            holder.b.tvAuthor.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)
        } else {
            holder.b.tvAuthor.visibility = android.view.View.GONE
        }

        // Link bài báo ở đít thẻ: chip "Xem nguồn"
        if (!n.link.isNullOrBlank()) {
            holder.b.layoutLinkContainer.visibility = android.view.View.VISIBLE
            holder.b.tvLink.text = n.link
            holder.b.tvViewSourceLabel.text = "Xem nguồn"
            holder.b.layoutLinkContainer.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(n.link))
                    it.context.startActivity(intent)
                } catch (_: Exception) {
                }
            }
        } else {
            holder.b.layoutLinkContainer.visibility = android.view.View.GONE
        }

        val label = NewsCardPresentationMapper.formatSentimentLabel(n.sentiment)
        val bgColor = NewsCardPresentationMapper.getSentimentBackgroundColor(n.sentiment)
        val textColor = NewsCardPresentationMapper.getSentimentTextColor(n.sentiment)
        holder.b.tvSentiment.text = label
        holder.b.tvSentiment.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
        holder.b.tvSentiment.setTextColor(textColor)

        // Bullet points động
        holder.b.bulletContainer.removeAllViews()
        NewsCardPresentationMapper.formatBullets(effectiveBullets).forEach { point ->
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
                setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)
            }
            val text = TextView(holder.itemView.context).apply {
                text = point
                textSize = 12f
                setTextColor(NewsCardPresentationMapper.COLOR_TEXT_PRIMARY)
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

    companion object {
        fun formatReleaseDate(rawDate: String?): String = NewsCardPresentationMapper.formatReleaseDate(rawDate)
        private fun legacyFormatReleaseDate(rawDate: String?): String {
            if (rawDate.isNullOrBlank()) return "--"
            return runCatching {
                val trimmed = rawDate.trim()
                val date = when {
                    trimmed.contains("T") && !trimmed.contains("-") -> {
                        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                        fmt.isLenient = false
                        fmt.parse(trimmed)
                    }
                    trimmed.contains("-") -> {
                        val clean = if (trimmed.contains("T")) trimmed.substringBefore("T") else trimmed
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        fmt.isLenient = false
                        fmt.parse(clean)
                    }
                    trimmed.length == 8 && trimmed.all { it.isDigit() } -> {
                        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
                        fmt.isLenient = false
                        fmt.parse(trimmed)
                    }
                    else -> null
                }
                if (date != null) {
                    SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
                } else {
                    "--"
                }
            }.getOrElse { "--" }
        }
    }
}
