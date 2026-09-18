## FNMF v1.1.26 — Shared Market Ticker, Wallet Action Reorder & Circular Avatar Profile

### Highlights
- **Market Ticker dùng chung xuyên suốt mọi Module**:
  - Dải ticker cuộn ngang cố định ở đầu `Activity2` (nằm giữa top bar và container), duy trì trạng thái mượt mà qua cả 5 tab (Trading, Watchlist, News, Forecast, Wallet) mà không reload.
  - 8 mã chuẩn theo thứ tự cố định: `BTCUSDT`, `ETHUSDT`, `XAUUSD`, `BNBUSDT`, `SOLUSDT`, `XRPUSDT`, `ADAUSDT`, `DOGEUSDT`.
  - Cơ chế hợp nhất dữ liệu theo từng trường (per-field merge): khi DTO thiếu trường (như `change24h` hoặc `price`), bảo toàn giá trị hợp lệ trước đó và đánh dấu `isStale=true`.
  - Format giá thích ứng qua `PriceFormatter` (giữ trọn độ chính xác thập phân cho XRP, ADA, DOGE) và format % biến động 24h (+ xanh, - đỏ, neutral gray cho null). Tuyệt đối không biến null thành 0.00%.
  - Single polling loop 30 giây ở cấp Activity (`onStart`/`onStop`), không mở WebSocket riêng cho ticker, giữ snapshot gần nhất khi mất mạng tạm thời.
  - Hỗ trợ trợ năng: nối chuỗi cảnh báo dữ liệu trễ (`market_ticker_stale_hint`) vào `contentDescription` khi dữ liệu cũ/thiếu trường.
  - Tương tác: bấm vào bất kỳ mã nào sẽ lập tức chuyển sang tab Trading và cập nhật mã tương ứng.
- **Hoán đổi vị trí nút Ví (Wallet)**:
  - Nút "Nạp tiền" (`btn_sandbox_deposit`) đặt ở bên TRÁI.
  - Nút "Rút tiền" (`btn_sandbox_withdraw`) đặt ở bên PHẢI.
  - Chiều cao và touch target đạt chuẩn $\ge 48\text{dp}$.
  - Giữ nguyên toàn bộ ID, callback, kiểm tra điều kiện sẵn sàng (`PaymentReadinessPolicy`) và bottom sheet xác nhận.
- **Hình ảnh Avatar Profile thay thế Icon cũ**:
  - Tích hợp ảnh đại diện nguồn vào `profile_avatar.jpg`.
  - Sử dụng `ShapeableImageView` với bo tròn `ShapeAppearance.Circle` và viền TradingView mượt mà.
  - Kích thước touch target $\ge 48\text{dp}$, thay thế icon cũ `@android:drawable/ic_menu_myplaces` ở cả header và dialog profile.
  - Chuỗi mô tả trợ năng đưa vào `strings.xml`.
- **Kiểm thử & Đóng gói**:
  - Bổ sung 23 behavioral regression tests kiểm chứng toàn diện các quy tắc ticker, ví, avatar và trợ năng.
  - Toàn bộ 283/283 unit tests vượt qua thành công (100% PASS).
  - Version bump: `versionCode = 26`, `versionName = "1.1.26"`.
