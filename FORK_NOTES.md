# RikkaHub custom compression prompt

This repository is a modified source snapshot of [RikkaHub](https://github.com/rikkahub/rikkahub) 3.1.2. It remains under the upstream [AGPL-3.0 license](LICENSE). It is not an official RikkaHub release.

Modified by Ilan-JK on 2026-11-10. Changes:

- Added a persistent, fully editable compression prompt to the same model prompt settings page as translation, title generation, chat suggestions, and OCR. The original compression instruction is the default, and `{locale}` remains available as a placeholder.
- Kept the existing conversation compression request flow and per-run additional instructions.
- Added tests for prompt construction and settings persistence.

Build overrides and provenance notes are documented in [tools/custom-apk](tools/custom-apk/README.md). The release attachments provide the unchanged ARM64 APK and source/build reference materials. The original prebuilt native components' full static-dependency provenance has not been independently verified; the notes distinguish confirmed sources from reference inputs. No claim of bit-for-bit reproducibility or complete legal certification is made. Signing private keys remain private; builders can sign their own builds with their own keys.

The local Android SDK, dependency caches, signing keys, `local.properties`, build output, and APK files are not part of this source repository. The upstream GitHub Actions workflows are excluded because the daily release workflow depends on signing and service secrets not configured for this fork.
