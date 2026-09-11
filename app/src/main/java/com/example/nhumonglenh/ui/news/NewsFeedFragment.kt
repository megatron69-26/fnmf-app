package com.example.nhumonglenh.ui.news

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nhumonglenh.databinding.FragmentNewsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class NewsFeedFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

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
        fetchNews(adapter, requireContext().applicationContext)
    }

    private fun fetchNews(adapter: NewsAdapter, appContext: Context) {
        binding.pbNewsLoading.visibility = View.VISIBLE
        binding.tvNewsError.visibility = View.GONE
        binding.rvNews.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val repository = com.example.nhumonglenh.data.repository.NewsRepository.getInstance(appContext)
            val result = repository.getNews()

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.pbNewsLoading.visibility = View.GONE

                when (result) {
                    is com.example.nhumonglenh.data.repository.NewsRepository.NewsResult.SyncSuccess -> {
                        binding.rvNews.visibility = View.VISIBLE
                        binding.tvNewsError.visibility = View.GONE
                        adapter.submit(result.news)
                    }
                    is com.example.nhumonglenh.data.repository.NewsRepository.NewsResult.CacheFallback -> {
                        binding.rvNews.visibility = View.VISIBLE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = "Đang ngoại tuyến. Ứng dụng đang hiển thị tin tức đã lưu trên thiết bị."
                        adapter.submit(result.news)
                    }
                    is com.example.nhumonglenh.data.repository.NewsRepository.NewsResult.CacheWriteFailure -> {
                        binding.rvNews.visibility = View.GONE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = "Không thể lưu tin tức trên thiết bị. Vui lòng thử lại."
                        adapter.submit(emptyList())
                    }
                    is com.example.nhumonglenh.data.repository.NewsRepository.NewsResult.Empty -> {
                        binding.rvNews.visibility = View.GONE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = "${result.message}\n(Không có tin tức khả dụng)"
                        adapter.submit(emptyList())
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
