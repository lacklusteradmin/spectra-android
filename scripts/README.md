# Scripts

The tools behind the Makefile.

| File | Purpose |
|---|---|
| `export-android-icons.sh` | Syncs core's crypto icon library from the spectra checkout `SPECTRA` names into `app/src/main/assets/crypto/`: copies a new or changed SVG, removes one whose source is gone, and refuses a source with no SVGs. `make icons SPECTRA=<spectra>` runs it. |
