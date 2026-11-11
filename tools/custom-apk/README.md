# Custom APK build information

Modified by Ilan-JK, 2026-11-11, under the root AGPL-3.0 license.

The 3.2.0 ARM64 APK corresponds to the source commit selected by its release tag. The earlier 3.1.2 APK used source snapshot `b24477c2b9bb5b1da3ef1b651c47d9f9db0c20ff`; its historical release and attached source materials remain available.

## Toolchain used

- Windows x64, JDK 21.0.11, Gradle 9.9.0 (the upstream wrapper still names 9.6.0).
- Android SDK platform 37, minor API 2; Build Tools 36.0.0.
- Android NDK 28.2.13676358; CMake 3.22.1.
- Node 24.15.0 and pnpm 11.25.0; dependencies in `web-ui/pnpm-lock.yaml`.
- AGP, Kotlin and other dependency versions: `gradle/libs.versions.toml`.

Install the SDK and set `ANDROID_HOME`, `JAVA_HOME`, and PATH for Gradle 9.9.0, Node and pnpm. Use your own Android debug keystore or signing configuration. The publisher's signing private key is not distributed. Another signing key cannot upgrade the publisher's custom installation; back up your data before installing differently signed builds.

## Portable build override

Run from the repository root (PowerShell example):

```powershell
pnpm --dir web-ui install --frozen-lockfile
gradle --no-daemon -I tools/custom-apk/local.init.gradle '-Prikkahub.localOptimized=true' '-Pandroid.overridePathCheck=true' assembleGithubRelease
```

Outputs are under `app/build/outputs/apk/github/release/`; select the ARM64 file. This builds all ABI splits as configured upstream; it does not make the universal file ARM64-only. Do not run a public build with your personal `google-services.json` in `app/` when using the placeholder configuration.

The init script retains the functional local release overrides: application ID suffix `.debug`, debug signing configuration, optimization enabled, `debuggable=false`, placeholder Firebase resources, collection disabled, and Google Services/Crashlytics upload tasks disabled where applicable. The portable script uses repository-relative resource paths and normal upstream dependency repositories.

The historical 3.1.2 release's `source-and-build-materials.zip` also contains its original init script, localhost dependency-cache helper, and manifest/resources. They contain no credentials. Those files document the older build and are not the current application source. The current repository-relative override above uses normal dependency repositories and does not require that cache helper.

The 3.2.0 build used cached dependencies, existing generated baseline/startup profiles, R8 and resource optimization. The APK embeds its compiled baseline profile; regenerating device-specific profiles is a separate upstream workflow. Upstream includes a mutable `sqlite-android` SNAPSHOT dependency. Rebuilt APK bytes, size, profiles and signing certificate may therefore differ. General-purpose SDKs, caches and private keys are not included.

## Native component sources

The `3.2.0-custom-compress-2` APK is 16,334,739 bytes, SHA-256 `ebb74bb0b3ac4e0e2522e143e0800453c9513438f1d5de8d4d1351a6ab47f1ae`. The prebuilt native components below are inherited from upstream. Historical source archives remain attached to [the 3.1.2 release](https://github.com/Ilan-JK/rikkahub-custom-compress/releases/tag/v3.1.2-custom-compress-1) for continued inspection and rebuilding:

- PRoot: binary version string `5.1.107.96`; [source commit](https://github.com/termux/proot/tree/a179d3e8a4e045aaa1fb8cc3284f23509d96d353), GPL-2.0-or-later. Upstream RikkaHub [binary-update commit](https://github.com/rikkahub/rikkahub/commit/bf8ff5a27482c7e87cebdc0b0a71cac0dbed7c69).
- The matching-era Termux package recipe declares talloc 2.4.3 and libandroid-shmem 0.7. Their upstream source archives and the package recipes from [termux-packages de396619](https://github.com/termux/termux-packages/tree/de39661946f7e8175b5dd0755121fa28cb0aebd1/packages) are attached as reference inputs. The precise static-dependency inputs of the upstream binary have not been verified.
- simple: [v0.6.1](https://github.com/wangfenjin/simple/tree/v0.6.1), choose its MIT license. Upstream RikkaHub [documents](https://github.com/rikkahub/rikkahub/commit/2b522f99090755f8d8ffd9a776e91157f7470867) NDK 29, API 21, Release, static libc++, `SIMPLE_WITH_JIEBA=OFF` and neutral file-prefix mapping. Source archive attached; submodules, where needed, must be obtained at the archive's `.gitmodules`/gitlink versions.
- Terminal emulator source and attribution: `terminal/README.md` and `terminal/LICENSE` (Apache-2.0).
- Material color utilities: `material3/material-color-utilities/kotlin/LICENSE` (Apache-2.0).
- Other runtime libraries: versions in the Gradle catalog and pnpm lockfile; retained embedded license/notice files in the APK.

Full license texts for the historical third-party source attachments are in their archives. Their licenses continue to apply; they are not replaced by the application's AGPL license. The Android About page now includes this fork's source link while retaining upstream attribution and license links.
