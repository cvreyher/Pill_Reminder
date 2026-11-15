# Medikamentenwecker

🌐 **Website:** [medikamentenwecker.vonreyher.com](https://medikamentenwecker.vonreyher.com)

**Eine Open-Source-Android-App zur Medikamentenerinnerung und Einnahmekontrolle – entwickelt mit Kotlin und Jetpack Compose.**

Der Medikamentenwecker macht die tägliche Medikamenteneinnahme einfach und zuverlässig: Medikamente organisieren, eigene Einnahmezeiten festlegen, jede Einnahme bestätigen – und den **Bundeseinheitlichen Medikationsplan (BMP)** direkt per Kamera einscannen.

Nie wieder eine Einnahme vergessen.

> **Projektstatus:** Frühe Entwicklungsphase. Die Grundfunktionen sind umgesetzt, weitere Funktionen stehen auf der Roadmap.

## Download

- **GitHub:** Unter [Releases](https://github.com/cvreyher/Pill_Reminder/releases) liegt zu jeder Version eine installierbare APK. Herunterladen, auf dem Handy öffnen und die Installation aus dieser Quelle erlauben (ab Android 8).
- **Neuester Stand:** Jeder Push auf `main` baut die App automatisch; die APK hängt als Artefakt am jeweiligen Lauf unter [Actions](https://github.com/cvreyher/Pill_Reminder/actions).
- **F-Droid:** Die App ist für F-Droid vorbereitet (siehe [F-Droid](#f-droid)).

## Funktionen

- **Übersichtliche Tagesansicht** – Persönliche Begrüßung, eine große Uhr mit dem Tagesfortschritt und alle Einnahmen des Tages in zeitlicher Reihenfolge – eine Linie zeigt, wo du gerade stehst.
- **Eigene Einnahmezeiten** – Jedes Medikament bekommt eine eigene Uhrzeit, die sich jederzeit über den Time Picker ändern lässt.
- **Einnahme-Detailansicht** – Ein Tipp auf ein Medikament öffnet eine Vollbildansicht mit Medikament, Dosis und Countdown („Noch 45 min bis zur Einnahme“ bzw. „Jetzt einnehmen“).
- **Wischen zum Bestätigen** – Die Einnahme wird wie beim Entsperren eines iPhones mit einem Schieberegler bestätigt – mit Erfolgsanimation, Vibration und einem kurzen Bestätigungston (stumm im Lautlos-Modus). Versehentliche Bestätigungen lassen sich rückgängig machen.
- **Medikationsplan scannen** – Den DataMatrix-Code eines Bundeseinheitlichen Medikationsplans einscannen und den Plan in der App speichern (siehe unten).
- **Planverlauf** – Jeder gescannte Medikationsplan wird als eigener Plan gespeichert. Pläne lassen sich aktiv und inaktiv schalten; ein neuer Plan kann den bisherigen ersetzen. Die App zeigt dabei, was neu, geändert oder abgesetzt ist, und behält frühere Pläne im Verlauf.
- **Medikamente bearbeiten** – Name, Wirkstoff, Zeiten, Hinweise usw. lassen sich nachträglich ändern.
- **Für mich oder als Pflegekraft** – Beim ersten Start wählst du, ob du nur deine eigenen Medikamente verwaltest oder die mehrerer Personen. Im Pflege-Modus zeigt die Tagesansicht alle Personen gemeinsam, mit Filtern pro Person.
- **Medikament hinzufügen** – Vollbild-Formular für Handelsname, Wirkstoff, Darreichungsform, PZN, Dosis, Einnahmezeiten (morgens/mittags/abends/nachts oder eigene Uhrzeiten), Hinweise und Grund. Beim Tippen schlägt die App passende Medikamente aus der lokalen Arzneimitteldatenbank vor; die PZN lässt sich per Barcode oder securPharm-Code von der Packung scannen.
- **Bei Bedarf** – Medikamente ohne feste Uhrzeit (z. B. Bedarfsmedikation aus dem Plan) erscheinen in einem eigenen Abschnitt und können beliebig oft bestätigt werden.
- **Wirkstoff und Hinweise** – Die Vollbildansicht zeigt Handelsname, Wirkstoff, Darreichungsform sowie Hinweise und Einnahmegrund aus dem Medikationsplan.
- **Verlauf** – Statistik mit Einnahmequote und Balken pro Tag, Auswertung pro Medikament (z. B. 13/14) und alle Einnahmen – für 7 Tage, 30 Tage oder gesamt, im Pflege-Modus pro Person.
- **Erinnerungen** – Pünktliche Benachrichtigungen mit hoher Priorität zu jeder geplanten Einnahme. Ein Tipp öffnet direkt den Bestätigen-Bildschirm (auch über dem Sperrbildschirm), „In 10 Min. erinnern“ verschiebt die Erinnerung. Optional erscheint sie bei gesperrtem Handy wie ein Wecker im Vollbild.
- **Homescreen-Widget** – Ein Widget in Blumenform zeigt die nächste Einnahme und den Tagesfortschritt.
- **Arzneimittelinfos der EMA** – Optional lassen sich Wirkstoffe, Darreichungsform und Klassifikation über die EMA-Datenbank abfragen (siehe unten).
- **Einstellungen** – Name, Modus, Personen und EMA-Zugang lassen sich jederzeit ändern.

- **Material 3 Expressive** – Die Oberfläche nutzt ausschließlich Komponenten aus der [Android UI Gallery](https://developer.android.com/design/ui/gallery) im Expressive-Stil (u. a. gewellte Fortschrittsanzeige, Material Shapes, Button Groups) in eigenem Lila mit Dark Mode.

## Medikationsplan scannen

Der papierbasierte **Bundeseinheitliche Medikationsplan (BMP)** nach § 31a SGB V enthält oben rechts einen 2D-**DataMatrix-Code**. Darin sind Patienten- und Medikationsdaten im sogenannten **Ultrakurzformat (UKF)** gespeichert – technisch spezifiziert in Anlage 3 der Vereinbarung von KBV, BÄK und DAV.

Der Medikamentenwecker liest diesen Code ein und übernimmt:

| Daten | Inhalt |
|---|---|
| Patient | Name und Geburtsdatum |
| Aussteller | Praxis bzw. Apotheke und Ausdruckdatum |
| Medikamente | Handelsname oder Wirkstoff, Wirkstärke, Darreichungsform, PZN |
| Dosierung | Schema morgens – mittags – abends – zur Nacht (z. B. `1-0-1-0`) oder Freitext |
| Hinweise | Einnahmegrund und Anwendungshinweise (z. B. „auf nüchternen Magen“) |
| Abschnitte | z. B. Dauermedikation, Bedarfsmedikation, Selbstmedikation |

**So funktioniert es:**

1. Im Tab **Pläne** auf **Plan scannen** tippen.
2. Den DataMatrix-Code oben rechts auf dem Medikationsplan formatfüllend in den Rahmen halten – oder **Aus Foto laden** wählen.
3. Mehrseitige Pläne werden erkannt – die weiteren Seiten über **Nächste Seite scannen** ergänzen.
4. Mit **Plan speichern** sichern. Der Plan erscheint unter **Pläne**, und alle Medikamente mit festem Dosierschema werden automatisch in die Tagesansicht übernommen.

Die Einnahmezeitpunkte des BMP werden dabei auf Standardzeiten abgebildet, die sich pro Medikament anpassen lassen:

| BMP-Zeitpunkt | Uhrzeit in der App |
|---|---|
| morgens | 08:00 |
| mittags | 12:00 |
| abends | 18:00 |
| zur Nacht | 22:00 |

Bedarfsmedikation und Freitext-Dosierungen werden im Plan gespeichert, aber nicht automatisch eingeplant.

**Hinweise:**

- Der DataMatrix-Code des BMP ist sehr dicht (bis zu 144 × 144 Punkte). Die App liest ihn deshalb mit CameraX und dem quelloffenen [ZXing-cpp](https://github.com/zxing-cpp/zxing-cpp) in hoher Auflösung – ohne Google-Dienste. Dafür braucht sie die **Kamera-Berechtigung**.
- Klappt das Scannen live nicht, z. B. bei schlechtem Licht, kann der Code über **Aus Foto laden** aus einem Foto gelesen werden. Die Taschenlampe lässt sich im Scanner einschalten.
- Alle Daten bleiben **lokal auf dem Gerät**. Es werden keine Gesundheitsdaten an Server übertragen.
- **Lokale Arzneimitteldatenbank:** Unter Einstellungen → Arzneimitteldatenbank lässt sich eine CSV-Datei mit PZN und Namen importieren (z. B. ein lizenzierter IFA-Export). Die App benennt damit PZN-Einträge aus Medikationsplänen automatisch und schlägt beim Hinzufügen passende Medikamente vor – offline. Die App enthält selbst **keine** Arzneimitteldaten; die Nutzungsrechte an importierten Daten liegen beim Nutzer.
- Laut BMP-Spezifikation stehen bei Medikamenten mit **PZN** Handelsname und Wirkstoff nicht im Code – Praxis- und Apothekensoftware schlagen sie in einer (kostenpflichtigen) Arzneimitteldatenbank nach. Die App zeigt diese Medikamente zunächst als „PZN 1234567“. Den Namen gibst du einmal über **Bearbeiten** ein oder importierst eine Arzneimitteldatenbank; die App merkt sich ihn zur PZN und trägt ihn bei allen künftigen Plänen automatisch ein.

## Arzneimittelinfos der EMA

Über die [Public PMS API der Europäischen Arzneimittel-Agentur](https://api.pms.ema.europa.eu/public/v1/swagger) kann die App zu einem Medikament Wirkstoffe, Darreichungsform, Klassifikation und Abgabestatus abfragen (Info-Symbol in der Vollbildansicht oder Tippen auf einen Eintrag eines Plans).

- Die API verlangt einen **OAuth2-Zugang** (Client Credentials). Zugangsdaten und Token-Endpunkt vergibt die EMA nach einer [Registrierung](https://www.ema.europa.eu/en/human-regulatory-overview/research-development/data-medicines-iso-idmp-standards-overview/product-management-services-pms).
- Die Zugangsdaten werden unter **Einstellungen → EMA-Datenbank** eingetragen und nur auf dem Gerät gespeichert – sie sind nicht im App-Code enthalten.
- Die API kennt **keine PZN**. Gesucht wird daher über den Namen des Medikaments. An die EMA wird nur dieser Name übertragen.

## Logo

Das Logo **„Bestätigt“** ist eine Kapsel, die zugleich der Schieber zum Bestätigen ist: eingeschaltet heißt eingenommen. Es greift damit die zentrale Interaktion der App auf, statt nur eine Pille zu zeigen.

| | |
|---|---|
| Hauptfarbe | Lila `#6E5A83` |
| Akzent | Helles Lila `#EDD9FF`, Weiß für den Schieberknopf |
| Dateien | `design/logo/` (SVG-Master in Farbe und Schwarz, App-Icon, Konzeptbogen, Testblatt) |
| App-Icons | Adaptives Icon und Themen-Icon (`res/drawable/ic_launcher_*`), Benachrichtigungs-Icon als Kleinst-Version (`ic_stat_logo`) |

Mindestgröße: 24 px. Darunter (z. B. 16-px-Favicons) die Kleinst-Version mit größerem Loch verwenden. Das Logo wurde mit dem [logo-design-skill](https://github.com/kaankiziltug/logo-design-skill) entwickelt; eine Markenrecherche wurde nicht durchgeführt.

## Screenshots

Screenshots folgen im Laufe der Entwicklung.

## Tech-Stack

| Technologie | Zweck |
|---|---|
| Kotlin | Programmiersprache |
| Jetpack Compose | Native Android-Oberfläche |
| Material 3 | Designsystem |
| CameraX + ZXing-cpp | Scannen des BMP-DataMatrix-Codes und der PZN |
| Jetpack Glance | Homescreen-Widget |
| Gradle Kotlin DSL | Build-Konfiguration |
| Android SDK | Native Android-Plattform |

## Projektstruktur

```text
Pill_Reminder/
├── app/
│   ├── src/main/
│   │   ├── java/com/vonreyher/medicationtracker/
│   │   │   ├── MainActivity.kt          # Navigation, Tagesansicht, Dialoge
│   │   │   ├── Medication.kt            # Datenmodell einer Einnahme
│   │   │   ├── data/
│   │   │   │   ├── AppSettings.kt       # Einstellungen, Modus, Personen
│   │   │   │   ├── BmpParser.kt         # Parser für den BMP-DataMatrix-Code
│   │   │   │   ├── EmaClient.kt         # Abfrage der EMA-Datenbank
│   │   │   │   ├── MedicationPlan.kt    # Datenmodell eines Medikationsplans
│   │   │   │   └── MedicationStore.kt   # Lokale Speicherung
│   │   │   ├── widget/                  # Homescreen-Widget
│   │   │   └── ui/
│   │   │       ├── TodayScreen.kt       # Startseite mit Uhr und Tagesplan
│   │   │       ├── HistoryScreen.kt     # Einnahmeverlauf
│   │   │       ├── OnboardingScreen.kt  # Einrichtung beim ersten Start
│   │   │       ├── SettingsScreen.kt    # Einstellungen
│   │   │       ├── DrugInfoSheet.kt     # Arzneimittelinfos der EMA
│   │   │       ├── MedicationDetailScreen.kt
│   │   │       ├── PlanScreens.kt       # Planliste, Plandetails, Scan-Vorschau
│   │   │       ├── ScannerScreen.kt     # Kamera-Scanner für den BMP-Code
│   │   │       ├── ConfirmationSound.kt # Bestätigungston
│   │   │       ├── SlideToConfirm.kt    # Schieberegler zum Bestätigen
│   │   │       └── theme/Theme.kt
│   │   ├── res/drawable/                # Material-Icons
│   │   ├── res/raw/                     # Bestätigungston
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Erste Schritte

### Voraussetzungen

- Android Studio oder IntelliJ IDEA mit Android-Unterstützung
- JDK 17 oder ein kompatibles JDK
- Android SDK
- Git
- Ein Gerät mit Kamera (für den Scanner)

### Repository klonen

```bash
git clone https://github.com/cvreyher/Pill_Reminder.git

cd Pill_Reminder
```

### App bauen

Unter macOS oder Linux:

```bash
./gradlew assembleDebug
```

Unter Windows:

```bash
gradlew.bat assembleDebug
```

### App starten

Projekt in Android Studio öffnen, die Gradle-Synchronisierung abwarten, einen Emulator oder ein angeschlossenes Gerät auswählen und die Konfiguration `app` starten.

## Entwicklung

Der Medikamentenwecker wird als native Android-App mit Kotlin und Jetpack Compose entwickelt.

Prioritäten in der Entwicklung:

1. Eine saubere und wartbare App-Architektur.
2. Zuverlässige lokale Erinnerungen und Benachrichtigungen.
3. Einnahmeverlauf und Statistiken.
4. Namensauflösung für PZN aus gescannten Medikationsplänen.
5. Barrierefreiheit und eine gute Nutzererfahrung.

## Roadmap

- [x] Medikamente anlegen und verwalten
- [x] Eigene Einnahmezeiten pro Medikament
- [x] Tagesansicht mit Uhr und Jetzt-Linie
- [x] Einnahme per Schieberegler bestätigen
- [x] Bundeseinheitlichen Medikationsplan (BMP) scannen
- [x] Gescannte Medikationspläne speichern
- [x] Lokale Speicherung
- [x] Deutsche Oberfläche
- [x] Erinnerungen und Benachrichtigungen
- [x] Einnahmeverlauf
- [x] Pflege-Modus für mehrere Personen
- [x] Homescreen-Widget
- [ ] Auflösung von PZN zu Handelsnamen (die EMA-API kennt keine PZN)
- [ ] App-Sperre per Fingerabdruck
- [x] Abgleich neuer Planversionen (neu, geändert, abgesetzt)
- [ ] Vorrats- und Nachkauf-Erinnerung

## Mitwirken

Beiträge, Vorschläge und Fehlermeldungen sind willkommen.

Wer eine Idee hat, wie sich Medikamentenerinnerungen verbessern oder die Verwaltung vereinfachen lässt, kann gerne ein Issue eröffnen oder einen Pull Request einreichen.

## Releases und Signatur

Der Workflow [`.github/workflows/build.yml`](.github/workflows/build.yml) baut bei jedem Push eine Debug- und eine Release-APK. Ein Git-Tag `v*` (z. B. `v1.0.0`) veröffentlicht die APKs als GitHub-Release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Damit Nutzer Updates installieren können, sollte die Release-APK immer mit demselben Schlüssel signiert sein. Dafür einmalig einen Schlüssel erzeugen und **gut sichern** (ohne ihn sind keine Updates mehr möglich):

```bash
keytool -genkeypair -v -keystore release.jks -alias medicationtracker -keyalg RSA -keysize 4096 -validity 10000
base64 -i release.jks | pbcopy   # macOS: Base64 in die Zwischenablage
```

und in GitHub unter *Settings → Secrets and variables → Actions* anlegen: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Ohne diese Secrets veröffentlicht der Workflow nur die mit einem Entwicklerschlüssel signierte Debug-APK.

## F-Droid

Die App erfüllt die Aufnahmekriterien von F-Droid: ausschließlich freie Abhängigkeiten (kein Google Play / ML Kit / Firebase), GPL-3.0, kein Tracking, keine Werbung und kein eingebetteter Abhängigkeits-Block im APK.

- Store-Texte und Icon liegen im Fastlane-Format unter [`fastlane/metadata/android/`](fastlane/metadata/android/) (Deutsch und Englisch). Screenshots kommen nach `…/images/phoneScreenshots/`.
- Die Build-Beschreibung für F-Droid liegt unter [`fdroid/com.vonreyher.medicationtracker.yml`](fdroid/com.vonreyher.medicationtracker.yml).

**Einreichen:**
1. Release-Tag setzen (z. B. `v1.0.0`) und pushen – F-Droid baut aus diesem Tag.
2. Auf [gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) forken, die YAML-Datei nach `metadata/` kopieren und einen Merge Request mit der Vorlage „App inclusion“ öffnen ([Anleitung](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)).

Die optionale EMA-Abfrage ist in der Metadatei als Anti-Feature `NonFreeNet` gekennzeichnet.

## Lizenz

Der Medikamentenwecker ist freie Software unter der [GNU General Public License v3.0](LICENSE).

Verwendete Bibliotheken stehen unter Apache-2.0 bzw. kompatiblen Lizenzen (u. a. AndroidX, Jetpack Compose, CameraX, Glance, [ZXing-cpp](https://github.com/zxing-cpp/zxing-cpp)). Logo und Bestätigungston sind Teil dieses Projekts und stehen ebenfalls unter der GPL-3.0.

---

**Medikamentenwecker** – Einfache, zuverlässige Medikamentenerinnerung und Einnahmekontrolle für Android.

Entwickelt mit Kotlin und Jetpack Compose.
