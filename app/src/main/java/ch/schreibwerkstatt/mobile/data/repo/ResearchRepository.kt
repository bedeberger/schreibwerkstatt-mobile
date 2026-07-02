package ch.schreibwerkstatt.mobile.data.repo

import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.net.dto.CreateResearchRequest
import ch.schreibwerkstatt.mobile.data.net.dto.ResearchItemDto
import ch.schreibwerkstatt.mobile.data.net.dto.ResearchUrlDto
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore

/**
 * Zugriff auf das buchweite Recherche-/Wissensboard (`routes/research.js`).
 *
 * Bewusst **eigenständig** neben [ContentRepository]: Recherche-Items sind keine
 * Buchinhalte (Seiten/Kapitel) und laufen serverseitig nicht über die Content-
 * Store-Facade — die „ContentRepository = einziger Content-Eintrittspunkt"-Regel
 * betrifft sie nicht. **Online-first** (v1): kein Room-Cache, keine Pending-Queue.
 * Ohne Verbindung schlägt der Aufruf fehl und die UI meldet das.
 */
class ResearchRepository(
    private val net: NetworkClient,
    private val settings: SettingsStore,
) {
    private suspend fun baseUrl(): String =
        settings.serverBaseUrlOnce() ?: error("Keine Server-URL konfiguriert")

    /** Recherche-Items eines Buchs (angeheftete zuerst, dann nach `updated_at`). */
    suspend fun list(bookId: Long): Result<List<ResearchItemDto>> = runCatching {
        net.research(baseUrl()).list(bookId)
    }

    /**
     * Recherche-Item vom Typ `link` anlegen. Mindestens eines von [url]/[title]/
     * [note] muss nicht-leer sein (sonst 400 `EMPTY` vom Server). Leere Felder
     * werden weggelassen.
     */
    suspend fun createLink(
        bookId: Long,
        url: String?,
        title: String?,
        note: String?,
    ): Result<ResearchItemDto> = runCatching {
        val cleanUrl = url?.trim()?.takeIf { it.isNotEmpty() }
        val urls = cleanUrl?.let { listOf(ResearchUrlDto(url = it)) } ?: emptyList()
        val resp = net.research(baseUrl()).create(
            CreateResearchRequest(
                book_id = bookId,
                kind = "link",
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                body = note?.trim()?.takeIf { it.isNotEmpty() },
                urls = urls,
            )
        )
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Leere Antwort")
    }
}
