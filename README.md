# EZ Sign 電子紙 (`com.dabendan.ezsign`)

Android 原生輕量化無源 NFC 電子紙更新工具。支援從任何相簿或繪圖 App 透過系統「分享 (Share)」選單一鍵喚起 Material Design 3 底部面板，自動識別電子紙硬體規格（2.9" 黑白雙色 / 4.2" 黑白紅黃四色 / COS v1 & v2）並執行 Floyd-Steinberg 誤差擴散抖動與 LZO1X 壓縮傳輸。

---

## 核心特色

- **系統級 Share Sheet 一鍵直達**：在 Google 相簿、Canva 或任何圖片 App 點擊「分享 → EZ Sign 傳送至電子紙」即可直接感應刷圖，免除繁瑣的開 App 選型號流程。
- **全自動硬體規格偵測**：感應瞬間透過 ISO 14443-4 (`IsoDep`) APDU 讀取電子紙韌體 TLV 規格，自動適配 **2.9" (296×128 黑白雙色)**、**4.2" (400×300 黑白紅黃四色)** 與 **FMSH COS v1/v2** 雙倍高封包格式。
- **高品質 Floyd-Steinberg 抖動演算法**：針對雙色與四色電子紙光學特性調校的色彩量化與誤差擴散。
- **Android 15 / 16 Quick Share 搶佔免疫**：內建系統分享面板退場期間的 `ReaderMode` 主動重申機制，解決 Android 15+ 系統分享後 NFC 被 GMS 重置的問題。
- **零連網、零敏感權限**：僅使用 `android.permission.NFC` 單一權限，100% 離線運作。

---

## 專案目錄結構

```text
ezsign-nfc-updater/
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
│       │   ├── kotlin/com/dabendan/ezsign/
│       │   │   ├── EzSignLogService.kt
│       │   │   ├── EzSignProtocol.kt
│       │   │   ├── FloydSteinbergDitherer.kt
│       │   │   ├── LogManager.kt
│       │   │   ├── Lzo1x.kt
│       │   │   └── ShareNfcActivity.kt
│       │   └── res/                 # Vector & Adaptive Launcher Icons (API 26+)
│       └── test/kotlin/com/dabendan/ezsign/
│           └── EzSignUnitTest.kt    # 純 JVM 協定、抖動與 LZO1X 單元測試套件
├── docs/
│   ├── privacy-policy.md            # Google Play 隱私權政策
│   ├── play-store/                  # Play Store 商店素材 (icon-512.png, feature-graphic-1024x500.png)
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
# 1. 執行完整編譯 (API 35)、JVM 單元測試並產出 APK 與 Google Play AAB
npm test
# 或直接執行：
./scripts/build-and-test.sh

# 2. 執行無頭 Android 模擬器 E2E 測試
npm run test:e2e

# 3. 部署至實體手機 (僅安裝)
npm run deploy:device
```

### 產出產物 (`app/build/`)
- `ezsign-nfc-updater-release.aab`：Google Play Console 正式上架用 Android App Bundle（已簽署並通過 `bundletool validate`）。
- `ezsign-nfc-updater-release.apk`：正式簽章獨立安裝包。
- `ezsign-nfc-updater.apk`：本地開發與 ADB 快速覆蓋安裝包。

---

## Google Play 審核員測試說明 (App Access / Demo Mode)

若審核環境無實體 EZ Sign NFC 電子紙硬體：
1. 從桌面啟動 **EZ Sign 電子紙** 並挑選任意一張圖片。
2. 在「準備更新電子紙」底部面板中，**長按中央的電子紙預覽卡片 1 秒**，即可觸發內建的「無硬體展示預覽模式 (Demo Mode)」，完整體驗 4.2" 四色 Floyd-Steinberg 抖動轉換、周界進度環動畫與自動完成流程。
