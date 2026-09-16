### FNMF v1.1.22 — Binance Real-time Kline & Lifecycle Coordinator

#### 1. Các điểm nâng cấp chính
- **Biểu đồ nến Binance Real-time Kline (Real-time Candlestick Streaming):**
  - Biểu đồ nến của `BTCUSDT`, `ETHUSDT` và `XAUUSD` (thông qua `PAXGUSDT` tham chiếu vàng quốc tế) chuyển động trực tiếp theo từng biến động giá thời gian thực từ Binance WebSocket stream (`kline_1m`), loại bỏ hoàn toàn việc nến bị đứng yên trong khi giá nhảy.
  - Định dạng nhãn biểu đồ chuẩn hóa: `"<symbol> • 1m"` cho Crypto/Vàng và `"<symbol> • 1D"` cho Cổ phiếu Mỹ.
- **Bộ phân tích & Thu gọn nến thuần (Pure Kline Parser & Series Reducer):**
  - `BinanceKlineParser`: Xác thực toàn vẹn payload Binance Kline, nghiêm ngặt bắt buộc khung thời gian `interval == "1m"` và các bất biến OHLCV (`open`, `high`, `low`, `close > 0`, `high >= low`, `high >= open/close`, `low <= open/close`, `volume >= 0`).
  - `CandleSeriesReducer`: Cùng phút (`event.openTime == lastOpenTime`) cập nhật cây nến cuối cùng; sang phút mới (`event.openTime > lastOpenTime`) tự động dịch chuyển 30 nến, loại bỏ nến cũ nhất ở vị trí index 0 và gán nến mới ở vị trí 29f cùng nhãn thời gian tương ứng.
  - Tuyệt đối không sinh dữ liệu ngẫu nhiên hoặc nội suy nến giả.
- **Bộ điều phối vòng đời & Bảo vệ Socket (Trading Lifecycle Policy & Socket Callback Guard):**
  - `TradingLifecyclePolicy`: Phân biệt rõ rệt giữa khởi tạo màn hình và quay lại foreground; ngăn chặn triệt để tình trạng gọi REST lần hai giữa `onViewCreated` và `onResume`.
  - `SocketCallbackGuard`: Kiểm tra danh tính tham chiếu (`webSocket === binanceWebSocket`), `generation` và `symbol`. Vô hiệu hóa socket cũ ngay khi ngắt kết nối để ngăn callback xếp hàng sửa đè giao diện hoặc lên lịch reconnect sai.
  - `SocketReconnectPolicy`: Reconnect với backoff hữu hạn 4 lần (2s, 5s, 10s, 30s). Sau khi hết lượt thử, giải phóng socket an toàn để lần foreground tiếp theo có thể tự động kết nối lại. Chỉ đặt lại bộ đếm sau khi nhận được kline hợp lệ.
- **Chuẩn hóa Interval & Tách biệt Cache Backend:**
  - Backend chuẩn hóa `interval` trước khi xử lý: chỉ chấp nhận `1m` và `daily`/`1d`.
  - Đối với 8 mã cổ phiếu Hoa Kỳ, backend chỉ chấp nhận `daily`; mọi yêu cầu `1m` hoặc chuỗi tùy ý đều bị từ chối với HTTP 400 trước khi gọi nhà cung cấp dữ liệu.
  - Tách biệt vùng nhớ đệm nến theo cặp `symbol_interval`.

#### 2. Thông tin chữ ký & Tệp cài đặt Release APK
- **Phiên bản:** `versionName: 1.1.22`, `versionCode: 22` (`debuggable: false`)
- **Gói ứng dụng:** `com.example.nhumonglenh`
- **Release Keystore:** Ký bằng chứng chỉ chính thức FNMF (hỗ trợ cài đè `adb install -r` bảo toàn toàn bộ dữ liệu người dùng).
  - **Certificate DN:** `CN=Dang Duc Khoi, OU=FNMF Mobile, O=FNMF, L=Hanoi, ST=Hanoi, C=VN`
  - **Certificate SHA-256:** `f19eddeb2acac5dcef5a5f710674cf0ea652fbb5da10233b736ceadc4d4648bc`
- **Tệp đính kèm:**
  - `FNMF-v1.1.22-Binance-Realtime-Kline.apk`
  - **File SHA-256:** `458DF29724042D51678B6E13B510803C5D0D9067194799004A7D728291C59360`
