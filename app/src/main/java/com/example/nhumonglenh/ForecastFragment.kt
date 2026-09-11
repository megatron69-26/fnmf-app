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

    private var tvForecastSymbol: TextView? = null
    private var tvResult: TextView? = null
    private var pbLoading: ProgressBar? = null
    private var llContent: LinearLayout? = null
    private var tvRecommendation: TextView? = null
    private var tvConfidence: TextView? = null
    private var tvSupport: TextView? = null
    private var tvResistance: TextView? = null
    private var tvTechOutlook: TextView? = null
    private var tvFundOutlook: TextView? = null
    private var tvKeyDrivers: TextView? = null
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
        tvForecastSymbol = view.findViewById(R.id.tvForecastSymbol)
        tvResult = view.findViewById(R.id.tvForecastResult)
        pbLoading = view.findViewById(R.id.pbForecastLoading)
        llContent = view.findViewById(R.id.llForecastContent)
        tvRecommendation = view.findViewById(R.id.tvRecommendation)
        tvConfidence = view.findViewById(R.id.tvConfidence)
        tvSupport = view.findViewById(R.id.tvSupport)
        tvResistance = view.findViewById(R.id.tvResistance)
        tvTechOutlook = view.findViewById(R.id.tvTechOutlook)
        tvFundOutlook = view.findViewById(R.id.tvFundOutlook)
        tvKeyDrivers = view.findViewById(R.id.tvKeyDrivers)

        loadForecast(currentSymbol)
    }

    fun setSymbol(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.ROOT)
        if (clean.isNotBlank() && clean != currentSymbol) {
            currentSymbol = clean
            if (isAdded && view != null) {
                loadForecast(clean)
            }
        }
    }

    fun loadForecast(symbol: String) {
        currentSymbol = symbol.trim().uppercase(Locale.ROOT)
        tvForecastSymbol?.text = currentSymbol
        val pb = pbLoading ?: return
        val res = tvResult ?: return
        val content = llContent ?: return

        pb.visibility = View.VISIBLE
        res.visibility = View.GONE
        content.visibility = View.GONE

        activeCall?.cancel()
        val call = RetrofitClient.apiService.getForecast(currentSymbol, "24H_7D")
        activeCall = call

        call.enqueue(object : Callback<ForecastResponse> {
            override fun onResponse(call: Call<ForecastResponse>, response: Response<ForecastResponse>) {
                if (call.isCanceled || !isAdded || view == null) return
                pbLoading?.visibility = View.GONE

                val forecast = response.body()
                if (response.isSuccessful && forecast != null) {
                    llContent?.visibility = View.VISIBLE
                    tvResult?.visibility = View.GONE

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
                } else {
                    tvResult?.visibility = View.VISIBLE
                    llContent?.visibility = View.GONE

                    val errorMsg = when (response.code()) {
                        422 -> "Mã tài sản '$currentSymbol' chưa được hỗ trợ nhận định thị trường."
                        503 -> "Chưa thể tạo nhận định lúc này. Vui lòng thử lại sau."
                        else -> "Chưa thể tạo nhận định lúc này. Vui lòng thử lại sau."
                    }
                    tvResult?.text = errorMsg
                    tvResult?.setTextColor(Color.parseColor("#F23645"))
                }
            }

            override fun onFailure(call: Call<ForecastResponse>, t: Throwable) {
                if (call.isCanceled || !isAdded || view == null) return
                pbLoading?.visibility = View.GONE
                llContent?.visibility = View.GONE
                tvResult?.visibility = View.VISIBLE
                tvResult?.text = "Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại sau."
                tvResult?.setTextColor(Color.parseColor("#F23645"))
            }
        })
    }

    override fun onDestroyView() {
        activeCall?.cancel()
        activeCall = null
        tvForecastSymbol = null
        tvResult = null
        pbLoading = null
        llContent = null
        tvRecommendation = null
        tvConfidence = null
        tvSupport = null
        tvResistance = null
        tvTechOutlook = null
        tvFundOutlook = null
        tvKeyDrivers = null
        super.onDestroyView()
    }
}
