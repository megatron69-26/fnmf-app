package com.example.nhumonglenh.data.remote

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * =====================================================================
 * RETROFIT API SERVICE - KẾT NỐI TOÀN DIỆN VỚI FNMF BACKEND (KHÔI)
 * =====================================================================
 */
interface ApiService {

    // 1. Xác thực & Đăng nhập JWT
    @POST("/api/auth/login")
    fun login(@Body request: LoginRequest): Call<AuthResponse>

    // 1.1. Đăng ký tài khoản mới & Khởi tạo ví $10,000
    @POST("/api/auth/register")
    fun register(@Body request: RegisterRequest): Call<AuthResponse>

    // 2. Lấy dữ liệu 30 nến OHLCV để vẽ biểu đồ MPAndroidChart
    @GET("/api/market/candles")
    fun getCandles(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("interval") interval: String = "daily"
    ): Call<List<CandleDto>>

    // 3. Đồng bộ Tin tức & AI Analysis để nạp vào Room DB (Phần của Mạnh)
    @GET("/api/mobile/news/sync")
    fun syncNews(@Query("limit") limit: Int = 10): Call<List<MobileNewsBundleResponse>>

    // 4. Đặt lệnh Mua/Bán Paper Trading (Khớp lệnh $10,000 vốn ảo)
    @POST("/api/trade/order")
    fun placeOrder(
        @Header("Authorization") token: String,
        @Body request: OrderRequest
    ): Call<OrderResponse>

    // 5. Lấy thông tin Tài sản ròng & Danh mục sở hữu
    @GET("/api/trade/portfolio")
    fun getPortfolio(
        @Header("Authorization") token: String
    ): Call<PortfolioSummaryDto>
}
