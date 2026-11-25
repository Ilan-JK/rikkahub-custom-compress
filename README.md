# RikkaHub Custom Compression Prompt

<img src="docs/icon.png" alt="RikkaHub icon" width="80" />

Context-compression customization based on RikkaHub 3.3.1, maintained by Ilan-JK.

[简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_TW.md) | English

## Download

**[Download ARM64 APK](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/download/3.3.1-custom-compress.9/RikkaHub-3.3.1-custom-compress.9-arm64.apk)** · [Release notes and source materials](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/latest)

Version `3.3.1-custom-compress.9`, 16.47 MB (15.71 MiB), Android 8.0+, ARM64.

The package ID is `me.rerere.rikkahub.debug`, with a separate signing certificate, so it installs alongside the official app rather than replacing it.

## What This Version Changes

Edit the main and mid-turn compression prompts under **Models and services → Default models and prompts → Prompts**. Compression uses the current conversation model.

- Trigger automatic compression by context usage or by uncovered message count. The count defaults to 50 and triggers only when the conversation is idle.
- Set the compression range to `0` for all eligible messages, `+n` for the earliest n, or `-n` to keep the latest n.
- Choose which summaries to read, the role of the compression instruction, and what to include in compression requests.
- Deleting a summary releases the messages it covered. Read All preserves conversation order and marks each uncovered block in compression requests. Latest Only starts at the last summary in the conversation.
- Open the conversation map from the top right. Turn off **Use conversation summaries** to restore covered messages to the request context and pause compression for that conversation. Turn it back on to reuse the saved summaries.
- Inspect requests from the message menu. First-packet timing uses the compact stats style as soon as streaming starts.

## Why Restore It?

Custom compression prompts are an existing RikkaHub feature, not an invention of this project:

- Official **2.2.6** had a [compression-prompt editor](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingModelPromptPage.kt#L158), stored a customizable `compressPrompt` and used it when compressing. See the [settings storage](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt#L183) and [compression call](https://github.com/rikkahub/rikkahub/blob/dcc14b016bf9411b7dd032794efd40cfc79e1ad2/app/src/main/java/me/rerere/rikkahub/service/ChatService.kt#L845).
- By **3.0.0**, the compression workflow had changed and the main instruction was [hardcoded in `buildCompressPrompt`](https://github.com/rikkahub/rikkahub/blob/55a108a4477fa308d6eacbe22ebf2fb0ceea1420/app/src/main/java/me/rerere/rikkahub/data/ai/prompts/CompressPrompt.kt). Per-run additional instructions remained, but the full prompt was no longer a settings field.
- This project restored the editor on 3.1.2 and now extends compression ranges, triggers and summary selection on 3.2.0.

## Source and Build

[Change notes](FORK_NOTES.md) · [Build instructions and component sources](tools/custom-apk/README.md)

## Credits and License

Based on [RikkaHub](https://github.com/rikkahub/rikkahub), developed by [re-ovo](https://github.com/re-ovo) and its contributors. This community modification is maintained by Ilan-JK, dated 2026-11-12.

The project remains under [AGPL-3.0](LICENSE). Existing copyright and third-party license notices are retained.
