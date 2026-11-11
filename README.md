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
| **Ausnahme-Apps** | Eigene Liste, z. B. Navi. Telefonate sind immer ausgenommen. |

### Die Hässlich-Stufen

1. **Matsch** (Grundstufe, immer an): braun-grüne, pulsierende Tönung mit Streifen, oben ein kleiner Session-Timer.
2. **Kotz-Regenbogen**: wechselnde Farben, dicke Streifen, wackelnder Text. Mit adb-Freigabe wird zusätzlich das **ganze Handy grau**.
3. **Chaos**: Schachbrett in Pink, Giftgrün und Braun, gelbe Streifen, Flecken, Nerv-Vibration. Optional werden die Farben invertiert.

Das Overlay lässt Berührungen durch, das Handy bleibt also benutzbar, nur eben hässlich. Die Farben
wechseln bewusst langsam und weich, ohne Stroboskop-Flackern.

## Installation

1. Auf dem Handy die neueste APK herunterladen:
   **Releases → „Checker-Schreck (neueste APK)“ → `checker-schreck.apk`**
   (oder unter *Actions → letzter Lauf → Artifacts*).
2. Installieren und die Installation aus dieser Quelle erlauben.
3. App öffnen und die Berechtigungen erteilen:
   - **Nutzungszugriff** (Pflicht)
   - **Über anderen Apps einblenden** (Pflicht)
   - Benachrichtigungen, Akku-Optimierung aus (empfohlen)
   - **Rezept-Erkennung** (optional, Bedienungshilfe). Ist der Schalter ausgegraut (Android 13+):
     *App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“*, danach geht es.
4. **Überwachung starten**. Mit „Stufe 1/2/3“ kannst du dir die Hässlichkeit vorher ansehen.

### Optional: Graustufen-Modus (systemweit)

Wenn das ganze Handy grau werden soll, einmalig am PC mit USB-Debugging:

```bash
adb shell pm grant app.checkerschreck android.permission.WRITE_SECURE_SETTINGS
```

Die App merkt sich deine ursprünglichen Farbeinstellungen und stellt sie wieder her, sobald du das
Handy weglegst oder ein Rezept öffnest.

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

Alles bleibt auf dem Handy. Die App hat keine Internet-Berechtigung. Das Check-Protokoll
(Zeitstempel) liegt im App-Speicher und wird nach 30 Tagen gelöscht.
