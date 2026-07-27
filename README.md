# TG → WA 一鍵貼圖轉換器

將整個 Telegram sticker pack 自動下載、轉成 WhatsApp 規格、每 30 張分包，
亦可以將本機影片剪成同時符合 Telegram 與 WhatsApp 限制的動態貼圖。

支援：

- Telegram 靜態 `.webp` / `.png` 貼圖
- Telegram 動畫 `.tgs` 貼圖
- Telegram 影片 `.webm` 貼圖
- 同一來源有靜態及動態貼圖時，自動分成獨立 `Static`／`Animated` pack
- 超過 30 張自動命名及分包：`Part 1`、`Part 2`、`Part 3`……直到完成
- 自動轉成 512 × 512、控制 WhatsApp 檔案大小
- 影片工作室：自訂開始時間、剪輯秒數、大小、位置及透明／黑／白背景
- 一次輸出 Telegram VP9 `.webm`、WhatsApp 動態 `.webp`，以及供
  Sticker Maker 以影片方式匯入的 `.mp4`
- 轉換完成後可直接經自己嘅 Telegram Bot 建立／加入動態貼圖包，毋須在
  手機相片選擇器尋找 `.webm`
- 可將 3–30 個 WhatsApp 動態 WebP 加入清單，一鍵組成真正會郁嘅
  `.wastickers` 貼圖包
- 附送 Android `TGWA Maker`，可在手機直接轉 Telegram／影片，亦可開啟
  `.wastickers` 及交給 WhatsApp
- 手機 QR Code 傳送，不用人手逐張搬檔
- 完成後可在電腦直接下載每個輸出檔案

## 最快使用方法

1. 雙擊 [啟動轉換工具.bat](./啟動轉換工具.bat)。
2. 第一次啟動會自動建立獨立 Python 環境及安裝轉換元件。
3. 第一次使用，到 Telegram [@BotFather](https://t.me/BotFather) 輸入
   `/newbot`，跟指示建立 bot，然後複製 Bot Token。
4. 將 Telegram 貼圖包連結及 Bot Token 貼入介面，按「一鍵轉成
   WhatsApp 貼圖」。
5. Android 手機第一次先安裝完成頁提供的 `TGWA-Maker.apk`。
6. 完成後可在電腦直接下載 `.wastickers`，或用同一 Wi-Fi 下的手機掃
   QR Code 下載。
7. 在 Android 用 `TGWA Maker` 開啟 `.wastickers`，再按
   **Add to WhatsApp**。Bridge 只需安裝一次。

Bot Token 設定一次後會經 Windows DPAPI 加密，只有目前 Windows 使用者可以
解密。Token 不會寫入瀏覽器或原始碼。

## 影片製作動態貼圖

1. 開啟「影片製作動態貼圖」分頁，拖入 MP4、MOV、M4V、MKV、WEBM、AVI
   或 GIF（最多 512 MB）。
2. 揀開始時間及 0.2–3 秒片段；拖動畫面改位置，亦可調校 25–400% 大小。
3. 選透明、黑色或白色背景，按「輸出 TG + WhatsApp 動態貼圖」。
4. 工具會自動搜尋檔案限制內最高可用畫質，輸出：
   - Telegram：512 × 512、VP9 WEBM、無聲、最多 30 FPS、最多 3 秒及
     256 KB。
   - WhatsApp：512 × 512、Animated WebP、最多 500 KB。
   - Sticker Maker：512 × 512 H.264 MP4 匯入片；第三方 app 如將
     Animated WebP 當成靜態，改用這個影片檔匯入。
5. 在完成頁按「連接 Telegram Bot」。首次使用請打開自己嘅 Bot，按
   **Start** 或傳送 `/start`，返到工具再按「重新檢查」。
6. 選擇「建立新動態貼圖包」或「加入已有貼圖包」，工具會將正確嘅
   VP9 WEBM 直接交給 Telegram；最後按貼圖包連結加入即可。

手機相片／媒體選擇器通常只會顯示 WhatsApp `.webp`，唔會顯示 Telegram
`.webm`；而 Telegram 不支援 Animated WebP，誤將 WhatsApp `.webp` 加入
Telegram 只會變成靜態貼圖。完成頁嘅直接加入功能會繞過呢個選擇器。

每次完成一段影片後，可按「加入 WhatsApp 動態貼圖包」。累積 3–30 段後，
輸入貼圖包名稱及作者，按「建立 `.wastickers`」。工具會保留 Animated WebP，
不會將動畫轉成靜態；亦不會為湊足三張而製造重複貼圖。

`Sticker Maker Import.mp4` 仍保留作第三方 app 手動匯入後備，但正常 Android
流程應使用上述 `.wastickers` + `TGWA Maker`，毋須 Sticker Maker。

## WhatsApp 動態貼圖包

1. 在影片工作室完成第一段片，按「加入 WhatsApp 動態貼圖包」。
2. 換另一段影片或另一個位置／剪輯設定再輸出，重複加入；清單至少 3 張，
   最多 30 張。
3. 輸入貼圖包名稱及作者，再按建立。
4. 在完成頁直接下載 `.wastickers`。Android 第一次亦下載並安裝
   `TGWA-Maker.apk`。
5. 在手機檔案管理員按 `.wastickers`，選擇 TGWA Maker，完成驗證後按
   **Add to WhatsApp**。

Bridge 會在手機本機安全解壓，拒絕路徑穿越或超大檔案，並再次檢查每張貼圖
尺寸、檔案大小及真正 Animated WebP 影格。若匯入檔同時有靜態及動態貼圖，
Bridge 會自動拆成兩類，再各自按每包最多 30 張分 Part；任何一類不足 3 張就
清楚報錯，絕不複製貼圖湊數。匯入內容只保存在 Bridge 私有資料夾。

## 點解需要 Bot Token？

Telegram 公開貼圖頁只會開啟 Telegram app，不會提供整包貼圖檔案。官方 Bot
API 的 `getStickerSet` 及 `getFile` 才能可靠地取得貼圖包內容，所以第一次需要
建立一個免費 Telegram bot。Bot 不需要加入任何群組。若要用「直接加入
Telegram」功能，你需要在自己同 Bot 嘅私人對話按一次 **Start**，等工具取得
你嘅 Telegram user ID；之後會安全記住帳戶配對。

## 點解 Android 仍要 TGWA Maker？

WhatsApp 官方匯入機制要求由 Android／iOS 貼圖 app 提供貼圖包，並由使用者
明確確認加入；桌面程式不能靜默直接寫入 WhatsApp 貼圖庫。此工具已將最麻煩的
下載、動畫轉檔、壓縮、分包與手機傳送全部自動化；TGWA Maker 則提供 WhatsApp
要求的 Android ContentProvider 及啟用 Intent，只保留 WhatsApp 必須的最後
確認。它不是從 StickerVibe 抽取出來，亦不包含廣告、AI、帳戶或付費功能。

WhatsApp 官方格式重點（貼圖包模式）：

- 每包 3–30 張
- 貼圖 512 × 512
- 靜態貼圖最多 100 KB
- 動畫貼圖最多 500 KB
- 動畫最長 10 秒
- 動畫每格最少 8 ms

參考：

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [WhatsApp 官方 Sticker sample](https://github.com/WhatsApp/stickers/tree/main/Android)

## APK 內完整轉換／影片製作

技術上可以將 Telegram 一鍵下載、裁切排版及動態貼圖製作全部搬入 Android，
但不能用 Android `Bitmap.compress()` 將影片逐格「另存 WebP」代替真正動畫
編碼；該路徑只會產生單幀 WebP，加入 WhatsApp 後就會變定格。

`TGWA Maker 2.1.2` 已在 APK 內完成：

- 貼上 Telegram sticker pack 連結後，直接以 Bot API 下載整包。
- 靜態 WebP／PNG、TGS 及 WEBM 分別解碼；TGS 由 Lottie 渲染，影片及 WEBM
  經手機媒體解碼器取幀。
- 自選手機影片、0.2–3 秒剪輯、拖曳位置、手勢縮放、透明／黑／白背景。
- 經 JNI 使用 `libwebp WebPAnimEncoder` 真正逐幀編碼 Animated WebP。
- 動圖與普通圖自動分開，超過 30 張自動建立 Part 1、Part 2…，並避免產生
  少於 3 張的尾包。
- Bot Token 由 Android Keystore 加密，只留在手機。
- APK 介面跟本機版深綠卡片排版，Telegram 轉換同影片 Maker 分成兩個分頁。
- 內置繁體中文（香港）／英文切換；切換時保留已填連結、Token、包名同已選影片。
- Token 可直接貼上、顯示／隱藏、忘記及開啟 BotFather，但唔會硬編碼入 APK。
- 撳一鍵轉換後即時顯示階段文字、百分比同粗進度條。
- 轉換完成會自動開啟 WhatsApp 加入畫面；多 Part 會喺每次確認後接住開下一包。
- Android 9+ 會按 frame index 分批解碼 Telegram VP9 WEBM，避免部分手機用時間
  抽格時不停取得同一個 keyframe，令動畫被壓成單格。

每個動態輸出都要通過 `ANIM + 最少 2 個 ANMF`、每格 8 ms、最長 10 秒、
512 × 512 及 500 KB 等檢查，才會出現在 **Add to WhatsApp** 清單。通過後的
Animated WebP bytes 會原封不動保存及交給 WhatsApp，不會在加入時再壓成定格。

## 私隱與網絡

- 圖像轉檔全部在本機完成，不會上載到第三方轉檔網站。
- 影片上載只係由瀏覽器送到同一部電腦的本機服務，不會離開電腦。
- 工具會聯絡 Telegram 官方 API 下載貼圖；轉換元件亦可能向 GitHub 查詢可選
  WhatsApp bridge 的版本，但不會將貼圖上載到該處。
- 只有你主動按「建立／加入／傳送 Telegram」時，Telegram WEBM 成品先會
  上載到 Telegram 官方 Bot API。
- 手機 QR 連結只在電腦工具開啟、而且手機與電腦在同一個 Wi-Fi 時有效。
- QR 下載網址包含隨機分享密碼；其他人不能靠猜網址取得貼圖。
- `.local/`、`output/` 與 `.venv/` 已排除於 Git。

## 輸出位置

完成的貼圖會放在：

```text
output/<Telegram-pack-name>-<日期時間>/ready/
output/video-<日期時間>-<識別碼>/ready/
output/whatsapp-pack-<日期時間>-<識別碼>/ready/
```

介面亦有「開啟輸出資料夾」按鈕。

## 開發與測試

顯示伺服器錯誤：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-debug.ps1
```

執行測試：

```powershell
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
node --check .\static\app.js
```

測試包含 Windows 加密 token round-trip、Telegram URL 驗證、Bot 連接與
動態貼圖發佈 mock、`.wastickers` 內容檢查、影片上載／排版，以及 Telegram
WEBM + WhatsApp Animated WebP 真實轉檔 smoke test。

編譯 Android Maker（需要 JDK 17+、Android SDK 35、NDK 27.3、CMake 3.22
及 Gradle 8.7）：

```powershell
cd .\android-bridge
gradle --no-daemon testDebugUnitTest assembleDebug
```

目前可安裝 APK 位於
`android-bridge/dist/TGWA-Maker.apk`。這是 sideload 測試／自用版本，採用
Android debug certificate 簽名；日後公開商店版本需要改用持久 release key。

## 疑難排解

### 手機掃 QR 後打不開

確認手機與電腦使用同一個 Wi-Fi，並在 Windows 防火牆提示時允許私人網絡存取。
亦可按「複製手機連結」傳給自己。

### Telegram Bot Token 無效

回到 [@BotFather](https://t.me/BotFather)，用 `/mybots` 選擇 bot，再重新複製
API Token。請勿包含前後空格。

### 關閉瀏覽器後程式仍在運行

按介面右上角電源圖示會同時關閉本機服務。重新雙擊啟動檔只會重開現有服務，
不會重複啟動多個程序。
