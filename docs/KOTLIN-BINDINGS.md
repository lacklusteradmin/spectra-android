# Kotlin bindings

How the app links the Rust core and the Kotlin bindings to it, with **UniFFI
0.31.2**. What core exports, and the rules an export follows, are core's:
spectra's `docs/FFI-BOUNDARY.md`.

## The core package

The app links core as `SpectraCoreAndroid/`, a directory at this repository's
root that is not checked in. Gradle compiles no Rust. spectra's `make android`
builds the package into its `target/SpectraCoreAndroid/`, with its
`scripts/build-android-package.sh`, and a developer copies that directory
here. It holds:

- `jniLibs/<abi>/libspectra_core.so`: core as a shared library for
  `arm64-v8a` and `x86_64`, linked for the app's `minSdk` with 16 KB pages;
- `kotlin/uniffi/spectra_core/spectra_core.kt`: the Kotlin bindings generated
  from that same library, in the package `uniffi.spectra_core`;
- `build-inputs`: the profile, ABIs and minimum API level it was built with.

`app/build.gradle.kts` adds both directories to the app's sources. The
bindings call core through JNA, whose AAR carries its own native part. A
Kotlin file that uses core imports from `uniffi.spectra_core`.

Nothing here assumes where a spectra checkout is, and nothing records which
core commit the package came from. In spectra, `make android` takes:

| Variable | Default | Use |
|---|---|---|
| `PROFILE` | `dev` | `release` for a build that ships; a release build refuses a dev package |
| `ABIS` | `arm64-v8a x86_64` | Fewer build faster; an emulator on Apple silicon is `arm64-v8a` |
| `ANDROID_MIN_SDK` | `33` | This app's `minSdk`, which every library is linked for |

It finds the NDK through `ANDROID_NDK_HOME`, or the newest one under
`ANDROID_HOME`'s `ndk/` (`~/Library/Android/sdk` on a Mac), and refuses one
older than r28.

## Changing the Rust API

Change the Rust API in spectra, run `make android` there, then copy the
package here, replacing the old one:

```sh
rm -rf SpectraCoreAndroid && cp -Rp <spectra>/target/SpectraCoreAndroid .
```

Then build: `make build`, or in Android Studio. Never edit
`SpectraCoreAndroid/` by hand: the generator patches nothing, so a Kotlin
problem in the bindings is a UniFFI version or API-shape problem to fix in
core.

## Kotlin shapes

| Rust | Kotlin |
|---|---|
| `uniffi::Record` | `data class` with `var` fields |
| A fieldless `uniffi::Enum` | `enum class` with SCREAMING_CASE constants |
| A `uniffi::Enum` with fields | `sealed class` of `data class`es and `object`s |
| `uniffi::Error` | `sealed class …Exception` of exception classes; core's carry `reason` |
| `uniffi::Object` | A class to `close()` or leave to the cleaner |
| An async export | A `suspend fun`, run on core's Tokio runtime |
| `u32`, `u64` | `UInt`, `ULong` |
| `Vec<u8>` | `ByteArray` |
| A `with_foreign` trait | A Kotlin `interface` the app implements: `SecretStore` (`KeystoreSecretStore`), `RefreshObserver` (`WalletRefreshObserver`) |

## Common errors

| Symptom | Check |
|---|---|
| `SpectraCoreAndroid/ is missing` | Run `make android` in spectra and copy `target/SpectraCoreAndroid/` here |
| `a release build needs the release core package` | Run `make android PROFILE=release` in spectra and copy the package |
| `Unresolved reference` to a core type | Rebuild and copy the package; confirm the declaration is exported |
| `UnsatisfiedLinkError: libspectra_core.so` | The package lacks the device's ABI: build it with that ABI in `ABIS` |
| Gradle's daemon disappears, `hs_err_pid…log` says `Field too big for insn` | JDK 21.0.1's arm64 compiler on the bindings' largest methods: run Gradle on a newer JDK 21 |
