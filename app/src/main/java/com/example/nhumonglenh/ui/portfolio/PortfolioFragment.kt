package com.example.nhumonglenh.ui.portfolio

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.repository.WalletProfileCombinedData
import com.example.nhumonglenh.data.repository.WalletProfileRepository
import com.example.nhumonglenh.databinding.FragmentPortfolioBinding
import com.example.nhumonglenh.ui.profile.HoldingAdapter
import com.example.nhumonglenh.ui.profile.TradeHistoryAdapter
import com.example.nhumonglenh.ui.profile.WalletProfileUiState
import com.example.nhumonglenh.ui.profile.WalletProfileViewModel
import com.example.nhumonglenh.ui.profile.WalletProfileViewModelFactory
import com.example.nhumonglenh.ui.trading.PortfolioValuationPolicy
import kotlinx.coroutines.launch
import java.util.Locale

class PortfolioFragment : Fragment() {

    private var _binding: FragmentPortfolioBinding? = null
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
        _binding = FragmentPortfolioBinding.inflate(inflater, container, false)
        return _binding?.root ?: inflater.inflate(R.layout.fragment_portfolio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        setupRecyclerViews(b)
        setupListeners(b)
        observeUiState()
        refreshIfStale()
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

    private fun setupRecyclerViews(b: FragmentPortfolioBinding) {
        holdingAdapter = HoldingAdapter()
        b.rvHoldings.layoutManager = LinearLayoutManager(requireContext())
        b.rvHoldings.adapter = holdingAdapter

        tradeHistoryAdapter = TradeHistoryAdapter()
        b.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        b.rvTransactions.adapter = tradeHistoryAdapter
    }

    private fun setupListeners(b: FragmentPortfolioBinding) {
        b.btnPortfolioRefresh.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
        }

        b.btnRetry.setOnClickListener {
            triggerDataLoad(forceRefresh = true)
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
        b: FragmentPortfolioBinding,
        data: WalletProfileCombinedData,
        isOffline: Boolean,
        lastUpdatedFormatted: String
    ) {
        val portfolio = data.portfolio

        // Total Net Worth, Holdings Value, Cash Balance, and PnL governed by PortfolioValuationPolicy
        b.tvNetWorth.text = PortfolioValuationPolicy.formatTotalNetWorth(portfolio)
        b.tvHoldingsValue.text = PortfolioValuationPolicy.formatTotalHoldingsValue(portfolio)

        val cashBalance = portfolio?.cashBalanceUsd ?: data.authResponse?.wallet?.balanceUsd
        b.tvCashBalance.text = if (cashBalance != null) {
            String.format(Locale.US, "$%,.2f", cashBalance)
        } else {
            PortfolioValuationPolicy.UNVALUED_PLACEHOLDER
        }

        val pnlText = PortfolioValuationPolicy.formatTotalPnl(portfolio)
        b.tvTotalPnl.text = pnlText
        if (pnlText != PortfolioValuationPolicy.UNVALUED_PLACEHOLDER) {
            val pnlValue = portfolio?.totalPnL ?: 0.0
            val pnlColorRes = if (pnlValue >= 0) R.color.tv_green else R.color.tv_red
            b.tvTotalPnl.setTextColor(ContextCompat.getColor(requireContext(), pnlColorRes))
        } else {
            b.tvTotalPnl.setTextColor(ContextCompat.getColor(requireContext(), R.color.tv_text_secondary))
        }

        // Holdings
        val holdings = portfolio?.holdings ?: emptyList()
        if (holdings.isEmpty()) {
            b.rvHoldings.visibility = View.GONE
            b.tvHoldingsEmpty.visibility = View.VISIBLE
        } else {
            b.rvHoldings.visibility = View.VISIBLE
            b.tvHoldingsEmpty.visibility = View.GONE
            holdingAdapter.submitList(holdings)
        }

        // Trade History
        val history = data.history
        if (history.isEmpty()) {
            b.rvTransactions.visibility = View.GONE
            b.tvTransactionsEmpty.visibility = View.VISIBLE
        } else {
            b.rvTransactions.visibility = View.VISIBLE
            b.tvTransactionsEmpty.visibility = View.GONE
            tradeHistoryAdapter.submitList(history)
        }

        // Offline badge
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

    private fun renderEmptyData(b: FragmentPortfolioBinding) {
        b.tvNetWorth.text = PortfolioValuationPolicy.UNVALUED_PLACEHOLDER
        b.tvHoldingsValue.text = PortfolioValuationPolicy.UNVALUED_PLACEHOLDER
        b.tvCashBalance.text = PortfolioValuationPolicy.UNVALUED_PLACEHOLDER
        b.tvTotalPnl.text = PortfolioValuationPolicy.UNVALUED_PLACEHOLDER
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
        AuthSessionManager.handleUnauthorized(currentActivity)
    }
}
