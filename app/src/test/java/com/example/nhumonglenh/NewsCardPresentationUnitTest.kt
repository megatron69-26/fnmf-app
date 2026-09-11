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

    @Test
    fun testResolvePublisher_resolvesDomainsCorrectly() {
        assertEquals("MarketBeat", NewsCardPresentationMapper.resolvePublisher("Unknown", "https://www.marketbeat.com/stocks/NASDAQ/NVDA/news/"))
        assertEquals("Yahoo Finance", NewsCardPresentationMapper.resolvePublisher("Tin thị trường", "https://finance.yahoo.com/news/article-123.html"))
        assertEquals("CNBC", NewsCardPresentationMapper.resolvePublisher(null, "https://www.cnbc.com/2026/09/08/us-markets.html"))
        assertEquals("TipRanks", NewsCardPresentationMapper.resolvePublisher("", "https://www.tipranks.com/news/article-abc"))
        assertEquals("24/7 Wall St.", NewsCardPresentationMapper.resolvePublisher(null, "https://247wallst.com/investing/2026/09/08/stock-pick/"))
        assertEquals("The Motley Fool", NewsCardPresentationMapper.resolvePublisher("none", "https://www.fool.com/investing/2026/09/08/crypto/"))
    }

    @Test
    fun testResolvePublisher_handlesSourceAndRejectsGeneric() {
        assertEquals("MarketBeat", NewsCardPresentationMapper.resolvePublisher("MarketBeat", null))
        assertEquals("The Wall Street Journal", NewsCardPresentationMapper.resolvePublisher("The Wall Street Journal", null))
        assertNull(NewsCardPresentationMapper.resolvePublisher("Tin thị trường", null))
        assertNull(NewsCardPresentationMapper.resolvePublisher("Financial News", null))
        assertNull(NewsCardPresentationMapper.resolvePublisher("Unknown", null))
        assertNull(NewsCardPresentationMapper.resolvePublisher(null, null))
        assertNull(NewsCardPresentationMapper.resolvePublisher("", ""))
        assertEquals("techcrunch.com", NewsCardPresentationMapper.resolvePublisher(null, "https://techcrunch.com/2026/09/08/article/"))
        assertEquals("CoinDesk", NewsCardPresentationMapper.resolvePublisher("Tin thị trường", "https://www.coindesk.com/markets/2026/09/08/btc/"))
    }

    @Test
    fun testResolvePublisher_neverReturnsForbiddenPlaceholdersWhenGenericOrNull() {
        val forbidden = setOf("MarketBeat", "Financial News", "Tin thị trường", "Unknown", "Thị trường tài chính")
        val result1 = NewsCardPresentationMapper.resolvePublisher("Tin thị trường", null)
        val result2 = NewsCardPresentationMapper.resolvePublisher(null, null)
        val result3 = NewsCardPresentationMapper.resolvePublisher("Financial News", "invalid-url")
        assertFalse(forbidden.contains(result1))
        assertFalse(forbidden.contains(result2))
        assertFalse(forbidden.contains(result3))
        assertNull(result1)
        assertNull(result2)
        assertNull(result3)
    }

    @Test
    fun testExtractCleanHost() {
        assertEquals("techcrunch.com", NewsCardPresentationMapper.extractCleanHost("https://www.techcrunch.com/item/1"))
        assertEquals("bloomberg.com", NewsCardPresentationMapper.extractCleanHost("http://bloomberg.com/news"))
        assertNull(NewsCardPresentationMapper.extractCleanHost(null))
        assertNull(NewsCardPresentationMapper.extractCleanHost("not-a-url"))
    }

    @Test
    fun testFormatMetadata_formatsPublisherAndDate() {
        assertEquals(
            "MarketBeat · 08/09/2026",
            NewsCardPresentationMapper.formatMetadata("MarketBeat", "20260908T031438", null)
        )
        assertEquals(
            "Yahoo Finance · 08/09/2026",
            NewsCardPresentationMapper.formatMetadata("Tin thị trường", "2026-09-08", "https://finance.yahoo.com/news/1")
        )
        assertEquals(
            "CNBC",
            NewsCardPresentationMapper.formatMetadata("CNBC", null, null)
        )
        assertEquals(
            "08/09/2026",
            NewsCardPresentationMapper.formatMetadata("Tin thị trường", "2026-09-08", null)
        )
        assertEquals(
            "",
            NewsCardPresentationMapper.formatMetadata(null, null, null)
        )
    }

    @Test
    fun testFormatConfidence_formatsProperly() {
        assertEquals("Tin cậy 87%", NewsCardPresentationMapper.formatConfidence(87))
        assertEquals("Tin cậy 95%", NewsCardPresentationMapper.formatConfidence(95))
        assertEquals("Tin cậy 0%", NewsCardPresentationMapper.formatConfidence(0))
    }

    @Test
    fun testSentimentFormattingAndColors() {
        assertEquals("Tích cực", NewsCardPresentationMapper.formatSentimentLabel("bullish"))
        assertEquals("Tiêu cực", NewsCardPresentationMapper.formatSentimentLabel("bearish"))
        assertEquals("Trung lập", NewsCardPresentationMapper.formatSentimentLabel("neutral"))
        assertEquals("Trung lập", NewsCardPresentationMapper.formatSentimentLabel(null))

        assertEquals(NewsCardPresentationMapper.COLOR_BULLISH, NewsCardPresentationMapper.getSentimentBackgroundColor("bullish"))
        assertEquals(NewsCardPresentationMapper.COLOR_BEARISH, NewsCardPresentationMapper.getSentimentBackgroundColor("bearish"))
        assertEquals(NewsCardPresentationMapper.COLOR_NEUTRAL_BG, NewsCardPresentationMapper.getSentimentBackgroundColor("neutral"))

        assertEquals(NewsCardPresentationMapper.COLOR_WHITE, NewsCardPresentationMapper.getSentimentTextColor("bullish"))
        assertEquals(NewsCardPresentationMapper.COLOR_WHITE, NewsCardPresentationMapper.getSentimentTextColor("bearish"))
        assertEquals(NewsCardPresentationMapper.COLOR_NEUTRAL_TEXT, NewsCardPresentationMapper.getSentimentTextColor("neutral"))
    }
}
