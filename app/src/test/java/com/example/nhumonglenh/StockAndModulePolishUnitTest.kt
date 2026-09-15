package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.ui.trading.AuthHttpPolicy
import com.example.nhumonglenh.ui.trading.PortfolioValuationPolicy
import com.example.nhumonglenh.ui.trading.StockReportPolicy
import com.example.nhumonglenh.ui.trading.StockTradePolicy
import com.example.nhumonglenh.ui.trading.StockWatchlistMatcher
import com.example.nhumonglenh.ui.trading.WatchlistMutation
import com.example.nhumonglenh.ui.trading.WatchlistStateReducer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class StockAndModulePolishUnitTest {

    // -------------------------------------------------------------------------
    // 1. STOCK TRADE POLICY TESTS
    // -------------------------------------------------------------------------
    @Test
    fun testSupportedStocks_containsExactEightSymbols() {
        val expected = setOf("AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META", "GOOGL", "JPM")
        assertEquals(expected, StockTradePolicy.SUPPORTED_STOCKS)
        for (sym in expected) {
            assertTrue("Symbol $sym must be identified as stock", StockTradePolicy.isStock(sym))
        }
        assertFalse("BTCUSDT must not be stock", StockTradePolicy.isStock("BTCUSDT"))
        assertFalse("ETHUSDT must not be stock", StockTradePolicy.isStock("ETHUSDT"))
        assertFalse("PAXGUSDT must not be stock", StockTradePolicy.isStock("PAXGUSDT"))
    }

    @Test
    fun testStockTrade_disabledWhenStaleIsTrue() {
        val enabled = StockTradePolicy.isTradeEnabled(
            isStock = true,
            stale = true,
            currentPrice = 150.0
        )
        assertFalse("Stock trade must be disabled when price is stale", enabled)

        val msg = StockTradePolicy.resolveTradeStatusMessage(
            isStock = true,
            stale = true,
            currentPrice = 150.0
        )
        assertNotNull(msg)
        assertTrue(msg!!.contains("không khả dụng") || msg.contains("chưa sẵn sàng") || msg.contains("cũ"))
    }

    @Test
    fun testStockTrade_disabledWhenPriceIsNullZeroOrNegative() {
        assertFalse(StockTradePolicy.isTradeEnabled(isStock = true, stale = false, currentPrice = null))
        assertFalse(StockTradePolicy.isTradeEnabled(isStock = true, stale = false, currentPrice = 0.0))
        assertFalse(StockTradePolicy.isTradeEnabled(isStock = true, stale = false, currentPrice = -10.0))

        val msgNull = StockTradePolicy.resolveTradeStatusMessage(isStock = true, stale = false, currentPrice = null)
        assertNotNull(msgNull)
        assertTrue(msgNull!!.contains("khả dụng") || msgNull.contains("chưa sẵn sàng"))
    }

    @Test
    fun testStockTrade_enabledOnlyWhenStaleIsFalseAndPricePositive() {
        val enabled = StockTradePolicy.isTradeEnabled(
            isStock = true,
            stale = false,
            currentPrice = 220.50
        )
        assertTrue("Stock trade must be enabled for fresh valid price", enabled)
        assertNull(StockTradePolicy.resolveTradeStatusMessage(isStock = true, stale = false, currentPrice = 220.50))
    }

    @Test
    fun testNonStockTrade_enabledWhenPricePositive() {
        assertTrue(StockTradePolicy.isTradeEnabled(isStock = false, stale = false, currentPrice = 65000.0))
        assertTrue(StockTradePolicy.isTradeEnabled(isStock = false, stale = true, currentPrice = 65000.0))
        assertFalse(StockTradePolicy.isTradeEnabled(isStock = false, stale = false, currentPrice = null))
        assertFalse(StockTradePolicy.isTradeEnabled(isStock = false, stale = false, currentPrice = 0.0))
    }

    // -------------------------------------------------------------------------
    // 2. STOCK REPORT POLICY TESTS (VIETNAMESE TITLE VS ENGLISH TITLE)
    // -------------------------------------------------------------------------
    @Test
    fun testStockReportButton_visibilityDependsOnUrl() {
        assertFalse(StockReportPolicy.shouldShowReportButton(null))
        assertFalse(StockReportPolicy.shouldShowReportButton(""))
        assertFalse(StockReportPolicy.shouldShowReportButton("   "))
        assertTrue(StockReportPolicy.shouldShowReportButton("https://example.com/report/aapl"))
    }

    @Test
    fun testStockReportLabel_forcesDefaultLabelWhenEnglishOrNull() {
        // When title is null or blank
        assertEquals(
            "Xem báo cáo mới nhất",
            StockReportPolicy.resolveReportButtonLabel(null)
        )
        assertEquals(
            "Xem báo cáo mới nhất",
            StockReportPolicy.resolveReportButtonLabel("")
        )

        // When title is English, MUST NEVER display English title
        assertEquals(
            "Xem báo cáo mới nhất",
            StockReportPolicy.resolveReportButtonLabel("Apple Q3 Earnings Report and Financial Analysis")
        )
        assertEquals(
            "Xem báo cáo mới nhất",
            StockReportPolicy.resolveReportButtonLabel("NVIDIA Stock Surge Following AI Chip Demand")
        )
        assertEquals(
            "Xem báo cáo mới nhất",
            StockReportPolicy.resolveReportButtonLabel("Tesla Deliveries Beat Wall Street Estimates")
        )
    }

    @Test
    fun testStockReportLabel_preservesValidVietnameseTitle() {
        val vnTitle = "Báo cáo phân tích kết quả kinh doanh quý 3 của Apple"
        assertEquals(vnTitle, StockReportPolicy.resolveReportButtonLabel(vnTitle))

        val vnTitle2 = "Triển vọng tăng trưởng cổ phiếu Nvidia năm 2026"
        assertEquals(vnTitle2, StockReportPolicy.resolveReportButtonLabel(vnTitle2))
    }

    // -------------------------------------------------------------------------
    // 3. STOCK WATCHLIST MATCHER TESTS
    // -------------------------------------------------------------------------
    @Test
    fun testStockWatchlistMatcher_matchesEightStocksCorrectly() {
        val catalog = listOf(
            StockCatalogDto("AAPL", "Apple Inc."),
            StockCatalogDto("MSFT", "Microsoft Corporation"),
            StockCatalogDto("NVDA", "NVIDIA Corporation"),
            StockCatalogDto("TSLA", "Tesla, Inc.")
        )
        val watchlist = listOf(
            WatchlistItemDto(symbol = "AAPL", name = "Apple"),
            WatchlistItemDto(symbol = "TSLA", name = "Tesla")
        )

        val result = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, watchlist)
        assertEquals(4, result.size)

        val aapl = result.first { it.symbol == "AAPL" }
        assertTrue("AAPL must be marked as watchlisted", aapl.isWatchlisted)

        val msft = result.first { it.symbol == "MSFT" }
        assertFalse("MSFT must not be marked as watchlisted", msft.isWatchlisted)

        val nvda = result.first { it.symbol == "NVDA" }
        assertFalse("NVDA must not be marked as watchlisted", nvda.isWatchlisted)

        val tsla = result.first { it.symbol == "TSLA" }
        assertTrue("TSLA must be marked as watchlisted", tsla.isWatchlisted)
    }

    @Test
    fun testStockWatchlistMatcher_toggleLabels() {
        assertEquals("Đã quan tâm", StockWatchlistMatcher.toggleWatchlistAction(true))
        assertEquals("Quan tâm", StockWatchlistMatcher.toggleWatchlistAction(false))
    }

    // -------------------------------------------------------------------------
    // 4. PORTFOLIO VALUATION POLICY TESTS (FULLY VALUED = FALSE -> "—")
    // -------------------------------------------------------------------------
    @Test
    fun testPortfolioValuation_unvaluedWhenFullyValuedIsFalse() {
        val unvaluedPortfolio = PortfolioSummaryDto(
            cashBalanceUsd = 5000.0,
            initialBalanceUsd = 10000.0,
            totalHoldingsValue = null,
            totalNetWorth = null,
            totalPnL = null,
            totalPnLPercent = null,
            holdings = listOf(
                HoldingDto(symbol = "AAPL", quantity = 10.0, avgBuyPrice = 150.0, currentPrice = null, unrealizedPnL = null)
            ),
            fullyValued = false
        )

        assertFalse(PortfolioValuationPolicy.isFullyValued(unvaluedPortfolio))
        assertEquals("—", PortfolioValuationPolicy.formatTotalNetWorth(unvaluedPortfolio))
        assertEquals("—", PortfolioValuationPolicy.formatTotalHoldingsValue(unvaluedPortfolio))
        assertEquals("—", PortfolioValuationPolicy.formatTotalPnl(unvaluedPortfolio))
    }

    @Test
    fun testPortfolioValuation_valuedWhenFullyValuedIsTrue() {
        val fullyValuedPortfolio = PortfolioSummaryDto(
            cashBalanceUsd = 5000.0,
            initialBalanceUsd = 10000.0,
            totalHoldingsValue = 6000.0,
            totalNetWorth = 11000.0,
            totalPnL = 1000.0,
            totalPnLPercent = 10.0,
            holdings = listOf(
                HoldingDto(symbol = "AAPL", quantity = 10.0, avgBuyPrice = 150.0, currentPrice = 200.0, unrealizedPnL = 500.0)
            ),
            fullyValued = true
        )

        assertTrue(PortfolioValuationPolicy.isFullyValued(fullyValuedPortfolio))
        assertEquals("$11,000.00", PortfolioValuationPolicy.formatTotalNetWorth(fullyValuedPortfolio))
        assertEquals("$6,000.00", PortfolioValuationPolicy.formatTotalHoldingsValue(fullyValuedPortfolio))
        assertEquals("+$1,000.00 (+10.00%)", PortfolioValuationPolicy.formatTotalPnl(fullyValuedPortfolio))
    }

    @Test
    fun testHoldingValuation_unvaluedWhenCurrentPriceMissing() {
        val holdingNullPrice = HoldingDto(
            symbol = "AAPL",
            quantity = 5.0,
            avgBuyPrice = 150.0,
            currentPrice = null,
            unrealizedPnL = null
        )
        assertEquals("—", PortfolioValuationPolicy.formatHoldingCurrentPrice(holdingNullPrice))
        assertEquals("—", PortfolioValuationPolicy.formatHoldingPnl(holdingNullPrice))

        val holdingZeroPrice = HoldingDto(
            symbol = "MSFT",
            quantity = 2.0,
            avgBuyPrice = 300.0,
            currentPrice = 0.0,
            unrealizedPnL = -600.0
        )
        assertEquals("—", PortfolioValuationPolicy.formatHoldingCurrentPrice(holdingZeroPrice))
        assertEquals("—", PortfolioValuationPolicy.formatHoldingPnl(holdingZeroPrice))
    }

    @Test
    fun testHoldingValuation_formattedWhenCurrentPriceValid() {
        val validHolding = HoldingDto(
            symbol = "NVDA",
            quantity = 10.0,
            avgBuyPrice = 100.0,
            currentPrice = 125.50,
            unrealizedPnL = 255.0
        )
        assertEquals("$125.50", PortfolioValuationPolicy.formatHoldingCurrentPrice(validHolding))
        assertEquals("+$255.00", PortfolioValuationPolicy.formatHoldingPnl(validHolding))
    }

    // -------------------------------------------------------------------------
    // 5. NAVIGATION & LAYOUT STRUCTURE INTEGRITY TESTS
    // -------------------------------------------------------------------------
    @Test
    fun testBottomNavMenu_containsExactFiveModules() {
        val menuFile = File("src/main/res/menu/bottom_nav_menu.xml")
        assertTrue("bottom_nav_menu.xml must exist", menuFile.exists())
        val content = menuFile.readText()

        assertTrue("Must have nav_trading", content.contains("id=\"@+id/nav_trading\""))
        assertTrue("Must have nav_forecast", content.contains("id=\"@+id/nav_forecast\""))
        assertTrue("Must have nav_news", content.contains("id=\"@+id/nav_news\""))
        assertTrue("Must have nav_portfolio", content.contains("id=\"@+id/nav_portfolio\""))
        assertTrue("Must have nav_wallet", content.contains("id=\"@+id/nav_wallet\""))

        // Standalone nav_watchlist and old nav_profile must not be present in bottom nav
        assertFalse("Standalone nav_watchlist must be removed from bottom nav", content.contains("id=\"@+id/nav_watchlist\""))
        assertFalse("Old nav_profile must be replaced by nav_portfolio and nav_wallet", content.contains("id=\"@+id/nav_profile\""))
    }

    @Test
    fun testActivity2Layout_containsProfileAvatarButton() {
        val layoutFile = File("src/main/res/layout/layout_activity2.xml")
        assertTrue("layout_activity2.xml must exist", layoutFile.exists())
        val content = layoutFile.readText()

        assertTrue("Must have btn_profile_avatar in top bar", content.contains("id=\"@+id/btn_profile_avatar\""))
    }

    @Test
    fun testActivity2Layout_avatarTouchTargetAtLeast48dp() {
        val layoutFile = File("src/main/res/layout/layout_activity2.xml")
        assertTrue("layout_activity2.xml must exist", layoutFile.exists())
        val content = layoutFile.readText()

        assertTrue("Must have btn_profile_avatar in top bar", content.contains("id=\"@+id/btn_profile_avatar\""))
        assertTrue("Avatar touch target width must be 48dp", content.contains("android:layout_width=\"48dp\""))
        assertTrue("Avatar touch target height must be 48dp", content.contains("android:layout_height=\"48dp\""))
        assertTrue("Avatar touch target minWidth must be 48dp", content.contains("android:minWidth=\"48dp\""))
        assertTrue("Avatar touch target minHeight must be 48dp", content.contains("android:minHeight=\"48dp\""))
    }

    @Test
    fun testProfileDialogLayout_notHardcodedVersion() {
        val dialogFile = File("src/main/res/layout/dialog_profile.xml")
        assertTrue("dialog_profile.xml must exist", dialogFile.exists())
        val content = dialogFile.readText()

        assertTrue("Must contain profile dialog email textview", content.contains("tv_profile_dialog_email"))
        assertTrue("Must contain profile dialog version textview", content.contains("tv_profile_dialog_version"))
        assertTrue("Must use placeholder for version instead of hardcoding 1.1.21 in android:text", content.contains("android:text=\"—\""))
        assertFalse("Must not hardcode 1.1.21 in android:text", content.contains("android:text=\"1.1.21\""))
        assertTrue("Must contain logout button", content.contains("btn_dialog_logout"))
    }

    @Test
    fun testTradingLayout_containsTabsAndEmbeddedWatchlist() {
        val tradingFile = File("src/main/res/layout/fragment_trading.xml")
        assertTrue("fragment_trading.xml must exist", tradingFile.exists())
        val content = tradingFile.readText()

        assertTrue("Must contain tabLayoutTradingMode", content.contains("tabLayoutTradingMode"))
        assertTrue("Must contain rvStockCatalog", content.contains("rvStockCatalog"))
        assertTrue("Must contain rvEmbeddedWatchlist", content.contains("rvEmbeddedWatchlist"))
        assertTrue("Must contain tvTradeWarningMessage", content.contains("tvTradeWarningMessage"))
        assertTrue("Must contain btnTradingWatchlistToggle", content.contains("btnTradingWatchlistToggle"))
    }

    // -------------------------------------------------------------------------
    // 6. RC2 BEHAVIORAL TESTS: NULL-DATA, 401/403, FAILURE RETENTION & ROLLBACK
    // -------------------------------------------------------------------------
    @Test
    fun testPortfolioValuation_totalPnlPercentNullDisplaysEmDash() {
        val summaryWithNullPct = PortfolioSummaryDto(
            cashBalanceUsd = 5000.0,
            initialBalanceUsd = 10000.0,
            totalHoldingsValue = 6000.0,
            totalNetWorth = 11000.0,
            totalPnL = 1000.0,
            totalPnLPercent = null, // Missing PnL percent
            holdings = emptyList(),
            fullyValued = true
        )

        val formatted = PortfolioValuationPolicy.formatTotalPnl(summaryWithNullPct)
        // Must preserve em-dash for missing percentage, NOT format as "+0.00%"
        assertEquals("+$1,000.00 (—)", formatted)
        assertFalse("Must not format null percent as +0.00%", formatted.contains("0.00%"))
    }

    @Test
    fun testHoldingValuation_unrealizedPnLNullDisplaysEmDash() {
        val holdingWithNullPnl = HoldingDto(
            symbol = "AAPL",
            quantity = 10.0,
            avgBuyPrice = 150.0,
            currentPrice = 180.0,
            unrealizedPnL = null // Missing unrealized PnL
        )
        // Even if currentPrice exists, if unrealizedPnL is null it must display em-dash
        assertEquals("—", PortfolioValuationPolicy.formatHoldingPnl(holdingWithNullPnl))
    }

    @Test
    fun testHoldingValuation_quantityAndAvgBuyPriceNullDisplaysEmDash() {
        // When quantity is null, formatHoldingQuantity must return "—"
        assertEquals("—", PortfolioValuationPolicy.formatHoldingQuantity(null))

        // When quantity is valid, formats properly
        assertEquals("10.5000", PortfolioValuationPolicy.formatHoldingQuantity(10.5))
        assertEquals("0.0000", PortfolioValuationPolicy.formatHoldingQuantity(0.0))

        // When avgBuyPrice is null or <= 0, formatHoldingAvgPrice must return "—"
        assertEquals("—", PortfolioValuationPolicy.formatHoldingAvgPrice(null))
        assertEquals("—", PortfolioValuationPolicy.formatHoldingAvgPrice(0.0))
        assertEquals("—", PortfolioValuationPolicy.formatHoldingAvgPrice(-15.0))

        // When avgBuyPrice is positive, formats with dollar sign
        assertEquals("$150.25", PortfolioValuationPolicy.formatHoldingAvgPrice(150.25))
    }

    @Test
    fun testAuthHttpPolicy_identifiesUnauthorizedStatuses() {
        assertTrue("401 must be unauthorized", AuthHttpPolicy.isUnauthorized(401))
        assertTrue("403 must be unauthorized", AuthHttpPolicy.isUnauthorized(403))
        assertFalse("200 is not unauthorized", AuthHttpPolicy.isUnauthorized(200))
        assertFalse("400 is not unauthorized", AuthHttpPolicy.isUnauthorized(400))
        assertFalse("404 is not unauthorized", AuthHttpPolicy.isUnauthorized(404))
        assertFalse("500 is not unauthorized", AuthHttpPolicy.isUnauthorized(500))
        assertFalse("503 is not unauthorized", AuthHttpPolicy.isUnauthorized(503))
    }

    @Test
    fun testWatchlistStateReducer_retainsOldStateOnFailure() {
        val currentSymbols = setOf("AAPL", "MSFT")

        // Failed addition of NVDA
        val resultAfterFailedAdd = WatchlistStateReducer.reduce(
            currentSymbols = currentSymbols,
            mutation = WatchlistMutation.Add("NVDA"),
            isSuccess = false
        )
        assertEquals("Watchlist must remain unchanged on add failure", currentSymbols, resultAfterFailedAdd)
        assertFalse("NVDA must not be added when isSuccess is false", resultAfterFailedAdd.contains("NVDA"))

        // Failed removal of AAPL
        val resultAfterFailedRemove = WatchlistStateReducer.reduce(
            currentSymbols = currentSymbols,
            mutation = WatchlistMutation.Remove("AAPL"),
            isSuccess = false
        )
        assertEquals("Watchlist must remain unchanged on remove failure", currentSymbols, resultAfterFailedRemove)
        assertTrue("AAPL must still be present when isSuccess is false", resultAfterFailedRemove.contains("AAPL"))
    }

    @Test
    fun testWatchlistStateReducer_updatesStateOnSuccess() {
        val currentSymbols = setOf("AAPL", "MSFT")

        // Successful addition of NVDA
        val resultAfterAdd = WatchlistStateReducer.reduce(
            currentSymbols = currentSymbols,
            mutation = WatchlistMutation.Add("NVDA"),
            isSuccess = true
        )
        assertEquals(setOf("AAPL", "MSFT", "NVDA"), resultAfterAdd)

        // Successful removal of AAPL
        val resultAfterRemove = WatchlistStateReducer.reduce(
            currentSymbols = currentSymbols,
            mutation = WatchlistMutation.Remove("AAPL"),
            isSuccess = true
        )
        assertEquals(setOf("MSFT"), resultAfterRemove)
    }

    @Test
    fun testWatchlistStateReducer_aliasingRegressionWithMutableSet() {
        // 1. Regression test for failed Add with mutableSet: clear() + addAll()
        val mutableSetAdd = mutableSetOf("AAPL", "MSFT")
        val addResult = WatchlistStateReducer.reduce(
            currentSymbols = mutableSetAdd,
            mutation = WatchlistMutation.Add("NVDA"),
            isSuccess = false
        )
        // Mô phỏng chính xác production: clear() rồi addAll()
        mutableSetAdd.clear()
        mutableSetAdd.addAll(addResult)

        assertEquals("Failed add must preserve original symbols without clearing due to aliasing", setOf("AAPL", "MSFT"), mutableSetAdd)
        assertTrue("Must still contain AAPL", mutableSetAdd.contains("AAPL"))
        assertTrue("Must still contain MSFT", mutableSetAdd.contains("MSFT"))
        assertFalse("Must not contain NVDA", mutableSetAdd.contains("NVDA"))

        // 2. Regression test for failed Remove with mutableSet: clear() + addAll()
        val mutableSetRemove = mutableSetOf("AAPL", "MSFT")
        val removeResult = WatchlistStateReducer.reduce(
            currentSymbols = mutableSetRemove,
            mutation = WatchlistMutation.Remove("AAPL"),
            isSuccess = false
        )
        // Mô phỏng chính xác production: clear() rồi addAll()
        mutableSetRemove.clear()
        mutableSetRemove.addAll(removeResult)

        assertEquals("Failed remove must preserve original symbols without clearing due to aliasing", setOf("AAPL", "MSFT"), mutableSetRemove)
        assertTrue("AAPL must remain in set after failed remove", mutableSetRemove.contains("AAPL"))
        assertTrue("MSFT must remain in set after failed remove", mutableSetRemove.contains("MSFT"))
    }

    @Test
    fun testStockWatchlistMatcher_preservesStateWhenWatchlistFetchFails() {
        val catalog = listOf(
            StockCatalogDto("AAPL", "Apple"),
            StockCatalogDto("TSLA", "Tesla")
        )
        // Cached watchlist from previous successful load
        val cachedSymbols = setOf("AAPL")
        val cachedWatchlistItems = cachedSymbols.map { WatchlistItemDto(symbol = it) }

        // When fresh fetch fails, fallback to cached items
        val uiModels = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, cachedWatchlistItems)
        val aapl = uiModels.first { it.symbol == "AAPL" }
        val tsla = uiModels.first { it.symbol == "TSLA" }

        assertTrue("AAPL must remain watchlisted on fallback", aapl.isWatchlisted)
        assertFalse("TSLA must not be watchlisted on fallback", tsla.isWatchlisted)
    }
}
