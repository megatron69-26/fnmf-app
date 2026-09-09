package com.example.nhumonglenh

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.CandleDto
import com.example.nhumonglenh.data.remote.HoldingDto
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.databinding.FragmentTradingBinding
import com.example.nhumonglenh.ui.trading.AuthHeaderFactory
import com.example.nhumonglenh.ui.trading.CandleFallbackPolicy
import com.example.nhumonglenh.ui.trading.CandleReloadPolicy
import com.example.nhumonglenh.ui.trading.ChartLabelFormatter
import com.example.nhumonglenh.ui.trading.MarketStreamHelper
import com.example.nhumonglenh.ui.trading.OrderTicketBottomSheet
import com.example.nhumonglenh.ui.trading.PortfolioSyncPolicy
import com.example.nhumonglenh.ui.trading.TradingDataReadiness
import com.example.nhumonglenh.ui.trading.TradingStateRestoration
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
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
 * TRADING FRAGMENT - GIAO DIỆN GIAO DỊCH CHUẨN CHART-FIRST (RC3)
 * =====================================================================
 * - Instrument Header: Symbol, full name, live price, 24h change %, live/offline badge.
 * - Chart-First: Candlestick chart chiếm không gian linh hoạt lớn nhất màn hình.
 * - Compact Account Strip: Tiền mặt khả dụng & Số lượng đang giữ của mã hiện tại.
 * - Sticky Actions: Hai nút MUA và BÁN cố định phía dưới.
 * - Không có giá khởi tạo giả, không có số dư mặc định $10,000.
 * - Quản lý nến mã cũ - mã mới độc lập, không dùng cache mã khác.
 * - Quản lý vòng đời WebSocket và Portfolio theo onHiddenChanged.
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

    // Calls đang chạy
    private var activeCandleCall: Call<List<CandleDto>>? = null
    private var activePortfolioCall: Call<PortfolioSummaryDto>? = null

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
                // Đồng bộ lại số dư ví thật sau khi khớp lệnh đúng 1 lần
                loadPortfolioSilently()
            }
        }

        // 2. Cấu hình giao diện Biểu đồ Nến Dark Theme
        setupCandleChartStyle()

        // 3. Cấu hình các nút đặt lệnh MUA / BÁN
        setupTradeActions()

        // 4. Khởi động với symbol đã lưu hoặc mặc định BTCUSDT (không dùng giá giả)
        val initialSymbol = TradingStateRestoration.resolveInitialSymbol(savedInstanceState?.getString(KEY_SAVED_SYMBOL))
        switchMarketSymbol(initialSymbol, isInitial = true)

        // 5. Tải danh mục đầu tư thật lần đầu
        loadPortfolio()

        // 6. Chạm thẻ tài sản để làm mới số dư chủ động
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
            // Khi tab bị ẩn: dừng WebSocket và hủy các call mạng đang dở
            disconnectWebSocket()
            activeCandleCall?.cancel()
            activeCandleCall = null
            activePortfolioCall?.cancel()
            activePortfolioCall = null
        } else {
            // Khi quay lại tab: kiểm tra nếu biểu đồ trống hoặc đổi symbol thì nạp lại nến
            val shouldReloadCandles = CandleReloadPolicy.shouldReloadOnTabVisible(
                hasCandleData = candleEntries.isNotEmpty(),
                loadedCandleSymbol = loadedCandleSymbol,
                currentSymbol = currentSymbol
            )
            if (shouldReloadCandles) {
                loadCandleData(currentSymbol)
            }
            // Kiểm tra dữ liệu cũ hơn 30s thì mới làm mới portfolio
            if (PortfolioSyncPolicy.isStale(lastPortfolioFetchTime)) {
                loadPortfolioSilently()
            }
            // Kết nối lại WebSocket nếu chưa có
            if (binanceWebSocket == null) {
                connectWebSocketForSymbol(currentSymbol)
            }
        }
    }

    override fun onDestroyView() {
        disconnectWebSocket()
        activeCandleCall?.cancel()
        activeCandleCall = null
        activePortfolioCall?.cancel()
        activePortfolioCall = null
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

            // Tắt trục Y bên Phải cho thoáng
            axisRight.isEnabled = false
        }
    }

    /**
     * Chuyển đổi mã tài sản hiển thị biểu đồ Nến (BTCUSDT, ETHUSDT, XAUUSD...)
     */
    fun switchMarketSymbol(symbol: String, isInitial: Boolean = false) {
        val sym = symbol.uppercase()
        currentSymbol = sym

        val b = binding ?: return
        val ctx = context ?: return

        // 1. Cập nhật Tiêu đề Header
        b.tvHeaderSymbol.text = formatSymbolDisplay(sym)
        b.tvHeaderFullName.text = if (MarketStreamHelper.isGoldReferenceStream(sym)) {
            getString(R.string.trading_gold_paxg_reference)
        } else {
            getFriendlyName(sym)
        }

        // 2. Xóa giá khởi tạo giả: đưa về null / "—", KHÔNG gán 78000 / 3550 / 2500
        currentAssetPrice = null
        previousAssetPrice = null
        baselinePeriodPrice = null

        b.tvCurrentPrice.text = "—"
        b.tvCurrentPrice.setTextColor(ContextCompat.getColor(ctx, R.color.tv_text_primary))
        b.tvPriceChange.text = "—"
        b.tvPriceChange.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.tv_surface))

        // Xóa nến cũ trên chart khi chuyển mã để tránh biểu đồ mã cũ hiển thị nhầm cho mã mới
        candleEntries.clear()
        b.candleChart.clear()

        // Trạng thái kết nối ban đầu (chưa nhận tick thì KHÔNG được hiện LIVE)
        val streamName = MarketStreamHelper.resolveWebSocketStream(sym)
        if (streamName == null) {
            b.tvLiveStatus.text = getString(R.string.trading_no_live_badge)
            b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.tv_text_secondary))
        } else {
            b.tvLiveStatus.text = getString(R.string.trading_connecting_badge)
            b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, R.color.tv_yellow))
        }

        if (!isInitial) {
            Toast.makeText(ctx, getString(R.string.trading_toast_switch_symbol, sym), Toast.LENGTH_SHORT).show()
        }

        // Cập nhật trạng thái nút Mua/Bán (bị disable vì giá đang null)
        updateTradeActionsState()

        // 3. Tải dữ liệu nến từ Backend
        loadCandleData(sym)

        // 4. Kết nối WebSocket stream tương ứng
        connectWebSocketForSymbol(sym)

        // 5. Cập nhật số dư & lượng coin sở hữu cho mã đang chọn
        updateHoldingsForCurrentSymbol()
        updatePortfolioDisplay()
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
        val ctx = context ?: return

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

        b.tvLiveStatus.text = getString(decision.badgeTextRes)
        b.tvLiveStatus.setTextColor(ContextCompat.getColor(ctx, decision.statusColorRes))
    }

    /**
     * Vẽ tập dữ liệu Nến lên MPAndroidChart
     */
    private fun renderCandleChart(candles: List<CandleDto>, symbol: String) {
        val b = binding ?: return
        val ctx = context ?: return
        candleEntries.clear()

        for (i in candles.indices) {
            val c = candles[i]
            val high = c.high.toFloat()
            val low = c.low.toFloat()
            val open = c.open.toFloat()
            val close = c.close.toFloat()
            candleEntries.add(CandleEntry(i.toFloat(), high, low, open, close))
        }

        loadedCandleSymbol = symbol

        if (candles.isNotEmpty()) {
            baselinePeriodPrice = candles.first().open
            currentAssetPrice = candles.last().close
            previousAssetPrice = currentAssetPrice
            val basePrice = baselinePeriodPrice ?: 0.0
            val curPrice = currentAssetPrice ?: 0.0
            val changePercent = if (basePrice > 0) {
                ((curPrice - basePrice) / basePrice) * 100.0
            } else 0.0
            b.tvCurrentPrice.text = String.format(Locale.US, "$%,.2f", curPrice)
            updatePriceChangeDisplay(changePercent)
            updateTradeActionsState()
            updatePortfolioDisplay()
        }

        val dataSet = CandleDataSet(candleEntries, ChartLabelFormatter.formatDailyDatasetLabel(symbol)).apply {
            color = ContextCompat.getColor(ctx, R.color.white)
            shadowColor = ContextCompat.getColor(ctx, R.color.tv_border)
            shadowWidth = 0.8f

            increasingColor = ContextCompat.getColor(ctx, R.color.tv_green)
            increasingPaintStyle = Paint.Style.FILL

            decreasingColor = ContextCompat.getColor(ctx, R.color.tv_red)
            decreasingPaintStyle = Paint.Style.FILL

            neutralColor = ContextCompat.getColor(ctx, R.color.white)
            setDrawValues(false)
        }

        this.candleDataSet = dataSet
        b.candleChart.data = CandleData(dataSet)
        b.candleChart.axisLeft.resetAxisMinimum()
        b.candleChart.axisLeft.resetAxisMaximum()
        b.candleChart.invalidate()
    }

    /**
     * Kết nối WebSocket / Ticker tương ứng với mã tài sản
     */
    private fun connectWebSocketForSymbol(symbol: String) {
        disconnectWebSocket()

        val sym = symbol.uppercase()
        val streamName = MarketStreamHelper.resolveWebSocketStream(sym)
        if (streamName != null) {
            connectBinanceStream(streamName)
        } else {
            Log.d(TAG, "Symbol $sym không có live WebSocket stream, sử dụng dữ liệu nến backend")
        }
    }

    /**
     * Kết nối Binance Public WebSocket API
     */
    private fun connectBinanceStream(streamName: String) {
        val streamUrl = MarketStreamHelper.buildWebSocketUrl(streamName)
        val request = Request.Builder()
            .url(streamUrl)
            .build()

        binanceWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "Binance WebSocket connected: $streamName")
                activity?.runOnUiThread {
                    context?.let { ctx ->
                        // Đang kết nối, chờ message đầu tiên mới chuyển sang LIVE
                        binding?.tvLiveStatus?.text = getString(R.string.trading_waiting_price_badge)
                        binding?.tvLiveStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.tv_yellow))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("k")) {
                        val k = json.getJSONObject("k")
                        val open = k.getString("o").toFloat()
                        val high = k.getString("h").toFloat()
                        val low = k.getString("l").toFloat()
                        val close = k.getString("c").toDouble()
                        val isClosed = k.optBoolean("x", false)

                        activity?.runOnUiThread {
                            context?.let { ctx ->
                                binding?.tvLiveStatus?.text = getString(R.string.trading_live_badge)
                                binding?.tvLiveStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.tv_green))
                            }
                            onLivePriceTick(open, high, low, close, isClosed)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi phân tích WebSocket JSON: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.w(TAG, "WebSocket disconnected: ${t.message}")
                activity?.runOnUiThread {
                    context?.let { ctx ->
                        binding?.tvLiveStatus?.text = getString(R.string.trading_offline_badge)
                        binding?.tvLiveStatus?.setTextColor(ContextCompat.getColor(ctx, R.color.tv_yellow))
                    }
                }
            }
        })
    }

    /**
     * Cập nhật nến sống và nhịp giá
     */
    private fun onLivePriceTick(open: Float, high: Float, low: Float, close: Double, isClosed: Boolean) {
        val b = binding ?: return
        val ctx = context ?: return

        val prev = currentAssetPrice ?: close
        previousAssetPrice = prev
        currentAssetPrice = close

        // 1. Cập nhật giá Header
        val priceColorRes = if (close >= prev) R.color.tv_green else R.color.tv_red
        b.tvCurrentPrice.text = String.format(Locale.US, "$%,.2f", close)
        b.tvCurrentPrice.setTextColor(ContextCompat.getColor(ctx, priceColorRes))

        // Cập nhật % thay đổi
        val basePrice = baselinePeriodPrice ?: 0.0
        if (basePrice > 0.0) {
            val change = ((close - basePrice) / basePrice) * 100.0
            updatePriceChangeDisplay(change)
        }

        // Cập nhật trạng thái nút Mua/Bán
        updateTradeActionsState()

        // 2. Cập nhật nến trên biểu đồ
        if (candleEntries.isNotEmpty()) {
            val lastEntry = candleEntries.last()
            lastEntry.high = Math.max(lastEntry.high, high)
            lastEntry.low = Math.min(lastEntry.low, low)
            lastEntry.close = close.toFloat()

            if (isClosed) {
                val newX = lastEntry.x + 1f
                candleEntries.add(CandleEntry(newX, high, low, open, close.toFloat()))
                if (candleEntries.size > 40) {
                    candleEntries.removeAt(0)
                }
            }

            candleDataSet?.calcMinMax()
            b.candleChart.data?.notifyDataChanged()
            b.candleChart.notifyDataSetChanged()
            b.candleChart.invalidate()
        }

        // 3. Cập nhật dòng Compact Account Strip
        updatePortfolioDisplay()
    }

    private fun updatePriceChangeDisplay(changePercent: Double) {
        val b = binding ?: return
        val ctx = context ?: return
        val isPositive = changePercent >= 0.0
        val sign = if (isPositive) "+" else ""
        val text = String.format(Locale.US, "%s%.2f%%", sign, changePercent)
        b.tvPriceChange.text = text
        val bgColorRes = if (isPositive) R.color.tv_green else R.color.tv_red
        b.tvPriceChange.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, bgColorRes))
    }

    /**
     * Cập nhật dòng Compact Account Strip: Tiền khả dụng & Lượng coin sở hữu
     */
    private fun updatePortfolioDisplay() {
        val b = binding ?: return
        val cash = userCashBalance
        if (portfolioLoaded && cash != null) {
            b.tvCashBalance.text = String.format(Locale.US, "$%,.2f USD", cash)
        } else {
            b.tvCashBalance.text = "—"
        }

        val assetTicker = getAssetTicker(currentSymbol)
        b.tvHoldingsLabel.text = getString(R.string.trading_holding_label_format, assetTicker)
        if (portfolioLoaded) {
            b.tvHoldings.text = String.format(Locale.US, "%.4f %s", userHoldingsQuantity, assetTicker)
        } else {
            b.tvHoldings.text = "—"
        }
    }

    /**
     * Cập nhật trạng thái enabled/disabled của nút MUA và BÁN
     * Chỉ enable khi token, giá thật và portfolio thật đều hợp lệ
     */
    private fun updateTradeActionsState() {
        val ready = TradingDataReadiness.isReadyForTrading(
            token = jwtToken,
            currentPrice = currentAssetPrice,
            portfolioLoaded = portfolioLoaded,
            userCashBalance = userCashBalance
        )
        binding?.btnBuy?.isEnabled = ready
        binding?.btnBuy?.alpha = if (ready) 1.0f else 0.5f
        binding?.btnSell?.isEnabled = ready
        binding?.btnSell?.alpha = if (ready) 1.0f else 0.5f
    }

    /**
     * Gắn sự kiện Mua / Bán mở BottomSheet Order Ticket
     */
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

        // Chỉ mở đúng 1 bottom sheet tại một thời điểm
        if (parentFragmentManager.findFragmentByTag(OrderTicketBottomSheet.TAG) != null) {
            Log.d(TAG, "OrderTicketBottomSheet đang mở, không mở thêm")
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

    /**
     * Tải thông tin số dư tài sản từ Backend Server
     */
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
                        // Giữ đúng số dư thật của backend, không gán số dư giả
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

    /**
     * Tải số dư ngầm không quấy rầy UI hay reset chart
     */
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
        return com.example.nhumonglenh.data.local.AuthSessionManager.getToken(ctx)
    }

    private fun formatSymbolDisplay(sym: String): String = OrderTicketBottomSheet.formatSymbolDisplay(sym)

    private fun getFriendlyName(sym: String): String {
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
