# Scripts

The tools behind the Makefile, and the source scans run by hand.

| File | Purpose |
|---|---|
| `export-android-icons.sh` | Syncs core's crypto icon library from the spectra checkout `SPECTRA` names into `app/src/main/assets/crypto/`: copies a new or changed SVG, removes one whose source is gone, and refuses a source with no SVGs. `make icons SPECTRA=<spectra>` runs it. |
| `export-android-strings.sh` | Syncs the string tables both apps share from spectra's `strings/`, in the checkout `SPECTRA` names, into `app/src/main/assets/strings/`: copies a new or changed `RuntimeStrings` table, removes one whose source is gone, leaves the app's own `AndroidStrings` tables alone, and refuses a checkout with no tables. `make strings SPECTRA=<spectra>` runs it. |
| `missing-strings.py` | Finds the sentences the app looks up that a language lacks: every literal key passed to `AppLocalization.string`, `format` or `formatCount` must be in each of the three languages in `app/src/main/assets/strings/`, the shared `RuntimeStrings` table and the app's own `AndroidStrings` table read as one. Keys built at run time cannot be read. It also finds an `AndroidStrings` key the shared table has too, and one a language lacks. Prints each finding and exits non-zero when it found any. No Makefile target runs it: `python3 scripts/missing-strings.py`. |
