# Scripts

The tools behind the Makefile, and the source scans run by hand.

| File | Purpose |
|---|---|
| `export-android-icons.sh` | Syncs core's crypto icon library from the spectra checkout `SPECTRA` names into `app/src/main/assets/crypto/`: copies a new or changed SVG, removes one whose source is gone, and refuses a source with no SVGs. `make icons SPECTRA=<spectra>` runs it. |
| `missing-strings.py` | Finds the sentences the app looks up that a string table lacks: every literal key passed to `AppLocalization.string`, `format` or `formatCount` must be in each of the three tables in `app/src/main/assets/strings/`. Keys built at run time cannot be read. Prints each missing key with the tables that lack it and exits non-zero when it found any. No Makefile target runs it: `python3 scripts/missing-strings.py`. |
