# 📋 BÁO CÁO NGHIỆM THU CANDIDATE FNMF v1.1.18 RC4
## Forecast Correctness, Security Hardening & UI Credibility Polish

> **Ngày thực hiện:** 12/09/2026  
> **Phiên bản:** `1.1.18` (Build `versionCode = 18`, `versionName = "1.1.18"`)  
> **Trạng thái:** ✅ SẴN SÀNG ĐÁNH GIÁ (Candidate RC4 đã vượt qua 100% bài kiểm thử)  
> **Quy tắc tuân thủ:** Không commit, không push, không tag, không release, không deploy Railway, không ghi đè bất kỳ file APK cũ nào (`RC1`, `RC2`, `RC3`), bảo toàn trọn vẹn 5 file News đang chờ đồng bộ.

---

## 🎯 1. TỔNG QUAN CÁC BLOCKER ĐÃ GIẢI QUYẾT TRONG RC4

### 🟢 Blocker 1: Forecast Mixed-Language Validation (Chống Slop Tiếng Anh Lồng Tiếng Việt)
* **Vấn đề tồn đọng trước RC4:** Policy cũ chỉ kiểm tra sự tồn tại của ít nhất 1 ký tự tiếng Việt có dấu (`regex find`). Điều này dẫn đến lỗ hổng chấp nhận các câu thuần tiếng Anh chỉ chèn 1 từ tiếng Việt (ví dụ: `"Nvidia reports quarterly revenue tăng"`, `"Market outlook bullish nhưng volatile"`).
* **Giải pháp trong RC4 (`ForecastQualityPolicy.java`):**
  * Tách từ (tokenization) và phân loại từ vựng chuyên sâu:
    1. **Tập từ trung tính tài chính (`ALLOWED_FINANCIAL_TOKENS`):** Miễn trừ hợp lệ cho các mã ticker, tên thương hiệu và từ viết tắt nghiệp vụ chuẩn: `BTC`, `ETH`, `USD`, `USDT`, `VND`, `ETF`, `RSI`, `MACD`, `FED`, `SEC`, `FOMC`, `GDP`, `CPI`, `DXY`, `EMA`, `SMA`, `Nvidia`, `Apple`, `Microsoft`, `Tesla`, `Binance`, `Coinbase`, `FNMF`, `Bitcoin`, `Ethereum`, `Solana`, `crypto`, `blockchain`, `bullish`, `bearish`, `sideways`, `support`, `resistance`, `long`, `short`, `volume`, `ohlcv`, `fibo`, `fibonacci`, `bollinger` cùng các con số/tỷ lệ `%`.
    2. **Tập từ tiếng Anh đặc trưng (`COMMON_ENGLISH_WORDS`):** Nhận diện các từ vựng tiếng Anh điển hình (`reports`, `reported`, `revenue`, `quarterly`, `market`, `outlook`, `volatile`, `earnings`, `growth`, `sentiment`, `analysts`, `forecast`, `expectations`, `the`, `is`, `and`...).
    3. **Tiêu chuẩn tiếng Việt thực chất (`isSubstantialVietnamese`):**
       * Phải có $\ge 2$ từ tiếng Việt có dấu.
       * Số từ tiếng Việt có dấu phải vượt trội so với số từ tiếng Anh (`vietnameseDiacriticWordCount > englishWordCount`).
       * Tỷ lệ từ tiếng Việt có dấu trên tổng số từ không trung tính phải đạt tối thiểu $35\%$.
  * Áp dụng thống nhất cho toàn bộ các trường nội dung: `technicalOutlook`, `fundamentalOutlook`, và từng ý trong `keyDrivers`.
  * `isValid()` ủy quyền duy nhất cho `validateOrThrow()`, loại bỏ hoàn toàn tình trạng validation hai chuẩn khác nhau.
* **Kết quả kiểm thử hành vi thực tế (`ForecastQualityPolicyTest.java`):**
  * ❌ Từ chối: `"Nvidia reports quarterly revenue tăng"`
  * ❌ Từ chối: `"Market outlook bullish nhưng volatile"`
  * ✅ Chấp nhận: `"Nvidia công bố doanh thu quý tăng trưởng mạnh và vượt kỳ vọng."`
  * ✅ Chấp nhận: `"Bitcoin ETF ghi nhận dòng vốn tích cực trong các phiên gần đây."`
  * ✅ Chấp nhận: Tiếng Việt chứa `BTC`, `ETH`, `USD`, `RSI`, `MACD` (`"Chỉ báo RSI và MACD của BTC và ETH theo cặp USD đang phát tín hiệu tích cực."`).

---

### 🟢 Blocker 2: Secure Token Persistence Result & Auth Flow (Xử Lý Lưu Token An Toàn & Điều Hướng)
* **Vấn đề tồn đọng trước RC4:** `SecureTokenStore.saveToken()` trả về `Boolean` nhưng `AuthSessionManager` và màn hình đăng nhập/đăng ký bỏ qua kết quả này (trả về `Unit`). Nếu Android KeyStore hoặc ổ đĩa bị lỗi, ứng dụng vẫn tự chuyển sang `Activity2` với token rỗng, dẫn đến vòng lặp logout hoặc lỗi dữ liệu.
* **Giải pháp trong RC4:**
  * `AuthSessionManager.saveToken(context, token): Boolean`: Trả về kết quả thực tế từ `SecureTokenStore.saveToken(context, token)`. Chỉ xóa cờ `isHandlingUnauthorized` khi lưu thành công.
  * Thiết kế lớp điều phối trung tâm **`AuthFlowCoordinator.kt`** theo nguyên tắc **Fail-Closed**:
    * Chỉ trả về `PersistenceOutcome.NavigateToMain` khi `tokenSaver` thành công ($= true$).
    * Khi `tokenSaver` thất bại ($= false$) hoặc token rỗng/null: trả về `PersistenceOutcome.StayOnAuth`:
      1. Tuyệt đối **KHÔNG điều hướng** sang `Activity2`.
      2. Tự động dọn dẹp phiên dang dở (`AuthSessionManager.clearSession`).
      3. Kích hoạt lại nút gửi (`button.isEnabled = true`) và phục hồi text nút.
      4. Hiển thị thông báo tiếng Việt an toàn từ `strings.xml`:
         ```xml
         <string name="auth_err_token_persistence_failed">Không thể bảo vệ phiên đăng nhập trên thiết bị. Vui lòng thử lại.</string>
         ```
         (Không làm lộ chi tiết ngoại lệ KeyStore hay lỗi hạ tầng ra UI).
  * Tích hợp đồng bộ trong cả `Activity1.kt` (Đăng nhập) và `RegisterActivity.kt` (Đăng ký).
* **Kết quả kiểm thử hành vi (Behavioral Unit Test):**
  * Viết kiểm thử hành vi độc lập `testLoginAndRegisterPersistenceOutcomesBehavioral` trong `AuthPolishUnitTest.kt`, kiểm thử trực tiếp các nhánh kết quả logic bằng mock context và state assertions (hoàn toàn không dùng source-scanning).

---

## 📊 2. KẾT QUẢ KIỂM THỬ TỔNG HỢP

### A. Backend (llm-gateway3) — Maven Surefire Test
```text
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:13 min
[INFO] Finished at: 2026-09-12T02:20:05+07:00
[INFO] ------------------------------------------------------------------------
[INFO] Results:
[INFO] Tests run: 183, Failures: 0, Errors: 0, Skipped: 0
```
* **Tổng số test:** 183/183 PASSED ($100\%$).
* **Test suites tiêu biểu:**
  * `ForecastQualityPolicyTest`: Kiểm thử chuẩn hóa tiếng Việt thực chất, chặn mixed-language, kiểm duyệt 12 tiêu chí chất lượng.
  * `ForecastServiceAndSecurityTest`: Kiểm duyệt Gemini integration, cache service, failover và bảo mật.
  * `AdminSecurityAndRoleTest`: 35 tests phân quyền và audit trail.
  * `ConcurrentTradeIdempotencyIntegrationTest`: Idempotency và race conditions.
  * `VnPaySandboxPaymentTest`: 24 tests thanh toán và đối soát.

### B. Android (FNMF_Manh_Test) — Gradle Local Unit Tests
```text
BUILD SUCCESSFUL in 1m 7s
27 actionable tasks: 5 executed, 22 up-to-date
```
| Suite | Số lượng Test | Thất bại | Kết quả |
| :--- | :---: | :---: | :---: |
| `AuthPolishUnitTest` | 11 | 0 | ✅ PASS |
| `SecureTokenStoreUnitTest` | 15 | 0 | ✅ PASS |
| `CorrectnessAndIntegrityUnitTest` | 18 | 0 | ✅ PASS |
| `EmailAuthUnitTest` | 12 | 0 | ✅ PASS |
| `ExampleUnitTest` | 1 | 0 | ✅ PASS |
| `NetworkConfigUnitTest` | 8 | 0 | ✅ PASS |
| `NewsCardPresentationUnitTest` | 15 | 0 | ✅ PASS |
| `NewsDateFormatUnitTest` | 4 | 0 | ✅ PASS |
| `NewsLocalizationUnitTest` | 11 | 0 | ✅ PASS |
| `SandboxPaymentUnitTest` | 18 | 0 | ✅ PASS |
| `TradingPolishUnitTest` | 32 | 0 | ✅ PASS |
| `UiPolishUnitTest` | 4 | 0 | ✅ PASS |
| `WalletProfileUnitTest` | 9 | 0 | ✅ PASS |
| **TỔNG CỘNG** | **158** | **0** | **✅ 100% PASS** |

### C. Android Hardware Connected Tests (Instrumented Test)
* **Trạng thái:** `BLOCKED` (môi trường kiểm thử tự động hiện tại không gắn thiết bị thật/emulator).
* **Mã nguồn kiểm thử:** Đã sẵn sàng trong [`SecureTokenStoreAndroidTest.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/androidTest/java/com/example/nhumonglenh/SecureTokenStoreAndroidTest.kt) để kiểm tra phần cứng AndroidKeyStore thực tế khi cắm máy.

---

## 📦 3. THÔNG SỐ VÀ CHECKSUM APK CANDIDATE RC4

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Tên tệp APK** | `FNMF-v1.1.18-Forecast-Security-Polish-RC4.apk` |
| **Đường dẫn** | `C:\Users\khoid\OneDrive\Desktop\FNMF_Manh_Test\FNMF-v1.1.18-Forecast-Security-Polish-RC4.apk` |
| **Dung lượng** | `8,335,913` bytes (~7.95 MB) |
| **Thuật toán băm** | SHA-256 |
| **Mã băm SHA-256** | `7FBFD7F5A63D24F2C3A234C7D14DB8BD5DD7183E82A7C85902B347F3DB70CB2F` |
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
   * `app/src/main/java/com/example/nhumonglenh/data/repository/NewsRepository.kt`
   * `app/src/main/java/com/example/nhumonglenh/ui/news/NewsDetailActivity.kt`
   * `app/src/main/java/com/example/nhumonglenh/ui/news/NewsFeedFragment.kt`
   * `app/src/main/java/com/example/nhumonglenh/ui/news/NewsLocalizationPolicy.kt`
   * `app/src/main/java/com/example/nhumonglenh/ui/news/NewsResponse.kt`
2. **Không ghi đè APK cũ:**
   * `FNMF-v1.1.18-Forecast-Security-Polish-RC1.apk` (7,208,547 bytes)
   * `FNMF-v1.1.18-Forecast-Security-Polish-RC2.apk` (8,335,409 bytes)
   * `FNMF-v1.1.18-Forecast-Security-Polish-RC3.apk` (8,335,409 bytes)
   * `FNMF-v1.1.18-Forecast-Security-Polish-RC4.apk` (8,335,913 bytes) — MỚI
3. **Kiểm tra `git diff --check`:**
   * Cả 2 repo đều trả về exit code 0.
   * Chỉ xuất hiện các cảnh báo LF/CRLF tự nhiên trên hệ điều hành Windows (được báo cáo chi tiết).
4. **Quy tắc phát hành:**
   * Không thực hiện `git commit`, `git push`, `git tag`, hoặc deploy Railway.

---

## 🚀 5. HƯỚNG DẪN TRIỂN KHAI CHO USER (KHÔI)

Khi Khôi sẵn sàng chốt bản phát hành chính thức `1.1.18`:
1. Kiểm tra lại diff và log test của 2 repository.
2. Thực hiện commit đồng bộ trên Backend và Android.
3. Push Backend lên GitHub `origin/main` và deploy lên Railway.
4. Đảm bảo cấu hình biến môi trường trên Railway:
   * `OPENAI_DEFAULT_MODEL = gemini-2.5-flash` (hoặc model Gemini tương thích).
   * Cập nhật Alpha Vantage API key mới không bị cạn quota.
5. Cài đặt `FNMF-v1.1.18-Forecast-Security-Polish-RC4.apk` lên thiết bị thật để nghiệm thu trực tiếp luồng lưu trữ bảo mật và dự báo thị trường.
