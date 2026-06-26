# schreibwerkstatt-mobile

Native **Android-Client** (Kotlin/Jetpack Compose) zur Web-App **schreibwerkstatt**. Die App ist ein dünner, offline-fähiger Client: Sie hält **keine** eigene Geschäftslogik für Buchinhalte, sondern liest/schreibt ausschliesslich über die HTTP-API des Servers und cached lokal in Room. Inhalte (Bücher/Kapitel/Seiten), Authentifizierung, STT-Diktat und der Focus-Editor leben serverseitig — die App rendert und synchronisiert sie nur.

**Mutterprojekt:** [`/Users/bd/ClaudeProjects/schreibwerkstatt`](/Users/bd/ClaudeProjects/schreibwerkstatt) (Node.js/SQLite-Server + Web-Frontend). Dort: `CLAUDE.md`, `README.md` (Deployment/Env), `docs/`. Die Server-API ist der **Vertrag** dieser App — siehe Harte Regel „Server-API gehört dem Mutterprojekt".

## Build & Test

```
./gradlew assembleDebug      # Debug-APK bauen (Standard-Test nach jeder Änderung)
./gradlew :app:compileDebugKotlin   # schneller reiner Compile-Check
./gradlew lint                # Android-Lint
```

- **Pflicht: Nach jeder Code-Änderung `./gradlew assembleDebug` ausführen** und den Erfolg verifizieren, bevor die Änderung als fertig gilt. Schlägt der Build fehl, zuerst fixen.
- **Toolchain:** Compile-Target ist **JDK 17** (`compileOptions`/`kotlinOptions`). Gradle 8.11.1 selbst läuft nur auf **JDK 17–23** — ein installiertes JDK 24+ als `JAVA_HOME` lässt den Wrapper scheitern. Bei Bedarf `JAVA_HOME` auf ein JDK 17/21 zeigen (z.B. das von Android Studio gebündelte JBR).
- Android SDK aus `local.properties` (`sdk.dir`); nicht ins VCS committen.

## Versionierung

- **Einzige Quelle der Wahrheit:** [`version.properties`](version.properties) im Projekt-Root (committet). Enthält `versionName` (nutzersichtbar, SemVer `major.minor.patch`) und `versionCode` (monoton steigende Ganzzahl). [app/build.gradle.kts](app/build.gradle.kts) liest die Datei beim Konfigurieren ein und setzt daraus `defaultConfig.versionName`/`versionCode` sowie `BuildConfig.CLIENT_VERSION` (`"android/<versionName>"`, geht als `X-Client-Version`-Header an den Server). **Version nie direkt in `build.gradle.kts` hartcodieren** — nur `version.properties` ändern.
- **Bump-Regel:** Die Version wird **nicht automatisch** erhöht. Nur **auf ausdrückliche Anweisung des Users** („bumpe die Version", „neue Version" o.Ä.) wird in `version.properties` der `versionName` nach SemVer angehoben (patch/minor/major je nach Anweisung; im Zweifel patch) **und** der `versionCode` um genau 1 erhöht. Danach `./gradlew assembleDebug` zur Verifikation. Beide Felder immer gemeinsam bumpen.

## Architektur

- **Manuelles DI** über `ServiceLocator` ([App.kt](app/src/main/java/ch/schreibwerkstatt/mobile/App.kt)) — kein Hilt. App-weite Singletons (Settings, Token, Network, DB, Repository, BundleManager) lazy; ViewModels ziehen Abhängigkeiten über `context.locator` + eigene `factory`.
- **UI:** Jetpack Compose + Material3, Navigation-Compose ([ui/AppNav.kt](app/src/main/java/ch/schreibwerkstatt/mobile/ui/AppNav.kt)). MVVM: `ui/<feature>/{Screen,ViewModel}.kt`. Screens: `pairing`, `books`, `tree`, `editor`, `settings`.
- **Persistenz:** Room (`data/db/`) als Offline-Cache; DataStore (`data/prefs/SettingsStore`) für nicht-geheime Config; EncryptedSharedPreferences (`data/prefs/TokenStore`) für das Geräte-Token.
- **Netzwerk:** Retrofit + OkHttp + kotlinx.serialization (`data/net/`). `NetworkClient` baut Retrofit pro (variabler) Basis-URL und cached es. DTOs in `data/net/dto/Dtos.kt`.
- **Editor:** Der Focus-Editor läuft als Web-Bundle in einer WebView (`editor/`, `bundle/`, `assets/editor-host/host.html`) — siehe Harte Regeln unten.

## Harte Regeln

- **`ContentRepository` ist der einzige Lese-/Schreib-Eintrittspunkt für Buchinhalte.** Native UI **und** der WebView-Editor (über `EditorBridge`) gehen über [ContentRepository](app/src/main/java/ch/schreibwerkstatt/mobile/data/repo/ContentRepository.kt) — **nie** direkt gegen den Server. Nur so teilen beide denselben Room-Cache. Online-Flush + Delta-Pull stecken im [SyncEngine](app/src/main/java/ch/schreibwerkstatt/mobile/data/repo/SyncEngine.kt). (Spiegelt die Content-Store-Facade-Regel des Mutterprojekts.)

- **Offline-first / Pending-Write-Queue.** Editor-Saves persistieren lokal als `dirty`, queuen einen `PendingWriteEntity` und versuchen dann best-effort den Online-Flush (`SaveResult` = `Saved`/`Queued`/`Conflict`/`Locked`). Der Delta-Pull (`GET …/sync`) darf **lokal-dirty Seiten nie mit Server-Stand überschreiben** — der Pending-Write hat Vorrang bis Flush/Konflikt. Konflikt = HTTP **409** (`PAGE_CONFLICT`), Lock = **423** (`PAGE_LOCKED`); Body via `errorBody`.

- **Auth-Modell.** Geräte-Token (`swd_…`) wird verschlüsselt im `TokenStore` abgelegt. Danach setzt `AuthInterceptor` auf **jedem** Request `Authorization: Bearer swd_…` + `X-Client-Version`. **401 → Token verwerfen → `isPaired=false` → Navigation zurück zum Pairing** (siehe `AppNav`). Token nie loggen, nie in DataStore/Klartext ablegen.

- **Pairing = manuelle Token-Eingabe (wie der Mac-Client).** `PairingScreen` ist ein reines Formular: Server-Adresse + ein am Server (Web-UI „Einstellungen → Geräte", `/me/device-tokens`) **vorab erzeugtes** Device-Token (`swd_…`). `PairingViewModel.couple()` verifiziert das Token über `NetworkClient.verifyToken()` (`GET …/config`, liegt hinter dem Auth-Guard) und legt es **erst bei Erfolg** im `TokenStore` ab — kein WebView/OIDC-Flow, keine `POST /me/device-tokens`-Ausstellung aus der App. **Why:** Google-OIDC im WebView ist unzuverlässig (`disallowed_useragent`); die manuelle Eingabe entkoppelt das Pairing vom Login-Flow.

- **`device_id` ≠ Auth-Token.** Die stabile Installations-UUID (`SettingsStore.deviceId()`) dient nur Konflikt-/Presence-Zuordnung (`PUT …/pages/:id { device_id }`, `device-ping`) und liegt bewusst getrennt vom geheimen Token.

- **Editor-Bundle (OTA) + Same-Origin-WebView.** `BundleManager` lädt `GET /content/editor-bundle.zip` (mit `If-None-Match`/`ETag`, 304 = kein Neuentpacken) ins App-Storage; `host.html` wird aus den Assets dazukopiert. Der `WebViewAssetLoader` serviert das Bundle unter `https://appassets.androidplatform.net/`, damit Same-Origin greift und relative Imports des Bundles funktionieren. **Zip-Slip-Schutz** beim Entpacken nicht entfernen. JS↔native nur über `EditorBridge` (`window.SWHost`), Ergebnisse asynchron via `window.__sw.resolve/reject`.

- **STT-Diktat sendet rohe Audio-Bytes (kein Multipart).** `DictationController` nimmt als `audio/mp4` (AAC, 16 kHz) auf und POSTet die Bytes an `POST /stt/transcribe`; der `Content-Type` trägt den Audio-MIME. Segment < 5 MB (Server-Limit). Erkannter Text wird verbatim über den normalen Save-Pfad eingefügt.

- **Cleartext-HTTP ist absichtlich erlaubt** ([network_security_config.xml](app/src/main/res/xml/network_security_config.xml)), weil die Server-URL self-hosted/variabel ist (LAN ohne TLS). Für produktive Deployments HTTPS verwenden — diese Begründung bleibt, nicht „aufräumen".

- **i18n:** User-sichtbare Strings als `@string/`-Ressourcen ([res/values/strings.xml](app/src/main/res/values/strings.xml)), nicht hartcodiert.

- **Doku-Stil dieser Datei:** Nur **aktueller Stand**. Keine Historie, kein „vorher war …", keine Migrationsnarrative — dafür gibt es `git log`. Begründungen (**Why**) für aktuelle Constraints bleiben.

## Server-API gehört dem Mutterprojekt

Die App ist reiner Konsument dieser Endpunkte (Quelle: `routes/`, `lib/` im Mutterprojekt):

| Endpunkt | App-Seite | Server-Quelle |
|---|---|---|
| `GET config` | `ConfigApi` · Pairing-Token-Check (`NetworkClient.verifyToken`) | `routes/…` (`/config`), `lib/device-auth.js`, `db/device-tokens.js` |
| `GET content/books` · `…/{id}/tree` | `ContentApi` | `routes/content.js` |
| `GET content/books/{id}/sync` | `SyncEngine` | `routes/content.js` (Delta-Pull, `since`/`since_id`/`limit`) |
| `GET`/`PUT content/pages/{id}` | `ContentRepository` | `routes/content.js` (409/423) |
| `POST content/books/{id}/device-ping` | Presence | `routes/content.js` |
| `POST stt/transcribe` | `DictationController` | `routes/stt.js` ([docs/stt.md](/Users/bd/ClaudeProjects/schreibwerkstatt/docs/stt.md)) |
| `GET content/editor-bundle.zip` | `BundleManager` | `lib/editor-bundle.js`, `routes/content.js` ([docs/focus-editor.md](/Users/bd/ClaudeProjects/schreibwerkstatt/docs/focus-editor.md)) |

- **Server-Änderungen NICHT selbst vornehmen.** Braucht eine Aufgabe eine neue/geänderte Server-Route, einen anderen Vertrag (Felder, Statuscodes), eine Config-Flag o.Ä., dann **nicht** im Mutterprojekt editieren, sondern dem User einen **fertigen Prompt-Vorschlag** liefern, den er im Mutterprojekt-Repo (mit dessen `CLAUDE.md`/Regeln) ausführen kann. Format:

  > **Prompt-Vorschlag fürs Mutterprojekt (`schreibwerkstatt`):**
  > „<konkret, was an welcher Route/Datei geändert/ergänzt werden soll, inkl. erwartetem Request/Response-Vertrag, Statuscodes, Auth-Scope und Begründung aus Mobile-Sicht>"

  Grund: Das Mutterprojekt hat eigene Harte Regeln (Content-Store-Facade, Job-Queue, i18n, Sync-Proxy-Ausnahmen). Änderungen dort gehören in dessen Kontext, nicht blind aus diesem Repo.
- **Vertrags-Drift:** Weicht ein DTO (`data/net/dto/Dtos.kt`) von der tatsächlichen Server-Antwort ab, ist die Server-Seite die Wahrheit — DTO anpassen und ggf. Prompt-Vorschlag fürs Mutterprojekt, falls dort etwas inkonsistent ist.