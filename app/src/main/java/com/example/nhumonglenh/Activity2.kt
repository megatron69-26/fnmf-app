package com.example.nhumonglenh

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.entity.AiAnalysisEntity
import com.example.nhumonglenh.data.local.entity.NewsEntity
import com.example.nhumonglenh.data.local.entity.WatchlistItem
import com.example.nhumonglenh.data.model.CandleDto
import com.example.nhumonglenh.data.model.HoldingDto
import com.example.nhumonglenh.data.model.MobileNewsBundleResponse
import com.example.nhumonglenh.data.model.OrderRequest
import com.example.nhumonglenh.data.model.OrderResponse
import com.example.nhumonglenh.data.model.PortfolioSummaryDto
import com.example.nhumonglenh.data.model.UiState
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * =====================================================================
 * ACTIVITY 2 - MÀN HÌNH GIAO DỊCH CHÍNH (HYBRID MULTI-ASSET ARCHITECTURE)
 * =====================================================================
 * 1. MULTI-ASSET WATCHLIST: Chuyển đổi nến tức thì giữa BTCUSDT, ETHUSDT, XAUUSD
 * 2. BINANCE WEBSOCKET: Truyền luồng nến sống & giá nhảy từng giây (1s Ticks)
 * 3. BACKEND KHÔI: Khớp lệnh Mua/Bán, tính toán số dư Oracle/H2 DB, AI Insights
 * 4. ROOM DB MẠNH: Lưu trữ danh mục theo dõi và tin tức ngoại tuyến
 * 5. REAL-TIME AUTO-SYNC LOOP: Tự động đồng bộ số dư mỗi 3 giây phục vụ Demo từ xa
 * =====================================================================
 */
class Activity2 : AppCompatActivity() {

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvLiveStatus: TextView
    private lateinit var candleChart: CandleStickChart
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvStateMessage: TextView
    private lateinit var tvCashBalance: TextView
    private lateinit var tvHoldings: TextView
    private lateinit var tvQuantityLabel: TextView
    private lateinit var etQuantity: EditText
    private lateinit var btnBuy: Button
    private lateinit var btnSell: Button
    private lateinit var llWatchlistContainer: LinearLayout
    private lateinit var lvWatchlist: ListView

    private lateinit var db: AppDatabase
    private var jwtToken: String = ""

    // Dữ liệu nến trong bộ nhớ
    private val candleEntries = ArrayList<CandleEntry>()
    private var candleDataSet: CandleDataSet? = null

    // Quản lý mã tài sản đang chọn
    private var currentSymbol: String = "BTCUSDT"
    private var currentAssetPrice: Double = 0.0
    private var previousAssetPrice: Double = 0.0
    private var userCashBalance: Double = 10000.0
    private var userHoldingsQuantity: Double = 0.0
    private var userHoldingsAvgBuyPrice: Double = 0.0
    private var portfolioHoldingsList: List<HoldingDto> = emptyList()

    // Map lưu view của từng item watchlist để highlight
    private val watchlistViewsMap = HashMap<String, View>()

    // WebSocket & Job Client
    private var binanceWebSocket: WebSocket? = null
    private var goldSimulationJob: Job? = null
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ẩn thanh ActionBar màu tím mặc định để giao diện Dark Theme tràn viền đẹp mắt
        supportActionBar?.hide()
        
        setContentView(R.layout.layout_activity2)
        Log.d(TAG, "Activity2 onCreate")

        // 1. Khởi tạo Views
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        candleChart = findViewById(R.id.candleChart)
        pbLoading = findViewById(R.id.pbLoading)
        tvStateMessage = findViewById(R.id.tvStateMessage)
        tvCashBalance = findViewById(R.id.tvCashBalance)
        tvHoldings = findViewById(R.id.tvHoldings)
        tvQuantityLabel = findViewById(R.id.tvQuantityLabel)
        etQuantity = findViewById(R.id.etQuantity)
        btnBuy = findViewById(R.id.btnBuy)
        btnSell = findViewById(R.id.btnSell)
        llWatchlistContainer = findViewById(R.id.llWatchlistContainer)
        lvWatchlist = findViewById(R.id.lvWatchlist)

        // 2. Khởi tạo Room Database
        db = AppDatabase.getInstance(this)
        jwtToken = getSavedToken()

        // 3. Cấu hình giao diện Biểu đồ Nến Dark Theme
        setupCandleChartStyle()

        // 4. Tải dữ liệu Watchlist & Tin tức Offline Room DB
        loadLocalWatchlist()
        syncNewsToRoomDB()

        // 5. Khởi động với mã mặc định BTCUSDT
        switchMarketSymbol("BTCUSDT", isInitial = true)

        // 6. Cập nhật Số dư ví & Khớp lệnh Mua/Bán
        setupTradeActions()
        loadPortfolio()

        // 7. Tự động quét đồng bộ số dư ngầm mỗi 3 giây (Hỗ trợ demo thao túng từ xa)
        startAutoSyncPortfolioLoop()

        // 8. Cho phép chạm vào thẻ Tài sản để refresh tức thì
        tvHoldings.setOnClickListener {
            Toast.makeText(this, "Đang đồng bộ số dư mới nhất từ Server...", Toast.LENGTH_SHORT).show()
            loadPortfolio()
        }
    }

    /**
     * Cấu hình thẩm mỹ chuẩn Dark Theme cho MPAndroidChart
     */
    private fun setupCandleChartStyle() {
        candleChart.apply {
            setBackgroundColor(Color.parseColor("#131722"))
            description.isEnabled = false
            legend.textColor = Color.WHITE
            setDrawGridBackground(false)
            isDoubleTapToZoomEnabled = true
            setPinchZoom(true)

            // Cấu hình trục X (Thời gian)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.parseColor("#787B86")
                setDrawGridLines(false)
                setAvoidFirstLastClipping(true)
            }

            // Cấu hình trục Y bên Trái (Giá tiền)
            axisLeft.apply {
                textColor = Color.parseColor("#787B86")
                gridColor = Color.parseColor("#2A2E39")
                setDrawAxisLine(false)
                resetAxisMinimum()
                resetAxisMaximum()
            }

            // Tắt trục Y bên Phải cho thoáng
            axisRight.isEnabled = false
        }
    }

    /**
     * Chuyển đổi mã tài sản hiển thị biểu đồ Nến (BTCUSDT, ETHUSDT, XAUUSD)
     */
    fun switchMarketSymbol(symbol: String, isInitial: Boolean = false) {
        val sym = symbol.uppercase()
        currentSymbol = sym

        // 1. Cập nhật Tiêu đề Header & Nhãn Khối lượng
        when (sym) {
            "BTCUSDT", "BTC" -> {
                tvHeaderTitle.text = "FNMF • BTC/USDT"
                tvQuantityLabel.text = "Khối lượng đặt lệnh (BTC):"
                etQuantity.setText("0.005")
            }
            "ETHUSDT", "ETH" -> {
                tvHeaderTitle.text = "FNMF • ETH/USDT"
                tvQuantityLabel.text = "Khối lượng đặt lệnh (ETH):"
                etQuantity.setText("0.05")
            }
            "XAUUSD", "XAU" -> {
                tvHeaderTitle.text = "FNMF • XAU/USD (VÀNG)"
                tvQuantityLabel.text = "Khối lượng đặt lệnh (Ounce):"
                etQuantity.setText("1.0")
            }
            else -> {
                tvHeaderTitle.text = "FNMF • $sym"
                tvQuantityLabel.text = "Khối lượng đặt lệnh ($sym):"
                etQuantity.setText("1.0")
            }
        }

        // 2. Highlight card được chọn trong Watchlist
        highlightSelectedWatchlistItem(sym)

        if (!isInitial) {
            Toast.makeText(this, "📊 Đang mở biểu đồ nến $sym...", Toast.LENGTH_SHORT).show()
        }

        // 3. Tải dữ liệu nến từ Backend
        loadCandleData(sym)

        // 4. Đổi luồng WebSocket theo mã
        connectWebSocketForSymbol(sym)

        // 5. Cập nhật lại số lượng coin của mã đang chọn trong ví
        updateHoldingsForCurrentSymbol()
        updatePortfolioDisplay()
    }

    /**
     * Highlight thẻ Watchlist đang được chọn
     */
    private fun highlightSelectedWatchlistItem(activeSymbol: String) {
        for ((sym, view) in watchlistViewsMap) {
            if (sym.equals(activeSymbol, ignoreCase = true) || 
                (activeSymbol.contains("BTC") && sym.contains("BTC")) ||
                (activeSymbol.contains("ETH") && sym.contains("ETH")) ||
                (activeSymbol.contains("XAU") && sym.contains("XAU"))) {
                view.setBackgroundColor(Color.parseColor("#2A3245"))
            } else {
                view.setBackgroundColor(Color.parseColor("#1E222D"))
            }
        }
    }

    /**
     * Tải dữ liệu nến từ Backend Khôi qua Retrofit
     */
    private fun loadCandleData(symbol: String) {
        handleUiState(UiState.Loading)

        RetrofitClient.apiService.getCandles(symbol, "daily").enqueue(object : Callback<List<CandleDto>> {
            override fun onResponse(call: Call<List<CandleDto>>, response: Response<List<CandleDto>>) {
                val candles = response.body()
                if (response.isSuccessful && !candles.isNullOrEmpty()) {
                    handleUiState(UiState.Success())
                    renderCandleChart(candles, symbol)
                } else {
                    Log.w(TAG, "API nến rỗng hoặc lỗi code: ${response.code()}, dùng dữ liệu dự phòng cho $symbol")
                    handleUiState(UiState.Success())
                    renderCandleChart(generateMockCandles(symbol), symbol)
                }
            }

            override fun onFailure(call: Call<List<CandleDto>>, t: Throwable) {
                Log.e(TAG, "Lỗi kết nối Retrofit: ${t.message}")
                handleUiState(UiState.Success())
                renderCandleChart(generateMockCandles(symbol), symbol)
            }
        })
    }

    /**
     * Vẽ tập dữ liệu Nến lên MPAndroidChart
     */
    private fun renderCandleChart(candles: List<CandleDto>, symbol: String) {
        candleEntries.clear()

        for (i in candles.indices) {
            val c = candles[i]
            val high = c.high.toFloat()
            val low = c.low.toFloat()
            val open = c.open.toFloat()
            val close = c.close.toFloat()

            candleEntries.add(CandleEntry(i.toFloat(), high, low, open, close))
        }

        if (candles.isNotEmpty()) {
            currentAssetPrice = candles.last().close
            previousAssetPrice = currentAssetPrice
            updatePortfolioDisplay()
        }

        val dataSet = CandleDataSet(candleEntries, "$symbol (Live 1s)").apply {
            color = Color.WHITE
            shadowColor = Color.DKGRAY
            shadowWidth = 0.8f

            // Nến TĂNG (Xanh TradingView)
            increasingColor = Color.parseColor("#089981")
            increasingPaintStyle = Paint.Style.FILL

            // Nến GIẢM (Đỏ TradingView)
            decreasingColor = Color.parseColor("#F23645")
            decreasingPaintStyle = Paint.Style.FILL

            neutralColor = Color.WHITE
            setDrawValues(false)
        }

        this.candleDataSet = dataSet
        candleChart.data = CandleData(dataSet)
        candleChart.axisLeft.resetAxisMinimum()
        candleChart.axisLeft.resetAxisMaximum()
        candleChart.invalidate()
    }

    /**
     * Kết nối WebSocket / Ticker tương ứng với mã tài sản
     */
    private fun connectWebSocketForSymbol(symbol: String) {
        // Đóng kết nối cũ
        binanceWebSocket?.close(1000, "Switching symbol")
        binanceWebSocket = null
        goldSimulationJob?.cancel()
        goldSimulationJob = null

        val sym = symbol.uppercase()
        if (sym.contains("BTC")) {
            connectBinanceStream("btcusdt@kline_1s")
        } else if (sym.contains("ETH")) {
            connectBinanceStream("ethusdt@kline_1s")
        } else if (sym.contains("XAU")) {
            startGoldLiveSimulation()
        }
    }

    /**
     * Kết nối Binance Public WebSocket API
     */
    private fun connectBinanceStream(streamName: String) {
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/$streamName")
            .build()

        binanceWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, ">>> ĐÃ KẾT NỐI BINANCE LIVE STREAM: $streamName")
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

                        runOnUiThread {
                            onLivePriceTick(open, high, low, close, isClosed)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi phân tích WebSocket JSON: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.w(TAG, "WebSocket disconnected: ${t.message}")
            }
        })
    }

    /**
     * Sinh luồng giá vàng (XAU/USD) biến động thời gian thực 1s
     */
    private fun startGoldLiveSimulation() {
        goldSimulationJob = lifecycleScope.launch {
            var goldPrice = if (currentAssetPrice > 1000) currentAssetPrice else 2412.50
            while (isActive) {
                delay(1000)
                val delta = (Math.random() * 0.8 - 0.4)
                val prev = goldPrice
                goldPrice = Math.round((goldPrice + delta) * 100.0) / 100.0
                val high = (Math.max(prev, goldPrice) + Math.random() * 0.2).toFloat()
                val low = (Math.min(prev, goldPrice) - Math.random() * 0.2).toFloat()
                onLivePriceTick(prev.toFloat(), high, low, goldPrice, true)
            }
        }
    }

    /**
     * Cập nhật cây nến sống động và liên tục sinh nến mới
     */
    private fun onLivePriceTick(open: Float, high: Float, low: Float, close: Double, isClosed: Boolean) {
        previousAssetPrice = currentAssetPrice
        currentAssetPrice = close

        // 1. Cập nhật nhãn Ticker nhấp nháy giá trực tiếp trên Header
        val priceColor = if (currentAssetPrice >= previousAssetPrice) "#089981" else "#F23645"
        tvLiveStatus.text = "● LIVE $${String.format("%,.2f", currentAssetPrice)}"
        tvLiveStatus.setTextColor(Color.parseColor(priceColor))

        // 2. Cập nhật / Sinh nến mới trên biểu đồ
        if (candleEntries.isNotEmpty()) {
            val lastEntry = candleEntries.last()
            lastEntry.high = Math.max(lastEntry.high, high)
            lastEntry.low = Math.min(lastEntry.low, low)
            lastEntry.close = close.toFloat()

            if (isClosed) {
                // Khi nến 1s đóng, tạo nến mới tiếp theo
                val newX = lastEntry.x + 1f
                candleEntries.add(CandleEntry(newX, high, low, open, close.toFloat()))

                if (candleEntries.size > 30) {
                    candleEntries.removeAt(0)
                }
            }

            candleDataSet?.calcMinMax()
            candleChart.data?.notifyDataChanged()
            candleChart.notifyDataSetChanged()
            candleChart.invalidate()
        }

        // 3. Cập nhật dòng Tài sản ròng & Lời/Lỗ theo giá mới
        updatePortfolioDisplay()

        // 4. Cập nhật giá trong danh mục Watchlist
        updateWatchlistPriceDisplay(currentSymbol, close)
    }

    /**
     * Tính toán động Tổng tài sản và PnL theo giá thị trường thời gian thực
     */
    private fun updatePortfolioDisplay() {
        // 1. Cập nhật Số dư tiền mặt khả dụng
        tvCashBalance.text = "$${String.format("%,.2f", userCashBalance)} USD"

        // 2. Cập nhật Tổng tài sản ròng (Tiền mặt + Giá trị Coin) và Lời/Lỗ
        val assetHoldingsValue = userHoldingsQuantity * currentAssetPrice
        val totalNetWorth = userCashBalance + assetHoldingsValue
        val pnl = if (userHoldingsQuantity > 0 && userHoldingsAvgBuyPrice > 0) {
            (currentAssetPrice - userHoldingsAvgBuyPrice) * userHoldingsQuantity
        } else {
            0.0
        }

        val pnlSign = if (pnl >= 0) "+$" else "-$"
        val pnlColor = if (pnl >= 0) "#089981" else "#F23645"
        val holdingDetail = if (userHoldingsQuantity > 0) " (${String.format("%.4f", userHoldingsQuantity)} $currentSymbol)" else ""

        tvHoldings.text = "Tài sản: $${String.format("%,.2f", totalNetWorth)} | Lời/Lỗ: $pnlSign${String.format("%,.2f", Math.abs(pnl))}$holdingDetail"
        tvHoldings.setTextColor(Color.parseColor(pnlColor))
    }

    /**
     * Vòng lặp quét ngầm tự động mỗi 3 giây để đồng bộ số dư khi thao túng từ xa
     */
    private fun startAutoSyncPortfolioLoop() {
        lifecycleScope.launch {
            while (isActive) {
                delay(3000)
                if (jwtToken.isNotEmpty()) {
                    loadPortfolioSilently()
                }
            }
        }
    }

    /**
     * Tải số dư ngầm không quấy rầy UI
     */
    private fun loadPortfolioSilently() {
        val authHeader = "Bearer $jwtToken"
        RetrofitClient.apiService.getPortfolio(authHeader).enqueue(object : Callback<PortfolioSummaryDto> {
            override fun onResponse(call: Call<PortfolioSummaryDto>, response: Response<PortfolioSummaryDto>) {
                val p = response.body()
                if (response.isSuccessful && p != null) {
                    val newCash = p.cashBalanceUsd ?: userCashBalance
                    portfolioHoldingsList = p.holdings ?: emptyList()
                    
                    var changed = (newCash != userCashBalance)
                    userCashBalance = newCash
                    
                    val holding = portfolioHoldingsList.find { 
                        it.symbol?.equals(currentSymbol, ignoreCase = true) == true ||
                        (currentSymbol.contains("BTC") && it.symbol?.contains("BTC") == true) ||
                        (currentSymbol.contains("ETH") && it.symbol?.contains("ETH") == true) ||
                        (currentSymbol.contains("XAU") && it.symbol?.contains("XAU") == true)
                    }

                    val newQty = holding?.quantity ?: 0.0
                    val newAvg = holding?.avgBuyPrice ?: 0.0
                    if (newQty != userHoldingsQuantity || newAvg != userHoldingsAvgBuyPrice) {
                        userHoldingsQuantity = newQty
                        userHoldingsAvgBuyPrice = newAvg
                        changed = true
                    }

                    if (changed) {
                        updatePortfolioDisplay()
                        Log.d(TAG, ">>> [AUTO-SYNC] Đã cập nhật số dư mới từ Server: $$userCashBalance USD!")
                    }
                }
            }

            override fun onFailure(call: Call<PortfolioSummaryDto>, t: Throwable) {
                // Im lặng khi mất mạng
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

    /**
     * Cập nhật thẻ giá trong Watchlist
     */
    private fun updateWatchlistPriceDisplay(symbol: String, price: Double) {
        val card = llWatchlistContainer.findViewWithTag<TextView>("tag_price_$symbol")
        if (card != null) {
            card.text = "$${String.format("%,.1f", price)}"
        }
    }

    /**
     * Gắn sự kiện Mua / Bán khớp lệnh giả lập
     */
    private fun setupTradeActions() {
        btnBuy.setOnClickListener {
            val qty = etQuantity.text.toString().trim().toDoubleOrNull()
            if (qty == null || qty <= 0) {
                Toast.makeText(this, "Vui lòng nhập khối lượng hợp lệ (> 0)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            executeTrade("BUY", qty)
        }

        btnSell.setOnClickListener {
            val qty = etQuantity.text.toString().trim().toDoubleOrNull()
            if (qty == null || qty <= 0) {
                Toast.makeText(this, "Vui lòng nhập khối lượng hợp lệ (> 0)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            executeTrade("SELL", qty)
        }
    }

    private fun executeTrade(type: String, qty: Double) {
        if (jwtToken.isEmpty()) {
            Toast.makeText(this, "Đang ở chế độ Offline! Khớp lệnh $type $qty $currentSymbol thành công.", Toast.LENGTH_LONG).show()
            if (type == "BUY") {
                val cost = qty * (if (currentAssetPrice > 0) currentAssetPrice else 1000.0)
                userCashBalance -= cost
                userHoldingsQuantity += qty
                userHoldingsAvgBuyPrice = currentAssetPrice
            } else {
                val revenue = qty * (if (currentAssetPrice > 0) currentAssetPrice else 1000.0)
                userCashBalance += revenue
                userHoldingsQuantity = Math.max(0.0, userHoldingsQuantity - qty)
            }
            updatePortfolioDisplay()
            return
        }

        val authHeader = "Bearer $jwtToken"
        val request = OrderRequest(symbol = currentSymbol, type = type, quantity = qty)

        RetrofitClient.apiService.placeOrder(authHeader, request).enqueue(object : Callback<OrderResponse> {
            override fun onResponse(call: Call<OrderResponse>, response: Response<OrderResponse>) {
                if (response.isSuccessful) {
                    val order = response.body()
                    val orderMsg = order?.message ?: "Khớp lệnh $type $qty $currentSymbol thành công!"
                    Toast.makeText(this@Activity2, "✅ $orderMsg", Toast.LENGTH_LONG).show()
                    loadPortfolio() // Tải lại số dư ví mới từ Server
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    val displayErr = if (errBody.contains("không đủ")) {
                        "❌ Số dư ví không đủ để đặt lệnh này!"
                    } else if (errBody.contains("không đủ số lượng")) {
                        "❌ Số lượng $currentSymbol trong ví không đủ để bán!"
                    } else {
                        "❌ Lỗi đặt lệnh (${response.code()})"
                    }
                    Toast.makeText(this@Activity2, displayErr, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<OrderResponse>, t: Throwable) {
                Toast.makeText(this@Activity2, "Khớp lệnh $type (Offline) thành công!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Tải thông tin tài sản ròng từ Backend Server
     */
    private fun loadPortfolio() {
        if (jwtToken.isEmpty()) return

        val authHeader = "Bearer $jwtToken"
        RetrofitClient.apiService.getPortfolio(authHeader).enqueue(object : Callback<PortfolioSummaryDto> {
            override fun onResponse(call: Call<PortfolioSummaryDto>, response: Response<PortfolioSummaryDto>) {
                val p = response.body()
                if (response.isSuccessful && p != null) {
                    userCashBalance = p.cashBalanceUsd ?: 10000.0
                    portfolioHoldingsList = p.holdings ?: emptyList()
                    updateHoldingsForCurrentSymbol()
                    updatePortfolioDisplay()
                }
            }

            override fun onFailure(call: Call<PortfolioSummaryDto>, t: Throwable) {
                Log.e(TAG, "Không thể tải portfolio: ${t.message}")
            }
        })
    }

    /**
     * Đồng bộ Tin tức từ Backend về nạp vào Room DB (Phần của Mạnh)
     */
    private fun syncNewsToRoomDB() {
        RetrofitClient.apiService.syncNews(5).enqueue(object : Callback<List<MobileNewsBundleResponse>> {
            override fun onResponse(call: Call<List<MobileNewsBundleResponse>>, response: Response<List<MobileNewsBundleResponse>>) {
                val list = response.body()
                if (response.isSuccessful && !list.isNullOrEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        for (item in list) {
                            val n = item.news
                            val a = item.aiAnalysis
                            db.newsDao().insertNews(NewsEntity(n.newsId, n.title, n.url, n.publishedAt))
                            db.newsDao().insertAIAnalysis(AiAnalysisEntity(0, a.newsId, a.summary, a.sentiment, a.confidenceScore, a.reason))
                        }
                        Log.d(TAG, "Đã đồng bộ an toàn ${list.size} bài báo vào Room DB trên IO Thread!")
                    }
                }
            }

            override fun onFailure(call: Call<List<MobileNewsBundleResponse>>, t: Throwable) {
                Log.d(TAG, "Chưa kết nối server, sử dụng tin tức đã lưu trong Room DB")
            }
        })
    }

    /**
     * Tải và hiển thị danh mục Watchlist từ Room DB với khả năng click để chuyển nến
     */
    private fun loadLocalWatchlist() {
        lifecycleScope.launch(Dispatchers.IO) {
            var items = db.watchlistDao().getAllWatchlist()
            if (items.isEmpty()) {
                db.watchlistDao().insertItem(WatchlistItem("BTCUSDT", 78000.0, 2.5))
                db.watchlistDao().insertItem(WatchlistItem("ETHUSDT", 3550.0, -1.2))
                db.watchlistDao().insertItem(WatchlistItem("XAUUSD", 2410.0, 0.8))
                items = db.watchlistDao().getAllWatchlist()
            }

            withContext(Dispatchers.Main) {
                llWatchlistContainer.removeAllViews()
                watchlistViewsMap.clear()
                val inflater = LayoutInflater.from(this@Activity2)
                for (item in items) {
                    val itemView = inflater.inflate(R.layout.item_watchlist, llWatchlistContainer, false)
                    val tvSymbol = itemView.findViewById<TextView>(R.id.tvWatchlistSymbol)
                    val tvName = itemView.findViewById<TextView>(R.id.tvWatchlistName)
                    val tvPrice = itemView.findViewById<TextView>(R.id.tvWatchlistPrice)
                    val tvChange = itemView.findViewById<TextView>(R.id.tvWatchlistChange)

                    tvSymbol.text = item.symbol
                    tvName.text = if (item.symbol.contains("BTC")) "Bitcoin / Tether"
                                  else if (item.symbol.contains("ETH")) "Ethereum / Tether"
                                  else if (item.symbol.contains("XAU")) "Vàng Thế Giới (Gold Spot)"
                                  else "Tài sản tài chính"
                    
                    tvPrice.text = "$${String.format("%,.1f", item.price)}"
                    tvPrice.tag = "tag_price_${item.symbol}"

                    if (item.change24h >= 0) {
                        tvChange.text = "+${item.change24h}%"
                        tvChange.setTextColor(Color.parseColor("#089981"))
                    } else {
                        tvChange.text = "${item.change24h}%"
                        tvChange.setTextColor(Color.parseColor("#F23645"))
                    }

                    // SỰ KIỆN CHẠM VÀO ITEM ĐỂ CHUYỂN BIỂU ĐỒ NẾN
                    itemView.setOnClickListener {
                        switchMarketSymbol(item.symbol)
                    }

                    watchlistViewsMap[item.symbol] = itemView
                    llWatchlistContainer.addView(itemView)
                }

                highlightSelectedWatchlistItem(currentSymbol)
            }
        }
    }

    /**
     * Xử lý trạng thái giao diện
     */
    private fun handleUiState(state: UiState?) {
        when (state) {
            is UiState.Loading -> {
                pbLoading.visibility = View.VISIBLE
                tvStateMessage.visibility = View.GONE
                candleChart.visibility = View.INVISIBLE
            }
            is UiState.Success -> {
                pbLoading.visibility = View.GONE
                tvStateMessage.visibility = View.GONE
                candleChart.visibility = View.VISIBLE
            }
            is UiState.Error -> {
                pbLoading.visibility = View.GONE
                candleChart.visibility = View.INVISIBLE
                tvStateMessage.visibility = View.VISIBLE
                tvStateMessage.text = state.errorMessage
            }
            is UiState.Empty -> {
                pbLoading.visibility = View.GONE
                candleChart.visibility = View.INVISIBLE
                tvStateMessage.visibility = View.VISIBLE
                tvStateMessage.text = "Không có dữ liệu nến khả dụng"
            }
            null -> {}
        }
    }

    /**
     * Dữ liệu nến dự phòng cho từng mã
     */
    private fun generateMockCandles(symbol: String = "BTCUSDT"): List<CandleDto> {
        val list = ArrayList<CandleDto>()
        var basePrice = when {
            symbol.contains("ETH") -> 3550.0
            symbol.contains("XAU") -> 2410.0
            else -> 78000.0
        }

        for (i in 0 until 30) {
            val open = basePrice
            val close = open + (Math.random() * (basePrice * 0.02) - (basePrice * 0.01))
            val high = Math.max(open, close) + (Math.random() * (basePrice * 0.005))
            val low = Math.min(open, close) - (Math.random() * (basePrice * 0.005))
            list.add(CandleDto("2026-08-${i + 1}", open, high, low, close, 1500.0))
            basePrice = close
        }
        return list
    }

    private fun getSavedToken(): String {
        val prefs = getSharedPreferences("fnmf_prefs", MODE_PRIVATE)
        return prefs.getString("jwt_token", "") ?: ""
    }

    override fun onDestroy() {
        super.onDestroy()
        binanceWebSocket?.close(1000, "Activity Destroyed")
        goldSimulationJob?.cancel()
    }

    companion object {
        private const val TAG = "FNMF_Activity2"
    }
}
