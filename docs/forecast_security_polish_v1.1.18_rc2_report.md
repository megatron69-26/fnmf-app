# 🛡️ BÁO CÁO NGHIỆM THU CANDIDATE FNMF v1.1.18 RC2
## Forecast Correctness, Security Hardening & UI Credibility Polish

---

### 1. BẢNG TỔNG HỢP TRẠNG THÁI NGHIỆM THU (VERIFICATION MATRIX)

| Tiêu chí kiểm định | Yêu cầu kỹ thuật | Trạng thái RC1 | Trạng thái RC2 | Phương pháp xác minh |
| :--- | :--- | :---: | :---: | :--- |
| **Bảo mật TokenStore** | Loại bỏ 100% hardcoded fallback key `0x5A` trong `src/main`; Fail-closed khi KeyStore lỗi | ❌ Tồn tại `0x5A` trong production |  **ĐẠT CHUẨN** | Static audit & 14 behavioral unit tests |
| **Migration Token** | Ghi đồng bộ `.commit()`, chỉ xóa plaintext sau khi ghi thành công, không trả legacy token nếu mã hóa lỗi | ⚠️ Dùng `.apply()`, có thể trả plaintext |  **ĐẠT CHUẨN** | Test migration fail-closed & synchronous commit |
| **Toàn vẹn Cache Forecast** | Cấm placeholder string trong `keyDrivers`, lỗi/rỗng -> cache miss, lọc qua `ForecastQualityPolicy` | ❌ Sinh placeholder khi keyDrivers rỗng |  **ĐẠT CHUẨN** | `ForecastServiceAndSecurityTest` (176/176 tests) |
| **Strict JSON Gemini** | `confidenceScore` phải là JSON integer thực (`isIntegralNumber()`), xóa neo số (anchoring) trong prompt | ⚠️ Parse chuỗi, có số 65000/71500/85 |  **ĐẠT CHUẨN** | Kiểm tra AST Jackson & Prompt Audit |
| **UI Credibility Forecast** | `android:text` chỉ hiển thị `" — "`, mọi dữ liệu mẫu đưa vào `tools:text` | ❌ Giữ text runtime `MUA MẠNH`, `95%`... |  **ĐẠT CHUẨN** | Kiểm tra `fragment_forecast.xml` |
| **Bảo mật Manifest APK** | `allowBackup="false"`, `usesCleartextTraffic="false"` trong binary manifest | ⚠️ Chỉ kiểm tra qua mã nguồn |  **ĐẠT CHUẨN** | `aapt dump xmltree AndroidManifest.xml` |
| **Giao diện & Accessibility** | Đăng xuất tập trung tại Hồ sơ, touch target $\ge 48\text{dp}$, sanitize lỗi UI | ⚠️ Nút đăng xuất còn trên top bar |  **ĐẠT CHUẨN** | Kiểm tra Layout & Activity UI code |
| **Cách ly Seed Data** | `news_cache_seed.json` không nằm trong production classpath | ❌ Nằm tại `src/main/resources` |  **ĐẠT CHUẨN** | Di chuyển sang `src/test/resources` |
| **Kiểm thử Backend** | Toàn bộ Maven unit & integration tests phải vượt qua | 145/145 tests |  **176/176 PASSED** | `mvn.cmd test` |
| **Kiểm thử Android** | Toàn bộ Local Unit Tests vượt qua | 142/142 tests |  **156/156 PASSED** | `gradlew.bat testDebugUnitTest` |
| **Kiểm thử Thiết bị thật** | `connectedDebugAndroidTest` | — | ⚠️ **BLOCKED** | Không có thiết bị vật lý / giả lập trực tiếp |

---

### 2. CHI TIẾT GIẢI QUYẾT 4 BLOCKERS TRỌNG YẾU

#### Blocker 1: `SecureTokenStore` - Xóa bỏ hoàn toàn khóa phần mềm cố định & Thiết kế Fail-Closed
- **Vấn đề RC1:** Tại [`SecureTokenStore.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/local/SecureTokenStore.kt#L65), khi `AndroidKeyStore` gặp lỗi, code tự tạo `SecretKeySpec(ByteArray(32) { 0x5A }, "AES")`. Điều này tạo ảo giác an toàn giả tạo và cực kỳ nguy hiểm trong production.
- **Giải pháp RC2:**
  1. **Xóa sạch 100%:** Toàn bộ mã nguồn `src/main` không còn bất kỳ byte `0x5A` hay `ByteArray(32)` nào.
  2. **Thiết kế Fail-Closed:** Khi `AndroidKeyStore` lỗi hoặc không sinh được khóa:
     - `getOrCreateSecretKey()` trả về `null`.
     - `encrypt()` và `decrypt()` trả về chuỗi rỗng `""`.
     - Ứng dụng từ chối lưu hoặc giải mã, buộc xóa phiên và chuyển hướng người dùng đăng nhập lại an toàn.
  3. **Đồng bộ hóa ghi SharedPreferences:** Sử dụng `.commit()` thay cho `.apply()` trong `saveToken()`, `clearToken()` và `clearLegacyPlaintextToken()`.
  4. **Bảo toàn tính toàn vẹn khi Migration:**
     - Chỉ xóa token plaintext cũ sau khi xác nhận `.commit()` mã hóa ghi thành công vào đĩa.
     - Nếu mã hóa thất bại, `getToken()` trả về `""`, tuyệt đối không trả lại token plaintext thô.
  5. **Khả năng kiểm thử cô lập (Testability):** Cung cấp hook `@VisibleForTesting var testSecretKeyProvider: (() -> SecretKey?)? = null` chỉ dùng riêng cho JUnit test môi trường JVM để tiêm khóa kiểm thử bộ nhớ mà không đưa khóa vào production binary.

#### Blocker 2: `ForecastCacheService` - Xóa Placeholder & Thắt chặt chất lượng Cache
- **Vấn đề RC1:** Nếu `keyDrivers` rỗng, cache tự chèn chuỗi `"Thông tin đang được cập nhật từ chuyên gia định lượng."`. Dữ liệu cache không được kiểm tra lại qua bộ lọc chất lượng trước khi trả về.
- **Giải pháp RC2:**
  1. **Triệt tiêu Placeholder:** Xóa hoàn toàn câu chữ placeholder. Nếu `keyDrivers` null hoặc rỗng, hàm parse trả về `List.of()`.
  2. **Cache Miss khi thiếu động lực:** Nếu `keyDrivers.isEmpty()`, `ForecastCacheService.getFreshForecast()` log cảnh báo và trả về `Optional.empty()` (coi như cache miss) để hệ thống gọi Gemini phân tích lại dữ liệu thực tế.
  3. **Kiểm định chất lượng 2 lớp:** Mọi bản ghi lấy từ cache phải vượt qua `ForecastQualityPolicy.validateOrThrow(response)` trước khi trả về. Nếu cache bị lỗi thời hoặc không đạt ngưỡng, hệ thống lập tức loại bỏ bản ghi cache đó.

#### Blocker 3: `GeminiForecastClient` - Thắt chặt kiểu dữ liệu & Chống Anchoring AI
- **Vấn đề RC1:** `confidenceScore` cho phép parse từ chuỗi; system prompt chứa các con số cụ thể (`65000`, `71500`, `85`) gây hiện tượng neo số (anchoring bias); prompt dùng từ "chính xác".
- **Giải pháp RC2:**
  1. **Strict Integral Number:** `confNode.isIntegralNumber()` — từ chối mọi dạng string hoặc float cho `confidenceScore`.
  2. **Strict Field Types:** Bắt buộc `isNumber()` cho `supportLevel`/`resistanceLevel`, `isTextual()` cho `trendPrediction`/`recommendation`/`technicalOutlook`/`fundamentalOutlook`, `isArray()` cho `keyDrivers`.
  3. **Chống Anchoring:** Thay toàn bộ các số ví dụ bằng mô tả cấu trúc JSON thuần túy (`<số nguyên từ 50 đến 99>`, `<số thập phân vùng hỗ trợ>`, `<số thập phân vùng kháng cự>`).
  4. **Điều chỉnh ngôn từ định lượng:** Thay "chính xác" bằng "ước lượng vùng giá Support và Resistance hợp lý dựa trên biến động nến thực tế".
  5. **Khả năng chịu lỗi cao:** Khi Gemini phản hồi null hoặc lỗi, `ForecastService` ném `ForecastUnavailableException` để Global Exception Handler trả HTTP 503 kèm JSON lỗi an toàn.

#### Blocker 4: Android UI Credibility & Giao diện người dùng thực tế
- **Vấn đề RC1:** File `fragment_forecast.xml` đặt cứng các giá trị `MUA MẠNH`, `Độ tin cậy: 95%`, `62,000`, `68,000` trong `android:text`. Nút Đăng xuất nằm ở top bar header gây rối mắt. Lỗi hiển thị thô `t.message` hoặc `t.localizedMessage`.
- **Giải pháp RC2:**
  1. **Reset Text mặc định:** Toàn bộ `android:text` của các trường dự báo được đặt thành `" — "`. Toàn bộ dữ liệu minh họa chuyển sang namespace `tools:text` chỉ hiển thị trong Android Studio Preview.
  2. **Tối ưu vị trí Đăng xuất:** Xóa nút Đăng xuất khỏi top bar [`layout_activity2.xml`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/res/layout/layout_activity2.xml). Di chuyển vào tab Hồ sơ [`fragment_wallet_profile.xml`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/res/layout/fragment_wallet_profile.xml) với kích thước chuẩn Accessibility (`minHeight="48dp"`, `minWidth="48dp"`), kích hoạt hộp thoại xác nhận phong cách TradingView.
  3. **Sanitize Error UI:**
     - Đăng nhập ([`Activity1.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/Activity1.kt)): Thông báo tiếng Việt thân thiện, không lộ stack trace hay URL server.
     - Đăng ký ([`RegisterActivity.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/RegisterActivity.kt)): Không hiển thị raw response body hoặc exception message.
     - Nạp tiền Sandbox ([`SandboxPaymentBottomSheet.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/payment/SandboxPaymentBottomSheet.kt)): Lọc sạch các mã lỗi hệ thống trước khi báo người dùng.

---

### 3. THÔNG SỐ BUILD & XÁC MINH NHỊ PHÂN APK RC2

- **Tên tệp APK Candidate:** `FNMF-v1.1.18-Forecast-Security-Polish-RC2.apk`
- **Đường dẫn tệp:** [`FNMF-v1.1.18-Forecast-Security-Polish-RC2.apk`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/FNMF-v1.1.18-Forecast-Security-Polish-RC2.apk)
- **Kích thước:** `8,335,409 bytes` (~8.34 MB)
- **Mã băm SHA-256:**
  ```text
  95D9D3B5E7979113DA4855C6F8B92A7349BAE269A32DDAD648CCF6FC8404F6C8
  ```

#### Bằng chứng xác minh `aapt dump badging`:
```text
package: name='com.example.nhumonglenh' versionCode='18' versionName='1.1.18' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'
application-label:'FNMF'
launchable-activity: name='com.example.nhumonglenh.Activity1'  label='' icon=''
```

#### Bằng chứng xác minh cấu trúc Binary Manifest (`aapt dump xmltree AndroidManifest.xml`):
```text
A: android:allowBackup(0x01010280)=(type 0x12)0x0
A: android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0
```
> *(type 0x12)0x0 khẳng định cả hai thuộc tính đã được biên dịch thành giá trị boolean `false` trực tiếp trong tệp nhị phân của APK).*

---

### 4. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG TOÀN DIỆN

#### 4.1. Backend Repository (`llm-gateway3`)
- **Lệnh thực thi:** `C:\tools\apache-maven-3.9.16\bin\mvn.cmd test`
- **Kết quả:** `176/176 tests PASSED`, `0 failures`, `0 errors`, `0 skipped`.
- **Thời gian chạy:** `13.593 s`. `BUILD SUCCESS`.

#### 4.2. Android Repository (`FNMF_Manh_Test`)
- **Lệnh thực thi:** `gradlew.bat testDebugUnitTest --no-daemon`
- **Kết quả:** `156/156 tests PASSED`, `0 failures`, `0 errors`, `0 skipped`.
- **Trọng tâm Suite `SecureTokenStoreUnitTest` (14/14 PASSED):**
  1. `testAESGCM_encryptAndDecryptRoundTrip`: PASS.
  2. `testAESGCM_randomIVProducesDistinctCiphertextsForSamePlaintext`: PASS.
  3. `testAESGCM_corruptedCiphertext_returnsEmptyAndDoesNotCrash`: PASS.
  4. `testFailClosed_whenKeyStoreOrKeyProviderFails`: PASS.
  5. `testMigration_fromLegacyPlaintextToEncrypted_synchronouslyAndClearsPlaintext`: PASS.
  6. `testMigration_failClosedWhenKeyStoreFails_doesNotExposeOrDeleteLegacyToken`: PASS.
  7. `testSaveToken_synchronousCommitAndCleansLegacy`: PASS.
  8. `testClearToken_clearsBothSecureAndLegacyPrefs`: PASS.
  9. `testSecureTokenStore_sourceCodeArchitectureAndSecurityConstraints`: PASS.
  10. `testPasswordPolicy_minimum8CharactersEnforced`: PASS.
  11. `testForecastResponse_supportsAnalysisSourceAndCandleCount`: PASS.
  12. `testAndroidManifest_declaresSecurityHardeningAttributes`: PASS.
  13. `testStringsXml_passwordTooShortErrorMessageUpdated`: PASS.
  14. `testBuildGradle_configuredVersionCode18AndVersionName1118`: PASS.

#### 4.3. Kiểm thử Thiết bị thực tế
- **Trạng thái:** `BLOCKED (không có thiết bị Android vật lý hoặc máy ảo ADB kết nối trực tiếp)`.

---

### 5. KIỂM TRA BẢO MẬT & TÍNH TOÀN VẸN MÃ NGUỒN

1. **Kiểm tra Whitespace & Line endings:**
   - Backend: `git diff --check` -> Clean (Mã thoát: 0).
   - Android: `git diff --check` -> Clean (Mã thoát: 0).
2. **Kiểm tra Secret Leakage:**
   - Quét không thấy bất kỳ API Key, private key, JWT token hoặc mật khẩu nào lọt vào diff.
3. **Bảo tồn 5 tệp News uncommitted trên Android:**
   - [`NewsRepository.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/repository/NewsRepository.kt) — Bảo toàn.
   - [`NewsFeedFragment.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsFeedFragment.kt) — Bảo toàn.
   - [`NewsLocalizationPolicy.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsLocalizationPolicy.kt) — Bảo toàn.
   - [`strings.xml`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/res/values/strings.xml) — Bảo toàn.
   - [`NewsLocalizationUnitTest.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/test/java/com/example/nhumonglenh/NewsLocalizationUnitTest.kt) — Bảo toàn.
4. **Quy tắc phát hành:**
   - Không thực hiện bất kỳ lệnh `git commit`, `git push`, `git tag`, hoặc deploy Railway nào.
   - Các bản dựng cũ (`app-debug.apk`, RC1, v1.1.16, v1.1.17) được bảo tồn nguyên vẹn.
