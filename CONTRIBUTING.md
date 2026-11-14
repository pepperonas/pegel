# Contributing

Issues and pull requests are welcome.

- **Bugs:** say which phone and Android version, which player, which connection mode
  (Settings › Connection), and what the status line on the Sound tab showed. "No effect with player X"
  is most useful together with the output of `adb shell dumpsys media.audio_flinger | grep -i -A3 "effect"`.
- **Code:** `./gradlew :app:testDebugUnitTest :app:lintDebug` must pass. New behaviour comes with a
  test that fails without it. Every user-visible text goes into `res/values/strings.xml` **and**
  `res/values-de/strings.xml` (`LocalizationTest` checks both).
- **Scope:** Pegel has no ads, no tracking and no account, and that is not negotiable. The only
  network access is the opt-in update check.
- Pegel is written from Android's public `android.media.audiofx` API. Please do not contribute code,
  tables or texts taken from other equalizer apps.

By contributing you agree that your contribution is licensed under the [MIT License](LICENSE).
