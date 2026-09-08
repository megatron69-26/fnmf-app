package com.example.nhumonglenh.ui.trading

import java.math.BigDecimal
import java.math.RoundingMode

data class OrderValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val estimatedOrderValue: Double = 0.0,
    val estimatedRemainingCash: Double = 0.0,
    val estimatedRemainingAsset: Double = 0.0
)

object OrderCalculator {

    fun calculateEstimatedValue(quantity: Double, price: Double): Double {
        if (quantity.isNaN() || quantity.isInfinite() || quantity <= 0.0) return 0.0
        if (price.isNaN() || price.isInfinite() || price <= 0.0) return 0.0
        val value = quantity * price
        return if (value.isNaN() || value.isInfinite()) 0.0 else value
    }

    fun calculateRemainingCash(availableCash: Double, orderValue: Double, orderType: String): Double {
        val cash = if (availableCash.isNaN() || availableCash.isInfinite()) 0.0 else availableCash
        val value = if (orderValue.isNaN() || orderValue.isInfinite()) 0.0 else orderValue
        return if (orderType.equals("BUY", ignoreCase = true)) {
            cash - value
        } else {
            cash + value
        }
    }

    fun calculateRemainingAsset(ownedQuantity: Double, orderQuantity: Double, orderType: String): Double {
        val owned = if (ownedQuantity.isNaN() || ownedQuantity.isInfinite()) 0.0 else ownedQuantity
        val qty = if (orderQuantity.isNaN() || orderQuantity.isInfinite()) 0.0 else orderQuantity
        return if (orderType.equals("BUY", ignoreCase = true)) {
            owned + qty
        } else {
            Math.max(0.0, owned - qty)
        }
    }

    fun validateOrder(
        orderType: String,
        quantity: Double?,
        currentPrice: Double,
        availableCash: Double,
        ownedQuantity: Double
    ): OrderValidationResult {
        if (quantity == null || quantity.isNaN() || quantity.isInfinite() || quantity <= 0.0) {
            return OrderValidationResult(
                isValid = false,
                errorMessage = "Khối lượng phải là số hữu hạn lớn hơn 0"
            )
        }

        if (currentPrice.isNaN() || currentPrice.isInfinite() || currentPrice <= 0.0) {
            return OrderValidationResult(
                isValid = false,
                errorMessage = "Giá thị trường chưa hợp lệ"
            )
        }

        val estValue = calculateEstimatedValue(quantity, currentPrice)
        if (estValue.isNaN() || estValue.isInfinite() || estValue <= 0.0) {
            return OrderValidationResult(
                isValid = false,
                errorMessage = "Giá trị lệnh không hợp lệ"
            )
        }

        val isBuy = orderType.equals("BUY", ignoreCase = true)
        return if (isBuy) {
            val remCash = availableCash - estValue
            val remAsset = ownedQuantity + quantity
            if (estValue > availableCash + 0.000001) {
                OrderValidationResult(
                    isValid = false,
                    errorMessage = "Số dư tiền mặt khả dụng không đủ để đặt lệnh",
                    estimatedOrderValue = estValue,
                    estimatedRemainingCash = remCash,
                    estimatedRemainingAsset = remAsset
                )
            } else {
                OrderValidationResult(
                    isValid = true,
                    errorMessage = null,
                    estimatedOrderValue = estValue,
                    estimatedRemainingCash = Math.max(0.0, remCash),
                    estimatedRemainingAsset = remAsset
                )
            }
        } else {
            val remCash = availableCash + estValue
            val remAsset = ownedQuantity - quantity
            if (quantity > ownedQuantity + 0.00000001) {
                OrderValidationResult(
                    isValid = false,
                    errorMessage = "Số lượng tài sản trong ví không đủ để bán",
                    estimatedOrderValue = estValue,
                    estimatedRemainingCash = remCash,
                    estimatedRemainingAsset = remAsset
                )
            } else {
                OrderValidationResult(
                    isValid = true,
                    errorMessage = null,
                    estimatedOrderValue = estValue,
                    estimatedRemainingCash = remCash,
                    estimatedRemainingAsset = Math.max(0.0, remAsset)
                )
            }
        }
    }

    fun calculateQuickQuantity(
        percent: Int,
        orderType: String,
        availableCash: Double,
        currentPrice: Double,
        ownedQuantity: Double
    ): Double {
        if (percent <= 0 || percent > 100) return 0.0
        val isBuy = orderType.equals("BUY", ignoreCase = true)
        return if (isBuy) {
            if (currentPrice <= 0.0 || availableCash <= 0.0) return 0.0
            val buffer = if (percent == 100) 0.999 else 1.0
            val budget = availableCash * (percent / 100.0) * buffer
            val rawQty = budget / currentPrice
            roundDown(rawQty, 4)
        } else {
            if (ownedQuantity <= 0.0) return 0.0
            val rawQty = ownedQuantity * (percent / 100.0)
            roundDown(rawQty, 4)
        }
    }

    fun parseBackendErrorMessage(errBody: String?): String? {
        if (errBody.isNullOrBlank()) return null
        return try {
            val json = org.json.JSONObject(errBody)
            when {
                json.has("message") -> json.optString("message").takeIf { it.isNotBlank() }
                json.has("error") -> json.optString("error").takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun roundDown(value: Double, decimals: Int): Double {
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return 0.0
        return try {
            BigDecimal(value).setScale(decimals, RoundingMode.DOWN).toDouble()
        } catch (_: Exception) {
            0.0
        }
    }
}
