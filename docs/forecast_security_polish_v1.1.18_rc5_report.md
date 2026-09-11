# 📋 BÁO CÁO NGHIỆM THU CANDIDATE FNMF v1.1.18 RC5
## Forecast Correctness, Security Hardening & UI Credibility Polish

> **Ngày thực hiện:** 12/09/2026  
> **Phiên bản:** `1.1.18` (Build `versionCode = 18`, `versionName = "1.1.18"`)  
> **Trạng thái:** ✅ SẴN SÀNG TRIỂN KHAI BACKEND TRƯỚC (Candidate RC5 đã vượt qua 100% bài kiểm thử)  
> **Quy tắc tuân thủ:** Không commit, không push, không tag, không release, không deploy Railway, không ghi đè bất kỳ file APK cũ nào (`RC1`, `RC2`, `RC3`, `RC4`), bảo toàn trọn vẹn 5 file News đang chờ đồng bộ.

---

## 🎯 1. HAI NỘI DUNG NÂNG CẤP CHÍNH TRONG RC5

### 🟢 1. Siết Chặt Policy Ngôn Ngữ Forecast (Thuần Việt Chuyên Ngành Tài Chính)
* **Vấn đề trước RC5:** `ForecastQualityPolicy` từng coi một số từ tiếng Anh tài chính phổ biến (`bullish`, `bearish`, `sideways`, `support`, `resistance`, `long`, `short`, `volume`) là token trung tính, dẫn đến việc chấp nhận các câu lồng tiếng Anh không phù hợp với giao diện tiếng Việt thuần túy.
* **Xử lý triệt để trong RC5:**
  * **Chỉ miễn trừ các ticker, tên riêng và từ viết tắt nghiệp vụ thực sự cần giữ nguyên (`ALLOWED_FINANCIAL_TOKENS`):**
    `BTC`, `ETH`, `USD`, `USDT`, `VND`, `ETF`, `RSI`, `MACD`, `FED`, `SEC`, `FOMC`, `GDP`, `CPI`, `DXY`, `EMA`, `SMA`, `OHLCV`, `Nvidia`, `Apple`, `Microsoft`, `Tesla`, `Binance`, `Coinbase`, `Bitcoin`, `Ethereum`, `Solana`, `FNMF`.
  * **Đưa vào danh mục kiểm duyệt và từ chối trực tiếp (`FORBIDDEN_UNTRANSLATED_TERMS_PATTERN`):**
    `bullish`, `bearish`, `sideways`, `support`, `resistance`, `long`, `short`, `volume`, `outlook`, `sentiment`, `forecast`, `action plan`.
    Nếu bất kỳ từ nào trong danh mục này xuất hiện trong `technicalOutlook`, `fundamentalOutlook` hoặc `keyDrivers`, policy sẽ từ chối ngay lập tức (`ForecastUnavailableException`) và hàm `isSubstantialVietnamese()` trả về `false`.
  * **Bảo lưu machine enums nội bộ:** Các enum kỹ thuật như `BULLISH_UPTREND`, `BEARISH_DOWNTREND`, `SIDEWAYS_CONSOLIDATION` trong `trendPrediction` và `recommendation` được giữ nguyên trong JSON schema nội bộ, không bị ảnh hưởng.
  * **Cập nhật Gemini System Prompt ([`GeminiForecastClient.java`](file:///C:/Users/khoid/OneDrive/Desktop/llm-gateway3/src/main/java/com/llmgateway/service/GeminiForecastClient.java)):**
    Yêu cầu AI bắt buộc sử dụng chuẩn thuật ngữ tiếng Việt:
    * `xu hướng tăng` (thay cho bullish)
    * `xu hướng giảm` (thay cho bearish)
    * `đi ngang` (thay cho sideways)
    * `hỗ trợ` (thay cho support)
    * `kháng cự` (thay cho resistance)
    * `vị thế mua` (thay cho long)
    * `vị thế bán` (thay cho short)
    * `khối lượng` (thay cho volume)
    * `nhận định` (thay cho outlook)
    * `tâm lý thị trường` (thay cho sentiment)
    * `kịch bản tham khảo` (thay cho action plan / forecast)
* **Kiểm thử hành vi mới bổ sung ([`ForecastQualityPolicyTest.java`](file:///C:/Users/khoid/OneDrive/Desktop/llm-gateway3/src/test/java/com/llmgateway/ForecastQualityPolicyTest.java)):**
  * ❌ **Reject:** `"Bullish support resistance tăng trưởng"`
  * ❌ **Reject:** `"Giá BTC bullish và volume tăng"`
  * ❌ **Reject:** `"Market sentiment tích cực, action plan là long"`
  * ✅ **Accept:** `"Giá BTC duy trì xu hướng tăng với khối lượng giao dịch cải thiện"`
  * ✅ **Accept:** `"Vùng hỗ trợ và kháng cự được xác định từ dữ liệu nến"`
  * ✅ **Accept:** Nội dung chứa ticker, tên riêng, `RSI`, `MACD`, `ETF` và `USD`.

---

### 🟢 2. Xóa Đường Lưu Token Cũ & Quét Xác Nhận Gọi Token An Toàn
* **Xóa hàm thừa:** Đã xóa bỏ hàm `private fun saveToken(token: String)` trong [`Activity1.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/Activity1.kt) vốn bỏ qua kết quả `Boolean`.
* **Quét toàn bộ `app/src/main`:**
  * Xác nhận **100%** các vị trí gọi lưu token đều thông qua [`AuthFlowCoordinator.handleAuthSuccess`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/local/AuthFlowCoordinator.kt), `AuthSessionManager.saveToken()`, hoặc `SecureTokenStore.saveToken()`, và đều kiểm tra kết quả `Boolean` để xử lý thành công/thất bại rõ ràng:
    * **Thành công (`true`):** Điều hướng vào màn hình chính `Activity2`.
    * **Thất bại (`false`):** Giữ nguyên tại màn hình Auth, dọn dẹp phiên dang dở (`clearSession`), bật lại nút bấm (`button.isEnabled = true`) và hiển thị thông báo an toàn tiếng Việt từ `strings.xml`:
      ```xml
      <string name="auth_err_token_persistence_failed">Không thể bảo vệ phiên đăng nhập trên thiết bị. Vui lòng thử lại.</string>
      ```
  * Hoàn toàn không còn bất kỳ wrapper hay listener nào nuốt cờ `Boolean` từ hệ thống bảo mật token.

---

## 📊 2. KẾT QUẢ KIỂM THỬ TỔNG HỢP

### A. Backend (llm-gateway3) — Maven Surefire Test
```text
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:08 min
[INFO] Finished at: 2026-09-12T02:43:32+07:00
[INFO] ------------------------------------------------------------------------
[INFO] Results:
[INFO] Tests run: 184, Failures: 0, Errors: 0, Skipped: 0
```
* **Tổng số test:** 184/184 PASSED ($100\%$).
* Thêm mới bài kiểm thử `testStrictVietnameseFinancialTerminologyPolicy` xác thực đầy đủ 6 ca nghiệp vụ siết từ vựng tài chính.

### B. Android (FNMF_Manh_Test) — Gradle Local Unit Tests
```text
BUILD SUCCESSFUL in 45s
27 actionable tasks: 5 executed, 22 up-to-date
```
**Chi tiết 13 tệp XML kết quả test thực tế tại `app/build/test-results/testDebugUnitTest/`:**

| Tệp XML Báo Cáo Test | Tests | Failures | Errors | Skipped | Kết quả |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `TEST-com.example.nhumonglenh.AuthPolishUnitTest.xml` | 11 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.SecureTokenStoreUnitTest.xml` | 15 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.CorrectnessAndIntegrityUnitTest.xml` | 18 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.EmailAuthUnitTest.xml` | 12 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.ExampleUnitTest.xml` | 1 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.NetworkConfigUnitTest.xml` | 8 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.NewsCardPresentationUnitTest.xml` | 15 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.NewsDateFormatUnitTest.xml` | 4 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.NewsLocalizationUnitTest.xml` | 11 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.SandboxPaymentUnitTest.xml` | 18 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.TradingPolishUnitTest.xml` | 32 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.UiPolishUnitTest.xml` | 4 | 0 | 0 | 0 | ✅ PASS |
| `TEST-com.example.nhumonglenh.WalletProfileUnitTest.xml` | 9 | 0 | 0 | 0 | ✅ PASS |
| **TỔNG CỘNG (13 Test Suites)** | **158** | **0** | **0** | **0** | **✅ 100% PASS** |

### C. Android Hardware Connected Tests (Instrumented Test)
* **Trạng thái:** `BLOCKED` (Do môi trường dòng lệnh hiện tại chưa gắn thiết bị Android vật lý hoặc emulator; mã nguồn kiểm thử phần cứng thực tế đã sẵn sàng trong [`SecureTokenStoreAndroidTest.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/androidTest/java/com/example/nhumonglenh/SecureTokenStoreAndroidTest.kt)).

---

## 📦 3. THÔNG SỐ VÀ CHECKSUM APK CANDIDATE RC5

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Tên tệp APK** | `FNMF-v1.1.18-Forecast-Security-Polish-RC5.apk` |
| **Đường dẫn** | `C:\Users\khoid\OneDrive\Desktop\FNMF_Manh_Test\FNMF-v1.1.18-Forecast-Security-Polish-RC5.apk` |
| **Dung lượng** | `8,335,913` bytes (~7.95 MB) |
| **Thuật toán băm** | SHA-256 |
| **Mã băm SHA-256** | `F85E4490EBBF4EA49F621094AA6F971C9255499A3CAD9B45D1E9C3560AED61A9` |
| **Package Name** | `com.example.nhumonglenh` |
| **versionCode** | `18` |
| **versionName** | `1.1.18` |
| **minSdkVersion** | `24` (Android 7.0) |
| **targetSdkVersion** | `35` (Android 15) |
| **Launch Activity** | `com.example.nhumonglenh.Activity1` |

### Xác minh Binary Manifest (`aapt dump xmltree AndroidManifest.xml`):
* `android:allowBackup="false"` (`(type 0x12)0x0`) ✅
* `android:usesCleartextTraffic="false"` (`(type 0x12)0x0`) ✅
* Deep Link Scheme VNPay: `scheme="fnmf"`, `host="payment"`, `pathPrefix="/return"` ✅
* Hoạt động khởi động: `Activity1` (Launcher), `RegisterActivity` (`exported="false"`) ✅

---

## 🛡️ 4. BẢO TOÀN DỮ LIỆU & TÌNH TRẠNG KHO CHỨA

1. **Bảo tồn trọn vẹn 5 file News đang chờ đồng bộ:**
   * [`NewsRepository.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/repository/NewsRepository.kt)
   * [`NewsDetailActivity.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsDetailActivity.kt)
   * [`NewsFeedFragment.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsFeedFragment.kt)
   * [`NewsLocalizationPolicy.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsLocalizationPolicy.kt)
   * [`NewsResponse.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsResponse.kt)
2. **Không ghi đè APK cũ:**
   * `RC1.apk` (`7,208,547` bytes)
   * `RC2.apk` (`8,335,409` bytes)
   * `RC3.apk` (`8,335,409` bytes)
   * `RC4.apk` (`8,335,913` bytes) — SHA-256: `7FBFD7F5...`
   * `RC5.apk` (`8,335,913` bytes) — SHA-256: `F85E4490...` (MỚI)
3. **Kiểm tra `git diff --check`:**
   * Cả 2 repo đều trả về exit code 0.
   * Chỉ xuất hiện các cảnh báo LF/CRLF tự nhiên trên hệ điều hành Windows: `LF will be replaced by CRLF the next time Git touches it`.
4. **Quy tắc phát hành:**
   * Chưa thực hiện commit, push, tag hay deploy.

---

## 🚀 5. KHUYẾN NGHỊ TRIỂN KHAI CHO KHÔI

Theo kết quả kiểm thử RC5:
1. **Mã nguồn Backend (`llm-gateway3`) đã đủ điều kiện triển khai trước:**
   * 184/184 test xanh 100%.
   * Khôi có thể commit và push backend lên `main`, sau đó trigger deploy trên Railway.
   * Cấu hình biến môi trường trên Railway: `OPENAI_DEFAULT_MODEL = gemini-2.5-flash` và cập nhật Alpha Vantage key mới.
2. **Mã nguồn Android (`FNMF_Manh_Test`):**
   * Giữ nguyên Candidate RC5.
   * Tiến hành smoke test API production với backend sau khi deploy xong.
   * Cài đặt file `FNMF-v1.1.18-Forecast-Security-Polish-RC5.apk` lên thiết bị thật để kiểm tra thực tế phần cứng AndroidKeyStore trước khi tạo bản release chính thức.
