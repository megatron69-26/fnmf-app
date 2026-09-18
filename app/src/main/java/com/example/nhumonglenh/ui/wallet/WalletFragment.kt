package com.example.nhumonglenh.ui.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.PaymentOrderDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.repository.WalletProfileCombinedData
import com.example.nhumonglenh.data.repository.WalletProfileRepository
import com.example.nhumonglenh.databinding.FragmentWalletBinding
import com.example.nhumonglenh.ui.payment.PaymentHistoryAdapter
import com.example.nhumonglenh.ui.payment.PaymentReadinessPolicy
import com.example.nhumonglenh.ui.payment.SandboxPaymentBottomSheet
import com.example.nhumonglenh.ui.profile.WalletProfileUiState
import com.example.nhumonglenh.ui.profile.WalletProfileViewModel
import com.example.nhumonglenh.ui.profile.WalletProfileViewModelFactory
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding

    private var isNavigatingToAuth = false

    private val viewModel: WalletProfileViewModel by viewModels {
        WalletProfileViewModelFactory(WalletProfileRepository(RetrofitClient.apiService))
    }

    private lateinit var paymentHistoryAdapter: PaymentHistoryAdapter

    private var currentCashBalance: Double? = null
    private var isWalletLoaded: Boolean = false
    private var activePendingOrderId: Long? = null

    private var historyCall: Call<List<PaymentOrderDto>>? = null
    private var pendingStatusCall: Call<PaymentOrderDto>? = null
    private var cancelCall: Call<PaymentOrderDto>? = null

    companion object {
        private const val KEY_ACTIVE_PENDING_ORDER_ID = "fnmf_active_pending_payment_order_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activePendingOrderId = savedInstanceState?.getLong(KEY_ACTIVE_PENDING_ORDER_ID, -1L)?.takeIf { it > 0 }
            ?: restorePendingOrderId()

        // Fragment Result API: Nhận kết quả từ SandboxPaymentBottomSheet an toàn
        parentFragmentManager.setFragmentResultListener(
            SandboxPaymentBottomSheet.REQUEST_KEY_PAYMENT,
            this
        ) { _, bundle ->
            val orderId = bundle.getLong(SandboxPaymentBottomSheet.KEY_PAYMENT_ORDER_ID, -1L).takeIf { it > 0 }
            val checkoutUrl = bundle.getString(SandboxPaymentBottomSheet.KEY_CHECKOUT_URL)
            if (orderId != null) {
                savePendingOrderId(orderId)
            }
            fetchPaymentHistory()
            if (!checkoutUrl.isNullOrBlank()) {
                openCheckoutUrl(checkoutUrl)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activePendingOrderId?.let { outState.putLong(KEY_ACTIVE_PENDING_ORDER_ID, it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return _binding?.root ?: inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        setupRecyclerView(b)
        setupListeners(b)
        observeUiState()
        fetchPaymentHistory()
        refreshIfStale()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshIfStale()
            checkPendingPaymentStatus()
            fetchPaymentHistory()
        }
    }

    override fun onResume() {
        super.onResume()
        if (activePendingOrderId == null) {
            activePendingOrderId = restorePendingOrderId()
        }
        if (!isHidden) {
            refreshIfStale()
            checkPendingPaymentStatus()
            fetchPaymentHistory()
        }
    }

    private fun refreshIfStale() {
        triggerDataLoad(forceRefresh = false)
    }

    override fun onDestroyView() {
        _binding?.finiPaymentsLoading?.cleanup()
        _binding?.finiLoadingWallet?.cleanup()
        super.onDestroyView()
        historyCall?.cancel()
        historyCall = null
        pendingStatusCall?.cancel()
        pendingStatusCall = null
        cancelCall?.cancel()
        cancelCall = null
        _binding = null
    }

    private fun setupRecyclerView(b: FragmentWalletBinding) {
        paymentHistoryAdapter = PaymentHistoryAdapter(
            onOpenCheckout = { order ->
                openCheckoutUrl(order.checkoutUrl)
            },
            onCancelOrder = { order ->
                val orderId = order.paymentOrderId ?: return@PaymentHistoryAdapter
                cancelPaymentOrder(orderId)
            }
        )
        b.rvPayments.layoutManager = LinearLayoutManager(requireContext())
        b.rvPayments.adapter = paymentHistoryAdapter
    }

    private fun setupListeners(b: FragmentWalletBinding) {
        b.btnWalletRefresh.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
            fetchPaymentHistory()
        }

        b.btnRetry.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
            fetchPaymentHistory()
        }

        b.btnRefreshPayments.setOnClickListener {
            fetchPaymentHistory()
        }

        b.btnSandboxDeposit.isEnabled = false
        b.btnSandboxWithdraw.isEnabled = false

        b.btnSandboxDeposit.setOnClickListener {
            val balance = currentCashBalance
            if (!PaymentReadinessPolicy.canOperate(isWalletLoaded, balance)) {
                Toast.makeText(requireContext(), PaymentReadinessPolicy.getReadinessWarning(), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sheet = SandboxPaymentBottomSheet.newInstance("DEPOSIT", balance!!)
            sheet.show(parentFragmentManager, "DepositBottomSheet")
        }

        b.btnSandboxWithdraw.setOnClickListener {
            val balance = currentCashBalance
            if (!PaymentReadinessPolicy.canOperate(isWalletLoaded, balance)) {
                Toast.makeText(requireContext(), PaymentReadinessPolicy.getReadinessWarning(), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sheet = SandboxPaymentBottomSheet.newInstance("WITHDRAWAL", balance!!)
            sheet.show(parentFragmentManager, "WithdrawBottomSheet")
        }
    }

    private fun triggerDataLoad(forceRefresh: Boolean) {
        if (isNavigatingToAuth) return
        val currentActivity = activity ?: return
        val token = AuthSessionManager.getToken(currentActivity)

        if (token.isBlank()) {
            handleUnauthorized()
            return
        }

        viewModel.loadData(token, forceRefresh = forceRefresh)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val b = binding ?: return@collect
                    when (state) {
                        is WalletProfileUiState.Idle -> {
                            b.finiLoadingWallet.hide()
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.GONE
                        }
                        is WalletProfileUiState.Loading -> {
                            b.layoutLoading.visibility = View.VISIBLE
                            b.finiLoadingWallet.show()
                            b.layoutError.visibility = View.GONE
                        }
                        is WalletProfileUiState.Success -> {
                            b.finiLoadingWallet.hide()
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.GONE
                            b.layoutContent.visibility = View.VISIBLE
                            renderSuccessData(b, state.data, state.isOffline, state.lastUpdatedFormatted)
                        }
                        is WalletProfileUiState.Empty -> {
                            b.finiLoadingWallet.hide()
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.GONE
                            b.layoutContent.visibility = View.VISIBLE
                            renderEmptyData(b)
                        }
                        is WalletProfileUiState.Unauthorized -> {
                            b.finiLoadingWallet.hide()
                            b.layoutLoading.visibility = View.GONE
                            handleUnauthorized()
                        }
                        is WalletProfileUiState.NetworkError -> {
                            b.finiLoadingWallet.hide()
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.VISIBLE
                            b.tvErrorMessage.text = getString(R.string.network_error_msg)
                            if (currentCashBalance == null) {
                                isWalletLoaded = false
                                b.btnSandboxDeposit.isEnabled = false
                                b.btnSandboxWithdraw.isEnabled = false
                            }
                        }
                        is WalletProfileUiState.ServerError -> {
                            b.finiLoadingWallet.hide()
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.VISIBLE
                            b.tvErrorMessage.text = getString(R.string.server_error_msg)
                            if (currentCashBalance == null) {
                                isWalletLoaded = false
                                b.btnSandboxDeposit.isEnabled = false
                                b.btnSandboxWithdraw.isEnabled = false
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderSuccessData(
        b: FragmentWalletBinding,
        data: WalletProfileCombinedData,
        isOffline: Boolean,
        lastUpdatedFormatted: String
    ) {
        val cashBalance = data.portfolio?.cashBalanceUsd ?: data.authResponse?.wallet?.balanceUsd
        currentCashBalance = cashBalance
        isWalletLoaded = (cashBalance != null)
        val canOperate = PaymentReadinessPolicy.canOperate(isWalletLoaded, currentCashBalance)
        b.btnSandboxDeposit.isEnabled = canOperate
        b.btnSandboxWithdraw.isEnabled = canOperate

        if (cashBalance != null) {
            b.tvCashBalance.text = String.format(Locale.US, "$%,.2f", cashBalance)
        } else {
            b.tvCashBalance.text = getString(R.string.portfolio_unavailable)
        }

        if (isOffline) {
            b.tvOfflineBadge.visibility = View.VISIBLE
            b.tvLastUpdated.text = "Ngoại tuyến"
        } else {
            b.tvOfflineBadge.visibility = View.GONE
            if (lastUpdatedFormatted.isNotEmpty()) {
                b.tvLastUpdated.text = "Cập nhật $lastUpdatedFormatted"
            }
        }
    }

    private fun renderEmptyData(b: FragmentWalletBinding) {
        currentCashBalance = null
        isWalletLoaded = false
        b.btnSandboxDeposit.isEnabled = false
        b.btnSandboxWithdraw.isEnabled = false
        b.tvCashBalance.text = getString(R.string.no_data)
        b.tvOfflineBadge.visibility = View.GONE
    }

    private fun fetchPaymentHistory() {
        val b = binding ?: return
        val currentActivity = activity ?: return
        val authHeader = AuthSessionManager.getAuthHeader(currentActivity) ?: return

        historyCall?.cancel()
        b.finiPaymentsLoading.show()
        val call = RetrofitClient.apiService.getPaymentHistory(authHeader)
        historyCall = call
        call.enqueue(object : Callback<List<PaymentOrderDto>> {
            override fun onResponse(call: Call<List<PaymentOrderDto>>, response: Response<List<PaymentOrderDto>>) {
                if (historyCall !== call) return
                if (!isAdded) return
                b.finiPaymentsLoading.hide()
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    if (list.isEmpty()) {
                        b.rvPayments.visibility = View.GONE
                        b.tvPaymentsEmpty.visibility = View.VISIBLE
                    } else {
                        b.rvPayments.visibility = View.VISIBLE
                        b.tvPaymentsEmpty.visibility = View.GONE
                        paymentHistoryAdapter.submitList(list)
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    handleUnauthorized()
                }
            }

            override fun onFailure(call: Call<List<PaymentOrderDto>>, t: Throwable) {
                if (historyCall !== call) return
                if (!isAdded) return
                b.finiPaymentsLoading.hide()
            }
        })
    }

    private fun checkPendingPaymentStatus() {
        val pendingId = activePendingOrderId ?: restorePendingOrderId() ?: return
        activePendingOrderId = pendingId
        val currentActivity = activity ?: return
        val authHeader = AuthSessionManager.getAuthHeader(currentActivity) ?: return

        pendingStatusCall?.cancel()
        val call = RetrofitClient.apiService.getPaymentDetails(authHeader, pendingId)
        pendingStatusCall = call
        call.enqueue(object : Callback<PaymentOrderDto> {
            override fun onResponse(call: Call<PaymentOrderDto>, response: Response<PaymentOrderDto>) {
                if (pendingStatusCall !== call) return
                if (!isAdded) return
                if (response.isSuccessful && response.body() != null) {
                    val order = response.body()!!
                    val status = order.status?.uppercase(Locale.ROOT)
                    if (status == "SUCCEEDED") {
                        savePendingOrderId(null)
                        val typeText = if (order.type == "DEPOSIT") "Nạp" else "Rút"
                        val msg = "$typeText thành công $${String.format(Locale.US, "%.2f", order.amountUsd ?: 0.0)} USD!"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        triggerDataLoad(forceRefresh = true)
                        fetchPaymentHistory()
                    } else if (status == "FAILED" || status == "CANCELLED") {
                        savePendingOrderId(null)
                        val reason = order.failureReason ?: "Giao dịch đã kết thúc ($status)"
                        Toast.makeText(requireContext(), "Giao dịch: $reason", Toast.LENGTH_LONG).show()
                        fetchPaymentHistory()
                    }
                }
            }

            override fun onFailure(call: Call<PaymentOrderDto>, t: Throwable) {
                if (pendingStatusCall !== call) return
            }
        })
    }

    private fun cancelPaymentOrder(orderId: Long) {
        val currentActivity = activity ?: return
        val authHeader = AuthSessionManager.getAuthHeader(currentActivity) ?: return

        AlertDialog.Builder(requireContext())
            .setTitle("Hủy yêu cầu thanh toán")
            .setMessage("Bạn có chắc chắn muốn hủy yêu cầu thanh toán #${orderId}?")
            .setPositiveButton("Hủy lệnh") { _, _ ->
                cancelCall?.cancel()
                val call = RetrofitClient.apiService.cancelPayment(authHeader, orderId)
                cancelCall = call
                call.enqueue(object : Callback<PaymentOrderDto> {
                    override fun onResponse(call: Call<PaymentOrderDto>, response: Response<PaymentOrderDto>) {
                        if (cancelCall !== call) return
                        if (!isAdded) return
                        if (response.isSuccessful) {
                            Toast.makeText(requireContext(), getString(R.string.payment_cancel_success, orderId), Toast.LENGTH_SHORT).show()
                            if (activePendingOrderId == orderId) {
                                savePendingOrderId(null)
                            }
                            fetchPaymentHistory()
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.payment_cancel_failed), Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<PaymentOrderDto>, t: Throwable) {
                        if (cancelCall !== call) return
                        if (!isAdded) return
                        Toast.makeText(requireContext(), getString(R.string.payment_cancel_network_error), Toast.LENGTH_SHORT).show()
                    }
                })
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun getPendingOrderKey(ctx: Context): String {
        val email = AuthSessionManager.getUserEmail(ctx).trim().lowercase(Locale.ROOT)
        return if (email.isNotEmpty()) "${KEY_ACTIVE_PENDING_ORDER_ID}_$email" else KEY_ACTIVE_PENDING_ORDER_ID
    }

    private fun savePendingOrderId(orderId: Long?) {
        activePendingOrderId = orderId
        val ctx = context ?: return
        val key = getPendingOrderKey(ctx)
        val prefs = ctx.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (orderId != null) {
                putLong(key, orderId)
            } else {
                remove(key)
            }
            apply()
        }
    }

    private fun restorePendingOrderId(): Long? {
        val ctx = context ?: return null
        val key = getPendingOrderKey(ctx)
        val prefs = ctx.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(key)) {
            prefs.getLong(key, -1L).takeIf { it > 0 }
        } else {
            null
        }
    }

    private var lastOpenedCheckoutUrl: String? = null
    private var lastOpenedCheckoutTime: Long = 0L

    private fun openCheckoutUrl(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Không tìm thấy đường dẫn thanh toán", Toast.LENGTH_SHORT).show()
            return
        }
        val now = System.currentTimeMillis()
        if (url == lastOpenedCheckoutUrl && now - lastOpenedCheckoutTime < 1500) {
            return
        }
        lastOpenedCheckoutUrl = url
        lastOpenedCheckoutTime = now
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), "Không thể mở trình duyệt thanh toán. Vui lòng thử lại.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleUnauthorized() {
        if (isNavigatingToAuth) return
        isNavigatingToAuth = true

        val currentActivity = activity ?: return
        AuthSessionManager.handleUnauthorized(currentActivity)
    }
}
