# RikkaHub 自訂壓縮提示詞版

<img src="docs/icon.png" alt="RikkaHub 圖示" width="80" />

基於 RikkaHub 3.3.1，由 Ilan-JK 維護的上下文壓縮自訂版。

[简体中文](README_ZH_CN.md) | 繁體中文 | [English](README.md)

## 下載

**[下載 ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/3.3.1-custom-compress.8/RikkaHub-3.3.1-custom-compress.8-arm64.apk)** · [版本說明與原始碼資料](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

版本 `3.3.1-custom-compress.8`，16.47 MB（15.71 MiB），Android 8.0 及以上，ARM64。

套件名稱為 `me.rerere.rikkahub.debug`，使用獨立簽章，可與官方版並存，不能直接覆蓋官方版。

## 本版改了什麼

在 **模型與服務 → 預設模型和提示詞 → 提示詞** 編輯主壓縮提示詞和中途附加提示詞。壓縮沿用目前的對話模型。

- 自動壓縮可按上下文比例或未壓縮訊息筆數觸發。預設 50 筆，只在對話閒置時觸發。
- 壓縮範圍預設為 `0`，壓縮所有可選訊息。`+n` 壓縮最早的 n 筆，`-n` 保留最近的 n 筆。
- 可選擇只讀取最新摘要或讀取所有摘要，也可設定壓縮指令角色和隨請求傳送的內容。
- 刪除摘要會釋放它涵蓋的原訊息。「讀取全部」依對話順序傳送，壓縮請求逐段標註未壓縮訊息。「僅最新」從對話中的最後一則摘要開始。
- 從右上角直接開啟對話地圖。摘要涵蓋的原文預設摺疊。
- 訊息選單可檢視請求內容。串流回覆收到第一個封包後，立即以統計列的小字顯示耗時。

## 為什麼說「恢復」

自訂壓縮提示詞不是本專案首創，而是 RikkaHub 曾經提供過的功能：

- 官方 **2.2.6** 已提供[壓縮提示詞編輯介面](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158)，儲存使用者自訂的 `compressPrompt`，壓縮時也讀取它。見[設定儲存](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183)及[壓縮呼叫](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845)。
- 到 **3.0.0**，壓縮流程調整後，主體提示詞[固定在 `buildCompressPrompt` 中](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt)。使用者仍可填寫單次附加說明，但不能再透過設定替換完整提示詞。
- 本專案從 3.1.2 恢復該設定，目前在 3.2.0 上擴充壓縮範圍、觸發方式與摘要讀取策略。

## 原始碼與建置

[修改說明](FORK_NOTES.md) · [建置方法與元件來源](tools/custom-apk/README.md)

## 致謝與授權

基於 [RikkaHub](https://github.com/rikkahub/rikkahub)，感謝 [re-ovo](https://github.com/re-ovo) 及所有上游貢獻者。本社群修改版由 Ilan-JK 維護，修改日期為 2026-11-12。

專案繼續採用 [AGPL-3.0](LICENSE)，保留原有著作權及第三方授權聲明。
