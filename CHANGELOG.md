# Changelog

All notable changes to Pegel. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow
[Semantic Versioning](https://semver.org/).

## [1.0.1] - 2026-11-15

### Changed

- **Automatic connection prefers the global mix.** Wherever the device grants it, Pegel now stays
  on the global mix, which reaches everything the phone plays. Players that announce their audio
  are followed only where the global mix is refused or another equalizer has taken it. Before,
  an announcing player pulled Pegel onto its own session — on a Galaxy S24 Ultra with Spotify
  that left the sound unchanged. Reverb needs a player's own session: choose *Players only* for it.
- A player announcing itself no longer makes Pegel take the global mix back from another equalizer.

### Added

- The About section links to the product page, <https://pegel.celox.io>.

## [1.0.0] - 2026-11-15

First release.

### Added

- **Equalizer** with ten bands from 31 Hz to 16 kHz, ±12 dB in half-decibel steps, a curve above
  the sliders, 13 built-in presets and your own presets.
- **Bass boost**, **loudness** and **virtualizer**, each with a switch and a strength. The bass
  cutoff (10–150 Hz) and what "100 %" means (5–30 dB) are ordinary settings.
- **Reverb** and **left/right balance** as optional cards.
- **Profiles**: save the whole sound under a name, load it, see when it was changed, save or
  discard the changes.
- **Automatic per device**: bind a profile to the speaker, wired or USB headphones, or a Bluetooth
  device — Pegel applies it when that output becomes active.
- **Connection modes**: follow players that announce their audio, use the global mix, or both
  (automatic). Pegel registers as the system equalizer, so a player's "Equalizer" button opens it.
- A **master switch** with a foreground service, a notification that says what Pegel is attached to,
  and a restart after reboot.
- **Backup and restore** of all profiles, presets and settings as one JSON file; single profiles can
  be shared as `.pegel` files.
- A **help page** that explains why an equalizer works with some apps and not with others, and what
  to try.
- Material 3 Expressive design, light and dark, optional Material You colours, English and German.
- **Updates inside the app:** once a day and when it is opened, Pegel asks its product page for the
  newest version (no identifier) and shows a card on the first screen. *Update* downloads the file,
  verifies its SHA-256 and hands it to Android's installer; a broken download resumes. The check can
  be switched off in Settings — then the app never goes online.

No ads, no tracking, no account, no paywall.
