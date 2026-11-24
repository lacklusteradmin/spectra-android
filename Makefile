SHELL := /bin/bash

# The app links SpectraCoreAndroid/, the package spectra's `make android`
# builds, copied here by hand. Nothing here builds core or assumes where its
# checkout is: what reads core's icon library takes SPECTRA, a spectra
# checkout, absolute or relative to this repository's root.
export SPECTRA

GRADLE := ./gradlew --console=plain

.PHONY: lint test test-device build release install icons

# ── Verification ────────────────────────────────────────────────────
# Three targets, each run by name: lint, test and test-device. AGENTS.md
# says which a change needs. test-device needs one emulator or device that
# adb sees, with a screen lock set: the secret store keeps secrets only on a
# device with one.

lint:
	$(GRADLE) :app:lintDebug

test:
	$(GRADLE) :app:testDebugUnitTest

test-device:
	$(GRADLE) :app:connectedDebugAndroidTest

# ── Builds ──────────────────────────────────────────────────────────
build:
	$(GRADLE) :app:assembleDebug

# A release build refuses a dev core package: copy one built with
# `make android PROFILE=release`.
release:
	$(GRADLE) :app:assembleRelease

install:
	$(GRADLE) :app:installDebug

# ── Assets ──────────────────────────────────────────────────────────
icons:
	@test -n "$(SPECTRA)" || { echo "error: the icons are core's: make icons SPECTRA=<spectra checkout>" >&2; exit 2; }
	scripts/export-android-icons.sh
