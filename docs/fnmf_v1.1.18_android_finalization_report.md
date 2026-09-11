# Báo cáo nghiệm thu Đợt 1: Hoàn tất kiểm thử Android và tạo Release Candidate ký số FNMF v1.1.18

- Ngày thực hiện: 12/09/2026
- Dự án: FNMF_Manh_Test (Android Client)
- Thiết bị kiểm thử: Samsung Galaxy A17 (SM-A175F, Android 16 / SDK 36, Serial R5GYC0DKFPW)
- Phiên bản ứng dụng: versionCode = 18, versionName = 1.1.18
- Phân loại kết quả: PASS (Mã nguồn đạt chuẩn, hoàn thành unit test không cache, hoàn thành connected test trên máy thật bằng bản debug, và tạo thành công release candidate ký số)
- Trạng thái tệp nhị phân release: SIGNED_NOT_DEVICE_TESTED

---

## 1. Bảng tổng hợp tiêu chí nghiệm thu

| Hạng mục | Tiêu chuẩn đánh giá | Trạng thái | Ghi chú kỹ thuật |
| :--- | :--- | :---: | :--- |
| Cấu hình Build Release | debuggable=false, allowBackup=false, usesCleartextTraffic=false, không dùng localhost | ĐẠT | Xác nhận trong AndroidManifest.xml và NetworkConfig.kt. |
| Khóa ký release | Cung cấp Approved Release Keystore và certificate tổ chức | ĐẠT | Ký thành công bằng Release Keystore của Khôi (CN=Dang Duc Khoi, OU=FNMF Mobile, O=FNMF). |
| Kiểm tra mã nguồn UI | Không in Exception thô/stack trace, không lộ secret, không fake/mock data, tiếng Việt tài chính chuẩn | ĐẠT | 100% Toast và UI dùng chuỗi định danh; version lấy từ BuildConfig.VERSION_NAME. |
| Cơ chế lưu trữ Token | Kiểm tra cờ Boolean cho 100% lượt ghi token, chặn chuyển màn hình nếu lỗi lưu | ĐẠT | Đồng bộ tập trung qua AuthFlowCoordinator, rollback phiên an toàn nếu KeyStore từ chối. |
| Unit Tests (No-Cache) | clean testDebugUnitTest --no-daemon --no-build-cache | ĐẠT | 158 / 158 tests PASSED (0 fail, 0 error, 0 skip) trên 13 test suites. |
| Connected Tests (Máy thật) | connectedDebugAndroidTest trên Samsung Galaxy A17 thật | ĐẠT | 10 / 10 tests PASSED (0 fail, 0 error, 0 skip): 9 test nghiệp vụ bảo mật và Room migration, 1 test ngữ cảnh ExampleInstrumentedTest. AndroidKeyStore AES-GCM hoạt động trên thiết bị Samsung thật. |
| Kiểm chứng nâng cấp dữ liệu | Kiểm thử nâng cấp giữ nguyên dữ liệu theo quy trình tách biệt | NOT_VERIFIED | connectedDebugAndroidTest tự động gỡ package sau khi chạy; lần adb install -r ngay sau đó không dùng làm bằng chứng bảo toàn dữ liệu. |
| Manual Smoke Checklist | Danh mục kiểm tra thủ công 9 luồng nghiệp vụ trên màn hình thật | ĐẠT | Đã kiểm tra trực quan trên bản debug candidate RC5: Auth, Trading/WS, Forecast, News, Watchlist, Profile, Deep Link, Xoay ngang/dọc, Insets. |
| Kiểm tra nhị phân Release Candidate | Phân tích SHA-256, Size, aapt badging, binary manifest, signing cert | ĐẠT | Tạo thành công FNMF-v1.1.18-Release-Candidate.apk, debuggable=false, minSdk=24, targetSdk=35, Signature Scheme v2: PASS. |
| Trạng thái tệp nhị phân release | Kiểm thử cài đặt và vận hành trên thiết bị thật | SIGNED_NOT_DEVICE_TESTED | Bản debug RC5 đã được kiểm thử trên máy thật; bản release APK mới chỉ được xác minh bằng aapt và apksigner, chưa smoke-test đầy đủ trên máy thật trước khi release. |

---

## 2. Chi tiết cấu hình Build Release và Manifest

1. Thuộc tính bảo mật ứng dụng (AndroidManifest.xml):
   - android:allowBackup="false": Không cho phép sao lưu dữ liệu bộ nhớ ứng dụng qua ADB hoặc Cloud Backup.
   - android:usesCleartextTraffic="false": Yêu cầu toàn bộ lưu lượng mạng phải chạy qua TLS/HTTPS.
2. Cấu hình endpoint mạng (NetworkConfig.kt):
   - Endpoint mặc định trỏ tới máy chủ sản xuất Railway chính thức:
     ```kotlin
     const val BASE_URL = "https://fnmf-backend-production.up.railway.app/"
     ```
   - Không chứa IP nội bộ mạng LAN (192.168.x.x, 10.x.x.x) hoặc localhost.
3. Cấu hình khóa ký:
   - Cấu hình trong app/build.gradle.kts tự động đọc thông số ký từ biến môi trường hoặc file local.properties:
     - RELEASE_STORE_FILE
     - RELEASE_STORE_PASSWORD
     - RELEASE_KEY_ALIAS
     - RELEASE_KEY_PASSWORD
   - Tuyệt đối không hardcode mật khẩu hay đường dẫn khóa trong build.gradle.kts.
   - File local.properties, *.jks, *.keystore và quy tắc *.apk đều nằm trong .gitignore. Quy tắc *.apk ngăn các file APK mới chưa tracked được thêm ngoài ý muốn vào Git (không tự động loại bỏ các file APK đã tracked từ trước).

---

## 3. Chi tiết kiểm tra mã nguồn app/src/main

1. Giao diện người dùng:
   - Toàn bộ mã nguồn Kotlin (TradingFragment.kt, ForecastFragment.kt, NewsFeedFragment.kt, WalletProfileFragment.kt, Activity1.kt, Activity2.kt) không còn lệnh e.printStackTrace() hoặc hiển thị trực tiếp e.localizedMessage lên Toast hoặc TextView.
   - Lỗi ngoại lệ đều được chuyển thành chuỗi thông điệp thân thiện trong strings.xml.
2. Không rò rỉ thông tin nhạy cảm:
   - Rà soát toàn bộ log Log.d, Log.e, Log.i: Không in Token JWT, mật khẩu hoặc payload chứa thông tin xác thực.
3. Dữ liệu thực tế, không dùng mock hoặc heuristic:
   - Đã loại bỏ các hàm sinh số ngẫu nhiên hoặc heuristic đoán số trên Android. Dữ liệu nến, dự báo AI, tin tức và số dư ví tiêu thụ trực tiếp từ backend.
4. Thuật ngữ tài chính và tiếng Việt:
   - Không dùng emoji trang trí trên giao diện giao dịch, danh mục và ví tiền.
   - Giữ nguyên các mã ticker: BTC, ETH, USD, USDT, VND.
   - Chỉ báo kỹ thuật: RSI, MACD, EMA, SMA.
   - Thuật ngữ tài chính tiếng Việt chuẩn: Hỗ trợ, Kháng cự, Khối lượng, Vị thế mua, Vị thế bán, Kịch bản tham khảo.
5. Định danh phiên bản:
   - WalletProfileFragment hiển thị số phiên bản lấy trực tiếp từ BuildConfig.VERSION_NAME (1.1.18).
6. Bảo vệ phiên đăng nhập:
   - Lưu trữ JWT token ủy quyền cho AuthFlowCoordinator.handleAuthSuccess().
   - Kiểm tra chặt chẽ cờ Boolean. Nếu KeyStore gặp sự cố mã hóa, ứng dụng xóa trạng thái tạm thời, mở lại nút bấm và hiển thị thông báo an toàn, không chuyển màn hình khi phiên chưa lưu thành công.

---

## 4. Kết quả kiểm thử tự động không dùng cache (No-Cache Unit Tests)

- Lệnh thực thi: `gradlew.bat clean testDebugUnitTest --no-daemon --no-build-cache`
- Thời gian thực hiện: 12/09/2026 03:30:47 +07:00
- Thời lượng: 45 giây
- Kết quả đối soát 13 tệp XML tại app/build/test-results/testDebugUnitTest/:

| Tên Test Suite XML | Số Test | Failures | Errors | Skipped | Kết quả |
| :--- | :---: | :---: | :---: | :---: | :---: |
| TEST-com.example.nhumonglenh.AuthPolishUnitTest.xml | 11 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.SecureTokenStoreUnitTest.xml | 15 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.CorrectnessAndIntegrityUnitTest.xml | 18 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.EmailAuthUnitTest.xml | 12 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.ExampleUnitTest.xml | 1 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.NetworkConfigUnitTest.xml | 8 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.NewsCardPresentationUnitTest.xml | 15 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.NewsDateFormatUnitTest.xml | 4 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.NewsLocalizationUnitTest.xml | 11 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.SandboxPaymentUnitTest.xml | 18 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.TradingPolishUnitTest.xml | 32 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.UiPolishUnitTest.xml | 4 | 0 | 0 | 0 | PASS |
| TEST-com.example.nhumonglenh.WalletProfileUnitTest.xml | 9 | 0 | 0 | 0 | PASS |
| TỔNG HỢP (13 Suites) | 158 | 0 | 0 | 0 | 100% PASS |

---

## 5. Kiểm thử thiết bị vật lý (Connected Tests trên Samsung Galaxy A17)

- Thiết bị: Samsung Galaxy A17 (SM-A175F, Android 16, API Level 36, Serial: R5GYC0DKFPW).
- Lệnh thực thi: `gradlew.bat connectedDebugAndroidTest`
- Kết quả: 10 tests completed, 0 failed, 0 skipped (thời gian: 1.621s).

Phân loại 10 bài test connected:
1. 9 test nghiệp vụ bảo mật và di trú cơ sở dữ liệu:
   - SecureTokenStoreAndroidTest.testRealAndroidKeyStoreEncryptAndDecrypt: Mã hóa và giải mã bằng thuật toán AES/GCM/NoPadding qua hệ thống AndroidKeyStore hoạt động chính xác trên thiết bị Samsung Galaxy A17 thật.
   - SecureTokenStoreAndroidTest.testRealAndroidKeyStoreSaveAndGetTokenPersistence: Lưu và đọc token an toàn qua SharedPreferences phối hợp AndroidKeyStore.
   - SecureTokenStoreAndroidTest.testJwtExpirationDetectionOnAndroidRuntime: Phát hiện chính xác JWT hết hạn trên runtime Android thật.
   - SecureTokenStoreAndroidTest.testMalformedTokensSafelyRejectedOnAndroidRuntime: Từ chối an toàn token sai định dạng.
   - RoomMigrationTest.testMigrate2To3_deduplicatesAiAnalysisAndAddsNewsMetadata: Xóa trùng lặp AiAnalysis và bổ sung metadata NewsArticle.
   - RoomMigrationTest.testMigrate3To4_preservesAllNewsAndAiAnalysisWithoutCascadeAndEnablesNullableWatchlist: Giữ nguyên dữ liệu tin tức, ngắt liên kết CASCADE, cho phép Watchlist độc lập.
   - RoomMigrationTest.testMigrate4To5_addsLocalizationColumnsAndPreservesAllData: Bổ sung các cột bản địa hóa titleVi, summaryVi, sentimentVi, bảo toàn dữ liệu cũ.
   - RoomMigrationTest.testMigrate2To4_directMigrationPreservesAllDataAndMatchesSchema: Di trú trực tiếp v2 lên v4 bảo đảm toàn vẹn dữ liệu.
   - RoomMigrationTest.testMigrate2To5_fullMigrationChainPreservesAllDataAndMatchesSchema5: Di trú toàn bộ chuỗi 2→3→4→5 lên schema v5, PRAGMA foreign_key_check đạt 0 lỗi, khớp 100% schema JSON.
2. 1 test kiểm tra ngữ cảnh ứng dụng:
   - ExampleInstrumentedTest.useAppContext: Xác thực context thực thi ứng dụng trên thiết bị SM-A175F.

Lưu ý về kiểm thử nâng cấp dữ liệu: Tiến trình connectedDebugAndroidTest tự động gỡ package sau khi chạy xong, do đó lần chạy adb install -r ngay sau connected tests không được coi là bằng chứng hợp lệ để kết luận bảo toàn dữ liệu. Tiêu chí nâng cấp trực tiếp từ bản cũ được ghi nhận là NOT_VERIFIED cho đến khi chạy riêng quy trình cách ly độc lập. Dù vậy, tính đúng đắn của logic di trú DB đã được xác nhận qua 5 bài test tự động RoomMigrationTest.

---

## 6. Đối soát chỉ số nhị phân Release Candidate đã ký

Tệp APK Release Candidate đã được ký số bằng keystore chính thức:

| Thuộc tính | Chi tiết kiểm định thực tế |
| :--- | :--- |
| Tên tệp tin | FNMF-v1.1.18-Release-Candidate.apk |
| Đường dẫn tệp tin | FNMF_Manh_Test/FNMF-v1.1.18-Release-Candidate.apk |
| Kích thước | 5,810,069 bytes (~5.54 MB) |
| Mã SHA-256 | D0164C5F05F03DD2C635CFC7B707CD7FB68F168A529F4DB6401C5BB3B23C0821 |
| Package Name | com.example.nhumonglenh |
| Version Code | 18 |
| Version Name | 1.1.18 |
| minSdkVersion | 24 |
| targetSdkVersion | 35 |
| android:debuggable | false |
| android:allowBackup | false |
| android:usesCleartextTraffic | false |
| Trạng thái apksigner | Verifies |
| APK Signature Scheme v2 | true (PASS) |
| APK Signature Scheme v1 | false |
| Danh tính Chứng chỉ Ký (DN) | CN=Dang Duc Khoi, OU=FNMF Mobile, O=FNMF, L=Hanoi, ST=Hanoi, C=VN |
| Thuật toán và Độ dài khóa | RSA / 2048 bits |
| Certificate SHA-256 Digest | f19eddeb2acac5dcef5a5f710674cf0ea652fbb5da10233b736ceadc4d4648bc |
| Certificate SHA-1 Digest | 34c1a67fe186c2daa294d751b41291c1d5415db8 |
| Certificate MD5 Digest | 3031260a37841ba46a58d39d74f1961f |
| Public Key SHA-256 Digest | 74d4bd02a0d2a0c4f1bc641629402c609ab1b62d9c0a4f7d5a85723bb6661740 |
| Phân loại kiểm thử nhị phân | SIGNED_NOT_DEVICE_TESTED |

Ghi chú kiểm thử thực tế:
- Bản debug RC5 đã được kiểm thử đầy đủ trên thiết bị thật (Samsung Galaxy A17).
- Bản release candidate ký số (FNMF-v1.1.18-Release-Candidate.apk) đã được xác minh tính hợp lệ bằng apksigner và aapt dump, phân loại trạng thái là SIGNED_NOT_DEVICE_TESTED để phản ánh chính xác phạm vi kiểm thử trước phát hành.

---

## 7. Kết luận Đợt 1

1. Toàn bộ mã nguồn Kotlin và cấu hình Android Client v1.1.18 đã được hoàn thiện.
2. Vượt qua 158/158 unit tests không cache và 10/10 instrumented tests trên Samsung Galaxy A17.
3. Tệp FNMF-v1.1.18-Release-Candidate.apk là bản release candidate đã ký số hợp lệ với danh tính của Khôi, Signature Scheme v2 đạt PASS, cờ debuggable đã tắt.
4. Đợt 1 hoàn thành, chuyển giao sang Đợt 2: Chuẩn hóa tài liệu, triển khai backend và phát hành GitHub Release.
