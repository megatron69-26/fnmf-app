package com.example.nhumonglenh.ui.news

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.nhumonglenh.databinding.ActivityNewsDetailBinding

class NewsDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityNewsDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityNewsDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: ""
        val sentiment = intent.getStringExtra(EXTRA_SENTIMENT) ?: "neutral"
        val confidence = intent.getIntExtra(EXTRA_CONFIDENCE, 0)
        val bullets = intent.getStringArrayExtra(EXTRA_BULLETS) ?: arrayOf()

        b.tvDetailTitle.text = title
        b.tvDetailSummary.text = summary
        b.tvDetailSource.text = "Nguồn: $source"
        b.tvDetailConfidence.text = "$confidence% tin cậy"

        val (label, color) = when (sentiment) {
            "bullish" -> "BULLISH" to Color.parseColor("#2E7D32")
            "bearish" -> "BEARISH" to Color.parseColor("#C62828")
            else      -> "NEUTRAL" to Color.parseColor("#F9A825")
        }
        b.tvDetailSentiment.text = label
        b.tvDetailSentiment.setBackgroundColor(color)

        // Thêm các gạch đầu dòng
        for (bullet in bullets) {
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
    }
}
