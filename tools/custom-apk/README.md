# Custom APK build information

Modified by Ilan-JK, 2026-11-10. This is an unofficial RikkaHub build under the root AGPL-3.0 license, without warranty.

The application source used for the ARM64 APK is the initial source snapshot `b24477c2b9bb5b1da3ef1b651c47d9f9db0c20ff`. Later commits add publication documentation and build support without changing the application Kotlin implementation. This is not a claim of bit-for-bit reproducibility or a complete legal audit.

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
gradle --no-daemon -I tools/custom-apk/local.init.gradle -Prikkahub.localOptimized=true -Pandroid.overridePathCheck=true assembleGithubRelease
```

Outputs are under `app/build/outputs/apk/github/release/`; select the ARM64 file. This builds all ABI splits as configured upstream; it does not make the universal file ARM64-only. Do not run a public build with your personal `google-services.json` in `app/` when using the placeholder configuration.

The init script retains the functional local release overrides: application ID suffix `.debug`, debug signing configuration, optimization enabled, `debuggable=false`, placeholder Firebase resources, collection disabled, and Google Services/Crashlytics upload tasks disabled where applicable. The portable script uses repository-relative resource paths and normal upstream dependency repositories.

The release's `source-and-build-materials.zip` also contains the original init script, localhost dependency-cache helper, and manifest/resources used for the existing APK. They contain no credentials. To recreate their original relative layout, put the source checkout in a directory named `rikkahub-master` and the original support files in its sibling `.build-tools` directory. The cache helper listens only on `127.0.0.1:18765`; keep it running while using the original init script. Configure SDK/JDK/Gradle paths for your machine, then use:

```powershell
gradle --no-daemon -I ../.build-tools/google-repositories.init.gradle -Prikkahub.localOptimized=true -Pandroid.overridePathCheck=true assembleGithubRelease
```

The original build used cached dependencies, existing generated baseline/startup profiles, R8 and resource optimization. Upstream includes a mutable `sqlite-android` SNAPSHOT dependency. Rebuilt APK bytes, size and signing certificate may therefore differ. General-purpose SDKs, caches and private keys are not included.

## Native dependencies and remaining provenance limits

The published APK is the existing, unchanged 16,247,376-byte file, SHA-256 `62111775b9aaa54d1d7a7bd342150700c2174117d0408cd50a4dd681f3fbe580`. Its prebuilt native libraries were inherited from upstream, not compiled by this fork. Source materials are attached alongside the APK for continued inspection and rebuilding:

- PRoot: binary version string `5.1.107.96`; [source commit](https://github.com/termux/proot/tree/a179d3e8a4e045aaa1fb8cc3284f23509d96d353), GPL-2.0-or-later. Upstream RikkaHub [binary-update commit](https://github.com/rikkahub/rikkahub/commit/bf8ff5a27482c7e87cebdc0b0a71cac0dbed7c69).
- The matching-era Termux package recipe declares talloc 2.4.3 and libandroid-shmem 0.7. Their upstream source archives and the package recipes from [termux-packages de396619](https://github.com/termux/termux-packages/tree/de39661946f7e8175b5dd0755121fa28cb0aebd1/packages) are attached. **These are reference inputs, not verified exact inputs for RikkaHub's statically linked binary. Its complete static-dependency provenance remains unverified.**
- simple: [v0.6.1](https://github.com/wangfenjin/simple/tree/v0.6.1), choose its MIT license. Upstream RikkaHub [documents](https://github.com/rikkahub/rikkahub/commit/2b522f99090755f8d8ffd9a776e91157f7470867) NDK 29, API 21, Release, static libc++, `SIMPLE_WITH_JIEBA=OFF` and neutral file-prefix mapping. Source archive attached; submodules, where needed, must be obtained at the archive's `.gitmodules`/gitlink versions.
- Terminal emulator source and attribution: `terminal/README.md` and `terminal/LICENSE` (Apache-2.0).
- Material color utilities: `material3/material-color-utilities/kotlin/LICENSE` (Apache-2.0).
- Other runtime libraries: versions in the Gradle catalog and pnpm lockfile; retained embedded license/notice files in the APK. This is not an exhaustive third-party license inventory.

Full license texts for the attached third-party sources are in their archives. Their licenses continue to apply; they are not replaced by the application's AGPL license. The application's About links in this existing APK still point to upstream; use this repository and the release attachments for this fork's source. A future rebuilt version should expose this fork's source directly in its Android and web interfaces.
