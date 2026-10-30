# TapInk 瞬墨 (`com.dabendan.tapink`)

**TapInk：NFC電子紙一鍵刷圖 & 智慧桌牌名片**（英文：**TapInk: NFC E-Paper & E-Ink**）

Android 原生輕量化無源 NFC 電子紙更新工具。支援從任何相簿或設計 App（Google 相簿、Canva、Figma）透過系統「分享 (Share)」選單點擊 **「刷入電子紙 (TapInk)」** 一鍵喚起 Material Design 3 底部感應面板，自動識別電子紙硬體規格（2.9" 黑白雙色 / 4.2" 黑白紅黃四色 / COS v1 & v2）並執行專業級 Floyd-Steinberg 誤差擴散抖動與 LZO1X 壓縮傳輸。

---

## 核心特色

- **系統級 Share Sheet 一鍵直達 (`刷入電子紙 (TapInk)`)**：在相簿或繪圖 App 點選「分享」即可直接貼靠電子紙刷圖，免除繁瑣的開 App 與手動設定型號流程。
- **全自動硬體規格識別**：感應瞬間透過 ISO 14443-4 (`IsoDep`) APDU 讀取電子紙韌體 TLV 規格，自動適配 **2.9" (296×128 黑白雙色)**、**4.2" (400×300 黑白紅黃四色)**、**2.13" (250×122)** 與 **FMSH COS v1/v2** 雙倍高封包格式。
- **專業級電子紙影像抖動演算法 (Floyd-Steinberg Dithering)**：針對反射式電子墨水光學特性調校的色彩量化與誤差擴散，自然還原人像漸層並保持文字與條碼銳利度。
- **Android 15 / 16 Quick Share 搶佔免疫**：內建系統分享面板退場期間的 `ReaderMode` 主動重申機制，解決 Android 15+ 系統分享後 NFC 被 GMS 重置的問題。
- **全球主要出貨國完整本地化 (9 語系)**：原生支援 **繁體中文 (`zh-TW` / `zh-HK`)、日文 (`ja`)、英文 (`en`)、德文 (`de`)、法文 (`fr`)、西班牙文 (`es`)、韓文 (`ko`)、簡體中文 (`zh-CN`)**，涵蓋日本、北美、歐洲、台港澳與亞太主要市場。
- **100% 純離線、零廣告、零敏感權限**：僅申請 `android.permission.NFC` 單一權限，完全不連網、不收集任何使用者資料。

---

## 專案目錄結構

```text
tapink/
├── .editorconfig                    # 跨編輯器縮排與 LF 換行規範
├── .gitattributes                   # Git 二進位檔案與 LF 行尾正規化
├── .gitignore                       # 建置產物與私鑰忽略清單
├── .github/workflows/
│   └── ci.yml                       # GitHub Actions CI/CD (Build, JVM Tests, AAB Validation, Release)
├── .husky/
│   ├── commit-msg                   # Git commit-msg hook (Conventional Commits 檢查)
│   └── pre-commit                   # Git pre-commit hook (私鑰攔截、Kotlin -Werror、單元測試、AAB 驗證)
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # API 35 Target SDK, Adaptive Icon, Share Intent Filter
│       │   ├── kotlin/com/dabendan/tapink/
│       │   │   ├── FloydSteinbergDitherer.kt
│       │   │   ├── LogManager.kt
│       │   │   ├── Lzo1x.kt
│       │   │   ├── ShareNfcActivity.kt
│       │   │   ├── TapInkLogService.kt
│       │   │   └── TapInkProtocol.kt
│       │   └── res/                 # TapInk Adaptive Icons (API 26+) & 9 語系本地化 (en, ja, de, fr, es, ko, zh-TW/HK/CN)
│       └── test/kotlin/com/dabendan/tapink/
│           └── TapInkUnitTest.kt    # 純 JVM 協定、抖動與 LZO1X 單元測試套件
├── docs/
│   ├── play-store/
│   │   ├── feature-graphic-1024x500.png
│   │   ├── icon-512.png
│   │   └── store-listing.md         # Google Play ASO 標題、簡短說明與中英完整文案
│   ├── privacy-policy.md            # Google Play 隱私權政策
│   └── screenshots/                 # E2E 畫面截圖 (01-waiting-nfc.png ~ 04-transferring-420bwry.png)
├── scripts/
│   ├── build-and-test.sh            # 一鍵編譯 APK + Play Store AAB + 單元測試 + 實機部署
│   └── generate-playstore-assets.py # 自動產生 Play Store 圖示與置頂大圖
├── tests/
│   └── emulator-e2e.test.py         # Android 模擬器自動化 E2E 驗證套件
├── LICENSE                          # MIT License
├── package.json                     # Husky Git Hooks 與專案自動化腳本入口
└── README.md
```

---

## 快速建置與測試

```bash
# 1. 執行完整編譯 (API 35, -Werror)、JVM 單元測試並產出 APK 與 Google Play AAB
npm test
# 或直接執行：
./scripts/build-and-test.sh

# 2. 重新產生 Google Play 512x512 圖示與 1024x500 置頂大圖
npm run assets:playstore

# 3. 執行無頭 Android 模擬器 E2E 測試
npm run test:e2e

# 4. 部署至實體手機 (僅安裝)
npm run deploy:device
```

### 產出產物 (`app/build/`)
- `tapink-release.aab`：Google Play Console 正式上架用 Android App Bundle（已簽署並通過 `bundletool validate`）。
- `tapink-release.apk`：正式簽章獨立安裝包。
- `tapink.apk`：本地開發與 ADB 快速覆蓋安裝包。

---

## Google Play 審核員測試說明 (App Access / Demo Mode)

若審核環境無實體 NFC 電子紙硬體：
1. 從桌面啟動 **TapInk 電子紙** (`TapInk NFC`) 並挑選任意一張圖片。
2. 在「準備更新電子紙」底部面板中，**長按中央的電子紙預覽卡片 1 秒**，即可觸發內建的「無硬體展示預覽模式 (Demo Mode)」，完整體驗 4.2" 四色 Floyd-Steinberg 抖動轉換、周界進度環動畫與自動完成流程。
