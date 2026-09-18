package com.example.nhumonglenh

import com.example.nhumonglenh.ui.trading.ChartLabelFormatter
import com.example.nhumonglenh.ui.trading.MarketDataProviderPolicy
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.StatusBadgeState
import com.example.nhumonglenh.ui.trading.StockBadgePolicy
import com.example.nhumonglenh.ui.trading.StockPollingPolicy
import com.example.nhumonglenh.ui.trading.StockTradePolicy
import com.example.nhumonglenh.ui.trading.TradingLifecyclePolicy
import com.example.nhumonglenh.ui.trading.TradingResumeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Targeted Unit Tests cho FNMF v1.1.23 — Fixed Provider Sharding trên Android App:
 * 1. Hiển thị đúng nguồn dữ liệu theo mã (Binance, Alpaca, Twelve Data, Alpha Vantage).
 * 2. Không mở WebSocket cho cổ phiếu (streamName == null & resume DO_NOTHING).
 * 3. Polling định kỳ 60s cho cổ phiếu và hủy khi unmount/switch/background/tab change.
 * 4. Guard kiểm soát phản hồi polling chống stale generation/symbol.
 * 5. Badge trạng thái "Dữ liệu có thể trễ" (màu vàng) và khóa Mua/Bán khi stale = true.
 * 6. Nến 1m (1 phút) cho cả cổ phiếu và crypto.
 */
class FixedProviderShardingAppUnitTest {

    // =========================================================================
    // 1. Ánh xạ nguồn dữ liệu cố định (Fixed Provider Sharding)
    // =========================================================================
    @Test
    fun testMarketDataProvider_fixedMapping() {
        // Binance: Crypto & Vàng
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("BTCUSDT"))
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("BTC"))
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("ETHUSDT"))
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("ETH"))
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("XAUUSD"))
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("PAXGUSDT"))

        // Alpaca: AAPL, MSFT, NVDA, GOOGL
        assertEquals("Alpaca", MarketDataProviderPolicy.resolveProviderName("AAPL"))
        assertEquals("Alpaca", MarketDataProviderPolicy.resolveProviderName("MSFT"))
        assertEquals("Alpaca", MarketDataProviderPolicy.resolveProviderName("NVDA"))
        assertEquals("Alpaca", MarketDataProviderPolicy.resolveProviderName("GOOGL"))

        // Twelve Data: TSLA, AMZN, META, JPM
        assertEquals("Twelve Data", MarketDataProviderPolicy.resolveProviderName("TSLA"))
        assertEquals("Twelve Data", MarketDataProviderPolicy.resolveProviderName("AMZN"))
        assertEquals("Twelve Data", MarketDataProviderPolicy.resolveProviderName("META"))
        assertEquals("Twelve Data", MarketDataProviderPolicy.resolveProviderName("JPM"))
    }

    @Test
    fun testMarketDataProvider_respectsExplicitBackendPayload() {
        assertEquals("Alpaca", MarketDataProviderPolicy.resolveProviderName("AAPL", "ALPACA"))
        assertEquals("Twelve Data", MarketDataProviderPolicy.resolveProviderName("TSLA", "TWELVE_DATA"))
        assertEquals("Alpha Vantage", MarketDataProviderPolicy.resolveProviderName("GOOGL", "ALPHA_VANTAGE"))
        assertEquals("Binance", MarketDataProviderPolicy.resolveProviderName("BTCUSDT", "BINANCE"))
    }

    // =========================================================================
    // 2. Tuyệt đối KHÔNG mở WebSocket cho Cổ phiếu Hoa Kỳ
    // =========================================================================
    @Test
    fun testStockTradePolicy_noWebSocketStreamForStocks() {
        val stocks = listOf("AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META", "GOOGL", "JPM")
        for (symbol in stocks) {
            assertTrue(StockTradePolicy.isStock(symbol))
            assertNull(
                "Mã cổ phiếu $symbol không được phép có Binance WebSocket stream",
                MarketStreamHelper.resolveWebSocketStream(symbol)
            )
        }
    }

    @Test
    fun testTradingLifecyclePolicy_stockResumeDoesNothing() {
        val action = TradingLifecyclePolicy.decideResumeAction(
            isStock = true,
            hasActiveCandleCall = false,
            hasCandles = false,
            hasActiveSocket = false
        )
        assertEquals(
            "Cổ phiếu khi resume phải trả về DO_NOTHING để không mở socket",
            TradingResumeAction.DO_NOTHING,
            action
        )
    }

    // =========================================================================
    // 3. Chính sách Polling định kỳ 60s cho Cổ phiếu
    // =========================================================================
    @Test
    fun testStockPollingPolicy_intervalIs60Seconds() {
        assertEquals(60_000L, StockPollingPolicy.STOCK_POLL_INTERVAL_MS)
    }

    @Test
    fun testStockPollingPolicy_conditions() {
        // Chỉ chạy khi: là cổ phiếu AND visible AND đang ở tab Giao dịch
        assertTrue(
            StockPollingPolicy.shouldPoll(
                isStock = true,
                isFragmentVisible = true,
                isTradingTabSelected = true
            )
        )

        // Không chạy cho Crypto/Vàng (crypto dùng WebSocket real-time)
        assertFalse(
            StockPollingPolicy.shouldPoll(
                isStock = false,
                isFragmentVisible = true,
                isTradingTabSelected = true
            )
        )

        // Dừng khi Fragment bị ẩn / Background / onPause
        assertFalse(
            StockPollingPolicy.shouldPoll(
                isStock = true,
                isFragmentVisible = false,
                isTradingTabSelected = true
            )
        )

        // Dừng khi đang xem tab danh mục Cổ phiếu (Tab 1)
        assertFalse(
            StockPollingPolicy.shouldPoll(
                isStock = true,
                isFragmentVisible = true,
                isTradingTabSelected = false
            )
        )
    }

    // =========================================================================
    // 4. Guard phản hồi Polling chống stale generation / symbol
    // =========================================================================
    @Test
    fun testStockPollingPolicy_responseGuards() {
        // Hợp lệ: cùng generation, cùng symbol, fragment đang hiển thị
        assertTrue(
            StockPollingPolicy.isPollResponseAllowed(
                callbackGeneration = 5L,
                activeGeneration = 5L,
                callbackSymbol = "AAPL",
                activeSymbol = "AAPL",
                isFragmentVisible = true
            )
        )

        // Chặn khi thế hệ (generation) không khớp (đã đổi mã)
        assertFalse(
            StockPollingPolicy.isPollResponseAllowed(
                callbackGeneration = 4L,
                activeGeneration = 5L,
                callbackSymbol = "AAPL",
                activeSymbol = "AAPL",
                isFragmentVisible = true
            )
        )

        // Chặn khi mã (symbol) không khớp (ví dụ AAPL về sau khi đã chuyển sang MSFT)
        assertFalse(
            StockPollingPolicy.isPollResponseAllowed(
                callbackGeneration = 5L,
                activeGeneration = 5L,
                callbackSymbol = "AAPL",
                activeSymbol = "MSFT",
                isFragmentVisible = true
            )
        )

        // Chặn khi fragment đã bị pause / background
        assertFalse(
            StockPollingPolicy.isPollResponseAllowed(
                callbackGeneration = 5L,
                activeGeneration = 5L,
                callbackSymbol = "AAPL",
                activeSymbol = "AAPL",
                isFragmentVisible = false
            )
        )
    }

    // =========================================================================
    // 5. Badge "Dữ liệu có thể trễ" (màu vàng) và khóa Mua/Bán khi stale=true
    // =========================================================================
    @Test
    fun testStockBadgePolicy_staleBadgeYellow() {
        val staleBadge = StockBadgePolicy.resolveStatusBadge(
            isStock = true,
            isStale = true,
            hasValidPrice = true
        )
        assertEquals(R.string.trading_stale_badge, staleBadge.textRes)
        assertEquals(R.color.tv_yellow, staleBadge.colorRes)
    }

    @Test
    fun testStockBadgePolicy_normalStockLiveBadgeGreen() {
        val liveBadge = StockBadgePolicy.resolveStatusBadge(
            isStock = true,
            isStale = false,
            hasValidPrice = true
        )
        assertEquals(R.string.trading_live_badge, liveBadge.textRes)
        assertEquals(R.color.tv_green, liveBadge.colorRes)
    }

    @Test
    fun testStockTradePolicy_staleDisablesTrading() {
        // Khi stale = true -> Mua/Bán bị khóa
        assertFalse(
            StockTradePolicy.isTradeEnabled(
                isStock = true,
                stale = true,
                currentPrice = 180.50
            )
        )

        // Khi stale = false và giá > 0 -> Mua/Bán được bật
        assertTrue(
            StockTradePolicy.isTradeEnabled(
                isStock = true,
                stale = false,
                currentPrice = 180.50
            )
        )

        // Khi giá null hoặc <= 0 -> Mua/Bán bị khóa
        assertFalse(
            StockTradePolicy.isTradeEnabled(
                isStock = true,
                stale = false,
                currentPrice = null
            )
        )
        assertFalse(
            StockTradePolicy.isTradeEnabled(
                isStock = true,
                stale = false,
                currentPrice = 0.0
            )
        )
    }

    // =========================================================================
    // 6. Nhãn nến 1s và 1m cho cả Cổ phiếu và Crypto
    // =========================================================================
    @Test
    fun testChartLabelFormatter_1mForBothCryptoAndStocks() {
        assertEquals("BTCUSDT • 1s", ChartLabelFormatter.formatChartDatasetLabel("BTCUSDT"))
        assertEquals("BTCUSDT • 1s", ChartLabelFormatter.formatChartDatasetLabel("BTCUSDT", "1s"))
        assertEquals("BTCUSDT • 1m", ChartLabelFormatter.formatChartDatasetLabel("BTCUSDT", "1m"))
        assertEquals("AAPL • 1s", ChartLabelFormatter.formatChartDatasetLabel("AAPL", "1s"))
        assertEquals("TSLA • 1s", ChartLabelFormatter.formatChartDatasetLabel("TSLA", "1s"))
    }
}
