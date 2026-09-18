package com.example.nhumonglenh.ui.ticker

import com.example.nhumonglenh.data.remote.MarketPriceDto
import com.example.nhumonglenh.ui.trading.PriceFormatter
import java.util.Locale

data class MarketTickerUiModel(
    val symbol: String,
    val displayName: String,
    val price: Double?,
    val change24h: Double?,
    val isStale: Boolean
)

object MarketTickerPolicy {

    /**
     * 8 mã cố định hiển thị trên Ticker theo đúng thứ tự yêu cầu:
     * BTCUSDT, ETHUSDT, XAUUSD, BNBUSDT, SOLUSDT, XRPUSDT, ADAUSDT, DOGEUSDT.
     */
    val CANONICAL_SYMBOLS = listOf(
        "BTCUSDT",
        "ETHUSDT",
        "XAUUSD",
        "BNBUSDT",
        "SOLUSDT",
        "XRPUSDT",
        "ADAUSDT",
        "DOGEUSDT"
    )

    fun resolveDisplayName(symbol: String): String {
        return when (symbol.trim().uppercase(Locale.ROOT)) {
            "BTCUSDT" -> "BTC"
            "ETHUSDT" -> "ETH"
            "XAUUSD" -> "XAU"
            "BNBUSDT" -> "BNB"
            "SOLUSDT" -> "SOL"
            "XRPUSDT" -> "XRP"
            "ADAUSDT" -> "ADA"
            "DOGEUSDT" -> "DOGE"
            else -> symbol.replace("/", "").removeSuffix("USDT").removeSuffix("USD")
        }
    }

    fun formatPrice(price: Double?): String {
        if (price == null || price <= 0.0) return "—"
        return PriceFormatter.formatPrice(price)
    }

    fun formatChange(change24h: Double?): String {
        if (change24h == null) return "—"
        return if (change24h >= 0.0) {
            String.format(Locale.US, "+%.2f%%", change24h)
        } else {
            String.format(Locale.US, "%.2f%%", change24h)
        }
    }

    fun resolveChangeColor(change24h: Double?): Int {
        return when {
            change24h == null -> 0xFF787B86.toInt() // Màu xám trung tính TradingView
            change24h >= 0.0 -> 0xFF089981.toInt()  // Xanh lá TradingView (+ hoặc 0.00%)
            else -> 0xFFF23645.toInt()             // Đỏ TradingView
        }
    }

    fun createDefaultTickerList(): List<MarketTickerUiModel> {
        return CANONICAL_SYMBOLS.map { sym ->
            MarketTickerUiModel(
                symbol = sym,
                displayName = resolveDisplayName(sym),
                price = null,
                change24h = null,
                isStale = false
            )
        }
    }

    /**
     * Hợp nhất dữ liệu mới từ GET /api/market/prices với snapshot trước đó theo từng trường (per-field):
     * - price mới chỉ thay thế khi khác null và > 0.
     * - change24h mới chỉ thay thế khi khác null.
     * - Trường thiếu giữ lại giá trị trước đó để chống mất snapshot hợp lệ.
     * - Đánh dấu isStale=true nếu response thiếu trường (price thiếu/<=0 hoặc change24h thiếu) hoặc latestDto.stale=true hoặc lỗi mạng.
     * - Tuyệt đối không biến null thành 0 hay 0.00%.
     */
    fun mergeTickerData(
        previous: List<MarketTickerUiModel>?,
        latestPrices: List<MarketPriceDto>?
    ): List<MarketTickerUiModel> {
        val prevMap = previous?.associateBy { it.symbol.uppercase(Locale.ROOT) } ?: emptyMap()
        val latestMap = latestPrices?.associateBy { it.symbol.uppercase(Locale.ROOT) } ?: emptyMap()

        return CANONICAL_SYMBOLS.map { sym ->
            val cleanSym = sym.uppercase(Locale.ROOT)
            val latestDto = latestMap[cleanSym]
            val prevItem = prevMap[cleanSym]

            // 1. price mới chỉ thay thế khi khác null và > 0
            val hasValidNewPrice = latestDto?.price != null && latestDto.price > 0.0
            val resolvedPrice = if (hasValidNewPrice) {
                latestDto!!.price
            } else {
                prevItem?.price
            }

            // 2. change24h mới chỉ thay thế khi khác null
            val hasValidNewChange = latestDto?.change24h != null
            val resolvedChange = if (hasValidNewChange) {
                latestDto!!.change24h
            } else {
                prevItem?.change24h
            }

            // 3. Đánh dấu stale nếu response thiếu trường, latestDto.stale=true, hoặc mất kết nối
            val isMissingFieldInDto = latestDto != null && (!hasValidNewPrice || !hasValidNewChange)
            val isStale = when {
                latestDto == null -> (prevItem != null && (prevItem.price != null || prevItem.change24h != null))
                latestDto.stale == true -> true
                isMissingFieldInDto -> true
                else -> false
            }

            MarketTickerUiModel(
                symbol = sym,
                displayName = resolveDisplayName(sym),
                price = resolvedPrice,
                change24h = resolvedChange,
                isStale = isStale
            )
        }
    }
}
