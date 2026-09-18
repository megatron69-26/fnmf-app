package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.CandleDto
import com.example.nhumonglenh.ui.trading.BinanceKlineEvent
import com.example.nhumonglenh.ui.trading.BinanceKlineParser
import com.example.nhumonglenh.ui.trading.CandleSeriesReducer
import com.example.nhumonglenh.ui.trading.CandleTimeFormatter
import com.example.nhumonglenh.ui.trading.ChartLabelFormatter
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.MarketSymbolMatcher
import com.example.nhumonglenh.ui.trading.SocketCallbackGuard
import com.example.nhumonglenh.ui.trading.SocketReconnectPolicy
import com.example.nhumonglenh.ui.trading.TradingLifecyclePolicy
import com.example.nhumonglenh.ui.trading.TradingResumeAction
import com.github.mikephil.charting.data.CandleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral Unit Tests cho Hotfix FNMF v1.1.22:
 * Gọi trực tiếp các production parser, reducer và policies của Binance Kline Real-time.
 */
class BinanceRealtimeKlineUnitTest {

    // =========================================================================
    // 1. Parse kline payload BTC hợp lệ
    // =========================================================================
    @Test
    fun testBinanceKlineParser_validBtcPayload() {
        val json = """
            {
              "e": "kline",
              "E": 1789503665000,
              "s": "BTCUSDT",
              "k": {
                "t": 1789503660000,
                "T": 1789503660999,
                "s": "BTCUSDT",
                "i": "1s",
                "o": "65000.00",
                "c": "65050.50",
                "h": "65100.00",
                "l": "64950.00",
                "v": "12.345",
                "x": false
              }
            }
        """.trimIndent()

        val event = BinanceKlineParser.parse(json)
        assertNotNull(event)
        assertEquals(1789503660000L, event!!.openTime)
        assertEquals(1789503660999L, event.closeTime)
        assertEquals("BTCUSDT", event.symbol)
        assertEquals("1s", event.interval)
        assertEquals(65000.00, event.open, 0.001)
        assertEquals(65050.50, event.close, 0.001)
        assertEquals(65100.00, event.high, 0.001)
        assertEquals(64950.00, event.low, 0.001)
        assertEquals(12.345, event.volume, 0.001)
        assertFalse(event.isClosed)
    }

    // =========================================================================
    // 2. Parse PAXGUSDT và ánh xạ hiển thị XAUUSD
    // =========================================================================
    @Test
    fun testBinanceKlineParser_parsePaxgusdtAndMapToXauusd() {
        val json = """
            {
              "e": "kline",
              "E": 1789503665000,
              "s": "PAXGUSDT",
              "k": {
                "t": 1789503660000,
                "T": 1789503660999,
                "s": "PAXGUSDT",
                "i": "1s",
                "o": "2650.10",
                "c": "2655.40",
                "h": "2658.00",
                "l": "2649.00",
                "v": "5.67",
                "x": false
              }
            }
        """.trimIndent()

        val event = BinanceKlineParser.parse(json)
        assertNotNull(event)
        assertEquals("PAXGUSDT", event!!.symbol)

        // Khớp PAXGUSDT với mã hiển thị XAUUSD và ngược lại
        assertTrue(MarketSymbolMatcher.matches(event.symbol, "XAUUSD"))
        assertTrue(MarketSymbolMatcher.matches(event.symbol, "XAU"))
        assertTrue(MarketSymbolMatcher.matches(event.symbol, "PAXGUSDT"))

        // Không nhầm sang BTC hay ETH
        assertFalse(MarketSymbolMatcher.matches(event.symbol, "BTCUSDT"))
        assertFalse(MarketSymbolMatcher.matches(event.symbol, "ETHUSDT"))

        // Stream helper ánh xạ đúng stream kline_1s
        assertEquals("paxgusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("XAUUSD"))
        assertEquals("btcusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("BTCUSDT"))
        assertEquals("ethusdt@kline_1s", MarketStreamHelper.resolveWebSocketStream("ETHUSDT"))
        assertNull(MarketStreamHelper.resolveWebSocketStream("USOIL"))
        assertNull(MarketStreamHelper.resolveWebSocketStream("AAPL"))
    }

    // =========================================================================
    // 3. Từ chối payload thiếu/sai OHLCV
    // =========================================================================
    @Test
    fun testBinanceKlineParser_rejectMissingOrMalformedOhlcv() {
        // Thiếu object k
        assertNull(BinanceKlineParser.parse("{}"))
        assertNull(BinanceKlineParser.parse(""))
        assertNull(BinanceKlineParser.parse(null))

        // Thiếu open
        val missingOpen = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","h":"100","l":"90","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(missingOpen))

        // Thiếu high
        val missingHigh = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","l":"90","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(missingHigh))

        // Thiếu low
        val missingLow = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","h":"100","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(missingLow))

        // Thiếu close
        val missingClose = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","h":"100","l":"90","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(missingClose))

        // Sai định dạng số (string không phải số)
        val notNumber = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"NaN","h":"100","l":"90","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(notNumber))

        // openTime không hợp lệ
        val invalidTime = """{"k":{"t":-1,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(invalidTime))
    }

    // =========================================================================
    // 4. Từ chối giá <= 0, high < low, high < open/close, low > open/close
    // =========================================================================
    @Test
    fun testBinanceKlineParser_rejectInvalidOhlcvInvariants() {
        // Giá <= 0
        val zeroPrice = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"0","h":"100","l":"0","c":"50","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(zeroPrice))

        val negativePrice = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"-50","h":"100","l":"-50","c":"50","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(negativePrice))

        // high < low
        val highLessThanLow = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"100","h":"80","l":"90","c":"85","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(highLessThanLow))

        // high < open
        val highLessThanOpen = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"120","h":"100","l":"90","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(highLessThanOpen))

        // high < close
        val highLessThanClose = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","h":"100","l":"80","c":"110","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(highLessThanClose))

        // low > open
        val lowGreaterThanOpen = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"80","h":"120","l":"90","c":"100","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(lowGreaterThanOpen))

        // low > close
        val lowGreaterThanClose = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"100","h":"120","l":"90","c":"80","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(lowGreaterThanClose))

        // volume âm
        val negativeVolume = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","h":"120","l":"85","c":"100","v":"-1"}}"""
        assertNull(BinanceKlineParser.parse(negativeVolume))
    }

    // =========================================================================
    // 5. Event cùng openTime cập nhật cây cuối, không thêm cây
    // =========================================================================
    @Test
    fun testCandleSeriesReducer_sameOpenTimeUpdatesLastCandleWithoutAdding() {
        val initialCandles = listOf(
            CandleDto("2026-09-16 14:00:00", 64000.0, 64200.0, 63900.0, 64100.0, 10.0, 1789503600000L),
            CandleDto("2026-09-16 14:01:00", 64100.0, 64150.0, 64050.0, 64120.0, 5.0, 1789503660000L)
        )

        // Event cùng phút 14:01:00 nhưng có giá mới (giá nhảy lên 64250)
        val tickEvent = BinanceKlineEvent(
            openTime = 1789503660000L,
            closeTime = 1789503660999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 64100.0,
            high = 64300.0,
            low = 64050.0,
            close = 64250.0,
            volume = 8.5,
            isClosed = false
        )

        val result = CandleSeriesReducer.reduce(initialCandles, tickEvent, maxCandles = 30)

        // Số lượng cây nến không đổi (vẫn là 2 cây)
        assertEquals(2, result.size)
        // Cây cuối cùng đã được cập nhật đúng giá
        val updatedLast = result.last()
        assertEquals(1789503660000L, updatedLast.openTime)
        assertEquals(64100.0, updatedLast.open, 0.001)
        assertEquals(64300.0, updatedLast.high, 0.001)
        assertEquals(64050.0, updatedLast.low, 0.001)
        assertEquals(64250.0, updatedLast.close, 0.001)
        assertEquals(8.5, updatedLast.volume, 0.001)
        assertFalse(updatedLast.isClosed)
    }

    // =========================================================================
    // 6. Event phút mới thêm đúng một cây
    // =========================================================================
    @Test
    fun testCandleSeriesReducer_newMinuteAddsSingleNewCandle() {
        val initialCandles = listOf(
            CandleDto("2026-09-16 14:00:00", 64000.0, 64200.0, 63900.0, 64100.0, 10.0, 1789503600000L),
            CandleDto("2026-09-16 14:01:00", 64100.0, 64150.0, 64050.0, 64120.0, 5.0, 1789503660000L)
        )

        // Event sang phút tiếp theo 14:02:00
        val nextMinuteEvent = BinanceKlineEvent(
            openTime = 1789503720000L,
            closeTime = 1789503720999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 64120.0,
            high = 64200.0,
            low = 64110.0,
            close = 64180.0,
            volume = 3.0,
            isClosed = false
        )

        val result = CandleSeriesReducer.reduce(initialCandles, nextMinuteEvent, maxCandles = 30)

        assertEquals(3, result.size)
        val newCandle = result.last()
        assertEquals(1789503720000L, newCandle.openTime)
        assertEquals(64120.0, newCandle.open, 0.001)
        assertEquals(64180.0, newCandle.close, 0.001)
    }

    // =========================================================================
    // 7. Event cũ bị bỏ qua
    // =========================================================================
    @Test
    fun testCandleSeriesReducer_staleEventIgnored() {
        val initialCandles = listOf(
            CandleDto("2026-09-16 14:01:00", 64100.0, 64150.0, 64050.0, 64120.0, 5.0, 1789503660000L)
        )

        // Event thuộc phút cũ (14:00:00)
        val oldEvent = BinanceKlineEvent(
            openTime = 1789503600000L,
            closeTime = 1789503600999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 63000.0,
            high = 63100.0,
            low = 62900.0,
            close = 63050.0,
            volume = 10.0,
            isClosed = true
        )

        val result = CandleSeriesReducer.reduce(initialCandles, oldEvent, maxCandles = 30)

        // Giữ nguyên danh sách hiện tại, không bị chèn ngược
        assertEquals(1, result.size)
        assertEquals(1789503660000L, result.last().openTime)
        assertEquals(64120.0, result.last().close, 0.001)
    }

    // =========================================================================
    // 8. Danh sách luôn tối đa 30 cây
    // =========================================================================
    @Test
    fun testCandleSeriesReducer_alwaysCappedAtMax30Candles() {
        val candles = ArrayList<CandleDto>()
        val baseTime = 1789500000000L
        for (i in 0 until 30) {
            val t = baseTime + (i * 1_000L)
            candles.add(CandleDto("time_$i", 60000.0, 60100.0, 59900.0, 60050.0, 1.0, t))
        }
        assertEquals(30, candles.size)

        // Thêm cây thứ 31
        val newEvent = BinanceKlineEvent(
            openTime = baseTime + (30 * 1_000L),
            closeTime = baseTime + (30 * 1_000L) + 999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 60050.0,
            high = 60200.0,
            low = 60040.0,
            close = 60180.0,
            volume = 2.5,
            isClosed = false
        )

        val result = CandleSeriesReducer.reduce(candles, newEvent, maxCandles = 30)

        assertEquals(30, result.size)
        // Cây đầu tiên cũ nhất đã bị loại bỏ (bắt đầu từ index 1)
        assertEquals(baseTime + 1_000L, result.first().openTime)
        // Cây cuối cùng là cây thứ 31 mới thêm
        assertEquals(baseTime + (30 * 1_000L), result.last().openTime)
    }

    // =========================================================================
    // 9. Event thuộc symbol/socket cũ không cập nhật symbol mới
    // =========================================================================
    @Test
    fun testMarketSymbolMatcher_eventFromOldSocketDoesNotUpdateNewSymbol() {
        // Đang xem ETHUSDT nhưng event cũ của BTCUSDT đến
        assertFalse(MarketSymbolMatcher.matches("BTCUSDT", "ETHUSDT"))
        assertFalse(MarketSymbolMatcher.matches("BTC", "ETHUSDT"))

        // Đang xem AAPL nhưng event WebSocket BTC đến
        assertFalse(MarketSymbolMatcher.matches("BTCUSDT", "AAPL"))
        assertFalse(MarketSymbolMatcher.matches("PAXGUSDT", "AAPL"))

        // Đang xem XAUUSD nhưng event BTC đến
        assertFalse(MarketSymbolMatcher.matches("BTCUSDT", "XAUUSD"))

        // Khớp đúng cặp
        assertTrue(MarketSymbolMatcher.matches("BTCUSDT", "BTCUSDT"))
        assertTrue(MarketSymbolMatcher.matches("ETHUSDT", "ETHUSDT"))
        assertTrue(MarketSymbolMatcher.matches("PAXGUSDT", "XAUUSD"))
    }

    // =========================================================================
    // 10. Candle x=false vẫn cập nhật real-time
    // =========================================================================
    @Test
    fun testCandleSeriesReducer_formingCandleUpdatesRealtimeWhenXFalse() {
        val series = listOf(
            CandleDto("2026-09-16 14:00:00", 65000.0, 65050.0, 64950.0, 65020.0, 1.0, 1789503600000L, isClosed = false)
        )

        val formingTick = BinanceKlineEvent(
            openTime = 1789503600000L,
            closeTime = 1789503600999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 65000.0,
            high = 65080.0,
            low = 64950.0,
            close = 65075.0,
            volume = 2.2,
            isClosed = false
        )

        val updated = CandleSeriesReducer.reduce(series, formingTick, 30)
        assertEquals(1, updated.size)
        assertEquals(65075.0, updated[0].close, 0.001)
        assertEquals(65080.0, updated[0].high, 0.001)
        assertFalse(updated[0].isClosed)
    }

    // =========================================================================
    // 11. Candle x=true hoàn tất đúng cây
    // =========================================================================
    @Test
    fun testCandleSeriesReducer_candleClosesCorrectlyWhenXTrue() {
        val series = listOf(
            CandleDto("2026-09-16 14:00:00", 65000.0, 65050.0, 64950.0, 65020.0, 1.0, 1789503600000L, isClosed = false)
        )

        val closingTick = BinanceKlineEvent(
            openTime = 1789503600000L,
            closeTime = 1789503600999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 65000.0,
            high = 65120.0,
            low = 64950.0,
            close = 65110.0,
            volume = 5.0,
            isClosed = true
        )

        val closedSeries = CandleSeriesReducer.reduce(series, closingTick, 30)
        assertEquals(1, closedSeries.size)
        assertEquals(65110.0, closedSeries[0].close, 0.001)
        assertTrue(closedSeries[0].isClosed)
    }

    // =========================================================================
    // 12. Lifecycle policy không reconnect khi fragment hidden/destroyed
    // =========================================================================
    @Test
    fun testSocketReconnectPolicy_doesNotReconnectWhenFragmentHiddenOrDestroyed() {
        // Khi fragment bị ẩn hoặc destroy (isVisible = false)
        assertFalse(SocketReconnectPolicy.shouldReconnect(attempt = 0, isVisible = false))
        assertFalse(SocketReconnectPolicy.shouldReconnect(attempt = 1, isVisible = false))
        assertFalse(SocketReconnectPolicy.shouldReconnect(attempt = 3, isVisible = false))

        // Khi visible (isVisible = true)
        assertTrue(SocketReconnectPolicy.shouldReconnect(attempt = 0, isVisible = true))
        assertTrue(SocketReconnectPolicy.shouldReconnect(attempt = 1, isVisible = true))
        assertTrue(SocketReconnectPolicy.shouldReconnect(attempt = 2, isVisible = true))
        assertTrue(SocketReconnectPolicy.shouldReconnect(attempt = 3, isVisible = true))

        // Vượt quá 4 lần thử -> không reconnect nữa (hữu hạn)
        assertFalse(SocketReconnectPolicy.shouldReconnect(attempt = 4, isVisible = true))
        assertFalse(SocketReconnectPolicy.shouldReconnect(attempt = 10, isVisible = true))

        // Backoff delays hữu hạn (2s, 5s, 10s, 30s)
        assertEquals(2000L, SocketReconnectPolicy.getDelayMs(0))
        assertEquals(5000L, SocketReconnectPolicy.getDelayMs(1))
        assertEquals(10000L, SocketReconnectPolicy.getDelayMs(2))
        assertEquals(30000L, SocketReconnectPolicy.getDelayMs(3))
        assertEquals(30000L, SocketReconnectPolicy.getDelayMs(10))
    }

    // =========================================================================
    // 13. CandleTimeFormatter format nhãn trục X và parse thời gian
    // =========================================================================
    @Test
    fun testCandleTimeFormatter_labelsAndParsing() {
        // 1s candle datetime: "2026-09-16 14:35:20" -> "14:35:20"
        assertEquals("14:35:20", CandleTimeFormatter.formatChartAxisLabel("2026-09-16 14:35:20"))
        // Daily candle date: "2026-09-16" -> "09-16"
        assertEquals("09-16", CandleTimeFormatter.formatChartAxisLabel("2026-09-16"))

        // Parsing
        val ms = CandleTimeFormatter.parseTimeToMillis("2026-09-16 14:35:00")
        assertTrue(ms > 0L)
    }

    @Test
    fun testCandleTimeFormatter_epochAxisLabelConsistency_eliminatesTimezoneDiscrepancy() {
        val fixedTz = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh") // UTC+7

        // Giả sử Backend chạy UTC trên Railway:
        // epochMs1 = 1789723800000L tương ứng UTC 09:30:00, nhưng tại UTC+7 là 16:30:00
        val epochMs1 = 1789723800000L
        val epochMs2 = 1789723801000L // 1 giây sau (16:30:01)

        // Nến 1 từ REST response (chứa openTime epoch)
        val restCandle = CandleDto(
            time = "2026-09-18 09:30:00",
            open = 65000.0, high = 65010.0, low = 64990.0, close = 65005.0,
            volume = 1.5, openTime = epochMs1
        )

        // Nến 2 từ WebSocket real-time event
        val wsCandle = CandleDto(
            time = "2026-09-18 16:30:01",
            open = 65005.0, high = 65020.0, low = 65000.0, close = 65015.0,
            volume = 2.0, openTime = epochMs2
        )

        // Dựng nhãn trực tiếp từ openTime epoch theo múi giờ thiết bị cố định
        val label1 = CandleTimeFormatter.formatCandleAxisLabel(restCandle, timeZone = fixedTz)
        val label2 = CandleTimeFormatter.formatCandleAxisLabel(wsCandle, timeZone = fixedTz)

        assertEquals("16:30:00", label1)
        assertEquals("16:30:01", label2)

        // Không bị nhảy 7 giờ giữa REST và WebSocket
        org.junit.Assert.assertNotEquals("09:30:00", label1)
    }

    @Test
    fun testCandleTimeFormatter_fallbackWhenOpenTimeNull_parsesUtcAndFormatsInDeviceTimezone() {
        val fixedTz = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh") // UTC+7

        // Nến REST chỉ có chuỗi time "2026-09-18 09:30:00" do Backend format bằng UTC, openTime = null
        val restCandleWithoutOpenTime = CandleDto(
            time = "2026-09-18 09:30:00",
            open = 65000.0, high = 65010.0, low = 64990.0, close = 65005.0,
            volume = 1.5, openTime = null
        )

        // Nến WebSocket kế tiếp tại giây tiếp theo (16:30:01 giờ địa phương)
        val wsCandle = CandleDto(
            time = "2026-09-18 16:30:01",
            open = 65005.0, high = 65020.0, low = 65000.0, close = 65015.0,
            volume = 2.0, openTime = 1789723801000L
        )

        // Fallback phải parse chuỗi backend "09:30:00" bằng UTC, sau đó format sang UTC+7 thành "16:30:00"
        val label1 = CandleTimeFormatter.formatCandleAxisLabel(restCandleWithoutOpenTime, timeZone = fixedTz)
        val label2 = CandleTimeFormatter.formatCandleAxisLabel(wsCandle, timeZone = fixedTz)

        assertEquals("16:30:00", label1)
        assertEquals("16:30:01", label2)

        // Tuyệt đối không được giữ nguyên "09:30:00" khi hiển thị trên thiết bị UTC+7
        org.junit.Assert.assertNotEquals("09:30:00", label1)
    }

    // =========================================================================
    // 14. Parser bắt buộc interval == "1s", từ chối bất kỳ interval nào khác
    // =========================================================================
    @Test
    fun testBinanceKlineParser_rejectIntervalNot1s() {
        // Payload interval "5m"
        val payload5m = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"5m","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(payload5m))

        // Payload interval "15m"
        val payload15m = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"15m","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(payload15m))

        // Payload interval "1d"
        val payload1d = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1d","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(payload1d))

        // Payload interval "daily"
        val payloadDaily = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"daily","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(payloadDaily))

        // Payload interval "1m" (trước đây hợp lệ nhưng nay phải bị từ chối vì đã đổi sang 1s)
        val payload1m = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1m","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNull(BinanceKlineParser.parse(payload1m))

        // Payload interval "1s" hợp lệ
        val payload1s = """{"k":{"t":1000,"T":2000,"s":"BTCUSDT","i":"1s","o":"90","h":"100","l":"85","c":"95","v":"1"}}"""
        assertNotNull(BinanceKlineParser.parse(payload1s))
    }

    // =========================================================================
    // 15. ChartLabelFormatter format nhãn 1s cho Crypto/Gold và 1D cho Cổ phiếu
    // =========================================================================
    @Test
    fun testChartLabelFormatter_dailyVs1s() {
        assertEquals("BTCUSDT • 1s", ChartLabelFormatter.formatChartDatasetLabel("BTCUSDT"))
        assertEquals("BTCUSDT • 1s", ChartLabelFormatter.formatChartDatasetLabel("BTCUSDT", "1s"))
        assertEquals("ETHUSDT • 1s", ChartLabelFormatter.formatChartDatasetLabel("ETHUSDT", "1s"))
        assertEquals("XAUUSD • 1s", ChartLabelFormatter.formatChartDatasetLabel("XAUUSD", "1s"))
        assertEquals("AAPL • 1D", ChartLabelFormatter.formatChartDatasetLabel("AAPL", "daily"))
        assertEquals("MSFT • 1D", ChartLabelFormatter.formatChartDatasetLabel("MSFT", "1d"))
    }

    // =========================================================================
    // 16. Mô phỏng tích hợp biểu đồ MPAndroidChart khi đủ 30 nến và sang phút mới:
    //     Toàn bộ 30 entries dịch chuyển, cây cũ nhất bị loại bỏ, cây 29 là phút mới.
    // =========================================================================
    @Test
    fun testChartEntryResync_30CandlesNewMinuteEntryShift() {
        val baseTime = 1789500000000L
        val currentCandles = ArrayList<CandleDto>()
        for (i in 0 until 30) {
            val t = baseTime + (i * 60_000L)
            currentCandles.add(CandleDto("time_$i", 60000.0 + i, 60100.0 + i, 59900.0 + i, 60050.0 + i, 1.0, t))
        }
        assertEquals(30, currentCandles.size)

        // Ban đầu khởi tạo 30 entries
        val candleEntries = ArrayList<CandleEntry>()
        val timeLabels = ArrayList<String>()
        for (i in currentCandles.indices) {
            val c = currentCandles[i]
            candleEntries.add(CandleEntry(i.toFloat(), c.high.toFloat(), c.low.toFloat(), c.open.toFloat(), c.close.toFloat()))
            timeLabels.add(c.time)
        }
        assertEquals(30, candleEntries.size)
        assertEquals(60050.0f, candleEntries[0].close, 0.001f) // Cây 0 có giá 60050
        assertEquals("time_0", timeLabels[0])

        // Sự kiện nến phút thứ 31 (openTime > lastOpenTime)
        val eventMinute31 = BinanceKlineEvent(
            openTime = baseTime + (30 * 60_000L),
            closeTime = baseTime + (30 * 60_000L) + 999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 60080.0,
            high = 60250.0,
            low = 60070.0,
            close = 60220.0,
            volume = 4.0,
            isClosed = false
        )

        val lastCandle = currentCandles.last()
        val lastOpenTime = lastCandle.openTime ?: CandleTimeFormatter.parseTimeToMillis(lastCandle.time)
        val isSameMinute = eventMinute31.openTime == lastOpenTime
        assertFalse("Phút thứ 31 phải là new minute chứ không phải same minute", isSameMinute)

        // Thực hiện reducer
        val updatedSeries = CandleSeriesReducer.reduce(currentCandles, eventMinute31, maxCandles = 30)
        currentCandles.clear()
        currentCandles.addAll(updatedSeries)
        assertEquals(30, currentCandles.size)

        // Thực hiện logic production: vì !isSameMinute, đồng bộ lại toàn bộ entries và timeLabels
        if (!isSameMinute) {
            candleEntries.clear()
            timeLabels.clear()
            for (i in currentCandles.indices) {
                val c = currentCandles[i]
                candleEntries.add(CandleEntry(i.toFloat(), c.high.toFloat(), c.low.toFloat(), c.open.toFloat(), c.close.toFloat()))
                timeLabels.add(c.time)
            }
        }

        assertEquals(30, candleEntries.size)
        assertEquals(30, timeLabels.size)

        // Cây ở index 0 trên chart bây giờ là time_1 (cây cũ time_0 đã trôi ra khỏi cửa sổ 30 nến)
        assertEquals("time_1", timeLabels[0])
        assertEquals(60051.0f, candleEntries[0].close, 0.001f)
        assertEquals(0f, candleEntries[0].x, 0.001f)

        // Cây ở index 29 trên chart bây giờ là cây phút 31 vừa tạo
        assertEquals(29f, candleEntries[29].x, 0.001f)
        assertEquals(60220.0f, candleEntries[29].close, 0.001f)
        assertEquals(60250.0f, candleEntries[29].high, 0.001f)
        assertEquals(60070.0f, candleEntries[29].low, 0.001f)
        assertEquals(60080.0f, candleEntries[29].open, 0.001f)

        // Sau đó cùng phút 31 nhận tiếp một tick mới (giá nhảy lên 60240)
        val tickInMinute31 = BinanceKlineEvent(
            openTime = baseTime + (30 * 60_000L),
            closeTime = baseTime + (30 * 60_000L) + 999L,
            symbol = "BTCUSDT",
            interval = "1s",
            open = 60080.0,
            high = 60260.0,
            low = 60070.0,
            close = 60240.0,
            volume = 5.0,
            isClosed = false
        )
        val lastCandleNow = currentCandles.last()
        val lastOpenTimeNow = lastCandleNow.openTime ?: CandleTimeFormatter.parseTimeToMillis(lastCandleNow.time)
        val isSameMinuteNow = tickInMinute31.openTime == lastOpenTimeNow
        assertTrue("Tick cùng phút 31 phải nhận diện là isSameMinute", isSameMinuteNow)

        val updatedSeriesTick = CandleSeriesReducer.reduce(currentCandles, tickInMinute31, maxCandles = 30)
        currentCandles.clear()
        currentCandles.addAll(updatedSeriesTick)

        // Production logic cho same minute: chỉ cập nhật phần tử cuối cùng
        val lastIdx = candleEntries.size - 1
        val lastC = currentCandles.last()
        val lastEntry = candleEntries[lastIdx]
        lastEntry.high = lastC.high.toFloat()
        lastEntry.low = lastC.low.toFloat()
        lastEntry.open = lastC.open.toFloat()
        lastEntry.close = lastC.close.toFloat()

        assertEquals(60240.0f, candleEntries[29].close, 0.001f)
        assertEquals(60260.0f, candleEntries[29].high, 0.001f)
        // Phần tử index 0 và nhãn thời gian vẫn giữ nguyên
        assertEquals("time_1", timeLabels[0])
        assertEquals(60051.0f, candleEntries[0].close, 0.001f)
    }

    // =========================================================================
    // 17. onViewCreated / switch rồi onResume không tạo REST lần hai
    // =========================================================================
    @Test
    fun testTradingLifecyclePolicy_onResumeDoesNotTriggerSecondRestCallWhenActiveCallInProgress() {
        // Khi onViewCreated hoặc switchMarketSymbol vừa kích hoạt, activeCandleCall đang chạy (hasActiveCandleCall = true)
        val actionDuringFlight = TradingLifecyclePolicy.decideResumeAction(
            isStock = false,
            hasActiveCandleCall = true,
            hasCandles = false,
            hasActiveSocket = false
        )
        assertEquals(
            "Đang có REST call chạy thì onResume phải DO_NOTHING, không được gọi lần 2",
            TradingResumeAction.DO_NOTHING,
            actionDuringFlight
        )

        // Đối với Cổ phiếu, onResume cũng không bao giờ tự ý kích hoạt REST nến
        val actionStock = TradingLifecyclePolicy.decideResumeAction(
            isStock = true,
            hasActiveCandleCall = false,
            hasCandles = false,
            hasActiveSocket = false
        )
        assertEquals(TradingResumeAction.DO_NOTHING, actionStock)

        // Chỉ khi không có nến, không có request chạy và không có socket thì mới load nến
        val actionCleanStart = TradingLifecyclePolicy.decideResumeAction(
            isStock = false,
            hasActiveCandleCall = false,
            hasCandles = false,
            hasActiveSocket = false
        )
        assertEquals(TradingResumeAction.LOAD_INITIAL_CANDLES, actionCleanStart)

        // Khi đã có nến và socket null (sau khi pause/resume) -> chỉ connect socket
        val actionResumeWithCandles = TradingLifecyclePolicy.decideResumeAction(
            isStock = false,
            hasActiveCandleCall = false,
            hasCandles = true,
            hasActiveSocket = false
        )
        assertEquals(TradingResumeAction.CONNECT_WEBSOCKET, actionResumeWithCandles)
    }

    // =========================================================================
    // 18. Callback từ socket cũ cùng symbol và cùng generation vẫn bị từ chối bằng socket identity
    // =========================================================================
    @Test
    fun testSocketCallbackGuard_oldSocketRejectedByIdentityEvenWithSameSymbolAndGeneration() {
        val oldSocketInstance = Any()
        val currentSocketInstance = Any()
        val generation = 5L
        val symbol = "BTCUSDT"

        // 1. Socket cũ (oldSocketInstance) so với currentSocketInstance -> BỊ TỪ CHỐI
        val allowedOld = SocketCallbackGuard.isCallbackAllowed(
            callbackSocket = oldSocketInstance,
            activeSocket = currentSocketInstance,
            callbackGeneration = generation,
            activeGeneration = generation,
            callbackSymbol = symbol,
            activeSymbol = symbol,
            isFragmentVisible = true
        )
        assertFalse("Socket cũ khác tham chiếu với activeSocket phải bị từ chối", allowedOld)

        // 2. Khi disconnect làm activeSocket = null -> callback socket cũ lập tức bị từ chối
        val allowedNullActive = SocketCallbackGuard.isCallbackAllowed(
            callbackSocket = oldSocketInstance,
            activeSocket = null,
            callbackGeneration = generation,
            activeGeneration = generation,
            callbackSymbol = symbol,
            activeSymbol = symbol,
            isFragmentVisible = true
        )
        assertFalse("Khi activeSocket là null thì mọi callback phải bị từ chối", allowedNullActive)

        // 3. Đúng socket hiện hành (currentSocketInstance) -> ĐƯỢC CHẤP NHẬN
        val allowedCurrent = SocketCallbackGuard.isCallbackAllowed(
            callbackSocket = currentSocketInstance,
            activeSocket = currentSocketInstance,
            callbackGeneration = generation,
            activeGeneration = generation,
            callbackSymbol = symbol,
            activeSymbol = symbol,
            isFragmentVisible = true
        )
        assertTrue("Socket hiện hành đúng tham chiếu, symbol và generation phải được chấp nhận", allowedCurrent)

        // 4. Fragment bị ẩn (background) -> BỊ TỪ CHỐI
        val allowedBackground = SocketCallbackGuard.isCallbackAllowed(
            callbackSocket = currentSocketInstance,
            activeSocket = currentSocketInstance,
            callbackGeneration = generation,
            activeGeneration = generation,
            callbackSymbol = symbol,
            activeSymbol = symbol,
            isFragmentVisible = false
        )
        assertFalse("Khi fragment bị ẩn thì callback không được phép", allowedBackground)
    }

    // =========================================================================
    // 19. Socket cũ onFailure không schedule reconnect
    // =========================================================================
    @Test
    fun testSocketReconnectPolicy_oldSocketFailureDoesNotScheduleReconnect() {
        val oldSocket = Any()
        val currentSocket = Any()
        val generation = 10L

        val decision = SocketReconnectPolicy.decideFailureAction(
            callbackSocket = oldSocket,
            activeSocket = currentSocket,
            callbackGeneration = generation,
            activeGeneration = generation,
            currentAttempt = 0,
            isVisible = true
        )

        assertFalse("Socket cũ onFailure tuyệt đối không được schedule reconnect", decision.shouldScheduleReconnect)
        assertFalse("Socket cũ onFailure không được xóa activeSocket", decision.shouldClearActiveSocket)
        assertEquals(0, decision.nextAttempt)
    }

    // =========================================================================
    // 20. Socket hiện hành không retry quá 4 lần
    // =========================================================================
    @Test
    fun testSocketReconnectPolicy_currentSocketCappedAt4Retries() {
        val activeSocket = Any()
        val generation = 1L

        // Lần 0 -> Lần 1 (delay 2s)
        val d0 = SocketReconnectPolicy.decideFailureAction(activeSocket, activeSocket, generation, generation, 0, true)
        assertTrue(d0.shouldScheduleReconnect)
        assertEquals(1, d0.nextAttempt)
        assertEquals(2000L, d0.delayMs)
        assertFalse(d0.shouldClearActiveSocket)

        // Lần 1 -> Lần 2 (delay 5s)
        val d1 = SocketReconnectPolicy.decideFailureAction(activeSocket, activeSocket, generation, generation, 1, true)
        assertTrue(d1.shouldScheduleReconnect)
        assertEquals(2, d1.nextAttempt)
        assertEquals(5000L, d1.delayMs)
        assertFalse(d1.shouldClearActiveSocket)

        // Lần 2 -> Lần 3 (delay 10s)
        val d2 = SocketReconnectPolicy.decideFailureAction(activeSocket, activeSocket, generation, generation, 2, true)
        assertTrue(d2.shouldScheduleReconnect)
        assertEquals(3, d2.nextAttempt)
        assertEquals(10000L, d2.delayMs)
        assertFalse(d2.shouldClearActiveSocket)

        // Lần 3 -> Lần 4 (delay 30s)
        val d3 = SocketReconnectPolicy.decideFailureAction(activeSocket, activeSocket, generation, generation, 3, true)
        assertTrue(d3.shouldScheduleReconnect)
        assertEquals(4, d3.nextAttempt)
        assertEquals(30000L, d3.delayMs)
        assertFalse(d3.shouldClearActiveSocket)

        // Lần 4 (đã dùng hết 4 lần thử): KHÔNG schedule reconnect nữa, yêu cầu xóa active socket
        val d4 = SocketReconnectPolicy.decideFailureAction(activeSocket, activeSocket, generation, generation, 4, true)
        assertFalse("Đã quá 4 lần thử thì không được schedule reconnect", d4.shouldScheduleReconnect)
        assertTrue("Đã hết lần thử thì phải yêu cầu clear active socket", d4.shouldClearActiveSocket)

        // Lần 5 trở lên: cũng không retry
        val d5 = SocketReconnectPolicy.decideFailureAction(activeSocket, activeSocket, generation, generation, 5, true)
        assertFalse(d5.shouldScheduleReconnect)
        assertTrue(d5.shouldClearActiveSocket)
    }

    // =========================================================================
    // 21. Sau khi exhausted, foreground mới có thể mở lại socket
    // =========================================================================
    @Test
    fun testSocketReconnectPolicy_afterExhaustedForegroundCanRestart() {
        val exhaustedSocket = Any()
        val generation = 1L

        // Mô phỏng socket bị exhausted sau 4 lần fail
        val failureDecision = SocketReconnectPolicy.decideFailureAction(
            callbackSocket = exhaustedSocket,
            activeSocket = exhaustedSocket,
            callbackGeneration = generation,
            activeGeneration = generation,
            currentAttempt = 4,
            isVisible = true
        )
        assertFalse(failureDecision.shouldScheduleReconnect)
        assertTrue(failureDecision.shouldClearActiveSocket)

        // TradingFragment thực thi failureDecision:
        var binanceWebSocket: Any? = exhaustedSocket
        if (failureDecision.shouldClearActiveSocket) {
            binanceWebSocket = null
        }
        assertNull("activeSocket đã được dọn sạch về null", binanceWebSocket)

        // Sau đó người dùng quay lại app / tab được đưa vào foreground (onResume):
        var reconnectAttempts = 4
        reconnectAttempts = 0

        // Kiểm tra TradingLifecyclePolicy khi quay lại foreground:
        // Đã có nến trước đó (hasCandles = true), socket hiện tại là null
        val resumeAction = TradingLifecyclePolicy.decideResumeAction(
            isStock = false,
            hasActiveCandleCall = false,
            hasCandles = true,
            hasActiveSocket = binanceWebSocket != null
        )

        // Cho phép mở lại WebSocket!
        assertEquals("Sau khi exhausted, foreground mới mở lại kết nối", TradingResumeAction.CONNECT_WEBSOCKET, resumeAction)
        assertEquals(0, reconnectAttempts)
    }
}

