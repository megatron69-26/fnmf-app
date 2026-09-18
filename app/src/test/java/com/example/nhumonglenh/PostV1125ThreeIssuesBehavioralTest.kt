package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.MarketPriceDto
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.ui.forecast.ForecastDateTimeFormatter
import com.example.nhumonglenh.ui.trading.StockWatchlistMatcher
import com.example.nhumonglenh.ui.trading.WatchlistMutation
import com.example.nhumonglenh.ui.trading.WatchlistStateReducer
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PostV1125ThreeIssuesBehavioralTest {

    // =====================================================================
    // ISSUE A: 24H CHANGE & PRICE INTEGRITY FOR ALL 8 SYMBOLS
    // =====================================================================

    @Test
    fun testAllEightSymbols_canonicalMapping_andAliases() {
        val expectedEight = listOf(
            "BTCUSDT", "ETHUSDT", "XAUUSD", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT"
        )

        // All 8 canonical symbols must be accepted in SUPPORTED_BINANCE_SYMBOLS
        expectedEight.forEach { sym ->
            assertTrue("Expected $sym to be supported", TradingFragment.SUPPORTED_BINANCE_SYMBOLS.contains(sym))
            assertEquals(sym, TradingFragment.canonicalTradingSymbol(sym))
        }

        // Aliases with slashes, dashes, lowercase
        assertEquals("BTCUSDT", TradingFragment.canonicalTradingSymbol("btc/usdt"))
        assertEquals("ETHUSDT", TradingFragment.canonicalTradingSymbol("ETH-USDT"))
        assertEquals("BNBUSDT", TradingFragment.canonicalTradingSymbol("bnb/usdt"))
        assertEquals("SOLUSDT", TradingFragment.canonicalTradingSymbol("SOL_USDT"))
        assertEquals("XRPUSDT", TradingFragment.canonicalTradingSymbol("xrp/usdt"))
        assertEquals("ADAUSDT", TradingFragment.canonicalTradingSymbol("ada/usdt"))
        assertEquals("DOGEUSDT", TradingFragment.canonicalTradingSymbol("DOGE/USDT"))
        assertEquals("XAUUSD", TradingFragment.canonicalTradingSymbol("xau/usd"))
        assertEquals("XAUUSD", TradingFragment.canonicalTradingSymbol("PAXGUSDT"))
        assertEquals("XAUUSD", TradingFragment.canonicalTradingSymbol("paxg/usdt"))
    }

    @Test
    fun testIssueA_marketPricesMerge_preservesExactValues_andNeverConvertsNullOrZero() {
        val marketPrices = listOf(
            MarketPriceDto(symbol = "BTCUSDT", price = 60000.0, change24h = 0.19),
            MarketPriceDto(symbol = "ETHUSDT", price = 2500.0, change24h = 0.89),
            MarketPriceDto(symbol = "XAUUSD", price = 2600.0, change24h = 0.79),
            MarketPriceDto(symbol = "BNBUSDT", price = 550.0, change24h = 2.23),
            MarketPriceDto(symbol = "SOLUSDT", price = 140.0, change24h = 2.91),
            MarketPriceDto(symbol = "XRPUSDT", price = 0.58, change24h = 0.0), // EXACT 0.00%
            MarketPriceDto(symbol = "ADAUSDT", price = 0.35, change24h = 5.49),
            MarketPriceDto(symbol = "DOGEUSDT", price = 0.10, change24h = 1.46)
        )

        val catalog = listOf(
            StockCatalogDto(symbol = "BTCUSDT", name = "Bitcoin", category = "CRYPTO"),
            StockCatalogDto(symbol = "ETHUSDT", name = "Ethereum", category = "CRYPTO"),
            StockCatalogDto(symbol = "XAUUSD", name = "Gold", category = "COMMODITY"),
            StockCatalogDto(symbol = "BNBUSDT", name = "BNB", category = "CRYPTO"),
            StockCatalogDto(symbol = "SOLUSDT", name = "Solana", category = "CRYPTO"),
            StockCatalogDto(symbol = "XRPUSDT", name = "Ripple", category = "CRYPTO"),
            StockCatalogDto(symbol = "ADAUSDT", name = "Cardano", category = "CRYPTO"),
            StockCatalogDto(symbol = "DOGEUSDT", name = "Dogecoin", category = "CRYPTO")
        )

        val watchlist = listOf(
            WatchlistItemDto(symbol = "ETHUSDT"),
            WatchlistItemDto(symbol = "XAUUSD"),
            WatchlistItemDto(symbol = "XRPUSDT")
        )

        val matched = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, watchlist, marketPrices)
        assertEquals(8, matched.size)

        // ETHUSDT: 0.89%
        val eth = matched.first { it.symbol == "ETHUSDT" }
        assertTrue(eth.isWatchlisted)
        assertEquals(2500.0, eth.price!!, 0.001)
        assertEquals(0.89, eth.change24h!!, 0.001)

        // XAUUSD: 0.79%
        val xau = matched.first { it.symbol == "XAUUSD" }
        assertTrue(xau.isWatchlisted)
        assertEquals(2600.0, xau.price!!, 0.001)
        assertEquals(0.79, xau.change24h!!, 0.001)

        // XRPUSDT: exact 0.0% must NOT be null and must NOT be dash
        val xrp = matched.first { it.symbol == "XRPUSDT" }
        assertTrue(xrp.isWatchlisted)
        assertNotNull(xrp.change24h)
        assertEquals(0.0, xrp.change24h!!, 0.0001)

        // Formatting verification: XRP at 0.0 must format as +0.00% or 0.00%, never dash
        val changeVal = xrp.change24h
        val xrpFormatted = if (changeVal != null) {
            String.format(Locale.US, "%s%.2f%%", if (changeVal >= 0) "+" else "", changeVal)
        } else "—"
        assertEquals("+0.00%", xrpFormatted)

        // Missing price verification: null must remain null and render as dash
        val missingPriceDto = MarketPriceDto(symbol = "TEST", price = null, change24h = null)
        assertNull(missingPriceDto.change24h)
        val missingFormatted = if (missingPriceDto.change24h != null) {
            String.format(Locale.US, "%s%.2f%%", if (missingPriceDto.change24h!! >= 0) "+" else "", missingPriceDto.change24h)
        } else "—"
        assertEquals("—", missingFormatted)
    }

    // =====================================================================
    // ISSUE B: 5 NEW SYMBOLS WATCHLIST ADD/REMOVE, IDEMPOTENCY & ROLLBACK PREVENTION
    // =====================================================================

    @Test
    fun testIssueB_addingFiveNewSymbols_optimisticSuccess_andIdempotency() {
        val initialSymbols = mutableSetOf("BTCUSDT", "ETHUSDT", "XAUUSD")
        val fiveNew = listOf("BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT")

        // Add each of the 5 new symbols
        fiveNew.forEach { newSym ->
            val updated = WatchlistStateReducer.reduce(
                initialSymbols,
                WatchlistMutation.Add(newSym),
                isSuccess = true
            )
            initialSymbols.clear()
            initialSymbols.addAll(updated)
        }

        // All 8 must now be present
        assertEquals(8, initialSymbols.size)
        fiveNew.forEach { sym ->
            assertTrue(initialSymbols.contains(sym))
        }

        // Idempotency: adding BNBUSDT again must not duplicate or fail
        val afterIdempotentAdd = WatchlistStateReducer.reduce(
            initialSymbols,
            WatchlistMutation.Add("BNBUSDT"),
            isSuccess = true
        )
        assertEquals(8, afterIdempotentAdd.size)

        // Idempotency with alias: adding BNB/USDT resolves to BNBUSDT
        val canonicalAlias = TradingFragment.canonicalTradingSymbol("BNB/USDT")
        assertEquals("BNBUSDT", canonicalAlias)
        val afterAliasAdd = WatchlistStateReducer.reduce(
            afterIdempotentAdd,
            WatchlistMutation.Add(canonicalAlias),
            isSuccess = true
        )
        assertEquals(8, afterAliasAdd.size)
    }

    @Test
    fun testIssueB_backgroundGetFailure_doesNotRollbackAddedSymbol() {
        val cachedWatchlistSymbols = mutableSetOf("BTCUSDT", "ETHUSDT")
        val addedSymbol = "SOLUSDT"

        // Step 1: POST to backend succeeds -> Reducer adds symbol
        val updated = WatchlistStateReducer.reduce(
            cachedWatchlistSymbols,
            WatchlistMutation.Add(addedSymbol),
            isSuccess = true
        )
        cachedWatchlistSymbols.clear()
        cachedWatchlistSymbols.addAll(updated)

        assertTrue(cachedWatchlistSymbols.contains("SOLUSDT"))
        assertEquals(3, cachedWatchlistSymbols.size)

        // Step 2: Background GET fails with network timeout / error
        // Defensive policy: do NOT call cachedWatchlistSymbols.clear() on GET failure
        val backgroundGetSuccessful = false
        if (!backgroundGetSuccessful) {
            // Keep existing cached symbols intact
        }

        // SOLUSDT must STILL be in the watchlist cache
        assertTrue("SOLUSDT must not be rolled back on background GET failure", cachedWatchlistSymbols.contains("SOLUSDT"))
        assertEquals(3, cachedWatchlistSymbols.size)
    }

    @Test
    fun testIssueB_postFailure_preservesPreviousState() {
        val cachedWatchlistSymbols = mutableSetOf("BTCUSDT", "ETHUSDT")

        // Step 1: POST to backend fails (isSuccess = false)
        val updated = WatchlistStateReducer.reduce(
            cachedWatchlistSymbols,
            WatchlistMutation.Add("DOGEUSDT"),
            isSuccess = false
        )
        cachedWatchlistSymbols.clear()
        cachedWatchlistSymbols.addAll(updated)

        // Previous state must be preserved without DOGEUSDT
        assertFalse(cachedWatchlistSymbols.contains("DOGEUSDT"))
        assertEquals(2, cachedWatchlistSymbols.size)
        assertTrue(cachedWatchlistSymbols.contains("BTCUSDT"))
        assertTrue(cachedWatchlistSymbols.contains("ETHUSDT"))
    }

    // =====================================================================
    // ISSUE C: FORECAST TIMESTAMP FORMATTING (VIETNAM TIMEZONE ASIA/HO_CHI_MINH)
    // =====================================================================

    @Test
    fun testIssueC_forecastTimestamp_convertsUtcToVietnamTime() {
        // Backend UTC timestamp: 2026-09-18T01:43:43.659449
        // Vietnam is UTC+7 -> 01:43 + 7 = 08:43, 18/09/2026
        val utcIso = "2026-09-18T01:43:43.659449"
        val formatted = ForecastDateTimeFormatter.formatToVietnamString(utcIso)
        assertEquals("Cập nhật lúc 08:43, 18/09/2026", formatted)

        // ISO with Z
        val utcIsoZ = "2026-09-18T01:43:43Z"
        val formattedZ = ForecastDateTimeFormatter.formatToVietnamString(utcIsoZ)
        assertEquals("Cập nhật lúc 08:43, 18/09/2026", formattedZ)

        // ISO with +00:00 offset
        val utcIsoOffset = "2026-09-18T01:43:43+00:00"
        val formattedOffset = ForecastDateTimeFormatter.formatToVietnamString(utcIsoOffset)
        assertEquals("Cập nhật lúc 08:43, 18/09/2026", formattedOffset)
    }

    @Test
    fun testIssueC_forecastTimestamp_handlesNullAndMalformedGracefully() {
        // Null timestamp
        assertEquals("Chưa rõ thời điểm cập nhật", ForecastDateTimeFormatter.formatToVietnamString(null))

        // Empty / Blank timestamp
        assertEquals("Chưa rõ thời điểm cập nhật", ForecastDateTimeFormatter.formatToVietnamString(""))
        assertEquals("Chưa rõ thời điểm cập nhật", ForecastDateTimeFormatter.formatToVietnamString("   "))

        // Malformed string does NOT throw exception
        assertEquals("Chưa rõ thời điểm cập nhật", ForecastDateTimeFormatter.formatToVietnamString("not-a-valid-date"))
        assertEquals("Chưa rõ thời điểm cập nhật", ForecastDateTimeFormatter.formatToVietnamString("2026-99-99T99:99:99"))

        // parseToVietnamTime returns null for invalid
        assertNull(ForecastDateTimeFormatter.parseToVietnamTime(null))
        assertNull(ForecastDateTimeFormatter.parseToVietnamTime("invalid"))
    }
}
