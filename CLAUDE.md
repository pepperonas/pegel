# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Pegel (`io.celox.pegel`) is an open-source (MIT) system equalizer for Android: a ten-band equalizer,
bass boost, loudness, virtualizer, optional reverb and balance, profiles, and a profile per output
device. It does not process audio itself — it configures Android's own effects
(`android.media.audiofx`) on audio sessions. No ads, no tracking, no account; `INTERNET` exists for
one thing only, the opt-in update check (off by default).

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
- **A lost global mix is not grabbed back** until the user does something — two equalizers taking
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
- **No reverb on the global mix.** There it is an auxiliary effect and would be a switch that does
  nothing, so the chain reports it unavailable and the card says why.
- **The update switch lives in `UpdateCheckStore`**, not in `Settings` — the worker reads it
  synchronously, and consent does not belong in a backup file.
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
- The product page (`pegel.celox.io`) and a share link for profiles are a follow-up; until the page
  exists the About section has no product-page chip and the update check asks GitHub directly.
