## FNMF v1.1.26 — Shared Continuous Auto-Scroll Market Ticker, LTR Wallet Action Reorder & Circular Avatar Profile

### Highlights
- **Market Ticker Tự động Cuộn Liên tục (Phải → Trái giống Binance)**:
  - Tích hợp `MarketTickerAutoScrollController` và `MarketTickerAutoScrollPolicy`: Tự động di chuyển mượt mà liên tục từ Phải sang Trái (`positiveDx` trong LTR).
  - Vòng lặp vô hạn liền mạch (seamless circular loop): 4 chu kỳ lặp hữu hạn (32 items) và cơ chế hoán đổi chu kỳ vị trí pixel-perfect khi vượt ngưỡng `firstVisiblePos >= 16`. Nối tiếp BTC ngay sau DOGE mà không dừng lại, không có khoảng trống, không giật hình.
  - Tạm dừng thông minh: Chạm giữ hoặc kéo vuốt sẽ tạm dừng auto-scroll; nhấc tay tự động tiếp tục chạy mượt mà.
  - An toàn trợ năng & hệ thống: Tự động tạm dừng khi TalkBack/Touch Exploration đang bật (chuyển về 8 items cố định để duyệt dễ dàng) hoặc khi hệ thống bật tùy chọn "Remove animations".
  - Dọn sạch tài nguyên và listener trong `onDestroy()` ngăn ngừa tuyệt đối rò rỉ bộ nhớ (leak).
  - Không phát sinh bất kỳ yêu cầu mạng REST API hay WebSocket nào mới; cập nhật polling 30 giây giữ nguyên vị trí cuộn, không reset về đầu.
  - Tương tác: Chạm vào bất kỳ mã nào trong dải ticker lập tức chuyển sang tab Trading tương ứng.
- **Hoán đổi & Chuẩn hóa Vị trí Nút Ví (Wallet)**:
  - Bắt buộc `android:layoutDirection="ltr"` ở cả hai layout Ví (`fragment_wallet.xml` và `fragment_wallet_profile.xml`).
  - Nút "Nạp tiền" (`btn_sandbox_deposit`) nằm ở bên **TRÁI**.
  - Nút "Rút tiền" (`btn_sandbox_withdraw`) nằm ở bên **PHẢI**.
  - Đảm bảo tọa độ runtime: `deposit.left < withdraw.left` trên mọi kích cỡ màn hình.
  - Chiều cao và touch target đạt chuẩn $\ge 48\text{dp}$.
  - Giữ nguyên toàn bộ ID, callback (`DEPOSIT` và `WITHDRAWAL`), kiểm tra điều kiện sẵn sàng (`PaymentReadinessPolicy`) và bottom sheet xác nhận.
- **Hình ảnh Avatar Profile thay thế Icon cũ**:
  - Tích hợp ảnh đại diện nguồn vào `profile_avatar.jpg`.
  - Sử dụng `ShapeableImageView` với bo tròn `ShapeAppearance.Circle` và viền TradingView mượt mà.
  - Kích thước touch target $\ge 48\text{dp}$, thay thế icon cũ `@android:drawable/ic_menu_myplaces` ở cả header và dialog profile.
  - Chuỗi mô tả trợ năng đưa vào `strings.xml`.
- **Kiểm thử & Đóng gói**:
  - Bổ sung 33 behavioral regression tests kiểm chứng toàn diện các quy tắc ticker, ví, avatar và trợ năng.
  - Toàn bộ 293/293 unit tests vượt qua thành công (100% PASS).
  - Version: `versionCode = 26`, `versionName = "1.1.26"`.

### Thông tin tệp APK Release
- **Tệp cài đặt**: `FNMF-v1.1.26.apk` (hoặc `app-release.apk`)
- **SHA-256**: `D1B3CFBD40728ED6EE663C0C18A52F0254CC70C1FCB8C2EFCD86BED9DCE13131`
- **Dung lượng**: `5,930,819 bytes`
- **Package**: `com.example.nhumonglenh` | `versionCode: 26` | `versionName: 1.1.26`
- **Chữ ký**: Android APK Signature Scheme v2 (Verified)
