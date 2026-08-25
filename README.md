# 🚀 TÀI LIỆU BÀN GIAO & HƯỚNG DẪN HOÀN THIỆN ỨNG DỤNG FNMF
> **Dành cho:** Nhóm phát triển FNMF (Mạnh - Leader, Hùng - Android Dev, Khôi - Backend Dev)  
> **Phiên bản:** `v1.2.0 - Unified Mobile & Backend Suite`  
> **Trạng thái:** 🟢 **Đã tích hợp thành công 3 phần việc, chạy mượt mà trên máy thật!**

---

## 🎯 1. TỔNG KẾT NHỮNG GÌ ĐÃ HOÀN THÀNH (100% CHẠY TỐT)

Hệ thống đã được **Khôi** ghép nối (Merge) thành công toàn bộ mã nguồn của 3 thành viên thành **1 dự án Android thống nhất duy nhất**:

| Thành viên | Phần việc đóng góp | Trạng thái hiện tại |
| :--- | :--- | :---: |
| **Đặng Đức Khôi (Backend)** | • Máy chủ Spring Boot 3, CSDL Oracle 21c.<br>• Pipeline Alpha Vantage (Nến thật) + Gemini AI Gateway.<br>• Trọn bộ 21 REST API (Xác thực JWT, Sàn ví ảo $10,000, Dự báo AI). | 🟢 **Hoàn thành 100%**<br>(Đã test pass 21/21 API trên Swagger) |
| **Nguyễn Quang Hùng (Android)** | • Màn hình Đăng nhập `Activity1`.<br>• Màn hình Giao dịch `Activity2` vẽ biểu đồ nến `MPAndroidChart` Xanh/Đỏ.<br>• Nút Mua/Bán khớp lệnh và danh sách Watchlist. | 🟢 **Hoàn thành khung MVP**<br>(Đã kết nối trực tiếp Backend) |
| **Nguyễn Hữu Mạnh (Leader/AI/Room)** | • Thiết kế cấu trúc CSDL cục bộ Room Database (`News` + `AI_Analysis`).<br>• Cơ chế lưu trữ ngoại tuyến (Offline Cache) khi điện thoại mất mạng. | 🟢 **Hoàn thành tích hợp**<br>(Tự động đồng bộ tin tức từ Backend) |

---

## 📱 2. HIỆN TRẠNG ỨNG DỤNG TRÊN ĐIỆN THOẠI ĐÃ LÀM ĐƯỢC GÌ?

Khi bạn mở App trên điện thoại Android (đã test thực tế trên Samsung A17):
1. **Đăng nhập mượt mà:** Nhập email $\rightarrow$ Bấm "Đăng Nhập" $\rightarrow$ Nhận Token bảo mật JWT từ Server.
2. **Vẽ nến thời gian thực:** Màn hình chuyển sang biểu đồ nến 30 ngày của Bitcoin (`BTCUSDT`) với màu xanh/đỏ chuẩn TradingView.
3. **Khớp lệnh Mua/Bán ví ảo:** Bấm nút **MUA (Buy)** / **BÁN (Sell)** $\rightarrow$ Backend tự động trừ tiền ví ảo $10,000, tính lãi/lỗ (PnL) và cập nhật số dư ngay trên màn hình.
4. **Lưu trữ Offline Room DB:** App tự động kéo tin tức tài chính và phân tích Bullish/Bearish từ Server lưu vào Room DB trong máy.

---

## 🛠️ 3. VIỆC CÒN LẠI CẦN HÙNG & MẠNH LÀM TIẾP (RẤT DỄ VÀ NHẸ NHÀNG)

Toàn bộ **Dữ liệu & API Backend của Khôi đã có sẵn 100%**, phía Android của **Hùng & Mạnh** chỉ cần thiết kế thêm **Giao diện hiển thị (UI Tab)** cho 2 màn hình sau:

```text
               ┌──────────────────────────────────────────────┐
               │         THANH ĐIỀU HƯỚNG TAB (BOTTOM NAV)    │
               └──────┬────────────────┬───────────────┬──────┘
                      │                │               │
                      ▼                ▼               ▼
                 [TAB 1: TRADING]  [TAB 2: TIN TỨC AI] [TAB 3: DỰ BÁO AI]
                 • ĐÃ XONG 100%    • CẦN VẼ THÊM UI    • CẦN VẼ THÊM UI
                 (Biểu đồ nến)     (Danh sách báo)     (Hỗ trợ/Kháng cự)
```

### 📋 Chi tiết 2 màn hình cần vẽ thêm:

#### 1️⃣ Màn hình Tab Tin tức AI (AI News Feed) - *Gợi ý cho Mạnh phụ trách*
* **Mục đích:** Hiển thị danh sách các bài báo tài chính kèm phân tích của Gemini AI.
* **API Backend đã có sẵn:** `GET /api/mobile/news/sync?limit=10`
* **Dữ liệu trả về để vẽ lên màn hình:**
  * Tiêu đề bài báo (`title`), Link đọc báo (`url`).
  * Nhãn tâm lý thị trường: `BULLISH` (Màu xanh), `BEARISH` (Màu đỏ), `NEUTRAL` (Màu vàng).
  * Điểm tin cậy: `confidenceScore` (Ví dụ: 93%).
  * 3 ý tóm tắt của AI (`summary`) và lý do giải thích (`reason`).

#### 2️⃣ Màn hình Tab Dự báo Thị trường AI (Market Forecast) - *Gợi ý cho Hùng phụ trách*
* **Mục đích:** Hiển thị nhận định xu hướng và khuyến nghị chiến lược của AI cho từng đồng tiền (BTC, Vàng, Dầu).
* **API Backend đã có sẵn:** `GET /api/forecast/BTCUSDT`
* **Dữ liệu trả về để vẽ lên màn hình:**
  * Xu hướng: `BULLISH_UPTREND` (Xu hướng tăng) hoặc `BEARISH_DOWNTREND` (Xu hướng giảm).
  * Vùng Hỗ trợ (`supportLevel` - ví dụ: $65,200) & Vùng Kháng cự (`resistanceLevel` - ví dụ: $69,500).
  * Khuyến nghị hành động: `STRONG_BUY`, `BUY`, `HOLD`, `SELL`.

---

## 💻 4. HƯỚNG DẪN HÙNG & MẠNH MỞ VÀ CHẠY PROJECT TRÊN MÁY TÍNH

Mã nguồn đã được đồng bộ chuẩn chỉnh và đặt tại thư mục:  
👉 **`FNMF_Android_Merged`**

### Các bước mở dự án:
1. Mở **Android Studio** $\rightarrow$ Chọn **File** $\rightarrow$ **Open...**
2. Chọn thư mục **`FNMF_Android_Merged`**.
3. Chờ Android Studio tải xong Gradle (khoảng 1 phút).
4. Cắm điện thoại Android vào máy tính (hoặc bật máy ảo Emulator).
5. Bấm nút **RUN ▶️ (Tam giác xanh)** là ứng dụng sẽ chạy lên ngay!

### 🌐 Cấu hình địa chỉ Server (File `RetrofitClient.kt`):
* **Nếu chạy máy ảo Android Studio:** Dùng `http://10.0.2.2:8083/`
* **Nếu cắm cáp điện thoại thật:** Dùng `http://localhost:8083/` (kèm lệnh `adb reverse tcp:8083 tcp:8083`).
* **Nếu chạy qua mạng Internet từ xa:** Dùng link Cloudflare Tunnel do Khôi cấp (Ví dụ: `https://xxxx.trycloudflare.com/`).

---

## 🎓 5. KỊCH BẢN BẢO VỆ ĐỒ ÁN (ĐẢM BẢO CẢ 3 BẠN ĐỀU ĐẠT ĐIỂM A+)

Khi đứng trước Hội đồng chấm thi, nhóm phân vai thuyết trình cực kỳ chuyên nghiệp như sau:

| Thành viên | Nội dung báo cáo ấn tượng trước Hội đồng |
| :--- | :--- |
| **Mạnh (Leader / Prompt & Cache)** | *"Em phụ trách tối ưu hóa Prompt AI tài chính và thiết kế kiến trúc Room Database để lưu trữ dữ liệu ngoại tuyến (Offline-First), đảm bảo người dùng mất mạng vẫn xem lại được các phân tích AI."* |
| **Hùng (Mobile Dev / UI & Charts)** | *"Em phụ trách phát triển giao diện Android, tích hợp thư viện MPAndroidChart để vẽ biểu đồ nến kỹ thuật theo thời gian thực và xây dựng trải nghiệm đặt lệnh Mua/Bán trực quan."* |
| **Khôi (Backend & Data Architect)** | *"Em phụ trách xây dựng toàn bộ hạ tầng Backend Spring Boot 3, CSDL Oracle 21c, cổng AI Gateway kết nối Gemini AI, đường ống dữ liệu Alpha Vantage và hệ thống tính toán khớp lệnh ví ảo Paper Trading $10,000."* |

---

> 💡 **Tài liệu Swagger kiểm thử toàn bộ API:**  
> Mở trình duyệt tại: `http://localhost:8083/swagger-ui/index.html` để xem và bấm thử toàn bộ 21 API thật của dự án!
