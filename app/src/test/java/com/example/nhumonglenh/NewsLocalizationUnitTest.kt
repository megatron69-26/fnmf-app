package com.example.nhumonglenh

import com.example.nhumonglenh.data.local.NewsEntity
import com.example.nhumonglenh.ui.news.News
import com.example.nhumonglenh.ui.news.NewsCardPresentationMapper
import com.example.nhumonglenh.ui.news.NewsLocalizationPolicy
import org.junit.Assert.*
import org.junit.Test

class NewsLocalizationUnitTest {

    @Test
    fun testHasVietnameseCharacteristics_detectsDiacriticsAndTokens() {
        assertTrue(NewsLocalizationPolicy.hasVietnameseCharacteristics("Thị trường chứng khoán phục hồi mạnh"))
        assertTrue(NewsLocalizationPolicy.hasVietnameseCharacteristics("Apple công bố kết quả kinh doanh quý"))
        assertTrue(NewsLocalizationPolicy.hasVietnameseCharacteristics("cổ phiếu VinFast tăng"))
        assertFalse(NewsLocalizationPolicy.hasVietnameseCharacteristics("Apple reports record quarterly earnings"))
        assertFalse(NewsLocalizationPolicy.hasVietnameseCharacteristics("Federal Reserve holds interest rate unchanged"))
        assertFalse(NewsLocalizationPolicy.hasVietnameseCharacteristics(""))
        assertFalse(NewsLocalizationPolicy.hasVietnameseCharacteristics(null))
    }

    @Test
    fun testIsLikelyEnglish_detectsEnglishSentences() {
        assertTrue(NewsLocalizationPolicy.isLikelyEnglish("Federal Reserve holds interest rate unchanged"))
        assertTrue(NewsLocalizationPolicy.isLikelyEnglish("Tesla stock drops amid global market selloff"))
        assertFalse(NewsLocalizationPolicy.isLikelyEnglish("Ngân hàng trung ương giữ nguyên lãi suất"))
        assertFalse(NewsLocalizationPolicy.isLikelyEnglish(null))
    }

    @Test
    fun testIsValidDisplayTitleVi_rejectsEnglishAndIdenticalTitles() {
        val orig = "Apple reports strong iPhone sales in latest quarter"
        assertFalse(NewsLocalizationPolicy.isValidDisplayTitleVi(orig, orig))
        assertFalse(NewsLocalizationPolicy.isValidDisplayTitleVi("Apple reports strong iPhone sales in latest quarter", orig))
        assertFalse(NewsLocalizationPolicy.isValidDisplayTitleVi("", orig))
        assertFalse(NewsLocalizationPolicy.isValidDisplayTitleVi(null, orig))

        assertTrue(NewsLocalizationPolicy.isValidDisplayTitleVi("Apple ghi nhận doanh số iPhone tăng mạnh trong quý mới nhất", orig))
    }

    @Test
    fun testIsValidDisplaySummaryVi_rejectsEnglishAndTitleDuplication() {
        val origSummary = "The Federal Reserve announced on Wednesday that benchmark interest rates will remain stable."
        val titleVi = "Cục Dự trữ Liên bang giữ nguyên lãi suất cơ bản"

        assertFalse(NewsLocalizationPolicy.isValidDisplaySummaryVi(origSummary, origSummary, titleVi))
        assertFalse(NewsLocalizationPolicy.isValidDisplaySummaryVi(titleVi, origSummary, titleVi))
        assertFalse(NewsLocalizationPolicy.isValidDisplaySummaryVi("Bài viết này được tạo bởi AI tổng hợp", origSummary, titleVi))
        assertFalse(NewsLocalizationPolicy.isValidDisplaySummaryVi("", origSummary, titleVi))
        assertFalse(NewsLocalizationPolicy.isValidDisplaySummaryVi(null, origSummary, titleVi))

        assertTrue(NewsLocalizationPolicy.isValidDisplaySummaryVi(
            "Cục Dự trữ Liên bang thông báo giữ nguyên mức lãi suất hiện tại nhằm hỗ trợ nền kinh tế.",
            origSummary,
            titleVi
        ))
    }

    @Test
    fun testIsValidBullets_validatesCountAndVietnamese() {
        val titleVi = "Cổ phiếu Nvidia lập đỉnh lịch sử mới"

        val validBullets = listOf(
            "Doanh thu mảng trung tâm dữ liệu tăng trưởng vượt kỳ vọng",
            "Nhu cầu chip trí tuệ nhân tạo tiếp tục duy trì ở mức cao",
            "Các nhà phân tích phố Wall nâng mức giá mục tiêu cho cổ phiếu"
        )
        assertTrue(NewsLocalizationPolicy.isValidBullets(validBullets, titleVi))

        val oneBullet = listOf("Doanh thu mảng trung tâm dữ liệu tăng trưởng vượt kỳ vọng")
        assertFalse(NewsLocalizationPolicy.isValidBullets(oneBullet, titleVi))

        val fiveBullets = listOf(
            "Ý thứ nhất về thị trường chip AI",
            "Ý thứ hai về trung tâm dữ liệu",
            "Ý thứ ba về phố Wall",
            "Ý thứ tư về triển vọng quý tới",
            "Ý thứ năm vượt quá giới hạn tối đa"
        )
        assertFalse(NewsLocalizationPolicy.isValidBullets(fiveBullets, titleVi))

        val englishBullets = listOf(
            "Revenue surged 122% year over year",
            "Data center business continues to show exceptional growth"
        )
        assertFalse(NewsLocalizationPolicy.isValidBullets(englishBullets, titleVi))

        val bulletDuplicatingTitle = listOf(
            "Cổ phiếu Nvidia lập đỉnh lịch sử mới",
            "Doanh thu mảng trung tâm dữ liệu tăng trưởng vượt kỳ vọng"
        )
        assertFalse(NewsLocalizationPolicy.isValidBullets(bulletDuplicatingTitle, titleVi))
    }

    @Test
    fun testIsEntityFullyLocalized_validatesAllAspects() {
        val validEntity = NewsEntity(
            "news-1",
            "Nvidia đạt mốc vốn hóa kỷ lục mới",
            "https://example.com/nvda",
            1725800000000L,
            "Reuters",
            "Nguyễn Văn A",
            "2026-09-08T10:00:00Z",
            "https://example.com/img.jpg",
            "Tập đoàn công nghệ Nvidia vừa thiết lập mốc giá trị vốn hóa cao nhất lịch sử.",
            "bullish",
            90,
            "Doanh số chip AI tăng mạnh\nTriển vọng quý 3 khả quan",
            "Nvidia hits new all-time high valuation",
            "Nvidia has reached a record market capitalization following surging AI demand.",
            "Nvidia đạt mốc vốn hóa kỷ lục mới",
            "Tập đoàn công nghệ Nvidia vừa thiết lập mốc giá trị vốn hóa cao nhất lịch sử.",
            "Doanh số chip AI tăng mạnh\nTriển vọng quý 3 khả quan",
            "Reuters"
        )
        assertTrue(NewsLocalizationPolicy.isEntityFullyLocalized(validEntity))

        val legacyEnglishEntity = NewsEntity(
            "news-2",
            "Federal Reserve holds interest rates",
            "https://example.com/fed",
            1725800000000L,
            "Tin thị trường",
            "Staff",
            "2026-09-08T10:00:00Z",
            "",
            "The central bank kept interest rates steady on Wednesday.",
            "neutral",
            75,
            "Interest rates remain stable\nInflation cools down",
            "",
            "",
            "",
            "",
            "",
            ""
        )
        assertFalse(NewsLocalizationPolicy.isEntityFullyLocalized(legacyEnglishEntity))
    }

    @Test
    fun testNewsEffectiveGetters_prioritizeVietnameseFields() {
        val news = News(
            id = "1",
            title = "Legacy Title",
            source = "Legacy Source",
            publishedAt = "2026-09-08",
            imageUrl = "https://example.com/apple.jpg",
            summary = "Legacy English Summary",
            sentiment = "bullish",
            confidence = 88,
            bulletPoints = listOf("Legacy Bullet 1", "Legacy Bullet 2"),
            originalTitle = "Apple stock climbs on iPhone demand",
            originalSummary = "Shares of Apple rose 3% in premarket trading.",
            displayTitleVi = "Cổ phiếu Apple tăng nhờ sức mua iPhone khả quan",
            displaySummaryVi = "Giá cổ phiếu Apple ghi nhận mức tăng tích cực trong phiên giao dịch sớm.",
            bulletPointsVi = listOf("Sức mua iPhone tăng tại các thị trường chính", "Lợi nhuận mảng dịch vụ tiếp tục mở rộng"),
            publisher = "Bloomberg"
        )

        assertEquals("Cổ phiếu Apple tăng nhờ sức mua iPhone khả quan", news.getEffectiveTitle())
        assertEquals("Giá cổ phiếu Apple ghi nhận mức tăng tích cực trong phiên giao dịch sớm.", news.getEffectiveSummary())
        assertEquals("Bloomberg", news.getEffectivePublisher())
        assertEquals(2, news.getEffectiveBullets().size)
        assertEquals("Sức mua iPhone tăng tại các thị trường chính", news.getEffectiveBullets()[0])
    }

    @Test
    fun testRejectsEnglishTitleWithoutVietnameseDiacritics() {
        val englishTitle = "Nvidia reports quarterly revenue and earnings growth"
        val origTitle = "Nvidia reports quarterly revenue and earnings growth"
        assertFalse(NewsLocalizationPolicy.isValidDisplayTitleVi(englishTitle, origTitle))
    }

    @Test
    fun testAcceptsProperNamesAndBusinessAllowlist() {
        val origTitle1 = "Nvidia reports strong quarterly revenue"
        val viTitle1 = "Nvidia công bố doanh thu quý tăng trưởng mạnh"
        assertTrue(NewsLocalizationPolicy.isValidDisplayTitleVi(viTitle1, origTitle1))

        val origTitle2 = "Bitcoin ETFs record 500 million USD in capital inflows"
        val viTitle2 = "Bitcoin ETF ghi nhận dòng vốn 500 triệu USD"
        assertTrue(NewsLocalizationPolicy.isValidDisplayTitleVi(viTitle2, origTitle2))
    }

    @Test
    fun testRejectsMixedEnglishBullets() {
        val viTitle = "Nvidia công bố doanh thu quý tăng trưởng mạnh"
        val mixedBullets = listOf(
            "Doanh thu data center surged 122% year over year",
            "Nhu cầu chip trí tuệ nhân tạo tiếp tục ở mức cao"
        )
        assertFalse(NewsLocalizationPolicy.isValidBullets(mixedBullets, viTitle))
    }

    @Test
    fun testOptionalPublisherHandling() {
        val newsWithoutPublisher = News(
            id = "news-opt",
            title = "Cổ phiếu VinFast tăng trưởng ấn tượng trong phiên",
            summary = "Cổ phiếu VinFast vừa ghi nhận phiên giao dịch bứt phá mạnh mẽ tại sàn quốc tế.",
            source = "",
            publishedAt = "08/09/2026",
            imageUrl = "",
            sentiment = "bullish",
            confidence = 90,
            bulletPoints = listOf(
                "Khối lượng giao dịch tăng gấp đôi so với trung bình 20 ngày",
                "Dòng tiền ngoại quay trở lại mua ròng tích cực"
            ),
            originalTitle = "VinFast shares surge in heavy trading",
            originalSummary = "VinFast shares rallied strongly on high volume.",
            displayTitleVi = "Cổ phiếu VinFast tăng trưởng ấn tượng trong phiên",
            displaySummaryVi = "Cổ phiếu VinFast vừa ghi nhận phiên giao dịch bứt phá mạnh mẽ tại sàn quốc tế.",
            bulletPointsVi = listOf(
                "Khối lượng giao dịch tăng gấp đôi so với trung bình 20 ngày",
                "Dòng tiền ngoại quay trở lại mua ròng tích cực"
            ),
            publisher = null
        )
        // Publisher null/rỗng vẫn được giữ và hợp lệ
        assertTrue(NewsLocalizationPolicy.isNewsFullyLocalized(newsWithoutPublisher))

        // Publisher generic không làm loại bài (vẫn giữ bài để phục vụ bạn đọc)
        val newsWithGenericPub = newsWithoutPublisher.copy(publisher = "Financial News")
        assertTrue(NewsLocalizationPolicy.isNewsFullyLocalized(newsWithGenericPub))

        // Presentation mapper sẽ loại bỏ publisher generic và chỉ hiển thị ngày đăng hoặc ẩn publisher
        val resolvedPub = NewsCardPresentationMapper.resolvePublisher(newsWithGenericPub.publisher, null)
        assertNull(resolvedPub)

        // Publisher uy tín được chấp nhận và hiển thị đầy đủ
        val newsWithMarketBeat = newsWithoutPublisher.copy(publisher = "MarketBeat")
        assertTrue(NewsLocalizationPolicy.isNewsFullyLocalized(newsWithMarketBeat))
        assertEquals("MarketBeat", NewsCardPresentationMapper.resolvePublisher(newsWithMarketBeat.publisher, null))
    }
}
