package com.example.nhumonglenh.ui.trading

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.remote.OrderRequest
import com.example.nhumonglenh.data.remote.OrderResponse
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.databinding.BottomSheetOrderTicketBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class OrderTicketBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetOrderTicketBinding? = null
    private val binding get() = _binding

    private var orderType: String = "BUY"
    private var symbol: String = "BTCUSDT"
    private var currentPrice: Double = 0.0
    private var availableCash: Double = 0.0
    private var ownedQuantity: Double = 0.0

    private var isSubmitting = false
    private var activeOrderCall: Call<OrderResponse>? = null
    var onOrderSuccessListener: ((OrderResponse) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            orderType = it.getString(ARG_ORDER_TYPE, "BUY")
            symbol = it.getString(ARG_SYMBOL, "BTCUSDT")
            currentPrice = it.getDouble(ARG_CURRENT_PRICE, 0.0)
            availableCash = it.getDouble(ARG_AVAILABLE_CASH, 0.0)
            ownedQuantity = it.getDouble(ARG_OWNED_QUANTITY, 0.0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BottomSheetOrderTicketBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        val isBuy = orderType.equals("BUY", ignoreCase = true)
        val accentColor = ContextCompat.getColor(requireContext(), if (isBuy) R.color.tv_green else R.color.tv_red)
        val assetTicker = getAssetTicker(symbol)

        // 1. Header & Badges
        b.tvOrderSymbol.text = formatSymbolDisplay(symbol)
        b.tvOrderTypeBadge.text = if (isBuy) getString(R.string.order_ticket_title_buy) else getString(R.string.order_ticket_title_sell)
        b.tvOrderTypeBadge.setBackgroundColor(accentColor)

        // 2. Button styling
        b.btnOrderConfirm.text = if (isBuy) getString(R.string.order_ticket_btn_confirm_buy) else getString(R.string.order_ticket_btn_confirm_sell)
        b.btnOrderConfirm.backgroundTintList = ColorStateList.valueOf(accentColor)
        b.tilOrderQuantity.hint = getString(R.string.order_ticket_qty_label, assetTicker)
        b.tvRemainingLabel.text = if (isBuy) getString(R.string.order_ticket_remaining_cash_label) else getString(R.string.order_ticket_remaining_asset_label)

        // 3. Current numbers
        b.tvOrderMarketPrice.text = String.format(Locale.US, "$%,.2f", currentPrice)
        b.tvOrderAvailableCash.text = String.format(Locale.US, "$%,.2f USD", availableCash)
        b.tvOrderOwnedQty.text = String.format(Locale.US, "%.4f %s", ownedQuantity, assetTicker)

        // 4. Listeners
        b.btnDismissSheet.setOnClickListener { dismiss() }
        b.btnOrderCancel.setOnClickListener { dismiss() }

        b.etOrderQuantity.doAfterTextChanged {
            validateAndUpdateUi()
        }

        b.btnQuick25.setOnClickListener { setQuickQuantity(25) }
        b.btnQuick50.setOnClickListener { setQuickQuantity(50) }
        b.btnQuick75.setOnClickListener { setQuickQuantity(75) }
        b.btnQuick100.setOnClickListener { setQuickQuantity(100) }

        b.btnOrderConfirm.setOnClickListener {
            submitOrder()
        }

        // Set default quantity suggestion
        val defaultQty = when {
            symbol.contains("BTC") -> "0.005"
            symbol.contains("ETH") -> "0.05"
            symbol.contains("XAU") -> "1.0"
            else -> "1.0"
        }
        b.etOrderQuantity.setText(defaultQty)
        b.etOrderQuantity.setSelection(b.etOrderQuantity.text?.length ?: 0)
        validateAndUpdateUi()
    }

    private fun setQuickQuantity(percent: Int) {
        val qty = OrderCalculator.calculateQuickQuantity(
            percent = percent,
            orderType = orderType,
            availableCash = availableCash,
            currentPrice = currentPrice,
            ownedQuantity = ownedQuantity
        )
        val text = if (qty > 0.0) String.format(Locale.US, "%.4f", qty).trimEnd('0').trimEnd('.') else "0.00"
        binding?.etOrderQuantity?.setText(text)
        binding?.etOrderQuantity?.setSelection(binding?.etOrderQuantity?.text?.length ?: 0)
    }

    private fun validateAndUpdateUi() {
        val b = binding ?: return
        val qtyText = b.etOrderQuantity.text?.toString()?.trim() ?: ""
        val qty = qtyText.toDoubleOrNull()
        val assetTicker = getAssetTicker(symbol)

        val result = OrderCalculator.validateOrder(
            orderType = orderType,
            quantity = qty,
            currentPrice = currentPrice,
            availableCash = availableCash,
            ownedQuantity = ownedQuantity
        )

        b.tvOrderEstimatedValue.text = String.format(Locale.US, "$%,.2f USD", result.estimatedOrderValue)

        val isBuy = orderType.equals("BUY", ignoreCase = true)
        if (isBuy) {
            b.tvOrderRemainingValue.text = String.format(Locale.US, "$%,.2f USD", result.estimatedRemainingCash)
            val remCashColor = ContextCompat.getColor(
                requireContext(),
                if (result.estimatedRemainingCash < 0) R.color.tv_red else R.color.tv_green
            )
            b.tvOrderRemainingValue.setTextColor(remCashColor)
        } else {
            b.tvOrderRemainingValue.text = String.format(Locale.US, "%.4f %s", result.estimatedRemainingAsset, assetTicker)
            val remAssetColor = ContextCompat.getColor(
                requireContext(),
                if (result.estimatedRemainingAsset < 0) R.color.tv_red else R.color.tv_text_primary
            )
            b.tvOrderRemainingValue.setTextColor(remAssetColor)
        }

        if (!result.isValid) {
            b.tvOrderErrorMessage.text = result.errorMessage
            b.tvOrderErrorMessage.visibility = View.VISIBLE
            b.btnOrderConfirm.isEnabled = false
            b.btnOrderConfirm.alpha = 0.5f
        } else {
            b.tvOrderErrorMessage.visibility = View.GONE
            b.btnOrderConfirm.isEnabled = !isSubmitting
            b.btnOrderConfirm.alpha = if (isSubmitting) 0.5f else 1.0f
        }
    }

    private fun submitOrder() {
        if (isSubmitting) return

        val b = binding ?: return
        val qty = b.etOrderQuantity.text?.toString()?.trim()?.toDoubleOrNull()
        val result = OrderCalculator.validateOrder(
            orderType = orderType,
            quantity = qty,
            currentPrice = currentPrice,
            availableCash = availableCash,
            ownedQuantity = ownedQuantity
        )

        if (!result.isValid || qty == null) {
            b.tvOrderErrorMessage.text = result.errorMessage ?: getString(R.string.order_ticket_err_invalid_qty)
            b.tvOrderErrorMessage.visibility = View.VISIBLE
            return
        }

        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("jwt_token", "") ?: ""
        val authHeader = AuthHeaderFactory.createBearerHeader(token)

        if (authHeader == null) {
            // Không có token thật -> báo lỗi hết hạn phiên, KHÔNG tạo mock order giả
            b.tvOrderErrorMessage.text = getString(R.string.session_expired_msg)
            b.tvOrderErrorMessage.visibility = View.VISIBLE
            b.btnOrderConfirm.isEnabled = true
            b.btnOrderCancel.isEnabled = true
            b.pbOrderSubmitting.visibility = View.GONE
            return
        }

        isSubmitting = true
        b.btnOrderConfirm.isEnabled = false
        b.btnOrderCancel.isEnabled = false
        b.pbOrderSubmitting.visibility = View.VISIBLE
        b.tvOrderErrorMessage.visibility = View.GONE

        val request = OrderRequest(symbol = symbol, type = orderType, quantity = qty)

        activeOrderCall?.cancel()
        val call = RetrofitClient.apiService.placeOrder(authHeader, request)
        activeOrderCall = call

        call.enqueue(object : Callback<OrderResponse> {
            override fun onResponse(call: Call<OrderResponse>, response: Response<OrderResponse>) {
                if (call.isCanceled || !isAdded || _binding == null) return

                isSubmitting = false
                val currentBinding = binding ?: return
                currentBinding.pbOrderSubmitting.visibility = View.GONE

                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val resultBundle = Bundle().apply {
                        putBoolean(KEY_ORDER_SUCCESS, true)
                        putString(KEY_ORDER_TYPE, orderType)
                        putString(KEY_SYMBOL, symbol)
                        putDouble(KEY_ORDER_QUANTITY, body.quantity ?: qty)
                        putString(KEY_SUCCESS_MESSAGE, body.message)
                    }
                    setFragmentResult(REQUEST_KEY_ORDER, resultBundle)
                    onOrderSuccessListener?.invoke(body)
                    dismiss()
                } else if (response.isSuccessful && body == null) {
                    currentBinding.btnOrderConfirm.isEnabled = true
                    currentBinding.btnOrderCancel.isEnabled = true
                    currentBinding.tvOrderErrorMessage.text = getString(R.string.order_ticket_err_body_null)
                    currentBinding.tvOrderErrorMessage.visibility = View.VISIBLE
                } else {
                    currentBinding.btnOrderConfirm.isEnabled = true
                    currentBinding.btnOrderCancel.isEnabled = true

                    val errBody = response.errorBody()?.string()
                    val backendMsg = OrderCalculator.parseBackendErrorMessage(errBody)
                    val displayErr = when (response.code()) {
                        401, 403 -> getString(R.string.session_expired_msg)
                        400 -> backendMsg ?: getString(R.string.order_error_generic)
                        in 500..599 -> getString(R.string.server_error_msg)
                        else -> backendMsg ?: "${getString(R.string.order_error_generic)} (${response.code()})"
                    }
                    currentBinding.tvOrderErrorMessage.text = displayErr
                    currentBinding.tvOrderErrorMessage.visibility = View.VISIBLE
                }
            }

            override fun onFailure(call: Call<OrderResponse>, t: Throwable) {
                if (call.isCanceled || !isAdded || _binding == null) return

                isSubmitting = false
                val currentBinding = binding ?: return
                currentBinding.pbOrderSubmitting.visibility = View.GONE
                currentBinding.btnOrderConfirm.isEnabled = true
                currentBinding.btnOrderCancel.isEnabled = true

                currentBinding.tvOrderErrorMessage.text = getString(R.string.network_error_msg)
                currentBinding.tvOrderErrorMessage.visibility = View.VISIBLE
            }
        })
    }

    override fun onDestroyView() {
        activeOrderCall?.cancel()
        activeOrderCall = null
        isSubmitting = false
        onOrderSuccessListener = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OrderTicketBottomSheet"
        const val REQUEST_KEY_ORDER = "REQUEST_KEY_ORDER"
        const val KEY_ORDER_SUCCESS = "KEY_ORDER_SUCCESS"
        const val KEY_ORDER_TYPE = "KEY_ORDER_TYPE"
        const val KEY_SYMBOL = "KEY_SYMBOL"
        const val KEY_ORDER_QUANTITY = "KEY_ORDER_QUANTITY"
        const val KEY_SUCCESS_MESSAGE = "KEY_SUCCESS_MESSAGE"

        private const val ARG_ORDER_TYPE = "ARG_ORDER_TYPE"
        private const val ARG_SYMBOL = "ARG_SYMBOL"
        private const val ARG_CURRENT_PRICE = "ARG_CURRENT_PRICE"
        private const val ARG_AVAILABLE_CASH = "ARG_AVAILABLE_CASH"
        private const val ARG_OWNED_QUANTITY = "ARG_OWNED_QUANTITY"

        fun formatSymbolDisplay(sym: String): String {
            return when {
                sym.contains("/") -> sym
                sym.endsWith("USDT") -> "${sym.removeSuffix("USDT")}/USDT"
                sym.endsWith("USD") -> "${sym.removeSuffix("USD")}/USD"
                else -> sym
            }
        }

        fun getAssetTicker(sym: String): String {
            return sym.replace("/", "").replace("USDT", "").replace("USD", "")
        }

        fun newInstance(
            orderType: String,
            symbol: String,
            currentPrice: Double,
            availableCash: Double,
            ownedQuantity: Double
        ): OrderTicketBottomSheet {
            val fragment = OrderTicketBottomSheet()
            val args = Bundle().apply {
                putString(ARG_ORDER_TYPE, orderType)
                putString(ARG_SYMBOL, symbol)
                putDouble(ARG_CURRENT_PRICE, currentPrice)
                putDouble(ARG_AVAILABLE_CASH, availableCash)
                putDouble(ARG_OWNED_QUANTITY, ownedQuantity)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
