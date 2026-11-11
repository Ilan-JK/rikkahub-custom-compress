# RikkaHub 自訂壓縮提示詞版

<img src="docs/icon.png" alt="RikkaHub 圖示" width="80" />

基於 RikkaHub 3.2.0，由 Ilan-JK 維護的上下文壓縮自訂版。

[简体中文](README_ZH_CN.md) | 繁體中文 | [English](README.md)

## 下載

**[下載 ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/3.2.0-custom-compress-1/RikkaHub-3.2.0-custom-compress-arm64-optimized.apk)** · [版本說明與原始碼資料](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

版本 `3.2.0-custom-compress-1`，16.33 MB（15.57 MiB），Android 8.0 及以上，ARM64。

套件名稱為 `me.rerere.rikkahub.debug`，使用獨立簽章，可與官方版並存，不能直接覆蓋官方版。安裝包是經過最佳化的 Release 建置，套件名稱中的 `.debug` 不代表可偵錯建置。

## 本版改了什麼

在 **設定 → 模型 → 提示詞 → 壓縮歷史** 中，可以編輯並儲存完整的壓縮提示詞。它與翻譯、標題產生、聊天回覆建議及 OCR 辨識放在同一頁面，支援還原原始提示詞和 `{locale}` 語言變數。

- 手動與自動壓縮分別設定最早壓縮或最近保留的資訊數，分別記住數值，預設 20。
- 自動壓縮可選 Token 比例或資訊數量觸發，後者預設 50，僅在完整回合結束後的閒置階段執行。
- 僅計算尚未被摘要覆蓋的一般資訊，摘要檢查點不占觸發、壓縮或保留數量。
- 支援僅讀取最新摘要或共同讀取多個摘要；原文保留於本機，不再傳送。
- 完整與回合中壓縮提示詞可編輯及還原，手動與自動壓縮共用設定。
- 更新提醒暫停天數可自訂，加入簡短改版聲明並移除自動贊助彈窗。
- 移除系統行事曆工具及權限；本機使用統計熱力圖不受影響。

安裝包使用程式碼與資源精簡的 Release 建置，尚未完成真機效能及全部服務商流程驗證。

## 為什麼說「恢復」

自訂壓縮提示詞不是本專案首創，而是 RikkaHub 曾經提供過的功能：

- 官方 **2.2.6** 已提供[壓縮提示詞編輯介面](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158)，儲存使用者自訂的 `compressPrompt`，壓縮時也讀取它。見[設定儲存](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183)及[壓縮呼叫](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845)。
- 到 **3.0.0**，壓縮流程調整後，主體提示詞[固定在 `buildCompressPrompt` 中](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt)。使用者仍可填寫單次附加說明，但不能再透過設定替換完整提示詞。
- 本專案從 3.1.2 恢復該設定，目前在 3.2.0 上擴充壓縮範圍、觸發方式與摘要讀取策略。

## 原始碼與建置

[修改說明](FORK_NOTES.md) · [建置方法與元件來源](tools/custom-apk/README.md)

## 致謝與授權

基於 [RikkaHub](https://github.com/rikkahub/rikkahub)，感謝 [re-ovo](https://github.com/re-ovo) 及所有上游貢獻者。本社群修改版由 Ilan-JK 維護，修改日期為 2026-11-11。

專案繼續採用 [AGPL-3.0](LICENSE)，保留原有著作權及第三方授權聲明。
