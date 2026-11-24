# Android UI reference

Spectra for Android is the iOS app's screens, flows and words in Material 3,
built with Jetpack Compose. The two apps show the same pages in the same
order with the same copy; each draws them in its platform's own style.
spectra-apple's `docs/IOS-UI.md` is the authority for what a page holds; this
document is the authority for how Android draws it.

## Mapping from iOS

| iOS | Android |
| --- | --- |
| `TabView` with Home, History, Settings | `NavigationBar` with the same three tabs, each with its own back stack (`Navigator`) |
| `NavigationStack` and `navigationDestination` | A `Route` pushed on the tab's stack and drawn by `NavDisplay` |
| `.hidesTabBar()` in a flow | The navigation bar shows only at a tab's root |
| Large title on a tab | `SpectraPage(large = true)`: a `LargeTopAppBar` that collapses as the page scrolls |
| Inline title on a detail | `SpectraPage`: a `TopAppBar` with Back |
| Liquid Glass card | `SpectraCard`: a `surfaceContainerLow` surface, or `surfaceContainerHigh` when `elevated` |
| `spectraInsetFill` inside a card | `Modifier.spectraInsetFill()`: `surfaceContainerHighest` |
| `SpectraRowGroup`, `SpectraRowSection` | The same names, in `SpectraCards.kt` |
| `.buttonStyle(.glassProminent)` | `Button` (filled) |
| `.buttonStyle(.glass)` | `FilledTonalButton`, or `OutlinedButton` beside a filled one |
| Toolbar button | `IconButton` in the top app bar's `actions` |
| `safeAreaBar(edge: .bottom)` with `SpectraBottomActionBar` | `SpectraPage(bottomBar = { SpectraBottomActionBar { … } })` |
| `.pageSheet` | A pushed route for a flow or a form; `ModalBottomSheet` for a short choice |
| `confirmationDialog`, `alert` | `AlertDialog` |
| `Menu` | `DropdownMenu` from an `IconButton` |
| `Picker(.segmented)` | `SingleChoiceSegmentedButtonRow` |
| `Toggle` | `SpectraSwitchRow`, or `Switch` |
| `Stepper` | `SpectraStepper` |
| `Label` | `SpectraLabel`: the symbol by the text's first line |
| `DisclosureGroup` | `SpectraDisclosure` |
| `.pageRefreshable` | `SpectraPage(onRefresh = …)`: pull to refresh |
| `.searchable` | `SpectraSearchField` at the top of the page's content |
| `AllChainsSelectionView` | `AllChainsSelectionPage`: pushed, or drawn in place of a form that keeps what was typed |
| `SecureField` for a wallet password | `WalletPasswordField` |
| `Link`, `openURL` | `openExternalUrl` |
| `ShareLink`, the share sheet | `shareText` |
| `SpectraTransientNotice` | `rememberNotice()`: a snackbar |
| `ContentUnavailableView` | `SpectraEmptyStateCard` |
| SF Symbol | A Material icon from `Icons.Outlined`, `Icons.Filled` for a selected tab |
| `.secretShield()` | `SecureWindow()`: the window is kept out of screenshots, recordings and recents |
| `PasteButton`, QR scanner | `AddressEntryRow`'s paste and scan buttons; `QRScannerDialog` |
| Haptics | None beyond what Material components give |

## Design baseline

- **Theme colour:** one accent, orange, as Material 3's `primary`, in
  `SpectraTheme.kt`. Buttons, switches, selection, links and decorative icons
  read it on their own. A view writes `MaterialTheme.colorScheme.primary`,
  never a literal orange. There is no dynamic colour: the accent is the
  app's, not the wallpaper's.
- **Semantic colours** do not follow the theme: `SpectraColors.semantic`
  `success` for confirmed and received, `warning` for pending, incomplete and
  warnings, `failure` for failures, sends and destructive actions.
- **Surfaces:** a page's content sits on `SpectraCard`s over the theme's
  background. Nothing inside a card is a card: an input, an address block, a
  chip or a tile takes `spectraInsetFill`. Two steps only: content cards, and
  `elevated` hero and header cards. A notice in its own colour is a
  `SpectraNoticeCard`.
- **Lists:** a list is rows in one card — `SpectraRowGroup`, each row padded
  with `Modifier.spectraRowPadding()` inside its own `clickable` — never a card
  per row. Dividers are `SpectraDivider`, inset under the text of a row that
  leads with a 36dp badge. A selectable list marks the choice with a trailing
  check. A list of hundreds of rows — every chain, the known tokens, the
  log — is a `SpectraLazyPage` of `pageItem`s and `lazyRowGroup`s, so only
  the rows on screen are composed.
- **Empty and loading states:** `SpectraEmptyStateCard` between cards,
  `SpectraEmptyStateContent` inside one, `SpectraLoadingRow` while a list loads.
- **Flows:** a flow's primary action is a `SpectraBottomActionBar` in the
  page's `bottomBar`; the content scrolls above it and it rises with the
  keyboard. Back in a multi-step flow is a step back, never the whole flow.
- **Secrets:** a recovery phrase or a private key on screen composes
  `SecureWindow()`. A seed word is typed in a `SecretWordField`, and a
  wallet's password in a `WalletPasswordField` on an unsaved `TextFieldState`
  (`remember { TextFieldState() }`), never written to the saved instance
  state. A copied secret goes through `copySecretToClipboard`: marked
  sensitive and cleared after a minute.
- **Balances:** every amount and value a person can hide goes through
  `BalanceText` or `SpectraValueRow(isHidden = …)`. Prices, a network's own
  figures, an amount being typed and one a review asks a person to sign are
  never masked.
- **Addresses:** a recipient field is an `AddressEntryRow` — typed, pasted,
  scanned (read by core against the network) or picked from the contacts.
  Where core names who holds an address, an `EndpointHolderLabel` says so
  beside it.
- **Hashes and payloads:** shown with `breakableAnywhere`, or as a
  `SpectraValueRow` with `SpectraValueStyle.Identifier`, and copied whole with
  `CopyButton` or `CopyRow`.
- **Dates and numbers:** formatted in `AppLocalization.locale` with
  `formatAppDate`, `formatUnixDate`, `formatPercent` and `AmountPresentation`,
  never the system locale.
- **Typography:** Material's type scale. `.largeTitle` is the large app bar,
  `.title` `headlineSmall`, `.headline` `titleMedium`, `.body` `bodyLarge`,
  `.subheadline` `bodyMedium`, `.footnote` and `.caption` `bodySmall`.
- **Text colours:** `onSurface` for primary text and `secondaryText`
  (`onSurfaceVariant`) for secondary.
- **Words:** every string a person reads comes from `AppLocalization`, keyed by
  the same English sentence or dotted name the iOS app uses, so both apps
  share one set of tables.
- **Logo:** `SpectraMark` and `SpectraLogo` draw the app icon's five bars; the
  launcher icon is the same bars on black.

## Spacing and radii

The steps are spectra-apple's, in dp: `SpectraLayout.Space` `xxs` 2, `xs` 4,
`s` 8, `m` 12, `l` 16, `xl` 24; `screenHorizontal` 16, `screenTop` 8,
`screenBottom` 16, `sectionSpacing` 12, `cardPadding` 16, `rowVertical` 8.
Rows are at least 48dp tall, Material's touch target. Every scrolling page
insets its content with `SpectraScreenPadding` and stops at
`SpectraLayout.pageMaxWidth`, which `SpectraPage` does for it.

Radii: `Radius.card` 20dp for every card, `Radius.inner` 14dp for a surface
inside one, `Radius.control` 10dp for dense controls, `Radius.sheet` 28dp for
sheets and dialogs. Material's shape scale is set to these: `large` is a
card, `medium` an inner surface, `small` a control.

## What Android does differently

- **Back:** the system back gesture pops the tab's stack; at a tab's root it
  leaves the app.
- **Recents:** the recents screen never shows the app's content
  (`setRecentsScreenshotEnabled(false)`), as the iOS app switcher shows its
  logo cover.
- **Lock:** the lock and the unreadable-store cover are a full-screen dialog
  above everything the app shows, as iOS covers the app with a window.
- **Device authentication:** `BiometricPrompt` with strong biometrics or the
  screen lock, where iOS asks for Face ID, Touch ID or the passcode.
- **Notifications:** a send is followed by a notification; there is no Live
  Activity.
