package com.example.nhumonglenh.ui

import java.util.Locale

/** Maps protocol-level codes to consistent Vietnamese user-facing text. */
object UiTextLocalizer {
    fun recommendation(value: String?): String {
        val code = value?.trim()?.uppercase(Locale.ROOT)
        return when (code) {
            "STRONG_BUY", "STRONG BUY" -> "Xu hướng tăng mạnh"
            "BUY" -> "Xu hướng tăng"
            "HOLD" -> "Đi ngang"
            "SELL" -> "Xu hướng giảm"
            "STRONG_SELL", "STRONG SELL" -> "Xu hướng giảm mạnh"
            null, "" -> "Chưa có nhận định"
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
            .replace(" :", ":")
            .replace("Action Plan:", "Kịch bản tham khảo:", ignoreCase = true)
            .replace("Kế hoạch giao dịch:", "Kịch bản tham khảo:", ignoreCase = true)
            .replace("Market Sentiment:", "Bối cảnh thị trường:", ignoreCase = true)
            .replace("Tâm lý thị trường:", "Bối cảnh thị trường:", ignoreCase = true)
            .trim()
    }
}
