# Schreibwerkstatt Mobile (Android)

Nativer Android-Client (Kotlin/Compose) für die selbst-gehostete Server-App
[**schreibwerkstatt**](https://github.com/bedeberger/schreibwerkstatt). Schlanker
Schreib-/Diktat-Client: native Shell (Auth, Navigation, Sync, Audio, Lifecycle)
plus der **Focus-Editor als OTA-Bundle in einer WebView** — der Editor wird nie
in Kotlin nachgebaut, sondern zur Laufzeit vom Server geladen.

Android-Pendant zum macOS-Client `schreibwerkstatt-focuseditor`.

- **applicationId:** `ch.schreibwerkstatt.mobile`
- **min SDK:** 26 (Android 8) · **target/compile SDK:** 35
- **Stack:** Kotlin, Jetpack Compose, AndroidX WebKit, Retrofit/OkHttp +
  Kotlinx-Serialization, Room, DataStore + EncryptedSharedPreferences,
  MediaRecorder

## Leitprinzip

Der Editor ist Single Source of Truth im Mutterprojekt. Die App lädt ihn als
ZIP-Bundle (`GET /content/editor-bundle.zip`), entpackt es ins App-internal-
Storage und serviert es der WebView via `WebViewAssetLoader` same-origin. So
driftet der Editor nie gegen die Web-/Mac-Variante.

## Architektur

```
ui/            Compose-Screens + ViewModels (pairing, books, tree, editor, settings) + AppNav
editor/        EditorBridge (@JavascriptInterface ⇄ host.html)
bundle/        BundleManager (OTA-Download, ETag/304, Unzip)
audio/         DictationController (MediaRecorder → STT-Proxy)
data/
  net/         Retrofit-APIs, AuthInterceptor (Bearer + X-Client-Version), DTOs
  db/          Room (books, pages, sync_cursors, pending_writes)
  prefs/       SettingsStore (Server-URL, device_id) · TokenStore (verschlüsselt)
  repo/        ContentRepository + SyncEngine (Delta-Pull, Pending-Writes, 409/423)
App.kt         ServiceLocator (manuelles DI)
```

Native Navigation **und** der WebView-Editor laufen über dasselbe
`ContentRepository` (geteilter Room-Cache) — der Editor spricht nie direkt mit
dem Server.

## Server-Integration (verifiziert gegen das Mutterprojekt)

- **Auth:** Device-Token `swd_*`. Pairing über die Web-Login-Session: die App
  öffnet `<server>/` in einer WebView, der Nutzer meldet sich per Google-OIDC an
  und tippt „Gerät koppeln". Ein injiziertes `fetch('/me/device-tokens',
  {credentials:'include'})` zieht den Token aus der Session; der `plain_token`
  wird verschlüsselt abgelegt (`EncryptedSharedPreferences`). Danach: Header
  `Authorization: Bearer swd_…` auf allen Requests. 401 → Token verworfen →
  zurück zum Pairing.
- **Editor-Bridge:** `host.html` lädt `js/editor/focus/standalone.js` und ruft
  `mountStandaloneFocus({ mount, bridge })`. Die Bridge bedient den verifizierten
  Vertrag: `loadPage()` (aktuell gewählte Seite, **kein** pageId-Arg) und
  `savePage({ id, name, html })` — beide über die native Sync-Schicht.
- **Sync:** `GET /content/books/:id/sync?since=&since_id=&limit=` mit Cursor-
  Paginierung (`has_more`); Cursor in Room persistiert. Lokal-dirty Seiten werden
  beim Pull nicht überschrieben.
- **Speichern:** `PUT /content/pages/:id { html, device_id, source:"main" }`;
  409 `PAGE_CONFLICT` → Dialog „Server-Version laden / lokal behalten",
  423 `PAGE_LOCKED` → Hinweis. Offline → Pending-Write-Queue, später geflusht.
- **Diktat:** nur wenn `/config` → `stt.enabled`. Aufnahme als `audio/mp4` (AAC),
  POST als rohe Bytes an `POST /stt/transcribe?bookId=&pageId=`
  (kein Multipart, < 5 MB). Erkannter Text wird am Cursor eingefügt.

## Build

Voraussetzungen: Android Studio (Ladybug+) oder Android SDK 35 + JDK 17.

```bash
# Wrapper-JAR ggf. einmalig erzeugen (nicht eingecheckt):
gradle wrapper --gradle-version 8.11.1
# danach:
./gradlew assembleDebug
```

> **Hinweis:** `gradle/wrapper/gradle-wrapper.jar` ist nicht enthalten. Android
> Studio erzeugt ihn beim ersten Gradle-Sync automatisch; alternativ obiger
> `gradle wrapper`-Aufruf (benötigt ein lokal installiertes Gradle).

Beim ersten Start: Server-URL eingeben → koppeln. Self-hosted-Server ohne TLS
sind via `network_security_config` (Cleartext erlaubt) erreichbar — für
produktive Deployments HTTPS verwenden.

## Stand / v1

Enthalten: Pairing, Buchliste, Kapitel-/Seitenbaum, Editor-WebView (OTA-Bundle),
Diktat, Offline-Cache + Delta-Sync + Pending-Writes mit 409/423-Handling,
Settings (Server-URL, STT-Status, Gerät abmelden).

Bewusst nicht in v1: Buchorganizer/DnD, KI-Analysen/Jobs, Chat, Export, eigener
Editor-Nachbau. Diktat-Segmentierung ist v1-einfach (Push-to-Record mit
maxSegment-Cap); VAD/Stille-Schnitt ist eine spätere Ausbaustufe.
