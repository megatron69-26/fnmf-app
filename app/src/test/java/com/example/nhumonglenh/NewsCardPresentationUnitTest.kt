package com.example.nhumonglenh

import com.example.nhumonglenh.ui.news.NewsCardPresentationMapper
import org.junit.Assert.*
import org.junit.Test

class NewsCardPresentationUnitTest {

    @Test
    fun testShouldShowAuthor_falseWhenNullOrBlank() {
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor(null, "Reuters"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("", "Reuters"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("   ", "Reuters"))
    }

    @Test
    fun testShouldShowAuthor_falseWhenMatchesSource() {
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("CNBC", "CNBC"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("cnbc", "CNBC"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("  MarketBeat  ", "marketbeat"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("Bloomberg", "bloomberg"))
    }

    @Test
    fun testShouldShowAuthor_falseWhenGenericPlaceholder() {
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("Tin thị trường", "VnExpress"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("Financial News", "Yahoo Finance"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("Tổng hợp", "CafeF"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("market news", "Reuters"))
        assertFalse(NewsCardPresentationMapper.shouldShowAuthor("none", "Bloomberg"))
    }

    @Test
    fun testShouldShowAuthor_trueWhenValidAuthor() {
        assertTrue(NewsCardPresentationMapper.shouldShowAuthor("John Doe", "Reuters"))
        assertTrue(NewsCardPresentationMapper.shouldShowAuthor("Nguyễn Văn A", "VnExpress"))
    }

    @Test
    fun testFormatAuthor_returnsFormattedOrNull() {
        assertNull(NewsCardPresentationMapper.formatAuthor(null, "CNBC"))
        assertNull(NewsCardPresentationMapper.formatAuthor("CNBC", "CNBC"))
        assertNull(NewsCardPresentationMapper.formatAuthor("Tin thị trường", "VnExpress"))
        assertEquals("Tác giả: Warren Buffett", NewsCardPresentationMapper.formatAuthor("Warren Buffett", "CNBC"))
    }

    @Test
    fun testFormatBullets_cleansBulletsAndLimitsCount() {
        val raw = listOf(
            "•  Điểm thứ nhất về thị trường",
            "- Điểm thứ hai về lạm phát",
            "*   Điểm thứ ba về lãi suất",
            "– Điểm thứ tư về cổ phiếu",
            "Điểm thứ năm bị bỏ qua"
        )
        val formatted = NewsCardPresentationMapper.formatBullets(raw, maxBullets = 4)
        assertEquals(4, formatted.size)
        assertEquals("Điểm thứ nhất về thị trường", formatted[0])
        assertEquals("Điểm thứ hai về lạm phát", formatted[1])
        assertEquals("Điểm thứ ba về lãi suất", formatted[2])
        assertEquals("Điểm thứ tư về cổ phiếu", formatted[3])
    }

    @Test
    fun testFormatBullets_handlesNullOrEmpty() {
        assertTrue(NewsCardPresentationMapper.formatBullets(null).isEmpty())
        assertTrue(NewsCardPresentationMapper.formatBullets(emptyList()).isEmpty())
        assertTrue(NewsCardPresentationMapper.formatBullets(listOf("  ", "\n")).isEmpty())
    }

    @Test
    fun testFormatReleaseDate_parsesVariousFormats() {
        assertEquals("08/09/2026", NewsCardPresentationMapper.formatReleaseDate("20260908T031438"))
        assertEquals("08/09/2026", NewsCardPresentationMapper.formatReleaseDate("2026-09-08T03:14:38Z"))
        assertEquals("08/09/2026", NewsCardPresentationMapper.formatReleaseDate("2026-09-08"))
        assertEquals("08/09/2026", NewsCardPresentationMapper.formatReleaseDate("20260908"))
        assertEquals("--", NewsCardPresentationMapper.formatReleaseDate(null))
        assertEquals("--", NewsCardPresentationMapper.formatReleaseDate(""))
    }
}
