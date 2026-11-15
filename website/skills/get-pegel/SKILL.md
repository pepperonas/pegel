---
name: get-pegel
description: Download, verify and install the newest Pegel APK — the free, open-source system equalizer for Android with ten bands, bass, loudness, virtualizer and a profile per output device. Use when someone asks for the app, its latest version, a download link, or how to check the APK is genuine.
license: MIT
---

# Get Pegel

Pegel is a free, open-source (MIT) system equalizer for Android 9 and later. It offers a ten-band equalizer (31 Hz to 16 kHz, ±12 dB in 0.5 dB steps) with a live response curve, 13 presets and your own presets, plus bass boost, loudness, a virtualizer, optional reverb and left/right balance. The whole sound can be saved as a profile, and each output — speaker, wired headphones, USB audio, each Bluetooth device — can have its own profile that Pegel applies when that output becomes active. Pegel does not process audio itself: it configures Android's own effects (DynamicsProcessing, Virtualizer, PresetReverb) on the global mix and on the audio sessions that music players announce, so it works without root and within the limits Android sets — apps that neither announce their session nor play through the global mix are not reached. Profiles, presets and settings can be backed up to one JSON file, and single profiles shared as .pegel files. There are no ads, no tracking and no account; the only network access is the update check, which can be switched off, and the checksum-verified update the app installs itself. Distributed as a signed APK through GitHub Releases.

## 1. Find the newest release

`GET https://pegel.celox.io/latest.json` returns `version`, `published`, `notes` and `assets[]`, each with `target`,
`name`, `url`, `size` (bytes) and `sha256`. It is refreshed from GitHub Releases every 15 minutes. On the
page itself, browsers with WebMCP expose the same data as the tools `get_latest_release`,
`get_download_url` and `get_checksums`.

## 2. Download

- Stable link, always the newest file for the visitor's platform: <https://pegel.celox.io/download>
- APK: <https://pegel.celox.io/download/android> — Android 9+

## 3. Verify

- The file's SHA-256 must equal the matching `assets[].sha256` in `latest.json`.
- Signing certificate SHA-256: `aa27ac4c8a8580905c0963cc362b943409fca43618dd1fabc0daf876ced8e282`
- Every release is signed with the same key. Compare with `apksigner verify --print-certs`.

## 4. Install

1. One file for every phone with Android 9 or later — there is nothing to pick.
2. Open the file. Android asks once whether your browser may install apps — allow it.
3. The switch is at the top right. If a player does not react, look for “system equalizer” in its audio settings — the in-app help lists what to try.

## Limits

- Android 9 or later only; not on Google Play — distributed as an APK through GitHub Releases.
- No root: Pegel reaches only audio that plays through the global mix or through a session a player announces. Apps with their own audio engine or copy protection may not be affected.
- Whether effects on the global mix are allowed depends on the device.
- Reverb works only on a player's own session, not on the global mix.
- Over Bluetooth, some phones need “Disable Bluetooth A2DP hardware offload” in the developer options before effects are audible.
- Two equalizers at once (including the one built into the phone) fight over the same audio session — use one.
- The interface is available in English and German only.
- It is not a parametric equalizer: the ten band frequencies are fixed.

More: [product page](https://pegel.celox.io/) · [Markdown version](https://pegel.celox.io/index.md) · [changelog](https://pegel.celox.io/changelog.md) · [source](https://github.com/pepperonas/pegel)
