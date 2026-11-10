# RikkaHub 自定义压缩提示词版

<img src="docs/icon.png" alt="RikkaHub 图标" width="80" />

在 RikkaHub 3.1.2 上恢复完整的压缩提示词自定义能力，保留 v3 的压缩流程。

简体中文 | [繁體中文](README_ZH_TW.md) | [English](README.md)

## 下载

**[下载 ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/v3.1.2-custom-compress-1/RikkaHub-3.1.2-custom-compress-arm64-optimized.apk)** · [版本说明与源码资料](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

版本 `3.1.2-custom-compress-1`，15.49 MiB，Android 8.0 及以上，ARM64。

包名为 `me.rerere.rikkahub.debug`，使用独立签名，可与官方版并存，不能直接覆盖官方版。安装包是经过优化的 Release 构建，包名中的 `.debug` 不代表可调试构建。

## 本版改了什么

在 **设置 → 模型 → 提示词 → 压缩历史** 中，可以编辑并保存完整的压缩提示词。它与“翻译”“标题生成”“聊天回复建议”“OCR 识别”放在同一页面，支持恢复原始提示词和 `{locale}` 语言变量。

压缩时的模型选择、历史处理、单次附加说明和摘要插入方式沿用 v3 实现。这次恢复的是提示词自定义能力，不是退回旧版压缩逻辑，也没有恢复旧版独立的压缩模型设置。提示词构建和设置持久化均有针对性的单元测试。

## 为什么说“恢复”

自定义压缩提示词不是本项目首创，而是 RikkaHub 曾经提供过的功能：

- 官方 **2.2.6** 已提供[压缩提示词编辑界面](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158)，保存用户自定义的 `compressPrompt`，压缩时也读取它。见[设置存储](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183)和[压缩调用](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845)。
- 到 **3.0.0**，压缩流程调整后，主体提示词[写死在 `buildCompressPrompt` 中](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt)。用户仍可填写单次附加说明，但不能再通过设置替换完整提示词。
- 本版在 3.1.2 源码上重新开放这个设置，不回退 v3 的压缩流程。

## 源码与构建

[改版说明](FORK_NOTES.md) · [构建方法与组件来源](tools/custom-apk/README.md)

## 致谢与许可

基于 [RikkaHub](https://github.com/rikkahub/rikkahub)，感谢 [re-ovo](https://github.com/re-ovo) 及所有上游贡献者。本社区修改版由 Ilan-JK 维护，修改日期为 2026-11-10。

项目继续采用 [AGPL-3.0](LICENSE)，保留原有版权与第三方许可声明。
