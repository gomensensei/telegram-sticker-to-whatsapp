# TG → WA 一鍵貼圖轉換器

將整個 Telegram sticker pack 自動下載、轉成 WhatsApp 規格、每 30 張分包，
最後產生可在手機下載的 `.wastickers` 檔案。

支援：

- Telegram 靜態 `.webp` / `.png` 貼圖
- Telegram 動畫 `.tgs` 貼圖
- Telegram 影片 `.webm` 貼圖
- 超過 30 張自動命名及分包：`Part 1`、`Part 2`、`Part 3`……直到完成
- 自動轉成 512 × 512、控制 WhatsApp 檔案大小
- 手機 QR Code 傳送，不用人手逐張搬檔
- 完成後可在電腦直接下載每個 `.wastickers` Part

## 最快使用方法

1. 雙擊 [啟動轉換工具.bat](./啟動轉換工具.bat)。
2. 第一次啟動會自動建立獨立 Python 環境及安裝轉換元件。
3. 第一次使用，到 Telegram [@BotFather](https://t.me/BotFather) 輸入
   `/newbot`，跟指示建立 bot，然後複製 Bot Token。
4. 將 Telegram 貼圖包連結及 Bot Token 貼入介面，按「一鍵轉成
   WhatsApp 貼圖」。
5. 完成後用手機掃 QR Code，下載 `.wastickers`。
6. 用 Sticker Maker Studio（iOS）或支援 `.wastickers` 的
   WAStickerApps（Android）開啟，再按 Add to WhatsApp。

Bot Token 設定一次後會經 Windows DPAPI 加密，只有目前 Windows 使用者可以
解密。Token 不會寫入瀏覽器或原始碼。

## 點解需要 Bot Token？

Telegram 公開貼圖頁只會開啟 Telegram app，不會提供整包貼圖檔案。官方 Bot
API 的 `getStickerSet` 及 `getFile` 才能可靠地取得貼圖包內容，所以第一次需要
建立一個免費 Telegram bot。Bot 不需要加入任何群組，亦不需要收取私人訊息。

## 點解手機仍要 Sticker Maker？

WhatsApp 官方匯入機制要求由 Android／iOS 貼圖 app 提供貼圖包，並由使用者
明確確認加入；桌面程式不能靜默直接寫入 WhatsApp 貼圖庫。此工具已將最麻煩的
下載、動畫轉檔、壓縮、分包與手機傳送全部自動化，只保留 WhatsApp 必須的最後
確認。

WhatsApp 官方格式重點：

- 每包 3–30 張
- 貼圖 512 × 512
- 靜態貼圖最多 100 KB
- 動畫貼圖最多 500 KB
- 動畫最長 10 秒

參考：

- [Telegram Bot API](https://core.telegram.org/bots/api)
- [WhatsApp 官方 Sticker sample](https://github.com/WhatsApp/stickers/tree/main/Android)

## 私隱與網絡

- 圖像轉檔全部在本機完成，不會上載到第三方轉檔網站。
- 工具會聯絡 Telegram 官方 API 下載貼圖；轉換元件亦可能向 GitHub 查詢可選
  WhatsApp bridge 的版本，但不會將貼圖上載到該處。
- 手機 QR 連結只在電腦工具開啟、而且手機與電腦在同一個 Wi-Fi 時有效。
- QR 下載網址包含隨機分享密碼；其他人不能靠猜網址取得貼圖。
- `.local/`、`output/` 與 `.venv/` 已排除於 Git。

## 輸出位置

完成的貼圖會放在：

```text
output/<Telegram-pack-name>-<日期時間>/ready/
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

測試包含 Windows 加密 token round-trip、Telegram URL 驗證、`.wastickers`
內容檢查，以及靜態 PNG + 動畫 TGS 真實轉檔 smoke test。

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
