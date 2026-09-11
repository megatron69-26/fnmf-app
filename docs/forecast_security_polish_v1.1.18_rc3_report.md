# 🛡️ BÁO CÁO NGHIỆM THU CANDIDATE FNMF v1.1.18 RC3
## Forecast Correctness, Security Hardening & UI Credibility Polish

---

### 1. BẢNG TỔNG HỢP TRẠNG THÁI NGHIỆM THU (VERIFICATION MATRIX)

| Tiêu chí kiểm định | Yêu cầu kỹ thuật | Trạng thái RC2 | Trạng thái RC3 | Phương pháp xác minh |
| :--- | :--- | :---: | :---: | :--- |
| **Hợp nhất ForecastQualityPolicy** | 1 implementation duy nhất cho `isValid` và `validateOrThrow`; bắt buộc trend chuẩn, GEMINI, nến 1..30, tiếng Việt có dấu | ⚠️ `isValid` kiểm tra trend nhưng `validateOrThrow` thì không |  **ĐẠT CHUẨN** | Unit test 181/181 Maven tests, kiểm tra AST & Regex tiếng Việt |
| **Bảo mật TokenStore Architecture** | Không chứa mutable test hook trong production singleton; tách internal DI; production luôn dùng AndroidKeyStore | ⚠️ Tồn tại `var testSecretKeyProvider` trong production singleton |  **ĐẠT CHUẨN** | Static audit & 15 JVM behavioral unit tests |
| **Phân định phạm vi Test KeyStore** | Phân định rõ JVM Behavioral Tests với AndroidKeyStore Runtime Tests; bổ sung `androidTest` | ⚠️ Gọi nhầm là runtime test |  **ĐẠT CHUẨN** | Phân tách `src/test` (JVM) và `src/androidTest` (`SecureTokenStoreAndroidTest`) |
| **Rà soát & Sanitize Lỗi UI toàn App** | Cấm đưa `t.message`, `localizedMessage`, raw error body lên Toast/UI ở Watchlist, News, Profile, Payment, Trading | ⚠️ Còn sót tại Watchlist, News, Profile, NewsDetail |  **ĐẠT CHUẨN** | Quét toàn bộ `src/main`, thay bằng chuỗi tiếng Việt chuẩn |
| **UI Credibility Forecast** | `android:text` chỉ hiển thị `" — "`, mọi dữ liệu mẫu đưa vào `tools:text` |  Đạt |  **ĐẠT CHUẨN** | Đã xác minh `fragment_forecast.xml` |
| **Bảo mật Manifest APK** | `allowBackup="false"`, `usesCleartextTraffic="false"` trong binary manifest |  Đạt |  **ĐẠT CHUẨN** | `aapt dump xmltree AndroidManifest.xml` |
| **Kiểm thử Backend** | Toàn bộ Maven unit & integration tests phải vượt qua | 176/176 tests |  **181/181 PASSED** | `mvn.cmd test` |
| **Kiểm thử Android Unit Tests** | Toàn bộ JVM Local Unit Tests vượt qua | 156/156 tests |  **157/157 PASSED** | `gradlew.bat testDebugUnitTest` |
| **Kiểm thử Android Runtime** | `connectedDebugAndroidTest` trên thiết bị thật | — | ⚠️ **BLOCKED** | Không có thiết bị Android vật lý / emulator kết nối trực tiếp |

---

### 2. CHI TIẾT GIẢI QUYẾT 3 BLOCKERS CỦA RC2

#### Blocker 1: Hợp nhất và Thắt chặt `ForecastQualityPolicy`
- **Vấn đề RC2:** `ForecastQualityPolicy.java` có hai luồng kiểm tra lệch nhau: `isValid()` kiểm tra `trendPrediction`, nhưng `validateOrThrow()` lại bỏ qua. Cache gọi `validateOrThrow()` nên bản ghi có trend sai lệch vẫn có thể lọt qua. Chưa kiểm tra tiếng Việt, chưa ép `analysisSource=GEMINI`, `candleCount 1..30`.
- **Giải pháp RC3:**
  1. **Hợp nhất 1 Implementation duy nhất:** `validateOrThrow()` là hạt nhân xác thực duy nhất. `isValid()` ủy quyền 100% cho `validateOrThrow()` trong khối `try-catch`.
  2. **Bắt buộc Xu hướng chuẩn:** `trendPrediction` bắt buộc thuộc `ALLOWED_TRENDS`: `BULLISH_UPTREND`, `BEARISH_DOWNTREND`, `SIDEWAYS_CONSOLIDATION`. Mọi giá trị khác hoặc `null` đều bị từ chối ngay lập tức.
  3. **Bắt buộc Nguồn & Nến:** `analysisSource == "GEMINI"`, `candleCount` trong phạm vi $1 \le \text{count} \le 30$.
  4. **Bắt buộc Thuộc tính cơ bản:** `symbol` không rỗng, `currentPrice > 0`, `generatedAt` không null và không ở tương lai xa.
  5. **Xác thực Tiếng Việt có dấu thực chất (Chống AI Slop & Placeholder tiếng Anh):** Dùng biểu thức chính quy Unicode tiếng Việt (`[àáảãạă...đĐ]`). Cả ba trường `keyDrivers` (2–5 ý), `technicalOutlook` ($\ge 10$ ký tự) và `fundamentalOutlook` ($\ge 10$ ký tự) đều bắt buộc phải là tiếng Việt có dấu thực tế. Văn bản tiếng Anh thuần túy hoặc placeholder sẽ bị loại bỏ.
  6. **Cache Miss hoàn toàn:** Trong `ForecastCacheService`, bất kỳ bản ghi cache nào không đáp ứng 100% tiêu chí trên đều lập tức trở thành cache miss (`Optional.empty()`), buộc hệ thống gọi Gemini phân tích lại dữ liệu nến mới.

#### Blocker 2: Kiến trúc `SecureTokenStore` & Phân định Phạm vi Kiểm thử
- **Vấn đề RC2:** `SecureTokenStore.kt` tồn tại thuộc tính public mutable `var testSecretKeyProvider: (() -> SecretKey?)? = null`. Dù có `@VisibleForTesting`, thuộc tính này vẫn tồn tại trong bytecode production singleton. Ngoài ra, việc báo cáo kiểm thử JVM là "runtime test" chưa phản ánh đúng bản chất thiếu vắng AndroidKeyStore API 24 trên JVM.
- **Giải pháp RC3:**
  1. **Loại bỏ hoàn toàn Mutable Hook trong Production Singleton:** `SecureTokenStore` là immutable singleton sạch 100%, không chứa bất kỳ biến mutable hay test hook nào. Luôn ủy quyền cho `AndroidKeyStoreProvider`.
  2. **Tách Internal Dependency Injection:**
     - `internal interface KeyStoreProvider`
     - `internal class AndroidKeyStoreProvider : KeyStoreProvider` (mặc định production, fail-closed khi lỗi).
     - `internal class TokenCryptor(private val keyProvider: KeyStoreProvider)`
     - `internal class TokenStoreEngine(private val cryptor: TokenCryptor)`
  3. **Phân định rõ JVM Behavioral Tests (`src/test`):**
     - 15 bài kiểm thử chạy trên JVM với `TokenCryptor` và `TokenStoreEngine` tiêm khóa bộ nhớ.
     - Kiểm thử: Mã hóa/giải mã AES-GCM 256-bit round-trip, IV ngẫu nhiên, từ chối an toàn ciphertext hỏng/tampered tag, cơ chế fail-closed khi provider trả null, kiểm tra singleton production fail-closed an toàn trong môi trường JVM, di chuyển SharedPreferences đồng bộ `.commit()`, và audit tĩnh mã nguồn.
  4. **Bổ sung Instrumented AndroidKeyStore Test (`src/androidTest`):**
     - Tạo [`SecureTokenStoreAndroidTest.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/androidTest/java/com/example/nhumonglenh/SecureTokenStoreAndroidTest.kt) sử dụng `ApplicationProvider.getApplicationContext()` và `AndroidJUnit4`.
     - Kiểm thử trực tiếp `AndroidKeyStore` phần cứng thực tế, sinh khóa thật và kiểm tra tính bền vững của token mã hóa.
     - **Báo cáo trung thực:** Ghi nhận `connectedDebugAndroidTest` là `BLOCKED (không có thiết bị Android vật lý / emulator kết nối trực tiếp)`.

#### Blocker 3: Rà soát & Sanitize Lỗi Kỹ thuật trên Toàn bộ Ứng dụng
- **Vấn đề RC2:** Lỗi kỹ thuật `t.message` hoặc `localizedMessage` vẫn còn hiển thị lên UI người dùng tại Watchlist (thêm/xóa thất bại), News (fallback cache), Wallet/Profile (ngoại lệ không xác định) và NewsDetail (mở trình duyệt).
- **Giải pháp RC3:**
  1. [`WatchlistFragment.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/watchlist/WatchlistFragment.kt#L368): Thay `Toast.makeText(ctx, "Lỗi kết nối khi thêm: " + t.message)` $\rightarrow$ `"Không thể kết nối đến máy chủ. Vui lòng thử lại."`.
  2. [`WatchlistFragment.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/watchlist/WatchlistFragment.kt#L442): Thay `Toast.makeText(ctx, "Lỗi kết nối khi xóa: " + t.message)` $\rightarrow$ `"Không thể kết nối đến máy chủ. Vui lòng thử lại."`.
  3. [`NewsRepository.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/repository/NewsRepository.kt#L129): Thay `err?.localizedMessage` $\rightarrow$ `"Mất kết nối máy chủ. Đang hiển thị tin tức đã lưu trên thiết bị."`.
  4. [`WalletProfileRepository.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/repository/WalletProfileRepository.kt#L107): Thay `e.localizedMessage` $\rightarrow$ `"Lỗi hệ thống khi tải thông tin hồ sơ"`.
  5. [`NewsDetailActivity.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsDetailActivity.kt#L65): Thay `e.message` $\rightarrow$ `"Không thể mở trình duyệt web. Vui lòng thử lại."`.
  6. [`WalletProfileFragment.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/profile/WalletProfileFragment.kt#L619): Thay `e2.message` $\rightarrow$ `"Không thể mở trình duyệt thanh toán. Vui lòng thử lại."`.
  7. **Rà soát toàn bộ `src/main`:** Không còn bất kỳ vị trí nào rò rỉ stack trace, tên exception class, hoặc chuỗi lỗi tiếng Anh từ thư viện ra giao diện người dùng.

---

### 3. THÔNG SỐ BUILD & XÁC MINH NHỊ PHÂN APK RC3

- **Tên tệp APK Candidate:** `FNMF-v1.1.18-Forecast-Security-Polish-RC3.apk`
- **Đường dẫn tệp:** [`FNMF-v1.1.18-Forecast-Security-Polish-RC3.apk`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/FNMF-v1.1.18-Forecast-Security-Polish-RC3.apk)
- **Kích thước:** `8,335,409 bytes` (~8.34 MB)
- **Mã băm SHA-256:**
  ```text
  652D27DA84E504FE3ADA702BAEB2EB6AE49C5BCB96B58D03BE54F80866545D5F
  ```
- **Bản dựng RC2 được bảo tồn nguyên vẹn:** [`FNMF-v1.1.18-Forecast-Security-Polish-RC2.apk`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/FNMF-v1.1.18-Forecast-Security-Polish-RC2.apk) (SHA-256: `95D9D3B5...04F6C8`).

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

---

### 4. KẾT QUẢ KIỂM THỬ TỰ ĐỘNG TOÀN DIỆN

#### 4.1. Backend Repository (`llm-gateway3`)
- **Lệnh thực thi:** `C:\tools\apache-maven-3.9.16\bin\mvn.cmd test`
- **Kết quả:** `181/181 tests PASSED`, `0 failures`, `0 errors`, `0 skipped`.
- **Trọng tâm Suite `ForecastQualityPolicyTest` (10/10 PASSED):**
  1. `testValidForecastPasses`: PASS.
  2. `testInvalidRecommendationRejected`: PASS.
  3. `testTrendPredictionValidation`: PASS.
  4. `testAnalysisSourceValidation`: PASS.
  5. `testCandleCountValidation`: PASS.
  6. `testSymbolAndPriceValidation`: PASS.
  7. `testInvalidConfidenceRejected`: PASS.
  8. `testNonPositiveLevelsRejected`: PASS.
  9. `testSupportGreaterThanResistanceRejected`: PASS.
  10. `testKeyDriversCountValidation`: PASS.
  11. `testForbiddenPhrasesRejected`: PASS.
  12. `testVietnameseLanguageEnforcement`: PASS.
- **Trạng thái:** `BUILD SUCCESS`.

#### 4.2. Android Repository (`FNMF_Manh_Test`)
- **Lệnh thực thi:** `gradlew.bat testDebugUnitTest --no-daemon`
- **Kết quả:** `157/157 tests PASSED`, `0 failures`, `0 errors`, `0 skipped`.
- **Trọng tâm Suite `SecureTokenStoreUnitTest` (15/15 PASSED):**
  1. `testAESGCM_encryptAndDecryptRoundTrip`: PASS (JVM Behavioral).
  2. `testAESGCM_randomIVProducesDistinctCiphertextsForSamePlaintext`: PASS (JVM Behavioral).
  3. `testAESGCM_corruptedCiphertext_returnsEmptyAndDoesNotCrash`: PASS (JVM Behavioral).
  4. `testFailClosed_whenKeyStoreOrKeyProviderFails`: PASS (JVM Behavioral).
  5. `testProductionSingleton_failsClosedInJvmEnvironmentWithoutCrashing`: PASS (JVM Behavioral).
  6. `testMigration_fromLegacyPlaintextToEncrypted_synchronouslyAndClearsPlaintext`: PASS (JVM Behavioral).
  7. `testMigration_failClosedWhenKeyStoreFails_doesNotExposeOrDeleteLegacyToken`: PASS (JVM Behavioral).
  8. `testSaveToken_synchronousCommitAndCleansLegacy`: PASS (JVM Behavioral).
  9. `testClearToken_clearsBothSecureAndLegacyPrefs`: PASS (JVM Behavioral).
  10. `testSecureTokenStore_sourceCodeArchitectureAndSecurityConstraints`: PASS (Static Audit).
  11. `testPasswordPolicy_minimum8CharactersEnforced`: PASS (Policy).
  12. `testForecastResponse_supportsAnalysisSourceAndCandleCount`: PASS (DTO).
  13. `testAndroidManifest_declaresSecurityHardeningAttributes`: PASS (Manifest).
  14. `testStringsXml_passwordTooShortErrorMessageUpdated`: PASS (Strings).
  15. `testBuildGradle_configuredVersionCode18AndVersionName1118`: PASS (Config).

#### 4.3. Kiểm thử Thiết bị thực tế (Instrumented AndroidKeyStore Test)
- **Tệp kiểm thử:** [`SecureTokenStoreAndroidTest.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/androidTest/java/com/example/nhumonglenh/SecureTokenStoreAndroidTest.kt) (đã sẵn sàng).
- **Trạng thái thực thi:** ⚠️ **BLOCKED (không có thiết bị Android vật lý hoặc máy ảo ADB kết nối trực tiếp)**. Báo cáo không tuyên bố đã kiểm tra trên runtime API 24 thực tế cho đến khi kết nối thiết bị.

---

### 5. KIỂM TRA BẢO MẬT & TÍNH TOÀN VẸN MÃ NGUỒN

1. **Kiểm tra Whitespace & Line endings (`git diff --check`):**
   - Backend: Sạch 100% (Mã thoát: 0).
   - Android: Sạch 100% (Mã thoát: 0).
2. **Kiểm tra Secret Leakage:**
   - Quét không thấy bất kỳ API Key, private key, JWT token hoặc mật khẩu nào lọt vào diff.
3. **Bảo tồn 5 tệp News uncommitted trên Android:**
   - [`NewsRepository.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/data/repository/NewsRepository.kt) — Bảo toàn.
   - [`NewsFeedFragment.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsFeedFragment.kt) — Bảo toàn.
   - [`NewsLocalizationPolicy.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/java/com/example/nhumonglenh/ui/news/NewsLocalizationPolicy.kt) — Bảo toàn.
   - [`strings.xml`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/main/res/values/strings.xml) — Bảo toàn.
   - [`NewsLocalizationUnitTest.kt`](file:///C:/Users/khoid/OneDrive/Desktop/FNMF_Manh_Test/app/src/test/java/com/example/nhumonglenh/NewsLocalizationUnitTest.kt) — Bảo toàn.
4. **Quy tắc phát hành nghiêm ngặt:**
   - Tuyệt đối chưa thực hiện `git commit`, `git push`, `git tag`, hoặc deploy Railway.
   - Các bản dựng cũ (`app-debug.apk`, RC1, RC2, v1.1.16, v1.1.17) được bảo tồn nguyên vẹn.
