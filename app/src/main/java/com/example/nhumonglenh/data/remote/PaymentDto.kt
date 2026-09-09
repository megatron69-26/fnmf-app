package com.example.nhumonglenh.data.remote

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CreatePaymentRequest(
    @SerializedName("amountUsd")
    val amountUsd: BigDecimal,
    @SerializedName("clientRequestId")
    val clientRequestId: String
)

data class PaymentOrderDto(
    @SerializedName("paymentOrderId")
    val paymentOrderId: Long? = null,
    @SerializedName("type")
    val type: String? = null, // DEPOSIT, WITHDRAWAL
    @SerializedName("amountUsd")
    val amountUsd: Double? = null,
    @SerializedName("status")
    val status: String? = null, // PENDING, PROCESSING, SUCCEEDED, FAILED, CANCELLED
    @SerializedName("checkoutUrl")
    val checkoutUrl: String? = null,
    @SerializedName("provider")
    val provider: String? = null,
    @SerializedName("createdAt")
    val createdAt: String? = null,
    @SerializedName("completedAt")
    val completedAt: String? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("failureReason")
    val failureReason: String? = null
)