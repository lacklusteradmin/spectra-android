# Spectra for Android

Not started. This repository keeps the history of an earlier attempt, and its
last state as a starting point: a Compose `MainActivity` stub and its Gradle
setup in `kotlin/`, and two scripts that built core for Android with
`cargo-ndk` and generated Kotlin bindings.

None of it builds today. The scripts compile an `ffi/` crate that core no
longer has: core now builds as the library itself, as
[spectra](https://github.com/lacklusteradmin/spectra)'s
`scripts/build-apple-package.sh` does for Apple. Android work starts by
building core from a spectra checkout as a `cdylib` for each Android ABI, as
its `make apple` builds a package for spectra-apple, and generating the
bindings with its `tools/uniffi-bindgen` and `--language kotlin`.

## License

Spectra is free software under the [GNU General Public License v3.0](LICENSE).
