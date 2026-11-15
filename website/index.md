<!--# block name="none" --><!--# endblock -->
# Pegel — Free Open-Source Equalizer for Android

> Pegel is a free, open-source (MIT) system equalizer for Android 9 and later. It offers a ten-band equalizer (31 Hz to 16 kHz, ±12 dB in 0.5 dB steps) with a live response curve, 22 presets and your own presets, plus bass boost, loudness, a virtualizer, optional reverb and left/right balance. The whole sound can be saved as a profile, and each output — speaker, wired headphones, USB audio, each Bluetooth device — can have its own profile that Pegel applies when that output becomes active. Pegel does not process audio itself: it configures Android's own effects (DynamicsProcessing, Virtualizer, PresetReverb) on the global mix and on the audio sessions that music players announce, so it works without root and within the limits Android sets — apps that neither announce their session nor play through the global mix are not reached. Profiles, presets and settings can be backed up to one JSON file, and single profiles shared as .pegel files. There are no ads, no tracking and no account; the only network access is the update check, which can be switched off, and the checksum-verified update the app installs itself. Distributed as a signed APK through GitHub Releases.

This is the Markdown version of https://pegel.celox.io/ for agents and text tools. A short summary with every link lives at https://pegel.celox.io/llms.txt.

## Download

- **Newest release:** https://pegel.celox.io/download (picks the file for your platform; always the current release)
- **Current version:** <!--# include virtual="/ssi/version.txt" stub="none" --> · released <!--# include virtual="/ssi/date.txt" stub="none" -->
- **Release data as JSON:** https://pegel.celox.io/latest.json
- **Requirements:** APK: Android 9+

Files in the current release:

<!--# include virtual="/ssi/files.md" stub="none" -->

## Features

- **Ten bands, half-decibel steps** — From 31 Hz to 16 kHz, ±12 dB each, with a curve above the sliders that shows what you are doing. 22 presets to start from, and your own next to them.
- **Bass, loudness, virtualizer** — Each with one strength slider. Loudness pushes its gain into a limiter, so a boost gets denser instead of clipping. Reverb and balance are there when you want them.
- **Profiles that follow your headphones** — Save the whole sound under a name and give each output its own: speaker, wired, USB, every Bluetooth device. Plug in or connect, and Pegel loads the right one.
- **Honest about what it reaches** — Without root, an equalizer reaches the global mix and the players that announce their audio. Pegel uses both, and one line at the top always says what it is attached to right now.
- **Backup and sharing** — All profiles, presets and settings in one JSON file — and back. A single profile travels as a `.pegel` file to anyone who has the app.
- **Nothing but sound** — No ads, no analytics, no account, no paid version. The app goes online for one thing only — its own, checksum-verified updates — and even that can be switched off.

### In numbers

Counted from the source code, updated with every change:

- **Lines of Kotlin:** <!--# include virtual="/ssi/stat-loc_main.txt" stub="none" --> (<!--# include virtual="/ssi/stat-loc_main-detail.txt" stub="none" -->)
- **Unit tests:** <!--# include virtual="/ssi/stat-tests.txt" stub="none" --> (<!--# include virtual="/ssi/stat-tests-detail.txt" stub="none" -->)

## Install

1. **Download the APK** — One file for every phone with Android 9 or later — there is nothing to pick.
2. **Allow the install** — Open the file. Android asks once whether your browser may install apps — allow it.
3. **Switch Pegel on** — The switch is at the top right. If a player does not react, look for “system equalizer” in its audio settings — the in-app help lists what to try.

## Verify

<!--# include virtual="/ssi/checksums.md" stub="none" -->
['- **Lines of Kotlin:** <!--# include virtual="/ssi/stat-loc_main.txt" stub="none" --> (<!--# include virtual="/ssi/stat-loc_main-detail.txt" stub="none" -->)', '- **Unit tests:** <!--# include virtual="/ssi/stat-tests.txt" stub="none" --> (<!--# include virtual="/ssi/stat-tests-detail.txt" stub="none" -->)']
- Every release is signed with the same key. Compare with `apksigner verify --print-certs`.

## FAQ

**Is Pegel free?** Yes. It is free and open source under the MIT licence — no ads, no account, no tracking, and no paid version with more features.

**Does it work with every app?** No equalizer without root does. Pegel reaches what plays through the phone's global mix and every player that announces its audio session; some players need “system equalizer” switched on in their settings. Apps with their own audio engine or copy protection may stay unaffected.

**Does anything leave my phone?** No. Pegel does not record or analyse audio — it only configures effects that Android runs. The only network access is the update check: it reads the newest version number from this page, without any identifier. Switch it off in Settings and the app makes no request at all.

**Which phones does it run on?** Android 9 or later — the audio engine Pegel builds on does not exist before that. On phones with aggressive battery savers, exempt the app from battery optimization; Settings offers it.

**How do I update?** Inside the app. When a new version is out, a card appears on the first screen: *Update* downloads the file, checks its SHA-256 and hands it to Android to install. Your profiles and settings stay. You can also download the APK here and install it over the app.

**Is the file genuine?** Compare the SHA-256 checksum and the signing certificate shown on this page. Android also refuses to install an update signed with a different key over the app.

## Limits

- Android 9 or later only; not on Google Play — distributed as an APK through GitHub Releases.
- Only headphones have measured corrections. Suggested curves for speakers, cars and the phone speaker are starting points by kind of device, not measurements.
- No root: Pegel reaches only audio that plays through the global mix or through a session a player announces. Apps with their own audio engine or copy protection may not be affected.
- Whether effects on the global mix are allowed depends on the device.
- Reverb works only on a player's own session, not on the global mix.
- Over Bluetooth, some phones need “Disable Bluetooth A2DP hardware offload” in the developer options before effects are audible.
- Two equalizers at once (including the one built into the phone) fight over the same audio session — use one.
- The interface is available in English and German only.
- It is not a parametric equalizer: the ten band frequencies are fixed.

## Links

- Source code: https://github.com/pepperonas/pegel
- Changelog: https://pegel.celox.io/changelog.md
- Licence (MIT): https://github.com/pepperonas/pegel/blob/main/LICENSE
- Support the project: https://www.paypal.com/donate/?business=martin.pfeffer@celox.io&currency_code=EUR&item_name=Pegel
- Author: Martin Pfeffer, https://celox.io — Imprint https://celox.io/impressum/ · Privacy https://celox.io/datenschutz/
