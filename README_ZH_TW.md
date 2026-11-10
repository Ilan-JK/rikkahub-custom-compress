# RikkaHub 自訂壓縮提示詞版

<img src="docs/icon.png" alt="RikkaHub 圖示" width="80" />

在 RikkaHub 3.1.2 上恢復完整的壓縮提示詞自訂能力，保留 v3 的壓縮流程。

[简体中文](README_ZH_CN.md) | 繁體中文 | [English](README.md)

## 下載

**[下載 ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/v3.1.2-custom-compress-1/RikkaHub-3.1.2-custom-compress-arm64-optimized.apk)** · [版本說明與原始碼資料](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

版本 `3.1.2-custom-compress-1`，15.49 MiB，Android 8.0 及以上，ARM64。

套件名稱為 `me.rerere.rikkahub.debug`，使用獨立簽章，可與官方版並存，不能直接覆蓋官方版。安裝包是經過最佳化的 Release 建置，套件名稱中的 `.debug` 不代表可偵錯建置。

## 本版改了什麼

在 **設定 → 模型 → 提示詞 → 壓縮歷史** 中，可以編輯並儲存完整的壓縮提示詞。它與翻譯、標題產生、聊天回覆建議及 OCR 辨識放在同一頁面，支援還原原始提示詞和 `{locale}` 語言變數。

壓縮時的模型選擇、歷史處理、單次附加說明及摘要插入方式沿用 v3 實作。這次恢復的是提示詞自訂能力，不是退回舊版壓縮邏輯，也沒有恢復舊版獨立的壓縮模型設定。提示詞建構與設定持久化均有針對性的單元測試。

## 為什麼說「恢復」

自訂壓縮提示詞不是本專案首創，而是 RikkaHub 曾經提供過的功能：

- 官方 **2.2.6** 已提供[壓縮提示詞編輯介面](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158)，儲存使用者自訂的 `compressPrompt`，壓縮時也讀取它。見[設定儲存](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183)及[壓縮呼叫](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845)。
- 到 **3.0.0**，壓縮流程調整後，主體提示詞[固定在 `buildCompressPrompt` 中](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt)。使用者仍可填寫單次附加說明，但不能再透過設定替換完整提示詞。
- 本版在 3.1.2 原始碼上重新開放這個設定，不回退 v3 的壓縮流程。

## 原始碼與建置

[修改說明](FORK_NOTES.md) · [建置方法與元件來源](tools/custom-apk/README.md)

## 致謝與授權

基於 [RikkaHub](https://github.com/rikkahub/rikkahub)，感謝 [re-ovo](https://github.com/re-ovo) 及所有上游貢獻者。本社群修改版由 Ilan-JK 維護，修改日期為 2026-11-10。

專案繼續採用 [AGPL-3.0](LICENSE)，保留原有著作權及第三方授權聲明。
