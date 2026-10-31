# Google Play Store 商品詳情與 ASO 文案 (`com.dabendan.tapink`)

## 1. 品牌與介面顯示名稱

| 欄位 | 繁體中文 (`zh-TW` / `zh-HK`) | 日文 (`ja-JP`) | 英文 (`en-US`) | 德文 (`de-DE`) | 韓文 (`ko-KR`) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **主品牌名稱** | **TapInk 瞬墨** | **TapInk (タップインク)** | **TapInk** | **TapInk** | **TapInk** |
| **桌面圖示名稱 (`app_name`)** | `TapInk 電子紙` | `TapInk 電子ペーパー` | `TapInk NFC` | `TapInk NFC` | `TapInk 전자종이` |
| **系統分享選單 (`share_action_label`)** | `刷入電子紙 (TapInk)` | `電子ペーパーへ転送 (TapInk)` | `Beam to E-Paper (TapInk)` | `An E-Paper senden (TapInk)` | `전자종이로 전송 (TapInk)` |
| **Google Play 商店標題 (≤30字)** | `TapInk：NFC電子紙一鍵刷圖 & 智慧桌牌名片` | `TapInk：NFC電子ペーパー瞬間書換` | `TapInk: NFC E-Paper & E-Ink` | `TapInk: NFC E-Paper & E-Ink` | `TapInk: NFC 전자종이 원터치 전송` |

---

## 2. 簡短說明 (Short Description，限 80 字元內)

### 繁體中文版 (65 字元)
```text
從系統「分享」直接更新無電池 NFC 電子紙。自動辨識螢幕尺寸、針對電子墨水最佳化色彩抖動，100% 本機離線快速刷新桌牌與名片。
```

### 英文版 (`en-US`, 78 字元)
```text
Update battery-free NFC E-Paper from Android Share. Auto-detect & 100% offline.
```

### 日文版 (`ja-JP`, 66 字元)
```text
写真やCanvaの「共有」から電池レスNFC電子ペーパーを直接更新。画面サイズの自動認識と電子インク向け色変換に対応するオフラインツール
```

### 德文版 (`de-DE`, 76 字元)
```text
Batterielose NFC-E-Paper direkt über „Teilen“ aktualisieren. 100 % offline.
```

### 韓文版 (`ko-KR`, 64 字元)
```text
갤러리나 Canva의 '공유' 메뉴에서 무전원 NFC 전자종이를 즉시 업데이트. 화면 크기 자동 인식 및 오프라인 처리
```

---

## 3. 完整說明 — 繁體中文版 (Full Description - Traditional Chinese)

```text
從相簿按下分享，手機貼靠即完成更新。無需手動設定尺寸，在無電池 NFC 電子紙上呈現清晰銳利的色彩與細節。

無電池 NFC 電子紙（如智慧識別證、會議室桌牌、數位名片、行李牌與 Santek EZ Sign 相容顯示卡）不需充電即可長久顯示畫面。TapInk 瞬墨 將電子紙的更新流程整合至 Android 系統分享選單：選好圖片、點選分享、將手機背面貼靠電子紙，數秒內即可完成刷新。

系統會在感應瞬間自動辨識面板尺寸與色彩規格，並針對反射式電子墨水最佳化人像漸層、文字與 QR Code 清晰度。全數影像處理皆於手機本機端完成，不需設定、不需連網。

⚡ 3 步快速更新流程
1. 選擇畫面：在 Google 相簿、Canva 或任何圖片 App 中準備好照片、名片或桌牌設計。
2. 點選系統「分享」：選擇 Android 系統分享選單中的「刷入電子紙 (TapInk)」（亦可直接從桌面開啟「TapInk 電子紙」選取圖片），螢幕底部將滑出輕量感應面板。
3. 貼靠感應並自動返回：將手機背面頂端 NFC 感應區平貼於電子紙中央並保持穩定。進度達 100% 且面板完成刷新後，畫面將自動收起並返回原應用程式。

🌟 核心功能與設計
• 自動識別硬體規格
  感應瞬間自動讀取電子紙的螢幕尺寸（1.54"、2.13"、2.66" 含 HD、2.9" 含 HD、3.0"、3.6"、3.7"、4.2"）與色彩配置（黑白雙色、黑白紅三色、黑白紅黃四色、六色全彩 Spectra 6），即時完成最佳比例裁切與解析度匹配，無需手動查找型號。
• 專為電子墨水調校的色彩抖動演算法
  針對反射式電子紙顏料特性設計的 Floyd-Steinberg 色彩抖動引擎，透過黑、白、紅、黃點陣排列，自然呈現人像膚色與光影漸層，同時維持純淨底色及文字、QR Code 的邊緣銳利度。
• 穩定的 NFC 供電與原位續刷
  針對 Android 7.0 至 Android 15+ 最佳化底層 NFC 連續供電時序與 LZO 分塊壓縮傳輸；若感應途中不慎移開手機，重新貼靠即可原位續刷。內建展示模式（長按預覽卡片 1 秒），無實體標籤亦可預覽完整操作流程。

🔒 100% 本機離線與隱私保障
• 單一權限設計：僅申請 NFC 硬體控制權限（android.permission.NFC），不申請任何網路連線或背景常駐權限。
• 本機端即時處理：照片、商務名片、會議桌牌與條碼皆在手機記憶體內轉換並直接寫入電子紙，不會上傳至任何外部伺服器。
• 輕量純淨：無廣告、無分析追蹤、無帳號登入需求，使用完畢即釋放系統資源。

📱 支援硬體規格
TapInk 支援採用無源 NFC 供電與通訊之電子紙顯示器（Passive NFC E-Paper / E-Ink Tags），包含但不限於：
• Santek EZ Sign 系列及相容規格之 1.54"、2.13"、2.66"、2.9"、3.0"、3.6"、3.7"、4.2" 無電池 NFC 電子紙識別證、會議室桌牌、數位名片與行李牌。
• 相容通訊協定之通用型無源 NFC 電子紙標籤與資訊展示牌（支援雙色、三色、四色及六色規格）。

【商標與獨立開發聲明】
TapInk 為獨立開發之應用程式，與 Santek、EZ Sign 或其他硬體製造商無隸屬、官方授權或背書關係。文中提及之品牌與產品名稱僅用於說明硬體相容性，各商標均歸其原權利人所有。
```

---

## 4. 完整說明 — 英文版 (Full Description - English `en-US`)

```text
Share from your gallery and tap to update. Bring crisp colors and sharp details to battery-free NFC E-Paper displays with zero manual setup.

Battery-free NFC E-Paper displays—such as smart badges, meeting room signs, digital business cards, luggage tags, and Santek EZ Sign compatible cards—hold their image indefinitely without a battery. TapInk integrates E-Paper updates directly into the Android Share Sheet: pick an image, tap Share, and hold your phone against the tag to refresh the screen in seconds.

The moment your phone connects, TapInk automatically detects the panel dimensions and color palette, optimizing portrait gradients, text, and QR codes for reflective electronic ink. All image processing happens locally on your device with no configuration or internet connection required.

⚡ 3-Step Update Workflow
1. Choose Your Image: Prepare a photo, badge, or desk sign in Google Photos, Canva, or any design app.
2. Tap System "Share": Select "Beam to E-Paper (TapInk)" from the Android Share Sheet (or open "TapInk NFC" from your home screen to pick a photo). A lightweight NFC bottom sheet slides up over your current app.
3. Tap & Auto-Dismiss: Hold the top back of your phone steady against the center of the E-Paper tag. Once the transfer reaches 100% and the screen refreshes, the sheet closes automatically.

🌟 Key Features
• Automatic Hardware Detection
  Instantly reads the tag's screen size (1.54", 2.13", 2.66" & HD, 2.9" & HD, 3.0", 3.6", 3.7", 4.2") and color configuration (2-Color BW, 3-Color BWR, 4-Color BWRY, and 6-Color Spectra 6), matching aspect ratios and resolutions on the fly without manual model selection.
• E-Ink Tuned Color Dithering
  Calibrated specifically for reflective E-Paper pigments, TapInk's Floyd-Steinberg dithering engine balances skin tones and smooth tonal gradients while keeping backgrounds clean and typography or QR codes sharp.
• Reliable NFC Power & Seamless Retry
  Optimized for Android 7.0 through Android 15+ with stable NFC power harvesting and LZO block compression. If the connection is interrupted mid-transfer, simply hold your phone against the tag again to resume in place. Includes a built-in Demo Mode (long-press the preview card for 1 second) to preview the workflow without a physical tag.

🔒 100% Offline & Privacy-Focused
• Single Permission: Requests only NFC hardware access (android.permission.NFC) and zero internet permissions.
• On-Device Processing: Personal photos, corporate badges, and QR codes are processed in local memory and written directly to the E-Paper tag—never uploaded to any server.
• Lightweight & Clean: Under 1 MB install size with no ads, no analytics tracking, no accounts, and no background services.

📱 Supported Hardware
TapInk supports passive NFC-powered E-Paper / E-Ink displays, including:
• Santek EZ Sign series and compatible 1.54", 2.13", 2.66", 2.9", 3.0", 3.6", 3.7", and 4.2" battery-free NFC E-Paper smart badges, desk signs, business cards, and luggage tags.
• Compatible passive NFC E-Paper tags and electronic shelf labels across 2-color, 3-color, 4-color, and 6-color specifications.

【Trademark & Independence Disclaimer】
TapInk is an independently developed application and is not affiliated with, authorized by, or endorsed by Santek, EZ Sign, or any other hardware manufacturer. All brand and product names mentioned are trademarks of their respective owners and are used solely to indicate hardware compatibility.
```

---

## 5. 完整說明 — 日文版 (Full Description - Japanese `ja-JP`)

```text
ギャラリーから「共有」を選び、スマートフォンをかざすだけで更新完了。事前設定なしで、電池レスNFC電子ペーパーに鮮明な色彩とディテールを表示します。

電池不要のNFC電子ペーパー（スマート名札、会議室プレート、デジタル名刺、ラゲッジタグ、Santek EZ Sign互換ディスプレイなど）は、充電なしで長期間表示を保持できます。TapInk（タップインク）は電子ペーパーの更新フローをAndroidのシステム共有メニューに統合。画像を選び、共有をタップしてスマートフォン背面を電子ペーパーにかざすだけで、数秒で画面を書き換えます。

かざした瞬間にパネルのサイズとカラー仕様を自動認識し、反射型電子インクの特性に合わせて人物のグラデーションや文字、QRコードの鮮明さを最適化します。すべての画像処理は端末内で完結し、複雑な設定や通信環境は必要ありません。

⚡ 3ステップの更新フロー
1. 画像を選択：GoogleフォトやCanvaなどのアプリで、写真や名札・卓上プレートのデザインを用意します。
2. システムの「共有」をタップ：共有メニューから「電子ペーパーへ転送 (TapInk)」を選択すると（またはホーム画面の「TapInk 電子ペーパー」から画像を選択）、画面下部にスキャンシートが表示されます。
3. かざして自動完了：スマートフォン背面のNFCアンテナを電子ペーパーの中央にかざして静止します。進捗が100%になり画面更新が完了すると、シートは自動的に閉じて元のアプリへ戻ります。

🌟 主な機能と設計
• ハードウェア仕様の自動認識
  かざした瞬間に画面サイズ（1.54" / 2.13" / 2.66"・HD / 2.9"・HD / 3.0" / 3.6" / 3.7" / 4.2"）とカラーモード（白黒2色 / 白黒赤3色 / 白黒赤黄4色 / 6色フルカラー Spectra 6）を自動で読み取り、最適なアスペクト比と解像度に調整します。
• 電子インク向けに調整されたディザリング処理
  反射型電子ペーパーの色材特性に合わせたFloyd-Steinbergディザリングアルゴリズムを採用。人物の肌色や陰影のグラデーションを自然に表現しつつ、白背景の美しさと文字・QRコードの輪郭をくっきりと保ちます。
• 安定したNFC給電とスムーズな再試行
  Android 7.0からAndroid 15+まで、NFCの連続給電タイミングとLZOブロック圧縮転送を最適化。転送中にスマートフォンが離れても、もう一度かざすだけでその場から再試行できます。実機が手元にない場合でも、プレビューカードを1秒長押ししてデモモードで動作を確認できます。

🔒 100%オフライン動作とプライバシー保護
• 最小限の権限：NFCハードウェア制御権限（android.permission.NFC）のみを使用し、インターネット通信権限は一切要求しません。
• 端末内ローカル処理：写真や名刺、QRコードなどのデータはすべて端末のメモリ内で処理され、外部サーバーへ送信されることはありません。
• 軽量・クリーン設計：広告、トラッキング、アカウント登録、バックグラウンド常駐プログラムは一切ありません。

📱 対応ハードウェアと免責事項
• Santek EZ Signシリーズおよび互換仕様の1.54"、2.13"、2.66"、2.9"、3.0"、3.6"、3.7"、4.2" 電池レスNFC電子ペーパー（スマート名札、卓上プレート、名刺、タグ等）に対応。
※TapInkは独立して開発されたアプリケーションであり、Santek、EZ Sign、またはその他のハードウェアメーカーとは提携・承認関係にありません。記載されているブランド名や製品名はハードウェアの互換性を示す目的でのみ使用されています。
```

---

## 6. 完整說明 — 德文版 (Full Description - German `de-DE`)

```text
Aus der Galerie teilen, Smartphone dranhalten, fertig. Aktualisieren Sie batterielose NFC-E-Paper-Displays ohne manuelle Einrichtung mit klaren Farben und gestochen scharfen Details.

Batterielose NFC-E-Paper-Displays – wie digitale Namensschilder, Tür- und Tischaufsteller, digitale Visitenkarten, Gepäckanhänger sowie mit Santek EZ Sign kompatible Karten – behalten ihr Bild dauerhaft ohne Akku. TapInk integriert die Aktualisierung direkt in das Android-Teilen-Menü: Bild auswählen, auf „Teilen“ tippen und die Smartphone-Rückseite an das Display halten, um die Anzeige in wenigen Sekunden zu aktualisieren.

Beim Kontakt erkennt TapInk automatisch die Displaygröße sowie das Farbprofil und optimiert Farbverläufe, Texte und QR-Codes gezielt für reflektierende E-Ink-Panels. Die gesamte Bildverarbeitung erfolgt lokal auf Ihrem Gerät – ohne Konfiguration und ohne Internetverbindung.

⚡ Aktualisierung in 3 Schritten
1. Bild auswählen: Bereiten Sie ein Foto, ein Namensschild oder ein Schilddesign in Google Fotos, Canva oder einer anderen Bild-App vor.
2. Über das Systemmenü teilen: Wählen Sie im Android-Teilen-Menü „An E-Paper senden (TapInk)“ (oder öffnen Sie „TapInk NFC“ direkt über den Startbildschirm). Am unteren Bildschirmrand blendet sich sofort das kompakte NFC-Scanfenster ein.
3. Dranhalten & automatisch schließen: Halten Sie die obere Rückseite Ihres Smartphones ruhig an die Mitte des E-Paper-Displays. Sobald die Übertragung 100 % erreicht und das Panel aktualisiert wurde, schließt sich das Fenster automatisch.

🌟 Kernfunktionen
• Automatische Hardwareerkennung
  Erkennt beim Dranhalten automatisch die Bildschirmgröße (1,54", 2,13", 2,66" inkl. HD, 2,9" inkl. HD, 3,0", 3,6", 3,7", 4,2") und den Farbmodus (2-Farben S/W, 3-Farben S/W/R, 4-Farben BWRY sowie 6-Farben Spectra 6) und passt Seitenverhältnis sowie Auflösung direkt an.
• Auf E-Ink abgestimmtes Farb-Dithering
  Der speziell auf reflektierende E-Paper-Pigmente kalibrierte Floyd-Steinberg-Algorithmus sorgt für natürliche Hauttöne und weiche Übergänge, während Hintergründe sauber und Schriften sowie QR-Codes klar lesbar bleiben.
• Zuverlässige NFC-Stromversorgung & nahtlose Wiederholung
  Optimiert für Android 7.0 bis Android 15+ mit stabiler NFC-Energieübertragung und LZO-Blockkomprimierung. Wird die Verbindung kurzzeitig unterbrochen, halten Sie das Smartphone einfach erneut an das Display, um direkt fortzufahren. Über den integrierten Demo-Modus (Vorschaukarte 1 Sekunde gedrückt halten) lässt sich der Ablauf auch ohne physisches Display testen.

🔒 100 % Offline & Datenschutzfreundlich
• Nur NFC-Berechtigung: Benötigt ausschließlich Zugriff auf die NFC-Hardware (android.permission.NFC) und keinerlei Internetberechtigungen.
• Lokale Verarbeitung: Fotos, Visitenkarten und QR-Codes werden vollständig im Arbeitsspeicher Ihres Smartphones verarbeitet und direkt auf das E-Paper übertragen – ohne Cloud-Upload.
• Schlank & werbefrei: Keine Werbung, kein Analyse-Tracking, kein Benutzerkonto und keine Hintergrunddienste.

📱 Unterstützte Hardware & Hinweis
• Unterstützt passive, NFC-betriebene E-Paper- / E-Ink-Displays, einschließlich mit der Santek EZ Sign-Serie kompatibler Modelle in 1,54", 2,13", 2,66", 2,9", 3,0", 3,6", 3,7" und 4,2" (2-, 3-, 4- und 6-Farben-Spezifikationen).
Hinweis: TapInk ist eine unabhängig entwickelte Anwendung und steht in keiner Verbindung zu Santek, EZ Sign oder anderen Hardwareherstellern. Genannte Marken- und Produktnamen dienen ausschließlich zur Angabe der Hardwarekompatibilität.
```

---

## 7. 完整說明 — 韓文版 (Full Description - Korean `ko-KR`)

```text
갤러리에서 공유를 누르고 휴대폰을 태그하면 업데이트 완료. 별도의 수동 설정 없이 무전원 NFC 전자종이에서 선명한 색상과 디테일을 구현합니다.

무전원 NFC 전자종이(스마트 사원증, 회의실 명패, 디지털 명함, 수하물 태그 및 Santek EZ Sign 호환 디스플레이 등)는 충전 없이도 화면을 반영구적으로 유지합니다. TapInk는 전자종이 업데이트 과정을 Android 시스템 공유 메뉴에 통합하여, 이미지를 고르고 공유를 누른 뒤 휴대폰 뒷면을 전자종이에 밀착하는 것만으로 수 초 내에 화면을 갱신합니다.

태그하는 순간 화면 크기와 색상 규격을 자동으로 인식하며, 반사형 전자잉크 특성에 맞춰 인물 그라데이션과 텍스트, QR 코드의 선명도를 최적화합니다. 모든 이미지 처리는 기기 내부에서 오프라인으로 이루어지며 복잡한 설정이나 네트워크 연결이 필요하지 않습니다.

⚡ 3단계 간편 업데이트
1. 화면 선택: Google 포토, Canva 또는 디자인 앱에서 사진, 명함, 테이블 명패 이미지를 준비합니다.
2. 시스템 '공유' 선택: Android 공유 메뉴에서 '전자종이로 전송 (TapInk)'을 선택하면(또는 홈 화면에서 'TapInk 전자종이' 앱을 직접 실행하여 사진 선택), 화면 하단에 가벼운 NFC 스캔 시트가 나타납니다.
3. 태그 후 자동 닫기: 휴대폰 뒷면 상단의 NFC 안테나 부위를 전자종이 중앙에 밀착하고 고정합니다. 전송이 100% 완료되고 화면 갱신이 끝나면 시트가 자동으로 닫히며 이전 앱으로 돌아갑니다.

🌟 주요 기능 및 설계
• 하드웨어 규격 자동 인식
  태그 즉시 전자종이의 화면 크기(1.54", 2.13", 2.66" 및 HD, 2.9" 및 HD, 3.0", 3.6", 3.7", 4.2")와 색상 모드(2색 흑백, 3색 흑백적, 4색 흑백적황 BWRY, 6색 풀컬러 Spectra 6)를 자동으로 읽어 최적의 비율과 해상도로 맞춤 크롭합니다.
• 전자잉크 맞춤형 컬러 디더링 알고리즘
  반사형 전자종이 안료 특성에 맞춰 조정된 Floyd-Steinberg 디더링 엔진을 통해 자연스러운 피부 톤과 명암 그라데이션을 표현하며, 깔끔한 배경과 선명한 텍스트 및 QR 코드 외곽선을 유지합니다.
• 안정적인 NFC 전력 공급 및 즉시 재시도
  Android 7.0부터 Android 15+까지 안정적인 NFC 연속 전력 공급 타이밍과 LZO 블록 압축 전송을 지원합니다. 전송 중 휴대폰이 떨어지더라도 다시 밀착하면 그 자리에서 바로 이어서 재시도할 수 있습니다. 실물 태그가 없을 때도 중앙 미리보기 카드를 1초간 길게 눌러 데모 모드로 전체 과정을 미리 체험할 수 있습니다.

🔒 100% 오프라인 동작 및 개인정보 보호
• 단일 권한 설계: NFC 하드웨어 제어 권한(android.permission.NFC)만 요청하며, 인터넷 연결 권한을 요구하지 않습니다.
• 기기 내 로컬 처리: 개인 사진, 비즈니스 명함, 회의실 명패 및 바코드 데이터는 모두 휴대폰 메모리 내에서 즉시 변환되어 전자종이로 직접 전송되며 외부 서버로 업로드되지 않습니다.
• 가볍고 깔끔한 환경: 광고, 분석 추적, 계정 로그인, 백그라운드 상주 프로세스가 없습니다.

📱 지원 하드웨어 및 고지사항
• Santek EZ Sign 시리즈 및 호환 규격의 1.54", 2.13", 2.66", 2.9", 3.0", 3.6", 3.7", 4.2" 무전원(패시브) NFC 전자종이 스마트 명찰, 테이블 명패, 디지털 명함, 수하물 태그(2색, 3색, 4색, 6색 모델)를 지원합니다.
※ TapInk는 독립적으로 개발된 애플리케이션으로 Santek, EZ Sign 또는 기타 하드웨어 제조사와 제휴, 공식 승인 또는 보증 관계가 없습니다. 언급된 브랜드 및 제품 명칭은 하드웨어 호환성을 안내하기 위한 목적으로만 사용되었습니다.
```
