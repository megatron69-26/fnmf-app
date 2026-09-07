package com.example.nhumonglenh.ui.watchlist

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.Activity2
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.WatchlistItem
import com.example.nhumonglenh.data.remote.MarketPriceDto
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class WatchlistFragment : Fragment() {

    private var rvWatchlist: RecyclerView? = null
    private var pbWatchlist: ProgressBar? = null
    private var tvEmptyWatchlist: TextView? = null
    private var tvOfflineNotice: TextView? = null
    private var tvWatchlistStatus: TextView? = null
    private var adapter: WatchlistAdapter? = null

    private var activeCall: Call<List<MarketPriceDto>>? = null
    private var lastFetchTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_watchlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvWatchlist = view.findViewById(R.id.rvWatchlist)
        pbWatchlist = view.findViewById(R.id.pbWatchlist)
        tvEmptyWatchlist = view.findViewById(R.id.tvEmptyWatchlist)
        tvOfflineNotice = view.findViewById(R.id.tvOfflineNotice)
        tvWatchlistStatus = view.findViewById(R.id.tvWatchlistStatus)

        setupRecyclerView()

        // Chỉ fetch mới nếu chưa có dữ liệu hoặc đã quá 30s kể từ lần tải trước
        if (lastFetchTime == 0L || System.currentTimeMillis() - lastFetchTime > 30_000L) {
            fetchLiveMarketPrices()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded && view != null) {
            // Khi người dùng chuyển tab quay lại Watchlist, chỉ fetch nếu quá 30 giây
            if (System.currentTimeMillis() - lastFetchTime > 30_000L) {
                fetchLiveMarketPrices()
            }
        }
    }

    override fun onDestroyView() {
        activeCall?.cancel()
        activeCall = null
        rvWatchlist = null
        pbWatchlist = null
        tvEmptyWatchlist = null
        tvOfflineNotice = null
        tvWatchlistStatus = null
        adapter = null
        super.onDestroyView()
    }

    private fun setupRecyclerView() {
        adapter = WatchlistAdapter(emptyList()) { selectedItem ->
            // Khi người dùng bấm vào một mã trong Watchlist -> chuyển về Trading chọn mã đó
            val act = activity as? Activity2
            if (act != null) {
                act.switchToTradingSymbol(selectedItem.symbol)
            } else {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Đã chọn ${selectedItem.symbol}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        rvWatchlist?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WatchlistFragment.adapter
            setHasFixedSize(false)
        }
    }

    private fun fetchLiveMarketPrices() {
        val ctx = context ?: return
        val appContext = ctx.applicationContext

        pbWatchlist?.visibility = View.VISIBLE
        tvEmptyWatchlist?.visibility = View.GONE
        tvOfflineNotice?.visibility = View.GONE

        val serverUrl = NetworkConfig.getServerUrl(appContext)
        RetrofitClient.updateBaseUrl(serverUrl)

        activeCall?.cancel()
        val call = RetrofitClient.apiService.getMarketPrices()
        activeCall = call

        call.enqueue(object : Callback<List<MarketPriceDto>> {
            override fun onResponse(
                call: Call<List<MarketPriceDto>>,
                response: Response<List<MarketPriceDto>>
            ) {
                if (call.isCanceled || !isAdded || view == null) return

                pbWatchlist?.visibility = View.GONE
                val body = response.body()
                if (response.isSuccessful && !body.isNullOrEmpty()) {
                    lastFetchTime = System.currentTimeMillis()
                    tvWatchlistStatus?.text = "● LIVE"
                    tvWatchlistStatus?.setTextColor(Color.parseColor("#089981"))
                    tvOfflineNotice?.visibility = View.GONE
                    tvEmptyWatchlist?.visibility = View.GONE

                    val uiItems = body.map { dto ->
                        WatchlistUiModel(
                            symbol = dto.symbol,
                            fullName = dto.name ?: getFriendlyName(dto.symbol),
                            price = dto.price,
                            changePercent = dto.change24h,
                            isOffline = false
                        )
                    }
                    adapter?.updateData(uiItems)

                    // Lưu cache ngầm vào Room DB trên IO dispatcher
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getInstance(appContext)
                            for (item in body) {
                                db.watchlistDao().insertItem(WatchlistItem(item.symbol, item.price, item.change24h))
                            }
                        } catch (_: Exception) {
                            // Bỏ qua lỗi DB nền
                        }
                    }
                } else {
                    loadFromRoomCache(appContext, "Không tải được giá trực tuyến (Mã lỗi: ${response.code()})")
                }
            }

            override fun onFailure(call: Call<List<MarketPriceDto>>, t: Throwable) {
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE
                loadFromRoomCache(appContext, "Không thể kết nối máy chủ: ${t.message}")
            }
        })
    }

    private fun loadFromRoomCache(appContext: Context, reason: String) {
        if (!isAdded || view == null) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val cachedList = try {
                val db = AppDatabase.getInstance(appContext)
                db.watchlistDao().getAllWatchlist()
            } catch (_: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext

                tvOfflineNotice?.visibility = View.VISIBLE
                tvOfflineNotice?.text = "⚠️ CHẾ ĐỘ OFFLINE: $reason"
                tvWatchlistStatus?.text = "● OFFLINE"
                tvWatchlistStatus?.setTextColor(Color.parseColor("#F9A825"))

                if (cachedList.isNotEmpty()) {
                    tvEmptyWatchlist?.visibility = View.GONE
                    val uiItems = cachedList.map { item ->
                        WatchlistUiModel(
                            symbol = item.symbol,
                            fullName = getFriendlyName(item.symbol),
                            price = item.price,
                            changePercent = item.change24h,
                            isOffline = true
                        )
                    }
                    adapter?.updateData(uiItems)
                } else {
                    tvEmptyWatchlist?.visibility = View.VISIBLE
                    tvEmptyWatchlist?.text = "Không có dữ liệu trong Room DB.\nVui lòng kết nối mạng và thử lại."
                    adapter?.updateData(emptyList())
                }
            }
        }
    }

    private fun getFriendlyName(symbol: String): String {
        return when {
            symbol.contains("BTC") -> "Bitcoin"
            symbol.contains("ETH") -> "Ethereum"
            symbol.contains("XAU") -> "Vàng Thế Giới (Gold Spot)"
            symbol.contains("OIL") -> "Dầu thô WTI"
            symbol.contains("BNB") -> "Binance Coin"
            symbol.contains("SOL") -> "Solana"
            else -> symbol
        }
    }
}
