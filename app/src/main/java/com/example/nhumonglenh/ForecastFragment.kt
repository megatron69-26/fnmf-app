package com.example.nhumonglenh

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.data.remote.RetrofitClient
import com.example.nhumonglenh.ui.UiTextLocalizer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class ForecastFragment : Fragment() {

    private var tvResult: TextView? = null
    private var pbLoading: ProgressBar? = null
    private var llContent: LinearLayout? = null
    private var tvRecommendation: TextView? = null
    private var tvConfidence: TextView? = null
    private var tvSupport: TextView? = null
    private var tvResistance: TextView? = null
    private var tvTechOutlook: TextView? = null
    private var tvFundOutlook: TextView? = null
    private var activeCall: Call<ForecastResponse>? = null

    private var currentSymbol: String = "BTCUSDT"

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

        loadForecast(currentSymbol)
    }

    fun loadForecast(symbol: String) {
        currentSymbol = symbol
        val pb = pbLoading ?: return
        val res = tvResult ?: return
        val content = llContent ?: return

        pb.visibility = View.VISIBLE
        res.visibility = View.GONE
        content.visibility = View.GONE

        activeCall?.cancel()
        val call = RetrofitClient.apiService.getForecast(symbol, "24H_7D")
        activeCall = call

        call.enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(call: Call<ForecastResponse>, response: Response<ForecastResponse>) {
                if (call.isCanceled || !isAdded || view == null) return
                pbLoading?.visibility = View.GONE

                val forecast = response.body()
                if (response.isSuccessful && forecast != null) {
                    llContent?.visibility = View.VISIBLE
                    val recommendationCode = forecast.recommendation?.trim()?.uppercase(Locale.ROOT)
                    tvRecommendation?.text = UiTextLocalizer.recommendation(forecast.recommendation)
                    val confidence = forecast.confidenceScore ?: 0
                    tvConfidence?.text = "Độ tin cậy: $confidence%"
                    tvSupport?.text = String.format("%,.2f", forecast.supportLevel ?: 0.0)
                    tvResistance?.text = String.format("%,.2f", forecast.resistanceLevel ?: 0.0)
                    tvTechOutlook?.text = UiTextLocalizer.forecastNarrative(forecast.technicalOutlook)
                    tvFundOutlook?.text = UiTextLocalizer.forecastNarrative(forecast.fundamentalOutlook)

                    when {
                        recommendationCode?.contains("BUY") == true ->
                            tvRecommendation?.setTextColor(Color.parseColor("#089981"))
                        recommendationCode?.contains("SELL") == true ->
                            tvRecommendation?.setTextColor(Color.parseColor("#F23645"))
                        else ->
                            tvRecommendation?.setTextColor(Color.parseColor("#D1D4DC"))
                    }
                } else {
                    tvResult?.visibility = View.VISIBLE
                    val errorMsg = when (response.code()) {
                        422 -> "Mã tài sản '$symbol' chưa được hỗ trợ dự báo AI."
                        503 -> "Dữ liệu thị trường tạm thời không khả dụng. Vui lòng thử lại sau."
                        else -> "Không thể tải dự báo AI (Mã lỗi: ${response.code()})"
                    }
                    tvResult?.text = errorMsg
                    tvResult?.setTextColor(Color.parseColor("#F23645"))
                }
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                if (call.isCanceled || !isAdded || view == null) return
                pbLoading?.visibility = View.GONE
                tvResult?.visibility = View.VISIBLE
                tvResult?.text = "Lỗi kết nối máy chủ: ${t.localizedMessage ?: t.message}"
                tvResult?.setTextColor(Color.parseColor("#F23645"))
            }
        })
    }

    override fun onDestroyView() {
        activeCall?.cancel()
        activeCall = null
        tvResult = null
        pbLoading = null
        llContent = null
        tvRecommendation = null
        tvConfidence = null
        tvSupport = null
        tvResistance = null
        tvTechOutlook = null
        tvFundOutlook = null
        super.onDestroyView()
    }

}
