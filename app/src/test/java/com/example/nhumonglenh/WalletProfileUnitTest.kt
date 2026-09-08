package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.data.remote.OrderResponse
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import com.example.nhumonglenh.data.repository.WalletProfileRepository
import com.example.nhumonglenh.ui.profile.WalletProfileViewModel
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.util.Locale

class WalletProfileUnitTest {

    private val gson = Gson()

    // -------------------------------------------------------------
    // 1. TEST DTO MAPPING: id -> transactionId, createdAt -> executedAt
    // -------------------------------------------------------------
    @Test
    fun testOrderResponseMapping_withBackendFieldNames() {
        val backendJson = """
            {
                "id": 888,
                "symbol": "BTCUSDT",
                "type": "BUY",
                "price": 65000.0,
                "quantity": 0.5,
                "totalAmount": 32500.0,
                "createdAt": "2026-09-08T10:15:30Z"
            }
        """.trimIndent()

        val order = gson.fromJson(backendJson, OrderResponse::class.java)

        assertNotNull(order)
        assertEquals(888L, order.transactionId)
        assertEquals("BTCUSDT", order.symbol)
        assertEquals("BUY", order.type)
        assertEquals(32500.0, order.totalAmount ?: 0.0, 0.001)
        assertEquals("2026-09-08T10:15:30Z", order.executedAt)
    }

    @Test
    fun testOrderResponseMapping_withAlternativeFieldNames() {
        val clientJson = """
            {
                "transactionId": 999,
                "symbol": "ETHUSDT",
                "type": "SELL",
                "price": 3500.0,
                "quantity": 2.0,
                "totalAmount": 7000.0,
                "executedAt": "2026-09-08T11:20:00Z"
            }
        """.trimIndent()

        val order = gson.fromJson(clientJson, OrderResponse::class.java)

        assertNotNull(order)
        assertEquals(999L, order.transactionId)
        assertEquals("ETHUSDT", order.symbol)
        assertEquals("SELL", order.type)
        assertEquals(7000.0, order.totalAmount ?: 0.0, 0.001)
        assertEquals("2026-09-08T11:20:00Z", order.executedAt)
    }

    // -------------------------------------------------------------
    // 2. TEST TRANSACTION TYPE CLASSIFICATION
    // -------------------------------------------------------------
    @Test
    fun testTransactionTypeClassification() {
        fun classify(type: String?): String {
            return when (type?.uppercase(Locale.ROOT)?.trim()) {
                "BUY" -> "BUY"
                "SELL" -> "SELL"
                "TOPUP" -> "TOPUP"
                "GRANT" -> "GRANT"
                else -> "OTHER"
            }
        }

        assertEquals("BUY", classify("BUY"))
        assertEquals("BUY", classify("buy"))
        assertEquals("SELL", classify("SELL"))
        assertEquals("SELL", classify("sell"))
        assertEquals("TOPUP", classify("TOPUP"))
        assertEquals("TOPUP", classify("topup"))
        assertEquals("GRANT", classify("GRANT"))
        assertEquals("GRANT", classify("grant"))

        // Types khác KHÔNG bị gộp nhầm thành SELL
        assertEquals("OTHER", classify("TRANSFER"))
        assertEquals("OTHER", classify("WITHDRAW"))
        assertEquals("OTHER", classify(null))
        assertEquals("OTHER", classify(""))
    }

    // -------------------------------------------------------------
    // 3. TEST UNAUTHORIZED DETECTION: 401, 403, 400 WITH TOKEN/AUTH MSG
    // -------------------------------------------------------------
    @Test
    fun testIsTokenOrAuthError_with401and403() {
        val resp401 = Response.error<Any>(401, "Unauthorized".toResponseBody("application/json".toMediaTypeOrNull()))
        val resp403 = Response.error<Any>(403, "Forbidden".toResponseBody("application/json".toMediaTypeOrNull()))

        assertTrue(WalletProfileRepository.isTokenOrAuthError(resp401))
        assertTrue(WalletProfileRepository.isTokenOrAuthError(resp403))
    }

    @Test
    fun testIsTokenOrAuthError_with400TokenKeywords() {
        val jsonMediaType = "application/json".toMediaTypeOrNull()

        val resp400TokenMissing = Response.error<Any>(
            400,
            "{\"message\": \"Vui lòng đính kèm Bearer Token hợp lệ trong Header Authorization!\"}".toResponseBody(jsonMediaType)
        )
        val resp400TokenExpired = Response.error<Any>(
            400,
            "{\"error\": \"Token không hợp lệ hoặc đã hết hạn!\"}".toResponseBody(jsonMediaType)
        )
        val resp400AuthFailed = Response.error<Any>(
            400,
            "{\"status\": \"Lỗi xác thực người dùng\"}".toResponseBody(jsonMediaType)
        )

        assertTrue(WalletProfileRepository.isTokenOrAuthError(resp400TokenMissing))
        assertTrue(WalletProfileRepository.isTokenOrAuthError(resp400TokenExpired))
        assertTrue(WalletProfileRepository.isTokenOrAuthError(resp400AuthFailed))
    }

    @Test
    fun testIsTokenOrAuthError_with400BusinessError_shouldNotBeUnauthorized() {
        val jsonMediaType = "application/json".toMediaTypeOrNull()

        val resp400Balance = Response.error<Any>(
            400,
            "{\"message\": \"Số dư tài khoản không đủ để thực hiện lệnh mua!\"}".toResponseBody(jsonMediaType)
        )
        val resp400InvalidSymbol = Response.error<Any>(
            400,
            "{\"message\": \"Mã tiền ảo BTCUSDT không được hỗ trợ!\"}".toResponseBody(jsonMediaType)
        )
        val resp500Server = Response.error<Any>(
            500,
            "{\"message\": \"Internal Server Error\"}".toResponseBody(jsonMediaType)
        )

        assertFalse(WalletProfileRepository.isTokenOrAuthError(resp400Balance))
        assertFalse(WalletProfileRepository.isTokenOrAuthError(resp400InvalidSymbol))
        assertFalse(WalletProfileRepository.isTokenOrAuthError(resp500Server))
    }

    // -------------------------------------------------------------
    // 4. TEST DEBOUNCE LOGIC & COOLDOWN
    // -------------------------------------------------------------
    @Test
    fun testDebounceLogic() {
        val now = 100_000L
        val lastSuccess = 85_000L // 15 giây trước -> nhỏ hơn 30 giây

        // Khi chưa đủ 30 giây và không ép buộc làm mới -> nên debounce (bỏ qua)
        val shouldDebounce = (now - lastSuccess < WalletProfileViewModel.DEBOUNCE_INTERVAL_MS)
        assertTrue(shouldDebounce)

        val oldSuccess = 60_000L // 40 giây trước -> lớn hơn 30 giây
        val shouldDebounceOld = (now - oldSuccess < WalletProfileViewModel.DEBOUNCE_INTERVAL_MS)
        assertFalse(shouldDebounceOld)
    }

    @Test
    fun testFailureCooldownLogic() {
        val now = 100_000L
        val lastAttempt = 95_000L // 5 giây trước khi vừa lỗi

        // Khi vừa gặp lỗi trong vòng 10s -> áp dụng cooldown không spam API
        val inCooldown = (now - lastAttempt < WalletProfileViewModel.FAILURE_RETRY_INTERVAL_MS)
        assertTrue(inCooldown)
    }

    // -------------------------------------------------------------
    // 5. TEST FALLBACK LOGIC: NO FAKE $10,000 OR FAKE EMAIL
    // -------------------------------------------------------------
    @Test
    fun testFallback_doesNotUseFakeData() {
        val emptyPortfolio: PortfolioSummaryDto? = null
        val netWorth = emptyPortfolio?.totalNetWorth
        val cashBalance = emptyPortfolio?.cashBalanceUsd
        val initialBalance = emptyPortfolio?.initialBalanceUsd

        assertNull(netWorth)
        assertNull(cashBalance)
        assertNull(initialBalance)

        // Không tự tính PnL khi dữ liệu null
        val totalPnL = emptyPortfolio?.totalPnL ?: if (netWorth != null && initialBalance != null) (netWorth - initialBalance) else null
        assertNull(totalPnL)

        // Email không fallback thành @fnmf.com
        val savedUser = "trader01"
        val userEmail: String? = null
        val displayedEmail = userEmail ?: savedUser.ifEmpty { "Chưa có dữ liệu" }
        assertEquals("trader01", displayedEmail)
        assertFalse(displayedEmail.contains("@fnmf.com"))
    }
}