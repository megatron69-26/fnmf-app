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
                putExtra(NewsDetailActivity.EXTRA_TITLE, news.title)
                putExtra(NewsDetailActivity.EXTRA_SUMMARY, news.summary)
                putExtra(NewsDetailActivity.EXTRA_SOURCE, news.source)
                putExtra(NewsDetailActivity.EXTRA_SENTIMENT, news.sentiment)
                putExtra(NewsDetailActivity.EXTRA_CONFIDENCE, news.confidence)
                putExtra(NewsDetailActivity.EXTRA_BULLETS, news.bulletPoints.toTypedArray())
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
            val result = runCatching { ApiClient.service(appContext).syncNews().data }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.pbNewsLoading.visibility = View.GONE

                if (result.isSuccess) {
                    val news = result.getOrNull() ?: emptyList()
                    if (news.isNotEmpty()) {
                        binding.rvNews.visibility = View.VISIBLE
                        binding.tvNewsError.visibility = View.GONE
                        adapter.submit(news)
                    } else {
                        binding.rvNews.visibility = View.GONE
                        binding.tvNewsError.visibility = View.VISIBLE
                        binding.tvNewsError.text = "Không có tin tức nào được trả về từ Server."
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorDetail = when (error) {
                        is HttpException -> "Lỗi HTTP ${error.code()}: ${error.message()}"
                        is IOException -> "Không thể kết nối tới Server (${error.message})"
                        else -> error?.localizedMessage ?: "Lỗi không xác định"
                    }
                    adapter.submit(emptyList())
                    binding.rvNews.visibility = View.GONE
                    binding.tvNewsError.visibility = View.VISIBLE
                    binding.tvNewsError.text = "⚠️ $errorDetail\n(Không dùng dữ liệu giả offline)"
                    Toast.makeText(appContext, "News Backend: $errorDetail", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
