# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Pegel (`io.celox.pegel`) is an open-source (MIT) system equalizer for Android: a ten-band equalizer,
bass boost, loudness, virtualizer, optional reverb and balance, profiles, and a profile per output
device. It does not process audio itself — it configures Android's own effects
(`android.media.audiofx`) on audio sessions. No ads, no tracking, no account; `INTERNET` exists for
one thing only: the update check (on unless switched off) and the update it downloads.

Repository: `github.com/pepperonas/pegel` (public, MIT). Distribution is GitHub Releases only — not
Google Play. Product page: <https://pegel.celox.io> (source in `website/`, see below).

Single Gradle module (`:app`), Kotlin + Jetpack Compose (Material 3 Expressive), minSdk 28 /
target+compile 35, JDK 17. minSdk is 28 rather than the house default 26 because
`DynamicsProcessing` — the engine everything runs on — arrived in Android 9.

The feature set was inspired by Flat Equalizer. Pegel is an independent implementation written
against Android's public API: never copy code, texts, layouts or preset tables from that or any other
equalizer app into this repository.

## Build & run

```bash
./gradlew :app:assembleDebug          # APK → app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JVM + Robolectric (sdk 34, see src/test/resources/robolectric.properties)
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest --tests "io.celox.pegel.audio.EffectEngineTest"   # one class
```

Device commands always name their target — look at `adb devices` first, then
`adb -s <emulator> install -r app/build/outputs/apk/debug/app-debug.apk` or
`ANDROID_SERIAL=<emulator> ./gradlew :app:installDebug`. Never run an install or a `connected*` task
against "whatever is attached": a real phone may be connected over Wi-Fi debugging.

The first cold start of a debug build on an emulator takes ~10 s; a screenshot taken earlier shows a
blank window, not a bug.

## Release

Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`, add a dated
`## [X.Y.Z] - YYYY-MM-DD` section to **both** `CHANGELOG.md` and `CHANGELOG.de.md` (`ChangelogTest`
fails without it, and when the two disagree), run `python3 scripts/update-badges.py` (README badges
for version / unit tests / lines of code / test code — `ReadmeBadgesTest` fails when they drift; never
type those numbers), commit, push, then `git tag vX.Y.Z && git push origin vX.Y.Z`.
`.github/workflows/release.yml` runs tests and lint, builds the signed APK, compares the signing
certificate with the repository variable `SIGNING_CERT_SHA256`, and publishes `pegel-vX.Y.Z.apk` +
`SHA256SUMS.txt` with the changelog section as notes. Secrets: `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Locally `assembleRelease` signs via the gitignored
`keystore.properties` + `release.jks` — never commit `*.jks`, `keystore.properties` or
`local.properties`.

The four secrets and the variable are set in the GitHub repo (2026-11-15). The key itself is backed
up in the private keystore repository (`pegel-keystore/`, with a `secrets.txt` describing it) — it
is the only signing key; if it is lost, no installed copy can ever be updated.

Third-party actions are pinned to commit SHAs and Dependabot proposes updates. Do not merge a major
bump of `setup-gradle` or `action-gh-release` blind: the release job only runs on a tag, so a broken
bump shows up at the worst moment. `tests.yml` runs on every push and pull request.

## Status (2026-11-15)

- **v1.0.1 is released** (2026-11-15) and is what `pegel.celox.io/download` serves; v1.0.0 was the
  first release the same day.
- **AUTO prefers the global mix since 1.0.1.** On the S24 Ultra, Spotify announces its session and
  the old AUTO moved there; only the global mix was audible (the move itself was inferred from the
  phone's broadcast history, not watched). AUTO now takes the global mix wherever the device grants
  it and follows players only where it is refused or lost. The app cannot measure audibility
  (that would need the microphone permission) — "granted" is the only signal there is.
- Built and accepted on an emulator with Android 9: service, global mix, player sessions, reboot,
  app update, backup round trip, both languages, both themes — and the in-app update end to end
  (a signed 0.9.0 test build found 1.0.0 on the page, downloaded, verified, asked for the install
  permission, continued by itself, installed, and posted "updated to 1.0.0").
- **Nothing has been verified by ear on a real phone.** Open questions only real hardware answers:
  - Do the 31 Hz and 62 Hz bands act separately at the default 10 ms block? If not, raise the
    default block size (`Settings.frameDurationMs`) — it trades latency for bass resolution.
  - Does loudness stay clean now that its gain sits in front of the limiter?
  - The Android 12+ paths: refused foreground-service start, sticky restart, notification permission,
    and the installer's `USER_ACTION_NOT_REQUIRED` path.
  - Auto-apply with Bluetooth devices (only the speaker was exercised on the emulator).
- Known and deliberately left: a restore interrupted by a configuration change in the few
  milliseconds between the database commit and the settings write can leave new profiles with old
  settings; the limiter is active even on "Flat" (not bit-transparent); any app can send a session
  announcement and pull Pegel off the global mix ("Detach" brings it back).
- The release APK is ~50 MB because `isMinifyEnabled = false` ships all of
  `material-icons-extended`. Turning R8 on would shrink it to a few MB but needs a full pass over
  the app first (Room, WorkManager, reflection in the update check).
- The settings screenshots in `docs/screenshots/` still show the old icon in the About card.
- The changelog has no "Unreleased" section (`ChangelogTest` wants every section dated) — put
  pending changes into the next version's section.
- Follow-ups: a share link for profiles, a legacy effect path for Android 8.

## Sound suggestions (1.1.0)

When an output becomes active and has neither a profile nor an answer, the Sound tab offers a
sound (`ui/suggest/`, decisions in `suggestionFor`): a measured correction for headphones the table
knows, otherwise a question what the device is and Pegel's own starting curve for that kind
(`device/StartCurves.kt`). Taking it creates a profile, binds it, loads it (`applySuggestion`).

- `app/src/main/assets/headphones.tsv` is generated: `python3 scripts/autoeq.py` reads AutoEq at
  the commit pinned in the script (blobless sparse clone into `build/autoeq`) and also writes
  `docs/AUTOEQ-NOTICE.md`. Never edit either by hand. The measurement data has no licence of its
  own (user decision 2026-11-15: ship it, name the sources).
- **No match is better than a wrong one** (`HeadphoneCatalog.match`): only whole names count, and
  the test with the real device names of the developer's phone guards it. Android gives an app
  without the Bluetooth permission the name only — no device class, no codec — hence the question.
- **There are no measurements for speakers, cars or phone speakers.** The start curves are Pegel's
  own and the UI says "starting point, not a measurement". Do not add per-model speaker curves
  that look measured.
- The card must not wait for the table: `suggestionFor(…, catalog = null)` offers what does not
  need it. Parsing takes seconds on a slow device.
- Room is at version 2 (`devices.type`, `devices.suggestion`); `MigrationTest` builds version 1 from
  the checked-in schema. Every schema change needs a migration in `PegelDatabase.MIGRATIONS`.
- Verified on the emulator: card, apply, the dialog from the device row, update from 1.0.1. **Not
  verified:** anything with a Bluetooth device (the emulator has none) — recognition by name, the
  type question and the model search have only their unit tests.

## Status wording (1.1.0)

The status card and the notification speak in whole sentences (user finding: "Global mix / Unsaved"
told him nothing): what is shaped, the output, the sound, and the effects that change something
right now (`activeEffects` — switched on is not the same as doing something). Keep new status
texts in that form; no bare labels. Presets are grouped (`presetGroup`); a new built-in preset
needs its id in `Presets.kt`, both `strings.xml` and `presetLabel` — `PresetsTest` checks all three.

## Icon and artwork

`python3 scripts/icon.py` writes the adaptive-icon layers, the themed layer, the status-bar icon and
`docs/icon.svg` / `docs/mark.svg` from one computed geometry (five level bars, an amber peak-hold cap).
`python3 scripts/art.py` renders `docs/banner.webp` and `docs/hero.png` (needs Chrome and `cwebp`).
Never edit the generated drawables by hand.

## Product page

`website/` is built with the product-page kit (`~/claude/_templates/apps/product-page`): texts in
`website/site.json` (five languages), then
`python3 $K/build.py website/site.json website --hero docs/hero.png --screens <strip.png> --icon docs/mark.svg --force`
and `./website/deploy.sh` (`server` also installs the release timer and the vhost; `"tls": "wildcard"`
uses the VPS's `*.celox.io` certificate, so there is no DNS or certbot step). A timer on the VPS
mirrors the newest GitHub release every 15 minutes into `latest.json`, `/files/` and `/download` —
**a release updates the page by itself**. The app depends on that file: `ReleaseSource.SITE_URL`
reads `version`, `name`, `url`, `size`, `sha256` from it, and `ApkReleaseParser` only accepts
`https://pegel.celox.io/files/…` or GitHub release URLs. Do not rename the release asset
(`pegel-vX.Y.Z.apk`) or move `/files/` without changing both.

## Architecture (data flow)

`SoundRepository` is the single source of truth. Everything else mirrors it.

```
UI (Compose) ──update()──▶ SoundRepository ──state/settings──▶ PegelService ──plan──▶ EffectEngine ──▶ EffectChain(s)
                               ▲                                   ▲                      ▲
                     Room + DataStore                DeviceWatcher → AutoApply     SessionReceiver
```

1. **`audio/`** — pure Kotlin except one file.
   - `SoundState` is what a profile stores; `normalized()` clamps everything, and it is called before
     anything is stored or applied. `Tuning` (bass cutoff, what "100 %" means) is a setting, not part
     of a profile.
   - `planFor(state, tuning)` turns that into a `ChainPlan` in the units the effects take. A
     switched-off effect is its neutral value — a chain never knows about switches.
   - `EffectEngine` decides which audio sessions carry a chain (`SessionMode` AUTO / GLOBAL_ONLY /
     PLAYERS_ONLY) and keeps them on the current plan. It is bookkeeping only and fully tested with
     `FakeEffectChain`.
   - **`AndroidEffectChain` is the only file that may touch `android.media.audiofx`**, and every call
     in it is guarded: effects can throw at any time (unsupported, session gone, another app took
     control). One `DynamicsProcessing` carries EQ (post-EQ, 10 bands), bass (pre-EQ band 0), and
     balance plus loudness as input gain per channel, in front of the limiter (gain *behind* a limiter
     clips — `inputGainsDb`, `LIMITER_POST_GAIN_DB`); `Virtualizer` and `PresetReverb` sit
     next to it.
2. **`data/`** — Room (`profiles`, `presets`, `devices`; deleting a profile sets bound devices to
   NULL), DataStore for settings and the sound being edited, and `SoundRepository` on top. Its
   `state`/`settings`/`activeProfile`/`modified` are updated synchronously inside each operation.
   `Backup.kt` is the file format of backups and shared profiles.
3. **`device/`** — `DeviceWatcher` reports the active output; `AutoApply` loads the bound profile when
   the output *changes*.
4. **`service/`** — `PegelService` (foreground, type `specialUse`) runs while the master switch is on
   and owns no state. `SessionReceiver` is in the manifest *and* registered at runtime by the service.
5. **`ui/`** — the decisions live in plain functions (`buildSoundUi`, `SoundActions`,
   `buildProfileRows`, `buildDeviceRows`) with tests; the ViewModels are glue.

## Things to keep in mind

- **Two receivers for one broadcast.** Players that address the session broadcast to equalizer apps
  reach the manifest receiver; players that send it without a target only reach a receiver registered
  at runtime (Android 8+). Removing either one silently loses players.
- **Foreground-service starts can be refused** (Android 12+, app in the background).
  `ServiceStarter.sync` turns that into a "tap to activate" notification; `MainActivity.onStart`
  starts the service again. Do not call `startForegroundService` anywhere else.
- **A lost global mix is not grabbed back** until the user does something (switch, mode, detach —
  not a player announcing itself: players do that on every track) — two equalizers taking
  session 0 from each other would never settle (`EffectEngine.globalLost`, pinned by a test).
  `setEnabled(true)` while already on is *not* such an event: the service re-asserts it on every
  state change.
- **Android never reports a dead session.** A player that is killed without closing its session keeps
  Pegel attached to nothing; `EffectEngine.forgetPlayers()` ("Detach" in the status row) is the way
  out. Do not assume `apply()` throws on a dead session — it does not.
- **The slider path does not wait for the disk.** `SoundRepository.update` changes the flows at once
  and leaves storing to one conflated writer that always writes the latest state; profile operations
  wait. Tests that simulate a restart call `flush()` first (RepoFixture does).
- **The drift guards read files outside the classpath** (READMEs, changelogs, workflows, manifest).
  They are declared as inputs of the test task in `app/build.gradle.kts`; without that Gradle calls
  the tests up to date and the guards silently do not run.
- **A red baseline makes a mutation check worthless.** The badge guard fails whenever the test count
  changes; run `scripts/update-badges.py` and see green before mutating, and list *which* test failed.
- **Headroom, not the limiter, keeps boosts clean.** `planFor` lowers the input gain by the largest
  boost of equalizer and bass (`peakBoostDb`); the limiter (`LIMITER_THRESHOLD_DB`, -0.5 dB) is only
  a safety net. Field finding 2026-11-15: at -2 dB and without headroom it ducked the whole mix on
  every kick — "the volume comes in waves". Do not trade the headroom for loudness; the status
  card shows it instead. Not verified by ear or measurement.
- **No reverb on the global mix.** There it is an auxiliary effect and would be a switch that does
  nothing, so the chain reports it unavailable and the card says why.
- **The update switch lives in `UpdateCheckStore`**, not in `Settings` — the worker reads it
  synchronously, and it does not belong in a backup file. It is on by default (user decision
  2026-11-15); off means no work is enqueued and nothing is asked (`UpdatePolicy`, pinned).
- **The in-app update never installs an unverified file.** `UpdateInstaller` downloads into the
  cache, compares the SHA-256 with the one the source published and deletes a mismatch; Android then
  checks the signature. `PegelApp` wraps the scheduling in `runCatching` — Robolectric has no
  WorkManager, and a failed schedule must not stop the app.
- **A restore never changes the master switch.** Reading a file must not start a service.
- **`startForeground` is wrapped** (`ServiceStarter.promote`): Android 12+ can refuse it on a sticky
  restart in the background. And the activity acts on its launch intent only once (`isFreshLaunch`),
  not on every rotation.
- **Launcher layers must be vector drawables.** The About section paints them with `painterResource`;
  a `<shape>` crashed Settings once (`LauncherIconTest`).
- **Strings:** English in `res/values/`, German in `res/values-de/`, same keys and placeholders
  (`LocalizationTest`). No hard-coded text in composables.
- **No control that does nothing.** If a device cannot do something, the UI says so
  (`ChainStatus` → `SoundUi`) instead of offering a dead switch.
- **Mutation-check new assertions.** A test that was never seen failing is not an assurance; two of
  the blind spots found while building 1.0.0 were dead code, two were real gaps.
