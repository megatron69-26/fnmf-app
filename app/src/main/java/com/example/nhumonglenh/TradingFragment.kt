package com.example.nhumonglenh

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.CandleDto
import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.remote.StockCatalogDto
import com.example.nhumonglenh.data.remote.StockDetailDto
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.data.remote.WatchlistRequest
import com.example.nhumonglenh.databinding.FragmentTradingBinding
import com.example.nhumonglenh.ui.trading.AuthHeaderFactory
import com.example.nhumonglenh.ui.trading.AuthHttpPolicy
import com.example.nhumonglenh.ui.trading.CandleFallbackPolicy
import com.example.nhumonglenh.ui.trading.CandleReloadPolicy
import com.example.nhumonglenh.ui.trading.ChartLabelFormatter
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.OrderTicketBottomSheet
import com.example.nhumonglenh.ui.trading.PortfolioSyncPolicy
import com.example.nhumonglenh.ui.trading.StockCatalogAdapter
import com.example.nhumonglenh.ui.trading.StockReportPolicy
import com.example.nhumonglenh.ui.trading.StockTradePolicy
import com.example.nhumonglenh.ui.trading.StockWatchlistMatcher
import com.example.nhumonglenh.ui.trading.TradingDataReadiness
import com.example.nhumonglenh.ui.trading.TradingStateRestoration
import com.example.nhumonglenh.ui.trading.WatchlistMutation
import com.example.nhumonglenh.ui.trading.WatchlistStateReducer
import com.example.nhumonglenh.ui.watchlist.WatchlistAdapter
import com.example.nhumonglenh.ui.watchlist.WatchlistUiModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import com.google.android.material.tabs.TabLayout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * =====================================================================
 * TRADING FRAGMENT - GIAO DIỆN GIAO DỊCH CHUẨN CHART-FIRST & CỔ PHIẾU HOA KỲ
 * =====================================================================
 * - Instrument Header: Symbol, full name, live price, 24h change %, live/offline badge.
 * - Mode Tabs: "Giao dịch" (Chart, Mua/Bán, Watchlist nhúng) và "Cổ phiếu" (8 mã cổ phiếu Mỹ).
 * - Compact Account Strip: Tiền mặt khả dụng & Số lượng đang giữ của mã hiện tại.
 * - Sticky Actions: Hai nút MUA và BÁN. Tự động vô hiệu hóa và cảnh báo an toàn khi stale=true hoặc thiếu giá.
 * - Nhúng Watchlist trực tiếp dưới nút Mua/Bán; hiển thị khuyến nghị và link báo cáo mới nhất.
 * - Không hiển thị tiêu đề báo cáo tiếng Anh (StockReportPolicy).
 * - Quản lý độc lập nến và WebSocket giữa Crypto/Vàng và Cổ phiếu.
 * =====================================================================
 */
class TradingFragment : Fragment() {

    private var _binding: FragmentTradingBinding? = null
    private val binding get() = _binding

    private var jwtToken: String = ""

    // Dữ liệu nến trong bộ nhớ
    private val candleEntries = ArrayList<CandleEntry>()
    private var candleDataSet: CandleDataSet? = null
    private var loadedCandleSymbol: String? = null

    // Quản lý mã tài sản đang chọn
    private var currentSymbol: String = "BTCUSDT"
    private var currentAssetPrice: Double? = null
    private var previousAssetPrice: Double? = null
    private var baselinePeriodPrice: Double? = null
    private var userCashBalance: Double? = null
    private var portfolioLoaded: Boolean = false
    private var lastPortfolioFetchTime: Long = 0L
    private var userHoldingsQuantity: Double = 0.0
    private var userHoldingsAvgBuyPrice: Double = 0.0
    private var portfolioHoldingsList: List<HoldingDto> = emptyList()

    // Trạng thái cổ phiếu
    private var isStockDetailStale: Boolean = false

    // Adapters
    private lateinit var stockCatalogAdapter: StockCatalogAdapter
    private lateinit var embeddedWatchlistAdapter: WatchlistAdapter
    private val cachedWatchlistSymbols = mutableSetOf<String>()

    // Calls đang chạy
    private var activeCandleCall: Call<List<CandleDto>>? = null
    private var activePortfolioCall: Call<PortfolioSummaryDto>? = null
    private var activeStockDetailCall: Call<StockDetailDto>? = null
    private var activeStockCatalogCall: Call<List<StockCatalogDto>>? = null
    private var activeEmbeddedWatchlistCall: Call<List<WatchlistItemDto>>? = null

    // WebSocket Client
    private var binanceWebSocket: WebSocket? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTradingBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        jwtToken = getSavedToken()

        // 1. Cấu hình lắng nghe kết quả đặt lệnh qua FragmentResult API
        setFragmentResultListener(OrderTicketBottomSheet.REQUEST_KEY_ORDER) { _, bundle ->
            if (bundle.getBoolean(OrderTicketBottomSheet.KEY_ORDER_SUCCESS, false)) {
                val orderType = bundle.getString(OrderTicketBottomSheet.KEY_ORDER_TYPE) ?: "BUY"
                val sym = bundle.getString(OrderTicketBottomSheet.KEY_SYMBOL) ?: currentSymbol
                val qty = bundle.getDouble(OrderTicketBottomSheet.KEY_ORDER_QUANTITY, 0.0)
                val customMsg = bundle.getString(OrderTicketBottomSheet.KEY_SUCCESS_MESSAGE)
                val qtyStr = String.format(Locale.US, "%.4f", qty)
                val msg = customMsg ?: getString(R.string.order_success_format, orderType, qtyStr, sym)
                context?.let { ctx ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                }
                loadPortfolioSilently()
            }
        }

        // 2. Cấu hình Tabs "Giao dịch" vs "Cổ phiếu"
        setupTabs()

        // 3. Cấu hình Adapter danh sách Cổ phiếu Mỹ
        setupStockCatalog()

        // 4. Cấu hình Adapter Watchlist nhúng
        setupEmbeddedWatchlist()

        // 5. Cấu hình nút Theo dõi trên Header
        setupWatchlistToggleAction()

        // 6. Cấu hình giao diện Biểu đồ Nến Dark Theme
        setupCandleChartStyle()

        // 7. Cấu hình các nút đặt lệnh MUA / BÁN
        setupTradeActions()

        // 8. Khởi động với symbol đã lưu hoặc mặc định BTCUSDT (không dùng giá giả)
        val initialSymbol = TradingStateRestoration.resolveInitialSymbol(savedInstanceState?.getString(KEY_SAVED_SYMBOL))
        switchMarketSymbol(initialSymbol, isInitial = true)

        // 9. Tải danh mục đầu tư thật lần đầu
        loadPortfolio()

        // 10. Chạm thẻ tài sản để làm mới số dư chủ động
        binding?.cardAccountStrip?.setOnClickListener {
            context?.let { c ->
                Toast.makeText(c, getString(R.string.trading_toast_syncing_balance), Toast.LENGTH_SHORT).show()
            }
            loadPortfolioSilently()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SAVED_SYMBOL, currentSymbol)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            disconnectWebSocket()
            activeCandleCall?.cancel()
            activeCandleCall = null
            activePortfolioCall?.cancel()
            activePortfolioCall = null
            activeStockDetailCall?.cancel()
            activeStockDetailCall = null
        } else {
            val shouldReloadCandles = CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = candleEntries.isNotEmpty(),
                loadedCandleSymbol = loadedCandleSymbol,
                currentSymbol = currentSymbol
            )
            if (shouldReloadCandles) {
                loadCandleData(currentSymbol)
            }
            if (PortfolioSyncPolicy.isStale(lastPortfolioFetchTime)) {
                loadPortfolioSilently()
            }
            if (!StockTradePolicy.isStock(currentSymbol) && binanceWebSocket == null) {
                connectWebSocketForSymbol(currentSymbol)
            } else if (StockTradePolicy.isStock(currentSymbol)) {
                fetchStockDetail(currentSymbol)
            }
            loadEmbeddedWatchlist()
        }
    }

    override fun onDestroyView() {
        disconnectWebSocket()
        activeCandleCall?.cancel()
        activeCandleCall = null
        activePortfolioCall?.cancel()
        activePortfolioCall = null
        activeStockDetailCall?.cancel()
        activeStockDetailCall = null
        activeStockCatalogCall?.cancel()
        activeStockCatalogCall = null
        activeEmbeddedWatchlistCall?.cancel()
        activeEmbeddedWatchlistCall = null
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectWebSocket()
    }

    private fun disconnectWebSocket() {
        binanceWebSocket?.close(1000, "Disconnecting")
        binanceWebSocket = null
    }

    private fun setupTabs() {
        val b = binding ?: return
        b.tabLayoutTradingMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        b.layoutTradingContainer.visibility = View.VISIBLE
                        b.layoutStockCatalogContainer.visibility = View.GONE
                        loadEmbeddedWatchlist()
                    }
                    1 -> {
                        b.layoutTradingContainer.visibility = View.GONE
                        b.layoutStockCatalogContainer.visibility = View.VISIBLE
                        loadStockCatalog()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    loadStockCatalog()
                }
            }
        })
    }

    private fun setupStockCatalog() {
        val b = binding ?: return
        stockCatalogAdapter = StockCatalogAdapter(
            items = emptyList(),
            onItemClick = { stock ->
                b.tabLayoutTradingMode.getTabAt(0)?.select()
                switchMarketSymbol(stock.symbol)
            },
            onWatchlistToggle = { stock ->
                toggleWatchlistForSymbol(stock.symbol)
            }
        )
        b.rvStockCatalog.layoutManager = LinearLayoutManager(requireContext())
        b.rvStockCatalog.adapter = stockCatalogAdapter
    }

    private fun loadStockCatalog() {
        val b = binding ?: return
        b.pbStockCatalogLoading.visibility = View.VISIBLE

        activeStockCatalogCall?.cancel()
        val call = RetrofitClient.apiService.getStocks()
        activeStockCatalogCall = call

        call.enqueue(object : Callback<List<StockCatalogDto>> {
            override fun onResponse(call: Call<List<StockCatalogDto>>, response: Response<List<StockCatalogDto>>) {
                if (activeStockCatalogCall !== call) return
                if (!isAdded || _binding == null) return
                b.pbStockCatalogLoading.visibility = View.GONE

                if (response.code() == 401 || response.code() == 403) {
                    AuthSessionManager.handleUnauthorized(activity)
                    return
                }

                if (!response.isSuccessful) {
                    Log.w(TAG, "Lỗi khi tải danh mục cổ phiếu: code ${response.code()}")
                    context?.let { ctx ->
                        Toast.makeText(ctx, getString(R.string.stock_catalog_load_failed), Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                val stocks = response.body() ?: emptyList()

                val authHeader = AuthHeaderFactory.createBearerHeader(jwtToken)
                if (authHeader != null) {
                    RetrofitClient.apiService.getWatchlist(authHeader).enqueue(object : Callback<List<WatchlistItemDto>> {
                        override fun onResponse(wCall: Call<List<WatchlistItemDto>>, wResp: Response<List<WatchlistItemDto>>) {
                            if (!isAdded || _binding == null) return
                            if (wResp.code() == 401 || wResp.code() == 403) {
                                AuthSessionManager.handleUnauthorized(activity)
                                return
                            }
                            if (wResp.isSuccessful) {
                                val watchlist = wResp.body() ?: emptyList()
                                cachedWatchlistSymbols.clear()
                                watchlist.mapNotNull { it.symbol.trim().uppercase(Locale.ROOT) }.forEach {
                                    cachedWatchlistSymbols.add(it)
                                }
                                val uiModels = StockWatchlistMatcher.matchCatalogWithWatchlist(stocks, watchlist)
                                stockCatalogAdapter.submitList(uiModels)
                                updateWatchlistToggleButton()
                            } else {
                                Log.w(TAG, "Không thể tải watchlist khi load catalog: code ${wResp.code()}")
                                val currentWatchlistItems = cachedWatchlistSymbols.map { WatchlistItemDto(symbol = it) }
                                val uiModels = StockWatchlistMatcher.matchCatalogWithWatchlist(stocks, currentWatchlistItems)
                                stockCatalogAdapter.submitList(uiModels)
                            }
                        }

                        override fun onFailure(wCall: Call<List<WatchlistItemDto>>, t: Throwable) {
                            if (!isAdded || _binding == null) return
                            Log.e(TAG, "Lỗi kết nối khi tải watchlist cho catalog: ${t.message}")
                            val currentWatchlistItems = cachedWatchlistSymbols.map { WatchlistItemDto(symbol = it) }
                            val uiModels = StockWatchlistMatcher.matchCatalogWithWatchlist(stocks, currentWatchlistItems)
                            stockCatalogAdapter.submitList(uiModels)
                        }
                    })
                } else {
                    val uiModels = StockWatchlistMatcher.matchCatalogWithWatchlist(stocks, emptyList())
                    stockCatalogAdapter.submitList(uiModels)
                }
            }

            override fun onFailure(call: Call<List<StockCatalogDto>>, t: Throwable) {
                if (activeStockCatalogCall !== call) return
                if (!isAdded || _binding == null) return
                b.pbStockCatalogLoading.visibility = View.GONE
                Log.e(TAG, "Lỗi mạng khi tải danh mục cổ phiếu: ${t.message}")
                context?.let { ctx ->
                    Toast.makeText(ctx, getString(R.string.network_error_msg), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun setupEmbeddedWatchlist() {
        val b = binding ?: return
        embeddedWatchlistAdapter = WatchlistAdapter(
            items = emptyList(),
            onClick = { item ->
                switchMarketSymbol(item.symbol)
            },
            onReportClick = { reportUrl ->
                openReportUrl(reportUrl)
            }
        )
        b.rvEmbeddedWatchlist.layoutManager = LinearLayoutManager(requireContext())
        b.rvEmbeddedWatchlist.adapter = embeddedWatchlistAdapter
    }

    private fun loadEmbeddedWatchlist() {
        val b = binding ?: return
        val authHeader = AuthHeaderFactory.createBearerHeader(jwtToken) ?: return

        activeEmbeddedWatchlistCall?.cancel()
        val call = RetrofitClient.apiService.getWatchlist(authHeader)
        activeEmbeddedWatchlistCall = call

        call.enqueue(object : Callback<List<WatchlistItemDto>> {
            override fun onResponse(call: Call<List<WatchlistItemDto>>, response: Response<List<WatchlistItemDto>>) {
                if (activeEmbeddedWatchlistCall !== call) return
                if (!isAdded || _binding == null) return

                if (AuthHttpPolicy.isUnauthorized(response.code())) {
                    AuthSessionManager.handleUnauthorized(activity)
                    return
                }

                if (response.isSuccessful) {
                    val items = response.body()
                    if (!items.isNullOrEmpty()) {
                        cachedWatchlistSymbols.clear()
                        items.mapNotNull { it.symbol.trim().uppercase(Locale.ROOT) }.forEach {
                            cachedWatchlistSymbols.add(it)
                        }
                        updateWatchlistToggleButton()

                        val uiModels = items.map { dto ->
                            val sym = dto.symbol.trim().uppercase(Locale.ROOT)
                            val isStockItem = StockTradePolicy.isStock(sym)
                            WatchlistUiModel(
                                symbol = sym,
                                fullName = dto.name ?: getFriendlyName(sym),
                                price = dto.currentPrice,
                                changePercent = dto.change24h,
                                priceAsOf = dto.priceAsOf,
                                recommendation = dto.recommendation,
                                latestReportTitle = dto.latestReportTitle,
                                latestReportUrl = dto.latestReportUrl,
                                isStock = isStockItem
                            )
                        }
                        b.rvEmbeddedWatchlist.visibility = View.VISIBLE
                        b.tvEmbeddedWatchlistEmpty.visibility = View.GONE
                        embeddedWatchlistAdapter.updateData(uiModels)
                    } else {
                        cachedWatchlistSymbols.clear()
                        updateWatchlistToggleButton()
                        b.rvEmbeddedWatchlist.visibility = View.GONE
                        b.tvEmbeddedWatchlistEmpty.visibility = View.VISIBLE
                    }
                } else {
                    Log.w(TAG, "Không thể tải danh sách theo dõi: code ${response.code()}")
                    context?.let { ctx ->
                        Toast.makeText(ctx, getString(R.string.watchlist_load_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<List<WatchlistItemDto>>, t: Throwable) {
                if (activeEmbeddedWatchlistCall !== call) return
                if (!isAdded || _binding == null) return
                Log.e(TAG, "Lỗi mạng khi tải embedded watchlist: ${t.message}")
                context?.let { ctx ->
                    Toast.makeText(ctx, getString(R.string.network_error_msg), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun setupWatchlistToggleAction() {
        binding?.btnTradingWatchlistToggle?.setOnClickListener {
            toggleWatchlistForSymbol(currentSymbol)
        }
    }

    private fun updateWatchlistToggleButton() {
        val b = binding ?: return
        val cleanSym = currentSymbol.trim().uppercase(Locale.ROOT)
        val isWatchlisted = cachedWatchlistSymbols.contains(cleanSym)
        if (isWatchlisted) {
            b.btnTradingWatchlistToggle.text = getString(R.string.btn_watchlist_remove)
            b.btnTradingWatchlistToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.tv_surface))
            b.btnTradingWatchlistToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.tv_text_secondary))
        } else {
            b.btnTradingWatchlistToggle.text = getString(R.string.btn_watchlist_toggle_add)
            b.btnTradingWatchlistToggle.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.tv_green))
            b.btnTradingWatchlistToggle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        }
    }

    private fun toggleWatchlistForSymbol(symbol: String) {
        val cleanSym = symbol.trim().uppercase(Locale.ROOT)
        val authHeader = AuthHeaderFactory.createBearerHeader(jwtToken) ?: return
        val isCurrentlyWatchlisted = cachedWatchlistSymbols.contains(cleanSym)

        if (isCurrentlyWatchlisted) {
            RetrofitClient.apiService.removeFromWatchlist(authHeader, cleanSym).enqueue(object : Callback<Map<String, String>> {
                override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                    if (AuthHttpPolicy.isUnauthorized(response.code())) {
                        AuthSessionManager.handleUnauthorized(activity)
                        return
                    }
                    val updated = WatchlistStateReducer.reduce(
                        cachedWatchlistSymbols,
                        WatchlistMutation.Remove(cleanSym),
                        isSuccess = response.isSuccessful
                    )
                    cachedWatchlistSymbols.clear()
                    cachedWatchlistSymbols.addAll(updated)
                    updateWatchlistToggleButton()

                    if (response.isSuccessful) {
                        loadEmbeddedWatchlist()
                        loadStockCatalog()
                        context?.let {
                            Toast.makeText(it, getString(R.string.watchlist_remove_success, cleanSym), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "Xóa khỏi watchlist thất bại: code ${response.code()}")
                        context?.let {
                            Toast.makeText(it, getString(R.string.watchlist_update_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                    Log.e(TAG, "Lỗi mạng khi xóa khỏi watchlist: ${t.message}")
                    val updated = WatchlistStateReducer.reduce(
                        cachedWatchlistSymbols,
                        WatchlistMutation.Remove(cleanSym),
                        isSuccess = false
                    )
                    cachedWatchlistSymbols.clear()
                    cachedWatchlistSymbols.addAll(updated)
                    updateWatchlistToggleButton()
                    context?.let {
                        Toast.makeText(it, getString(R.string.network_error_msg), Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } else {
            RetrofitClient.apiService.addToWatchlist(authHeader, WatchlistRequest(cleanSym)).enqueue(object : Callback<WatchlistItemDto> {
                override fun onResponse(call: Call<WatchlistItemDto>, response: Response<WatchlistItemDto>) {
                    if (AuthHttpPolicy.isUnauthorized(response.code())) {
                        AuthSessionManager.handleUnauthorized(activity)
                        return
                    }
                    val updated = WatchlistStateReducer.reduce(
                        cachedWatchlistSymbols,
                        WatchlistMutation.Add(cleanSym),
                        isSuccess = response.isSuccessful
                    )
                    cachedWatchlistSymbols.clear()
                    cachedWatchlistSymbols.addAll(updated)
                    updateWatchlistToggleButton()

                    if (response.isSuccessful) {
                        loadEmbeddedWatchlist()
                        loadStockCatalog()
                        context?.let {
                            Toast.makeText(it, getString(R.string.watchlist_add_success, cleanSym), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "Thêm vào watchlist thất bại: code ${response.code()}")
                        context?.let {
                            Toast.makeText(it, getString(R.string.watchlist_update_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call<WatchlistItemDto>, t: Throwable) {
                    Log.e(TAG, "Lỗi mạng khi thêm vào watchlist: ${t.message}")
                    val updated = WatchlistStateReducer.reduce(
                        cachedWatchlistSymbols,
                        WatchlistMutation.Add(cleanSym),
                        isSuccess = false
                    )
                    cachedWatchlistSymbols.clear()
                    cachedWatchlistSymbols.addAll(updated)
                    updateWatchlistToggleButton()
                    context?.let {
                        Toast.makeText(it, getString(R.string.network_error_msg), Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun openReportUrl(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e2: Exception) {
                Toast.makeText(requireContext(), getString(R.string.report_open_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Cấu hình thẩm mỹ chuẩn Dark Theme cho MPAndroidChart sử dụng màu R.color
     */
    private fun setupCandleChartStyle() {
        val chart = binding?.candleChart ?: return
        val ctx = context ?: return
        chart.apply {
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.tv_bg))
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(ctx, R.color.white)
            setDrawGridBackground(false)
            isDoubleTapToZoomEnabled = true
            setPinchZoom(true)

            // Trục X (Thời gian)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = ContextCompat.getColor(ctx, R.color.tv_text_secondary)
                setDrawGridLines(false)
                setAvoidFirstLastClipping(true)
            }

            // Trục Y bên Trái (Giá tiền)
            axisLeft.apply {
                textColor = ContextCompat.getColor(ctx, R.color.tv_text_secondary)
                gridColor = ContextCompat.getColor(ctx, R.color.tv_border)
                setDrawAxisLine(false)
                resetAxisMinimum()
                resetAxisMaximum()
            }

            // Tắt trục Y bên Phải
            axisRight.isEnabled = false
        }
    }

    /**
     * Chuyển đổi mã tài sản hiển thị biểu đồ Nến (BTCUSDT, ETHUSDT, AAPL, MSFT...)
     */
    fun switchMarketSymbol(symbol: String, isInitial: Boolean = false) {
        val sym = symbol.uppercase(Locale.ROOT)
        currentSymbol = sym
        (activity as? Activity2)?.updateActiveSymbol(sym)

        val b = binding ?: return
        val ctx = context ?: return

        val isStock = StockTradePolicy.isStock(sym)

        // 1. Cập nhật Tiêu đề Header
        b.tvHeaderSymbol.text = formatSymbolDisplay(sym)
        b.tvHeaderFullName.text = if (MarketStreamHelper.isGoldReferenceStream(sym)) {
            getString(R.string.trading_gold_paxg_reference)
        } else {
            getFriendlyName(sym)
        }

        // 2. Xóa giá hiển thị cũ, đưa về trạng thái chờ
        currentAssetPrice = null
        previousAssetPrice = null
        baselinePeriodPrice = null
        isStockDetailStale = false

        b.tvCurrentPrice.text = "—"
        b.tvCurrentPrice.setTextColor(ContextCompat.getColor(ctx, R.color.tv_text_primary))
        b.tvPriceChange.text = "—"
        b.tvPriceChange.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.tv_surface))

        candleEntries.clear()
        b.candleChart.clear()

        if (!isInitial) {
            Toast.makeText(ctx, getString(R.string.trading_toast_switch_symbol, sym), Toast.LENGTH_SHORT).show()
        }

        updateWatchlistToggleButton()
        updateTradeActionsState()

        // 3. Tải nến ngày
        loadCandleData(sym)

        // 4. Luồng xử lý Cổ phiếu vs Crypto/Gold
        if (isStock) {
            disconnectWebSocket()
            b.tvLiveStatus.text = getString(R.string.trading_no_live_badge)
            b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.tv_text_secondary))
            fetchStockDetail(sym)
        } else {
            b.llStockMetaRow.visibility = View.GONE
            b.tvTradeWarningMessage.visibility = View.GONE

            val streamName = MarketStreamHelper.resolveWebSocketStream(sym)
            if (streamName == null) {
                b.tvLiveStatus.text = getString(R.string.trading_no_live_badge)
                b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.tv_text_secondary))
            } else {
                b.tvLiveStatus.text = getString(R.string.trading_connecting_badge)
                b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.tv_yellow))
            }
            connectWebSocketForSymbol(sym)
        }

        // 5. Cập nhật số dư & lượng tài sản sở hữu
        updateHoldingsForCurrentSymbol()
        updatePortfolioDisplay()
        loadEmbeddedWatchlist()
    }

    private fun fetchStockDetail(symbol: String) {
        activeStockDetailCall?.cancel()
        val call = RetrofitClient.apiService.getStockDetail(symbol)
        activeStockDetailCall = call

        val b = binding ?: return
        val ctx = context ?: return

        call.enqueue(object : Callback<StockDetailDto> {
            override fun onResponse(call: Call<StockDetailDto>, response: Response<StockDetailDto>) {
                if (activeStockDetailCall !== call) return
                if (!isAdded || _binding == null) return

                if (response.code() == 401 || response.code() == 403) {
                    AuthSessionManager.handleUnauthorized(activity)
                    return
                }

                val detail = response.body()
                if (response.isSuccessful && detail != null) {
                    val price = detail.currentPrice
                    isStockDetailStale = detail.stale == true

                    if (price != null && price > 0.0) {
                        currentAssetPrice = price
                        b.tvCurrentPrice.text = String.format(Locale.US, "$%,.2f", price)
                        b.tvCurrentPrice.setTextColor(ContextCompat.getColor(ctx, R.color.tv_text_primary))
                    } else {
                        currentAssetPrice = null
                        b.tvCurrentPrice.text = "—"
                    }

                    if (detail.change24h != null) {
                        val sign = if (detail.change24h >= 0) "+" else ""
                        b.tvPriceChange.text = String.format(Locale.US, "%s%.2f%%", sign, detail.change24h)
                        val colorRes = if (detail.change24h >= 0) R.color.tv_green else R.color.tv_red
                        b.tvPriceChange.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes))
                    } else {
                        b.tvPriceChange.text = "—"
                        b.tvPriceChange.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.tv_surface))
                    }

                    // Stock metadata: recommendation & latest report
                    if (!detail.recommendation.isNullOrBlank()) {
                        b.tvStockRecommendation.text = detail.recommendation
                        b.tvStockRecommendation.visibility = View.VISIBLE
                    } else {
                        b.tvStockRecommendation.visibility = View.GONE
                    }

                    if (StockReportPolicy.shouldShowReportButton(detail.latestReportUrl)) {
                        b.btnStockReport.text = StockReportPolicy.resolveReportButtonLabel(detail.latestReportTitle)
                        b.btnStockReport.visibility = View.VISIBLE
                        b.btnStockReport.setOnClickListener {
                            openReportUrl(detail.latestReportUrl)
                        }
                    } else {
                        b.btnStockReport.visibility = View.GONE
                    }

                    b.llStockMetaRow.visibility = if (b.tvStockRecommendation.visibility == View.VISIBLE || b.btnStockReport.visibility == View.VISIBLE) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }

                    val tradeEnabled = StockTradePolicy.isTradeEnabled(
                        isStock = true,
                        stale = detail.stale,
                        currentPrice = detail.currentPrice
                    )
                    val statusMsg = StockTradePolicy.resolveTradeStatusMessage(
                        isStock = true,
                        stale = detail.stale,
                        currentPrice = detail.currentPrice
                    )

                    if (!tradeEnabled) {
                        b.tvTradeWarningMessage.text = statusMsg ?: getString(R.string.stock_trade_disabled_warning)
                        b.tvTradeWarningMessage.visibility = View.VISIBLE
                        b.btnBuy.isEnabled = false
                        b.btnBuy.alpha = 0.5f
                        b.btnSell.isEnabled = false
                        b.btnSell.alpha = 0.5f
                    } else {
                        b.tvTradeWarningMessage.visibility = View.GONE
                        updateTradeActionsState()
                    }
                } else {
                    isStockDetailStale = true
                    b.tvTradeWarningMessage.text = getString(R.string.stock_trade_disabled_warning)
                    b.tvTradeWarningMessage.visibility = View.VISIBLE
                    b.btnBuy.isEnabled = false
                    b.btnBuy.alpha = 0.5f
                    b.btnSell.isEnabled = false
                    b.btnSell.alpha = 0.5f
                }
            }

            override fun onFailure(call: Call<StockDetailDto>, t: Throwable) {
                if (activeStockDetailCall !== call) return
                if (!isAdded || _binding == null) return
                isStockDetailStale = true
                b.tvTradeWarningMessage.text = getString(R.string.stock_trade_disabled_warning)
                b.tvTradeWarningMessage.visibility = View.VISIBLE
                b.btnBuy.isEnabled = false
                b.btnBuy.alpha = 0.5f
                b.btnSell.isEnabled = false
                b.btnSell.alpha = 0.5f
            }
        })
    }

    private fun releaseCandleCall(call: Call<List<CandleDto>>?) {
        if (activeCandleCall == call) {
            activeCandleCall = null
        }
    }

    private fun releasePortfolioCall(call: Call<PortfolioSummaryDto>?) {
        if (activePortfolioCall == call) {
            activePortfolioCall = null
        }
    }

    /**
     * Tải dữ liệu nến từ Backend qua Retrofit
     */
    private fun loadCandleData(symbol: String) {
        setLoadingState(true)

        activeCandleCall?.cancel()
        val call = RetrofitClient.apiService.getCandles(symbol, "daily")
        activeCandleCall = call

        call.enqueue(object : Callback<List<CandleDto>> {
            override fun onResponse(call: Call<List<CandleDto>>, response: Response<List<CandleDto>>) {
                try {
                    if (call.isCanceled || !isAdded || _binding == null) return
                    setLoadingState(false)

                    if (response.code() == 401 || response.code() == 403) {
                        AuthSessionManager.handleUnauthorized(activity)
                        return
                    }

                    if (response.code() == 503) {
                        context?.let { ctx ->
                            Toast.makeText(ctx, "Nguồn dữ liệu thị trường tạm thời không khả dụng", Toast.LENGTH_SHORT).show()
                        }
                        handleCandleLoadFallback(symbol)
                        return
                    }

                    val candles = response.body()
                    if (response.isSuccessful && !candles.isNullOrEmpty()) {
                        renderCandleChart(candles, symbol)
                    } else {
                        Log.w(TAG, "API nến rỗng hoặc lỗi code: ${response.code()} cho symbol $symbol")
                        handleCandleLoadFallback(symbol)
                    }
                } finally {
                    releaseCandleCall(call)
                }
            }

            override fun onFailure(call: Call<List<CandleDto>>, t: Throwable) {
                try {
                    if (call.isCanceled || !isAdded || _binding == null) return
                    setLoadingState(false)
                    Log.e(TAG, "Lỗi kết nối Retrofit nến: ${t.message}")
                    handleCandleLoadFallback(symbol)
                } finally {
                    releaseCandleCall(call)
                }
            }
        })
    }

    private fun handleCandleLoadFallback(symbol: String) {
        val b = binding ?: return

        val decision = CandleFallbackPolicy.decide(
            currentSymbol = symbol,
            loadedCandleSymbol = loadedCandleSymbol,
            hasCachedCandles = candleEntries.isNotEmpty()
        )

        if (decision.shouldClearChart) {
            candleEntries.clear()
            b.candleChart.clear()
            b.tvStateMessage.text = getString(R.string.trading_err_no_candles)
            b.tvStateMessage.visibility = View.VISIBLE
        } else {
            b.tvStateMessage.visibility = View.GONE
        }
    }

    private fun renderCandleChart(candles: List<CandleDto>, symbol: String) {
        val b = binding ?: return
        val ctx = context ?: return

        candleEntries.clear()
        val timeLabels = ArrayList<String>()

        for (i in candles.indices) {
            val c = candles[i]
            candleEntries.add(CandleEntry(i.toFloat(), c.high.toFloat(), c.low.toFloat(), c.open.toFloat(), c.close.toFloat()))
            timeLabels.add(c.time)
        }

        if (candleEntries.isEmpty()) {
            b.candleChart.clear()
            b.tvStateMessage.text = getString(R.string.trading_chart_empty)
            b.tvStateMessage.visibility = View.VISIBLE
            return
        }

        b.tvStateMessage.visibility = View.GONE
        loadedCandleSymbol = symbol

        b.candleChart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                if (index in timeLabels.indices) {
                    val raw = timeLabels[index]
                    return if (raw.length >= 10) raw.substring(5, 10) else raw
                }
                return ""
            }
        }

        val dataSet = CandleDataSet(candleEntries, ChartLabelFormatter.formatDailyDatasetLabel(symbol)).apply {
            setDrawIcons(false)
            shadowColor = ContextCompat.getColor(ctx, R.color.tv_text_secondary)
            shadowWidth = 1.2f
            decreasingColor = ContextCompat.getColor(ctx, R.color.tv_red)
            decreasingPaintStyle = Paint.Style.FILL
            increasingColor = ContextCompat.getColor(ctx, R.color.tv_green)
            increasingPaintStyle = Paint.Style.FILL
            neutralColor = ContextCompat.getColor(ctx, R.color.tv_text_secondary)
            setDrawValues(false)
            highLightColor = ContextCompat.getColor(ctx, R.color.white)
        }

        candleDataSet = dataSet
        b.candleChart.data = CandleData(dataSet)
        b.candleChart.invalidate()
    }

    private fun connectWebSocketForSymbol(symbol: String) {
        disconnectWebSocket()

        if (StockTradePolicy.isStock(symbol)) {
            return
        }

        val streamName = MarketStreamHelper.resolveWebSocketStream(symbol) ?: return
        val wsUrl = MarketStreamHelper.buildWebSocketUrl(streamName)
        val request = Request.Builder().url(wsUrl).build()

        binanceWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                activity?.runOnUiThread {
                    if (currentSymbol.equals(symbol, ignoreCase = true)) {
                        binding?.tvLiveStatus?.text = getString(R.string.trading_waiting_price_badge)
                        binding?.tvLiveStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tv_yellow))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                activity?.runOnUiThread {
                    handleWebSocketMessage(text, symbol)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                activity?.runOnUiThread {
                    if (currentSymbol.equals(symbol, ignoreCase = true)) {
                        binding?.tvLiveStatus?.text = getString(R.string.trading_offline_badge)
                        binding?.tvLiveStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.tv_red))
                    }
                }
            }
        })
    }

    private fun handleWebSocketMessage(jsonText: String, expectedSymbol: String) {
        if (!currentSymbol.equals(expectedSymbol, ignoreCase = true) || _binding == null || !isAdded) return

        try {
            val json = JSONObject(jsonText)
            val priceStr = json.optString("c")
            val openPriceStr = json.optString("o")
            if (priceStr.isNotEmpty()) {
                val livePrice = priceStr.toDouble()
                val openPrice = if (openPriceStr.isNotEmpty()) openPriceStr.toDoubleOrNull() else null
                updateLivePriceDisplay(livePrice, openPrice)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi phân tích JSON WebSocket: ${e.message}")
        }
    }

    private fun updateLivePriceDisplay(livePrice: Double, periodOpenPrice: Double?) {
        val b = binding ?: return
        val ctx = context ?: return

        previousAssetPrice = currentAssetPrice
        currentAssetPrice = livePrice

        b.tvLiveStatus.text = getString(R.string.trading_live_badge)
        b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.tv_green))

        b.tvCurrentPrice.text = String.format(Locale.US, "$%,.2f", livePrice)
        val priceColor = when {
            previousAssetPrice != null && livePrice > previousAssetPrice!! -> R.color.tv_green
            previousAssetPrice != null && livePrice < previousAssetPrice!! -> R.color.tv_red
            else -> R.color.tv_text_primary
        }
        b.tvCurrentPrice.setTextColor(ContextCompat.getColor(ctx, priceColor))

        val baseline = periodOpenPrice ?: baselinePeriodPrice
        if (baseline != null && baseline > 0) {
            val changePercent = ((livePrice - baseline) / baseline) * 100
            val sign = if (changePercent >= 0) "+" else ""
            b.tvPriceChange.text = String.format(Locale.US, "%s%.2f%%", sign, changePercent)
            val badgeColor = if (changePercent >= 0) R.color.tv_green else R.color.tv_red
            b.tvPriceChange.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, badgeColor))
        }

        updateHoldingsForCurrentSymbol()
        updatePortfolioDisplay()
        updateTradeActionsState()
    }

    private fun updatePortfolioDisplay() {
        val b = binding ?: return
        val cash = userCashBalance
        if (cash != null) {
            b.tvCashBalance.text = String.format(Locale.US, "$%,.2f USD", cash)
        } else {
            b.tvCashBalance.text = "—"
        }

        val ticker = getAssetTicker(currentSymbol)
        b.tvHoldingsLabel.text = getString(R.string.trading_holding_label_format, ticker)
        if (portfolioLoaded) {
            b.tvHoldings.text = String.format(Locale.US, "%.4f %s", userHoldingsQuantity, ticker)
        } else {
            b.tvHoldings.text = "—"
        }
    }

    private fun updateTradeActionsState() {
        val isStock = StockTradePolicy.isStock(currentSymbol)
        val ready = TradingDataReadiness.isReadyForTrading(
            token = jwtToken,
            currentPrice = currentAssetPrice,
            portfolioLoaded = portfolioLoaded,
            userCashBalance = userCashBalance
        ) && (!isStock || (!isStockDetailStale && currentAssetPrice != null && currentAssetPrice!! > 0.0))

        binding?.btnBuy?.isEnabled = ready
        binding?.btnBuy?.alpha = if (ready) 1.0f else 0.5f
        binding?.btnSell?.isEnabled = ready
        binding?.btnSell?.alpha = if (ready) 1.0f else 0.5f
    }

    private fun setupTradeActions() {
        binding?.btnBuy?.setOnClickListener {
            openOrderTicket("BUY")
        }

        binding?.btnSell?.setOnClickListener {
            openOrderTicket("SELL")
        }

        updateTradeActionsState()
    }

    private fun openOrderTicket(orderType: String) {
        if (!TradingDataReadiness.canOpenOrderTicket(jwtToken, currentAssetPrice, portfolioLoaded, userCashBalance)) {
            context?.let { ctx ->
                Toast.makeText(ctx, getString(R.string.trading_err_data_not_ready), Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (parentFragmentManager.findFragmentByTag(OrderTicketBottomSheet.TAG) != null) {
            return
        }

        val price = currentAssetPrice ?: return
        val cash = userCashBalance ?: return

        val bottomSheet = OrderTicketBottomSheet.newInstance(
            orderType = orderType,
            symbol = currentSymbol,
            currentPrice = price,
            availableCash = cash,
            ownedQuantity = userHoldingsQuantity
        )

        bottomSheet.show(parentFragmentManager, OrderTicketBottomSheet.TAG)
    }

    private fun loadPortfolio() {
        val authHeader = AuthHeaderFactory.createBearerHeader(jwtToken) ?: return

        if (activePortfolioCall != null) {
            return
        }

        val call = RetrofitClient.apiService.getPortfolio(authHeader)
        activePortfolioCall = call

        call.enqueue(object : Callback<PortfolioSummaryDto> {
            override fun onResponse(call: Call<PortfolioSummaryDto>, response: Response<PortfolioSummaryDto>) {
                try {
                    if (call.isCanceled || !isAdded || _binding == null) return
                    if (response.code() == 401 || response.code() == 403) {
                        AuthSessionManager.handleUnauthorized(activity)
                        return
                    }
                    val p = response.body()
                    if (response.isSuccessful && p != null) {
                        userCashBalance = p.cashBalanceUsd
                        portfolioLoaded = true
                        lastPortfolioFetchTime = System.currentTimeMillis()
                        portfolioHoldingsList = p.holdings ?: emptyList()
                        updateHoldingsForCurrentSymbol()
                        updatePortfolioDisplay()
                        updateTradeActionsState()
                    }
                } finally {
                    releasePortfolioCall(call)
                }
            }

            override fun onFailure(call: Call<PortfolioSummaryDto>, t: Throwable) {
                try {
                    if (call.isCanceled) return
                    Log.e(TAG, "Không thể tải portfolio: ${t.message}")
                } finally {
                    releasePortfolioCall(call)
                }
            }
        })
    }

    private fun loadPortfolioSilently() {
        val authHeader = AuthHeaderFactory.createBearerHeader(jwtToken) ?: return

        if (activePortfolioCall != null) {
            return
        }

        val call = RetrofitClient.apiService.getPortfolio(authHeader)
        activePortfolioCall = call

        call.enqueue(object : Callback<PortfolioSummaryDto> {
            override fun onResponse(call: Call<PortfolioSummaryDto>, response: Response<PortfolioSummaryDto>) {
                try {
                    if (call.isCanceled || !isAdded || _binding == null) return
                    if (response.code() == 401 || response.code() == 403) {
                        AuthSessionManager.handleUnauthorized(activity)
                        return
                    }
                    val p = response.body()
                    if (response.isSuccessful && p != null) {
                        if (p.cashBalanceUsd != null) {
                            userCashBalance = p.cashBalanceUsd
                        }
                        portfolioLoaded = true
                        lastPortfolioFetchTime = System.currentTimeMillis()
                        portfolioHoldingsList = p.holdings ?: emptyList()
                        updateHoldingsForCurrentSymbol()
                        updatePortfolioDisplay()
                        updateTradeActionsState()
                    }
                } finally {
                    releasePortfolioCall(call)
                }
            }

            override fun onFailure(call: Call<PortfolioSummaryDto>, t: Throwable) {
                releasePortfolioCall(call)
            }
        })
    }

    private fun updateHoldingsForCurrentSymbol() {
        val holding = portfolioHoldingsList.find {
            it.symbol?.equals(currentSymbol, ignoreCase = true) == true ||
            (currentSymbol.contains("BTC") && it.symbol?.contains("BTC") == true) ||
            (currentSymbol.contains("ETH") && it.symbol?.contains("ETH") == true) ||
            (currentSymbol.contains("XAU") && it.symbol?.contains("XAU") == true)
        }
        userHoldingsQuantity = holding?.quantity ?: 0.0
        userHoldingsAvgBuyPrice = holding?.avgBuyPrice ?: 0.0
    }

    private fun setLoadingState(isLoading: Boolean) {
        val b = binding ?: return
        b.pbLoading.visibility = if (isLoading && candleEntries.isEmpty()) View.VISIBLE else View.GONE
        b.tvStateMessage.visibility = View.GONE
    }

    private fun getSavedToken(): String {
        val ctx = context ?: return ""
        return AuthSessionManager.getToken(ctx)
    }

    private fun formatSymbolDisplay(sym: String): String = OrderTicketBottomSheet.formatSymbolDisplay(sym)

    private fun getFriendlyName(sym: String): String {
        if (StockTradePolicy.isStock(sym)) {
            return when (sym) {
                "AAPL" -> "Apple Inc."
                "MSFT" -> "Microsoft Corporation"
                "NVDA" -> "NVIDIA Corporation"
                "TSLA" -> "Tesla, Inc."
                "AMZN" -> "Amazon.com, Inc."
                "META" -> "Meta Platforms, Inc."
                "GOOGL" -> "Alphabet Inc."
                "JPM" -> "JPMorgan Chase & Co."
                else -> "Cổ phiếu Hoa Kỳ"
            }
        }
        return when {
            sym.contains("BTC") -> "Bitcoin / Tether"
            sym.contains("ETH") -> "Ethereum / Tether"
            sym.contains("XAU") -> "Vàng Thế Giới (Gold Spot)"
            else -> "Tài sản tài chính"
        }
    }

    private fun getAssetTicker(sym: String): String = OrderTicketBottomSheet.getAssetTicker(sym)

    companion object {
        private const val TAG = "FNMF_TradingFragment"
        const val KEY_SAVED_SYMBOL = "SAVED_MARKET_SYMBOL"
    }
}
