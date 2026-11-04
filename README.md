<div align="center">

<img src="docs/play-store/icon-512.png" width="112" height="112" alt="TapInk 瞬墨 - Android 無電池 NFC 電子紙與智慧識別證更新工具圖示" title="TapInk 瞬墨 - Android 離線無電池 NFC 電子紙更新工具" />

# TapInk 瞬墨

繁體中文 | [English](README.en.md) | [日本語](README.ja.md)

**從相簿按下分享，手機貼靠即完成更新。**  
**無需手動設定尺寸，在無電池 NFC 電子紙上呈現清晰銳利的色彩與細節。**

*Update battery-free NFC E-Paper displays directly from Android's share sheet—100% offline.*

[![Download APK](https://img.shields.io/badge/下載_Android_安裝包-tapink--release.apk_(840_KB)-0B57D0?style=for-the-badge&logo=android&logoColor=white)](https://github.com/dabendan2/tapink/releases/latest/download/tapink-release.apk)
[![Obtainium](https://img.shields.io/badge/Obtainium-一鍵加入更新追蹤-5A32A3?style=for-the-badge&logo=android&logoColor=white)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/dabendan2/tapink)

[![Release](https://img.shields.io/badge/版本-v0.9.0-1F1F1F?style=flat-square)](https://github.com/dabendan2/tapink/releases/latest)
[![Platform](https://img.shields.io/badge/系統需求-Android%207.0%20~%2015%2B-00639B?style=flat-square)](#下載與安裝-download--install)
[![Privacy](https://img.shields.io/badge/隱私設計-純本機處理・無網路權限-146C2E?style=flat-square)](docs/privacy-policy.md)
[![License: MIT](https://img.shields.io/badge/開源授權-MIT-444746?style=flat-square)](LICENSE)

</div>

---

無電池 NFC 電子紙（如智慧識別證、會議室桌牌、數位名片與行李牌）不需充電即可長久顯示畫面。**TapInk 瞬墨** 將電子紙的更新流程整合至 Android 系統分享選單：選好圖片、點選分享、將手機背面貼靠電子紙，數秒內即可完成刷新。

系統會在感應瞬間自動辨識面板尺寸與色彩規格，並針對反射式電子墨水最佳化人像漸層、文字與 QR Code 清晰度。全數影像處理皆於手機本機端完成，不需設定、不需連網。

<div align="center">
  <img src="docs/play-store/feature-graphic-1024x500.png" width="840" alt="TapInk 瞬墨 - 相簿分享一觸秒刷無電池 NFC 電子紙桌牌與數位名片 (支援雙色/三色/四色/Spectra 6 抖動)" title="TapInk 瞬墨 - 相簿分享一觸秒刷無電池 NFC 電子紙桌牌與名片" />
</div>

---

## 下載與安裝 (Download & Install)

**[下載 Android 安裝包 (`tapink-release.apk`, 840 KB)](https://github.com/dabendan2/tapink/releases/latest/download/tapink-release.apk)**  
支援具備 NFC 功能的 Android 7.0 至 Android 15+ 裝置。下載後開啟檔案即可安裝。

### 使用方式
1. **選擇畫面**：在 Google 相簿、Canva 或任何圖片 App 中準備好畫面，點選系統 **「分享 ➔ 刷入電子紙 (TapInk)」**（亦可直接從桌面開啟 `TapInk 電子紙` 選取圖片）。
2. **貼靠感應**：將**手機背面 NFC 感應區**平貼於電子紙中央並保持穩定。進度達 100% 且面板完成刷新後，畫面將自動收起。
3. **展示模式**：若身邊暫無實體電子紙，可開啟 TapInk 主畫面並**長按中央預覽卡片 1 秒**，啟動內建展示模式（Demo Mode）預覽操作流程。

> **隱私與權限說明**  
> TapInk 僅申請單一 NFC 硬體控制權限（`android.permission.NFC`），**不申請網路連線權限**。您的照片、名片與條碼皆在手機本機端完成轉換並直接寫入電子紙，不會上傳至任何伺服器，亦無廣告、分析追蹤或背景常駐程序。

---

## 畫面預覽 (Screenshots)

| 1. 準備感應與即時預覽 | 2. 黑白雙色快速刷新 | 3. 四色電子墨水色彩抖動 | 4. 中斷保護與原位重試 |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/01-waiting-nfc.png" width="200" alt="TapInk 系統分享選單底部感應面板：準備更新無電池 NFC 電子紙與即時畫面預覽" title="準備感應與即時預覽 (Android 系統分享底部抽屜)" /> | <img src="docs/screenshots/03-transferring-290bw.png" width="200" alt="TapInk 自動識別 2.9 吋 (296×128) 黑白雙色 NFC 電子紙識別證並快速刷新" title="2.9 吋黑白雙色電子紙智慧識別證快速刷新" /> | <img src="docs/screenshots/04-transferring-420bwry.png" width="200" alt="TapInk 自動識別 4.2 吋 (400×300) 黑白紅黃四色 BWRY NFC 電子紙桌牌與 Floyd-Steinberg 色彩抖動傳輸" title="4.2 吋四色 BWRY 電子紙桌牌色彩抖動與刷新" /> | <img src="docs/screenshots/02-error-retry.png" width="200" alt="TapInk NFC 感應中斷保護與重新貼靠電子紙原位無縫續刷" title="NFC 感應中斷保護與原位無縫重試" /> |

---

## 核心功能與設計 (Key Features)

| 功能面向 | 傳統更新流程 | **TapInk 設計** |
| :--- | :--- | :--- |
| **操作路徑** | 開啟專用 App ➔ 匯入圖片 ➔ 手動選擇型號 ➔ 裁切 ➔ 傳送 | **支援 Android 系統分享（Share Sheet）**：從任何圖片 App 直接分享並貼靠刷入，完成後自動返回原畫面 |
| **硬體識別** | 需從型號清單手動指定尺寸與色彩模式 | **自動識別規格**：感應瞬間自動讀取螢幕尺寸、解析度與色彩配置（雙色 / 三色 / 四色 / 六色） |
| **影像處理** | 單純色階轉換容易導致膚色失真、文字與條碼邊緣模糊 | **專為電子墨水調校的抖動演算法**：平衡人像膚色光影漸層，同時保留文字與 QR Code 的邊緣銳利度 |
| **傳輸穩定性** | 感應過程稍有位移即需重新操作 | **連線強化與無縫重試**：針對 Android 15 最佳化 NFC 供電時序；若感應中斷，重新貼靠即可原位續刷 |
| **隱私與資源** | 常包含網路連線或非必要的儲存空間權限 | **100% 離線運作**：安裝包僅 840 KB，零網路權限，使用完畢即釋放系統資源 |

---

## 支援硬體規格 (Supported Hardware)

TapInk 支援採用無源 NFC 供電與通訊之電子紙顯示器、智慧識別證、桌牌與行李牌（包含 **Santek EZ Sign** 系列及相容規格之無電池 NFC 電子紙）：

| 螢幕尺寸 | 實體解析度 | 支援色彩模式 | 常見應用場景 |
| :--- | :--- | :--- | :--- |
| **1.54"** | 200 × 200 | 黑白雙色 / 黑白紅三色 / 黑白紅黃四色 | 方形吊牌、小型徽章、資產標籤 |
| **2.13"** | 250 × 122 | 黑白雙色 / 黑白紅三色 / 黑白紅黃四色 | 行李牌、小型標價牌、隨身配件 |
| **2.66" / 2.66" HD** | 296 × 152 / 360 × 184 | 黑白雙色 / 黑白紅黃四色 | 商務名片夾、展架標籤、儲物櫃標示 |
| **2.9" / 2.9" HD** | 296 × 128 / 384 × 168 | 黑白雙色 / 黑白紅三色 / 黑白紅黃四色 | **智慧識別證、數位名片、工作證** |
| **3.0" / 3.7"** | 400 × 168 / 416 × 240 | 黑白雙色 / 黑白紅三色 / 黑白紅黃四色 | 大型隨身展示卡、長條桌牌、門牌標示 |
| **3.6"** | 600 × 400 | 六色全彩 (Spectra 6) | 彩色相框、展示立牌 |
| **4.2"** | 400 × 300 | 黑白雙色 / 黑白紅三色 / 黑白紅黃四色 | **會議室桌牌、座位牌、桌上型立牌** |

*內建多語系介面：隨系統設定自動切換繁體中文（台灣／香港）、英文、日文、韓文、德文、法文、西班牙文與簡體中文。*

---

## 進階資訊：安全驗證與原始碼建置 (Verification & Build)

<details>
<summary><b>展開檢視：APK SHA-256 校驗碼、建置環境與測試指令</b></summary>
<br />

### 發行包 SHA-256 校驗碼 (`v0.9.0`)
可透過以下雜湊值驗證 [`tapink-release.apk`](https://github.com/dabendan2/tapink/releases/latest/download/tapink-release.apk) 檔案完整性（同步記錄於 Release 頁面之 `SHA256SUMS.txt`）：
```text
40cffc5d9ff5ea2064ad7f097ff241bc0f69cabbf6ed9416d6c2f1fa3e7a36aa  tapink-release.apk
```

### 從原始碼建置與測試
本專案為原生 Android 應用程式（`android/app`），包含 JVM 單元測試與 Android 模擬器端對端（E2E）測試。

- **環境需求**：JDK 17+、Android SDK (API 35 / Build-Tools `35.0.0`)、Kotlin Compiler (`kotlinc`)

```bash
# 執行完整建置與單元測試（APK/AAB 編譯 + JVM 單元測試 + Bundletool 驗證）
npm test

# 執行 Android 模擬器端對端 (E2E) 測試與截圖驗證
npm run test:e2e

# 安裝至已連接的實體 Android 裝置
npm run deploy:device
```

</details>

---

## 相關文件與聲明 (Legal & Privacy)

- **隱私權政策**：[Privacy Policy (`docs/privacy-policy.md`)](docs/privacy-policy.md)
- **商標聲明**：TapInk 為獨立開發之應用程式，與 Santek、EZ Sign 或其他硬體製造商無隸屬或官方授權關係。文中提及之品牌名稱僅用於說明硬體相容性。
- **開源授權**：[MIT License](LICENSE)
