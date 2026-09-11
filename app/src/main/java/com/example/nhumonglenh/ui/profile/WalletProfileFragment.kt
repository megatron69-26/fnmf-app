package com.example.nhumonglenh.ui.profile

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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nhumonglenh.Activity1
import com.example.nhumonglenh.Activity2
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.PaymentOrderDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.repository.WalletProfileCombinedData
import com.example.nhumonglenh.data.repository.WalletProfileRepository
import com.example.nhumonglenh.databinding.FragmentWalletProfileBinding
import com.example.nhumonglenh.ui.payment.PaymentHistoryAdapter
import com.example.nhumonglenh.ui.payment.PaymentReadinessPolicy
import com.example.nhumonglenh.ui.payment.SandboxPaymentBottomSheet
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import kotlin.math.abs

class WalletProfileFragment : Fragment() {

    private var _binding: FragmentWalletProfileBinding? = null
    private val binding get() = _binding

    private var isNavigatingToAuth = false

    private val viewModel: WalletProfileViewModel by viewModels {
        WalletProfileViewModelFactory(WalletProfileRepository(RetrofitClient.apiService))
    }

    private lateinit var holdingAdapter: HoldingAdapter
    private lateinit var tradeHistoryAdapter: TradeHistoryAdapter
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

        // Fragment Result API: Nhận kết quả từ SandboxPaymentBottomSheet an toàn cả khi xoay màn hình/process death
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
        _binding = FragmentWalletProfileBinding.inflate(inflater, container, false)
        return _binding?.root ?: inflater.inflate(R.layout.fragment_wallet_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        setupRecyclerViews(b)
        setupListeners(b)
        displaySystemInfo(b)
        observeUiState()
        fetchPaymentHistory()
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
        super.onDestroyView()
        historyCall?.cancel()
        historyCall = null
        pendingStatusCall?.cancel()
        pendingStatusCall = null
        cancelCall?.cancel()
        cancelCall = null
        _binding = null
    }

    private fun setupRecyclerViews(b: FragmentWalletProfileBinding) {
        holdingAdapter = HoldingAdapter()
        b.rvHoldings.layoutManager = LinearLayoutManager(requireContext())
        b.rvHoldings.adapter = holdingAdapter

        tradeHistoryAdapter = TradeHistoryAdapter()
        b.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        b.rvTransactions.adapter = tradeHistoryAdapter

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

    private fun setupListeners(b: FragmentWalletProfileBinding) {
        // Nút làm mới trên Header
        b.btnProfileRefresh.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
            fetchPaymentHistory()
        }

        // Nút thử lại khi gặp màn hình lỗi
        b.btnRetry.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
            fetchPaymentHistory()
        }

        // Nút Đăng xuất ở cuối màn hình
        b.btnProfileLogout.setOnClickListener {
            (activity as? Activity2)?.showLogoutConfirmationDialog()
        }

        // Mặc định khóa 2 nút Nạp/Rút trước khi có số dư thật từ server
        b.btnSandboxDeposit.isEnabled = false
        b.btnSandboxWithdraw.isEnabled = false

        // Nút Nạp tiền Sandbox
        b.btnSandboxDeposit.setOnClickListener {
            val balance = currentCashBalance
            if (!PaymentReadinessPolicy.canOperate(isWalletLoaded, balance)) {
                Toast.makeText(requireContext(), PaymentReadinessPolicy.getReadinessWarning(), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sheet = SandboxPaymentBottomSheet.newInstance("DEPOSIT", balance!!)
            sheet.show(parentFragmentManager, "DepositBottomSheet")
        }

        // Nút Rút tiền Sandbox
        b.btnSandboxWithdraw.setOnClickListener {
            val balance = currentCashBalance
            if (!PaymentReadinessPolicy.canOperate(isWalletLoaded, balance)) {
                Toast.makeText(requireContext(), PaymentReadinessPolicy.getReadinessWarning(), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val sheet = SandboxPaymentBottomSheet.newInstance("WITHDRAWAL", balance!!)
            sheet.show(parentFragmentManager, "WithdrawBottomSheet")
        }

        // Nút Làm mới lịch sử giao dịch Sandbox
        b.btnRefreshPayments.setOnClickListener {
            fetchPaymentHistory()
        }
    }

    private fun displaySystemInfo(b: FragmentWalletProfileBinding) {
        val versionName = runCatching {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName
        }.getOrNull() ?: "—"
        b.tvAppVersion.text = getString(R.string.app_version_label, versionName)
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
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.GONE
                        }
                        is WalletProfileUiState.Loading -> {
                            b.layoutLoading.visibility = View.VISIBLE
                            b.layoutError.visibility = View.GONE
                        }
                        is WalletProfileUiState.Success -> {
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.GONE
                            b.layoutContent.visibility = View.VISIBLE
                            renderSuccessData(b, state.data, state.isOffline, state.lastUpdatedFormatted)
                        }
                        is WalletProfileUiState.Empty -> {
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.GONE
                            b.layoutContent.visibility = View.VISIBLE
                            renderEmptyData(b)
                        }
                        is WalletProfileUiState.Unauthorized -> {
                            b.layoutLoading.visibility = View.GONE
                            handleUnauthorized()
                        }
                        is WalletProfileUiState.NetworkError -> {
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
        b: FragmentWalletProfileBinding,
        data: WalletProfileCombinedData,
        isOffline: Boolean,
        lastUpdatedFormatted: String
    ) {
        val currentActivity = activity ?: return
        val prefs = currentActivity.getSharedPreferences(NetworkConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val savedEmail = prefs.getString(NetworkConfig.KEY_SAVED_EMAIL, "") ?: ""

        // 1. Thông tin tài khoản
        val user = data.authResponse?.user
        val fullName = user?.fullName ?: savedEmail.ifEmpty { getString(R.string.no_data) }
        val email = if (NetworkConfig.isValidEmail(user?.email)) {
            user?.email!!
        } else if (NetworkConfig.isValidEmail(savedEmail)) {
            savedEmail
        } else {
            getString(R.string.no_data)
        }
        val userId = user?.id?.let { "#$it" } ?: getString(R.string.no_data)

        b.tvProfileFullname.text = fullName
        b.tvProfileEmail.text = email
        b.tvProfileUserId.text = userId

        // 2. Tổng tài sản & Ví đầu tư
        val portfolio = data.portfolio
        val wallet = data.authResponse?.wallet

        val netWorth = portfolio?.totalNetWorth ?: wallet?.balanceUsd
        val cashBalance = portfolio?.cashBalanceUsd ?: wallet?.balanceUsd
        val initialBalance = portfolio?.initialBalanceUsd ?: wallet?.initialBalance

        currentCashBalance = cashBalance
        isWalletLoaded = (cashBalance != null)
        val canOperate = PaymentReadinessPolicy.canOperate(isWalletLoaded, currentCashBalance)
        b.btnSandboxDeposit.isEnabled = canOperate
        b.btnSandboxWithdraw.isEnabled = canOperate

        if (netWorth != null) {
            b.tvNetWorth.text = String.format(Locale.US, "$%,.2f", netWorth)
        } else {
            b.tvNetWorth.text = getString(R.string.no_data)
        }

        if (cashBalance != null) {
            b.tvCashBalance.text = String.format(Locale.US, "$%,.2f", cashBalance)
        } else {
            b.tvCashBalance.text = getString(R.string.portfolio_unavailable)
        }

        if (initialBalance != null) {
            b.tvInitialBalance.text = String.format(Locale.US, "$%,.2f", initialBalance)
        } else {
            b.tvInitialBalance.text = getString(R.string.no_data)
        }

        val totalPnL = portfolio?.totalPnL ?: if (netWorth != null && initialBalance != null) (netWorth - initialBalance) else null
        val totalPnLPercent = portfolio?.totalPnLPercent ?: if (netWorth != null && initialBalance != null && initialBalance > 0) ((netWorth - initialBalance) / initialBalance * 100) else null

        if (totalPnL != null && totalPnLPercent != null) {
            val pnlColorRes = if (totalPnL >= 0) R.color.tv_green else R.color.tv_red
            val pnlSign = if (totalPnL >= 0) "+" else "-"
            val formattedPnL = String.format(
                Locale.US,
                "%s$%,.2f (%s%.2f%%)",
                pnlSign,
                abs(totalPnL),
                pnlSign,
                abs(totalPnLPercent)
            )
            b.tvTotalPnl.text = formattedPnL
            b.tvTotalPnl.setTextColor(ContextCompat.getColor(requireContext(), pnlColorRes))
        } else {
            b.tvTotalPnl.text = getString(R.string.no_data)
            b.tvTotalPnl.setTextColor(ContextCompat.getColor(requireContext(), R.color.tv_text_secondary))
        }

        // 3. Danh mục nắm giữ (Holdings)
        val holdings = portfolio?.holdings ?: emptyList()
        if (holdings.isEmpty()) {
            b.rvHoldings.visibility = View.GONE
            b.tvHoldingsEmpty.visibility = View.VISIBLE
        } else {
            b.rvHoldings.visibility = View.VISIBLE
            b.tvHoldingsEmpty.visibility = View.GONE
            holdingAdapter.submitList(holdings)
        }

        // 4. Lịch sử giao dịch (Trade History)
        val history = data.history
        if (history.isEmpty()) {
            b.rvTransactions.visibility = View.GONE
            b.tvTransactionsEmpty.visibility = View.VISIBLE
        } else {
            b.rvTransactions.visibility = View.VISIBLE
            b.tvTransactionsEmpty.visibility = View.GONE
            tradeHistoryAdapter.submitList(history)
        }

        // 5. Trạng thái ngoại tuyến (Offline Badge) & Thời gian cập nhật
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

    private fun renderEmptyData(b: FragmentWalletProfileBinding) {
        currentCashBalance = null
        isWalletLoaded = false
        b.btnSandboxDeposit.isEnabled = false
        b.btnSandboxWithdraw.isEnabled = false
        b.tvProfileFullname.text = getString(R.string.no_data)
        b.tvProfileEmail.text = getString(R.string.no_data)
        b.tvProfileUserId.text = getString(R.string.no_data)
        b.tvNetWorth.text = getString(R.string.no_data)
        b.tvCashBalance.text = getString(R.string.no_data)
        b.tvInitialBalance.text = getString(R.string.no_data)
        b.tvTotalPnl.text = getString(R.string.no_data)
        b.tvTotalPnl.setTextColor(ContextCompat.getColor(requireContext(), R.color.tv_text_secondary))
        b.rvHoldings.visibility = View.GONE
        b.tvHoldingsEmpty.visibility = View.VISIBLE
        b.rvTransactions.visibility = View.GONE
        b.tvTransactionsEmpty.visibility = View.VISIBLE
        b.tvOfflineBadge.visibility = View.GONE
    }

    private fun fetchPaymentHistory() {
        val b = binding ?: return
        val currentActivity = activity ?: return
        val authHeader = AuthSessionManager.getAuthHeader(currentActivity) ?: return

        historyCall?.cancel()
        b.pbPaymentsLoading.visibility = View.VISIBLE
        val call = RetrofitClient.apiService.getPaymentHistory(authHeader)
        historyCall = call
        call.enqueue(object : Callback<List<PaymentOrderDto>> {
            override fun onResponse(call: Call<List<PaymentOrderDto>>, response: Response<List<PaymentOrderDto>>) {
                if (historyCall !== call) return
                if (!isAdded) return
                b.pbPaymentsLoading.visibility = View.GONE
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
                b.pbPaymentsLoading.visibility = View.GONE
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
                // Ignore network error on background polling
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
                            Toast.makeText(requireContext(), "Đã hủy yêu cầu thanh toán #${orderId}", Toast.LENGTH_SHORT).show()
                            if (activePendingOrderId == orderId) {
                                savePendingOrderId(null)
                            }
                            fetchPaymentHistory()
                        } else {
                            Toast.makeText(requireContext(), "Hủy thất bại: HTTP ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: Call<PaymentOrderDto>, t: Throwable) {
                        if (cancelCall !== call) return
                        if (!isAdded) return
                        Toast.makeText(requireContext(), "Lỗi mạng khi hủy lệnh", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Không thể mở trình duyệt: ${e2.message}", Toast.LENGTH_SHORT).show()
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
