package com.example.nhumonglenh.data.repository

import android.content.Context
import android.util.Log
import com.example.nhumonglenh.data.local.AppDatabase
import com.example.nhumonglenh.data.local.ForecastEntity
import com.example.nhumonglenh.data.remote.ForecastResponse
import com.example.nhumonglenh.ui.common.AutoRefreshScheduler
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
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString(KEY_FORECAST_PREFIX + symbol, json)
                .putLong(KEY_FORECAST_TIMESTAMP_PREFIX + symbol, now)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi ghi SharedPreferences cho $symbol: ${e.message}")
        }
    }

    fun getLastSavedTimestamp(symbol: String = "MARKET"): Long {
        val cached = getCachedForecastFast(symbol)
        if (cached != null) {
            val parsed = com.example.nhumonglenh.ui.forecast.ForecastDateTimeFormatter.parseToVietnamTime(cached.createdAt)
            if (parsed != null) {
                return parsed.toInstant().toEpochMilli()
            }
        }
        return prefs.getLong(KEY_FORECAST_TIMESTAMP_PREFIX + symbol, 0L)
    }

    fun clearCache(symbol: String = "MARKET") {
        memoryCache = null
        prefs.edit()
            .remove(KEY_FORECAST_PREFIX + symbol)
            .remove(KEY_FORECAST_TIMESTAMP_PREFIX + symbol)
            .apply()
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
        prefs.edit()
            .remove(KEY_FORECAST_PREFIX + symbol)
            .remove(KEY_FORECAST_TIMESTAMP_PREFIX + symbol)
            .apply()
        try {
            AppDatabase.getInstance(context).forecastDao().deleteForecast(symbol)
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi xóa forecast trong Room DB cho $symbol: ${e.message}")
        }
    }

    fun getScheduler(symbol: String = "MARKET"): AutoRefreshScheduler {
        return AutoRefreshScheduler(
            prefs = prefs,
            keyNextAttempt = KEY_FORECAST_NEXT_ATTEMPT_PREFIX + symbol,
            keyFailures = KEY_FORECAST_FAILURES_PREFIX + symbol,
            cycleMs = FORECAST_CYCLE_MS,
            baseBackoffMs = FORECAST_BASE_BACKOFF_MS,
            maxBackoffMs = FORECAST_MAX_BACKOFF_MS
        )
    }

    fun getOrInitNextAutoAttemptAt(symbol: String = "MARKET", now: Long = System.currentTimeMillis()): Long {
        val lastSaved = getLastSavedTimestamp(symbol)
        return getScheduler(symbol).getOrInitNextAttemptAt(now, lastSaved)
    }

    fun recordAutoAttemptSuccess(symbol: String = "MARKET", now: Long = System.currentTimeMillis()): Long {
        return getScheduler(symbol).recordSuccess(now)
    }

    fun recordAutoAttemptFailure(symbol: String = "MARKET", now: Long = System.currentTimeMillis()): Long {
        return getScheduler(symbol).recordFailure(now)
    }

    companion object {
        private const val TAG = "ForecastRepository"
        private const val PREFS_NAME = "fnmf_forecast_cache_prefs"
        private const val KEY_FORECAST_PREFIX = "cache_forecast_"
        private const val KEY_FORECAST_TIMESTAMP_PREFIX = "cache_forecast_ts_"
        const val KEY_FORECAST_NEXT_ATTEMPT_PREFIX = "forecast_next_attempt_"
        const val KEY_FORECAST_FAILURES_PREFIX = "forecast_failures_"

        const val FORECAST_CYCLE_MS = 24 * 60 * 60 * 1000L // 24 giờ
        const val FORECAST_BASE_BACKOFF_MS = 2 * 60 * 1000L // 2 phút
        const val FORECAST_MAX_BACKOFF_MS = 30 * 60 * 1000L // 30 phút

        @Volatile
        private var INSTANCE: ForecastRepository? = null

        fun getInstance(context: Context): ForecastRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ForecastRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
