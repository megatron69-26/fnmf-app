package com.example.nhumonglenh.ui.payment

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Chính sách xác định tính sẵn sàng và tính hợp lệ của các thao tác thanh toán Sandbox (Nạp/Rút).
 * Tách biệt hoàn toàn khỏi Android Framework để đảm bảo 100% unit-testable.
 */
object PaymentReadinessPolicy {

    val MAX_SANDBOX_AMOUNT: BigDecimal = BigDecimal("100000.00")
    const val MAX_DECIMAL_SCALE: Int = 2
    const val READINESS_WARNING_MSG: String = "Đang tải số dư ví, vui lòng đợi..."

    fun canOperate(isWalletLoaded: Boolean, currentBalance: Double?): Boolean {
        return isWalletLoaded && currentBalance != null && currentBalance >= 0.0
    }

    fun getReadinessWarning(): String = READINESS_WARNING_MSG

    sealed class ValidationResult {
        data class Valid(val amount: BigDecimal, val projectedBalance: BigDecimal) : ValidationResult()
        object WalletNotReady : ValidationResult()
        object AmountMissingOrInvalid : ValidationResult()
        object AmountNotPositive : ValidationResult()
        data class ScaleExceeded(val maxScale: Int) : ValidationResult()
        data class ExceedsMaxLimit(val maxAmount: BigDecimal) : ValidationResult()
        data class InsufficientFunds(val requested: BigDecimal, val available: BigDecimal) : ValidationResult()
    }

    fun validateAmount(
        rawAmountStr: String?,
        isDeposit: Boolean,
        isWalletLoaded: Boolean,
        currentBalance: Double?
    ): ValidationResult {
        if (!canOperate(isWalletLoaded, currentBalance)) {
            return ValidationResult.WalletNotReady
        }

        if (rawAmountStr.isNullOrBlank()) {
            return ValidationResult.AmountMissingOrInvalid
        }

        val amount: BigDecimal
        try {
            amount = BigDecimal(rawAmountStr.trim())
        } catch (e: Exception) {
            return ValidationResult.AmountMissingOrInvalid
        }

        if (amount.stripTrailingZeros().scale() > MAX_DECIMAL_SCALE) {
            return ValidationResult.ScaleExceeded(MAX_DECIMAL_SCALE)
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.AmountNotPositive
        }

        if (amount.compareTo(MAX_SANDBOX_AMOUNT) > 0) {
            return ValidationResult.ExceedsMaxLimit(MAX_SANDBOX_AMOUNT)
        }

        val curBalBig = BigDecimal.valueOf(currentBalance!!)
        val projected = if (isDeposit) curBalBig.add(amount) else curBalBig.subtract(amount)

        if (!isDeposit && projected.compareTo(BigDecimal.ZERO) < 0) {
            return ValidationResult.InsufficientFunds(amount, curBalBig)
        }

        return ValidationResult.Valid(amount.setScale(MAX_DECIMAL_SCALE, RoundingMode.HALF_UP), projected)
    }
}