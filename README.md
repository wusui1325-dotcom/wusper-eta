# Wusper ETA

香港公共交通到站。原生 Android（Kotlin + Jetpack Compose），介面為繁體中文。

預期公開倉庫：[github.com/wusui1325-dotcom/wusper-eta](https://github.com/wusui1325-dotcom/wusper-eta)  
授權：MIT

## 做什麼

- 附近路線：九巴、城巴、專線小巴（`https://wusper.com/api/eta/nearby-routes`）。港鐵分頁同時列出重鐵與輕鐵
- 官方到站：九巴、城巴、專線小巴、港鐵、輕鐵
- 站與路線搜尋
- 地圖：Google Maps SDK，使用預設彩色底圖。金鑰只放在本機 `local.properties`
- 行程：`POST https://wusper.com/api/eta/compute-routes`。沒有路徑時顯示「未能規劃」
- 收藏與最近看過的站，只存在這部手機。沒有登入

可疑的名稱旁邊有圓圈問號，說明在按下去之後才出現。

## 冷啟

沒有啟動動畫。第一幀先畫出分頁按鈕，以及上次記住的附近路線（第一次開啟是中環一帶的真實路線）。網路、定位、Google 地圖都在第一幀之後才開始。三分鐘內的到站分鐘會先顯示，並立刻向官方更新。

## 建置

需要 JDK 17+ 與 Android SDK（compileSdk 36）。

```bash
cp local.properties.example local.properties
# 編輯 sdk.dir、MAPS_API_KEY
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

Debug 憑證 SHA-1：

```bash
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
```

行程由 `https://wusper.com/api/eta/compute-routes` 規劃，應用程式不內建 Routes 金鑰。沒有路徑時只顯示「未能規劃」。

## 資料

| 資料 | 來源 |
| --- | --- |
| 附近路線、搜尋、沿線站、行程 | `https://wusper.com/api/eta/` |
| 九巴到站 | `https://data.etabus.gov.hk/v1/transport/kmb/` |
| 城巴到站 | `https://rt.data.gov.hk/v2/transport/citybus/` |
| 專線小巴到站 | `https://data.etagmb.gov.hk/` |
| 港鐵到站 | `https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php` |
| 輕鐵路線與車站 | 內置 `lrt_stops.json`（官方路線站表；座標來自地政總署地圖） |
| 輕鐵到站 | `https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule` |
| 港鐵、機場快線、輕鐵成人八達通車費 | `https://opendata.mtr.com.hk/data/mtr_lines_fares.csv`、`airport_express_fares.csv`、`light_rail_fares.csv` |
| 港鐵出口與地標 | 港鐵車站位置圖（`/archive/ch/services/maps/`）及金鐘出口牌。重鐵每站都顯示「出口」。輕鐵只在有官方出口資料時顯示 |
| 全國地鐵 | 城市名單 `https://wusper.com/api/metro/cities`。北京、上海用營運商官方海報；香港用內置港鐵官方路線圖。其餘城市用與 wusper.com/tools/metro 相同的路網示意（`/api/metro/network`，線色與站名），不是官方海報。裝置只向 wusper.com 取路網。圖檔與路網在裝置上保留 30 日 |

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

`MAPS_API_KEY` is the Android Maps SDK key (package `com.wusper.eta` plus your SHA-1). Trip planning calls `https://wusper.com/api/eta/compute-routes`. The app does not embed a Routes key. There is no account system.
