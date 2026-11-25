# Wusper ETA

香港公共交通到站。原生 Android（Kotlin + Jetpack Compose），介面為繁體中文。

預期公開倉庫：[github.com/wusui1325-dotcom/wusper-eta](https://github.com/wusui1325-dotcom/wusper-eta)  
授權：MIT

## 做什麼

- 附近路線：九巴、城巴、專線小巴（`https://wusper.com/api/eta/nearby-routes`）
- 官方到站：九巴、城巴、專線小巴、港鐵
- 站與路線搜尋
- 地圖：有 Maps 金鑰時用 Google Maps SDK；否則畫站點示意
- 行程：有 Routes 金鑰時用 Google Routes API（步行或公交）。沒有金鑰時只顯示標成「示範」的直線距離，不會改走其他地圖
- 收藏與最近看過的站，只存在這部手機。沒有登入

可疑的名稱旁邊有圓圈問號，說明在按下去之後才出現。

## 冷啟

沒有啟動動畫。第一幀先畫出分頁按鈕，以及上次記住的附近路線（第一次開啟是中環一帶的真實路線）。網路、定位、Google 地圖都在第一幀之後才開始。三分鐘內的到站分鐘會先顯示，並立刻向官方更新。

## 建置

需要 JDK 17+ 與 Android SDK（compileSdk 36）。

```bash
cp local.properties.example local.properties
# 編輯 sdk.dir、MAPS_API_KEY、ROUTES_API_KEY
./gradlew :app:assembleDebug
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安裝：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

套件名稱 `com.wusper.eta`。`minSdk` 26，`targetSdk` 36。手機直向是單欄；寬度約 760dp 以上（平板、或大屏橫向）是左列表、右到站或地圖。

單元測試：

```bash
./gradlew :app:testDebugUnitTest
```

## 金鑰

不要把金鑰寫進 git。只放在本機的 `local.properties`。

| 欄位 | 用途 |
| --- | --- |
| `MAPS_API_KEY` | Maps SDK for Android。限制套件 `com.wusper.eta` 與簽署憑證 SHA-1 |
| `ROUTES_API_KEY` | Routes API。這是裝置發出的 HTTPS，Android 應用限制套用不上。請只開放 Routes API |

Debug 憑證 SHA-1：

```bash
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
```

沒有 Maps 金鑰時，地圖頁是黑白站點示意。沒有 Routes 金鑰時，行程結果標為「示範」。

## 資料

| 資料 | 來源 |
| --- | --- |
| 附近路線、搜尋、沿線站 | `https://wusper.com/api/eta/` |
| 九巴到站 | `https://data.etabus.gov.hk/v1/transport/kmb/` |
| 城巴到站 | `https://rt.data.gov.hk/v2/transport/citybus/` |
| 專線小巴到站 | `https://data.etagmb.gov.hk/` |
| 港鐵到站 | `https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php` |

到站時間只來自官方回應。官方沒有給時間就顯示「暫無」，不會估算。

## 發佈到 GitHub

此目錄已用 MIT，專案名稱是 `wusper-eta`。若要公開成 `wusui1325-dotcom/wusper-eta`：

```bash
# 在 GitHub 建立空的 public repo：wusui1325-dotcom/wusper-eta
git remote add github git@github.com:wusui1325-dotcom/wusper-eta.git
git push -u github main
```

## Build (English)

```bash
cp local.properties.example local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`MAPS_API_KEY` is the Android Maps SDK key (package `com.wusper.eta` plus your SHA-1). `ROUTES_API_KEY` is a Routes API key used from the device over HTTPS; restrict it to the Routes API. Leave either blank to use the on-device fallback: a monochrome stop sketch, or a straight-line distance marked 示範. There is no account system.
