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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class NewsFeedFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val isRefreshingInProgress = AtomicBoolean(false)
    private var newsAdapter: NewsAdapter? = null

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
        binding.rvNews.adapter = adapter
        newsAdapter = adapter

        setupPullToRefresh(adapter)
        updateQuotaLabel(RefreshQuotaManager.getRemaining())
        syncQuotaInBackground()

        fetchNews(adapter, requireContext().applicationContext)
    }

    private fun setupPullToRefresh(adapter: NewsAdapter) {
        binding.swipeRefreshNews.setColorSchemeColors(Color.parseColor("#2962FF"))
        binding.swipeRefreshNews.setOnRefreshListener {
            performPullToRefresh(adapter, requireContext().applicationContext)
        }
    }

    private fun performPullToRefresh(adapter: NewsAdapter, appContext: Context) {
        if (!isRefreshingInProgress.compareAndSet(false, true)) {
            binding.swipeRefreshNews.isRefreshing = false
            return
        }

        val authHeader = AuthSessionManager.getAuthHeader(appContext)
        if (authHeader.isNullOrBlank()) {
            isRefreshingInProgress.set(false)
            binding.swipeRefreshNews.isRefreshing = false
            Toast.makeText(
                requireContext(),
                getString(R.string.refresh_auth_required),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val clientRequestId = UUID.randomUUID().toString()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val repository = NewsRepository.getInstance(appContext)
            val result = repository.refreshNews(authHeader, clientRequestId)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                isRefreshingInProgress.set(false)
                binding.swipeRefreshNews.isRefreshing = false

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

    private fun fetchNews(adapter: NewsAdapter, appContext: Context) {
        binding.pbNewsLoading.visibility = View.VISIBLE
        binding.tvNewsError.visibility = View.GONE
        binding.rvNews.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val repository = NewsRepository.getInstance(appContext)
            val result = repository.getNews()

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.pbNewsLoading.visibility = View.GONE

                when (result) {
                    is NewsRepository.NewsResult.SyncSuccess -> {
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
                    is NewsRepository.NewsResult.CacheFallback -> {
                        binding.rvNews.visibility = View.VISIBLE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = "Đang ngoại tuyến. Ứng dụng đang hiển thị tin tức đã lưu trên thiết bị."
                        binding.tvNewsStaleWarning.visibility = View.VISIBLE
                        binding.tvNewsStaleWarning.setText(R.string.news_stale_warning)
                        adapter.submit(result.news)
                    }
                    is NewsRepository.NewsResult.CacheWriteFailure -> {
                        binding.rvNews.visibility = View.GONE
                        binding.tvNewsStaleWarning.visibility = View.GONE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = "Không thể lưu tin tức trên thiết bị. Vui lòng thử lại."
                        adapter.submit(emptyList())
                    }
                    is NewsRepository.NewsResult.Empty -> {
                        binding.rvNews.visibility = View.GONE
                        binding.tvNewsStaleWarning.visibility = View.GONE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = if (result.message.isNotBlank()) result.message else "Chưa có bản tin mới"
                        adapter.submit(emptyList())
                    }
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
        }
    }

    override fun onDestroyView() {
        isRefreshingInProgress.set(false)
        newsAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
