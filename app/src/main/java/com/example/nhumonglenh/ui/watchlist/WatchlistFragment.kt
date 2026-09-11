package com.example.nhumonglenh.ui.watchlist

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nhumonglenh.Activity2
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.WatchlistItem
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.NetworkConfig
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.remote.WatchlistItemDto
import com.example.nhumonglenh.data.remote.WatchlistRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

/**
 * WatchlistFragment — Quản lý danh mục theo dõi Cloud CRUD (GET / POST / DELETE /api/watchlist)
 * Tách biệt bộ nhớ đệm Room DB theo từng tài khoản (userEmail).
 * Không tạo dữ liệu giả, không báo thành công giả khi ngoại tuyến.
 */
class WatchlistFragment : Fragment() {

    private var rvWatchlist: RecyclerView? = null
    private var pbWatchlist: ProgressBar? = null
    private var tvEmptyWatchlist: TextView? = null
    private var tvOfflineNotice: TextView? = null
    private var tvWatchlistStatus: TextView? = null
    private var btnAddWatchlist: Button? = null
    private var adapter: WatchlistAdapter? = null

    private var activeWatchlistCall: Call<List<WatchlistItemDto>>? = null
    private var activeAddCall: Call<WatchlistItemDto>? = null
    private var activeDeleteCall: Call<Map<String, String>>? = null
    private var lastFetchTime = 0L
    private var isCurrentlyOffline = false

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
        btnAddWatchlist = view.findViewById(R.id.btnAddWatchlist)

        setupRecyclerView()

        btnAddWatchlist?.setOnClickListener {
            showAddSymbolDialog()
        }

        // Tải danh mục Cloud nếu chưa có hoặc quá 30 giây
        if (lastFetchTime == 0L || System.currentTimeMillis() - lastFetchTime > 30_000L) {
            fetchCloudWatchlist()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded && view != null) {
            if (System.currentTimeMillis() - lastFetchTime > 30_000L) {
                fetchCloudWatchlist()
            }
        }
    }

    override fun onDestroyView() {
        activeWatchlistCall?.cancel()
        activeWatchlistCall = null
        activeAddCall?.cancel()
        activeAddCall = null
        activeDeleteCall?.cancel()
        activeDeleteCall = null

        rvWatchlist = null
        pbWatchlist = null
        tvEmptyWatchlist = null
        tvOfflineNotice = null
        tvWatchlistStatus = null
        btnAddWatchlist = null
        adapter = null
        super.onDestroyView()
    }

    private fun setupRecyclerView() {
        adapter = WatchlistAdapter(
            items = emptyList(),
            onClick = { selectedItem ->
                val act = activity as? Activity2
                if (act != null) {
                    act.switchToTradingSymbol(selectedItem.symbol)
                } else {
                    context?.let { ctx ->
                        Toast.makeText(ctx, "Đã chọn " + selectedItem.symbol, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onLongClick = { selectedItem ->
                showDeleteConfirmationDialog(selectedItem)
            }
        )
        rvWatchlist?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WatchlistFragment.adapter
            setHasFixedSize(false)
        }
    }

    /**
     * Tải danh mục theo dõi từ Cloud Backend (GET /api/watchlist) kèm xác thực Bearer Token
     */
    private fun fetchCloudWatchlist() {
        val ctx = context ?: return
        val appContext = ctx.applicationContext
        val authHeader = AuthSessionManager.getAuthHeader(appContext)
        val userEmail = AuthSessionManager.getUserEmail(appContext).lowercase(Locale.ROOT)

        if (authHeader == null || userEmail.isBlank()) {
            tvOfflineNotice?.visibility = View.GONE
            tvWatchlistStatus?.text = "CHƯA ĐĂNG NHẬP"
            tvWatchlistStatus?.setTextColor(Color.parseColor("#888888"))
            tvEmptyWatchlist?.visibility = View.VISIBLE
            tvEmptyWatchlist?.text = "Vui lòng đăng nhập để xem danh mục theo dõi cá nhân."
            adapter?.updateData(emptyList())
            return
        }

        pbWatchlist?.visibility = View.VISIBLE
        tvEmptyWatchlist?.visibility = View.GONE
        tvOfflineNotice?.visibility = View.GONE

        val serverUrl = NetworkConfig.getServerUrl(appContext)
        RetrofitClient.updateBaseUrl(serverUrl)

        activeWatchlistCall?.cancel()
        val call = RetrofitClient.apiService.getWatchlist(authHeader)
        activeWatchlistCall = call

        call.enqueue(object : Callback<List<WatchlistItemDto>> {
            override fun onResponse(
                call: Call<List<WatchlistItemDto>>,
                response: Response<List<WatchlistItemDto>>
            ) {
                activeWatchlistCall = null
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE

                if (response.code() == 401 || response.code() == 403) {
                    AuthSessionManager.handleUnauthorized(activity)
                    return
                }

                val body = response.body()
                val syncDecision = WatchlistRoomSyncPolicy.evaluate(
                    statusCode = response.code(),
                    isSuccessful = response.isSuccessful,
                    itemCount = body?.size
                )

                if (syncDecision.action == WatchlistRoomSyncPolicy.Action.SYNC_ROOM) {
                    lastFetchTime = System.currentTimeMillis()
                    isCurrentlyOffline = false
                    tvWatchlistStatus?.text = "TRỰC TIẾP"
                    tvWatchlistStatus?.setTextColor(Color.parseColor("#089981"))
                    tvOfflineNotice?.visibility = View.GONE

                    val items = body ?: emptyList()
                    if (items.isEmpty()) {
                        tvEmptyWatchlist?.visibility = View.VISIBLE
                        tvEmptyWatchlist?.text = "Danh mục theo dõi trống.\nBấm '+ Thêm' để theo dõi mã tài sản."
                        adapter?.updateData(emptyList())
                    } else {
                        tvEmptyWatchlist?.visibility = View.GONE
                        val uiItems = items.map { dto ->
                            WatchlistUiModel(
                                symbol = dto.symbol,
                                fullName = dto.name ?: getFriendlyName(dto.symbol),
                                price = dto.currentPrice,
                                changePercent = dto.change24h,
                                isOffline = false
                            )
                        }
                        adapter?.updateData(uiItems)
                    }

                    // Đồng bộ Room DB theo kết quả server (kể cả danh sách rỗng [] để tránh Watchlist ma khi xóa trên máy khác)
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getInstance(appContext)
                            val itemsToCache = items.map { item ->
                                WatchlistItem(
                                    item.symbol,
                                    item.currentPrice,
                                    item.change24h,
                                    userEmail
                                )
                            }
                            db.watchlistDao().clearAndInsertAll(userEmail, itemsToCache)
                        } catch (e: Exception) {
                            android.util.Log.w("WatchlistFragment", "Không thể ghi cache Room DB: " + e.message)
                        }
                    }
                } else {
                    loadFromRoomCache(appContext, userEmail, syncDecision.reason)
                }
            }

            override fun onFailure(call: Call<List<WatchlistItemDto>>, t: Throwable) {
                activeWatchlistCall = null
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE
                val networkDecision = WatchlistRoomSyncPolicy.evaluateNetworkFailure(t)
                loadFromRoomCache(appContext, userEmail, networkDecision.reason)
            }
        })
    }

    /**
     * Tải danh mục theo dõi từ Room Database nội bộ khi mất mạng (chế độ OFFLINE)
     */
    private fun loadFromRoomCache(appContext: Context, userEmail: String, reason: String) {
        if (!isAdded || view == null) return
        isCurrentlyOffline = true

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val cachedList = try {
                val db = AppDatabase.getInstance(appContext)
                if (userEmail.isNotBlank()) {
                    db.watchlistDao().getWatchlistByUser(userEmail)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.w("WatchlistFragment", "Không thể đọc cache Room DB: " + e.message)
                emptyList()
            }

            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext

                tvOfflineNotice?.visibility = View.VISIBLE
                tvOfflineNotice?.text = "CHẾ ĐỘ NGOẠI TUYẾN: Đang hiển thị dữ liệu đã lưu trên thiết bị"
                tvWatchlistStatus?.text = "NGOẠI TUYẾN"
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
                    tvEmptyWatchlist?.text = "Không có dữ liệu đã lưu trên thiết bị cho tài khoản này.\\nVui lòng kết nối mạng và thử lại."
                    adapter?.updateData(emptyList())
                }
            }
        }
    }

    /**
     * Mở Dialog thêm mã hợp lệ vào Watchlist (POST /api/watchlist)
     */
    private fun showAddSymbolDialog() {
        val ctx = context ?: return
        val appContext = ctx.applicationContext

        if (isCurrentlyOffline) {
            Toast.makeText(ctx, "Đang ngoại tuyến, không thể thêm mã mới lên máy chủ", Toast.LENGTH_SHORT).show()
            return
        }

        val authHeader = AuthSessionManager.getAuthHeader(appContext)
        if (authHeader == null) {
            Toast.makeText(ctx, "Vui lòng đăng nhập để thêm mã theo dõi", Toast.LENGTH_SHORT).show()
            return
        }

        // Danh sách các mã hợp lệ được hệ thống hỗ trợ (Loại bỏ USOIL vì chưa có nguồn WTI thật)
        val supportedSymbols = listOf("BTCUSDT", "ETHUSDT", "XAUUSD")
        val currentSymbols = adapter?.getItems()?.map { it.symbol }?.toSet() ?: emptySet()
        val availableSymbols = supportedSymbols.filter { !currentSymbols.contains(it) }

        if (availableSymbols.isEmpty()) {
            Toast.makeText(ctx, "Tất cả mã khả dụng đã có trong danh sách theo dõi", Toast.LENGTH_SHORT).show()
            return
        }

        val displayNames = availableSymbols.map { it + " — " + getFriendlyName(it) }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("Thêm mã theo dõi")
            .setItems(displayNames) { _, which ->
                val selectedSymbol = availableSymbols[which]
                performAddToWatchlist(authHeader, selectedSymbol)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun performAddToWatchlist(authHeader: String, symbol: String) {
        val ctx = context ?: return

        pbWatchlist?.visibility = View.VISIBLE
        activeAddCall?.cancel()
        val call = RetrofitClient.apiService.addToWatchlist(authHeader, WatchlistRequest(symbol))
        activeAddCall = call

        call.enqueue(object : Callback<WatchlistItemDto> {
            override fun onResponse(call: Call<WatchlistItemDto>, response: Response<WatchlistItemDto>) {
                activeAddCall = null
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE

                if (response.code() == 401 || response.code() == 403) {
                    AuthSessionManager.handleUnauthorized(activity)
                    return
                }

                if (response.isSuccessful) {
                    Toast.makeText(ctx, "Đã thêm " + symbol + " vào danh sách theo dõi", Toast.LENGTH_SHORT).show()
                    fetchCloudWatchlist()
                } else if (response.code() == 400) {
                    Toast.makeText(ctx, "Mã " + symbol + " đã có trong danh sách theo dõi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "Không thể thêm " + symbol + " (Mã lỗi: " + response.code() + ")", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<WatchlistItemDto>, t: Throwable) {
                activeAddCall = null
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE
                Toast.makeText(ctx, "Lỗi kết nối khi thêm: " + t.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Mở Dialog xác nhận xóa mã khỏi Watchlist (DELETE /api/watchlist/{symbol})
     */
    private fun showDeleteConfirmationDialog(item: WatchlistUiModel) {
        val ctx = context ?: return
        val appContext = ctx.applicationContext

        if (isCurrentlyOffline) {
            Toast.makeText(ctx, "Đang ngoại tuyến, không thể xóa mã trên máy chủ", Toast.LENGTH_SHORT).show()
            return
        }

        val authHeader = AuthSessionManager.getAuthHeader(appContext)
        val userEmail = AuthSessionManager.getUserEmail(appContext).lowercase(Locale.ROOT)
        if (authHeader == null) {
            Toast.makeText(ctx, "Vui lòng đăng nhập để thao tác", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(ctx)
            .setTitle("Xóa mã theo dõi")
            .setMessage("Bạn có chắc chắn muốn xóa " + item.symbol + " khỏi danh mục theo dõi?")
            .setPositiveButton("Xóa") { _, _ ->
                performRemoveFromWatchlist(authHeader, userEmail, item.symbol)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun performRemoveFromWatchlist(authHeader: String, userEmail: String, symbol: String) {
        val ctx = context ?: return
        val appContext = ctx.applicationContext

        pbWatchlist?.visibility = View.VISIBLE
        activeDeleteCall?.cancel()
        val call = RetrofitClient.apiService.removeFromWatchlist(authHeader, symbol)
        activeDeleteCall = call

        call.enqueue(object : Callback<Map<String, String>> {
            override fun onResponse(call: Call<Map<String, String>>, response: Response<Map<String, String>>) {
                activeDeleteCall = null
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE

                if (response.code() == 401 || response.code() == 403) {
                    AuthSessionManager.handleUnauthorized(activity)
                    return
                }

                if (response.isSuccessful) {
                    Toast.makeText(ctx, "Đã xóa " + symbol + " khỏi danh sách theo dõi", Toast.LENGTH_SHORT).show()
                    // Xóa khỏi Room DB
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            AppDatabase.getInstance(appContext).watchlistDao().deleteByUserAndSymbol(userEmail, symbol)
                        } catch (e: Exception) {
                            android.util.Log.w("WatchlistFragment", "Không thể xóa khỏi Room DB: " + e.message)
                        }
                    }
                    fetchCloudWatchlist()
                } else {
                    Toast.makeText(ctx, "Không thể xóa " + symbol + " (Mã lỗi: " + response.code() + ")", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<Map<String, String>>, t: Throwable) {
                activeDeleteCall = null
                if (call.isCanceled || !isAdded || view == null) return
                pbWatchlist?.visibility = View.GONE
                Toast.makeText(ctx, "Lỗi kết nối khi xóa: " + t.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun getFriendlyName(symbol: String): String {
        return when {
            symbol.contains("BTC") -> "Bitcoin"
            symbol.contains("ETH") -> "Ethereum"
            symbol.contains("XAU") -> "Vàng giao ngay thế giới"
            else -> symbol
        }
    }
}
