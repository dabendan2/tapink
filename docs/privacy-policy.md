# 隱私權政策 (Privacy Policy)

**生效日期：2026 年 10 月 31 日**  
**應用程式名稱：TapInk 瞬墨 — NFC 電子紙更新工具**  
**支援平台：Android (`com.dabendan.tapink`) & iOS (`com.dabendan.tapink`)**

## 1. 資料收集與使用 (Data Collection and Use)
本應用程式（含 Android 版與 iOS 版）為**純離線本地端工具 (100% Offline Utility)**，**不會**收集、儲存、分析、傳輸或分享您的任何個人資料、相片、裝置識別碼或使用紀錄。

- **零網路連線**：本應用程式不進行任何網路通訊（Android 版未申請 `android.permission.INTERNET` 網路權限；iOS 版不發起任何網路請求），所有影像縮放、色彩抖動 (Floyd-Steinberg Dithering) 與 LZO1X 壓縮均在您的手機本地端記憶體中即時完成，隨即透過 NFC 寫入電子紙標籤並釋放。
- **相片與媒體存取**：本應用程式僅在您主動透過系統「分享 (Share Sheet / iOS Share Extension)」傳送單張圖片，或主動透過系統圖片選擇器 (`PhotosPicker` / `ACTION_GET_CONTENT`) 挑選單張圖片時，於本地端讀取該張圖片以轉換為電子紙格式，不會掃描或存取您相簿中的其他照片。

## 2. 裝置權限說明 (Permissions)
- **NFC 近場通訊 (`android.permission.NFC` / iOS `CoreNFC` `NFCReaderUsageDescription`)**：僅用於與支援 ISO 14443-4 / ISO 7816 (`IsoDep` / `NFCISO7816Tag`) 協定的無源 NFC 電子紙進行近場感應供電與畫面資料傳輸。

## 3. 第三方服務 (Third-Party Services)
本應用程式不包含任何第三方廣告 SDK、追蹤器 (Trackers) 或分析工具 (Analytics)。

## 4. 聯絡我們 (Contact Us)
若您對本隱私權政策有任何疑問，請透過 GitHub Issues 與開發團隊聯繫。
