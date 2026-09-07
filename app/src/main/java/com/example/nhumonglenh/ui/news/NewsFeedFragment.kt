package com.example.nhumonglenh.ui.news

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager

import com.example.nhumonglenh.ui.news.News
import com.example.nhumonglenh.databinding.FragmentNewsBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewsFeedFragment : Fragment() {

    private var _b: FragmentNewsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentNewsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.rvNews.layoutManager = LinearLayoutManager(requireContext())
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
        b.rvNews.adapter = adapter
        fetchNews(adapter)
    }

    private fun fetchNews(adapter: NewsAdapter) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.service.syncNews()
                val list = response.data
                launch(Dispatchers.Main) { adapter.submit(list) }
            } catch (e: Exception) {
                e.printStackTrace()
                // Lá»—i máº¡ng â†’ dÃ¹ng mock data Ä‘á»ƒ app váº«n cháº¡y
                val mock = loadMockNews()
                launch(Dispatchers.Main) {
                    adapter.submit(mock)
                    // Hiá»ƒn thá»‹ trá»±c tiáº¿p lá»—i há»‡ thá»‘ng ra Toast Ä‘á»ƒ báº¯t Ä‘Ãºng nguyÃªn nhÃ¢n
                    val errorMessage = "Lá»—i (${e.javaClass.simpleName}): ${e.message}"
                    Toast.makeText(
                        requireContext(),
                        "KhÃ´ng gá»i Ä‘Æ°á»£c backend, Ä‘ang dÃ¹ng dá»¯ liá»‡u máº«u: $errorMessage",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadMockNews(): List<News> {
        val json = requireContext().assets.open("news_mock.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<News>>() {}.type
        return Gson().fromJson(json, type)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}




