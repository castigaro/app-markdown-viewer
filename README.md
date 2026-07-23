# Markdown Viewer (Android)

Eine kleine Android-App zum Anzeigen und Bearbeiten von Markdown-Dateien.

## Funktionen

- **Datei öffnen**: Auswahl einer `.md`-Datei über den Android-Dateipicker
  (Storage Access Framework) — es sind keine Speicher-Berechtigungen nötig.
- **Vorschau**: Die Datei wird gerendert angezeigt (Überschriften, Listen,
  Tabellen, Links, Task-Listen, Durchgestrichenes) — gerendert mit
  [Markwon](https://github.com/noties/Markwon).
- **Code-Ansicht**: Umschalter zwischen *Vorschau* und *Code*. In der
  Code-Ansicht lässt sich der Markdown-Quelltext in einem Monospace-Editor
  bearbeiten.
- **Speichern**: Änderungen werden direkt in die Originaldatei
  zurückgeschrieben. Ungespeicherte Änderungen werden mit `*` im Titel
  markiert; beim Öffnen einer anderen Datei wird nachgefragt.

## Build

Das Projekt ist ein Standard-Gradle-Projekt (Kotlin, AGP 8.5, compileSdk 34,
minSdk 26 / Android 8.0):

1. In Android Studio öffnen (**File → Open…**) und Gradle-Sync abwarten, oder
2. auf der Kommandozeile: `./gradlew assembleDebug` —
   das APK liegt danach unter `app/build/outputs/apk/debug/`.

## Projektstruktur

- `app/src/main/java/com/appsonar/mdviewer/MainActivity.kt` — gesamte App-Logik
  (Dateiauswahl, Laden/Speichern, Moduswechsel, Markdown-Rendering)
- `app/src/main/res/layout/activity_main.xml` — Layout mit Toolbar,
  Umschalter, Vorschau- und Editor-Ansicht

## Lizenz

Copyright © 2026 Torsten Klein

Dieses Projekt steht unter der **GNU Affero General Public License v3.0 oder
später** (AGPL-3.0-or-later), siehe [LICENSE](LICENSE): Wer den Code — auch als
Netzwerkdienst — weiterverwendet oder verändert, muss den Quellcode unter
derselben Lizenz offenlegen. Eine kommerzielle Lizenz ist auf Anfrage möglich.
