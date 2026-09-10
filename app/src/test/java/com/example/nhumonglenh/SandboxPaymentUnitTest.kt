package com.example.nhumonglenh

import com.example.nhumonglenh.data.remote.ApiService
import com.example.nhumonglenh.data.remote.CreatePaymentRequest
import com.example.nhumonglenh.data.remote.PaymentOrderDto
import com.example.nhumonglenh.ui.payment.PaymentIdempotencyManager
import com.example.nhumonglenh.ui.payment.PaymentItemFormatter
import com.example.nhumonglenh.ui.payment.PaymentReadinessPolicy
import com.example.nhumonglenh.ui.payment.SandboxPaymentBottomSheet
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.POST
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * UNIT TEST SUITE CHO MODULE NẠP/RÚT TIỀN SANDBOX (FNMF v1.1.14)
 * Kiểm thử tính đúng đắn của ApiService Contract, JSON Serialization,
 * Idempotency Key Preservation và giới hạn Scale tiền tệ USD (cents <= 2).
 */
class SandboxPaymentUnitTest {

    private val gson = Gson()

    // ------------------------------------------------------------------------------------
    // 1. KIỂM THỬ API SERVICE CONTRACT CHO PAYMENT
    // ------------------------------------------------------------------------------------
    @Test
    fun testPaymentApiContract_hasCreateDepositEndpoint() {
        val method = ApiService::class.java.methods.find { it.name == "createDeposit" }
        assertNotNull("ApiService phải khai báo method createDeposit", method)

        val postAnnotation = method!!.getAnnotation(POST::class.java)
        assertNotNull("createDeposit phải có annotation @POST", postAnnotation)
        assertEquals("/api/payments/deposits", postAnnotation!!.value)

        val paramAnnotations = method.parameterAnnotations
        assertTrue(
            "Tham số thứ nhất phải là @Header(\"Authorization\")",
            paramAnnotations[0].any { it is Header && it.value == "Authorization" }
        )
        assertTrue(
            "Tham số thứ hai phải là @Body",
            paramAnnotations[1].any { it is Body }
        )
    }

    @Test
    fun testPaymentApiContract_hasCreateWithdrawalEndpoint() {
        val method = ApiService::class.java.methods.find { it.name == "createWithdrawal" }
        assertNotNull("ApiService phải khai báo method createWithdrawal", method)

        val postAnnotation = method!!.getAnnotation(POST::class.java)
        assertNotNull("createWithdrawal phải có annotation @POST", postAnnotation)
        assertEquals("/api/payments/withdrawals", postAnnotation!!.value)

        val paramAnnotations = method.parameterAnnotations
        assertTrue(
            "Tham số thứ nhất phải là @Header(\"Authorization\")",
            paramAnnotations[0].any { it is Header && it.value == "Authorization" }
        )
        assertTrue(
            "Tham số thứ hai phải là @Body",
            paramAnnotations[1].any { it is Body }
        )
    }

    @Test
    fun testPaymentApiContract_hasGetPaymentHistoryEndpoint() {
        val method = ApiService::class.java.methods.find { it.name == "getPaymentHistory" }
        assertNotNull("ApiService phải khai báo method getPaymentHistory", method)

        val getAnnotation = method!!.getAnnotation(GET::class.java)
        assertNotNull("getPaymentHistory phải có annotation @GET", getAnnotation)
        assertEquals("/api/payments", getAnnotation!!.value)
    }

    @Test
    fun testPaymentApiContract_hasGetPaymentDetailsEndpoint() {
        val method = ApiService::class.java.methods.find { it.name == "getPaymentDetails" }
        assertNotNull("ApiService phải khai báo method getPaymentDetails", method)

        val getAnnotation = method!!.getAnnotation(GET::class.java)
        assertNotNull("getPaymentDetails phải có annotation @GET", getAnnotation)
        assertEquals("/api/payments/{paymentOrderId}", getAnnotation!!.value)

        val paramAnnotations = method.parameterAnnotations
        assertTrue(
            "Tham số thứ hai phải là @Path(\"paymentOrderId\")",
            paramAnnotations[1].any { it is Path && it.value == "paymentOrderId" }
        )
    }

    @Test
    fun testPaymentApiContract_hasCancelPaymentEndpoint() {
        val method = ApiService::class.java.methods.find { it.name == "cancelPayment" }
        assertNotNull("ApiService phải khai báo method cancelPayment", method)

        val postAnnotation = method!!.getAnnotation(POST::class.java)
        assertNotNull("cancelPayment phải có annotation @POST", postAnnotation)
        assertEquals("/api/payments/{paymentOrderId}/cancel", postAnnotation!!.value)

        val paramAnnotations = method.parameterAnnotations
        assertTrue(
            "Tham số thứ hai phải là @Path(\"paymentOrderId\")",
            paramAnnotations[1].any { it is Path && it.value == "paymentOrderId" }
        )
    }

    // ------------------------------------------------------------------------------------
    // 2. JSON DTO SERIALIZATION & DESERIALIZATION
    // ------------------------------------------------------------------------------------
    @Test
    fun testCreatePaymentRequest_serialization() {
        val request = CreatePaymentRequest(
            amountUsd = BigDecimal("250.75"),
            clientRequestId = "req-test-uuid-12345"
        )

        val json = gson.toJson(request)
        assertTrue("JSON phải chứa amountUsd", json.contains("\"amountUsd\":250.75"))
        assertTrue("JSON phải chứa clientRequestId", json.contains("\"clientRequestId\":\"req-test-uuid-12345\""))

        val deserialized = gson.fromJson(json, CreatePaymentRequest::class.java)
        assertEquals(BigDecimal("250.75"), deserialized.amountUsd)
        assertEquals("req-test-uuid-12345", deserialized.clientRequestId)
    }

    @Test
    fun testPaymentOrderDto_deserialization() {
        val sampleJson = """
            {
                "paymentOrderId": 1001,
                "type": "DEPOSIT",
                "amountUsd": 500.0,
                "status": "PENDING",
                "checkoutUrl": "http://10.0.2.2:8083/sandbox-bank/checkout/token_abc123",
                "provider": "SANDBOX_INTERNAL",
                "failureReason": null,
                "message": "Vui lòng mở giao diện Sandbox để xác nhận",
                "createdAt": "2026-09-09T08:15:00",
                "completedAt": null
            }
        """.trimIndent()

        val dto = gson.fromJson(sampleJson, PaymentOrderDto::class.java)
        assertNotNull(dto)
        assertEquals(1001L, dto.paymentOrderId)
        assertEquals("DEPOSIT", dto.type)
        assertEquals(500.0, dto.amountUsd ?: 0.0, 0.0001)
        assertEquals("PENDING", dto.status)
        assertEquals("http://10.0.2.2:8083/sandbox-bank/checkout/token_abc123", dto.checkoutUrl)
        assertEquals("SANDBOX_INTERNAL", dto.provider)
        assertEquals("Vui lòng mở giao diện Sandbox để xác nhận", dto.message)
        assertNull(dto.failureReason)
        assertNull(dto.completedAt)
        assertEquals("2026-09-09T08:15:00", dto.createdAt)
    }

    // ------------------------------------------------------------------------------------
    // 3. PAYMENT IDEMPOTENCY MANAGER & SCALE VALIDATION
    // ------------------------------------------------------------------------------------
    @Test
    fun testIdempotencyManager_scaleValidation() {
        val manager = PaymentIdempotencyManager()

        assertTrue("Scale 0 (số nguyên) phải hợp lệ", manager.isValidScale(BigDecimal("100")))
        assertTrue("Scale 1 (hàng chục cents) phải hợp lệ", manager.isValidScale(BigDecimal("100.5")))
        assertTrue("Scale 2 (cents chuẩn USD) phải hợp lệ", manager.isValidScale(BigDecimal("100.50")))
        assertTrue("Scale 2 với 0 thừa phải hợp lệ", manager.isValidScale(BigDecimal("100.5000")))

        assertFalse("Scale 3 (phần nghìn USD) phải bị từ chối", manager.isValidScale(BigDecimal("100.123")))
        assertFalse("Scale 6 phải bị từ chối trong nạp/rút USD", manager.isValidScale(BigDecimal("100.123456")))
        assertFalse("Scale 8 (satoshi) phải bị từ chối", manager.isValidScale(BigDecimal("0.00000001")))
    }

    @Test
    fun testIdempotencyManager_preservesKeyOnNetworkRetry() {
        val manager = PaymentIdempotencyManager()
        val amount = BigDecimal("500.00")

        // Lần bấm đầu tiên
        val key1 = manager.resolveClientOrderId("DEPOSIT", amount)
        assertNotNull(key1)
        assertTrue(key1.isNotBlank())

        // Lần bấm thứ 2 (mạng chập chờn, retry với cùng loại và số tiền)
        val key2 = manager.resolveClientOrderId("DEPOSIT", amount)
        assertEquals("Khi retry với cùng số tiền và loại lệnh, key idempotency phải giữ nguyên tuyệt đối", key1, key2)

        // Khi đổi số tiền -> Phải sinh UUID mới
        val key3 = manager.resolveClientOrderId("DEPOSIT", BigDecimal("600.00"))
        assertNotEquals("Đổi số tiền phải sinh key mới", key1, key3)

        // Khi đổi loại lệnh -> Phải sinh UUID mới
        val key4 = manager.resolveClientOrderId("WITHDRAWAL", BigDecimal("600.00"))
        assertNotEquals("Đổi loại lệnh phải sinh key mới", key3, key4)

        // Khi reset thành công -> Phải sinh UUID mới
        manager.reset()
        val key5 = manager.resolveClientOrderId("WITHDRAWAL", BigDecimal("600.00"))
        assertNotEquals("Sau khi reset, phải sinh key mới", key4, key5)
    }

    // ------------------------------------------------------------------------------------
    // 4. KIỂM THỬ TRÌNH BÀY DANH SÁCH GIAO DỊCH & CHỐNG GIẢ MẠO DỮ LIỆU (ANTI-FAKE DEFAULTS)
    // ------------------------------------------------------------------------------------
    @Test
    fun testPaymentItemFormatter_nullSafetyAndNoFakeDefaults() {
        // Khi các trường null từ server: Tuyệt đối không sinh số 0.00, #0 hoặc mặc định PENDING giả
        assertEquals("Loại null phải trả về '—'", "—", PaymentItemFormatter.formatType(null))
        assertEquals("Loại không hợp lệ phải trả về '—'", "—", PaymentItemFormatter.formatType("UNKNOWN_TYPE"))
        assertEquals("DEPOSIT phải format thành NẠP", "NẠP", PaymentItemFormatter.formatType("deposit"))
        assertEquals("WITHDRAWAL phải format thành RÚT", "RÚT", PaymentItemFormatter.formatType("withdrawal"))

        assertEquals("Số tiền null phải trả về '—'", "—", PaymentItemFormatter.formatAmount(null, "DEPOSIT"))
        assertEquals("Số tiền null với type null phải trả về '—'", "—", PaymentItemFormatter.formatAmount(null, null))
        assertNotEquals("Không được trả về $0.00 khi amountUsd null", "$0.00", PaymentItemFormatter.formatAmount(null, "DEPOSIT"))

        assertEquals("Format số tiền nạp chuẩn", "+$250.50", PaymentItemFormatter.formatAmount(250.5, "DEPOSIT"))
        assertEquals("Format số tiền rút chuẩn", "-$125.75", PaymentItemFormatter.formatAmount(125.75, "WITHDRAWAL"))
        assertEquals("Format số tiền có dấu phẩy hàng nghìn", "+$1,250.00", PaymentItemFormatter.formatAmount(1250.0, "DEPOSIT"))

        assertEquals("Mã đơn null phải hiển thị '#—'", "#—", PaymentItemFormatter.formatOrderId(null))
        assertEquals("Mã đơn 0 phải hiển thị '#—'", "#—", PaymentItemFormatter.formatOrderId(0L))
        assertEquals("Mã đơn âm phải hiển thị '#—'", "#—", PaymentItemFormatter.formatOrderId(-10L))
        assertNotEquals("Không được trả về '#0' khi orderId null", "#0", PaymentItemFormatter.formatOrderId(null))
        assertEquals("Mã đơn hợp lệ phải hiển thị đúng ID", "#1001", PaymentItemFormatter.formatOrderId(1001L))

        assertEquals("Thời gian null phải trả về '—'", "—", PaymentItemFormatter.formatCreatedAt(null))
        assertEquals("Format ISO timestamp chuẩn", "2026-09-09 08:30:15", PaymentItemFormatter.formatCreatedAt("2026-09-09T08:30:15.123456"))

        assertEquals("Trạng thái null phải là UNKNOWN", "UNKNOWN", PaymentItemFormatter.formatStatus(null))
        assertNotEquals("Không được tự ý gán PENDING khi status null", "PENDING", PaymentItemFormatter.formatStatus(null))
        assertEquals("Chuẩn hóa trạng thái PENDING", "PENDING", PaymentItemFormatter.formatStatus("pending"))
        assertEquals("Chuẩn hóa trạng thái SUCCEEDED", "SUCCEEDED", PaymentItemFormatter.formatStatus("succeeded"))
    }

    @Test
    fun testPaymentItemFormatter_pendingActionGuards() {
        // Chỉ hiển thị và mở nút hành động khi đơn PENDING / PROCESSING VÀ có orderId > 0
        assertTrue(
            "Đơn PENDING với orderId hợp lệ phải mở nút hành động",
            PaymentItemFormatter.shouldShowPendingActions("PENDING", 1001L)
        )
        assertTrue(
            "Đơn PROCESSING với orderId hợp lệ phải mở nút hành động",
            PaymentItemFormatter.shouldShowPendingActions("PROCESSING", 1002L)
        )

        assertFalse(
            "Đơn PENDING nhưng orderId null phải ẩn nút hành động",
            PaymentItemFormatter.shouldShowPendingActions("PENDING", null)
        )
        assertFalse(
            "Đơn PENDING nhưng orderId <= 0 phải ẩn nút hành động",
            PaymentItemFormatter.shouldShowPendingActions("PENDING", 0L)
        )
        assertFalse(
            "Đơn SUCCEEDED không được hiển thị nút pending",
            PaymentItemFormatter.shouldShowPendingActions("SUCCEEDED", 1001L)
        )
        assertFalse(
            "Đơn FAILED không được hiển thị nút pending",
            PaymentItemFormatter.shouldShowPendingActions("FAILED", 1001L)
        )
        assertFalse(
            "Đơn CANCELLED không được hiển thị nút pending",
            PaymentItemFormatter.shouldShowPendingActions("CANCELLED", 1001L)
        )
        assertFalse(
            "Đơn status null không được hiển thị nút pending",
            PaymentItemFormatter.shouldShowPendingActions(null, 1001L)
        )
    }

    // ------------------------------------------------------------------------------------
    // 5. KIỂM THỬ KHÔI PHỤC TRẠNG THÁI IDEMPOTENCY (PROCESS DEATH & CONFIGURATION CHANGE)
    // ------------------------------------------------------------------------------------
    @Test
    fun testIdempotencyManager_stateSnapshotAndRestoration() {
        val manager = PaymentIdempotencyManager()
        val amount = BigDecimal("350.50")
        val reqId = manager.resolveClientOrderId("DEPOSIT", amount)

        // Tạo snapshot trước khi cấu hình thay đổi / tiến trình bị hủy
        val snapshot = manager.toSnapshot()
        assertEquals(reqId, snapshot.activeClientRequestId)
        assertEquals("DEPOSIT", snapshot.lastType)
        assertTrue("Amount snapshot phải bằng amount ban đầu", amount.compareTo(snapshot.lastAmount) == 0)

        // Giả lập hủy đối tượng manager
        manager.reset()
        assertNull(manager.activeClientRequestId)
        assertNull(manager.lastType)
        assertNull(manager.lastAmount)

        // Khôi phục từ snapshot
        manager.restoreFromSnapshot(snapshot)
        assertEquals(reqId, manager.activeClientRequestId)
        assertEquals("DEPOSIT", manager.lastType)
        assertTrue("Amount sau khôi phục phải bằng amount ban đầu", amount.compareTo(manager.lastAmount) == 0)

        // Khi người dùng thử lại sau khi xoay màn hình với cùng tham số -> Giữ nguyên key idempotency
        val replayedKey = manager.resolveClientOrderId("DEPOSIT", BigDecimal("350.50"))
        assertEquals("Sau khi khôi phục, retry phải giữ nguyên idempotency key", reqId, replayedKey)
    }

    @Test
    fun testIdempotencyManager_restoreFromSerializedStrings() {
        val manager = PaymentIdempotencyManager()

        // Khôi phục từ các chuỗi serialized (tương đương Bundle / SharedPreferences)
        val originalKey = "saved-uuid-999-888"
        manager.restoreState(
            requestId = originalKey,
            type = "WITHDRAWAL",
            amountStr = "150.00"
        )

        assertEquals(originalKey, manager.activeClientRequestId)
        assertEquals("WITHDRAWAL", manager.lastType)
        assertTrue("Amount khôi phục từ string phải bằng 150.00", BigDecimal("150.00").compareTo(manager.lastAmount) == 0)

        // Cùng tham số -> Giữ nguyên key
        val key = manager.resolveClientOrderId("WITHDRAWAL", BigDecimal("150.00"))
        assertEquals(originalKey, key)

        // Đổi số tiền -> Sinh key mới
        val newKey = manager.resolveClientOrderId("WITHDRAWAL", BigDecimal("200.00"))
        assertNotEquals(originalKey, newKey)
    }

    // ------------------------------------------------------------------------------------
    // 6. KIỂM THỬ KHÓA NÚT KHI CHƯA TẢI SỐ DƯ, CÔ LẬP TÀI KHOẢN VÀ FRAGMENT RESULT API
    // ------------------------------------------------------------------------------------
    @Test
    fun testPaymentReadinessPolicy_canOperateGuards() {
        // 1. Chưa tải ví và số dư null -> Không được phép thao tác
        assertFalse(
            "Không được phép thao tác khi ví chưa tải và số dư null",
            PaymentReadinessPolicy.canOperate(isWalletLoaded = false, currentBalance = null)
        )

        // 2. Chưa tải ví nhưng số dư gán tạm 0.0 -> Tuyệt đối không cho phép thao tác (chống lỗi renderEmptyData cũ)
        assertFalse(
            "Không được phép thao tác khi isWalletLoaded = false dù balance = 0.0",
            PaymentReadinessPolicy.canOperate(isWalletLoaded = false, currentBalance = 0.0)
        )

        // 3. Đánh dấu đã tải nhưng số dư vẫn null -> Không được phép
        assertFalse(
            "Không được phép thao tác khi balance null dù isWalletLoaded = true",
            PaymentReadinessPolicy.canOperate(isWalletLoaded = true, currentBalance = null)
        )

        // 4. Đã tải số dư âm bất thường -> Không được phép
        assertFalse(
            "Không được phép thao tác khi balance âm",
            PaymentReadinessPolicy.canOperate(isWalletLoaded = true, currentBalance = -50.0)
        )

        // 5. Đã tải số dư hợp lệ >= 0 -> Cho phép thao tác
        assertTrue(
            "Phải cho phép thao tác khi ví đã tải và số dư 0.0",
            PaymentReadinessPolicy.canOperate(isWalletLoaded = true, currentBalance = 0.0)
        )
        assertTrue(
            "Phải cho phép thao tác khi ví đã tải và số dư 1500.0",
            PaymentReadinessPolicy.canOperate(isWalletLoaded = true, currentBalance = 1500.0)
        )

        // Kiểm tra thông điệp cảnh báo
        assertTrue(PaymentReadinessPolicy.getReadinessWarning().contains("Đang tải số dư ví"))
    }

    @Test
    fun testPaymentReadinessPolicy_validateAmount_productionRules() {
        // 1. Ví chưa sẵn sàng -> Trả về WalletNotReady
        val notReadyResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "100.00",
            isDeposit = true,
            isWalletLoaded = false,
            currentBalance = null
        )
        assertTrue(notReadyResult is PaymentReadinessPolicy.ValidationResult.WalletNotReady)

        // 2. Số tiền rỗng / không hợp lệ
        val emptyResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "",
            isDeposit = true,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(emptyResult is PaymentReadinessPolicy.ValidationResult.AmountMissingOrInvalid)

        val invalidFormatResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "abc",
            isDeposit = true,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(invalidFormatResult is PaymentReadinessPolicy.ValidationResult.AmountMissingOrInvalid)

        // 3. Số tiền <= 0
        val zeroResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "0.00",
            isDeposit = true,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(zeroResult is PaymentReadinessPolicy.ValidationResult.AmountNotPositive)

        // 4. Scale > 2 chữ số thập phân
        val scaleResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "50.123",
            isDeposit = true,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(scaleResult is PaymentReadinessPolicy.ValidationResult.ScaleExceeded)
        assertEquals(2, (scaleResult as PaymentReadinessPolicy.ValidationResult.ScaleExceeded).maxScale)

        // 5. Vượt hạn mức tối đa Sandbox $100,000.00
        val maxResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "100000.01",
            isDeposit = true,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(maxResult is PaymentReadinessPolicy.ValidationResult.ExceedsMaxLimit)

        // 6. Rút tiền vượt quá số dư (rút 1500 khi chỉ có 1000)
        val insufficientResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "1500.00",
            isDeposit = false,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(insufficientResult is PaymentReadinessPolicy.ValidationResult.InsufficientFunds)

        // 7. Rút tiền hợp lệ trong số dư (rút 400 khi có 1000 -> còn 600)
        val validWithdrawResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "400.00",
            isDeposit = false,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(validWithdrawResult is PaymentReadinessPolicy.ValidationResult.Valid)
        val withdrawValid = validWithdrawResult as PaymentReadinessPolicy.ValidationResult.Valid
        assertEquals(0, BigDecimal("400.00").compareTo(withdrawValid.amount))
        assertEquals(0, BigDecimal("600.00").compareTo(withdrawValid.projectedBalance))

        // 8. Nạp tiền hợp lệ (nạp 500 khi có 1000 -> thành 1500)
        val validDepositResult = PaymentReadinessPolicy.validateAmount(
            rawAmountStr = "500.00",
            isDeposit = true,
            isWalletLoaded = true,
            currentBalance = 1000.0
        )
        assertTrue(validDepositResult is PaymentReadinessPolicy.ValidationResult.Valid)
        val depositValid = validDepositResult as PaymentReadinessPolicy.ValidationResult.Valid
        assertEquals(0, BigDecimal("500.00").compareTo(depositValid.amount))
        assertEquals(0, BigDecimal("1500.00").compareTo(depositValid.projectedBalance))
    }

    @Test
    fun testAccountSwitching_isolatedUserPreferences() {
        val manager = PaymentIdempotencyManager()

        val keyA = manager.getPrefKey("payment_idemp_active_req_id", "khoi@fnmf.com")
        val keyB = manager.getPrefKey("payment_idemp_active_req_id", "manh@fnmf.com")
        val keyDefault = manager.getPrefKey("payment_idemp_active_req_id", null)

        assertEquals("payment_idemp_active_req_id_khoi@fnmf.com", keyA)
        assertEquals("payment_idemp_active_req_id_manh@fnmf.com", keyB)
        assertEquals("payment_idemp_active_req_id", keyDefault)
        assertNotEquals("Key của hai tài khoản khác nhau phải hoàn toàn độc lập", keyA, keyB)
    }

    @Test
    fun testFragmentResultApi_bundleContractKeys() {
        assertEquals("request_key_sandbox_payment", SandboxPaymentBottomSheet.REQUEST_KEY_PAYMENT)
        assertEquals("key_payment_order_id", SandboxPaymentBottomSheet.KEY_PAYMENT_ORDER_ID)
        assertEquals("key_checkout_url", SandboxPaymentBottomSheet.KEY_CHECKOUT_URL)
        assertEquals("key_payment_type", SandboxPaymentBottomSheet.KEY_PAYMENT_TYPE)
        assertEquals("key_amount_usd", SandboxPaymentBottomSheet.KEY_AMOUNT_USD)
        assertEquals("key_amount_vnd", SandboxPaymentBottomSheet.KEY_AMOUNT_VND)
        assertEquals("key_exchange_rate", SandboxPaymentBottomSheet.KEY_EXCHANGE_RATE)
        assertEquals("key_status", SandboxPaymentBottomSheet.KEY_STATUS)
        assertEquals("key_message", SandboxPaymentBottomSheet.KEY_MESSAGE)
    }

    @Test
    fun testScreenRotation_statePreservation() {
        val manager = PaymentIdempotencyManager()
        val originalReqId = manager.resolveClientOrderId("DEPOSIT", BigDecimal("500.00"))

        // Mô phỏng Bundle lưu trạng thái trước khi xoay màn hình
        val savedReqId = manager.activeClientRequestId
        val savedType = manager.lastType
        val savedAmountStr = manager.lastAmount?.toPlainString()

        // Tiến trình/Activity bị recreate -> Manager mới
        val newManager = PaymentIdempotencyManager()
        assertNull(newManager.activeClientRequestId)

        // Khôi phục từ Bundle đã lưu
        newManager.restoreState(savedReqId, savedType, savedAmountStr)
        assertEquals(originalReqId, newManager.activeClientRequestId)
        assertEquals("DEPOSIT", newManager.lastType)

        // Người dùng ấn xác nhận lại với cùng số tiền -> Trả lại chính xác key ban đầu
        val replayed = newManager.resolveClientOrderId("DEPOSIT", BigDecimal("500.00"))
        assertEquals("Key idempotency phải được bảo toàn qua vòng đời xoay màn hình", originalReqId, replayed)
    }
}
