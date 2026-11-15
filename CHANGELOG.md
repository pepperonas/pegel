# Changelog

All notable changes to Pegel. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versions follow
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-15

### Added

- **A sound for each output, suggested.** When a new output becomes active, a card on the Sound tab
  offers a fitting sound; one tap creates a profile for that device, binds it and loads it.
  - **Headphones:** Pegel recognises about 6,000 models by their Bluetooth name and applies the
    correction the [AutoEq](https://github.com/jaakkopasanen/AutoEq) project computed for Pegel's
    ten bands, with the name of who measured it. Not recognised? Search the model by hand.
  - **Speakers, cars, the phone's own speaker:** no open measurements exist for these. Pegel asks
    once what the device is and offers its own starting curve for that kind — labelled as a
    starting point, not a measurement.
  - "Not now" is remembered per device; *Profiles › Devices › Suggest a sound…* brings the offer back.
- **Nine more presets, in groups:** Techno, House, Drum & bass, Metal, R&B & soul, Lounge, and for
  everything that is not music Film & series, Gaming and Night mode. The picker now sorts all 22
  under *Tone*, *Music*, *Film, games, speech* and *Your own*.
- No new permission: the device is recognised by the name Android already gives every app.
- Help: where the suggested sound comes from, and Samsung's own sound effects running next to Pegel.

### Fixed

- **The volume no longer moves in waves when something is boosted.** Until now a limiter caught
  every boosted peak and pulled the whole mix down with it — with more bass, on every kick. Pegel
  now lowers the level in front by the largest boost of equalizer and bass, so the limiter has
  nothing to do; it only remains as a safety net just below full scale (it used to start at −2 dB
  and worked on loud tracks even with a flat sound). The price: a boosted sound is quieter than
  before — the status card says by how many dB. Loudness still pushes into the limiter on purpose.

### Changed

- **The status says what is happening, in whole sentences.** Instead of "Global mix / Unsaved" the
  card on the Sound tab now reads: what Pegel shapes ("everything this phone plays" or the named
  player), the output in use, the sound ("profile “Rock”", "changed since", or "your own setting,
  not saved as a profile") and the effects that actually change something right now — an equalizer
  with every band at zero is not called active. The notification uses the same wording.
- "Electronic" is now "Electro", "Speech" is "Speech & podcast".

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
