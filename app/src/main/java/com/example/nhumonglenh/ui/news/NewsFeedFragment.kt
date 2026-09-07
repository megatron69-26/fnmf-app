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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            }
            startActivity(intent)
        }
        binding.rvNews.adapter = adapter
        fetchNews(adapter, requireContext().applicationContext)
    }

    private fun fetchNews(adapter: NewsAdapter, appContext: Context) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { ApiClient.service(appContext).syncNews().data }
            val news = result.getOrElse { loadMockNewsSafely(appContext) }

            withContext(Dispatchers.Main) {
                adapter.submit(news)
                result.exceptionOrNull()?.let { error ->
                    Toast.makeText(
                        appContext,
                        "Không gọi được News backend; đang dùng dữ liệu offline (${error.javaClass.simpleName}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadMockNewsSafely(context: Context): List<News> {
        return runCatching {
            val json = context.assets.open("news_mock.json")
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<News>>() {}.type
            Gson().fromJson<List<News>>(json, type).orEmpty()
        }.getOrElse { emptyList() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
