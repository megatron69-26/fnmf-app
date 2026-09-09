package com.example.nhumonglenh

import com.example.nhumonglenh.ui.trading.AuthHeaderFactory
import com.example.nhumonglenh.ui.trading.CandleFallbackAction
import com.example.nhumonglenh.ui.trading.CandleFallbackPolicy
import com.example.nhumonglenh.ui.trading.CandleReloadPolicy
import com.example.nhumonglenh.ui.trading.ChartLabelFormatter
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.OrderCalculator
import com.example.nhumonglenh.ui.trading.OrderTicketBottomSheet
import com.example.nhumonglenh.ui.trading.PortfolioSyncPolicy
import com.example.nhumonglenh.ui.trading.RequestFlightTracker
import com.example.nhumonglenh.ui.trading.SubmissionGuard
import com.example.nhumonglenh.ui.trading.TradingDataReadiness
import com.example.nhumonglenh.ui.trading.TradingStateRestoration
import com.example.nhumonglenh.ui.watchlist.WatchlistRoomSyncPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TradingPolishUnitTest {

    // -------------------------------------------------------------
    // 1. ESTIMATED ORDER VALUE CALCULATIONS (PRODUCTION ORDER CALCULATOR)
    // -------------------------------------------------------------
    @Test
    fun testCalculateEstimatedValue_validInputs() {
        val actual = OrderCalculator.calculateEstimatedValue(0.5, 60000.0)
        assertEquals(30000.0, actual, 0.0001)
    }

    @Test
    fun testCalculateEstimatedValue_zeroOrNegative_returnsZero() {
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(0.0, 60000.0), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(-1.0, 60000.0), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(1.0, 0.0), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(1.0, -100.0), 0.0001)
    }

    @Test
    fun testCalculateEstimatedValue_nanAndInfinity_returnsZero() {
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(Double.NaN, 60000.0), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(1.0, Double.NaN), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(Double.POSITIVE_INFINITY, 60000.0), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(1.0, Double.POSITIVE_INFINITY), 0.0001)
        assertEquals(0.0, OrderCalculator.calculateEstimatedValue(Double.NEGATIVE_INFINITY, 60000.0), 0.0001)
    }

    // -------------------------------------------------------------
    // 2. BUY & SELL VALIDATIONS
    // -------------------------------------------------------------
    @Test
    fun testBuyOrder_withSufficientBalance_isValid() {
        val result = OrderCalculator.validateOrder(
            orderType = "BUY",
            quantity = 0.1,
            currentPrice = 70000.0,
            availableCash = 10000.0,
            ownedQuantity = 0.05
        )
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
        assertEquals(7000.0, result.estimatedOrderValue, 0.0001)
        assertEquals(3000.0, result.estimatedRemainingCash, 0.0001)
    }

    @Test
    fun testBuyOrder_withInsufficientBalance_isRejected() {
        val result = OrderCalculator.validateOrder(
            orderType = "BUY",
            quantity = 0.2,
            currentPrice = 70000.0,
            availableCash = 10000.0,
            ownedQuantity = 0.0
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testSellOrder_withSufficientHoldings_isValid() {
        val result = OrderCalculator.validateOrder(
            orderType = "SELL",
            quantity = 0.02,
            currentPrice = 80000.0,
            availableCash = 5000.0,
            ownedQuantity = 0.05
        )
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun testSellOrder_withInsufficientHoldings_isRejected() {
        val result = OrderCalculator.validateOrder(
            orderType = "SELL",
            quantity = 0.06,
            currentPrice = 80000.0,
            availableCash = 5000.0,
            ownedQuantity = 0.05
        )
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    // -------------------------------------------------------------
    // 3. QUICK QUANTITY PERCENTAGES & 0.999 BUFFER
    // -------------------------------------------------------------
    @Test
    fun testQuickQuantity_buyPercentages_includesSafetyBuffer() {
        val cash = 10000.0
        val price = 50000.0

        val q25 = OrderCalculator.calculateQuickQuantity(25, "BUY", cash, price, 0.0)
        assertEquals(0.05, q25, 0.0001)

        val q50 = OrderCalculator.calculateQuickQuantity(50, "BUY", cash, price, 0.0)
        assertEquals(0.1, q50, 0.0001)

        val q75 = OrderCalculator.calculateQuickQuantity(75, "BUY", cash, price, 0.0)
        assertEquals(0.15, q75, 0.0001)

        // 100% BUY applies 0.999 buffer: budget = 10000 * 0.999 = 9990; 9990 / 50000 = 0.1998 BTC
        val q100 = OrderCalculator.calculateQuickQuantity(100, "BUY", cash, price, 0.0)
        assertEquals(0.1998, q100, 0.0001)
        assertTrue("100% BUY must leave safety margin below available cash", q100 * price < cash)
    }

    // -------------------------------------------------------------
    // 4. PRODUCTION AUTH HEADER FACTORY
    // -------------------------------------------------------------
    @Test
    fun testAuthHeaderFactory_productionBehavior() {
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGZubWYuY29tIn0"
        val header = AuthHeaderFactory.createBearerHeader(validToken)
        assertNotNull(header)
        assertEquals("Bearer $validToken", header)

        // Null, empty, blank tokens must return null (NEVER bare 'Bearer ')
        assertNull(AuthHeaderFactory.createBearerHeader(null))
        assertNull(AuthHeaderFactory.createBearerHeader(""))
        assertNull(AuthHeaderFactory.createBearerHeader("   "))
    }

    // -------------------------------------------------------------
    // 5. PRODUCTION MARKET STREAM HELPER (WEBSOCKET MAPPING)
    // -------------------------------------------------------------
    @Test
    fun testMarketStreamHelper_resolveWebSocketStream() {
        assertEquals("btcusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("BTCUSDT"))
        assertEquals("btcusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("BTC"))
        assertEquals("ethusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("ETHUSDT"))
        assertEquals("ethusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("ETH"))
        assertEquals("paxgusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("XAUUSD"))
        assertEquals("paxgusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("XAU"))
        assertEquals("paxgusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("PAXGUSDT"))

        // Unknown or non-streamable symbol MUST return null and NEVER fallback to BTC
        assertNull("USOIL must return null stream", MarketStreamHelper.resolveWebSocketStream("USOIL"))
        assertNull("Unknown symbol must return null stream", MarketStreamHelper.resolveWebSocketStream("AAPL"))
        assertNull("Empty symbol must return null stream", MarketStreamHelper.resolveWebSocketStream(""))

        assertTrue(MarketStreamHelper.isGoldReferenceStream("XAUUSD"))
        assertTrue(MarketStreamHelper.isGoldReferenceStream("PAXGUSDT"))
        assertFalse(MarketStreamHelper.isGoldReferenceStream("BTCUSDT"))
        assertFalse(MarketStreamHelper.isGoldReferenceStream("USOIL"))
    }

    // -------------------------------------------------------------
    // 6. PRODUCTION CANDLE FALLBACK DECISION (SAME-SYMBOL VS CROSS-SYMBOL)
    // -------------------------------------------------------------
    @Test
    fun testCandleFallbackPolicy_crossSymbol_neverShowsOldCache() {
        // When switching BTC -> ETH, and ETH fails:
        val decision = CandleFallbackPolicy.decide(
            currentSymbol = "ETHUSDT",
            loadedCandleSymbol = "BTCUSDT",
            hasCachedCandles = true
        )
        assertEquals(CandleFallbackAction.SHOW_OFFLINE_ERROR, decision.action)
        assertTrue("Must clear chart so BTC candles never appear under ETH header", decision.shouldClearChart)
        assertEquals(R.string.trading_offline_badge, decision.badgeTextRes)
        assertEquals(R.color.tv_red, decision.statusColorRes)
    }

    @Test
    fun testCandleFallbackPolicy_sameSymbol_retainsCachedOffline() {
        // When viewing BTC, refresh BTC, and it fails:
        val decision = CandleFallbackPolicy.decide(
            currentSymbol = "BTCUSDT",
            loadedCandleSymbol = "BTCUSDT",
            hasCachedCandles = true
        )
        assertEquals(CandleFallbackAction.SHOW_CACHED_OFFLINE, decision.action)
        assertFalse("Same symbol with cached data should keep chart rendered", decision.shouldClearChart)
        assertEquals(R.string.trading_offline_cached_badge, decision.badgeTextRes)
        assertEquals(R.color.tv_yellow, decision.statusColorRes)
    }

    @Test
    fun testCandleFallbackPolicy_sameSymbol_noCache_showsOfflineError() {
        val decision = CandleFallbackPolicy.decide(
            currentSymbol = "BTCUSDT",
            loadedCandleSymbol = "BTCUSDT",
            hasCachedCandles = false
        )
        assertEquals(CandleFallbackAction.SHOW_OFFLINE_ERROR, decision.action)
        assertTrue(decision.shouldClearChart)
        assertEquals(R.string.trading_offline_badge, decision.badgeTextRes)
        assertEquals(R.color.tv_red, decision.statusColorRes)
    }

    // -------------------------------------------------------------
    // 7. PRODUCTION TRADING DATA READINESS (MUA/BAN & TICKET GUARDS)
    // -------------------------------------------------------------
    @Test
    fun testTradingDataReadiness_unloadedPortfolio_disabled() {
        // Portfolio not loaded -> disabled
        assertFalse(TradingDataReadiness.isReadyForTrading("valid_token", 60000.0, false, null))
        assertFalse(TradingDataReadiness.canOpenOrderTicket("valid_token", 60000.0, false, null))
    }

    @Test
    fun testTradingDataReadiness_unloadedPrice_disabled() {
        // Price null or <= 0 -> disabled
        assertFalse(TradingDataReadiness.isReadyForTrading("valid_token", null, true, 5000.0))
        assertFalse(TradingDataReadiness.isReadyForTrading("valid_token", 0.0, true, 5000.0))
        assertFalse(TradingDataReadiness.isReadyForTrading("valid_token", -100.0, true, 5000.0))
        assertFalse(TradingDataReadiness.canOpenOrderTicket("valid_token", null, true, 5000.0))
    }

    @Test
    fun testTradingDataReadiness_unauthenticated_disabled() {
        // Missing token -> disabled
        assertFalse(TradingDataReadiness.isReadyForTrading(null, 60000.0, true, 5000.0))
        assertFalse(TradingDataReadiness.isReadyForTrading("", 60000.0, true, 5000.0))
    }

    @Test
    fun testTradingDataReadiness_allValid_ready() {
        assertTrue(TradingDataReadiness.isReadyForTrading("valid_token", 60000.0, true, 5000.0))
        assertTrue(TradingDataReadiness.canOpenOrderTicket("valid_token", 60000.0, true, 5000.0))
    }

    // -------------------------------------------------------------
    // 8. PRODUCTION PORTFOLIO SYNC POLICY (DEBOUNCE 30 SECONDS)
    // -------------------------------------------------------------
    @Test
    fun testPortfolioSyncPolicy_staleThreshold30Seconds() {
        val now = 100_000L

        // Initial state (timestamp = 0) -> always stale
        assertTrue(PortfolioSyncPolicy.isStale(0L, now))

        // Fresh data (fetched 10s ago) -> not stale
        assertFalse(PortfolioSyncPolicy.isStale(now - 10_000L, now))

        // Boundary (29s ago) -> not stale
        assertFalse(PortfolioSyncPolicy.isStale(now - 29_999L, now))

        // Stale data (fetched 30s ago) -> stale
        assertTrue(PortfolioSyncPolicy.isStale(now - 30_000L, now))

        // Stale data (fetched 45s ago) -> stale
        assertTrue(PortfolioSyncPolicy.isStale(now - 45_000L, now))
    }

    // -------------------------------------------------------------
    // 9. PRODUCTION SUBMISSION GUARD (CONCURRENCY PROTECTION)
    // -------------------------------------------------------------
    @Test
    fun testSubmissionGuard_blocksConcurrentExecutions() {
        val guard = SubmissionGuard()
        assertFalse(guard.isInFlight())

        assertTrue(guard.tryAcquire())
        assertTrue(guard.isInFlight())

        // Concurrent submit blocked
        assertFalse(guard.tryAcquire())

        guard.release()
        assertFalse(guard.isInFlight())

        // Can acquire again after completion
        assertTrue(guard.tryAcquire())
    }

    // -------------------------------------------------------------
    // 10. SYMBOL FORMATTING
    // -------------------------------------------------------------
    @Test
    fun testFormatSymbolDisplay() {
        assertEquals("BTC/USDT", OrderTicketBottomSheet.formatSymbolDisplay("BTCUSDT"))
        assertEquals("ETH/USDT", OrderTicketBottomSheet.formatSymbolDisplay("ETHUSDT"))
        assertEquals("XAU/USD", OrderTicketBottomSheet.formatSymbolDisplay("XAUUSD"))
        assertEquals("PAXG/USDT", OrderTicketBottomSheet.formatSymbolDisplay("PAXGUSDT"))
        assertEquals("CUSTOM", OrderTicketBottomSheet.formatSymbolDisplay("CUSTOM"))
    }

    @Test
    fun testGetAssetTicker() {
        assertEquals("BTC", OrderTicketBottomSheet.getAssetTicker("BTCUSDT"))
        assertEquals("ETH", OrderTicketBottomSheet.getAssetTicker("ETHUSDT"))
        assertEquals("XAU", OrderTicketBottomSheet.getAssetTicker("XAUUSD"))
        assertEquals("PAXG", OrderTicketBottomSheet.getAssetTicker("PAXGUSDT"))
    }

    // -------------------------------------------------------------
    // 11. BACKEND ERROR MESSAGE PARSER
    // -------------------------------------------------------------
    @Test
    fun testParseBackendErrorMessage() {
        val msgJson = """{"message":"Số dư ví không đủ để đặt lệnh"}"""
        assertEquals("Số dư ví không đủ để đặt lệnh", OrderCalculator.parseBackendErrorMessage(msgJson))

        val errJson = """{"error":"Lệnh không hợp lệ"}"""
        assertEquals("Lệnh không hợp lệ", OrderCalculator.parseBackendErrorMessage(errJson))

        assertNull(OrderCalculator.parseBackendErrorMessage(null))
        assertNull(OrderCalculator.parseBackendErrorMessage(""))
        assertNull(OrderCalculator.parseBackendErrorMessage("invalid json"))
    }

    // -------------------------------------------------------------
    // 12. STATIC CODE SANITY AUDIT (MANDATORY ARCHITECTURAL CONSTRAINTS)
    // -------------------------------------------------------------
    @Test
    fun testStaticCodeSanityAudit_verifiesZeroMockAndZeroFakeDefaults() {
        val projectDirs = listOf(
            File("src/main/java/com/example/nhumonglenh"),
            File("app/src/main/java/com/example/nhumonglenh"),
            File("C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh")
        )
        val sourceDir = projectDirs.firstOrNull { it.exists() && it.isDirectory } ?: return

        val tradingFile = File(sourceDir, "TradingFragment.kt")
        if (tradingFile.exists()) {
            val content = tradingFile.readText()

            // 1. No fake financial defaults
            assertFalse("TradingFragment must not contain 10000.0 default cash balance", content.contains("10000.0"))
            assertFalse("TradingFragment must not hardcode 78000.0 price", content.contains("78000.0"))
            assertFalse("TradingFragment must not hardcode 3550.0 price", content.contains("3550.0"))
            assertFalse("TradingFragment must not hardcode 2500.0 price", content.contains("2500.0"))

            // 2. No fake orders or mock candles
            assertFalse("TradingFragment must not contain generateMockCandles", content.contains("generateMockCandles"))

            // 3. No polling loop or delay(3000)
            assertFalse("TradingFragment must not contain delay(3000)", content.contains("delay(3000)"))
            assertFalse("TradingFragment must not contain startAutoSyncPortfolioLoop", content.contains("startAutoSyncPortfolioLoop"))

            // 4. No News responsibility in Trading
            assertFalse("TradingFragment must not contain syncNewsToRoomDB", content.contains("syncNewsToRoomDB"))
            assertFalse("TradingFragment must not import AppDatabase", content.contains("AppDatabase"))
            assertFalse("TradingFragment must not contain MobileNewsBundleResponse", content.contains("MobileNewsBundleResponse"))

            // 5. No bare Bearer or bare ws URL
            assertFalse("TradingFragment must not contain bare 'Bearer ' literal", content.contains("\"Bearer \""))
            assertFalse("TradingFragment must not contain bare ws URL", content.contains("\"wss://stream.binance.com:9443/ws/\""))

            // 6. No gold simulation job
            assertFalse("TradingFragment must not contain goldSimulationJob", content.contains("goldSimulationJob"))

            // 7. No "Live 1s" in candle dataset label
            assertFalse("TradingFragment must not label daily candle dataset with 'Live 1s'", content.contains("(Live 1s)"))
            assertTrue("TradingFragment must use ChartLabelFormatter", content.contains("ChartLabelFormatter.formatDailyDatasetLabel"))

            // 8. Flight reference release helpers
            assertTrue("TradingFragment must implement releasePortfolioCall", content.contains("releasePortfolioCall"))
            assertTrue("TradingFragment must implement releaseCandleCall", content.contains("releaseCandleCall"))

            // 9. State restoration constant
            assertTrue("TradingFragment must define KEY_SAVED_SYMBOL", content.contains("KEY_SAVED_SYMBOL"))
        }

        val orderTicketFile = File(sourceDir, "ui/trading/OrderTicketBottomSheet.kt")
        if (orderTicketFile.exists()) {
            val content = orderTicketFile.readText()
            assertFalse("OrderTicket must not contain mockOrder", content.contains("mockOrder"))
            assertFalse("OrderTicket must not contain bare 'Bearer ' literal", content.contains("\"Bearer \""))
        }
    }

    // -------------------------------------------------------------
    // 13. REQUEST FLIGHT TRACKER (FLIGHT GUARD & RETRY / REFRESH ALLOWANCE)
    // -------------------------------------------------------------
    @Test
    fun testRequestFlightTracker_lifecycleAndRetryAllowance() {
        val tracker = RequestFlightTracker()
        assertFalse("Tracker should start not in-flight", tracker.isInFlight())

        // 1. Start initial request -> succeeds
        assertTrue("Initial startRequest should succeed", tracker.startRequest())
        assertTrue("Should be marked in-flight", tracker.isInFlight())

        // 2. Duplicate concurrent request -> blocked
        assertFalse("Concurrent request while in-flight must be blocked", tracker.startRequest())

        // 3. Request finishes (e.g. onFailure or onResponse) -> released
        tracker.finishRequest()
        assertFalse("After finishRequest, tracker should not be in-flight", tracker.isInFlight())

        // 4. Retry on failure or refresh after order completion -> succeeds!
        assertTrue("Subsequent refresh or retry must succeed after release", tracker.startRequest())
        assertTrue(tracker.isInFlight())

        // Release again
        tracker.finishRequest()
        assertFalse(tracker.isInFlight())
    }

    // -------------------------------------------------------------
    // 14. CANDLE RELOAD POLICY (TAB RESTORATION / PRESERVATION)
    // -------------------------------------------------------------
    @Test
    fun testCandleReloadPolicy_decisionRules() {
        // Case A: No candle data (e.g. call was canceled while switching tabs) -> Must reload
        assertTrue(
            "Empty chart must trigger reload when returning to tab",
            CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = false,
                loadedCandleSymbol = "BTCUSDT",
                currentSymbol = "BTCUSDT"
            )
        )

        // Case B: Loaded symbol is null or blank -> Must reload
        assertTrue(
            "Null loaded symbol must trigger reload",
            CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = true,
                loadedCandleSymbol = null,
                currentSymbol = "BTCUSDT"
            )
        )
        assertTrue(
            "Blank loaded symbol must trigger reload",
            CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = true,
                loadedCandleSymbol = "   ",
                currentSymbol = "BTCUSDT"
            )
        )

        // Case C: Symbol mismatch (cross-symbol) -> Must reload
        assertTrue(
            "Mismatched symbol must trigger reload",
            CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = true,
                loadedCandleSymbol = "BTCUSDT",
                currentSymbol = "ETHUSDT"
            )
        )

        // Case D: Same symbol with existing candle data -> Keep rendered chart and zoom, DO NOT reload!
        assertFalse(
            "Same symbol with existing data must NOT reload (preserves chart & zoom)",
            CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = true,
                loadedCandleSymbol = "BTCUSDT",
                currentSymbol = "BTCUSDT"
            )
        )
        assertFalse(
            "Case-insensitive matching symbol must NOT reload",
            CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = true,
                loadedCandleSymbol = "btcusdt",
                currentSymbol = "BTCUSDT"
            )
        )
    }

    // -------------------------------------------------------------
    // 15. CHART LABEL FORMATTER (DAILY CANDLES "• 1D")
    // -------------------------------------------------------------
    @Test
    fun testChartLabelFormatter_dailyCandles() {
        assertEquals("BTCUSDT • 1D", ChartLabelFormatter.formatDailyDatasetLabel("BTCUSDT"))
        assertEquals("ETHUSDT • 1D", ChartLabelFormatter.formatDailyDatasetLabel("ETHUSDT"))
        assertEquals("XAUUSD • 1D", ChartLabelFormatter.formatDailyDatasetLabel("XAUUSD"))

        val label = ChartLabelFormatter.formatDailyDatasetLabel("BTCUSDT")
        assertTrue("Label must end with • 1D", label.endsWith("• 1D"))
        assertFalse("Daily dataset label must not mention 'Live 1s'", label.contains("Live 1s"))
    }

    // -------------------------------------------------------------
    // 16. TRADING STATE RESTORATION (SYMBOL PRESERVATION ACROSS CONFIG CHANGES)
    // -------------------------------------------------------------
    @Test
    fun testTradingStateRestoration_symbolHandling() {
        assertEquals("ETHUSDT", TradingStateRestoration.resolveInitialSymbol("ETHUSDT"))
        assertEquals("XAUUSD", TradingStateRestoration.resolveInitialSymbol("xauusd"))
        assertEquals("BTCUSDT", TradingStateRestoration.resolveInitialSymbol(null))
        assertEquals("BTCUSDT", TradingStateRestoration.resolveInitialSymbol(""))
        assertEquals("BTCUSDT", TradingStateRestoration.resolveInitialSymbol("   "))
    }

    // -------------------------------------------------------------
    // 17. MARKET STREAM HELPER & BINANCE VISION DOMAIN
    // -------------------------------------------------------------
    @Test
    fun testMarketStreamHelper_binanceVisionWebSocketUrl() {
        assertEquals(
            "wss://data-stream.binance.vision:443/ws/",
            MarketStreamHelper.DEFAULT_BINANCE_WS_BASE_URL
        )

        // BTC stream
        val btcStream = MarketStreamHelper.resolveWebSocketStream("BTCUSDT")
        assertEquals("btcusdt@kline_1s", btcStream)
        val btcUrl = MarketStreamHelper.buildWebSocketUrl(btcStream!!)
        assertEquals("wss://data-stream.binance.vision:443/ws/btcusdt@kline_1s", btcUrl)

        // ETH stream
        val ethStream = MarketStreamHelper.resolveWebSocketStream("ETHUSDT")
        assertEquals("ethusdt@kline_1s", ethStream)
        val ethUrl = MarketStreamHelper.buildWebSocketUrl(ethStream!!)
        assertEquals("wss://data-stream.binance.vision:443/ws/ethusdt@kline_1s", ethUrl)

        // XAU reference stream (PAXG)
        val xauStream = MarketStreamHelper.resolveWebSocketStream("XAUUSD")
        assertEquals("paxgusdt@kline_1s", xauStream)
        val xauUrl = MarketStreamHelper.buildWebSocketUrl(xauStream!!)
        assertEquals("wss://data-stream.binance.vision:443/ws/paxgusdt@kline_1s", xauUrl)

        // Unsupported symbols return null
        assertNull(MarketStreamHelper.resolveWebSocketStream("USOIL"))
        assertNull(MarketStreamHelper.resolveWebSocketStream("UNKNOWN"))

        // Custom base URL with or without trailing slash
        val customUrl1 = MarketStreamHelper.buildWebSocketUrl("test@stream", "wss://custom.domain/ws")
        assertEquals("wss://custom.domain/ws/test@stream", customUrl1)
        val customUrl2 = MarketStreamHelper.buildWebSocketUrl("/test@stream", "wss://custom.domain/ws/")
        assertEquals("wss://custom.domain/ws/test@stream", customUrl2)
    }

    @Test
    fun testMarketStreamHelper_goldReferenceDetection() {
        assertTrue(MarketStreamHelper.isGoldReferenceStream("XAUUSD"))
        assertTrue(MarketStreamHelper.isGoldReferenceStream("XAU"))
        assertTrue(MarketStreamHelper.isGoldReferenceStream("PAXGUSDT"))
        assertFalse(MarketStreamHelper.isGoldReferenceStream("BTCUSDT"))
        assertFalse(MarketStreamHelper.isGoldReferenceStream("ETHUSDT"))
        assertFalse(MarketStreamHelper.isGoldReferenceStream("USOIL"))
    }

    // -------------------------------------------------------------
    // 18. BINANCE WS DOMAIN PRODUCTION SANITY AUDIT
    // -------------------------------------------------------------
    @Test
    fun testStaticSanityAudit_verifiesBinanceVisionWebSocketUsage() {
        val projectDirs = listOf(
            File("src/main/java/com/example/nhumonglenh"),
            File("app/src/main/java/com/example/nhumonglenh"),
            File("C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh")
        )
        val sourceDir = projectDirs.firstOrNull { it.exists() && it.isDirectory } ?: return

        val tradingFile = File(sourceDir, "TradingFragment.kt")
        if (tradingFile.exists()) {
            val content = tradingFile.readText()
            // Không hardcode stream.binance.com:9443
            assertFalse("TradingFragment must not contain stream.binance.com:9443", content.contains("stream.binance.com:9443"))
            // Sử dụng MarketStreamHelper.buildWebSocketUrl
            assertTrue("TradingFragment must use MarketStreamHelper.buildWebSocketUrl", content.contains("MarketStreamHelper.buildWebSocketUrl"))
            // Xử lý 401/403 bằng AuthSessionManager.handleUnauthorized
            assertTrue("TradingFragment must call AuthSessionManager.handleUnauthorized", content.contains("AuthSessionManager.handleUnauthorized"))
        }

        val watchlistFile = File(sourceDir, "ui/watchlist/WatchlistFragment.kt")
        val policyFile = File(sourceDir, "ui/watchlist/WatchlistRoomSyncPolicy.kt")
        if (watchlistFile.exists()) {
            val content = watchlistFile.readText()
            val policyContent = if (policyFile.exists()) policyFile.readText() else ""
            val combined = content + "\n" + policyContent
            // Xử lý 503 thông báo dữ liệu thị trường tạm thời không khả dụng
            assertTrue("Watchlist policy must handle 503 with proper message", combined.contains("Nguồn dữ liệu thị trường tạm thời không khả dụng"))
            // Sử dụng WatchlistRoomSyncPolicy
            assertTrue("WatchlistFragment must use WatchlistRoomSyncPolicy", content.contains("WatchlistRoomSyncPolicy"))
            // Gọi AuthSessionManager.handleUnauthorized
            assertTrue("WatchlistFragment must call AuthSessionManager.handleUnauthorized", content.contains("AuthSessionManager.handleUnauthorized"))
        }
    }

    // -------------------------------------------------------------
    // 19. WATCHLIST ROOM SYNC POLICY DIRECT UNIT TESTS
    // -------------------------------------------------------------
    @Test
    fun testWatchlistRoomSyncPolicy_http200_syncsRoomEvenIfEmpty() {
        // Case A: HTTP 200 with non-empty list -> Must SYNC_ROOM and clearAndInsert
        val decisionNonEmpty = WatchlistRoomSyncPolicy.evaluate(
            statusCode = 200,
            isSuccessful = true,
            itemCount = 5
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.SYNC_ROOM, decisionNonEmpty.action)
        assertTrue(decisionNonEmpty.shouldClearAndInsert)
        assertTrue(decisionNonEmpty.reason.contains("Đồng bộ Room DB"))

        // Case B: HTTP 200 with EMPTY list ([]) -> CRITICAL: Must SYNC_ROOM to clear phantom watchlist
        val decisionEmpty = WatchlistRoomSyncPolicy.evaluate(
            statusCode = 200,
            isSuccessful = true,
            itemCount = 0
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.SYNC_ROOM, decisionEmpty.action)
        assertTrue(decisionEmpty.shouldClearAndInsert)
        assertTrue(decisionEmpty.reason.contains("0 mục"))

        // Case C: HTTP 200 with null itemCount -> Fallbacks to 0 items, still SYNC_ROOM
        val decisionNullCount = WatchlistRoomSyncPolicy.evaluate(
            statusCode = 200,
            isSuccessful = true,
            itemCount = null
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.SYNC_ROOM, decisionNullCount.action)
        assertTrue(decisionNullCount.shouldClearAndInsert)

        // Case D: Direct outcome evaluation with HttpSuccess
        val outcomeEmpty = WatchlistRoomSyncPolicy.evaluateOutcome(
            WatchlistRoomSyncPolicy.ResponseOutcome.HttpSuccess(200, 0)
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.SYNC_ROOM, outcomeEmpty.action)
        assertTrue(outcomeEmpty.shouldClearAndInsert)
    }

    @Test
    fun testWatchlistRoomSyncPolicy_errors_preserveCache() {
        // Case A: HTTP 503 Market Data Unavailable -> KEEP_CACHE, do not clear
        val decision503 = WatchlistRoomSyncPolicy.evaluate(
            statusCode = 503,
            isSuccessful = false,
            itemCount = null
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.KEEP_CACHE, decision503.action)
        assertFalse(decision503.shouldClearAndInsert)
        assertTrue(decision503.reason.contains("Nguồn dữ liệu thị trường tạm thời không khả dụng"))

        // Case B: HTTP 500 Server Error -> KEEP_CACHE
        val decision500 = WatchlistRoomSyncPolicy.evaluate(
            statusCode = 500,
            isSuccessful = false,
            itemCount = null
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.KEEP_CACHE, decision500.action)
        assertFalse(decision500.shouldClearAndInsert)
        assertTrue(decision500.reason.contains("500"))

        // Case C: HTTP 401 Unauthorized -> KEEP_CACHE
        val decision401 = WatchlistRoomSyncPolicy.evaluate(
            statusCode = 401,
            isSuccessful = false,
            itemCount = null
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.KEEP_CACHE, decision401.action)
        assertFalse(decision401.shouldClearAndInsert)

        // Case D: Network failure (IOException / timeout) -> KEEP_CACHE
        val timeoutEx = java.net.SocketTimeoutException("Failed to connect to backend")
        val networkDecision = WatchlistRoomSyncPolicy.evaluateNetworkFailure(timeoutEx)
        assertEquals(WatchlistRoomSyncPolicy.Action.KEEP_CACHE, networkDecision.action)
        assertFalse(networkDecision.shouldClearAndInsert)
        assertTrue(networkDecision.reason.contains("Failed to connect to backend"))

        // Case E: Direct outcome evaluation with NetworkError
        val outcomeNetwork = WatchlistRoomSyncPolicy.evaluateOutcome(
            WatchlistRoomSyncPolicy.ResponseOutcome.NetworkError(java.io.IOException("No route to host"))
        )
        assertEquals(WatchlistRoomSyncPolicy.Action.KEEP_CACHE, outcomeNetwork.action)
        assertFalse(outcomeNetwork.shouldClearAndInsert)
        assertTrue(outcomeNetwork.reason.contains("No route to host"))
    }
}
