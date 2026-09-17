package com.example.nhumonglenh.ui.trading

import com.example.nhumonglenh.R

/**
 * Tạo HTTP Authorization Bearer Header chuẩn production.
 * Trả về null nếu token null hoặc rỗng để ngăn chặn việc gửi bare "Bearer ".
 */
object AuthHeaderFactory {
    fun createBearerHeader(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return "Bearer ${token.trim()}"
    }
}

/**
 * Quản lý độ phân giải WebSocket Stream cho các cặp tiền tệ.
 * Chỉ hỗ trợ BTCUSDT, ETHUSDT và XAUUSD (qua PAXGUSDT tham chiếu).
 * Các symbol khác (ví dụ: USOIL) trả về null để không fallback nhầm vào BTC.
 */
object MarketStreamHelper {
    const val DEFAULT_BINANCE_WS_BASE_URL = "wss://data-stream.binance.vision/ws/"

    fun buildWebSocketUrl(streamName: String, baseUrl: String = DEFAULT_BINANCE_WS_BASE_URL): String {
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanStream = if (streamName.startsWith("/")) streamName.substring(1) else streamName
        return "$normalizedBase$cleanStream"
    }

    fun resolveWebSocketStream(symbol: String): String? {
        val sym = symbol.uppercase().trim()
        return when {
            sym == "BTCUSDT" || sym == "BTC" -> "btcusdt@kline_1m"
            sym == "ETHUSDT" || sym == "ETH" -> "ethusdt@kline_1m"
            sym == "XAUUSD" || sym == "XAU" || sym == "PAXGUSDT" -> "paxgusdt@kline_1m"
            sym == "BNBUSDT" || sym == "BNB" -> "bnbusdt@kline_1m"
            sym == "SOLUSDT" || sym == "SOL" -> "solusdt@kline_1m"
            sym == "XRPUSDT" || sym == "XRP" -> "xrpusdt@kline_1m"
            sym == "ADAUSDT" || sym == "ADA" -> "adausdt@kline_1m"
            sym == "DOGEUSDT" || sym == "DOGE" -> "dogeusdt@kline_1m"
            else -> null
        }
    }

    fun isGoldReferenceStream(symbol: String): Boolean {
        val sym = symbol.uppercase().trim()
        return sym == "XAUUSD" || sym == "XAU" || sym == "PAXGUSDT"
    }
}

/**
 * Kết quả quyết định fallback hiển thị biểu đồ nến.
 */
enum class CandleFallbackAction {
    SHOW_CACHED_OFFLINE,
    SHOW_OFFLINE_ERROR
}

data class CandleFallbackResult(
    val action: CandleFallbackAction,
    val badgeTextRes: Int,
    val statusColorRes: Int,
    val shouldClearChart: Boolean
)

/**
 * Quyết định hành vi fallback khi tải nến thất bại:
 * - Chỉ giữ lại cache nến cũ nếu cùng symbol.
 * - Khi đổi symbol (cross-symbol, ví dụ BTC -> ETH) và request lỗi: KHÔNG hiển thị chart cũ của BTC!
 */
object CandleFallbackPolicy {
    fun decide(
        currentSymbol: String,
        loadedCandleSymbol: String?,
        hasCachedCandles: Boolean
    ): CandleFallbackResult {
        val isSameSymbol = loadedCandleSymbol != null && loadedCandleSymbol.equals(currentSymbol, ignoreCase = true)
        return if (isSameSymbol && hasCachedCandles) {
            CandleFallbackResult(
                action = CandleFallbackAction.SHOW_CACHED_OFFLINE,
                badgeTextRes = R.string.trading_offline_cached_badge,
                statusColorRes = R.color.tv_yellow,
                shouldClearChart = false
            )
        } else {
            CandleFallbackResult(
                action = CandleFallbackAction.SHOW_OFFLINE_ERROR,
                badgeTextRes = R.string.trading_offline_badge,
                statusColorRes = R.color.tv_red,
                shouldClearChart = true
            )
        }
    }
}

/**
 * Kiểm tra trạng thái sẵn sàng của dữ liệu trước khi cho phép giao dịch.
 * Đảm bảo:
 * 1. Token hợp lệ.
 * 2. Giá thị trường thật > 0 (không phải placeholder).
 * 3. Portfolio thật đã tải thành công và có số dư tiền mặt hợp lệ >= 0.
 */
object TradingDataReadiness {
    fun isReadyForTrading(
        token: String?,
        currentPrice: Double?,
        portfolioLoaded: Boolean,
        userCashBalance: Double?
    ): Boolean {
        val hasToken = !token.isNullOrBlank()
        val hasValidPrice = currentPrice != null && !currentPrice.isNaN() && !currentPrice.isInfinite() && currentPrice > 0.0
        val hasPortfolio = portfolioLoaded && userCashBalance != null && !userCashBalance.isNaN() && !userCashBalance.isInfinite() && userCashBalance >= 0.0
        return hasToken && hasValidPrice && hasPortfolio
    }

    fun canOpenOrderTicket(
        token: String?,
        currentPrice: Double?,
        portfolioLoaded: Boolean,
        userCashBalance: Double?
    ): Boolean {
        return isReadyForTrading(token, currentPrice, portfolioLoaded, userCashBalance)
    }
}

/**
 * Chính sách làm mới danh mục đầu tư (Portfolio):
 * Tránh việc liên tục polling 3 giây gây nghẽn mạng;
 * Chỉ làm mới nếu dữ liệu cũ hơn 30 giây khi quay lại tab.
 */
object PortfolioSyncPolicy {
    const val STALE_THRESHOLD_MS = 30_000L

    fun isStale(lastFetchTimestamp: Long, currentTimestamp: Long = System.currentTimeMillis()): Boolean {
        if (lastFetchTimestamp <= 0L) return true
        return (currentTimestamp - lastFetchTimestamp) >= STALE_THRESHOLD_MS
    }
}

/**
 * Quản lý vòng đời gọi mạng:
 * - Chỉ cho phép 1 request in-flight tại 1 thời điểm.
 * - Khi request kết thúc (thành công hoặc thất bại hoặc bị hủy), gọi finishRequest() để giải phóng.
 * - Cho phép request tiếp theo (refresh / retry) được thực thi.
 */
class RequestFlightTracker {
    private var inFlight = false

    fun startRequest(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    fun finishRequest() {
        inFlight = false
    }

    fun isInFlight(): Boolean = inFlight
}

/**
 * Cơ chế bảo vệ chống double-click / gửi lệnh đồng thời.
 */
class SubmissionGuard {
    private var inFlight = false

    fun tryAcquire(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    fun release() {
        inFlight = false
    }

    fun isInFlight(): Boolean = inFlight
}

/**
 * Chính sách quyết định xem có cần tải lại dữ liệu nến khi tab hiển thị lại (onHiddenChanged(false)) hay không:
 * - Nếu chưa có nến nào (ví dụ request trước bị cancel khi ẩn tab) -> Bắt buộc tải lại.
 * - Nếu mã nến đã tải khác với currentSymbol -> Bắt buộc tải lại.
 * - Nếu đang có đúng nến của currentSymbol -> Giữ nguyên chart và zoom, KHÔNG tải lại.
 */
object CandleReloadPolicy {
    fun shouldReloadOnTabVisible(
        hasCandleData: Boolean,
        loadedCandleSymbol: String?,
        currentSymbol: String
    ): Boolean {
        if (!hasCandleData) return true
        if (loadedCandleSymbol.isNullOrBlank()) return true
        return !loadedCandleSymbol.equals(currentSymbol, ignoreCase = true)
    }
}

/**
 * Định dạng nhãn tập dữ liệu biểu đồ nến ngày (1D)
 */
object ChartLabelFormatter {
    fun formatDailyDatasetLabel(symbol: String): String {
        return "$symbol • 1D"
    }

    fun formatChartDatasetLabel(symbol: String, interval: String = "1m"): String {
        val norm = interval.trim().lowercase()
        return if (norm == "daily" || norm == "1d") {
            "$symbol • 1D"
        } else {
            "$symbol • 1m"
        }
    }
}

/**
 * Phục hồi symbol khi Fragment recreation / configuration change.
 * Giữ nguyên symbol (ví dụ ETHUSDT, XAUUSD) nếu có, nếu không thì fallback về default.
 */
object TradingStateRestoration {
    const val DEFAULT_SYMBOL = "BTCUSDT"

    fun resolveInitialSymbol(savedSymbol: String?): String {
        return if (!savedSymbol.isNullOrBlank()) savedSymbol.trim().uppercase() else DEFAULT_SYMBOL
    }
}

/**
 * Định dạng giá thích ứng (Adaptive Price Formatter) cho tiền mã hóa & tài sản tài chính:
 * - >= $100: 2 chữ số thập phân (e.g. BTC $76,370.90, BNB $725.30, SOL $100.31)
 * - >= $1: 2-4 chữ số thập phân (e.g. XRP $1.2988)
 * - < $1: 4-6 chữ số thập phân (e.g. ADA $0.2006, DOGE $0.08125)
 * - Tuyệt đối không làm tròn thô 2 chữ số gây mất mát độ chính xác giao dịch.
 */
object PriceFormatter {
    fun formatPrice(price: Double): String {
        return when {
            price >= 100.0 -> String.format(java.util.Locale.US, "$%,.2f", price)
            price >= 1.0 -> {
                val s = String.format(java.util.Locale.US, "$%,.4f", price)
                trimDecimals(s, minDecimals = 2)
            }
            price >= 0.0001 -> {
                val s = String.format(java.util.Locale.US, "$%.6f", price)
                trimDecimals(s, minDecimals = 2)
            }
            else -> {
                val s = String.format(java.util.Locale.US, "$%.8f", price)
                trimDecimals(s, minDecimals = 2)
            }
        }
    }

    private fun trimDecimals(formatted: String, minDecimals: Int = 2): String {
        val trimmed = formatted.trimEnd('0')
        return if (trimmed.endsWith('.')) {
            trimmed + "0".repeat(minDecimals)
        } else {
            val decimalsCount = trimmed.substringAfter('.').length
            if (decimalsCount < minDecimals) {
                trimmed + "0".repeat(minDecimals - decimalsCount)
            } else {
                trimmed
            }
        }
    }
}

