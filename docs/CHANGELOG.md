# Changelog

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
