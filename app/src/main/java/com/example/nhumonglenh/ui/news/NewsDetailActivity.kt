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
        SystemBarInsets.apply(this, findViewById(android.R.id.content))

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        val sentiment = intent.getStringExtra(EXTRA_SENTIMENT) ?: "neutral"
        val confidence = intent.getIntExtra(EXTRA_CONFIDENCE, 0)
        val bullets = intent.getStringArrayExtra(EXTRA_BULLETS) ?: arrayOf()

        val author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
        val link = intent.getStringExtra(EXTRA_LINK) ?: ""

        val publisher = NewsCardPresentationMapper.resolvePublisher(source, link)

        b.tvDetailTitle.text = title
        b.tvDetailTitle.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_PRIMARY)
        b.tvDetailSummary.text = summary
        if (!publisher.isNullOrBlank()) {
            b.tvDetailSource.text = "Nguồn: $publisher"
            b.tvDetailSource.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)
            b.tvDetailSource.visibility = android.view.View.VISIBLE
        } else {
            b.tvDetailSource.visibility = android.view.View.GONE
        }
        b.tvDetailConfidence.text = NewsCardPresentationMapper.formatConfidence(confidence)
        b.tvDetailConfidence.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)

        val formattedAuthor = NewsCardPresentationMapper.formatAuthor(author, source)
        if (formattedAuthor != null) {
            b.tvDetailAuthor.text = formattedAuthor
            b.tvDetailAuthor.setTextColor(NewsCardPresentationMapper.COLOR_TEXT_SECONDARY)
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

        val label = NewsCardPresentationMapper.formatSentimentLabel(sentiment)
        val bgColor = NewsCardPresentationMapper.getSentimentBackgroundColor(sentiment)
        val textColor = NewsCardPresentationMapper.getSentimentTextColor(sentiment)
        b.tvDetailSentiment.text = label
        b.tvDetailSentiment.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
        b.tvDetailSentiment.setTextColor(textColor)

        // Thêm các gạch đầu dòng
        b.layoutBullets.removeAllViews()
        for (bullet in NewsCardPresentationMapper.formatBullets(bullets.toList())) {
            val tv = TextView(this).apply {
                text = "•  $bullet"
                textSize = 14f
                setTextColor(NewsCardPresentationMapper.COLOR_TEXT_PRIMARY)
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
