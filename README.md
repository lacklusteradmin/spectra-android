# Spectra for Android

Spectra is a prelaunch, multi-chain self-custodial crypto wallet focused on
custody, payments, recovery and staking. This repository is its Android app:
the iOS app's pages, flows and words, drawn with Jetpack Compose and
Material 3.

## Development

The Rust wallet core it runs on is
[spectra](https://github.com/lacklusteradmin/spectra), which the app links as
a package built there. Core owns domain state and decisions; the app renders
its results and forwards user intent. The iOS and macOS app is
[spectra-apple](https://github.com/lacklusteradmin/spectra-apple).

- [AGENTS.md](AGENTS.md): the rules for working in this repository, with
  spectra's `AGENTS.md`, Rule 0 first. Read first.
- [Architecture](docs/ARCHITECTURE.md): how the app's Kotlin is put together.
- [Android UI](docs/ANDROID-UI.md): how the iOS design maps to Material 3.
- [Kotlin bindings](docs/KOTLIN-BINDINGS.md): the core package the app links,
  and how to build it.
- [Open items](docs/OPEN-ITEMS.md): remaining app work. Core's are in
  spectra's `docs/OPEN-ITEMS.md`.
- [Scripts](scripts/README.md): the tools behind the Makefile.

### Building

It needs a JDK 21 newer than 21.0.1, the Android SDK with platform 37.2 and
its build tools, and, to build core, the NDK r28 or newer and the current
stable Rust. JDK 21.0.1's arm64 compiler crashes Gradle on the generated
bindings' largest methods.

Clone spectra anywhere and, in that checkout, build the core package:

```sh
make android
```

It compiles the Rust core for `arm64-v8a` and `x86_64` and writes
`target/SpectraCoreAndroid/`: the shared libraries and the Kotlin bindings
generated from them. Copy it to the root of this repository, replacing any
copy already here:

```sh
rm -rf SpectraCoreAndroid && cp -Rp <spectra>/target/SpectraCoreAndroid .
```

The package is **not** checked in; never hand-edit it. Gradle compiles no
Rust, so rebuild and copy the package again after changing core. A release
build needs `make android PROFILE=release`.
[Kotlin bindings](docs/KOTLIN-BINDINGS.md) has the rest.

Then `make build`, or open the repository in Android Studio and run the `app`
configuration. The app's minimum is Android 13 (API 33).

The secret store keeps secrets only on a device with a screen lock, so set a
PIN on an emulator before creating or importing a wallet:

```sh
adb shell locksettings set-pin 1111
```

### Verification

Three targets gate a change, each run by name; [AGENTS.md](AGENTS.md#verification)
says which a change needs:

| Target | What it runs |
|---|---|
| `make lint` | Android lint |
| `make test` | the JVM unit tests |
| `make test-device` | the instrumented tests, on the emulator or device adb sees |

## License

Spectra is free software under the [GNU General Public License v3.0](LICENSE).
It comes with no warranty.
