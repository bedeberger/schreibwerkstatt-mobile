package ch.schreibwerkstatt.mobile.data.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Bücher / Baum ───────────────────────────────────────────────────────────

@Serializable
data class BookDto(
    val id: Long,
    val name: String,
    val role: String? = null,
    val owner_email: String? = null,
    val buchtyp: String? = null,
)

@Serializable
data class TreeDto(
    val chapters: List<ChapterNodeDto> = emptyList(),
    val topPages: List<TreePageDto> = emptyList(),
)

@Serializable
data class ChapterNodeDto(
    val id: Long,
    val name: String,
    val position: Int? = null,
    val parent_chapter_id: Long? = null,
    /** Verschachtelte Unterkapitel (Server-Schlüssel `subchapters`, gleiche Shape). */
    val subchapters: List<ChapterNodeDto> = emptyList(),
    /** Manche Tree-Builder hängen Seiten direkt unter den Kapitelknoten. */
    val pages: List<TreePageDto> = emptyList(),
)

@Serializable
data class TreePageDto(
    val id: Long,
    val name: String,
    val chapter_id: Long? = null,
)

// ── Seiten ──────────────────────────────────────────────────────────────────

@Serializable
data class PageDto(
    val id: Long,
    val name: String? = null,
    val chapter_id: Long? = null,
    val html: String? = null,
    val updated_at: String? = null,
)

/**
 * Neue Seite anlegen (`POST content/pages`). Mindestens eines von `book_id`/
 * `chapter_id` muss gesetzt sein; `name` ist Pflicht. Für Tagebuch-Einträge ist
 * `name` der ISO-Tag `YYYY-MM-DD`. Antwort = [PageDto] (Extra-Felder ignoriert).
 */
@Serializable
data class CreatePageRequest(
    val book_id: Long? = null,
    val chapter_id: Long? = null,
    val name: String,
    val html: String? = null,
)

/**
 * Neues Kapitel anlegen (`POST content/chapters`). `book_id` + `name` Pflicht;
 * `parent_chapter_id` für verschachtelte (Monats-)Kapitel, `position` für die
 * Sortierung (Jahr = Jahrzahl, Monat = 1–12). Antwort = [ChapterNodeDto].
 */
@Serializable
data class CreateChapterRequest(
    val book_id: Long,
    val name: String,
    val position: Int? = null,
    val parent_chapter_id: Long? = null,
)

@Serializable
data class SavePageRequest(
    val html: String,
    val device_id: String,
    val source: String = "main",
    /**
     * Optimistic-Concurrency-Guard: zuletzt bekannter Server-`updated_at`, auf dem
     * dieser Edit basiert. Stimmt der DB-Stand nicht überein → Server liefert 409
     * PAGE_CONFLICT statt last-write-wins. null = kein Guard (z.B. unbekannte Basis).
     */
    val expected_updated_at: String? = null,
)

/** 409 PAGE_CONFLICT-Body. */
@Serializable
data class PageConflictDto(
    val error_code: String? = null,
    val server_updated_at: String? = null,
    val server_editor_email: String? = null,
    val server_editor_name: String? = null,
)

/** 423 PAGE_LOCKED-Body. */
@Serializable
data class PageLockedDto(
    val error_code: String? = null,
    val locked_by_email: String? = null,
    val expires_at: String? = null,
)

// ── Delta-Sync ──────────────────────────────────────────────────────────────

@Serializable
data class SyncResponse(
    val now: String,
    val pages: List<SyncPageDto> = emptyList(),
    val has_more: Boolean = false,
    val cursor: SyncCursorDto? = null,
)

@Serializable
data class SyncPageDto(
    val page_id: Long,
    val page_name: String? = null,
    val chapter_id: Long? = null,
    val updated_at: String? = null,
    val html: String? = null,
)

@Serializable
data class SyncCursorDto(
    val since: String? = null,
    val since_id: Long? = null,
)

// ── /config (nur STT-relevanter Teil) ───────────────────────────────────────

@Serializable
data class ConfigDto(
    val stt: SttConfigDto? = null,
)

@Serializable
data class SttConfigDto(
    val enabled: Boolean = false,
    val provider: String? = null,
    val vad: SttVadDto? = null,
)

@Serializable
data class SttVadDto(
    val silenceMs: Long = 800,
    val threshold: Double = 0.015,
    val maxSegmentS: Int = 30,
)

// ── STT ─────────────────────────────────────────────────────────────────────

@Serializable
data class TranscribeResponse(
    val text: String = "",
)

/** Generischer Fehler-Body ({ error_code } oder { error }). */
@Serializable
data class ApiErrorDto(
    val error_code: String? = null,
    val error: String? = null,
    val detail: String? = null,
) {
    val code: String? get() = error_code ?: error
}
