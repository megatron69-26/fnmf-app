package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.CandleDto
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.MarketPriceDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.ui.trading.BinanceKlineEvent
import com.example.nhumonglenh.ui.trading.CandleSeriesReducer
import com.example.nhumonglenh.ui.trading.CandleTimeFormatter
import com.example.nhumonglenh.ui.trading.StockWatchlistMatcher
import org.junit.Assert.*
import org.junit.Test

class CatalogAndChartBehavioralTest {

    // =====================================================================
    // 1. CATALOG INDEPENDENT OF WATCHLIST
    // =====================================================================

    @Test
    fun testCatalog_displaysAll5Assets_whenWatchlistEmpty() {
        val catalog = listOf(
            StockCatalogDto("BNBUSDT", "BNB"),
            StockCatalogDto("SOLUSDT", "Solana"),
            StockCatalogDto("XRPUSDT", "XRP"),
            StockCatalogDto("ADAUSDT", "Cardano"),
            StockCatalogDto("DOGEUSDT", "Dogecoin")
        )
        val result = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, emptyList(), emptyList())
        assertEquals(5, result.size)
        assertEquals("BNBUSDT", result[0].symbol)
        assertEquals("DOGEUSDT", result[4].symbol)
        // All must show isWatchlisted = false
        result.forEach { assertFalse(it.isWatchlisted) }
        // Prices must be null (not zero)
        result.forEach { assertNull(it.price) }
    }

    @Test
    fun testCatalog_displaysAll5Assets_whenPricesFail() {
        val catalog = listOf(
            StockCatalogDto("BNBUSDT", "BNB"),
            StockCatalogDto("SOLUSDT", "Solana"),
            StockCatalogDto("XRPUSDT", "XRP"),
            StockCatalogDto("ADAUSDT", "Cardano"),
            StockCatalogDto("DOGEUSDT", "Dogecoin")
        )
        // null prices simulates network failure
        val result = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, emptyList(), null)
        assertEquals(5, result.size)
        result.forEach { assertNull(it.price) }
    }

    @Test
    fun testCatalog_displaysAll5Assets_whenWatchlist401() {
        val catalog = listOf(
            StockCatalogDto("BNBUSDT", "BNB"),
            StockCatalogDto("SOLUSDT", "Solana"),
            StockCatalogDto("XRPUSDT", "XRP"),
            StockCatalogDto("ADAUSDT", "Cardano"),
            StockCatalogDto("DOGEUSDT", "Dogecoin")
        )
        val prices = listOf(
            MarketPriceDto(symbol = "BNBUSDT", price = 600.0, change24h = 3.5),
            MarketPriceDto(symbol = "SOLUSDT", price = 150.0, change24h = -2.1)
        )
        // Watchlist failure (401) means empty watchlist, catalog must still show all 5 with prices
        val result = StockWatchlistMatcher.matchCatalogWithWatchlist(catalog, emptyList(), prices)
        assertEquals(5, result.size)
        assertEquals(600.0, result[0].price!!, 0.001)
        assertEquals(150.0, result[1].price!!, 0.001)
        assertNull(result[2].price) // XRPUSDT has no price data
        result.forEach { assertFalse(it.isWatchlisted) }
    }

    // =====================================================================
    // 2. CANDLE SERIES REDUCER - SAME MINUTE UPDATE
    // =====================================================================

    @Test
    fun testReducer_sameMinute_updatesLastCandle() {
        val openTime = 1695000000000L
        val initial = listOf(
            CandleDto(time = "2026-09-17 10:00:00", open = 100.0, high = 105.0, low = 99.0, close = 103.0, volume = 500.0, openTime = openTime)
        )
        val tick = BinanceKlineEvent(
            openTime = openTime, closeTime = openTime + 59999,
            symbol = "BTCUSDT", interval = "1m",
            open = 100.0, high = 108.0, low = 98.0, close = 106.0,
            volume = 750.0, isClosed = false
        )
        val result = CandleSeriesReducer.reduce(initial, tick)
        assertEquals(1, result.size)
        assertEquals(108.0, result[0].high, 0.001)
        assertEquals(98.0, result[0].low, 0.001)
        assertEquals(106.0, result[0].close, 0.001)
        assertEquals(750.0, result[0].volume, 0.001)
    }

    @Test
    fun testReducer_newMinute_addsNewCandle_dropsFirst_when30() {
        val baseTime = 1695000000000L
        val series = (0 until 30).map { i ->
            CandleDto(
                time = CandleTimeFormatter.formatDateTime(baseTime + i * 60000L),
                open = 100.0 + i, high = 105.0 + i, low = 99.0 + i, close = 103.0 + i,
                volume = 500.0, openTime = baseTime + i * 60000L
            )
        }
        assertEquals(30, series.size)

        val newMinuteTime = baseTime + 30 * 60000L
        val tick = BinanceKlineEvent(
            openTime = newMinuteTime, closeTime = newMinuteTime + 59999,
            symbol = "BTCUSDT", interval = "1m",
            open = 130.0, high = 135.0, low = 129.0, close = 133.0,
            volume = 600.0, isClosed = false
        )
        val result = CandleSeriesReducer.reduce(series, tick)
        assertEquals(30, result.size)
        // First candle should be the second original candle (index 1)
        assertEquals(baseTime + 1 * 60000L, result[0].openTime)
        // Last candle should be the new one
        assertEquals(newMinuteTime, result[29].openTime)
        assertEquals(133.0, result[29].close, 0.001)
    }

    @Test
    fun testReducer_newMinute_timeLabelChanges() {
        val baseTime = 1695000000000L
        val series = listOf(
            CandleDto(time = CandleTimeFormatter.formatDateTime(baseTime), open = 100.0, high = 105.0, low = 99.0, close = 103.0, volume = 500.0, openTime = baseTime)
        )
        val newMinuteTime = baseTime + 60000L
        val tick = BinanceKlineEvent(
            openTime = newMinuteTime, closeTime = newMinuteTime + 59999,
            symbol = "BTCUSDT", interval = "1m",
            open = 103.0, high = 106.0, low = 102.0, close = 105.0,
            volume = 300.0, isClosed = false
        )
        val result = CandleSeriesReducer.reduce(series, tick)
        assertEquals(2, result.size)
        // Time labels must differ
        assertNotEquals(result[0].time, result[1].time)
    }

    @Test
    fun testReducer_oldEvent_ignored() {
        val baseTime = 1695000000000L
        val series = listOf(
            CandleDto(time = "2026-09-17 10:00:00", open = 100.0, high = 105.0, low = 99.0, close = 103.0, volume = 500.0, openTime = baseTime),
            CandleDto(time = "2026-09-17 10:01:00", open = 103.0, high = 108.0, low = 102.0, close = 106.0, volume = 600.0, openTime = baseTime + 60000L)
        )
        val oldTick = BinanceKlineEvent(
            openTime = baseTime - 60000L, closeTime = baseTime - 1,
            symbol = "BTCUSDT", interval = "1m",
            open = 90.0, high = 95.0, low = 89.0, close = 93.0,
            volume = 100.0, isClosed = true
        )
        val result = CandleSeriesReducer.reduce(series, oldTick)
        assertEquals(2, result.size)
        // Should be unchanged
        assertEquals(106.0, result[1].close, 0.001)
    }
}
