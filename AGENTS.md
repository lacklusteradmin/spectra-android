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
  takes it as `SPECTRA`: the icon export, `make icons SPECTRA=<path>`, and the
  string sync, `make strings SPECTRA=<path>`.
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
| [scripts/README.md](scripts/README.md) | The icon export, the string sync and the string scan |
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
does not, which is usually an unchanged part, the whole picture after the
change, or process. Add what the diff has and the entries lack. Numbers match
the code, and no claim goes beyond what was done. Then rename the heading to
the commit's title, such as `## Beta Commit 252`.

How to write it:

- **Voice.** An objective record. Do not address the reader, and write no
  asides. A decision for me goes in the conversation or in `OPEN-ITEMS.md`.
- **The result, not the process.** Write what the changed part is now, with
  the before or the reason in one parenthesised sentence when it helps. No
  process ("first … then", "found … so", "tried …", debugging), and no whole
  picture after the change: the docs hold that, so point there in a few words.
- **One sub-item per change.** Each change is a bullet whose bold headline
  states the result, with where in parentheses (screens, files, classes).
  Under it, one sub-item per change, in one or two sentences. A change is one
  thing `git diff` shows: a behaviour, a screen or control, a number or
  default, a file that moved, whether stored data still loads. Many changes,
  many sub-items; a change with only one such thing is the headline alone,
  with no sub-items.
- **No padding.** Leave out unchanged parts, unless a reader would think they
  changed; per-item visual detail, which `ANDROID-UI.md` and the code hold;
  lists of functions and call sites; mechanical edits such as an added
  property, a rename or a rebuilt `SpectraCoreAndroid/`; anything that repeats
  the headline. A refactor that keeps behaviour is one line naming what moved
  where; how that was shown goes under `Measured:`.
- **Names as they were.** A section names files and symbols as they were at
  that commit, even after they move or go.
- **Measurements.** The conclusion only, with its source (a test, a run on a
  named device or emulator).
- **Each section ends with these, in order, when they apply:**
  - `Measured:` one line per run: date, device or emulator, result, key
    numbers only, before → after.
  - `Tests: N new`: one line per area, "area N: what it guards against". Not
    what each test checks. An existing test that was only updated is left out
    unless what it guards changed.
  - `Docs:` which doc, and which sections.
  - `Total:` always, last: N .kt files changed (M new), +X −Y lines; data,
    asset and config files by name or grouped by folder, or none;
    dependencies unchanged, or what was added or removed; tests passing in
    `make test` and `make test-device` before → after, or which was not run.
    "No code changed" when the change is docs alone.

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

- `app/src/main/kotlin/` holds every source, by feature, one directory per
  package with the common root `dev.spectra` left out: `app` (`AppState`,
  its adopters, the bridge, the secret store, navigation and device
  services), `models` (display helpers on core's records and the catalogs
  read from core), `components` (the theme and shared composables), then one
  package per feature, its state beside its screens.
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
sentence or dotted name spectra-apple uses, with Apple's format specifiers
(`%@`, `%1$@`, `%lld`), which `AppLocalization` reads as Java's. Each language
in `app/src/main/assets/strings/` is two tables read as one:

- `RuntimeStrings.<locale>.json`, the tables both apps share, kept in
  spectra's `strings/` with their rules in spectra's `AGENTS.md`. Never edit
  the copy here: a sentence spectra-apple also shows is added or changed
  there, then synced with `make strings SPECTRA=<spectra checkout>`.
- `AndroidStrings.<locale>.json`, the sentences only this app shows, edited
  here. A key in it must not be in the shared tables.

A sentence goes in all three languages, `en`, `zh-Hans` and `zh-Hant`;
`scripts/missing-strings.py` finds one a language lacks.

## Icons

`app/src/main/assets/crypto/` is core's crypto icon library, synced by
`scripts/export-android-icons.sh` from spectra's `icons/crypto/`: never edit
it by hand. The launcher icon is `res/drawable/ic_launcher_foreground.xml`,
the five bars of spectra-apple's `icons/appicon/`; change them together.
