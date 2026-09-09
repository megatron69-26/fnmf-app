package com.example.nhumonglenh.ui.payment

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.CreatePaymentRequest
import com.example.nhumonglenh.data.remote.PaymentOrderDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.fragment.app.setFragmentResult
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

class SandboxPaymentBottomSheet : BottomSheetDialogFragment() {

    private var paymentType: String = "DEPOSIT"
    private var currentBalance: Double = 0.0
    private val idempotencyManager = PaymentIdempotencyManager()
    private var activeCall: Call<PaymentOrderDto>? = null

    companion object {
        const val REQUEST_KEY_PAYMENT = "request_key_sandbox_payment"
        const val KEY_PAYMENT_ORDER_ID = "key_payment_order_id"
        const val KEY_CHECKOUT_URL = "key_checkout_url"
        const val KEY_PAYMENT_TYPE = "key_payment_type"
        const val KEY_AMOUNT_USD = "key_amount_usd"
        const val KEY_STATUS = "key_status"
        const val KEY_MESSAGE = "key_message"

        private const val ARG_TYPE = "arg_type"
        private const val ARG_BALANCE = "arg_balance"

        fun newInstance(type: String, balance: Double): SandboxPaymentBottomSheet {
            val f = SandboxPaymentBottomSheet()
            val b = Bundle()
            b.putString(ARG_TYPE, type)
            b.putDouble(ARG_BALANCE, balance)
            f.arguments = b
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            paymentType = it.getString(ARG_TYPE, "DEPOSIT")
            currentBalance = it.getDouble(ARG_BALANCE, 0.0)
        }
        idempotencyManager.restoreFromBundle(savedInstanceState)
        if (idempotencyManager.activeClientRequestId == null) {
            context?.let { idempotencyManager.restoreFromPreferences(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        idempotencyManager.saveToBundle(outState)
        context?.let { idempotencyManager.saveToPreferences(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_sandbox_payment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle: TextView = view.findViewById(R.id.tv_sheet_title)
        val tvSubtitle: TextView = view.findViewById(R.id.tv_sheet_subtitle)
        val tvCurrentBal: TextView = view.findViewById(R.id.tv_sheet_current_balance)
        val tvProjectedBal: TextView = view.findViewById(R.id.tv_sheet_projected_balance)
        val layoutAmount: TextInputLayout = view.findViewById(R.id.layout_amount_input)
        val etAmount: TextInputEditText = view.findViewById(R.id.et_amount_input)
        val btnConfirm: MaterialButton = view.findViewById(R.id.btn_confirm_payment)
        val pbLoading: ProgressBar = view.findViewById(R.id.pb_sheet_loading)

        val btn100: MaterialButton = view.findViewById(R.id.btn_preset_100)
        val btn500: MaterialButton = view.findViewById(R.id.btn_preset_500)
        val btn1000: MaterialButton = view.findViewById(R.id.btn_preset_1000)
        val btn5000: MaterialButton = view.findViewById(R.id.btn_preset_5000)

        val isDeposit = paymentType.equals("DEPOSIT", ignoreCase = true)
        if (isDeposit) {
            tvTitle.text = "Nạp tiền Sandbox"
            tvSubtitle.text = "Mô phỏng nạp tiền USD vào ví vốn ảo FNMF (Tối đa \$100,000.00)"
            btnConfirm.text = "TIẾP TỤC ĐẾN CỔNG NẠP SANDBOX"
            btnConfirm.setBackgroundColor(android.graphics.Color.parseColor("#0284C7"))
        } else {
            tvTitle.text = "Rút tiền Sandbox"
            tvSubtitle.text = "Mô phỏng rút tiền USD từ ví vốn ảo FNMF (Tối đa \$100,000.00)"
            btnConfirm.text = "TIẾP TỤC ĐẾN CỔNG RÚT SANDBOX"
            btnConfirm.setBackgroundColor(android.graphics.Color.parseColor("#D97706"))
        }

        tvCurrentBal.text = String.format(Locale.US, "$%,.2f USD", currentBalance)
        tvProjectedBal.text = String.format(Locale.US, "$%,.2f USD", currentBalance)

        fun updateProjectedBalance(rawStr: String) {
            layoutAmount.error = null
            if (rawStr.isBlank()) {
                tvProjectedBal.text = String.format(Locale.US, "$%,.2f USD", currentBalance)
                return
            }
            try {
                val inputNum = BigDecimal(rawStr.trim())
                if (inputNum.stripTrailingZeros().scale() > 2) {
                    layoutAmount.error = "Tối đa 2 chữ số thập phân (cents)"
                    return
                }
                if (inputNum.compareTo(BigDecimal.ZERO) <= 0) {
                    layoutAmount.error = "Số tiền phải lớn hơn 0"
                    return
                }
                if (inputNum.compareTo(BigDecimal("100000.00")) > 0) {
                    layoutAmount.error = "Số tiền tối đa \$100,000.00 USD"
                    return
                }

                val curBig = BigDecimal.valueOf(currentBalance)
                val projected = if (isDeposit) curBig.add(inputNum) else curBig.subtract(inputNum)

                if (!isDeposit && projected.compareTo(BigDecimal.ZERO) < 0) {
                    layoutAmount.error = "Số dư không đủ để thực hiện rút tiền"
                    tvProjectedBal.setTextColor(android.graphics.Color.parseColor("#F87171"))
                } else {
                    tvProjectedBal.setTextColor(android.graphics.Color.parseColor("#34D399"))
                }
                tvProjectedBal.text = String.format(Locale.US, "$%,.2f USD", projected.toDouble())
            } catch (e: Exception) {
                layoutAmount.error = "Số tiền không hợp lệ"
            }
        }

        etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateProjectedBalance(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btn100.setOnClickListener { etAmount.setText("100.00"); etAmount.setSelection(etAmount.text?.length ?: 0) }
        btn500.setOnClickListener { etAmount.setText("500.00"); etAmount.setSelection(etAmount.text?.length ?: 0) }
        btn1000.setOnClickListener { etAmount.setText("1000.00"); etAmount.setSelection(etAmount.text?.length ?: 0) }
        btn5000.setOnClickListener { etAmount.setText("5000.00"); etAmount.setSelection(etAmount.text?.length ?: 0) }

        btnConfirm.setOnClickListener {
            val inputStr = etAmount.text?.toString()?.trim() ?: ""
            if (inputStr.isBlank()) {
                layoutAmount.error = "Vui lòng nhập số tiền"
                return@setOnClickListener
            }

            val amount: BigDecimal
            try {
                amount = BigDecimal(inputStr)
            } catch (e: Exception) {
                layoutAmount.error = "Số tiền không hợp lệ"
                return@setOnClickListener
            }

            if (amount.stripTrailingZeros().scale() > 2) {
                layoutAmount.error = "Số tiền USD tối đa 2 chữ số thập phân"
                return@setOnClickListener
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                layoutAmount.error = "Số tiền phải lớn hơn 0"
                return@setOnClickListener
            }
            if (amount.compareTo(BigDecimal("100000.00")) > 0) {
                layoutAmount.error = "Số tiền tối đa \$100,000.00 USD"
                return@setOnClickListener
            }

            if (!isDeposit && amount.compareTo(BigDecimal.valueOf(currentBalance)) > 0) {
                layoutAmount.error = "Số dư khả dụng không đủ để rút tiền"
                return@setOnClickListener
            }

            val context = requireContext()
            val authHeader = AuthSessionManager.getAuthHeader(context)
            if (authHeader.isNullOrBlank()) {
                Toast.makeText(context, "Phiên đăng nhập hết hạn, vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show()
                dismiss()
                return@setOnClickListener
            }

            // Sinh Idempotency Key (hoặc giữ nguyên khi retry mạng)
            val clientRequestId = idempotencyManager.resolveClientRequestId(paymentType, amount)
            val request = CreatePaymentRequest(
                amountUsd = amount.setScale(2, RoundingMode.HALF_UP),
                clientRequestId = clientRequestId
            )

            btnConfirm.isEnabled = false
            pbLoading.visibility = View.VISIBLE

            val apiService = RetrofitClient.apiService
            val call = if (isDeposit) {
                apiService.createDeposit(authHeader, request)
            } else {
                apiService.createWithdrawal(authHeader, request)
            }
            activeCall = call

            call.enqueue(object : Callback<PaymentOrderDto> {
                override fun onResponse(call: Call<PaymentOrderDto>, response: Response<PaymentOrderDto>) {
                    if (!isAdded) return
                    btnConfirm.isEnabled = true
                    pbLoading.visibility = View.GONE

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        context?.let { idempotencyManager.clearPreferences(it) } ?: idempotencyManager.reset()

                        val resultBundle = Bundle().apply {
                            putLong(KEY_PAYMENT_ORDER_ID, body.paymentOrderId ?: -1L)
                            putString(KEY_CHECKOUT_URL, body.checkoutUrl)
                            putString(KEY_PAYMENT_TYPE, body.type)
                            putDouble(KEY_AMOUNT_USD, body.amountUsd ?: 0.0)
                            putString(KEY_STATUS, body.status)
                            putString(KEY_MESSAGE, body.message)
                        }
                        setFragmentResult(REQUEST_KEY_PAYMENT, resultBundle)
                        dismiss()
                    } else {
                        val errorMsg = response.errorBody()?.string() ?: "Tạo giao dịch thất bại (HTTP ${response.code()})"
                        layoutAmount.error = errorMsg
                    }
                }

                override fun onFailure(call: Call<PaymentOrderDto>, t: Throwable) {
                    if (!isAdded) return
                    btnConfirm.isEnabled = true
                    pbLoading.visibility = View.GONE
                    layoutAmount.error = "Lỗi kết nối mạng: ${t.localizedMessage ?: "Timeout"}. Bạn có thể bấm lại để thử lại với cùng mã giao dịch."
                }
            })
        }
    }

    override fun onDestroyView() {
        activeCall?.cancel()
        activeCall = null
        super.onDestroyView()
    }
}