package com.example.nhumonglenh.ui.news

import com.example.nhumonglenh.data.local.NewsEntity
import java.util.Locale
import java.util.regex.Pattern

object NewsLocalizationPolicy {

    private val VIETNAMESE_DIACRITICS = Pattern.compile(
        "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđÀÁẠẢÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐÃ]"
    )

    private val VIETNAMESE_FINANCIAL_TOKENS = Pattern.compile(
        "\\b(cổ phiếu|thị trường|chứng khoán|doanh thu|lợi nhuận|nhà đầu tư|tăng trưởng|hạ lãi suất|tăng lãi suất|lạm phát|suy thoái|giảm mạnh|tăng mạnh|tín hiệu|kỷ lục|dự báo|giá vàng|tiền điện tử|mở vị thế|nắm giữ|đầu tư|giải ngân|mua lại|sáp nhập|quỹ đầu tư|phiên giao dịch)\\b",
        Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
    )

    private val TICKER_OR_ACRONYM = Pattern.compile("^[A-Z0-9]{2,6}$")

    private val ALLOWED_PROPER_NAMES = setOf(
        "nvidia", "apple", "microsoft", "tesla", "google", "meta", "amazon", "intel", "amd",
        "qualcomm", "broadcom", "netflix", "openai", "vinfast", "vingroup", "warren", "buffett",
        "elon", "musk", "tim", "cook", "jensen", "huang", "powell", "jerome",
        "bitcoin", "ethereum", "binance", "coinbase", "tether", "solana", "ripple",
        "vnpay", "vietcombank", "techcombank", "mbbank", "fpt", "viettel",
        "wall", "street", "nasdaq", "dow", "jones", "sp500", "s&p"
    )

    private val ALLOWED_BUSINESS_TERMS = setOf(
        "btc", "eth", "usd", "vnd", "ai", "etf", "vnpay", "fed", "ceo", "cfo", "ipo", "gdp", "cpi", "sec", "fomc",
        "million", "billion", "trillion", "m", "b", "k"
    )

    private val COMMON_ENGLISH_WORDS = setOf(
        "report", "reports", "reported", "reporting",
        "quarterly", "quarter", "annual", "yearly",
        "revenue", "revenues", "earnings", "growth",
        "shares", "share", "stock", "stocks",
        "market", "markets", "price", "prices",
        "target", "targets", "upgrade", "downgrade", "upgrades", "downgrades",
        "investors", "investor", "trading", "trade", "trader", "traders",
        "rates", "rate", "interest", "inflation",
        "cuts", "cut", "hikes", "hike", "surges", "surge", "surged",
        "falls", "fall", "fell", "drops", "drop", "dropped",
        "rises", "rise", "rose", "risen", "jumps", "jump", "jumped",
        "analysts", "analyst", "beat", "beats", "miss", "misses",
        "guidance", "outlook", "sales", "sale",
        "dividend", "dividends", "rally", "slump",
        "gain", "gains", "loss", "losses", "profit", "profits", "profitable",
        "record", "high", "highs", "low", "lows",
        "and", "the", "of", "to", "in", "is", "are", "for", "on", "with", "by", "at", "from", "this", "that",
        "financial", "news", "company", "companies", "business", "fund", "funds",
        "holding", "holdings", "holds", "held", "unchanged", "policy", "meeting",
        "says", "said", "amid", "after", "before", "over", "under", "into", "about", "new",
        "crypto", "cryptocurrency", "blockchain",
        "etfs", "percent", "percentage"
    )

    private val BOILERPLATE_PATTERNS = listOf(
        "bài viết này được tạo bởi",
        "chúng tôi không chịu trách nhiệm",
        "vui lòng tham khảo ý kiến chuyên gia",
        "không phải lời khuyên đầu tư",
        "đọc thêm tại",
        "nguồn tin từ",
        "bản quyền thuộc về",
        "all rights reserved",
        "disclaimer"
    )

    fun hasExcessiveEnglishTokens(text: String?): Boolean {
        if (text.isNullOrBlank()) return false

        val cleaned = text.replace(Regex("[.,:;!?\"'()\\[\\]{}/*\\-–—]"), " ")
        val tokens = cleaned.split(Regex("\\s+"))

        var englishWordCount = 0
        var meaningfulTokens = 0

        for (rawToken in tokens) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue

            // Bỏ qua số và token chứa chữ số (ví dụ: 500, 100%, 2026, Q3)
            if (token.contains(Regex("\\d"))) continue

            // Bỏ qua ticker/viết hoa (ví dụ: NVDA, AAPL, BTC, USD)
            if (TICKER_OR_ACRONYM.matcher(token).matches()) continue

            val lower = token.lowercase(Locale.ROOT)

            // Bỏ qua allowlist tên riêng / nghiệp vụ
            if (ALLOWED_PROPER_NAMES.contains(lower) || ALLOWED_BUSINESS_TERMS.contains(lower)) {
                continue
            }

            meaningfulTokens++

            if (COMMON_ENGLISH_WORDS.contains(lower)) {
                englishWordCount++
            }
        }

        if (englishWordCount >= 2) {
            return true
        }

        if (meaningfulTokens > 0 && (englishWordCount.toDouble() / meaningfulTokens) >= 0.40) {
            return true
        }

        return false
    }

    fun hasVietnameseCharacteristics(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val clean = text.trim()
        if (VIETNAMESE_DIACRITICS.matcher(clean).find()) return true
        return VIETNAMESE_FINANCIAL_TOKENS.matcher(clean).find()
    }

    fun isLikelyEnglish(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        if (hasExcessiveEnglishTokens(text)) return true
        if (VIETNAMESE_DIACRITICS.matcher(text).find()) return false
        return hasExcessiveEnglishTokens(text) || text.contains(Regex("(?i)\\b(the|and|of|to|in|is|for|on|with|by|at|from|stock|stocks|shares|market|report|earnings|quarter|revenue|price)\\b"))
    }

    fun isBoilerplate(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase(Locale.ROOT)
        return BOILERPLATE_PATTERNS.any { lower.contains(it) }
    }

    fun isValidDisplayTitleVi(displayTitleVi: String?, originalTitle: String?): Boolean {
        if (displayTitleVi.isNullOrBlank()) return false
        val trimmedDisplay = displayTitleVi.trim()
        if (!hasVietnameseCharacteristics(trimmedDisplay)) return false
        if (hasExcessiveEnglishTokens(trimmedDisplay)) return false

        if (!originalTitle.isNullOrBlank()) {
            val trimmedOrig = originalTitle.trim()
            if (trimmedDisplay.equals(trimmedOrig, ignoreCase = true)) return false
            if (isLikelyEnglish(trimmedOrig) && trimmedDisplay.equals(trimmedOrig, ignoreCase = true)) return false
        }
        return true
    }

    fun isValidDisplaySummaryVi(
        displaySummaryVi: String?,
        originalSummary: String?,
        displayTitleVi: String?
    ): Boolean {
        if (displaySummaryVi.isNullOrBlank()) return false
        val trimmedSummary = displaySummaryVi.trim()
        if (!hasVietnameseCharacteristics(trimmedSummary)) return false
        if (hasExcessiveEnglishTokens(trimmedSummary)) return false

        if (!originalSummary.isNullOrBlank()) {
            val trimmedOrig = originalSummary.trim()
            if (trimmedSummary.equals(trimmedOrig, ignoreCase = true)) return false
            if (isLikelyEnglish(trimmedOrig) && trimmedSummary.equals(trimmedOrig, ignoreCase = true)) return false
        }
        if (!displayTitleVi.isNullOrBlank()) {
            if (trimmedSummary.equals(displayTitleVi.trim(), ignoreCase = true)) return false
        }
        if (isBoilerplate(trimmedSummary)) return false
        return true
    }

    fun isValidBullets(bullets: List<String>?, title: String?): Boolean {
        if (bullets == null || bullets.size < 2 || bullets.size > 4) return false
        for (b in bullets) {
            if (b.isBlank()) return false
            val cleanB = stripLeadingBullet(b)
            if (!hasVietnameseCharacteristics(cleanB)) return false
            if (hasExcessiveEnglishTokens(cleanB)) return false
            if (!title.isNullOrBlank() && cleanB.equals(title.trim(), ignoreCase = true)) return false
            if (isBoilerplate(cleanB)) return false
        }
        return true
    }

    private fun stripLeadingBullet(text: String): String {
        var s = text.trim()
        while (s.startsWith("•") || s.startsWith("-") || s.startsWith("*") || s.startsWith("–")) {
            s = s.substring(1).trim()
        }
        return s
    }

    fun isEntityFullyLocalized(entity: NewsEntity): Boolean {
        val effectiveTitle = if (entity.displayTitleVi.isNotBlank()) entity.displayTitleVi else entity.title
        val origTitle = if (entity.originalTitle.isNotBlank()) entity.originalTitle else ""
        if (!isValidDisplayTitleVi(effectiveTitle, origTitle)) return false

        val effectiveSummary = if (entity.displaySummaryVi.isNotBlank()) entity.displaySummaryVi else entity.summary
        val origSummary = if (entity.originalSummary.isNotBlank()) entity.originalSummary else ""
        if (!isValidDisplaySummaryVi(effectiveSummary, origSummary, effectiveTitle)) return false

        val rawBullets = if (entity.bulletPointsVi.isNotBlank()) {
            entity.bulletPointsVi.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        } else if (entity.bulletPoints.isNotBlank()) {
            entity.bulletPoints.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        if (!isValidBullets(rawBullets, effectiveTitle)) return false

        // Publisher là metadata tùy chọn: null/rỗng vẫn hợp lệ. Nếu có giá trị thì không được là generic.
        val pub = if (entity.publisher.isNotBlank()) entity.publisher else entity.source
        if (pub.isNotBlank() && NewsCardPresentationMapper.isGeneric(pub)) return false

        return true
    }

    fun isNewsFullyLocalized(news: News): Boolean {
        val title = news.getEffectiveTitle()
        val origTitle = news.originalTitle ?: ""
        if (!isValidDisplayTitleVi(title, origTitle)) return false

        val summary = news.getEffectiveSummary()
        val origSummary = news.originalSummary ?: ""
        if (!isValidDisplaySummaryVi(summary, origSummary, title)) return false

        val bullets = news.getEffectiveBullets()
        if (!isValidBullets(bullets, title)) return false

        // Publisher là metadata tùy chọn: null/rỗng vẫn hợp lệ. Nếu có giá trị thì không được là generic.
        val pub = news.getEffectivePublisher()
        if (pub.isNotBlank() && NewsCardPresentationMapper.isGeneric(pub)) return false

        return true
    }
}
