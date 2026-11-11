# Checker-Schreck 🤢📱

Android-App für notorische Handychecker. Sie zählt jeden Check, analysiert dein Check-Verhalten
und macht das Handy **richtig hässlich**, wenn du zu lange dranhängst. Außer es ist gerade ein
Rezept offen, dann hat sie Verständnis. 🍳

## Was die App macht

| Funktion | Details |
|---|---|
| **Checks zählen** | Jedes Entsperren ist ein Check. Beim Entsperren kommt kurz „Check #23 heute · letzter vor 4 min“. |
| **Session messen** | Zeit am Stück. Kurz Bildschirm aus und wieder an zählt **nicht** als Pause (Standard: erst nach 2 min aus). |
| **Hässlich machen** | Grundstufe 1: Bei jeder Nutzung liegt ein Matsch-Overlay über allem. Nach 10 min am Stück Stufe 2, nach 15 min Stufe 3 (einstellbar). Ohne Grundstufe sieht das Handy bis zum Limit normal aus. |
| **Tageslimit** | Nach 120 min am Tag ist jede Nutzung sofort eine Stufe hässlicher. |
| **Check-Attacken** | 5 Checks in 15 min → Alarm und 10 min Strafe (eine Stufe hässlicher). Doppelt so viele → zwei Stufen. |
| **Analyse** | Checks heute, Reflex-Checks (< 60 s), Ø Abstand, längste Pause, Checks pro Stunde (Diagramm), Hotspot-Stunde, Vergleich mit den letzten 7 Tagen, Top-Apps, Urteil („Notorischer Checker“, „Goldfisch mit WLAN“ …). |
| **Rezept-Ausnahme** | Rezept-Apps werden am Namen erkannt (Chefkoch, Kitchen Stories, Cookidoo …). Die optionale Bedienungshilfe erkennt Rezepte auch im Browser (Wörter wie „Zutaten“, „Zubereitung“, „Portionen“). Zusätzlich gibt es den Kochmodus (Standard: 2× am Tag je 45 min). |
| **YouTube-Limit** | 20 min YouTube pro Tag (einstellbar, 0 = aus). 5 min vorher kommt eine Warnung. Danach ist YouTube sofort Stufe 2 (alle 5 min eine Stufe mehr) und die App wirft dich standardmäßig zurück auf den Startbildschirm. Gilt für die YouTube-App (auch Kids/ReVanced), nicht für YouTube im Browser. |
| **Check-Budget** | Erlaubte Checks pro Tag. Darüber wird es eine Stufe hässlicher, ab 50 % drüber zwei. Wird automatisch aus deinem Schnitt gesetzt oder von Hand in den Einstellungen. |
| **Automatisch zurückdrehen** | Jeden Tag um Mitternacht wird deine echte Nutzung der letzten 7 Tage ausgewertet. Tageslimit, Session-Limit, YouTube-Limit und Check-Budget werden auf „Schnitt minus 10 %“ gesetzt, wenn das strenger ist als bisher (höchstens 10 % pro Tag). Lockerer wird nichts von allein. Bewegt sich eine Woche nichts, wird trotzdem um 5 % gedreht. Untergrenzen: 30 min am Tag, 3 min Session, 5 min YouTube, 15 Checks. Startet nach 3 vollen Messtagen. |
| **Update-Suche** | Knopf „Nach Updates suchen“ lädt die neueste APK aus dem GitHub-Release und öffnet den Installer. Beim Öffnen der App wird höchstens alle 12 Stunden automatisch nachgesehen. |
| **Auslöser-Analyse** | Mit Benachrichtigungszugriff hält die App bei jedem Check fest, ob in den 90 Sekunden davor eine Benachrichtigung kam und von welcher App. Du siehst z. B. „38 % ohne Anlass, 31 % WhatsApp“ und einen Tipp, was du stummschalten solltest. Erfasst werden nur App und Zeitpunkt, nie der Inhalt. |
| **Schummel-Schutz** | Alles, was lockert (Limit hoch, Funktion aus, Überwachung stoppen, neue Ausnahme-App), braucht eine Hürde: einen langen Satz abtippen (Standard) oder 24 Stunden warten. Strenger stellen geht sofort. Wurde die Überwachung abgeschossen, wird die Nutzung aus dieser Zeit beim nächsten Start nachgetragen. |
| **Zeitfenster** | Nachtmodus (22 bis 6 Uhr): sofort Stufe 3 und Graustufen. Morgensperre: 30 min nach dem ersten Entsperren liegt ein Vollbild über allem (Telefon, Wecker, Ausnahme-Apps und eine Notfall-Freigabe gehen). Tagsüber (8 bis 18 Uhr) gilt nur das halbe Session-Limit. |
| **Doomscroll-Erkennung** | Wer in 2 Minuten mehr als 45 Sekunden scrollt, scrollt endlos: Die Session-Uhr läuft dann doppelt so schnell. Braucht die Bedienungshilfe. |
| **Wochenbericht** | Sonntags ab 18 Uhr als Benachrichtigung, jederzeit über „Wochenbericht“ abrufbar: Checks, aktive Zeit, YouTube, Scrollzeit, bester und schlechtester Tag, Auslöser, Vergleich mit der Woche davor. |
| **Widget** | Startbildschirm-Widget mit Checks, Restzeit, Session und YouTube-Rest. |
| **Ausnahme-Apps** | Eigene Liste, z. B. Navi. Telefonate sind immer ausgenommen. |

### Die Hässlich-Stufen

1. **Matsch** (Grundstufe, immer an): braun-grüne, pulsierende Tönung mit Streifen, oben ein kleiner Session-Timer.
2. **Kotz-Regenbogen**: wechselnde Farben, dicke Streifen, wackelnder Text. Mit adb-Freigabe wird zusätzlich das **ganze Handy grau**.
3. **Chaos**: Schachbrett in Pink, Giftgrün und Braun, gelbe Streifen, Flecken, Nerv-Vibration. Optional werden die Farben invertiert.

Ab Stufe 2 kommen dazu:

- **Schrumpfender Bildschirm**: schwarze Balken an allen Rändern, die pro Minute wachsen (bis 12 % oben
  und unten, 6 % links und rechts). Sie schlucken Berührungen.
- **Riesige Schrift**: braucht „Systemeinstellungen ändern“, in der App ohne PC erteilbar.
- **Zähe Animationen**: braucht die adb-Freigabe (siehe unten).

Das farbige Overlay lässt Berührungen durch, das Handy bleibt also benutzbar, nur eben hässlich. Die
Farben wechseln bewusst langsam und weich, ohne Stroboskop-Flackern. Schrift, Animationen und Farben
werden zurückgesetzt, sobald der Bildschirm ausgeht, eine Ausnahme gilt oder die Überwachung endet.

## Installation

1. Auf dem Handy die neueste APK herunterladen:
   **Releases → „Checker-Schreck (neueste APK)“ → `checker-schreck.apk`**
   (oder unter *Actions → letzter Lauf → Artifacts*).
2. Installieren und die Installation aus dieser Quelle erlauben.
3. App öffnen und die Berechtigungen erteilen:
   - **Nutzungszugriff** (Pflicht)
   - **Über anderen Apps einblenden** (Pflicht). Meldet Android „App was denied access“ /
     „Eingeschränkte Einstellung“: *Einstellungen → Apps → Checker-Schreck → ⋮ (oben rechts) →
     „Eingeschränkte Einstellungen zulassen“*, bestätigen, dann die Berechtigung nochmal erteilen.
     Das ⋮-Menü erscheint erst, nachdem man einmal versucht hat, die Berechtigung zu erteilen.
   - Benachrichtigungen, Akku-Optimierung aus (empfohlen)
   - **Rezept-Erkennung** (optional, Bedienungshilfe). Ist der Schalter ausgegraut (Android 13+):
     *App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“*, danach geht es.
4. **Überwachung starten**. Mit „Stufe 1/2/3“ kannst du dir die Hässlichkeit vorher ansehen.

### Optional: Graustufen und zähe Animationen (systemweit)

Wenn das ganze Handy grau und träge werden soll, einmalig am PC mit USB-Debugging:

```bash
adb shell pm grant app.checkerschreck android.permission.WRITE_SECURE_SETTINGS
```

Die App merkt sich deine ursprünglichen Farbeinstellungen und stellt sie wieder her, sobald du das
Handy weglegst oder ein Rezept öffnest.

### Grenzen des Schummel-Schutzes

Die App kann nicht verhindern, dass du sie deinstallierst, ihre Daten löschst oder ihr die
Berechtigungen entziehst. Sie meldet fehlende Berechtigungen und trägt unbeaufsichtigte Nutzung
nach, sobald sie wieder läuft. Mehr geht ohne Geräteverwaltung nicht.

## Selbst bauen

```bash
./gradlew assembleRelease
```

Dafür braucht es JDK 17 und das Android SDK (Platform 35). Jeder Push baut über GitHub Actions
automatisch eine neue APK und aktualisiert das Release `apk-latest`.

Die APK wird mit dem Schlüssel `app/signing.keystore` signiert, der im Repo liegt (Passwort
`checkerschreck`). So lässt sich jede neue Version als Update über die alte installieren. Für eine
private App ist das in Ordnung. Wer einen eigenen Schlüssel will, setzt `CS_KEYSTORE`,
`CS_KEYSTORE_PASSWORD`, `CS_KEY_ALIAS` und `CS_KEY_PASSWORD`.

## Datenschutz

Alle Nutzungsdaten bleiben auf dem Handy. Die App geht nur für die Update-Suche ins Internet
(Abruf von `version.json` und der APK aus dem GitHub-Release dieses Repos), es wird nichts
hochgeladen. Das Check-Protokoll (Zeitstempel) liegt im App-Speicher und wird nach 30 Tagen
gelöscht, die Tageszusammenfassungen nach 60 Tagen.
