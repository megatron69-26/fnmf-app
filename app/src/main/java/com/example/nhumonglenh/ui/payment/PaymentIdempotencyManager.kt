package com.example.nhumonglenh.ui.payment

import android.content.Context
import android.os.Bundle
import com.example.nhumonglenh.data.remote.NetworkConfig
import java.math.BigDecimal
import java.util.UUID

/**
 * Quản lý Idempotency Key cho các lệnh Nạp / Rút tiền Sandbox.
 * Đảm bảo:
 * 1. Khi retry mạng do timeout/lỗi kết nối cùng số tiền và loại giao dịch -> Giữ nguyên clientRequestId.
 * 2. Khi người dùng thay đổi số tiền hoặc loại giao dịch (Nạp <-> Rút) -> Bắt buộc sinh clientRequestId mới.
 * 3. Kiểm soát scale số tiền USD tối đa 2 chữ số thập phân (cents).
 * 4. Hỗ trợ lưu trữ và khôi phục trạng thái qua Bundle và SharedPreferences chống mất key khi Activity recreation / Process death.
 */
class PaymentIdempotencyManager {

    companion object {
        private const val KEY_IDEMP_REQ_ID = "payment_idemp_active_req_id"
        private const val KEY_IDEMP_TYPE = "payment_idemp_last_type"
        private const val KEY_IDEMP_AMOUNT = "payment_idemp_last_amount"
    }

    var activeClientRequestId: String? = null
        private set

    var lastType: String? = null
        private set

    var lastAmount: BigDecimal? = null
        private set

    fun isValidScale(amount: BigDecimal): Boolean {
        return amount.stripTrailingZeros().scale() <= 2
    }

    fun resolveClientRequestId(type: String, amount: BigDecimal): String {
        val normalizedAmount = amount.stripTrailingZeros()
        if (!isValidScale(normalizedAmount)) {
            throw IllegalArgumentException("Số tiền USD tối đa 2 chữ số thập phân (scale <= 2)")
        }

        val sameType = (lastType != null && lastType.equals(type, ignoreCase = true))
        val sameAmount = (lastAmount != null && lastAmount!!.compareTo(normalizedAmount) == 0)

        if (sameType && sameAmount && !activeClientRequestId.isNullOrBlank()) {
            return activeClientRequestId!!
        }

        val newId = UUID.randomUUID().toString()
        this.activeClientRequestId = newId
        this.lastType = type
        this.lastAmount = normalizedAmount
        return newId
    }

    fun resolveClientOrderId(type: String, amount: BigDecimal): String {
        return resolveClientRequestId(type, amount)
    }

    fun reset() {
        this.activeClientRequestId = null
        this.lastType = null
        this.lastAmount = null
    }

    data class StateSnapshot(
        val activeClientRequestId: String?,
        val lastType: String?,
        val lastAmount: BigDecimal?
    )

    fun toSnapshot(): StateSnapshot = StateSnapshot(activeClientRequestId, lastType, lastAmount)

    fun restoreFromSnapshot(snapshot: StateSnapshot) {
        this.activeClientRequestId = snapshot.activeClientRequestId
        this.lastType = snapshot.lastType
        this.lastAmount = snapshot.lastAmount
    }

    fun restoreState(requestId: String?, type: String?, amountStr: String?) {
        this.activeClientRequestId = requestId
        this.lastType = type
        this.lastAmount = if (!amountStr.isNullOrBlank()) {
            try { BigDecimal(amountStr).stripTrailingZeros() } catch (e: Exception) { null }
        } else null
    }

    fun saveToBundle(outState: Bundle) {
        outState.putString(KEY_IDEMP_REQ_ID, activeClientRequestId)
        outState.putString(KEY_IDEMP_TYPE, lastType)
        outState.putString(KEY_IDEMP_AMOUNT, lastAmount?.toPlainString())
    }

    fun restoreFromBundle(savedState: Bundle?) {
        if (savedState == null) return
        restoreState(
            savedState.getString(KEY_IDEMP_REQ_ID),
            savedState.getString(KEY_IDEMP_TYPE),
            savedState.getString(KEY_IDEMP_AMOUNT)
        )
    }

    fun getPrefKey(baseKey: String, userIdentifier: String?): String {
        val clean = userIdentifier?.trim()?.lowercase(java.util.Locale.ROOT)
        return if (!clean.isNullOrEmpty()) "${baseKey}_$clean" else baseKey
    }

    fun saveToPreferences(context: Context, userIdentifier: String? = null) {
        val user = userIdentifier ?: com.example.nhumonglenh.data.local.AuthSessionManager.getUserEmail(context)
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(getPrefKey(KEY_IDEMP_REQ_ID, user), activeClientRequestId)
            .putString(getPrefKey(KEY_IDEMP_TYPE, user), lastType)
            .putString(getPrefKey(KEY_IDEMP_AMOUNT, user), lastAmount?.toPlainString())
            .apply()
    }

    fun restoreFromPreferences(context: Context, userIdentifier: String? = null) {
        val user = userIdentifier ?: com.example.nhumonglenh.data.local.AuthSessionManager.getUserEmail(context)
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        restoreState(
            prefs.getString(getPrefKey(KEY_IDEMP_REQ_ID, user), null),
            prefs.getString(getPrefKey(KEY_IDEMP_TYPE, user), null),
            prefs.getString(getPrefKey(KEY_IDEMP_AMOUNT, user), null)
        )
    }

    fun clearPreferences(context: Context, userIdentifier: String? = null) {
        reset()
        val user = userIdentifier ?: com.example.nhumonglenh.data.local.AuthSessionManager.getUserEmail(context)
        val prefs = context.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(getPrefKey(KEY_IDEMP_REQ_ID, user))
            .remove(getPrefKey(KEY_IDEMP_TYPE, user))
            .remove(getPrefKey(KEY_IDEMP_AMOUNT, user))
            .apply()
    }
}