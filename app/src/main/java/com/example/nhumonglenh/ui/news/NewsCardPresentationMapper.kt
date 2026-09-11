package com.example.nhumonglenh.ui.news

import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale

object NewsCardPresentationMapper {

    const val COLOR_BULLISH = 0xFF089981.toInt()
    const val COLOR_BEARISH = 0xFFF23645.toInt()
    const val COLOR_NEUTRAL_BG = 0xFF2A2E39.toInt()
    const val COLOR_NEUTRAL_TEXT = 0xFFF39C12.toInt()
    const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    const val COLOR_TEXT_PRIMARY = 0xFFD1D4DC.toInt()
    const val COLOR_TEXT_SECONDARY = 0xFF787B86.toInt()
    const val COLOR_BLUE = 0xFF2962FF.toInt()

    private val GENERIC_AUTHORS = setOf(
        "tin thị trường",
        "financial news",
        "tổng hợp",
        "market news",
        "unknown",
        "n/a",
        "none",
        "thị trường"
    )

    private val GENERIC_PUBLISHERS = setOf(
        "tin thị trường",
        "financial news",
        "tổng hợp",
        "market news",
        "unknown",
        "n/a",
        "none",
        "thị trường"
    )

    fun resolveDomain(link: String?): String? {
        if (link.isNullOrBlank()) return null
        val host = runCatching {
            val uri = URI(link.trim())
            uri.host?.lowercase(Locale.ROOT)
        }.getOrNull() ?: return null

        return when {
            host.contains("marketbeat.com") -> "MarketBeat"
            host.contains("yahoo.com") -> "Yahoo Finance"
            host.contains("cnbc.com") -> "CNBC"
            host.contains("tipranks.com") -> "TipRanks"
            host.contains("247wallst.com") -> "24/7 Wall St."
            host.contains("fool.com") -> "The Motley Fool"
            host.contains("bloomberg.com") -> "Bloomberg"
            host.contains("reuters.com") -> "Reuters"
            host.contains("investors.com") -> "Investor's Business Daily"
            host.contains("benzinga.com") -> "Benzinga"
            host.contains("barrons.com") -> "Barron's"
            host.contains("wsj.com") -> "The Wall Street Journal"
            host.contains("coindesk.com") -> "CoinDesk"
            host.contains("cointelegraph.com") -> "CoinTelegraph"
            host.contains("thestreet.com") -> "TheStreet"
            host.contains("forbes.com") -> "Forbes"
            host.contains("marketwatch.com") -> "MarketWatch"
            host.contains("ft.com") -> "Financial Times"
            else -> null
        }
    }

    fun normalizePublisherName(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.contains("marketbeat") -> "MarketBeat"
            lower.contains("yahoo") -> "Yahoo Finance"
            lower.contains("cnbc") -> "CNBC"
            lower.contains("tipranks") -> "TipRanks"
            lower.contains("24/7 wall st") || lower.contains("247wallst") -> "24/7 Wall St."
            lower.contains("motley fool") -> "The Motley Fool"
            lower.contains("bloomberg") -> "Bloomberg"
            lower.contains("reuters") -> "Reuters"
            lower.contains("investor's business daily") || lower.contains("investors.com") -> "Investor's Business Daily"
            lower.contains("benzinga") -> "Benzinga"
            lower.contains("barron") -> "Barron's"
            lower.contains("wall street journal") || lower.contains("wsj") -> "The Wall Street Journal"
            lower.contains("coindesk") -> "CoinDesk"
            lower.contains("cointelegraph") -> "CoinTelegraph"
            lower.contains("thestreet") -> "TheStreet"
            lower.contains("forbes") -> "Forbes"
            lower.contains("marketwatch") -> "MarketWatch"
            else -> raw.trim()
        }
    }

    fun isGeneric(source: String?): Boolean {
        if (source.isNullOrBlank()) return true
        return GENERIC_PUBLISHERS.contains(source.trim().lowercase(Locale.ROOT))
    }

    fun extractCleanHost(link: String?): String? {
        if (link.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(link.trim())
            var host = uri.host?.lowercase(Locale.ROOT)?.trim() ?: return null
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            if (host.isNotBlank()) host else null
        }.getOrNull()
    }

    fun resolvePublisher(source: String?, link: String? = null): String? {
        // 1. Nếu source hợp lệ: dùng source
        if (!source.isNullOrBlank() && !isGeneric(source)) {
            return normalizePublisherName(source.trim())
        }

        // 2. Nếu source generic: suy ra từ hostname có mapping
        val linkPublisher = resolveDomain(link)
        if (!linkPublisher.isNullOrBlank()) {
            return linkPublisher
        }

        // 3. Nếu chưa có mapping: dùng hostname đã làm sạch
        val cleanHost = extractCleanHost(link)
        if (!cleanHost.isNullOrBlank()) {
            return cleanHost
        }

        // 4. Nếu URL không hợp lệ hoặc không có hostname: trả null/rỗng
        // Tuyệt đối không fallback về MarketBeat, Financial News, Tin thị trường hoặc Unknown
        return null
    }

    fun formatMetadata(source: String?, rawDate: String?, link: String? = null): String {
        val pub = resolvePublisher(source, link)
        val date = formatReleaseDate(rawDate)
        val hasPub = !pub.isNullOrBlank()
        val hasDate = date != "--" && date.isNotBlank()

        return when {
            hasPub && hasDate -> "$pub · $date"
            hasPub -> pub!!
            hasDate -> date
            else -> ""
        }
    }

    fun formatConfidence(confidence: Int): String {
        return "Tin cậy $confidence%"
    }

    fun formatSentimentLabel(sentiment: String?): String {
        return when (sentiment?.lowercase(Locale.ROOT)) {
            "bullish" -> "Tích cực"
            "bearish" -> "Tiêu cực"
            else -> "Trung lập"
        }
    }

    fun getSentimentBackgroundColor(sentiment: String?): Int {
        return when (sentiment?.lowercase(Locale.ROOT)) {
            "bullish" -> COLOR_BULLISH
            "bearish" -> COLOR_BEARISH
            else -> COLOR_NEUTRAL_BG
        }
    }

    fun getSentimentTextColor(sentiment: String?): Int {
        return when (sentiment?.lowercase(Locale.ROOT)) {
            "bullish", "bearish" -> COLOR_WHITE
            else -> COLOR_NEUTRAL_TEXT
        }
    }

    fun shouldShowAuthor(author: String?, source: String?): Boolean {
        if (author.isNullOrBlank()) return false
        val trimmedAuthor = author.trim()
        val lowerAuthor = trimmedAuthor.lowercase(Locale.ROOT)
        if (GENERIC_AUTHORS.contains(lowerAuthor)) return false
        if (!source.isNullOrBlank() && trimmedAuthor.equals(source.trim(), ignoreCase = true)) {
            return false
        }
        val resolved = resolvePublisher(source)
        if (resolved != null && trimmedAuthor.equals(resolved, ignoreCase = true)) {
            return false
        }
        return true
    }

    fun formatAuthor(author: String?, source: String? = null): String? {
        if (!shouldShowAuthor(author, source)) return null
        return "Tác giả: ${author!!.trim()}"
    }

    fun formatBullets(bullets: List<String>?, maxBullets: Int = 4): List<String> {
        if (bullets.isNullOrEmpty()) return emptyList()
        return bullets
            .asSequence()
            .map { it.trim() }
            .map { stripLeadingBullet(it) }
            .filter { it.isNotBlank() }
            .take(maxBullets)
            .toList()
    }

    private fun stripLeadingBullet(text: String): String {
        var s = text.trim()
        while (s.startsWith("•") || s.startsWith("-") || s.startsWith("*") || s.startsWith("–")) {
            s = s.substring(1).trim()
        }
        return s
    }

    fun formatReleaseDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return "--"
        return runCatching {
            val trimmed = rawDate.trim()
            val date = when {
                trimmed.contains("T") && !trimmed.contains("-") -> {
                    val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                    fmt.isLenient = false
                    fmt.parse(trimmed)
                }
                trimmed.contains("-") -> {
                    val clean = if (trimmed.contains("T")) trimmed.substringBefore("T") else trimmed
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    fmt.isLenient = false
                    fmt.parse(clean)
                }
                trimmed.length == 8 && trimmed.all { it.isDigit() } -> {
                    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
                    fmt.isLenient = false
                    fmt.parse(trimmed)
                }
                else -> null
            }
            if (date != null) {
                SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date)
            } else {
                "--"
            }
        }.getOrDefault("--")
    }
}
