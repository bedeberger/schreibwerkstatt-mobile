# Play Store Publishing — Schreibwerkstatt (Android)

Arbeitsmappe für den Play-Console-Upload. Build-seitig ist die App bereit
(`./gradlew bundleRelease` → signiertes AAB); hier liegen die Console-Inhalte.

## Assets (`assets/`)
| Datei | Verwendung | Format |
|---|---|---|
| `icon-512.png` | App-Icon (Store) | 512×512, deckend, aus Marken-SVG gerendert |
| `feature-graphic-1024x500.png` | Feature-Graphic | 1024×500 |

**Fehlt noch — Screenshots:** Play verlangt ≥ 2 Phone-Screenshots (16:9 oder 9:16,
mind. 320 px Kante). Die müssen aus der echten App kommen (Emulator/Gerät) —
nicht generierbar. Vorschlag: Books-Liste, Tree-Ansicht, Editor mit Diktat.

## Datenschutzerklärung
`datenschutz-android.html` — hostbar (z. B. unter `/datenschutz-android` des Servers).
Die in der Play Console verlangte **Datenschutz-URL** zeigt auf diese Seite.
Inhaltlich aus der bestehenden Erklärung des Mutterprojekts abgeleitet, Geräte-/
Mikrofon-/Offline-Cache-Abschnitte für den Android-Client präzisiert.

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

## App-Zugriff / Reviewer-Login (PFLICHT, sonst Ablehnung)
Die App ist ein gepairter, self-hosted Client → der Google-Reviewer kommt ohne
erreichbaren Server + gültiges Geräte-Token an keinen Inhalt. Im Abschnitt
**„App-Zugriff“** hinterlegen:
- erreichbare Demo-Server-Adresse (öffentlich, **HTTPS**),
- ein vorab erzeugtes Test-Geräte-Token (`swd_…`),
- Pairing-Anleitung: App öffnen → Server-Adresse + Token eingeben → Koppeln.

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
1. **Closed Testing zuerst** — persönliche Accounts (nach Nov 2023) brauchen 20 Tester / 14 Tage vor Production.
2. **Screenshots** aus der echten App.
3. **Öffentlich erreichbarer Demo-Server (HTTPS)** für Reviewer + Test-Token.
4. **Datenschutz-URL** live schalten (HTML hosten).
