# FNMF Mobile Client — Ung dung Android Tin tuc Tai chinh & Giao dich Mo phong

Financial News & Market Forecasting (FNMF) Mobile Client la ung dung di dong Android ho tro nha dau tu theo doi thi truong, cap nhat tin tuc tai chinh quoc te duoc phan tich boi tri tue nhan tao Google Gemini, va thuc hanh giao dich gia lap (Paper Trading).

- **Phien ban ung dung:** v1.1.23 (`versionCode = 23`, `versionName = "1.1.23"`)
- **Thanh vien thuc hien:**
  - Nguyen Quang Hung (Member #2 - Android Developer / UI, MPAndroidChart, Retrofit)
  - Nguyen Huu Manh (Member #1 / Leader - Prompt AI, Room Database & Offline Cache)
  - Dang Duc Khoi (Member #3 - Backend, Data Pipeline & Android Integration)
- **Nen tang cong nghe:**
  - Ngon ngu: Kotlin
  - Yeu cau he dieu hanh: Android SDK minSdk 24 (Android 7.0), targetSdk 35 (Android 15)
  - Kien truc: MVVM (Model - View - ViewModel)
  - Mang va API: Retrofit 2, OkHttp 3, Binance WebSocket Client
  - Bieu do ky thuat: MPAndroidChart (nen Nhat OHLCV 1m / daily thoi gian thuc)
  - Co so du lieu cuc bo: Room Database (SQLite) luu tru offline tin tuc va danh muc theo doi
  - Bao mat: AndroidKeyStore voi thuat toan AES-GCM 256-bit ma hoa token phien JWT, Network Security Config chan cleartext HTTP
- **Backend Production Endpoint:** `https://fnmf-backend-production.up.railway.app/`

---

## 1. Kien Truc Fixed Provider Sharding & Polling Tren Android (v1.1.23)

Ung dung di dong Android FNMF v1.1.23 thuc thi nghiem ngat cac chinh sach phan luong nha cung cap:

1. **Anh Xa Nguon Du Lieu Co Dinh (MarketDataProviderPolicy):**
   - **Binance:** Crypto (`BTCUSDT`, `ETHUSDT`) & Vang (`XAUUSD`). Mo WebSocket live cap nhat tick gia real-time.
   - **Alpaca:** Cổ phiếu Mỹ Nhóm A (`AAPL`, `MSFT`, `NVDA`, `GOOGL`). Badge hiển thị nguồn: `"Alpaca"`.
   - **Twelve Data:** Cổ phiếu Mỹ Nhóm B (`TSLA`, `AMZN`, `META`, `JPM`). Badge hiển thị nguồn: `"Twelve Data"`.
2. **Tuyet Doi KHONG Mo WebSocket Cho Co Phieu:**
   - `MarketStreamHelper.resolveWebSocketStream` tra ve `null` cho tat ca 8 ma co phieu My.
   - `TradingLifecyclePolicy.decideResumeAction` tra ve `DO_NOTHING` khi resume man hinh co phieu.
3. **Chinh Sach Polling Dinh Ky 60 Giay (StockPollingPolicy):**
   - Tu dong goi API nạp nến và giá mỗi 60 giây khi đang ở tab Giao dịch và màn hình hiển thị.
   - Tự động hủy/dừng polling khi chuyển sang tab khác, mở danh mục cổ phiếu hoặc khi ứng dụng vào background.
   - Guard kiểm soát thế hệ (`generation`) và mã (`symbol`) chống ghi đè dữ liệu cũ (race condition).
4. **Badge Trang Thai & Khoa Lenh Khi Du Lieu Cham (Stale Guard):**
   - Khi `stale = true` hoặc mất kết nối: Hiển thị badge màu vàng *"Dữ liệu có thể trễ"* (`@string/trading_stale_badge`) và khóa nút Mua / Bán (`isTradeEnabled = false`).
   - Khi `stale = false`: Hiển thị badge màu xanh *"Trực tiếp"* (`@string/trading_live_badge`).
5. **Nhan Bieu Do Nen 1 Phut (1m Timeframe):**
   - Tất cả tài sản đều hỗ trợ khung thời gian 1 phút (`1m`) được định dạng nhãn chuẩn qua `ChartLabelFormatter`.

---

## 2. Thong Tin Release APK (v1.1.23 RC3)

- **File APK:** `app/build/outputs/apk/release/app-release.apk`
- **SHA-256 Checksum:**
  ```text
  1DECA04C58901B1F3920C7C91E506D6F4491469410AD9CD6113FDD2A616317CD
  ```
- **Xac Thuc Chu Ky So (`apksigner verify --verbose`):**
  - **Trang thai:** `Verifies: true`
  - **Co che ky:** `APK Signature Scheme v2: true`
  - **So luong chu ky:** 1
- **Thong Tin Chung Chi So (`--print-certs`):**
  - **DN:** `CN=Dang Duc Khoi, OU=FNMF Mobile, O=FNMF, L=Hanoi, ST=Hanoi, C=VN`
  - **SHA-256 Digest:** `f19eddeb2acac5dcef5a5f710674cf0ea652fbb5da10233b736ceadc4d4648bc`
  - **SHA-1 Digest:** `34c1a67fe186c2daa294d751b41291c1d5415db8`

---

## 3. Huong Dan Bien Dich va Cai Dat

### 1. Chay Unit Tests
```bash
gradlew.bat testDebugUnitTest
```
Ket qua: 100% PASS (Tat ca test trong `FixedProviderShardingAppUnitTest`, `SecureTokenStoreUnitTest`, `TradingPolishUnitTest`).

### 2. Bien Dich Release APK
```bash
gradlew.bat assembleRelease
```

### 3. Kiem Tra Chu Ky So APK
```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

### 4. Cai Dat Len Thiet Bi
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Sau khi phát hành phiên bản v1.1.18, hệ thống backend đã thực hiện các hotfix bổ sung sau:
- Commit `20b25a1`: Rút gọn nội dung Forecast. Giới hạn độ dài nhận định kỹ thuật (`technicalOutlook` tối đa 140 ký tự), nhận định cơ bản (`fundamentalOutlook` tối đa 140 ký tự) và đúng 3 yếu tố dẫn dắt chính (`keyDrivers`, mỗi ý tối đa 85 ký tự) để hiển thị phù hợp trên giao diện ứng dụng di động Android.
- Commit `3dffc56`: Làm sạch thông báo lỗi và log provider. Loại bỏ nội dung lỗi thô và chi tiết nhà cung cấp khỏi nhật ký máy chủ và phản hồi client.
- Commit `527e455`: Ngừng vòng xử lý News khi Gemini trả 429. Ngắt ngay vòng lặp khi gặp mã 429 (`break`), kích hoạt thời gian chờ (cooldown) và ưu tiên phục vụ dữ liệu đã lưu trong cơ sở dữ liệu để bảo vệ hạn ngạch cho Forecast.
- Triển khai Railway Production: Mã triển khai `bc3e787b-ffb1-4e6f-b784-d7b02c1698ae` đạt trạng thái `SUCCESS` (triển khai trực tiếp qua Railway CLI nên bảng điều khiển không gắn commit SHA). Nhật ký vận hành xác nhận cơ chế ngắt vòng lặp khi gặp lỗi 429 (`break-on-429`) đã hoạt động trong phiên điều phối thực tế; không suy diễn thành bằng chứng đối chiếu cho toàn bộ mã nguồn.

