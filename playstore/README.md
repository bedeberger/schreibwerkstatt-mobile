# Play Store Publishing — Schreibwerkstatt (Android)

Arbeitsmappe für den Play-Console-Upload; hier liegen die Console-Inhalte.

## Build für die Console

```
./gradlew bundlePlayRelease
# → app/build/outputs/bundle/playRelease/app-play-release.aab
```

**Zwingend der `play`-Flavor.** Der `github`-Flavor enthält das In-App-Update
(`REQUEST_INSTALL_PACKAGES` + Installer-Intent) und wäre ein Verstoss gegen Googles
„Device and Network Abuse" — siehe CLAUDE.md „Vertriebskanäle". Gegenprobe am fertigen AAB:

```
unzip -p …/app-play-release.aab base/dex/classes.dex | strings | grep -c package-archive   # muss 0 sein
```

Vor dem Build prüfen, dass `demo.properties` und `keystore.properties` im Root liegen
(sonst fehlt der Demo-Button bzw. ist das AAB unsigniert).

## Assets (`assets/`)
| Datei | Verwendung | Format |
|---|---|---|
| `icon-512.png` | App-Icon (Store) | 512×512, deckend, aus Marken-SVG gerendert |
| `feature-graphic-1024x500.png` | Feature-Graphic | 1024×500 |

**Fehlt noch — Screenshots:** Play verlangt ≥ 2 Phone-Screenshots (16:9 oder 9:16,
mind. 320 px Kante). Die müssen aus der echten App kommen (Emulator/Gerät) —
nicht generierbar. Vorschlag: Books-Liste, Tree-Ansicht, Editor mit Diktat.

## Datenschutzerklärung
**Datenschutz-URL für die Console:** `https://demo.schreibwerkstatt.app/datenschutz`
(live, aus `routes/public.js` des Mutterprojekts). Sie deckt Diktat, Geräte-Token und
Offline-Cache ab, spricht aber bislang nur vom **macOS-Client** — der Android-Abschnitt
fehlt und muss im Mutterprojekt ergänzt werden.

`datenschutz-android.html` ist der Textbaustein dafür (Geräte-/Mikrofon-/Offline-Cache-
Abschnitte für den Android-Client präzisiert), keine separat zu hostende Seite.

## Data-Safety-Formular (so ausfüllen)
- **Erhebt/teilt die App Daten?** Ja, erhoben (an den vom Nutzer gewählten Server übertragen).
- **Datenarten:**
  - *Persönlich*: E-Mail-Adresse, Name (Kontodaten).
  - *App-Inhalte*: vom Nutzer erstellte Texte/Bücher.
  - *Audio*: Sprachaufnahmen (nur bei aktivem Diktat).
  - *App-Aktivität / Geräte-IDs*: Installations-Kennung für Sync-Zuordnung; Server-Logs (IP/Zeit/User-Agent).
- **Verschlüsselung bei Übertragung:** über HTTPS ja; im self-hosted LAN ggf. unverschlüsselt → in der App-Beschreibung/Policy transparent gemacht.
- **Löschung:** Nutzer kann Konto-/Datenlöschung per E-Mail verlangen.
- **Kein** Werbe-/Analytics-Sharing, **kein** Tracking, **keine** Datenweitergabe zu Werbezwecken.
- KI-Verarbeitung (optional, Standard Anthropic/USA) im Formular als Datenübertragung/Drittland deklarieren — Detail in der Policy, Abschnitt 7.

## Berechtigungen (Begründung, falls nachgefragt)
- `RECORD_AUDIO` — Diktatfunktion (Speech-to-Text), nur bei aktiver Nutzung.
- `INTERNET` / `ACCESS_NETWORK_STATE` — Synchronisierung mit dem Server.
- `VIBRATE` — taktiles Feedback bei Diktat-Start/-Stop.
- `WAKE_LOCK` / `RECEIVE_BOOT_COMPLETED` / `FOREGROUND_SERVICE` — von WorkManager
  (periodischer Hintergrund-Pull) eingezogen, nicht selbst deklariert.
- `REQUEST_INSTALL_PACKAGES` ist im Play-Build **nicht** enthalten (nur `github`-Flavor).

## App-Zugriff / Reviewer-Login (PFLICHT, sonst Ablehnung)
Die App ist ein gepairter, self-hosted Client → der Google-Reviewer kommt ohne
erreichbaren Server + gültiges Geräte-Token an keinen Inhalt. Im Abschnitt
**„App-Zugriff“** hinterlegen:
- Demo-Server-Adresse: **`https://demo.schreibwerkstatt.app`**,
- ein dort vorab erzeugtes Test-Geräte-Token (`swd_…`),
- Pairing-Anleitung: App öffnen → Server-Adresse + Token eingeben → Koppeln.

**Zugangsdaten** (Demo-Konto, Web-Passwort, Geräte-Token) stehen in
`playstore/reviewer-access.local.md` — gitignored, weil dieses Repo öffentlich ist.
Token/Passwort deshalb **nie** in dieser Datei, im Listing oder in Screenshots.

**In der App:** Der Pairing-Screen hat einen Button **„Demo ausprobieren"**, der
Adresse + Demo-Token selbst einträgt und koppelt — der Reviewer muss nichts abtippen.
Die Werte kommen beim Build aus `demo.properties` im Repo-Root (gitignored, Vorlage
`demo.properties.template`); ohne diese Datei fehlt der Button im Build. **Vor jedem
Release-Build für die Console prüfen, dass `demo.properties` vorhanden und gültig ist.**
Trotzdem im Console-Formular zusätzlich Adresse + Token hinterlegen (Fallback).

## Store-Listing — Texte (Entwurf)

**App-Name:** Schreibwerkstatt

**Kurzbeschreibung (max. 80 Zeichen):**
> Schreiben, diktieren und synchronisieren — dein Schreibprojekt auf dem Android-Gerät.

**Vollständige Beschreibung (Entwurf):**
> Schreibwerkstatt ist der Android-Client zur gleichnamigen Schreib-Plattform.
> Schreibe an deinen Büchern, Kapiteln und Seiten — online wie offline. Die App
> hält deine Texte lokal vor und synchronisiert sie mit deinem eigenen
> Schreibwerkstatt-Server.
>
> • Offline-fähig: Änderungen werden lokal gespeichert und automatisch synchronisiert, sobald wieder Verbindung besteht.
> • Diktat: Texte per Sprache erfassen — die Aufnahme wird zur Erkennung an deinen Server übermittelt.
> • Fokus-Editor: ablenkungsfreies Schreiben.
> • Eigener Server: du verbindest die App mit deiner eigenen Instanz; deine Inhalte bleiben unter deiner Kontrolle.
>
> Voraussetzung: Zugang zu einem Schreibwerkstatt-Server und ein dort erzeugtes Geräte-Token.

## Noch offen (nicht in dieser Mappe lösbar)
1. **Closed Testing zuerst** — persönliche Accounts brauchen eine Mindest-Testerzahl
   über 14 Tage, bevor Production freigeschaltet wird (aktuelle Zahl in der Console
   gegenprüfen, Google hat sie seither gesenkt).
2. **Screenshots** aus der echten App — ≥ 2 Phone-Screenshots (16:9 oder 9:16,
   min. 320 px Kante). Vorschlag: Books-Liste, Tree-Ansicht, Editor mit Diktat.
3. **Play-Release-AAB auf einem echten Gerät smoke-testen** — Release-Builds laufen
   minifiziert (R8), Debug nicht. Per `bundletool build-apks --local-testing`
   installieren und Pairing → Buch → Editor → Diktat → Rechtschreibung → Sync durchspielen.
4. **Datenschutz-Seite um den Android-Client ergänzen** — `https://demo.schreibwerkstatt.app/datenschutz`
   ist live, erwähnt aber nur den macOS-Client. Gehört dem Mutterprojekt; die Bausteine
   liegen in `datenschutz-android.html`.
5. **Konto-Löschung**: Play fragt einen Lösch-Pfad ab. Die App legt selbst keine Konten
   an (nur Token-Pairing) — im Formular entsprechend deklarieren.

**Erledigt:** Demo-Server (`https://demo.schreibwerkstatt.app`) + Reviewer-Token,
Demo-Button in der App, Icon/Feature-Graphic, `play`-Flavor ohne Selbst-Update,
targetSdk 36, signiertes AAB baut.
