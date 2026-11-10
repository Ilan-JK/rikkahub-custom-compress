# RikkaHub custom compression prompt

This repository is a modified source snapshot of [RikkaHub](https://github.com/rikkahub/rikkahub) 3.1.2. It remains under the upstream [AGPL-3.0 license](LICENSE). It is not an official RikkaHub release.

Changes made in November 2026:

- Added a persistent, fully editable compression prompt to the same model prompt settings page as translation, title generation, chat suggestions, and OCR. The original compression instruction is the default, and `{locale}` remains available as a placeholder.
- Kept the existing conversation compression request flow and per-run additional instructions.
- Added tests for prompt construction and settings persistence.
- Included local build adaptations for producing the separately signed custom Android APK. A custom APK does not replace an official installation and should be identified as an unofficial build.

The local Android SDK, dependency caches, signing keys, `local.properties`, build output, and APK files are not part of this source repository. The upstream GitHub Actions workflows are excluded because the daily release workflow depends on signing and service secrets not configured for this fork.
