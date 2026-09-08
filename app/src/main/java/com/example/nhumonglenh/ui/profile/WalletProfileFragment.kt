package com.example.nhumonglenh.ui.profile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.repository.WalletProfileCombinedData
import com.example.nhumonglenh.data.repository.WalletProfileRepository
import com.example.nhumonglenh.databinding.FragmentWalletProfileBinding
import kotlinx.coroutines.launch
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
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshIfStale()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            refreshIfStale()
        }
    }

    private fun refreshIfStale() {
        triggerDataLoad(forceRefresh = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerViews(b: FragmentWalletProfileBinding) {
        holdingAdapter = HoldingAdapter()
        b.rvHoldings.layoutManager = LinearLayoutManager(requireContext())
        b.rvHoldings.adapter = holdingAdapter

        tradeHistoryAdapter = TradeHistoryAdapter()
        b.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        b.rvTransactions.adapter = tradeHistoryAdapter
    }

    private fun setupListeners(b: FragmentWalletProfileBinding) {
        // Nút làm mới trên Header
        b.btnProfileRefresh.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
        }

        // Nút thử lại khi gặp màn hình lỗi
        b.btnRetry.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
        }

        // Nút Đăng xuất ở cuối màn hình
        b.btnProfileLogout.setOnClickListener {
            (activity as? Activity2)?.showLogoutConfirmationDialog()
        }
    }

    private fun displaySystemInfo(b: FragmentWalletProfileBinding) {
        val prefs = requireActivity().getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        val savedServerUrl = prefs.getString("server_url", RetrofitClient.BASE_URL) ?: RetrofitClient.BASE_URL
        b.tvServerUrl.text = getString(R.string.server_url_label, savedServerUrl)
    }

    private fun triggerDataLoad(forceRefresh: Boolean) {
        if (isNavigatingToAuth) return
        val currentActivity = activity ?: return
        val token = com.example.nhumonglenh.data.local.AuthSessionManager.getToken(currentActivity)

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
                        }
                        is WalletProfileUiState.ServerError -> {
                            b.layoutLoading.visibility = View.GONE
                            b.layoutError.visibility = View.VISIBLE
                            b.tvErrorMessage.text = getString(R.string.server_error_msg)
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

        // 4. Lịch sử giao dịch (Transactions)
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

    private fun handleUnauthorized() {
        if (isNavigatingToAuth) return
        isNavigatingToAuth = true

        val currentActivity = activity ?: return
        com.example.nhumonglenh.data.local.AuthSessionManager.handleUnauthorized(currentActivity)
    }
}