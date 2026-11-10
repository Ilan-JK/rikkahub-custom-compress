# RikkaHub Custom Compression Prompt

<img src="docs/icon.png" alt="RikkaHub icon" width="80" />

Restore full compression-prompt customization on RikkaHub 3.1.2, while keeping its v3 compression workflow.

[简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_TW.md) | English

## Download

**[Download ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/v3.1.2-custom-compress-1/RikkaHub-3.1.2-custom-compress-arm64-optimized.apk)** · [Release notes and source materials](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

Version `3.1.2-custom-compress-1`, 15.49 MiB, Android 8.0+, ARM64.

The package ID is `me.rerere.rikkahub.debug`, with a separate signing certificate, so it installs alongside the official app rather than replacing it. The APK is an optimized Release build; `.debug` in the package ID does not mean a debuggable build.

## What This Version Changes

In **Settings > Model > Prompts > Compress history**, edit and save the entire compression prompt, next to translation, title generation, chat suggestions and OCR. The editor also supports resetting to the original prompt and the `{locale}` placeholder.

The model selection, history processing, per-compression additional instructions and summary insertion follow the v3 implementation. This restores prompt customization, not the old compression engine or the old dedicated compression-model setting. Prompt construction and persistence have focused unit tests.

## Why Restore It?

Custom compression prompts are an existing RikkaHub feature, not an invention of this project:

- Official **2.2.6** had a [compression-prompt editor](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158), stored a customizable `compressPrompt` and used it when compressing. See the [settings storage](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183) and [compression call](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845).
- By **3.0.0**, the compression workflow had changed and the main instruction was [hardcoded in `buildCompressPrompt`](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt). Per-run additional instructions remained, but the full prompt was no longer a settings field.
- This version makes the full prompt editable again on the 3.1.2 codebase without reverting the v3 workflow.

## Source and Build

[Change notes](FORK_NOTES.md) · [Build instructions and component sources](tools/custom-apk/README.md)

## Credits and License

Based on [RikkaHub](https://github.com/rikkahub/rikkahub), developed by [re-ovo](https://github.com/re-ovo) and its contributors. This community modification is maintained by Ilan-JK, dated 2026-11-10.

The project remains under [AGPL-3.0](LICENSE). Existing copyright and third-party license notices are retained.
