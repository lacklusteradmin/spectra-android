# Changelog

## Uncommitted

- **The Android app is spectra-apple's, page for page, in Compose and
  Material 3** (`app/src/main/kotlin/dev/spectra/`; before: a `MainActivity`
  stub)
  - Home, History and Settings tabs, each with its own Navigation 3 back stack;
    a page's flow is pushed on the tab it started from and the navigation bar
    shows only at a tab's root.
  - Every iOS feature is ported: dashboard and notices, wallets (create, import,
    watch, detail, advanced, key export, copy to a network, funds finder),
    wallet tools, staking, send and receive, history and the transaction page,
    settings, diagnostics and the crypto wiki.
  - `AppState` is the process's one store, its projections adopted from core
    with one writer each and every edit a command through `StateCommandQueue`,
    as spectra-apple's (`docs/ARCHITECTURE.md`).
  - One orange accent as Material 3's `primary`, no dynamic colour, and the iOS
    spacing and radius steps (`components/`, `docs/ANDROID-UI.md`).
- **Secrets are sealed with Android Keystore keys** (`app/SecureStores.kt`
  `KeystoreSecretStore`)
  - Each value is AES-GCM sealed under a Keystore key and written under the
    no-backup directory, named by its key's SHA-256; the device key is wrapped
    by a second key, in StrongBox where the device has one.
  - Both keys work only while the device is unlocked; a device without a
    screen lock is refused, and nothing is backed up or transferred
    (`allowBackup="false"`, data extraction rules).
- **Device authentication is `BiometricPrompt`** with strong biometrics or the
  screen lock, for unlocking, sends, deleting, resetting and secret material;
  the lock covers the app in a full-screen dialog, and the recents screen
  never shows the app's content.
- **The Gradle project is at the repository's root** (before: `kotlin/`):
  `minSdk` 33, `targetSdk` 37, `compileSdk` 37.2 (before: 26, 34, 34), with a
  version catalog in `gradle/libs.versions.toml`.
- **Core is linked as `SpectraCoreAndroid/`**, the package spectra's
  `make android` builds, its libraries and Kotlin bindings added to the app's
  sources; a build without it, or a release build with a dev one, stops with
  what to do.
- **The string tables are spectra-apple's `RuntimeStrings`**
  (`app/src/main/assets/strings/`), read by `models/AppLocalization.kt` with
  Apple's format specifiers, plus 20 sentences in all three languages for
  wording only Android has and three translations the iOS tables lack.
- **Core's crypto icons are app assets** (`app/src/main/assets/crypto/`, 123
  SVGs), synced by `scripts/export-android-icons.sh` and drawn with
  AndroidSVG; the launcher icon is the iOS icon's bars on black, with a
  themed monochrome layer.
- **Make targets** `lint`, `test`, `test-device`, `build`, `release`,
  `install` and `icons`; `scripts/missing-strings.py` finds a looked-up
  sentence a table lacks.
- Tests: 10 new
  - Secret store 5: a missing value read as present, a value stored in the
    clear or in another class's directory, a delete that leaves it, a device
    key stored unwrapped, a corrupt or tampered wrapping read as a key.
  - Core 2: the library failing to load through JNA, a fresh store opening
    with data.
  - Strings 3: Apple's specifiers read wrongly by Java's formatter.
- Docs: `README.md`, `AGENTS.md` (rewritten for the built app);
  `docs/ARCHITECTURE.md`, `docs/ANDROID-UI.md`, `docs/KOTLIN-BINDINGS.md`,
  `docs/OPEN-ITEMS.md`, `scripts/README.md` (new).
- Total: 161 .kt files changed (160 new, 1 deleted), +31314 −27 lines; data
  and config files: the Gradle build (`settings.gradle.kts`,
  `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, the wrapper), `app/proguard-rules.pro`,
  `AndroidManifest.xml`, `res/`, `assets/`, `Makefile`, `.gitignore`,
  `scripts/export-android-icons.sh`, `scripts/missing-strings.py`;
  dependencies: Android Gradle plugin 8.2.0 → 9.5.2, Kotlin 1.9.22 → 2.4.21,
  Compose BOM 2026.11.01, `activity-compose` 1.8.2 → 1.13.0, JNA 5.14.0 →
  5.19.1, added `core-ktx`, `lifecycle-runtime-compose`, `lifecycle-process`,
  Navigation 3, `biometric`, `fragment-ktx`, CameraX, `material-icons-extended`,
  `kotlinx-coroutines-android`, ZXing, AndroidSVG; tests passing in
  `make test` 0 → 3 and `make test-device` 0 → 7.

## Beta Commit 254

- **`docs/CHANGELOG.md` records what each commit changed, newest first, from
  Beta Commit 37 on**, written from the git history; `AGENTS.md` gains a
  "CHANGELOG" section on how it is written and tidied before a commit, and its
  Git rule points there.
- **`scripts/build-android.sh` and `scripts/bindgen-android.sh` are deleted**:
  both built the `ffi/` crate, which spectra no longer has, so neither ran.
  Android builds core from a spectra checkout when its work starts
  (`README.md`).
- Docs: `CHANGELOG.md` (new); `AGENTS.md` "Git", "CHANGELOG" (new).
- Total: no .kt files changed, +152 −74 lines; data and config files:
  `scripts/build-android.sh`, `scripts/bindgen-android.sh` (deleted);
  dependencies unchanged; no tests.

## Beta Commit 251

- **The README no longer assumes where the spectra checkout is** (`README.md`):
  Android work builds core from a spectra checkout as a `cdylib` for each
  Android ABI, as spectra's `make apple` builds a package for spectra-apple
  (before: from `../Spectra`).
- **spectra's `AGENTS.md` applies once the app builds against core**
  (`AGENTS.md`): its Rule 0 and Funds and keys cover everything here (before:
  add `@../Spectra/AGENTS.md` to `CLAUDE.md`).
- Docs: `README.md`; `AGENTS.md` "Status".
- Total: no code changed, +4 −4 lines.

## Beta Commit 250

- **Android work starts from a spectra checkout beside this repository instead
  of a `spectra/` submodule** (`README.md`): core is built from `../Spectra`,
  with spectra's `scripts/build-apple-package.sh` as the model, and the bindings
  come from its `tools/uniffi-bindgen`.
- **`AGENTS.md` says to load `@../Spectra/AGENTS.md` once the app builds against
  core** (before: `@spectra/AGENTS.md` once `spectra/` is a submodule).
- Docs: `README.md`; `AGENTS.md` "Status".
- Total: no code changed, +8 −8 lines.

## Restore the Android starting point

- **This repository starts from the Android files as they were before Beta
  Commit 236**: the Compose `MainActivity` stub with its Gradle setup in
  `kotlin/`, and `scripts/build-android.sh` and `scripts/bindgen-android.sh`,
  restored unchanged.
- **`README.md` says the app is not started and where the work begins**: none of
  it builds, because the scripts compile an `ffi/` crate that core no longer
  has; work starts by taking spectra as the `spectra/` git submodule, building
  `spectra/core` as a `cdylib` for each Android ABI, and generating bindings
  with `spectra/tools/uniffi-bindgen --language kotlin`.
- **`AGENTS.md`, `CLAUDE.md`, `LICENSE` and `.gitignore` added**: no commit or
  push unless asked; GPL-3.0; Gradle and Android Studio output, the generated
  bindings and the native libraries are ignored.
- Docs: `README.md`, `AGENTS.md` and `CLAUDE.md` (new).
- Total: 1 .kt file changed (1 new), +871 −0 lines; data and config files:
  `kotlin/build.gradle.kts`, `kotlin/settings.gradle.kts`,
  `kotlin/app/build.gradle.kts`, `kotlin/app/src/main/AndroidManifest.xml`,
  `kotlin/app/src/main/res/values/strings.xml`, `scripts/build-android.sh`,
  `scripts/bindgen-android.sh`, `.gitignore`, `LICENSE`; dependencies as before
  Beta Commit 236; test functions unchanged at 0.

## Beta Commit 236

- **Android app removed**: `kotlin/` (the `MainActivity` stub and its Gradle
  setup) and both scripts are deleted, leaving no files.
- Total: 1 .kt file changed, +0 −156 lines; data and config files:
  `kotlin/build.gradle.kts`, `kotlin/settings.gradle.kts`,
  `kotlin/app/build.gradle.kts`, `kotlin/app/src/main/AndroidManifest.xml`,
  `kotlin/app/src/main/res/values/strings.xml`, `scripts/build-android.sh`,
  `scripts/bindgen-android.sh` (all deleted); dependencies: all removed; test
  functions unchanged at 0.

## Beta Commit 231

- **Placeholder `Bindings` and `WalletStore` removed**
  (`kotlin/app/src/main/kotlin/dev/spectra/core/`): the app is the
  `MainActivity` stub alone.
- Total: 2 .kt files changed, +0 −18 lines; data and config files: none;
  dependencies unchanged; test functions unchanged at 0.

## Beta Commit 214

- **The `WalletStore` placeholder names core's `ResidentState` as the state it
  will hold** (before: `CoreAppState`); comment only.
- Total: 1 .kt file changed, +1 −1 lines; data and config files: none;
  dependencies unchanged; test functions unchanged at 0.

## Beta Commit 175

- **The comment on the release-built binding generator stands on its own**
  (`scripts/bindgen-android.sh`): the build profile does not reach the generated
  bindings, so it only makes generation faster (before: it referred to
  `bindgen-ios.sh`).
- Total: no .kt files changed, +2 −2 lines; data and config files:
  `scripts/bindgen-android.sh`; dependencies unchanged; test functions unchanged
  at 0.

## Beta Commit 145

- **`bindgen-android.sh` runs the binding generator as a release build** (`cargo
  run --release`) against the debug library; the generated bindings are the
  same.
- Total: no .kt files changed, +3 −1 lines; data and config files:
  `scripts/bindgen-android.sh`; dependencies unchanged; test functions unchanged
  at 0.

## Beta Commit 43

- **`Kotlin/` renamed to `kotlin/`**; the output paths in both scripts follow.
- Total: 3 .kt files changed, +3 −3 lines; data and config files: `kotlin/` (5
  moved), `scripts/build-android.sh`, `scripts/bindgen-android.sh`; dependencies
  unchanged; test functions unchanged at 0.

## Beta Commit 37

- **Android app skeleton** (`Kotlin/`): a Compose `MainActivity` that shows
  "Spectra"; `dev.spectra.core.Bindings`, which loads `libspectra_core`, and an
  empty `WalletStore`, both placeholders for the UniFFI bindings. `compileSdk`
  and `targetSdk` 34, `minSdk` 26.
- **Scripts that build core for Android** (`scripts/`)
  - `build-android.sh` compiles the `ffi/` crate with `cargo-ndk` for arm64-v8a,
    armeabi-v7a and x86_64 and copies `libspectra_core.so` into `jniLibs/`;
    `--release` builds the release profile.
  - `bindgen-android.sh` builds the `ffi/` crate for the host and generates
    Kotlin UniFFI bindings into `Kotlin/app/src/main/kotlin/uniffi/`.
- Total: 3 .kt files changed (3 new), +172 −0 lines; data and config files:
  `Kotlin/build.gradle.kts`, `Kotlin/settings.gradle.kts`,
  `Kotlin/app/build.gradle.kts`, `Kotlin/app/src/main/AndroidManifest.xml`,
  `Kotlin/app/src/main/res/values/strings.xml`, `scripts/build-android.sh`,
  `scripts/bindgen-android.sh`; dependencies: added Android Gradle plugin 8.2.0,
  Kotlin Android 1.9.22, `activity-compose` 1.8.2, Compose `ui` 1.6.0,
  `material3` 1.3.0, JNA 5.14.0; test functions unchanged at 0.
