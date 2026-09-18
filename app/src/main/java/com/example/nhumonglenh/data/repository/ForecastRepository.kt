package com.example.nhumonglenh.data.repository

import android.content.Context
import android.util.Log
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.ForecastEntity
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dual-write persistent repository cho Market Forecast:
 * 1. SharedPreferences: Đọc tức thì (< 5ms) khi mở màn hình, không giật lag.
 * 2. Room DB (forecast_table): Lưu trữ cấu trúc bền vững lâu dài.
 */
class ForecastRepository private constructor(private val context: Context) {

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var memoryCache: ForecastResponse? = null

    /**
     * Lấy ngay bản forecast từ bộ nhớ hoặc SharedPreferences gần như tức thì.
     */
    fun getCachedForecastFast(symbol: String = "MARKET"): ForecastResponse? {
        memoryCache?.let { return it }

        val json = prefs.getString(KEY_FORECAST_PREFIX + symbol, null)
        if (!json.isNullOrBlank()) {
            try {
                val forecast = gson.fromJson(json, ForecastResponse::class.java)
                if (forecast != null) {
                    memoryCache = forecast
                    return forecast
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi đọc cache SharedPreferences cho $symbol: ${e.message}")
            }
        }

        return null
    }

    /**
     * Đọc đầy đủ từ Room DB (I/O thread) nếu SharedPreferences chưa có.
     */
    suspend fun getCachedForecast(symbol: String = "MARKET"): ForecastResponse? = withContext(Dispatchers.IO) {
        getCachedForecastFast(symbol)?.let { return@withContext it }

        try {
            val dao = AppDatabase.getInstance(context).forecastDao()
            val entity = dao.getForecast(symbol) ?: return@withContext null
            val drivers: List<String> = if (!entity.keyDriversJson.isNullOrBlank()) {
                try {
                    val listType = object : TypeToken<List<String>>() {}.type
                    gson.fromJson(entity.keyDriversJson, listType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val forecast = ForecastResponse(
                symbol = entity.symbol,
                assetName = "Toàn thị trường",
                currentPrice = entity.currentPrice,
                trendPrediction = entity.trendPrediction,
                timeframe = entity.timeframe ?: "24H_7D",
                supportLevel = entity.supportLevel,
                resistanceLevel = entity.resistanceLevel,
                recommendation = entity.recommendation,
                confidenceScore = entity.confidenceScore,
                keyDrivers = drivers,
                technicalOutlook = entity.technicalOutlook,
                fundamentalOutlook = entity.fundamentalOutlook,
                analysisSource = entity.analysisSource ?: "GEMINI",
                aiShard = entity.aiShard,
                candleCount = entity.candleCount ?: 30,
                fromCache = true,
                stale = entity.stale ?: false,
                createdAt = entity.createdAt
            )

            // Lưu ngược lại SharedPreferences để lần sau đọc nhanh hơn
            saveToSharedPreferences(symbol, forecast)
            memoryCache = forecast
            forecast
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc forecast từ Room DB: ${e.message}", e)
            null
        }
    }

    /**
     * Ghi đồng thời vào SharedPreferences và Room DB.
     */
    suspend fun saveForecast(forecast: ForecastResponse) = withContext(Dispatchers.IO) {
        val sym = forecast.symbol?.trim()?.uppercase() ?: "MARKET"
        memoryCache = forecast
        saveToSharedPreferences(sym, forecast)

        try {
            val keyDriversJson = forecast.keyDrivers?.let { gson.toJson(it) } ?: "[]"
            val entity = ForecastEntity(
                sym,
                forecast.recommendation,
                forecast.confidenceScore,
                forecast.currentPrice,
                forecast.supportLevel,
                forecast.resistanceLevel,
                keyDriversJson,
                forecast.trendPrediction,
                forecast.technicalOutlook,
                forecast.fundamentalOutlook,
                forecast.analysisSource ?: "GEMINI",
                forecast.createdAt,
                forecast.timeframe ?: "24H_7D",
                forecast.stale ?: false,
                forecast.aiShard,
                forecast.candleCount ?: 30,
                System.currentTimeMillis()
            )

            val dao = AppDatabase.getInstance(context).forecastDao()
            dao.insertForecast(entity)
            Log.i(TAG, "Đã lưu forecast vào Room DB và SharedPreferences cho symbol=$sym")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi ghi forecast vào Room DB: ${e.message}", e)
        }
    }

    private fun saveToSharedPreferences(symbol: String, forecast: ForecastResponse) {
        try {
            val json = gson.toJson(forecast)
            prefs.edit().putString(KEY_FORECAST_PREFIX + symbol, json).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi ghi SharedPreferences cho $symbol: ${e.message}")
        }
    }

    fun clearCache(symbol: String = "MARKET") {
        memoryCache = null
        prefs.edit().remove(KEY_FORECAST_PREFIX + symbol).apply()
        try {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.getInstance(context).forecastDao().deleteForecast(symbol)
                } catch (e: Exception) {
                    Log.w(TAG, "Lỗi xóa forecast trong Room DB cho $symbol: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi dispatch xóa forecast Room: ${e.message}")
        }
    }

    suspend fun clearCacheSync(symbol: String = "MARKET") = withContext(Dispatchers.IO) {
        memoryCache = null
        prefs.edit().remove(KEY_FORECAST_PREFIX + symbol).apply()
        try {
            AppDatabase.getInstance(context).forecastDao().deleteForecast(symbol)
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi xóa forecast trong Room DB cho $symbol: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ForecastRepository"
        private const val PREFS_NAME = "fnmf_forecast_cache_prefs"
        private const val KEY_FORECAST_PREFIX = "cache_forecast_"

        @Volatile
        private var INSTANCE: ForecastRepository? = null

        fun getInstance(context: Context): ForecastRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ForecastRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
