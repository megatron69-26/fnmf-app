package com.example.nhumonglenh.ui.news

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.nhumonglenh.databinding.ActivityNewsDetailBinding
import com.example.nhumonglenh.ui.SystemBarInsets
import com.example.nhumonglenh.ui.UiTextLocalizer

class NewsDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityNewsDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityNewsDetailBinding.inflate(layoutInflater)
        setContentView(b.root)
        SystemBarInsets.apply(this, findViewById(android.R.id.content), useLightStatusIcons = true)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        val sentiment = intent.getStringExtra(EXTRA_SENTIMENT) ?: "neutral"
        val confidence = intent.getIntExtra(EXTRA_CONFIDENCE, 0)
        val bullets = intent.getStringArrayExtra(EXTRA_BULLETS) ?: arrayOf()

        val author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
        val link = intent.getStringExtra(EXTRA_LINK) ?: ""

        b.tvDetailTitle.text = title
        b.tvDetailSummary.text = summary
        b.tvDetailSource.text = "Nguồn: $source"
        b.tvDetailConfidence.text = "$confidence% tin cậy"

        val formattedAuthor = NewsCardPresentationMapper.formatAuthor(author, source)
        if (formattedAuthor != null) {
            b.tvDetailAuthor.text = formattedAuthor
            b.tvDetailAuthor.visibility = android.view.View.VISIBLE
        } else {
            b.tvDetailAuthor.visibility = android.view.View.GONE
        }

        if (link.isNotBlank()) {
            b.tvDetailLink.text = link
            b.tvDetailLink.visibility = android.view.View.VISIBLE
            b.btnOpenArticle.visibility = android.view.View.VISIBLE

            val openBrowser = android.view.View.OnClickListener {
                try {
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Không thể mở trình duyệt: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            b.btnOpenArticle.setOnClickListener(openBrowser)
            b.tvDetailLink.setOnClickListener(openBrowser)
        } else {
            b.tvDetailLink.visibility = android.view.View.GONE
            b.btnOpenArticle.visibility = android.view.View.GONE
        }

        val (label, color) = when (sentiment) {
            "bullish" -> UiTextLocalizer.sentiment(sentiment) to Color.parseColor("#2E7D32")
            "bearish" -> UiTextLocalizer.sentiment(sentiment) to Color.parseColor("#C62828")
            else      -> UiTextLocalizer.sentiment(sentiment) to Color.parseColor("#F9A825")
        }
        b.tvDetailSentiment.text = label
        b.tvDetailSentiment.setBackgroundColor(color)

        // Thêm các gạch đầu dòng
        for (bullet in NewsCardPresentationMapper.formatBullets(bullets.toList())) {
            val tv = TextView(this).apply {
                text = "•  $bullet"
                textSize = 14f
                setTextColor(0xFF555555.toInt())
                setPadding(0, 8, 0, 8)
            }
            b.layoutBullets.addView(tv)
        }
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUMMARY = "extra_summary"
        const val EXTRA_SOURCE = "extra_source"
        const val EXTRA_SENTIMENT = "extra_sentiment"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_BULLETS = "extra_bullets"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_LINK = "extra_link"
    }
}
