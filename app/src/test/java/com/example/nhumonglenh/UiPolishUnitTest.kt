package com.example.nhumonglenh

import com.example.nhumonglenh.ui.UiTextLocalizer
import com.example.nhumonglenh.ui.payment.PaymentItemFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UiPolishUnitTest {
    @Test
    fun recommendationCodes_areDisplayedInVietnamese() {
        assertEquals("MUA MẠNH", UiTextLocalizer.recommendation("STRONG_BUY"))
        assertEquals("MUA", UiTextLocalizer.recommendation("BUY"))
        assertEquals("GIỮ", UiTextLocalizer.recommendation("HOLD"))
        assertEquals("BÁN", UiTextLocalizer.recommendation("SELL"))
        assertEquals("BÁN MẠNH", UiTextLocalizer.recommendation("STRONG_SELL"))
    }

    @Test
    fun sentimentCodes_areDisplayedInVietnamese() {
        assertEquals("TÍCH CỰC", UiTextLocalizer.sentiment("bullish"))
        assertEquals("TIÊU CỰC", UiTextLocalizer.sentiment("bearish"))
        assertEquals("TRUNG LẬP", UiTextLocalizer.sentiment("neutral"))
    }

    @Test
    fun paymentStatuses_areDisplayedInVietnamese() {
        assertEquals("THÀNH CÔNG", PaymentItemFormatter.formatStatusLabel("SUCCEEDED"))
        assertEquals("ĐANG CHỜ", PaymentItemFormatter.formatStatusLabel("PENDING"))
        assertEquals("ĐANG XỬ LÝ", PaymentItemFormatter.formatStatusLabel("PROCESSING"))
        assertEquals("THẤT BẠI", PaymentItemFormatter.formatStatusLabel("FAILED"))
        assertEquals("ĐÃ HỦY", PaymentItemFormatter.formatStatusLabel("CANCELLED"))
    }

    @Test
    fun legacyForecastHeadings_areRemovedFromNarrative() {
        val localized = UiTextLocalizer.forecastNarrative(
            "Kế hoạch giao dịch (Action Plan): Chờ giá hồi về vùng hỗ trợ"
        )
        assertFalse(localized.contains("Action Plan", ignoreCase = true))
        assertEquals("Kế hoạch giao dịch: Chờ giá hồi về vùng hỗ trợ", localized)
    }
}
