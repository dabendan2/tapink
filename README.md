# TapInk 瞬墨 (`com.dabendan.tapink`)

**TapInk：NFC電子紙一鍵刷圖 & 智慧桌牌名片**（英文：**TapInk: NFC E-Paper & E-Ink**）

跨平台（**Android API 35** & **iOS 16+ CoreNFC ISO 7816**）原生輕量化無源 NFC 電子紙更新工具。支援從任何相簿或設計 App（Google 相簿、Apple 照片、Canva、Figma）透過系統「分享 (Share)」選單點擊 **「刷入電子紙 (TapInk)」** 一鍵喚起底部感應面板，自動識別電子紙硬體規格（2.9" 黑白雙色 / 4.2" 黑白紅黃四色 / COS v1 & v2）並執行專業級 Floyd-Steinberg 誤差擴散抖動與 LZO1X 壓縮傳輸。

---

## 核心特色

- **雙平台系統級 Share Sheet 一鍵直達 (`刷入電子紙 (TapInk)`)**：在 Android 與 iPhone 的相簿或繪圖 App 點選「分享」即可直接貼靠電子紙刷圖，免除繁瑣的開 App 與手動設定型號流程。
- **全自動硬體規格識別 (ISO 14443-4 / ISO 7816 APDU)**：感應瞬間透過 APDU (`00D10000`) 讀取電子紙韌體 TLV 規格，自動適配 **2.9" (296×128 黑白雙色)**、**4.2" (400×300 黑白紅黃四色)**、**2.13" (250×122)** 與 **FMSH COS v1/v2** 雙倍高封包格式。
- **100% 雙端 Byte-for-Byte 對齊演算法 (`Kotlin` & `Swift`)**：
  - **Floyd-Steinberg 誤差擴散抖動器**：包含 `<64->0, <128->err/2` 誤差衰減、雙色票 Yellow 量化 `(255,255,0)` / 預覽 `#FFC000`、暖白底色不偏黃，以及硬體位元打包 (`30,000` / `9,472` / `7,750` bytes)。
  - **LZO1X-1 區塊壓縮引擎**：將畫面切分為 2,000-byte 區塊高速無損壓縮並透過 `F0 D3` APDU 分包寫入。
- **Android 15 / 16 Quick Share 搶佔免疫 & iOS CoreNFC 即時進度回饋**：Android 端內建 `ReaderMode` 主動重申機制；iOS 端透過 `NFCTagReaderSession` 動態更新感應進度與天線貼靠引導。
- **全球主要出貨國完整本地化 (9 語系)**：Android (`res/values-*`) 與 iOS (`Resources/*.lproj`) 同步原生支援 **繁體中文 (`zh-TW` / `zh-Hant`、`zh-HK`)、日文 (`ja`)、英文 (`en`)、德文 (`de`)、法文 (`fr`)、西班牙文 (`es`)、韓文 (`ko`)、簡體中文 (`zh-CN` / `zh-Hans`)**。
- **100% 純離線、零廣告、零敏感權限**：僅使用 NFC 單一硬體權限，完全不連網、不收集任何使用者資料。

---

## 專案目錄結構

```text
tapink/
├── .editorconfig                        # 跨編輯器縮排與 LF 換行規範
├── .gitattributes                       # Git 二進位檔案與 LF 行尾正規化
├── .gitignore                           # 建置產物與私鑰忽略清單
├── .github/workflows/
│   └── ci.yml                           # GitHub Actions CI/CD (Android CI + iOS Swift Linux CI + macOS XcodeGen CI)
├── .husky/
│   ├── commit-msg                       # Git commit-msg hook (Conventional Commits 檢查)
│   └── pre-commit                       # Git pre-commit hook (私鑰攔截、Android 建置與測試、iOS swift test、AAB 驗證)
├── android/
│   └── app/
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml  # API 35 Target SDK, Adaptive Icon, Share Intent Filter
│           │   ├── kotlin/com/dabendan/tapink/
│           │   │   ├── FloydSteinbergDitherer.kt
│           │   │   ├── LogManager.kt
│           │   │   ├── Lzo1x.kt
│           │   │   ├── ShareNfcActivity.kt
│           │   │   ├── TapInkLogService.kt
│           │   │   └── TapInkProtocol.kt
│           │   └── res/                 # TapInk Adaptive Icons (API 26+) & 9 語系本地化
│           └── test/kotlin/com/dabendan/tapink/
│               └── TapInkUnitTest.kt    # 純 JVM 協定、抖動與 LZO1X 單元測試套件 (7 大測試)
├── ios/
│   ├── Package.swift                    # Swift Package Manager 定義 (TapInkCore & TapInkCoreTests)
│   ├── project.yml                      # XcodeGen 專案設定檔 (一鍵生成 TapInk.xcodeproj)
│   ├── Sources/TapInkCore/              # 跨平台純 Swift 核心模組 (macOS / iOS / Linux 零警告編譯)
│   │   ├── FloydSteinbergDitherer.swift
│   │   ├── Lzo1x.swift
│   │   └── TapInkProtocol.swift
│   ├── Tests/TapInkCoreTests/
│   │   └── TapInkCoreTests.swift        # XCTest 單元測試套件 (與 Android 完全對齊的 7 大核心測試)
│   ├── TapInkApp/                       # iOS 16.0+ SwiftUI + CoreNFC 主程式
│   │   ├── TapInkApp.swift
│   │   ├── ContentView.swift
│   │   ├── CoreNfcIsoDepTransceiver.swift
│   │   ├── Info.plist
│   │   └── TapInkApp.entitlements
│   ├── TapInkShareExtension/            # iOS 系統分享擴充功能 (App Group 共享圖片直傳)
│   │   ├── ShareViewController.swift
│   │   ├── Info.plist
│   │   └── TapInkShareExtension.entitlements
│   └── Resources/
│       ├── Assets.xcassets/             # 1024x1024 無透明通道 AppIcon.appiconset
│       └── *.lproj/                     # 9 語系 Localizable.strings 與 InfoPlist.strings
├── docs/
│   ├── app-store/
│   │   └── store-listing.md             # Apple App Store ASO 標題、Subtitle、Keywords 與審核說明
│   ├── play-store/
│   │   ├── feature-graphic-1024x500.png
│   │   ├── icon-512.png
│   │   └── store-listing.md             # Google Play ASO 標題、簡短說明與多語系完整文案
│   ├── privacy-policy.md                # 雙平台隱私權政策
│   └── screenshots/                     # E2E 畫面截圖 (01-waiting-nfc.png ~ 04-transferring-420bwry.png)
├── scripts/
│   ├── build-and-test.sh                # 一鍵編譯 Android APK/AAB + JVM 單元測試 + iOS swift test + 實機部署
│   └── generate-store-assets.py         # 自動產生 Google Play 圖示/置頂大圖與 iOS 1024x1024 AppIcon
├── tests/
│   └── emulator-e2e.test.py             # Android 模擬器自動化 E2E 驗證套件
├── LICENSE                              # MIT License
├── package.json                         # Husky Git Hooks 與專案自動化腳本入口
└── README.md
```

---

## 快速建置與測試

```bash
# 1. 執行完整跨平台驗證：Android 編譯 (API 35, -Werror)、JVM 單元測試、Play Store AAB 驗證 + iOS TapInkCore Swift 單元測試
npm test
# 或直接執行：
./scripts/build-and-test.sh

# 2. 單獨執行 iOS TapInkCore Swift 單元測試
npm run test:ios

# 3. 在 macOS 上透過 XcodeGen 產生 Xcode 專案並編譯 iOS App & Share Extension
cd ios && xcodegen generate
xcodebuild -project TapInk.xcodeproj -scheme TapInkApp -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build

# 4. 重新產生 Google Play (512x512 + 1024x500) 與 Apple App Store (1024x1024 RGB) 圖示資產
npm run assets:store

# 5. 執行無頭 Android 模擬器 E2E 測試
npm run test:e2e

# 6. 部署至實體 Android 手機 (僅安裝)
npm run deploy:device
```

### Android 產出產物 (`android/app/build/`)
- `tapink-release.aab`：Google Play Console 正式上架用 Android App Bundle（已簽署並通過 `bundletool validate`）。
- `tapink-release.apk`：正式簽章獨立安裝包。
- `tapink.apk`：本地開發與 ADB 快速覆蓋安裝包。

---

## Google Play & Apple App Store 審核員測試說明 (App Access / Demo Mode)

若審核環境無實體 NFC 電子紙硬體：
1. 啟動 **TapInk 電子紙** (`TapInk NFC`)（iOS 版已預載四色示範插畫，或可挑選任意圖片）。
2. 在主畫面或底部面板中，**長按中央的電子紙預覽卡片 1 秒**，即可觸發內建的「無硬體展示預覽模式 (Demo Mode)」，完整體驗 4.2" 四色 Floyd-Steinberg 抖動轉換、進度動畫與自動完成流程。
