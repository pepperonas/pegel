# Changelog

Alle nennenswerten Änderungen an Pegel. Format nach
[Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach
[Semantic Versioning](https://semver.org/lang/de/).

## [1.1.0] - 2026-11-15

### Neu

- **Ein Klang für jede Ausgabe, vorgeschlagen.** Wird eine neue Ausgabe aktiv, bietet eine Karte im
  Tab Klang einen passenden Klang an; ein Tipp legt ein Profil für dieses Gerät an, bindet und lädt es.
  - **Kopfhörer:** Pegel erkennt rund 6.000 Modelle am Bluetooth-Namen und wendet die Korrektur an,
    die das Projekt [AutoEq](https://github.com/jaakkopasanen/AutoEq) für Pegels zehn Bänder
    berechnet hat, samt dem Namen dessen, der gemessen hat. Nicht erkannt? Das Modell von Hand suchen.
  - **Lautsprecher, Autos, der Lautsprecher des Handys:** dafür gibt es keine offenen Messungen.
    Pegel fragt einmal, was das Gerät ist, und bietet eine eigene Startkurve für diese Art an —
    als Ausgangspunkt beschriftet, nicht als Messung.
  - „Nicht jetzt“ merkt sich Pegel je Gerät; *Profile › Geräte › Klang vorschlagen…* holt das
    Angebot zurück.
- **Neun weitere Presets, in Gruppen:** Techno, House, Drum & Bass, Metal, R&B & Soul, Lounge und
  für alles, was keine Musik ist, Film & Serie, Gaming und Nachtmodus. Die Auswahl ordnet alle 22
  unter *Klangfarbe*, *Musik*, *Film, Spiele, Sprache* und *Eigene*.
- Keine neue Berechtigung: erkannt wird am Namen, den Android ohnehin jeder App nennt.
- Hilfe: woher der vorgeschlagene Klang kommt, und Samsungs eigene Klangeffekte neben Pegel.

### Behoben

- **Die Lautstärke schwankt nicht mehr in Wellen, wenn etwas angehoben ist.** Bisher fing ein
  Begrenzer jede angehobene Spitze ab und zog dabei den ganzen Mix herunter — mit mehr Bass bei
  jedem Schlag. Pegel senkt den Pegel jetzt vorab um die größte Anhebung von Equalizer und Bass,
  sodass der Begrenzer nichts mehr zu tun hat; er bleibt nur als Sicherheitsnetz knapp unter
  Vollaussteuerung (früher griff er ab −2 dB und arbeitete bei lauten Titeln sogar mit neutralem
  Klang). Der Preis: ein angehobener Klang ist leiser als vorher — die Statuskarte sagt, um wie
  viele dB. Loudness drückt weiterhin absichtlich in den Begrenzer.

### Geändert

- **Der Status sagt in ganzen Sätzen, was passiert.** Statt „Globaler Mix / Ungespeichert“ steht
  auf der Karte im Tab Klang jetzt: was Pegel formt („alles, was das Handy abspielt“ oder der
  genannte Player), die Ausgabe, der Klang („Profil ‚Rock‘“, „seither geändert“ oder „eigene
  Einstellung, nicht als Profil gespeichert“) und die Effekte, die gerade wirklich etwas ändern —
  ein Equalizer mit allen Bändern auf null gilt nicht als aktiv. Die Benachrichtigung nutzt
  dieselben Worte.
- „Elektronisch“ heißt jetzt „Elektro“, „Sprache“ heißt „Sprache & Podcast“.

## [1.0.1] - 2026-11-15

### Geändert

- **Die automatische Verbindung bevorzugt den globalen Mix.** Wo das Gerät ihn zulässt, bleibt
  Pegel jetzt auf dem globalen Mix, der alles erreicht, was das Handy abspielt. Playern, die ihre
  Wiedergabe anmelden, folgt Pegel nur noch dort, wo der globale Mix verweigert wird oder ein
  anderer Equalizer ihn übernommen hat. Vorher zog ein Player, der sich anmeldet, Pegel auf seine
  eigene Sitzung — auf einem Galaxy S24 Ultra mit Spotify blieb der Klang dadurch unverändert.
  Hall braucht die eigene Sitzung eines Players: dafür *Nur Player* wählen.
- Meldet sich ein Player an, holt Pegel den globalen Mix nicht mehr von einem anderen Equalizer zurück.

### Neu

- Der Abschnitt „Über“ verlinkt die Produktseite, <https://pegel.celox.io>.

## [1.0.0] - 2026-11-15

Erste Version.

### Neu

- **Equalizer** mit zehn Bändern von 31 Hz bis 16 kHz, ±12 dB in Halb-Dezibel-Schritten, einer
  Kurve über den Reglern, 13 eingebauten Presets und eigenen Presets.
- **Bass-Boost**, **Loudness** und **Virtualizer**, jeweils mit Schalter und Stärke. Die
  Bass-Grenzfrequenz (10–150 Hz) und was „100 %“ bedeutet (5–30 dB), sind normale Einstellungen.
- **Hall** und **Links/Rechts-Balance** als zuschaltbare Karten.
- **Profile**: den ganzen Klang unter einem Namen speichern, laden, Änderungen erkennen, speichern
  oder verwerfen.
- **Automatisch je Gerät**: ein Profil an Lautsprecher, Kabel- oder USB-Kopfhörer oder ein
  Bluetooth-Gerät binden — Pegel wendet es an, sobald diese Ausgabe aktiv wird.
- **Verbindungsmodi**: Playern folgen, die ihre Wiedergabe anmelden, den globalen Mix nutzen oder
  beides (automatisch). Pegel meldet sich als System-Equalizer an, der „Equalizer“-Knopf eines
  Players öffnet ihn also.
- Ein **Hauptschalter** mit Vordergrunddienst, einer Benachrichtigung, die sagt, womit Pegel
  verbunden ist, und Neustart nach dem Booten.
- **Sicherung und Wiederherstellung** aller Profile, Presets und Einstellungen als eine JSON-Datei;
  einzelne Profile lassen sich als `.pegel`-Datei teilen.
- Eine **Hilfeseite**, die erklärt, warum ein Equalizer mit manchen Apps funktioniert und mit
  anderen nicht, und was man probieren kann.
- Material 3 Expressive, hell und dunkel, optional Material-You-Farben, Englisch und Deutsch.
- **Updates in der App:** Einmal am Tag und beim Öffnen fragt Pegel seine Produktseite nach der
  neuesten Version (ohne Kennung) und zeigt eine Karte auf dem ersten Bildschirm. *Aktualisieren*
  lädt die Datei, prüft ihre SHA-256-Prüfsumme und übergibt sie Androids Installer; ein
  abgebrochener Download macht weiter. Die Prüfung lässt sich in den Einstellungen ausschalten —
  dann geht die App nie ins Netz.

Keine Werbung, kein Tracking, kein Konto, keine Bezahlschranke.
