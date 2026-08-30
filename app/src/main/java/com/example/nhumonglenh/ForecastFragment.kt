package com.example.nhumonglenh

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ForecastFragment : Fragment() {

    private lateinit var tvResult: TextView
    private lateinit var pbLoading: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_forecast, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvResult = view.findViewById(R.id.tvForecastResult)
        pbLoading = view.findViewById(R.id.pbForecastLoading)

        loadForecast("BTCUSDT")
    }

    private fun loadForecast(symbol: String) {
        pbLoading.visibility = View.VISIBLE
        tvResult.visibility = View.GONE
        
        RetrofitClient.apiService.getForecast(symbol).enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(call: Call<ForecastResponse>, response: Response<ForecastResponse>) {
                pbLoading.visibility = View.GONE
                tvResult.visibility = View.VISIBLE
                if (response.isSuccessful) {
                    val forecast = response.body()
                    tvResult.text = "Symbol: ${forecast?.symbol}\nPrediction: $${forecast?.predictedPrice}\nConfidence: ${forecast?.confidence}%\nAdvice: ${forecast?.advice}"
                } else {
                    tvResult.text = "Failed to load forecast"
                }
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                pbLoading.visibility = View.GONE
                tvResult.visibility = View.VISIBLE
                tvResult.text = "Error: ${t.message}"
                Log.e("ForecastFragment", "Error: ${t.message}")
            }
        })
    }
}
