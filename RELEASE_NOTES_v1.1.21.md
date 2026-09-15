### FNMF v1.1.21 — Hợp nhất thị trường cổ phiếu & Chuẩn hóa 5 module giao diện

#### 1. Các điểm nâng cấp chính
- **Tích hợp thị trường Cổ phiếu (Stock Market Integration):**
  - Hỗ trợ danh mục 8 mã cổ phiếu công nghệ và tài chính hàng đầu: `AAPL`, `MSFT`, `NVDA`, `TSLA`, `AMZN`, `META`, `GOOGL`, `JPM`.
  - Tích hợp biểu đồ nến (candlestick chart) cho cổ phiếu song song với thị trường Crypto.
  - Hỗ trợ thêm/xóa/theo dõi cổ phiếu trong Danh sách theo dõi (Watchlist) và Danh mục đầu tư (Portfolio).
  - Khớp lệnh giao dịch cổ phiếu với cơ chế kiểm tra giá thị trường thời gian thực, từ chối lệnh khi giá bị quá hạn (stale) hoặc dữ liệu thị trường chưa sẵn sàng.
- **Hoàn thiện cấu trúc 5 module giao diện (Bottom Navigation):**
  - **Giao dịch (Trading):** Tích hợp chuyển đổi linh hoạt giữa Crypto và Cổ phiếu, danh mục theo dõi nhúng, đặt lệnh Mua/Bán trực tiếp.
  - **Dự báo (Forecast):** Phân tích AI kỹ thuật và khuyến nghị đầu tư tự động từ Gemini kết hợp nến và tin tức.
  - **Tin tức (News):** Cập nhật tin tức thị trường bằng tiếng Việt.
  - **Danh mục (Portfolio):** Hiển thị chi tiết số dư nắm giữ cổ phiếu/crypto, tính toán PnL, hiển thị an toàn `—` khi chưa có dữ liệu giá.
  - **Ví (Wallet):** Quản lý số dư, nạp/rút tiền qua VNPay Sandbox và chuyển tiền an toàn.
- **Tối ưu hóa & Bảo mật phiên bản Release (RC2 Polish):**
  - Bảo vệ dữ liệu thiếu: Không tự động chuyển giá trị rỗng thành 0 hoặc +0.00%, giữ nguyên `—` đúng chuẩn tài chính.
  - Xử lý xác thực tập trung: Điều hướng đăng nhập ngay khi gặp lỗi 401/403 qua `AuthSessionManager.handleUnauthorized`.
  - Cơ chế cập nhật độc lập Watchlist (`WatchlistStateReducer`) chống lỗi snapshot aliasing khi thao tác nhanh.
  - Nâng cấp vùng chạm Avatar tối thiểu 48dp theo tiêu chuẩn Accessibility (A11y).
  - Toàn bộ chuỗi hiển thị runtime chuyển sang `strings.xml`, không để lộ mã HTTP hoặc chi tiết exception ra giao diện người dùng.

#### 2. Thông tin chữ ký & Tệp cài đặt Release APK
- **Phiên bản:** `versionName: 1.1.21`, `versionCode: 21` (`debuggable: false`)
- **Gói ứng dụng:** `com.example.nhumonglenh`
- **Release Keystore:** Ký bằng chứng chỉ chính thức FNMF (khớp 100% các bản phát hành trước, hỗ trợ cài đè `adb install -r` giữ nguyên dữ liệu).
  - **Certificate DN:** `CN=Dang Duc Khoi, OU=FNMF Mobile, O=FNMF, L=Hanoi, ST=Hanoi, C=VN`
  - **Certificate SHA-256:** `f19eddeb2acac5dcef5a5f710674cf0ea652fbb5da10233b736ceadc4d4648bc`
- **Tệp đính kèm:**
  - `FNMF-v1.1.21.apk`
  - **File SHA-256:** `45853DD5C6EF7156F436E280A582CC1DF25AF19DAB62BD6F5B78D4CDADFD2036`
