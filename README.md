# FNMF Mobile Client — Ung dung Android Tin tuc Tai chinh & Giao dich Mo phong

Financial News & Market Forecasting (FNMF) Mobile Client la ung dung di dong Android ho tro nha dau tu theo doi thi truong, cap nhat tin tuc tai chinh quoc te duoc phan tich boi tri tue nhan tao Google Gemini, va thuc hanh giao dich gia lap (Paper Trading).

- **Phien ban ung dung:** v1.1.18 (`versionCode = 18`, `versionName = "1.1.18"`)
- **Thanh vien thuc hien:**
  - Nguyen Quang Hung (Member #2 - Android Developer / UI, MPAndroidChart, Retrofit)
  - Nguyen Huu Manh (Member #1 / Leader - Prompt AI, Room Database & Offline Cache)
  - Dang Duc Khoi (Member #3 - Backend, Data Pipeline & Android Integration)
- **Nen tang cong nghe:**
  - Ngon ngu: Kotlin
  - Yeu cau he dieu hanh: Android SDK minSdk 24 (Android 7.0), targetSdk 35 (Android 15)
  - Kien truc: MVVM (Model - View - ViewModel)
  - Mang va API: Retrofit 2, OkHttp 3, Binance WebSocket Client
  - Bieu do ky thuat: MPAndroidChart (nen Nhat OHLCV thoi gian thuc)
  - Co so du lieu cuc bo: Room Database (SQLite) luu tru offline tin tuc va danh muc theo doi
  - Bao mat: AndroidKeyStore voi thuat toan AES-GCM 256-bit ma hoa token phien JWT, Network Security Config chan cleartext HTTP
- **Backend Production Endpoint:** `https://fnmf-backend-production.up.railway.app/`

---

## 1. Cac Man hinh va Tab Chuc nang (5 Bottom Navigation Tabs)

Ung dung duoc thiet ke toi gian, truc quan voi thanh dieu huong duoi gom 5 tab chuc nang:

### 1. Tab Giao dich (Trading)
- Ve bieu do nen Nhat ky thuat 30 ngay su dung `MPAndroidChart` tu du lieu Binance REST Klines.
- Ket noi Binance WebSocket de nhan tick gia live cap nhat bieu do va gia header voi do tre < 100ms.
- Phieu dat lenh Mua (`BUY`) va Ban (`SELL`) Paper Trading voi so du von ao ban dau $10,000.00 USD.
- Hien thi gia khop authoritative tu backend, ty le loi/lo (PnL) thoi gian thuc, va so du vi kha dung.

### 2. Tab Du bao AI (Market Forecast)
- Truy van ban du bao xu huong da chieu tu backend qua Google Gemini 3.6 Flash (`GET /api/forecast/{symbol}?timeframe=24H_7D`).
- Hien thi xu huong du bao (`BULLISH_UPTREND`, `BEARISH_DOWNTREND`, `SIDEWAYS`).
- Cung cap nguong Ho tro ky thuat (`supportLevel`), Khang cu ky thuat (`resistanceLevel`), Khuyen nghi hanh dong (`STRONG_BUY`, `BUY`, `HOLD`, `SELL`), va Diem tin cay AI.
- Quan ly vong doi an toan: huy request khi `onDestroyView()`, khong bao gio sinh du lieu gia mo phong tren client.

### 3. Tab Tin tuc AI (Financial News)
- Dong bo ban tin tai chinh quoc te tu Alpha Vantage qua backend (`GET /api/news/sync`).
- Tieu de duoc dich sang tieng Viet tu nhien, noi dung su kien tom tat thanh 2 den 4 bullet ro rang tu Gemini AI.
- Gan nhan cam xuc thi truong (`BULLISH`, `BEARISH`, `NEUTRAL`), diem tin cay, va ly do phan tich kinh te vi mo.
- Tu dong upsert va duy tri Room Database cuc bo, ho tro doc offline khi thiet bi mat ket noi Internet.

### 4. Tab Danh muc theo doi (Watchlist)
- Quan ly danh muc tai san quan tam cua nguoi dung (Cloud CRUD: xem, them, xoa ma theo doi).
- Tu dong lam giau gia truc tuyen va ty le bien dong 24h.
- Luu tru bo nho dem Room DB cuc bo phan tach theo `userEmail`, bao dam khi doi tai khoan khong bi lan lon du lieu.

### 5. Tab Vi & Ho so (Wallet & Profile)
- Hien thi thong tin tai khoan email-only, vai tro, va so du vi tien mat hien tai.
- Tich hop module Nap/Rut tien mo phong (Sandbox Banking):
  - Nhap so tien (scale <= 2, toi da $100,000.00 USD).
  - Khoi chay Chrome Custom Tabs mo giao dien Hosted Checkout voi token co han 15 phut.
  - Tu dong lam moi so du vi va hien thi lich su so cai bien dong `WALLET_LEDGER` khi quay lai ung dung.
- Ho tro dieu huong Deep Link: `fnmf://app/*` (vi du: `fnmf://app/trading`, `fnmf://app/wallet`).

---

## 2. Huong dan Bien dich va Cai dat

### 1. Mo du an trong Android Studio
1. Khoi dong Android Studio (ban Koala, Ladybug hoac moi hon).
2. Chon **Open** va tro toi thu muc chua ma nguon Android (`FNMF_Manh_Test`).
3. Cho Android Studio va Gradle hoan tat dong bo dependencies.

### 2. Chay Unit Tests
Tren terminal cua Android Studio hoac CMD:
```bash
gradlew testDebugUnitTest --no-build-cache
```

### 3. Bien dich Ban Release Candidate
Tao file APK release duoc ky so bang keystore:
```bash
gradlew assembleRelease
```
File APK dau ra duoc tao tai:
`app/build/outputs/apk/release/app-release.apk`
(Hoac ban sao luu: `FNMF-v1.1.18-Release-Candidate.apk` tai thu muc goc du an).

### 4. Kiem tra Chu ky so va Tuan thu Bao mat APK
Su dung cong cu `apksigner` cua Android SDK Build-Tools:
```bash
apksigner verify --verbose --print-certs FNMF-v1.1.18-Release-Candidate.apk
```
Tieu chuan xac nhan:
- Verified using v2 scheme: `true`
- Certificate SHA-256: `f19eddeb2acac5dcef5a5f710674cf0ea652fbb5da10233b736ceadc4d4648bc`
- `android:debuggable = false`
- `minSdkVersion = 24`, `targetSdkVersion = 35`

### 5. Cai dat len Thiet bi Android
Ket noi thiet bi qua USB debugging va chay lenh:
```bash
adb install -r FNMF-v1.1.18-Release-Candidate.apk
```
