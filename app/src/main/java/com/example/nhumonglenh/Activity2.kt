package com.example.nhumonglenh

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nhumonglenh.data.local.AiAnalysisEntity
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.NewsEntity
import com.example.nhumonglenh.data.local.WatchlistItem
import com.example.nhumonglenh.data.remote.CandleDto
import com.example.nhumonglenh.data.remote.MobileNewsBundleResponse
import com.example.nhumonglenh.data.remote.OrderRequest
import com.example.nhumonglenh.data.remote.OrderResponse
import com.example.nhumonglenh.data.remote.PortfolioSummaryDto
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.github.mikephil.charting.charts.CandleStickChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.CandleData
import com.github.mikephil.charting.data.CandleDataSet
import com.github.mikephil.charting.data.CandleEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

/**
 * =====================================================================
 * ACTIVITY 2 - MÀN HÌNH GIAO DỊCH CHÍNH (HYBRID ARCHITECTURE)
 * =====================================================================
 * 1. BINANCE WEBSOCKET: Truyền luồng nến sống & giá nhảy từng giây (1s Ticks)
 * 2. BACKEND KHÔI: Khớp lệnh Mua/Bán, tính toán số dư Oracle/H2 DB, AI Insights
 * 3. ROOM DB MẠNH: Lưu trữ danh mục theo dõi và tin tức ngoại tuyến
 * 4. REAL-TIME AUTO-SYNC LOOP: Tự động đồng bộ số dư mỗi 3 giây phục vụ Demo từ xa
 * =====================================================================
 */
class Activity2 : AppCompatActivity() {

    private lateinit var candleChart: CandleStickChart
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvStateMessage: TextView
    private lateinit var tvCashBalance: TextView
    private lateinit var tvHoldings: TextView
    private lateinit var tvLiveStatus: TextView
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

    // Quản lý trạng thái tài sản thời gian thực
    private var currentBtcPrice: Double = 0.0
    private var previousBtcPrice: Double = 0.0
    private var userCashBalance: Double = 10000.0
    private var userBtcQuantity: Double = 0.0
    private var userAvgBuyPrice: Double = 0.0

    // WebSocket Client
    private var binanceWebSocket: WebSocket? = null
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
        candleChart = findViewById(R.id.candleChart)
        pbLoading = findViewById(R.id.pbLoading)
        tvStateMessage = findViewById(R.id.tvStateMessage)
        tvCashBalance = findViewById(R.id.tvCashBalance)
        tvHoldings = findViewById(R.id.tvHoldings)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
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

        // 4. Tải dữ liệu Nến ban đầu từ Backend
        loadCandleData()

        // 5. Tải danh mục Watchlist & Tin tức Offline Room DB (Chạy trên Dispatchers.IO)
        loadLocalWatchlist()
        syncNewsToRoomDB()

        // 6. Cập nhật Số dư ví & Khớp lệnh Mua/Bán
        setupTradeActions()
        loadPortfolio()

        // 7. Kết nối luồng dữ liệu thời gian thực Binance WebSocket (1s kline)
        connectBinanceWebSocket()

        // 8. Tự động quét đồng bộ số dư ngầm mỗi 3 giây (Hỗ trợ demo thao túng từ xa)
        startAutoSyncPortfolioLoop()

        // 9. Cho phép chạm vào thẻ Tài sản để refresh tức thì
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
     * Tải dữ liệu nến từ Backend Khôi qua Retrofit
     */
    private fun loadCandleData() {
        handleUiState(UiState.Loading)

        RetrofitClient.apiService.getCandles("BTCUSDT", "daily").enqueue(object : Callback<List<CandleDto>> {
            override fun onResponse(call: Call<List<CandleDto>>, response: Response<List<CandleDto>>) {
                val candles = response.body()
                if (response.isSuccessful && !candles.isNullOrEmpty()) {
                    handleUiState(UiState.Success())
                    renderCandleChart(candles)
                } else {
                    Log.w(TAG, "API nến rỗng hoặc lỗi code: ${response.code()}, dùng dữ liệu dự phòng")
                    handleUiState(UiState.Success())
                    renderCandleChart(generateMockCandles())
                }
            }

            override fun onFailure(call: Call<List<CandleDto>>, t: Throwable) {
                Log.e(TAG, "Lỗi kết nối Retrofit: ${t.message}")
                handleUiState(UiState.Success())
                renderCandleChart(generateMockCandles())
            }
        })
    }

    /**
     * Vẽ tập dữ liệu Nến lên MPAndroidChart
     */
    private fun renderCandleChart(candles: List<CandleDto>) {
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
            currentBtcPrice = candles.last().close
            previousBtcPrice = currentBtcPrice
            updatePortfolioDisplay()
        }

        val dataSet = CandleDataSet(candleEntries, "BTC / USDT (Live 1s)").apply {
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
     * Kết nối Binance Public WebSocket API để nhận từng biến động giá 1s
     */
    private fun connectBinanceWebSocket() {
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/btcusdt@kline_1s")
            .build()

        binanceWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, ">>> ĐÃ KẾT NỐI THÀNH CÔNG BINANCE LIVE WEBSOCKET!")
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
                Log.w(TAG, "WebSocket disconnected: ${t.message}, sẽ tự động kết nối lại khi mở app.")
            }
        })
    }

    /**
     * Cập nhật cây nến sống động và liên tục sinh nến mới
     */
    private fun onLivePriceTick(open: Float, high: Float, low: Float, close: Double, isClosed: Boolean) {
        previousBtcPrice = currentBtcPrice
        currentBtcPrice = close

        // 1. Cập nhật nhãn Ticker nhấp nháy giá trực tiếp trên Header
        val priceColor = if (currentBtcPrice >= previousBtcPrice) "#089981" else "#F23645"
        tvLiveStatus.text = "● LIVE $${String.format("%,.2f", currentBtcPrice)}"
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

        // 4. Cập nhật giá BTC trong danh mục Watchlist
        updateWatchlistBtcPrice(close)
    }

    /**
     * Tính toán động Tổng tài sản và PnL theo giá thị trường thời gian thực
     */
    private fun updatePortfolioDisplay() {
        // 1. Cập nhật Số dư tiền mặt khả dụng
        tvCashBalance.text = "$${String.format("%,.2f", userCashBalance)} USD"

        // 2. Cập nhật Tổng tài sản ròng (Tiền mặt + Giá trị Coin) và Lời/Lỗ
        val btcHoldingsValue = userBtcQuantity * currentBtcPrice
        val totalNetWorth = userCashBalance + btcHoldingsValue
        val pnl = if (userBtcQuantity > 0 && userAvgBuyPrice > 0) {
            (currentBtcPrice - userAvgBuyPrice) * userBtcQuantity
        } else {
            0.0
        }

        val pnlSign = if (pnl >= 0) "+$" else "-$"
        val pnlColor = if (pnl >= 0) "#089981" else "#F23645"
        val holdingDetail = if (userBtcQuantity > 0) " (${String.format("%.4f", userBtcQuantity)} BTC)" else ""

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
                    val btcHolding = p.holdings?.find { it.symbol?.contains("BTC") == true }
                    val newBtcQty = btcHolding?.quantity ?: 0.0
                    val newAvgPrice = btcHolding?.avgBuyPrice ?: 0.0

                    if (newCash != userCashBalance || newBtcQty != userBtcQuantity || newAvgPrice != userAvgBuyPrice) {
                        userCashBalance = newCash
                        userBtcQuantity = newBtcQty
                        userAvgBuyPrice = newAvgPrice
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

    /**
     * Cập nhật thẻ BTCUSDT trong Watchlist theo giá WebSocket
     */
    private fun updateWatchlistBtcPrice(price: Double) {
        val btcCard = llWatchlistContainer.findViewWithTag<TextView>("tag_price_BTCUSDT")
        if (btcCard != null) {
            btcCard.text = "$${String.format("%,.1f", price)}"
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
            Toast.makeText(this, "Đang ở chế độ Offline! Khớp lệnh $type $qty BTC thành công.", Toast.LENGTH_LONG).show()
            if (type == "BUY") {
                val cost = qty * (if (currentBtcPrice > 0) currentBtcPrice else 78000.0)
                userCashBalance -= cost
                userBtcQuantity += qty
                userAvgBuyPrice = currentBtcPrice
            } else {
                val revenue = qty * (if (currentBtcPrice > 0) currentBtcPrice else 78000.0)
                userCashBalance += revenue
                userBtcQuantity = Math.max(0.0, userBtcQuantity - qty)
            }
            updatePortfolioDisplay()
            return
        }

        val authHeader = "Bearer $jwtToken"
        val request = OrderRequest(symbol = "BTCUSDT", type = type, quantity = qty)

        RetrofitClient.apiService.placeOrder(authHeader, request).enqueue(object : Callback<OrderResponse> {
            override fun onResponse(call: Call<OrderResponse>, response: Response<OrderResponse>) {
                if (response.isSuccessful) {
                    val order = response.body()
                    val orderMsg = order?.message ?: "Khớp lệnh $type $qty BTC thành công!"
                    Toast.makeText(this@Activity2, "✅ $orderMsg", Toast.LENGTH_LONG).show()
                    loadPortfolio() // Tải lại số dư ví mới từ Server
                } else {
                    val errBody = response.errorBody()?.string() ?: ""
                    val displayErr = if (errBody.contains("không đủ")) {
                        "❌ Số dư ví không đủ để đặt lệnh này!"
                    } else if (errBody.contains("không đủ số lượng")) {
                        "❌ Số lượng BTC trong ví không đủ để bán!"
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
                    
                    // Tìm số lượng BTC đang sở hữu
                    val btcHolding = p.holdings?.find { it.symbol?.contains("BTC") == true }
                    if (btcHolding != null) {
                        userBtcQuantity = btcHolding.quantity ?: 0.0
                        userAvgBuyPrice = btcHolding.avgBuyPrice ?: 0.0
                    } else {
                        userBtcQuantity = 0.0
                        userAvgBuyPrice = 0.0
                    }
                    
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
     * Tải và hiển thị danh mục Watchlist từ Room DB
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
                    if (item.symbol.contains("BTC")) {
                        tvPrice.tag = "tag_price_BTCUSDT"
                    }

                    if (item.change24h >= 0) {
                        tvChange.text = "+${item.change24h}%"
                        tvChange.setTextColor(Color.parseColor("#089981"))
                    } else {
                        tvChange.text = "${item.change24h}%"
                        tvChange.setTextColor(Color.parseColor("#F23645"))
                    }

                    llWatchlistContainer.addView(itemView)
                }
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
                tvStateMessage.text = "Không có dữ liệu hiển thị"
            }
            null -> {}
        }
    }

    private fun getSavedToken(): String {
        val prefs = getSharedPreferences("fnmf_prefs", Context.MODE_PRIVATE)
        return prefs.getString("jwt_token", "") ?: ""
    }

    private fun generateMockCandles(): List<CandleDto> {
        val list = ArrayList<CandleDto>()
        var price = 78000.0
        for (i in 1..30) {
            val open = price
            val close = open + (Math.random() - 0.48) * 300
            val high = Math.max(open, close) + Math.random() * 150
            val low = Math.min(open, close) - Math.random() * 150
            list.add(CandleDto("2026-08-$i", open, high, low, close, 1500.0))
            price = close
        }
        return list
    }

    override fun onDestroy() {
        super.onDestroy()
        binanceWebSocket?.close(1000, "Activity Destroyed")
        Log.d(TAG, "Đã đóng WebSocket kết nối an toàn.")
    }

    companion object {
        private const val TAG = "Activity2_Trading"
    }
}
