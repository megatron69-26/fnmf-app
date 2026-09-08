package com.example.nhumonglenh.ui.trading

import java.math.BigDecimal
import java.util.UUID

/**
 * Quản lý tính Idempotent cho việc đặt lệnh từ Mobile:
 * - Chuẩn hóa số lượng giao dịch (quantity) bằng BigDecimal/plain string chính xác.
 * - Khi retry do lỗi mạng / timeout với cùng tham số (symbol, orderType, quantity), giữ nguyên clientOrderId cũ
 *   để backend replay giao dịch một cách an toàn mà không trừ tiền 2 lần.
 * - Khi người dùng thay đổi bất kỳ tham số nào (dù sai số nhỏ nhất ở quantity) hoặc sau khi lệnh kết thúc thành công,
 *   sinh clientOrderId (UUID) mới hoàn toàn.
 */
class OrderIdempotencyManager {

    var activeClientOrderId: String? = null
        private set
    var lastSubmittedSymbol: String? = null
        private set
    var lastSubmittedOrderType: String? = null
        private set
    var lastSubmittedQuantityString: String? = null
        private set

    /**
     * Chuẩn hóa quantity thành chuỗi plain decimal chính xác từ BigDecimal, loại bỏ trailing zeros.
     */
    fun normalizeQuantity(quantity: BigDecimal): String {
        return quantity.stripTrailingZeros().toPlainString()
    }

    /**
     * Chuẩn hóa quantity từ Double thông qua BigDecimal.valueOf.
     */
    fun normalizeQuantity(quantity: Double): String {
        return BigDecimal.valueOf(quantity).stripTrailingZeros().toPlainString()
    }

    /**
     * Parse chuỗi nhập liệu của người dùng thành plain string chuẩn hóa, bảo toàn độ chính xác cao.
     */
    fun parseAndNormalizeQuantity(rawString: String?): String? {
        if (rawString.isNullOrBlank()) return null
        return try {
            BigDecimal(rawString.trim()).stripTrailingZeros().toPlainString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Xác định clientOrderId từ BigDecimal chuẩn hóa:
     * - Trả về cùng UUID nếu các tham số giống hệt lần submit trước (kể cả trailing zero: 0.050000 == 0.05).
     * - Sinh UUID mới nếu tham số thay đổi hoặc chưa có active key.
     */
    fun resolveClientOrderId(symbol: String, orderType: String, quantity: BigDecimal): String {
        val normQty = normalizeQuantity(quantity)
        return resolveClientOrderIdInternal(symbol, orderType, normQty)
    }

    /**
     * Xác định clientOrderId từ chuỗi nhập thô của người dùng.
     */
    fun resolveClientOrderId(symbol: String, orderType: String, rawQuantityString: String): String {
        val normQty = parseAndNormalizeQuantity(rawQuantityString) ?: rawQuantityString.trim()
        return resolveClientOrderIdInternal(symbol, orderType, normQty)
    }

    /**
     * Xác định clientOrderId cho một lần submit lệnh (Double overload).
     */
    fun resolveClientOrderId(symbol: String, orderType: String, quantity: Double): String {
        return resolveClientOrderId(symbol, orderType, BigDecimal.valueOf(quantity))
    }

    private fun resolveClientOrderIdInternal(symbol: String, orderType: String, normQty: String): String {
        val paramsChanged = (symbol != lastSubmittedSymbol) ||
                (orderType != lastSubmittedOrderType) ||
                (normQty != lastSubmittedQuantityString)

        val id = if (paramsChanged || activeClientOrderId == null) {
            UUID.randomUUID().toString().also {
                activeClientOrderId = it
                lastSubmittedSymbol = symbol
                lastSubmittedOrderType = orderType
                lastSubmittedQuantityString = normQty
            }
        } else {
            activeClientOrderId!!
        }
        return id
    }

    /**
     * Reset toàn bộ trạng thái sau khi lệnh đã hoàn tất thành công (terminal success)
     * hoặc bị từ chối 400 Bad Request dứt điểm.
     */
    fun reset() {
        activeClientOrderId = null
        lastSubmittedSymbol = null
        lastSubmittedOrderType = null
        lastSubmittedQuantityString = null
    }

    fun restoreState(clientOrderId: String?, symbol: String?, orderType: String?, quantityString: String?) {
        this.activeClientOrderId = clientOrderId
        this.lastSubmittedSymbol = symbol
        this.lastSubmittedOrderType = orderType
        this.lastSubmittedQuantityString = quantityString
    }
}
