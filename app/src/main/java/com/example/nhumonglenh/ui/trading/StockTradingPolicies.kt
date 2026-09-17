package com.example.nhumonglenh.ui.trading

import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.ui.news.NewsLocalizationPolicy
import java.util.Locale
import kotlin.math.abs

/**
 * Các quy tắc nghiệp vụ Đợt 2:
 * 1. Nhận diện cổ phiếu & kiểm tra tính hợp lệ của giá để đặt lệnh (chống stale=true, chống thiếu giá).
 * 2. Hiển thị báo cáo mới nhất: nếu title rỗng hoặc không thuần Việt nhưng URL có thật -> "Xem báo cáo mới nhất".
 * 3. Đồng bộ trạng thái Quan tâm / Đã quan tâm giữa 8 mã cổ phiếu và Watchlist.
 * 4. Định giá danh mục tài sản: hỗ trợ fullyValued=false hiển thị "—" cho giá trị chưa định giá.
 */
object StockTradePolicy {

    val SUPPORTED_STOCKS = setOf(
        "AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META", "GOOGL", "JPM"
    )

    fun isStock(rawSymbol: String?): Boolean {
        if (rawSymbol.isNullOrBlank()) return false
        val clean = rawSymbol.trim().uppercase(Locale.ROOT)
        return SUPPORTED_STOCKS.contains(clean)
    }

    /**
     * Khớp lệnh chỉ được bật khi giá tồn tại, lớn hơn 0 và stale=false.
     */
    fun isTradeEnabled(price: Double?, isStale: Boolean?): Boolean {
        if (price == null || price <= 0.0) return false
        if (isStale == true) return false
        return true
    }

    fun isTradeEnabled(isStock: Boolean, stale: Boolean?, currentPrice: Double?): Boolean {
        if (!isStock) {
            return currentPrice != null && currentPrice > 0.0
        }
        return isTradeEnabled(currentPrice, stale)
    }

    /**
     * Thông báo tiếng Việt an toàn khi stale=true hoặc thiếu giá.
     */
    fun resolveTradeStatusMessage(price: Double?, isStale: Boolean?): String? {
        if (isStale == true || price == null || price <= 0.0) {
            return "Giá thị trường tạm thời không khả dụng để đặt lệnh"
        }
        return null
    }

    fun resolveTradeStatusMessage(isStock: Boolean, stale: Boolean?, currentPrice: Double?): String? {
        if (!isStock) return null
        return resolveTradeStatusMessage(currentPrice, stale)
    }
}

object StockReportPolicy {

    const val DEFAULT_BUTTON_LABEL = "Xem báo cáo mới nhất"

    /**
     * Nếu latestReportTitle rỗng hoặc không phải tiếng Việt nhưng URL có thật,
     * chỉ hiện nhãn "Xem báo cáo mới nhất", không hiển thị tiêu đề tiếng Anh.
     */
    fun resolveReportButtonLabel(latestReportTitle: String?, latestReportUrl: String? = null): String {
        val title = latestReportTitle?.trim()
        if (!title.isNullOrBlank() && NewsLocalizationPolicy.hasVietnameseCharacteristics(title)) {
            return title
        }
        return DEFAULT_BUTTON_LABEL
    }

    fun shouldShowReportButton(latestReportUrl: String?): Boolean {
        return !latestReportUrl.isNullOrBlank()
    }
}

data class StockCatalogUiModel(
    val symbol: String,
    val name: String,
    val isWatchlisted: Boolean,
    val change24h: Double? = null,
    val price: Double? = null
)

object StockWatchlistMatcher {

    fun matchCatalogWithWatchlist(
        catalog: List<StockCatalogDto>,
        watchlist: List<WatchlistItemDto>,
        prices: List<com.example.nhumonglenh.data.remote.MarketPriceDto>? = null
    ): List<StockCatalogUiModel> {
        val watchlistedSymbols = watchlist.mapNotNull { it.symbol?.trim()?.uppercase(Locale.ROOT) }.toSet()
        val priceMap = prices?.associateBy { it.symbol.trim().uppercase(Locale.ROOT) } ?: emptyMap()
        return catalog.map { stock ->
            val clean = stock.symbol.trim().uppercase(Locale.ROOT)
            val mp = priceMap[clean]
            StockCatalogUiModel(
                symbol = clean,
                name = stock.name,
                isWatchlisted = watchlistedSymbols.contains(clean),
                change24h = mp?.change24h,
                price = mp?.price
            )
        }
    }

    fun toggleWatchlistAction(isCurrentlyWatchlisted: Boolean): String {
        return if (isCurrentlyWatchlisted) "Đã quan tâm" else "Quan tâm"
    }
}

object PortfolioValuationPolicy {

    const val UNVALUED_PLACEHOLDER = "—"

    fun isFullyValued(summary: PortfolioSummaryDto?): Boolean {
        if (summary == null) return false
        if (summary.fullyValued == false) return false
        if (summary.totalHoldingsValue == null) return false
        return true
    }

    fun formatTotalNetWorth(summary: PortfolioSummaryDto?): String {
        if (!isFullyValued(summary)) return UNVALUED_PLACEHOLDER
        val netWorth = summary?.totalNetWorth ?: return UNVALUED_PLACEHOLDER
        return String.format(Locale.US, "$%,.2f", netWorth)
    }

    fun formatTotalHoldingsValue(summary: PortfolioSummaryDto?): String {
        if (!isFullyValued(summary)) return UNVALUED_PLACEHOLDER
        val valHoldings = summary?.totalHoldingsValue ?: return UNVALUED_PLACEHOLDER
        return String.format(Locale.US, "$%,.2f", valHoldings)
    }

    fun formatTotalPnl(summary: PortfolioSummaryDto?): String {
        if (!isFullyValued(summary)) return UNVALUED_PLACEHOLDER
        val pnl = summary?.totalPnL ?: return UNVALUED_PLACEHOLDER
        val pnlPct = summary.totalPnLPercent
        val sign = if (pnl >= 0) "+" else "-"
        return if (pnlPct != null) {
            val pctSign = if (pnlPct >= 0) "+" else "-"
            String.format(Locale.US, "%s$%,.2f (%s%.2f%%)", sign, abs(pnl), pctSign, abs(pnlPct))
        } else {
            String.format(Locale.US, "%s$%,.2f (—)", sign, abs(pnl))
        }
    }

    fun formatHoldingCurrentPrice(holding: HoldingDto?): String {
        val price = holding?.currentPrice
        if (price == null || price <= 0.0) return UNVALUED_PLACEHOLDER
        return String.format(Locale.US, "$%,.2f", price)
    }

    fun formatHoldingPnl(holding: HoldingDto?): String {
        val price = holding?.currentPrice
        if (price == null || price <= 0.0) return UNVALUED_PLACEHOLDER
        val pnl = holding.unrealizedPnL ?: return UNVALUED_PLACEHOLDER
        val sign = if (pnl >= 0) "+" else "-"
        return String.format(Locale.US, "%s$%,.2f", sign, abs(pnl))
    }

    fun formatHoldingQuantity(quantity: Double?): String {
        if (quantity == null) return UNVALUED_PLACEHOLDER
        return String.format(Locale.US, "%.4f", quantity)
    }

    fun formatHoldingAvgPrice(avgBuyPrice: Double?): String {
        if (avgBuyPrice == null || avgBuyPrice <= 0.0) return UNVALUED_PLACEHOLDER
        return PriceFormatter.formatPrice(avgBuyPrice)
    }
}

object AuthHttpPolicy {
    fun isUnauthorized(statusCode: Int): Boolean {
        return statusCode == 401 || statusCode == 403
    }
}

sealed class WatchlistMutation {
    data class Add(val symbol: String) : WatchlistMutation()
    data class Remove(val symbol: String) : WatchlistMutation()
}

object WatchlistStateReducer {
    fun reduce(
        currentSymbols: Set<String>,
        mutation: WatchlistMutation,
        isSuccess: Boolean
    ): Set<String> {
        val snapshot = currentSymbols.toSet()
        if (!isSuccess) {
            return snapshot
        }
        return when (mutation) {
            is WatchlistMutation.Add -> {
                val clean = mutation.symbol.trim().uppercase(Locale.ROOT)
                if (clean.isNotBlank()) snapshot + clean else snapshot
            }
            is WatchlistMutation.Remove -> {
                val clean = mutation.symbol.trim().uppercase(Locale.ROOT)
                snapshot - clean
            }
        }
    }
}

/**
 * Quản lý ánh xạ nguồn cấp dữ liệu thị trường cố định (Fixed Provider Sharding):
 * - BINANCE: BTCUSDT, ETHUSDT, XAUUSD (PAXGUSDT)
 * - ALPACA: AAPL, MSFT, NVDA
 * - TWELVE_DATA: TSLA, AMZN, META
 * - ALPHA_VANTAGE: GOOGL, JPM
 */
object MarketDataProviderPolicy {
    fun resolveProviderName(symbol: String, marketDataProvider: String? = null): String {
        if (!marketDataProvider.isNullOrBlank()) {
            return when (marketDataProvider.trim().uppercase(Locale.ROOT)) {
                "BINANCE" -> "Binance"
                "ALPACA" -> "Alpaca"
                "TWELVE_DATA", "TWELVEDATA" -> "Twelve Data"
                "ALPHA_VANTAGE", "ALPHAVANTAGE" -> "Alpha Vantage"
                else -> marketDataProvider.trim()
            }
        }
        val sym = symbol.trim().uppercase(Locale.ROOT)
        return when (sym) {
            "BTCUSDT", "BTC", "ETHUSDT", "ETH", "XAUUSD", "XAU", "PAXGUSDT",
            "BNBUSDT", "BNB", "SOLUSDT", "SOL", "XRPUSDT", "XRP",
            "ADAUSDT", "ADA", "DOGEUSDT", "DOGE" -> "Binance"
            "AAPL", "MSFT", "NVDA", "GOOGL" -> "Alpaca"
            "TSLA", "AMZN", "META", "JPM" -> "Twelve Data"
            else -> "Binance"
        }
    }
}

/**
 * Quản lý chính sách Polling định kỳ 60s cho Cổ phiếu Hoa Kỳ:
 * - Chỉ chạy khi đang xem màn hình giao dịch (Tab Giao dịch) và là mã cổ phiếu.
 * - Hủy ngay lập tức khi unmount, chuyển mã, sang tab danh mục hoặc background.
 */
object StockPollingPolicy {
    const val STOCK_POLL_INTERVAL_MS = 60_000L

    fun shouldPoll(
        isStock: Boolean,
        isFragmentVisible: Boolean,
        isTradingTabSelected: Boolean
    ): Boolean {
        return isStock && isFragmentVisible && isTradingTabSelected
    }

    fun isPollResponseAllowed(
        callbackGeneration: Long,
        activeGeneration: Long,
        callbackSymbol: String?,
        activeSymbol: String?,
        isFragmentVisible: Boolean
    ): Boolean {
        if (!isFragmentVisible) return false
        if (callbackGeneration != activeGeneration) return false
        if (callbackSymbol.isNullOrBlank() || activeSymbol.isNullOrBlank()) return false
        return callbackSymbol.equals(activeSymbol, ignoreCase = true)
    }
}

data class StatusBadgeState(
    val textRes: Int,
    val colorRes: Int
)

/**
 * Quản lý hiển thị Badge trạng thái:
 * - Khi stale = true -> "Dữ liệu có thể trễ" (màu vàng/cam)
 * - Khi bình thường có giá -> "Trực tiếp" / "Đang cập nhật" (màu xanh)
 * - Khi không có giá -> "Dữ liệu máy chủ" / "Mất kết nối"
 */
object StockBadgePolicy {
    fun resolveStatusBadge(
        isStock: Boolean,
        isStale: Boolean?,
        hasValidPrice: Boolean
    ): StatusBadgeState {
        if (isStock && isStale == true) {
            return StatusBadgeState(R.string.trading_stale_badge, R.color.tv_yellow)
        }
        return if (hasValidPrice) {
            StatusBadgeState(R.string.trading_live_badge, R.color.tv_green)
        } else {
            StatusBadgeState(R.string.trading_no_live_badge, R.color.tv_text_secondary)
        }
    }
}
