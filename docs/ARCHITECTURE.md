# Architecture

How the app's Kotlin is put together. What core owns and decides, and the
boundary every front end keeps to, is spectra's `docs/ARCHITECTURE.md`; how
the app links core is [KOTLIN-BINDINGS.md](KOTLIN-BINDINGS.md), and how a page
looks is [ANDROID-UI.md](ANDROID-UI.md). The app is spectra-apple's, page for
page: its `docs/ARCHITECTURE.md` describes the same design in Swift.

## Layout

`app/src/main/kotlin/` holds every source, by feature, as spectra-apple's
`app/` does. Every package is under `dev.spectra`, and the directories leave
that common root out, as Kotlin's coding conventions recommend:
`dev.spectra.send` is `send/`, and the unit and instrumented tests' source sets
do the same.

| Package | Holds |
| --- | --- |
| `app` | `AppState` and its adopters, the `WalletServiceBridge` and how a coroutine calls core (`CoreCalls.kt`), the Keystore secret store, navigation, device services, the activity and the root composable |
| `models` | Display helpers on core's records, `AmountPresentation`, `AppLocalization` and the catalogs read once from core |
| `components` | The theme, layout tokens and shared composables |
| `dashboard`, `wallets`, `wallets.tools`, `send`, `receive`, `history`, `settings`, `staking`, `diagnostics`, `wiki` | One feature each, its state beside its screens |

A file is named after its type, or after its topic for a file of extension
functions (`AppStateSendFlow.kt`).

## AppState and its domains

`AppState` is the app's store: the wallet, portfolio and transaction
projections, and the composition root for the domains. The application holds
one for the life of the process; every member is read and written on the
main thread, through `AppState.scope`. It composes one object per domain —
the send and receive flows, wallet import, the address book, token
preferences, price alerts, Tor, history paging, diagnostics and the
platform's own preferences — each holding that domain's projection or view
state and the actions that need nothing else. Screens read the domain
object; no forwarding copies of its fields live on `AppState`.

State a screen reads is Compose snapshot state (`mutableStateOf`), so a page
redraws when what it read changes. A projection of core state has one writer:
`applyCoreState`, `applyPortfolioSnapshot` and `refreshTransactionProjection`
adopt what core returned, and each domain's `adopt`. Its edits are commands
through the shared `StateCommandQueue`, which keeps the order the user acted
in. A projection's setter is `internal` only so those adopters can write it.

What needs the wallet projection or several domains — opening the send flow
on a wallet, the holding it sends — is an extension function on `AppState` in
the feature's package.

## Navigation

Each tab has its own back stack in the `Navigator`; `NavDisplay` draws the
top of the selected tab's. A route is a `Route`: the routes one feature opens
in another are `Routes`, so both agree on their shape, and a feature's own
routes live in its package. Each feature registers what it draws in its
`…Entries` function on `EntryProviderScope<Route>`, called by
`SpectraRoot`. A screen reads `LocalAppState` and `LocalNavigator`.

A flow — sending, receiving, adding a wallet — is pushed on the tab it started
from; the navigation bar shows only at a tab's root. One left open on another
tab is closed there first (`Navigator.close(tab, route)`), and work a page
started closes it only while it is still on top (`Navigator.dismiss`).

## Secrets

`KeystoreSecretStore` is core's `SecretStore` on Android. Core seals seeds and
private keys under its device key before they arrive and decides when that
key is minted, reused or refused. What is left here is the platform's:

- every stored value is sealed with an AES-GCM key in the Android Keystore
  and written under the app's no-backup directory, one file per key, named by
  the key's SHA-256;
- the device key is wrapped with a second Keystore key, in StrongBox where
  the device has one;
- both keys are usable only while the device is unlocked, ask for no user
  presence, and never leave the device;
- secrets are stored only on a device with a secure lock screen, as iOS keeps
  them only on a device with a passcode.

Nothing in the app is backed up or moved to another device
(`allowBackup="false"` and the data extraction rules).

## Device authentication

`AppState.authenticate` asks the device owner through `BiometricPrompt` —
strong biometrics or the screen lock — for the actions spectra-apple asks
for: unlock, a send while "Authenticate Sends" is on, deleting a wallet,
resetting data, and always for signing material leaving its seal.

## Keeping the shell thin

The rules are spectra-apple's: Kotlin that reads core-owned data only to send
it back for a decision is a misplaced rule; keep one writer per projection;
never infer identity from a ticker or a price provider's id; delete a cache
field when its last reader goes.
