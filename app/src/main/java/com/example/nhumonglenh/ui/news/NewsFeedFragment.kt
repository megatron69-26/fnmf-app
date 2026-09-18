package com.example.nhumonglenh.ui.news

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nhumonglenh.R
import com.example.nhumonglenh.data.local.AuthSessionManager
import com.example.nhumonglenh.data.repository.NewsRepository
import com.example.nhumonglenh.data.repository.RefreshQuotaManager
import com.example.nhumonglenh.databinding.FragmentNewsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class NewsFeedFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val isRefreshingInProgress = AtomicBoolean(false)
    private val isAutoSyncInProgress = AtomicBoolean(false)
    private var newsAdapter: NewsAdapter? = null
    private var countdownJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvNews.layoutManager = LinearLayoutManager(requireContext())

        val adapter = NewsAdapter { news ->
            val intent = Intent(requireContext(), NewsDetailActivity::class.java).apply {
                putExtra(NewsDetailActivity.EXTRA_TITLE, news.getEffectiveTitle())
                putExtra(NewsDetailActivity.EXTRA_SUMMARY, news.getEffectiveSummary())
                putExtra(NewsDetailActivity.EXTRA_SOURCE, news.getEffectivePublisher())
                putExtra(NewsDetailActivity.EXTRA_SENTIMENT, news.sentiment)
                putExtra(NewsDetailActivity.EXTRA_CONFIDENCE, news.confidence)
                putExtra(NewsDetailActivity.EXTRA_BULLETS, news.getEffectiveBullets().toTypedArray())
                putExtra(NewsDetailActivity.EXTRA_AUTHOR, news.author)
                putExtra(NewsDetailActivity.EXTRA_LINK, news.link)
            }
            startActivity(intent)
        }
        newsAdapter = adapter
        binding.rvNews.adapter = adapter

        setupRefreshButton(adapter)
        setupPullToRefresh(adapter)
        updateQuotaLabel(RefreshQuotaManager.getRemaining())
        syncQuotaInBackground()

        startCountdownTimer()
        fetchNews(adapter, requireContext().applicationContext)
    }

    private fun setupRefreshButton(adapter: NewsAdapter) {
        binding.btnNewsRefresh.setOnClickListener {
            performNewsRefresh(adapter, requireContext().applicationContext)
        }
    }

    private fun setupPullToRefresh(adapter: NewsAdapter) {
        binding.swipeRefreshNews.setColorSchemeColors(Color.parseColor("#2962FF"))
        binding.swipeRefreshNews.setOnRefreshListener {
            performNewsRefresh(adapter, requireContext().applicationContext)
        }
    }

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            val appContext = context?.applicationContext ?: return@launch
            val repository = NewsRepository.getInstance(appContext)

            while (isActive) {
                val now = System.currentTimeMillis()
                val nextAttempt = repository.getOrInitNextAutoAttemptAt(now)
                val remainingMs = (nextAttempt - now).coerceAtLeast(0L)

                val minutes = (remainingMs / 1000) / 60
                val seconds = (remainingMs / 1000) % 60

                val timeStr = String.format(Locale.US, "%02d:%02d", minutes, seconds)
                _binding?.tvNewsCountdown?.text = getString(R.string.auto_update_countdown, timeStr)

                if (remainingMs <= 0L) {
                    if (isAutoSyncInProgress.compareAndSet(false, true)) {
                        // Khi về 0: Tự động revalidate nền qua GET (cache-first), tuyệt đối KHÔNG gọi POST refresh tiêu hao quota
                        newsAdapter?.let { currentAdapter ->
                            fetchNews(currentAdapter, appContext, isAutoSync = true)
                        } ?: run {
                            isAutoSyncInProgress.set(false)
                        }
                    }
                }

                delay(1000L)
            }
        }
    }

    private fun performNewsRefresh(adapter: NewsAdapter, appContext: Context) {
        if (!isRefreshingInProgress.compareAndSet(false, true)) {
            binding.swipeRefreshNews.isRefreshing = false
            return
        }

        binding.btnNewsRefresh.isEnabled = false
        binding.swipeRefreshNews.isRefreshing = false
        if (adapter.itemCount == 0) {
            binding.finiLoadingNews.show()
        } else {
            binding.finiInlineNews.show()
        }

        val authHeader = AuthSessionManager.getAuthHeader(appContext)
        if (authHeader.isNullOrBlank()) {
            // Không có authHeader: cho phép người dùng khách đồng bộ tin tức nền (GET)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val repository = NewsRepository.getInstance(appContext)
                val result = repository.syncNewsInBackground(force = true)
                val now = System.currentTimeMillis()
                if (result is NewsRepository.NewsResult.SyncSuccess && !result.isStale) {
                    repository.recordAutoAttemptSuccess(now)
                } else {
                    repository.recordAutoAttemptFailure(now)
                }

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.finiLoadingNews.hide()
                    binding.finiInlineNews.hide()
                    binding.btnNewsRefresh.isEnabled = true
                    isRefreshingInProgress.set(false)
                    when (result) {
                        is NewsRepository.NewsResult.SyncSuccess,
                        is NewsRepository.NewsResult.CacheHit -> {
                            val newsList = if (result is NewsRepository.NewsResult.SyncSuccess) result.news else (result as NewsRepository.NewsResult.CacheHit).news
                            binding.rvNews.visibility = View.VISIBLE
                            binding.tvNewsError.visibility = View.GONE
                            adapter.submit(newsList)
                        }
                        is NewsRepository.NewsResult.CacheFallback -> {
                            if (adapter.itemCount == 0 && result.news.isNotEmpty()) {
                                adapter.submit(result.news)
                            }
                            binding.tvNewsStaleWarning.visibility = View.VISIBLE
                        }
                        else -> {
                            Toast.makeText(requireContext(), getString(R.string.refresh_auth_required), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            return
        }

        val clientRequestId = UUID.randomUUID().toString()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val repository = NewsRepository.getInstance(appContext)
            val result = repository.refreshNews(authHeader, clientRequestId)
            val now = System.currentTimeMillis()
            if (result is NewsRepository.NewsRefreshResult.Success && !result.isStale) {
                repository.recordAutoAttemptSuccess(now)
            } else if (result is NewsRepository.NewsRefreshResult.DegradedOrEmpty ||
                result is NewsRepository.NewsRefreshResult.ServerError ||
                result is NewsRepository.NewsRefreshResult.NetworkError) {
                repository.recordAutoAttemptFailure(now)
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.finiLoadingNews.hide()
                binding.finiInlineNews.hide()
                binding.btnNewsRefresh.isEnabled = true
                isRefreshingInProgress.set(false)

                when (result) {
                    is NewsRepository.NewsRefreshResult.Success -> {
                        result.remainingRefreshes?.let {
                            RefreshQuotaManager.setRemaining(it)
                            updateQuotaLabel(it)
                        }
                        binding.rvNews.visibility = View.VISIBLE
                        binding.tvNewsError.visibility = View.GONE
                        if (result.isStale) {
                            binding.tvNewsStaleWarning.visibility = View.VISIBLE
                            binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                        } else {
                            binding.tvNewsStaleWarning.visibility = View.GONE
                        }
                        adapter.submit(result.news)
                    }
                    is NewsRepository.NewsRefreshResult.DegradedOrEmpty -> {
                        result.remainingRefreshes?.let {
                            RefreshQuotaManager.setRemaining(it)
                            updateQuotaLabel(it)
                        }
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        if (result.cachedNews.isNotEmpty()) {
                            binding.rvNews.visibility = View.VISIBLE
                            binding.tvNewsError.visibility = View.GONE
                            binding.tvNewsStaleWarning.visibility = View.VISIBLE
                            binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                            adapter.submit(result.cachedNews)
                        }
                    }
                    is NewsRepository.NewsRefreshResult.QuotaExhausted -> {
                        RefreshQuotaManager.setRemaining(0)
                        updateQuotaLabel(0)
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.refresh_quota_exhausted),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (result.cachedNews.isNotEmpty()) {
                            binding.rvNews.visibility = View.VISIBLE
                            binding.tvNewsError.visibility = View.GONE
                            binding.tvNewsStaleWarning.visibility = View.VISIBLE
                            binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                            adapter.submit(result.cachedNews)
                        }
                    }
                    is NewsRepository.NewsRefreshResult.Unauthorized -> {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.session_expired_msg),
                            Toast.LENGTH_SHORT
                        ).show()
                        activity?.let { AuthSessionManager.handleUnauthorized(it) }
                    }
                    is NewsRepository.NewsRefreshResult.ServerError -> {
                        result.remainingRefreshes?.let {
                            RefreshQuotaManager.setRemaining(it)
                            updateQuotaLabel(it)
                        }
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        if (result.cachedNews.isNotEmpty()) {
                            binding.rvNews.visibility = View.VISIBLE
                            binding.tvNewsError.visibility = View.GONE
                            binding.tvNewsStaleWarning.visibility = View.VISIBLE
                            binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                            adapter.submit(result.cachedNews)
                        }
                    }
                    is NewsRepository.NewsRefreshResult.NetworkError -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        if (result.cachedNews.isNotEmpty()) {
                            binding.rvNews.visibility = View.VISIBLE
                            binding.tvNewsError.visibility = View.GONE
                            binding.tvNewsStaleWarning.visibility = View.VISIBLE
                            binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                            adapter.submit(result.cachedNews)
                        }
                    }
                }
            }
        }
    }

    private fun updateQuotaLabel(remaining: Int) {
        _binding?.tvNewsQuota?.text = getString(R.string.refresh_quota_remaining, remaining)
    }

    private fun syncQuotaInBackground() {
        context?.let { ctx ->
            RefreshQuotaManager.syncQuotaStatus(ctx) { remaining ->
                if (_binding != null) {
                    updateQuotaLabel(remaining)
                }
            }
        }
    }

    private fun fetchNews(adapter: NewsAdapter, appContext: Context, isAutoSync: Boolean = false) {
        val repository = NewsRepository.getInstance(appContext)

        // 1. Đọc và hiển thị NGAY LẬP TỨC toàn bộ tin tức đã lưu trong Room Database (nếu không phải auto-sync)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (!isAutoSync) {
                val cachedNews = repository.getCachedNews()
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    if (cachedNews.isNotEmpty()) {
                        binding.rvNews.visibility = View.VISIBLE
                        binding.finiLoadingNews.hide()
                        binding.tvNewsError.visibility = View.GONE
                        adapter.submit(cachedNews)
                        binding.finiInlineNews.show()
                    } else {
                        // Cold start: Chưa từng có tin tức nào trong Room
                        binding.finiLoadingNews.show()
                        binding.finiInlineNews.hide()
                        binding.rvNews.visibility = View.GONE
                        binding.tvNewsError.visibility = View.GONE
                    }
                }
            }

            // 2. Chạy background sync bất đồng bộ
            try {
                val result = repository.syncNewsInBackground(force = false)
                val now = System.currentTimeMillis()
                when (result) {
                    is NewsRepository.NewsResult.SyncSuccess -> {
                        // Remote network sync thành công thực sự:
                        if (!result.isStale) {
                            repository.recordAutoAttemptSuccess(now)
                        } else {
                            repository.recordAutoAttemptFailure(now)
                        }
                    }
                    is NewsRepository.NewsResult.CacheHit -> {
                        // Cache hit do throttle Room: TUYỆT ĐỐI KHÔNG reset scheduler, giữ nguyên đếm ngược hiện tại
                    }
                    is NewsRepository.NewsResult.CacheFallback,
                    is NewsRepository.NewsResult.CacheWriteFailure,
                    is NewsRepository.NewsResult.Empty -> {
                        if (isAutoSync) {
                            repository.recordAutoAttemptFailure(now)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    binding.finiLoadingNews.hide()
                    binding.finiInlineNews.hide()

                    when (result) {
                        is NewsRepository.NewsResult.SyncSuccess,
                        is NewsRepository.NewsResult.CacheHit -> {
                            val newsList = if (result is NewsRepository.NewsResult.SyncSuccess) result.news else (result as NewsRepository.NewsResult.CacheHit).news
                            val isStale = if (result is NewsRepository.NewsResult.SyncSuccess) result.isStale else (result as NewsRepository.NewsResult.CacheHit).isStale
                            binding.rvNews.visibility = View.VISIBLE
                            binding.tvNewsError.visibility = View.GONE
                            if (isStale) {
                                binding.tvNewsStaleWarning.visibility = View.VISIBLE
                                binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                            } else {
                                binding.tvNewsStaleWarning.visibility = View.GONE
                            }
                            adapter.submit(newsList)
                        }
                        is NewsRepository.NewsResult.CacheFallback -> {
                            // GIỮ NGUYÊN danh sách tin tức cũ đã nạp từ Room, TUYỆT ĐỐI KHÔNG làm trắng màn hình
                            if (adapter.itemCount == 0 && result.news.isNotEmpty()) {
                                adapter.submit(result.news)
                            }
                            if (adapter.itemCount > 0) {
                                binding.rvNews.visibility = View.VISIBLE
                                binding.tvNewsStaleWarning.visibility = View.VISIBLE
                                binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                                binding.tvNewsError.visibility = View.GONE
                            } else {
                                binding.tvNewsError.visibility = View.VISIBLE
                                binding.tvNewsError.text = "Đang ngoại tuyến. Ứng dụng đang hiển thị tin tức đã lưu trên thiết bị."
                            }
                        }
                        is NewsRepository.NewsResult.CacheWriteFailure -> {
                            if (adapter.itemCount == 0) {
                                binding.rvNews.visibility = View.GONE
                                binding.tvNewsStaleWarning.visibility = View.GONE
                                binding.tvNewsError.visibility = View.VISIBLE
                                binding.tvNewsError.text = "Không thể lưu tin tức trên thiết bị. Vui lòng thử lại."
                            }
                        }
                        is NewsRepository.NewsResult.Empty -> {
                            if (adapter.itemCount == 0) {
                                binding.rvNews.visibility = View.GONE
                                binding.tvNewsStaleWarning.visibility = View.GONE
                                binding.tvNewsError.visibility = View.VISIBLE
                                binding.tvNewsError.text = if (result.message.isNotBlank()) result.message else "Chưa có bản tin mới"
                            }
                        }
                    }
                }
            } finally {
                if (isAutoSync) {
                    isAutoSyncInProgress.set(false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateQuotaLabel(RefreshQuotaManager.getRemaining())
        syncQuotaInBackground()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            updateQuotaLabel(RefreshQuotaManager.getRemaining())
            syncQuotaInBackground()
            startCountdownTimer()
        }
    }

    override fun onDestroyView() {
        countdownJob?.cancel()
        countdownJob = null
        isRefreshingInProgress.set(false)
        isAutoSyncInProgress.set(false)
        newsAdapter = null
        _binding?.finiLoadingNews?.cleanup()
        _binding?.finiInlineNews?.cleanup()
        _binding = null
        super.onDestroyView()
    }
}
