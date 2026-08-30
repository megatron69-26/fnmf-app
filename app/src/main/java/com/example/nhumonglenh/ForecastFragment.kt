package com.example.nhumonglenh

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
    private lateinit var llContent: LinearLayout
    private lateinit var tvRecommendation: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvSupport: TextView
    private lateinit var tvResistance: TextView
    private lateinit var tvTechOutlook: TextView
    private lateinit var tvFundOutlook: TextView

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
        llContent = view.findViewById(R.id.llForecastContent)
        tvRecommendation = view.findViewById(R.id.tvRecommendation)
        tvConfidence = view.findViewById(R.id.tvConfidence)
        tvSupport = view.findViewById(R.id.tvSupport)
        tvResistance = view.findViewById(R.id.tvResistance)
        tvTechOutlook = view.findViewById(R.id.tvTechOutlook)
        tvFundOutlook = view.findViewById(R.id.tvFundOutlook)

        loadForecast("BTCUSDT")
    }

    private fun loadForecast(symbol: String) {
        pbLoading.visibility = View.VISIBLE
        tvResult.visibility = View.GONE
        llContent.visibility = View.GONE
        
        RetrofitClient.apiService.getForecast(symbol).enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(call: Call<ForecastResponse>, response: Response<ForecastResponse>) {
                pbLoading.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val forecast = response.body()!!
                    llContent.visibility = View.VISIBLE
                    tvRecommendation.text = forecast.recommendation ?: "N/A"
                    tvConfidence.text = "Độ tin cậy: " + (forecast.confidenceScore ?: 0) + "%"
                    tvSupport.text = String.format("%,.2f", forecast.supportLevel ?: 0.0)
                    tvResistance.text = String.format("%,.2f", forecast.resistanceLevel ?: 0.0)
                    tvTechOutlook.text = forecast.technicalOutlook ?: "Đang cập nhật..."
                    tvFundOutlook.text = forecast.fundamentalOutlook ?: "Đang cập nhật..."
                    
                    if (forecast.recommendation?.contains("BUY") == true) {
                        tvRecommendation.setTextColor(android.graphics.Color.parseColor("#089981"))
                    } else if (forecast.recommendation?.contains("SELL") == true) {
                        tvRecommendation.setTextColor(android.graphics.Color.parseColor("#F23645"))
                    } else {
                        tvRecommendation.setTextColor(android.graphics.Color.parseColor("#D1D4DC"))
                    }
                } else {
                    tvResult.visibility = View.VISIBLE
                    tvResult.text = "Failed to load forecast: " + response.code()
                }
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                pbLoading.visibility = View.GONE
                tvResult.visibility = View.VISIBLE
                tvResult.text = "Error: " + t.message
            }
        })
    }
}
