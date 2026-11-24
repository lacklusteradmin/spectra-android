# Open items

Remaining app work. Core's is spectra's `docs/OPEN-ITEMS.md`.

## Parity with spectra-apple

- **Sharing a file.** A receive request's QR image and a diagnostics bundle
  are shared as text: sharing the file itself needs a `FileProvider` in the
  manifest and its paths in `res/xml`. A very large bundle can exceed what a
  text share carries, and then the export reports the failure.
- **A send's progress outside the app.** iOS follows a send in a Live
  Activity; here only the notification core asks for when it confirms or
  fails. An ongoing notification for a pending send is the Android
  counterpart, if wanted.
- **The string tables are copies.** `app/src/main/assets/strings/` holds
  spectra-apple's `RuntimeStrings` tables plus Android-only sentences. A
  change to the iOS tables reaches Android only by copying them again;
  giving both apps one set of tables (in spectra, as the icon library is) is a
  decision to make.

## Tests and checks

- **Tests.** The instrumented tests cover the secret store, core loading
  through the bridge, and the JVM tests the string format conversion.
  spectra-apple's suites for the send session, wallet import, history paging,
  staking state and presentation have no Kotlin counterparts yet.
- **Source scans.** `scripts/missing-strings.py` finds a looked-up sentence a
  table lacks. spectra-apple's other scans — unreachable exports, chain names
  and precision spelled in app code, design tokens — have no Android
  counterparts, and none of the scans runs in a Makefile target yet.
- **Devices.** The app has run on the arm64 emulator only. StrongBox, a
  physical device's biometrics and the camera scanner are untested on
  hardware.

## Release

- **Signing and distribution.** There is no release signing configuration,
  and `make release` has not been run against a release core package.
