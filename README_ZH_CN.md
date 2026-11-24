# RikkaHub 自定义压缩提示词版

<img src="docs/icon.png" alt="RikkaHub 图标" width="80" />

基于 RikkaHub 3.3.1，由 Ilan-JK 维护的上下文压缩定制版。

简体中文 | [繁體中文](README_ZH_TW.md) | [English](README.md)

## 下载

**[下载 ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/3.3.1-custom-compress.6/RikkaHub-3.3.1-custom-compress.6-arm64.apk)** · [版本说明与源码资料](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

版本 `3.3.1-custom-compress.6`，16.47 MB（15.71 MiB），Android 8.0 及以上，ARM64。

包名为 `me.rerere.rikkahub.debug`，使用独立签名，可与官方版并存，不能直接覆盖官方版。

## 本版改了什么

在 **模型与服务 → 默认模型和提示词 → 提示词** 编辑主压缩提示词和中途附加提示词。压缩沿用当前对话模型。

- 自动压缩可按上下文比例或未压缩消息条数触发。条数默认 50，只在会话空闲时触发。
- 压缩范围默认 `0`，压缩全部可选消息。`+n` 压缩最早的 n 条，`-n` 保留最近的 n 条。
- 可选择只读取最新摘要或读取全部摘要，并配置压缩指令角色和随请求发送的内容。
- “读取全部”时，压缩请求会整体标注未压缩信息。
- 从右上角直接打开对话地图。已被摘要覆盖的原文默认折叠。
- 消息菜单可查看请求内容。流式回复收到首包后，立即用统计栏的小字显示耗时。

## 为什么说“恢复”

自定义压缩提示词不是本项目首创，而是 RikkaHub 曾经提供过的功能：

- 官方 **2.2.6** 已提供[压缩提示词编辑界面](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158)，保存用户自定义的 `compressPrompt`，压缩时也读取它。见[设置存储](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183)和[压缩调用](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845)。
- 到 **3.0.0**，压缩流程调整后，主体提示词[写死在 `buildCompressPrompt` 中](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt)。用户仍可填写单次附加说明，但不能再通过设置替换完整提示词。
- 本项目从 3.1.2 恢复该设置，当前在 3.2.0 上进一步扩展压缩范围、触发方式和摘要读取策略。

## 源码与构建

[改版说明](FORK_NOTES.md) · [构建方法与组件来源](tools/custom-apk/README.md)

## 致谢与许可

基于 [RikkaHub](https://github.com/rikkahub/rikkahub)，感谢 [re-ovo](https://github.com/re-ovo) 及所有上游贡献者。本社区修改版由 Ilan-JK 维护，修改日期为 2026-11-12。

项目继续采用 [AGPL-3.0](LICENSE)，保留原有版权与第三方许可声明。
