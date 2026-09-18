package com.example.nhumonglenh

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.remote.ForecastRefreshRequest
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.data.repository.ForecastRepository
import com.example.nhumonglenh.data.repository.RefreshQuotaManager
import com.example.nhumonglenh.ui.UiTextLocalizer
import com.example.nhumonglenh.ui.common.FiniLoadingView
import com.example.nhumonglenh.ui.forecast.ForecastDateTimeFormatter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ForecastFragment : Fragment() {

    companion object {
        const val FORECAST_CYCLE_MS = 24 * 60 * 60 * 1000L
    }

    private var swipeRefreshForecast: SwipeRefreshLayout? = null
    private var scrollForecast: ScrollView? = null
    private var tvForecastQuota: TextView? = null
    private var tvForecastTitle: TextView? = null
    private var tvForecastCountdown: TextView? = null
    private var btnForecastRefresh: MaterialButton? = null
    private var tvForecastSymbol: TextView? = null
    private var tvForecastTimestamp: TextView? = null
    private var tvResult: TextView? = null
    private var finiLoadingForecast: FiniLoadingView? = null
    private var finiInlineForecast: FiniLoadingView? = null
    private var llContent: LinearLayout? = null
    private var tvRecommendation: TextView? = null
    private var tvConfidence: TextView? = null
    private var tvSupport: TextView? = null
    private var tvResistance: TextView? = null
    private var tvTechOutlook: TextView? = null
    private var tvFundOutlook: TextView? = null
    private var tvKeyDrivers: TextView? = null
    private var tvForecastStaleWarning: TextView? = null

    private var activeCall: Call<ForecastResponse>? = null
    private var activeRefreshCall: Call<ForecastResponse>? = null
    private val isRefreshingInProgress = AtomicBoolean(false)
    private val isAutoRevalidationInProgress = AtomicBoolean(false)
    private var currentLoadGeneration: Long = 0L
    private var countdownJob: Job? = null

    private var currentSymbol: String = "MARKET"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_forecast, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefreshForecast = view.findViewById(R.id.swipeRefreshForecast)
        scrollForecast = view.findViewById(R.id.scrollForecast)
        tvForecastQuota = view.findViewById(R.id.tvForecastQuota)
        tvForecastTitle = view.findViewById(R.id.tvForecastTitle)
        tvForecastCountdown = view.findViewById(R.id.tvForecastCountdown)
        btnForecastRefresh = view.findViewById(R.id.btnForecastRefresh)
        tvForecastSymbol = view.findViewById(R.id.tvForecastSymbol)
        tvResult = view.findViewById(R.id.tvForecastResult)
        finiLoadingForecast = view.findViewById(R.id.finiLoadingForecast)
        finiInlineForecast = view.findViewById(R.id.finiInlineForecast)
        llContent = view.findViewById(R.id.llForecastContent)
        tvForecastTimestamp = view.findViewById(R.id.tvForecastTimestamp)
        tvForecastStaleWarning = view.findViewById(R.id.tvForecastStaleWarning)
        tvRecommendation = view.findViewById(R.id.tvRecommendation)
        tvConfidence = view.findViewById(R.id.tvConfidence)
        tvSupport = view.findViewById(R.id.tvSupport)
        tvResistance = view.findViewById(R.id.tvResistance)
        tvTechOutlook = view.findViewById(R.id.tvTechOutlook)
        tvFundOutlook = view.findViewById(R.id.tvFundOutlook)
        tvKeyDrivers = view.findViewById(R.id.tvKeyDrivers)

        setupPullToRefresh()
        setupRefreshButton()
        updateQuotaLabel(RefreshQuotaManager.getRemaining())
        syncQuotaInBackground()

        startCountdownTimer()
        loadForecast("MARKET")
    }

    private fun setupRefreshButton() {
        btnForecastRefresh?.setOnClickListener {
            performManualRefresh()
        }
    }

    private fun performManualRefresh() {
        btnForecastRefresh?.isEnabled = false
        performPullToRefresh()
    }

    private fun setupPullToRefresh() {
        swipeRefreshForecast?.setColorSchemeColors(Color.parseColor("#2962FF"))
        swipeRefreshForecast?.setOnChildScrollUpCallback { _, _ ->
            scrollForecast?.canScrollVertically(-1) == true
        }
        swipeRefreshForecast?.setOnRefreshListener {
            performPullToRefresh()
        }
    }

    private fun performPullToRefresh() {
        val context = context ?: return

        // Khóa chống thao tác kép khi đang có request làm mới chạy
        if (!isRefreshingInProgress.compareAndSet(false, true)) {
            swipeRefreshForecast?.isRefreshing = false
            return
        }

        val authHeader = AuthSessionManager.getAuthHeader(context)
        if (authHeader.isNullOrBlank()) {
            isRefreshingInProgress.set(false)
            swipeRefreshForecast?.isRefreshing = false
            btnForecastRefresh?.isEnabled = true
            Toast.makeText(context, getString(R.string.refresh_auth_required), Toast.LENGTH_SHORT).show()
            return
        }

        finiInlineForecast?.show()
        btnForecastRefresh?.isEnabled = false
        swipeRefreshForecast?.isRefreshing = false

        val clientRequestId = UUID.randomUUID().toString()
        val request = ForecastRefreshRequest(
            symbol = "MARKET",
            timeframe = "24H_7D",
            clientRequestId = clientRequestId
        )

        activeCall?.cancel()
        isAutoRevalidationInProgress.set(false)
        activeRefreshCall?.cancel()
        val call = RetrofitClient.apiService.refreshMarketForecast(authHeader, clientRequestId, request)
        activeRefreshCall = call

        call.enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(call: Call<ForecastResponse>, response: Response<ForecastResponse>) {
                if (call.isCanceled || !isAdded || view == null) return
                isRefreshingInProgress.set(false)
                swipeRefreshForecast?.isRefreshing = false
                btnForecastRefresh?.isEnabled = true
                finiInlineForecast?.hide()

                when {
                    response.code() == 401 || response.code() == 403 -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.session_expired_msg),
                            Toast.LENGTH_SHORT
                        ).show()
                        AuthSessionManager.handleUnauthorized(activity)
                    }
                    response.code() == 429 -> {
                        RefreshQuotaManager.setRemaining(0)
                        updateQuotaLabel(0)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.refresh_quota_exhausted),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    response.isSuccessful && response.body() != null -> {
                        val forecast = response.body()!!
                        val repo = ForecastRepository.getInstance(context)
                        if (forecast.stale == true) {
                            repo.recordAutoAttemptFailure("MARKET")
                        } else {
                            repo.recordAutoAttemptSuccess("MARKET")
                        }
                        forecast.remainingRefreshes?.let {
                            RefreshQuotaManager.setRemaining(it)
                            updateQuotaLabel(it)
                        }
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            repo.saveForecast(forecast)
                        }
                        displayForecastData(forecast)
                        startCountdownTimer()
                    }
                    response.code() == 503 -> {
                        ForecastRepository.getInstance(context).recordAutoAttemptFailure("MARKET")
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.refresh_forecast_error),
                            Toast.LENGTH_SHORT
                        ).show()

                        // Đọc remainingRefreshes từ body lỗi nếu backend có gửi kèm
                        runCatching {
                            val errStr = response.errorBody()?.string()
                            if (!errStr.isNullOrBlank()) {
                                val json = JSONObject(errStr)
                                if (json.has("remainingRefreshes")) {
                                    val rem = json.getInt("remainingRefreshes")
                                    RefreshQuotaManager.setRemaining(rem)
                                    updateQuotaLabel(rem)
                                }
                            }
                        }
                    }
                    else -> {
                        ForecastRepository.getInstance(context).recordAutoAttemptFailure("MARKET")
                        val errorMsg = when (response.code()) {
                            422 -> getString(R.string.refresh_forecast_unsupported, currentSymbol)
                            else -> getString(R.string.refresh_forecast_error)
                        }
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()

                        // Đọc remainingRefreshes từ body lỗi nếu backend có gửi kèm
                        runCatching {
                            val errStr = response.errorBody()?.string()
                            if (!errStr.isNullOrBlank()) {
                                val json = JSONObject(errStr)
                                if (json.has("remainingRefreshes")) {
                                    val rem = json.getInt("remainingRefreshes")
                                    RefreshQuotaManager.setRemaining(rem)
                                    updateQuotaLabel(rem)
                                }
                            }
                        }
                    }
                }
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                isRefreshingInProgress.set(false)
                swipeRefreshForecast?.isRefreshing = false
                btnForecastRefresh?.isEnabled = true
                finiInlineForecast?.hide()
                if (call.isCanceled || !isAdded || view == null) return
                context?.let { ForecastRepository.getInstance(it).recordAutoAttemptFailure("MARKET") }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.refresh_network_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            val appContext = context?.applicationContext ?: return@launch
            val repo = ForecastRepository.getInstance(appContext)

            while (isActive) {
                val now = System.currentTimeMillis()
                val nextAttempt = repo.getOrInitNextAutoAttemptAt("MARKET", now)
                val remainingMs = (nextAttempt - now).coerceAtLeast(0L)

                val hours = (remainingMs / 1000) / 3600
                val minutes = ((remainingMs / 1000) % 3600) / 60
                val seconds = (remainingMs / 1000) % 60

                val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                tvForecastCountdown?.text = getString(R.string.auto_update_countdown, timeStr)

                if (remainingMs <= 0L) {
                    if (isAutoRevalidationInProgress.compareAndSet(false, true)) {
                        // Tự động revalidation qua GET (cache-first), tuyệt đối KHÔNG gọi POST refresh tiêu hao quota
                        loadForecast("MARKET", isAutoRefresh = true)
                    }
                }

                delay(1000L)
            }
        }
    }

    private fun displayForecastData(forecast: ForecastResponse) {
        llContent?.visibility = View.VISIBLE
        tvResult?.visibility = View.GONE

        // Hiển thị ngày/giờ nhận định thực tế từ backend (Asia/Ho_Chi_Minh)
        val timestampText = ForecastDateTimeFormatter.formatForecastTimestamp(forecast.createdAt, requireContext())
        tvForecastTimestamp?.text = timestampText
        tvForecastTimestamp?.visibility = View.VISIBLE

        if (forecast.stale == true) {
            tvForecastStaleWarning?.visibility = View.VISIBLE
        } else {
            tvForecastStaleWarning?.visibility = View.GONE
        }

        val recommendationCode = forecast.recommendation?.trim()?.uppercase(Locale.ROOT)
        val recText = UiTextLocalizer.recommendation(forecast.recommendation)
        tvRecommendation?.text = if (!recText.isNullOrBlank()) recText else "—"

        tvConfidence?.text = forecast.confidenceScore?.let { "Độ tin cậy: $it%" } ?: "—"

        tvSupport?.text = forecast.supportLevel?.let {
            String.format(Locale.US, "%,.2f", it)
        } ?: "—"

        tvResistance?.text = forecast.resistanceLevel?.let {
            String.format(Locale.US, "%,.2f", it)
        } ?: "—"

        val techText = forecast.technicalOutlook?.takeIf { it.isNotBlank() }?.let {
            UiTextLocalizer.forecastNarrative(it)
        }
        tvTechOutlook?.text = techText ?: "—"

        val fundText = forecast.fundamentalOutlook?.takeIf { it.isNotBlank() }?.let {
            UiTextLocalizer.forecastNarrative(it)
        }
        tvFundOutlook?.text = fundText ?: "—"

        val drivers = forecast.keyDrivers?.filter { it.isNotBlank() }
        if (!drivers.isNullOrEmpty()) {
            tvKeyDrivers?.text = drivers.joinToString("\n") { "• $it" }
        } else {
            tvKeyDrivers?.text = "—"
        }

        when {
            recommendationCode?.contains("BUY") == true ->
                tvRecommendation?.setTextColor(Color.parseColor("#089981"))
            recommendationCode?.contains("SELL") == true ->
                tvRecommendation?.setTextColor(Color.parseColor("#F23645"))
            else ->
                tvRecommendation?.setTextColor(Color.parseColor("#D1D4DC"))
        }
    }

    private fun updateQuotaLabel(remaining: Int) {
        tvForecastQuota?.text = getString(R.string.refresh_quota_remaining, remaining)
    }

    private fun syncQuotaInBackground() {
        context?.let { ctx ->
            RefreshQuotaManager.syncQuotaStatus(ctx) { remaining ->
                if (isAdded && view != null) {
                    updateQuotaLabel(remaining)
                }
            }
        }
    }

    fun setSymbol(symbol: String) {
        // Compatibility hook for Activity2 navigation; loads market forecast
        if (isAdded && view != null && llContent?.visibility != View.VISIBLE && finiLoadingForecast?.isLoading() != true) {
            loadForecast("MARKET")
        }
    }

    fun loadForecast(symbol: String = "MARKET", isAutoRefresh: Boolean = false) {
        currentSymbol = "MARKET"
        tvForecastSymbol?.text = "TOÀN THỊ TRƯỜNG"
        val appContext = context?.applicationContext ?: return
        val repo = ForecastRepository.getInstance(appContext)

        val generation = ++currentLoadGeneration
        activeCall?.cancel()

        // 1. Đọc ngay tức thì từ fast cache (memory / SharedPreferences)
        val fastCache = repo.getCachedForecastFast("MARKET")
        if (fastCache != null) {
            displayForecastData(fastCache)
            finiLoadingForecast?.hide()
            tvResult?.visibility = View.GONE
            if (!isAutoRefresh) {
                finiInlineForecast?.show()
            }
            startNetworkRevalidation(generation, repo, isAutoRefresh)
        } else {
            // Đọc Room DB hoàn tất trước, tuyệt đối không chạy đua ghi đè network
            viewLifecycleOwner.lifecycleScope.launch {
                val dbCache = withContext(Dispatchers.IO) { repo.getCachedForecast("MARKET") }
                if (generation != currentLoadGeneration || !isAdded || view == null) return@launch

                if (dbCache != null) {
                    displayForecastData(dbCache)
                    finiLoadingForecast?.hide()
                    tvResult?.visibility = View.GONE
                    if (!isAutoRefresh) {
                        finiInlineForecast?.show()
                    }
                } else {
                    // Cold start thực sự (chưa từng có cache)
                    if (llContent?.visibility != View.VISIBLE) {
                        finiLoadingForecast?.show()
                        finiInlineForecast?.hide()
                        llContent?.visibility = View.GONE
                        tvResult?.visibility = View.GONE
                    }
                }

                // Bắt đầu background revalidation sau khi đã nạp xong local state
                startNetworkRevalidation(generation, repo, isAutoRefresh)
            }
        }
    }

    private fun startNetworkRevalidation(generation: Long, repo: ForecastRepository, isAutoRefresh: Boolean) {
        if (generation != currentLoadGeneration || !isAdded || view == null) return

        activeCall?.cancel()
        val call = RetrofitClient.apiService.getMarketForecast("24H_7D")
        activeCall = call

        call.enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(call: Call<ForecastResponse>, response: Response<ForecastResponse>) {
                isAutoRevalidationInProgress.set(false)
                if (call.isCanceled || generation != currentLoadGeneration || !isAdded || view == null) return
                finiLoadingForecast?.hide()
                finiInlineForecast?.hide()

                val forecast = response.body()
                if (response.isSuccessful && forecast != null) {
                    val previousSavedTs = repo.getLastSavedTimestamp("MARKET")
                    val newForecastTs = ForecastDateTimeFormatter.parseToVietnamTime(forecast.createdAt)?.toInstant()?.toEpochMilli() ?: 0L
                    val isNewerData = previousSavedTs == 0L || (newForecastTs > 0L && newForecastTs > previousSavedTs)

                    if (isAutoRefresh) {
                        // Tự động làm mới khi countdown về 0:
                        if (forecast.stale == true || (!isNewerData && previousSavedTs > 0L)) {
                            // Server vẫn trả cache cũ chưa cập nhật: lùi lịch qua backoff, tránh bão request 30s
                            repo.recordAutoAttemptFailure("MARKET")
                        } else {
                            // Nhận được bản dự báo mới thực sự: đặt lịch 24h
                            repo.recordAutoAttemptSuccess("MARKET")
                        }
                    } else {
                        // Cache-first revalidation khi mở màn hình/recreate:
                        // CHỈ cập nhật scheduler khi server có bản dự báo mới hơn cache hiện có
                        // Tuyệt đối không reset 24h nếu dữ liệu trả về chỉ là cache hit trùng khớp
                        if (isNewerData && forecast.stale != true && previousSavedTs > 0L) {
                            repo.recordAutoAttemptSuccess("MARKET")
                        }
                    }

                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        repo.saveForecast(forecast)
                    }
                    displayForecastData(forecast)
                    startCountdownTimer()
                } else {
                    if (isAutoRefresh) {
                        repo.recordAutoAttemptFailure("MARKET")
                    }
                    // Nếu đã có dữ liệu hiển thị từ cache, tuyệt đối KHÔNG làm trắng màn hình
                    if (llContent?.visibility == View.VISIBLE) {
                        tvForecastStaleWarning?.visibility = View.VISIBLE
                    } else {
                        tvResult?.visibility = View.VISIBLE
                        llContent?.visibility = View.GONE

                        val errorMsg = when (response.code()) {
                            503 -> getString(R.string.refresh_forecast_error)
                            422 -> getString(R.string.refresh_forecast_unsupported, currentSymbol)
                            else -> getString(R.string.refresh_forecast_error)
                        }
                        tvResult?.text = errorMsg
                        tvResult?.setTextColor(Color.parseColor("#F23645"))
                    }
                }
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                isAutoRevalidationInProgress.set(false)
                if (call.isCanceled || generation != currentLoadGeneration || !isAdded || view == null) return
                finiLoadingForecast?.hide()
                finiInlineForecast?.hide()

                if (isAutoRefresh) {
                    repo.recordAutoAttemptFailure("MARKET")
                }

                // Nếu đã có dữ liệu hiển thị từ cache, giữ nguyên màn hình và hiện cảnh báo stale
                if (llContent?.visibility == View.VISIBLE) {
                    tvForecastStaleWarning?.visibility = View.VISIBLE
                } else {
                    llContent?.visibility = View.GONE
                    tvResult?.visibility = View.VISIBLE
                    tvResult?.text = getString(R.string.refresh_network_error)
                    tvResult?.setTextColor(Color.parseColor("#F23645"))
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateQuotaLabel(RefreshQuotaManager.getRemaining())
        syncQuotaInBackground()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded && view != null) {
            updateQuotaLabel(RefreshQuotaManager.getRemaining())
            syncQuotaInBackground()
            startCountdownTimer()
        }
    }

    override fun onDestroyView() {
        countdownJob?.cancel()
        countdownJob = null
        activeCall?.cancel()
        activeCall = null
        activeRefreshCall?.cancel()
        activeRefreshCall = null
        isRefreshingInProgress.set(false)
        finiLoadingForecast?.cleanup()
        finiLoadingForecast = null
        finiInlineForecast?.cleanup()
        finiInlineForecast = null
        swipeRefreshForecast = null
        scrollForecast = null
        tvForecastQuota = null
        tvForecastTitle = null
        tvForecastCountdown = null
        btnForecastRefresh = null
        tvForecastSymbol = null
        tvResult = null
        llContent = null
        tvForecastStaleWarning = null
        tvRecommendation = null
        tvConfidence = null
        tvSupport = null
        tvResistance = null
        tvTechOutlook = null
        tvFundOutlook = null
        tvKeyDrivers = null
        tvForecastTimestamp = null
        super.onDestroyView()
    }
}
