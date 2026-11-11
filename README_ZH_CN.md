# RikkaHub 自定义压缩提示词版

<img src="docs/icon.png" alt="RikkaHub 图标" width="80" />

基于 RikkaHub 3.2.0，由 Ilan-JK 维护的上下文压缩定制版。

简体中文 | [繁體中文](README_ZH_TW.md) | [English](README.md)

## 下载

**[下载 ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/3.2.0-custom-compress-1/RikkaHub-3.2.0-custom-compress-arm64-optimized.apk)** · [版本说明与源码资料](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

版本 `3.2.0-custom-compress-1`，16.33 MB（15.57 MiB），Android 8.0 及以上，ARM64。

包名为 `me.rerere.rikkahub.debug`，使用独立签名，可与官方版并存，不能直接覆盖官方版。安装包是经过优化的 Release 构建，包名中的 `.debug` 不代表可调试构建。

## 本版改了什么

在 **设置 → 模型 → 提示词 → 压缩历史** 中，可以编辑并保存完整的压缩提示词。它与“翻译”“标题生成”“聊天回复建议”“OCR 识别”放在同一页面，支持恢复原始提示词和 `{locale}` 语言变量。

- 手动和自动压缩分别设置“压缩最早的信息数”或“保留最近的信息数”，分别记住输入值，默认 20。
- 自动压缩可选择 Token 比例或“信息条数触发”，后者默认 50，仅在一轮回复和工具处理完成后的空闲阶段执行。
- 计数只包括未被摘要覆盖的普通信息，摘要检查点不占触发、压缩或保留条数。
- 支持“仅读最新摘要”与“多个摘要共同读取”；已压缩原文保留在本地，但不再发送。
- 完整压缩提示词与中途压缩提示词可编辑、保存和恢复默认，手动和自动压缩共用设置。
- 暂停更新提醒可输入整数天数；“关于”和“赞助”简单标注改版身份，移除自动赞助弹窗。
- 移除系统日历工具及其读写权限；本地使用统计热力图不依赖系统日历。

使用经过代码精简和资源优化的 Release 构建，不以 Debug 包作为日常安装包；尚未完成真机性能和全部服务商端到端验证。

## 为什么说“恢复”

自定义压缩提示词不是本项目首创，而是 RikkaHub 曾经提供过的功能：

- 官方 **2.2.6** 已提供[压缩提示词编辑界面](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158)，保存用户自定义的 `compressPrompt`，压缩时也读取它。见[设置存储](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183)和[压缩调用](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845)。
- 到 **3.0.0**，压缩流程调整后，主体提示词[写死在 `buildCompressPrompt` 中](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt)。用户仍可填写单次附加说明，但不能再通过设置替换完整提示词。
- 本项目从 3.1.2 恢复该设置，当前在 3.2.0 上进一步扩展压缩范围、触发方式和摘要读取策略。

## 源码与构建

[改版说明](FORK_NOTES.md) · [构建方法与组件来源](tools/custom-apk/README.md)

## 致谢与许可

基于 [RikkaHub](https://github.com/rikkahub/rikkahub)，感谢 [re-ovo](https://github.com/re-ovo) 及所有上游贡献者。本社区修改版由 Ilan-JK 维护，修改日期为 2026-11-11。

项目继续采用 [AGPL-3.0](LICENSE)，保留原有版权与第三方许可声明。
