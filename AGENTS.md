## Git

Do not git commit or push unless I said so. Before a commit, tidy the
CHANGELOG as [CHANGELOG](#changelog) says.

## Repositories

This repository is the Android app. The Rust core and the CLI are the
`spectra` repository, and the iOS and macOS app is `spectra-apple`. spectra's
`AGENTS.md` applies here too: read it before a change that touches core or
what the app shows of it. Rule 0, the architecture rules and Funds and keys
there apply to everything here.

- The app links `SpectraCoreAndroid/`, a package spectra's `make android`
  builds into its `target/SpectraCoreAndroid/`. Copy that directory here, and
  Gradle compiles no Rust.
- Nothing here assumes where a spectra checkout is. What reads core's source
  takes it as `SPECTRA`: the icon export, `make icons SPECTRA=<path>`.
- Nothing here records which core commit the package came from. Land a core
  change in spectra before the app change that needs it.
- The app is spectra-apple's, page for page, with the same words. A change to
  what a page shows or does is made in both apps, or the difference is
  recorded in [OPEN-ITEMS.md](docs/OPEN-ITEMS.md).

## Docs

Read the one that covers what you are changing:

| Doc | Covers |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | How the app's Kotlin is put together: `AppState` and its domains, navigation, secrets |
| [ANDROID-UI.md](docs/ANDROID-UI.md) | How the iOS design maps to Material 3: surfaces, colour, spacing, radii |
| [KOTLIN-BINDINGS.md](docs/KOTLIN-BINDINGS.md) | The core package the app links, and its Kotlin bindings |
| [OPEN-ITEMS.md](docs/OPEN-ITEMS.md) | Remaining app work |
| [CHANGELOG.md](docs/CHANGELOG.md) | What each commit changed, and why |
| [scripts/README.md](scripts/README.md) | The icon export |
| spectra's `docs/` | Core's architecture, chain support, FFI boundary and open items |
| spectra-apple's `docs/IOS-UI.md` | What each page holds, which this app draws in Material 3 |

Keep them true:

- A change that alters what a doc says updates that doc in the same change.
- A doc states what is true now, and names a file or symbol only while it
  exists. History belongs in `CHANGELOG.md` and git.
- `OPEN-ITEMS.md` holds remaining work only. Delete an item when it is done.

## CHANGELOG

[CHANGELOG.md](docs/CHANGELOG.md) records what each commit changed and why,
newest first. Record a change under `## Uncommitted` as it is made. A section
written before the split covers only this repository's part of that commit;
spectra keeps its own for core.

**Tidy it before a commit.** When I am about to commit, or ask for the
CHANGELOG to be tidied, check `## Uncommitted` against the actual change
(`git diff`). Every sub-item matches one change in the diff: delete one that
does not. Add what the diff has and the entries lack. Numbers match the code,
and no claim goes beyond what was done. Then rename the heading to the
commit's title, such as `## Beta Commit 252`.

How to write it: as spectra's `AGENTS.md` "CHANGELOG" says, with one
difference: `Total:` counts .kt files, and tests passing in `make test` and
`make test-device` before → after.

## Verification

Match verification to the scope and risk of the change, and report which checks
actually ran.

| Change | Run |
|---|---|
| Docs, copy, resources | Only what the edit can affect |
| Compose pages and components | `make build`, and the page on the emulator |
| A localized code change | The target that covers it |
| Broad refactors, FFI changes, the secret store, or a core change the app depends on | All three targets, and core's own for what changed in the core checkout, unless I say otherwise |

The three targets are `make lint`, `test` and `test-device`: Android lint,
the JVM unit tests, and the instrumented tests on a device. No target runs
them all: each is run by name. The Makefile holds the exact commands.

Use one emulator only, `Spectra_Pixel_9` (Pixel 9, Android 17, arm64), for
`make test-device`, installs, driving and screenshots, and set a screen lock
on it (`adb shell locksettings set-pin 1111`): the secret store refuses a
device without one. Ask before creating or booting a second device. The
emulator may hold my test wallet, so back up its data before an experiment
that touches it.

- After the emulator boots, unlock it once (`adb shell input text 1111` then
  `adb shell input keyevent 66`): until the first unlock after a boot, with a
  screen lock set, the app's activity does not resolve and `am start` reports
  it missing.
- `make test-device` uninstalls the app when it finishes, which deletes its
  wallets: install it again with `make install`.

No test is expected to fail. Treat a failure as real even when it looks
unrelated to the change, and report it; do not write it off as flaky or
pre-existing without evidence.

## Platform constraints

- Check APIs and generated bindings against **UniFFI 0.31.2** before changing
  FFI code. See [KOTLIN-BINDINGS.md](docs/KOTLIN-BINDINGS.md) and spectra's
  `docs/FFI-BOUNDARY.md`.
- Never hand-edit `SpectraCoreAndroid/`. Change the Rust API in the core
  checkout and rebuild and copy the package.
- The app's `minSdk` is **33** and its `targetSdk` **37**. Write for that floor
  with the current idiom: no `Build.VERSION` checks or fallbacks for older
  releases, Compose rather than views, coroutines rather than callbacks or
  threads, Navigation 3 back stacks, Material 3 components. Raising `minSdk`
  means rebuilding core with spectra's `make android ANDROID_MIN_SDK=<level>`.
- [docs/ANDROID-UI.md](docs/ANDROID-UI.md) is the authority for colour,
  typography, spacing and radii.

## Kotlin conventions

- `app/src/main/kotlin/dev/spectra/` holds every source, by feature: `app`
  (`AppState`, its adopters, the bridge, the secret store, navigation and
  device services), `models` (display helpers on core's records and the
  catalogs read from core), `components` (the theme and shared composables),
  then one package per feature, its state beside its screens.
- State a screen reads is Compose snapshot state. `AppState` and the domain
  objects are read and written on the main thread; a blocking core call runs
  in `withContext(Dispatchers.IO)`.
- A projection of core state has one writer. Edits are state commands through
  the shared `StateCommandQueue`.
- Pure transformations belong in core or free functions; `AppState`
  extensions are thin adapters.
- Name a file after its type, or after its topic for a file of extension
  functions (`AppStateSendFlow.kt`).

## Strings

Every word on screen comes from `AppLocalization`, keyed by the same English
sentence or dotted name spectra-apple uses. The tables in
`app/src/main/assets/strings/` are spectra-apple's `RuntimeStrings` tables,
with its format specifiers (`%@`, `%1$@`, `%lld`), which `AppLocalization`
reads as Java's. A sentence the iOS app also shows takes its key and words
from there; an Android-only sentence is added to all three tables, `en`,
`zh-Hans` and `zh-Hant`.

## Icons

`app/src/main/assets/crypto/` is core's crypto icon library, synced by
`scripts/export-android-icons.sh` from spectra's `icons/crypto/`: never edit
it by hand. The launcher icon is `res/drawable/ic_launcher_foreground.xml`,
the five bars of spectra-apple's `icons/appicon/`; change them together.
