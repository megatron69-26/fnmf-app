## FNMF v1.1.25 — Independent Market Catalog & Realtime Candle Rebuild

### Changes
- **Catalog Thị trường**: Đổi tiêu đề chuẩn thành "5 TÀI SẢN BINANCE". Decouple hoàn toàn callback lồng nhau: khi `getStocks()` trả về thành công, lập tức submit 5 mã vào adapter; các tác vụ bổ sung `getMarketPrices()` và `getWatchlist()` chạy độc lập, lỗi mạng hay HTTP 401/403 của watchlist không làm mất catalog.
- **Biểu đồ nến Realtime**: Thay thế việc sửa trực tiếp `CandleEntry` cũ bằng quy trình dựng lại biểu đồ qua `rebuildCandleChartFromSeries()`:
  - Trong cùng một phút: Cập nhật hình dạng cây nến cuối cùng (Open/High/Low/Close).
  - Sang phút mới: Giữ nguyên cây nến cũ, thêm một cây nến mới ở cuối, loại bỏ cây đầu tiên (tối đa 30 cây nến cố định) và cập nhật nhãn thời gian trục X.
  - Bảo toàn viewport (zoom/pan matrix) của người dùng khi biểu đồ cập nhật.
- **Unit Tests**:
  - Bổ sung `CatalogAndChartBehavioralTest.kt` với 7 unit test kiểm chứng Catalog độc lập và cơ chế Reducer theo phút.
  - Toàn bộ 242/242 Android unit test PASS.
