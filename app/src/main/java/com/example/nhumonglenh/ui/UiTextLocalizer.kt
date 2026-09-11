package com.example.nhumonglenh.ui

import java.util.Locale

/** Maps protocol-level codes to consistent Vietnamese user-facing text. */
object UiTextLocalizer {
    fun recommendation(value: String?): String {
        val code = value?.trim()?.uppercase(Locale.ROOT)
        return when (code) {
            "STRONG_BUY", "STRONG BUY" -> "MUA MẠNH"
            "BUY" -> "MUA"
            "HOLD" -> "GIỮ"
            "SELL" -> "BÁN"
            "STRONG_SELL", "STRONG SELL" -> "BÁN MẠNH"
            null, "" -> "Chưa có khuyến nghị"
            else -> value.trim()
        }
    }

    fun sentiment(value: String?): String {
        return when (value?.trim()?.lowercase(Locale.ROOT)) {
            "bullish" -> "TÍCH CỰC"
            "bearish" -> "TIÊU CỰC"
            else -> "TRUNG LẬP"
        }
    }

    fun forecastNarrative(value: String?): String {
        if (value.isNullOrBlank()) return "Đang cập nhật..."
        return value
            .replace("(Action Plan)", "", ignoreCase = true)
            .replace("Action Plan:", "Kế hoạch giao dịch:", ignoreCase = true)
            .replace("Market Sentiment:", "Tâm lý thị trường:", ignoreCase = true)
            .replace(" :", ":")
            .trim()
    }
}
