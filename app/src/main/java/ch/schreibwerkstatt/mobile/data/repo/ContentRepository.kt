package ch.schreibwerkstatt.mobile.data.repo

import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.db.BookEntity
import ch.schreibwerkstatt.mobile.data.db.PageEntity
import ch.schreibwerkstatt.mobile.data.db.PendingWriteEntity
import ch.schreibwerkstatt.mobile.data.net.DevicePingRequest
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.net.dto.ApiErrorDto
import ch.schreibwerkstatt.mobile.data.net.dto.BookDto
import ch.schreibwerkstatt.mobile.data.net.dto.ChapterNodeDto
import ch.schreibwerkstatt.mobile.data.net.dto.CreateChapterRequest
import ch.schreibwerkstatt.mobile.data.net.dto.CreatePageRequest
import ch.schreibwerkstatt.mobile.data.net.dto.PageConflictDto
import ch.schreibwerkstatt.mobile.data.net.dto.PageLockedDto
import ch.schreibwerkstatt.mobile.data.net.dto.SavePageRequest
import ch.schreibwerkstatt.mobile.data.net.dto.TreeDto
import ch.schreibwerkstatt.mobile.data.net.dto.TreePageDto
import java.util.Locale
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import okhttp3.ResponseBody
import retrofit2.Response

/**
 * Zentrale Schreib-/Lese-Schicht. Native Navigation UND der WebView-Editor
 * laufen hierüber (NICHT direkt gegen den Server) — so teilen sie denselben
 * Room-Cache. Online-Flush + Delta-Pull stecken im [SyncEngine].
 */
class ContentRepository(
    private val db: AppDatabase,
    private val net: NetworkClient,
    private val settings: SettingsStore,
) {
    private suspend fun baseUrl(): String =
        settings.serverBaseUrlOnce() ?: error("Keine Server-URL konfiguriert")

    private val sync by lazy { SyncEngine(db, net, settings) }

    // ── Bücher ───────────────────────────────────────────────────────────────

    fun observeBooks(): Flow<List<BookEntity>> = db.bookDao().observeAll()

    suspend fun refreshBooks(): Result<Unit> = runCatching {
        val books = net.content(baseUrl()).books()
        db.bookDao().upsertAll(books.map { it.toEntity() })
        db.bookDao().deleteMissing(books.map { it.id })
    }

    suspend fun tree(bookId: Long): Result<TreeDto> = runCatching {
        net.content(baseUrl()).tree(bookId)
    }

    /** Buch-Metadaten aus dem lokalen Cache (u.a. `buchtyp`). */
    suspend fun bookById(bookId: Long): BookEntity? = db.bookDao().byId(bookId)

    // ── Tagebuch: Eintrag für ein Datum anlegen/öffnen ────────────────────────

    /**
     * Liefert die Page-ID für den Tagebuch-Eintrag des Tages `dateIso`
     * (`YYYY-MM-DD`). Existiert bereits eine Seite mit diesem Datum, wird deren
     * ID zurückgegeben (keine Duplikate). Sonst wird die Seite serverseitig
     * angelegt — eingeordnet ins Jahr-Kapitel (`YYYY`) und, falls vorhanden,
     * ins passende Monats-Unterkapitel. Spiegelt die Logik des Web-Clients
     * (`public/js/book/diary-calendar.js`).
     *
     * **Online-only:** Das Anlegen braucht die vom Server vergebene ID; offline
     * schlägt der Aufruf fehl (kein Queuing). Editieren danach läuft normal
     * offline-fähig über [savePage].
     */
    suspend fun createDiaryEntry(bookId: Long, dateIso: String): Result<Long> = runCatching {
        require(DIARY_DATE_RE.matches(dateIso)) { "Ungültiges Datum: $dateIso" }
        val api = net.content(baseUrl())
        val tree = api.tree(bookId)

        // Schon vorhanden? → bestehende Seite öffnen statt duplizieren.
        collectPages(tree).firstOrNull { diaryDateOf(it.name) == dateIso }?.let {
            return@runCatching it.id
        }

        val year = dateIso.substring(0, 4).toInt()
        val month = dateIso.substring(5, 7).toInt()
        val yearChapterId = ensureYearChapter(api, bookId, tree, year)
        val chapterId = resolveMonthChapter(api, bookId, tree, yearChapterId, year, month)

        val resp = api.createPage(
            CreatePageRequest(book_id = bookId, chapter_id = chapterId, name = dateIso, html = "<p></p>")
        )
        if (!resp.isSuccessful) error("createPage HTTP ${resp.code()}")
        resp.body()?.id ?: error("createPage lieferte keine ID")
    }

    /** Top-Level-Jahr-Kapitel `YYYY` finden oder anlegen; liefert dessen ID. */
    private suspend fun ensureYearChapter(
        api: ch.schreibwerkstatt.mobile.data.net.ContentApi,
        bookId: Long,
        tree: TreeDto,
        year: Int,
    ): Long {
        val yearStr = year.toString()
        tree.chapters.firstOrNull { it.name == yearStr && it.parent_chapter_id == null }
            ?.let { return it.id }
        val resp = api.createChapter(
            CreateChapterRequest(book_id = bookId, name = yearStr, position = year)
        )
        if (!resp.isSuccessful) error("createChapter (Jahr) HTTP ${resp.code()}")
        return resp.body()?.id ?: error("createChapter (Jahr) lieferte keine ID")
    }

    /**
     * Kapitel-ID, in die der Eintrag gehört. Heuristik wie im Web:
     * - Jahr-Kapitel hat keine Unterkapitel → Jahr-Kapitel selbst.
     * - Unterkapitel da, aber keine monatsartigen → Jahr-Kapitel (fremdes Schema).
     * - Monatsartige Unterkapitel da, passender Monat dabei → dieses.
     * - Monatsartige da, Monat fehlt → neues Monats-Kapitel anlegen.
     */
    private suspend fun resolveMonthChapter(
        api: ch.schreibwerkstatt.mobile.data.net.ContentApi,
        bookId: Long,
        tree: TreeDto,
        yearChapterId: Long,
        year: Int,
        month: Int,
    ): Long {
        val subs = yearChapterSubs(tree, yearChapterId)
        if (subs.isEmpty()) return yearChapterId

        val monthSubs = subs.mapNotNull { sub ->
            val m = parseMonthName(sub.name)
                ?: sub.position?.takeIf { it in 1..12 }
            m?.let { it to sub }
        }
        if (monthSubs.isEmpty()) return yearChapterId

        monthSubs.firstOrNull { it.first == month }?.let { return it.second.id }

        val names = if (Locale.getDefault().language == "en") MONTH_NAMES_EN else MONTH_NAMES_DE
        val resp = api.createChapter(
            CreateChapterRequest(
                book_id = bookId,
                name = "$year ${names[month - 1]}",
                position = month,
                parent_chapter_id = yearChapterId,
            )
        )
        if (!resp.isSuccessful) error("createChapter (Monat) HTTP ${resp.code()}")
        return resp.body()?.id ?: error("createChapter (Monat) lieferte keine ID")
    }

    /** Unterkapitel eines Kapitels — verschachtelte UND flache Tree-Form. */
    private fun yearChapterSubs(tree: TreeDto, parentId: Long): List<ChapterNodeDto> {
        val nested = collectChapters(tree).firstOrNull { it.id == parentId }?.subchapters.orEmpty()
        val flat = tree.chapters.filter { it.parent_chapter_id == parentId }
        return nested + flat
    }

    // ── Seiten ─────────────────────────────────────────────────────────────

    fun observePages(bookId: Long): Flow<List<PageEntity>> = db.pageDao().observeForBook(bookId)

    /**
     * Seite für den Editor laden: erst Cache, sonst Server. Cached Seiten mit
     * lokal-dirty Stand werden NICHT überschrieben (Pending-Write hängt dran).
     */
    suspend fun loadPage(pageId: Long, bookId: Long): Result<PageEntity> = runCatching {
        val cached = db.pageDao().byId(pageId)
        if (cached != null && (cached.html != null || cached.dirty)) return@runCatching cached
        val dto = net.content(baseUrl()).page(pageId)
        val entity = PageEntity(
            id = dto.id,
            bookId = bookId,
            chapterId = dto.chapter_id,
            name = dto.name,
            html = dto.html,
            updatedAt = dto.updated_at,
            dirty = false,
        )
        db.pageDao().upsert(entity)
        entity
    }

    /**
     * Editor-Save: lokal persistieren, Pending-Write queuen, dann (best effort)
     * online flushen. Liefert das Resultat des Online-Versuchs.
     */
    suspend fun savePage(pageId: Long, bookId: Long, html: String): SaveResult {
        val deviceId = settings.deviceId()
        // Basis-Stand VOR der lokalen Mutation festhalten: updateHtml lässt updatedAt
        // unberührt, hier steht also der zuletzt bestätigte Server-Stand (dirty-Seiten
        // werden vom Sync-Pull nie überschrieben). Dieser Snapshot geht als
        // expected_updated_at mit, damit der Server einen Fremd-Save als 409 erkennt.
        val baseUpdatedAt = db.pageDao().byId(pageId)?.updatedAt
        // 1) Lokal persistieren (dirty) + Queue konsolidieren.
        db.pageDao().updateHtml(pageId, html, dirty = true)
        db.pendingWriteDao().deletePendingForPage(pageId)
        val localId = db.pendingWriteDao().insert(
            PendingWriteEntity(
                pageId = pageId,
                bookId = bookId,
                html = html,
                deviceId = deviceId,
                createdAt = nowMillis(),
                baseUpdatedAt = baseUpdatedAt,
            )
        )
        // 2) Online-Versuch.
        return flushOne(localId, pageId, bookId, html, deviceId, baseUpdatedAt)
    }

    /** Einzelnen Pending-Write gegen den Server schicken. */
    suspend fun flushOne(
        localId: Long,
        pageId: Long,
        bookId: Long,
        html: String,
        deviceId: String,
        baseUpdatedAt: String?,
    ): SaveResult {
        val resp: Response<ch.schreibwerkstatt.mobile.data.net.dto.PageDto> = try {
            net.content(baseUrl()).savePage(
                pageId,
                SavePageRequest(html = html, device_id = deviceId, expected_updated_at = baseUpdatedAt),
            )
        } catch (e: Exception) {
            return SaveResult.Queued   // offline → bleibt in der Queue
        }
        return when {
            resp.isSuccessful -> {
                val dto = resp.body()
                db.pageDao().applyServerVersion(pageId, dto?.html ?: html, dto?.updated_at, dto?.name)
                db.pendingWriteDao().delete(localId)
                SaveResult.Saved(db.pageDao().byId(pageId)!!)
            }
            resp.code() == 409 -> {
                val c = parseError(resp.errorBody(), PageConflictDto.serializer())
                db.pendingWriteDao().setStatus(
                    localId, PendingWriteEntity.STATUS_CONFLICT, c?.server_editor_name
                )
                SaveResult.Conflict(c?.server_updated_at, c?.server_editor_name)
            }
            resp.code() == 423 -> {
                val l = parseError(resp.errorBody(), PageLockedDto.serializer())
                db.pendingWriteDao().setStatus(
                    localId, PendingWriteEntity.STATUS_LOCKED, l?.locked_by_email
                )
                SaveResult.Locked(l?.locked_by_email, l?.expires_at)
            }
            else -> {
                val e = parseError(resp.errorBody(), ApiErrorDto.serializer())
                db.pendingWriteDao().setStatus(
                    localId, PendingWriteEntity.STATUS_FAILED, e?.code ?: "HTTP ${resp.code()}"
                )
                SaveResult.Queued
            }
        }
    }

    /**
     * Konflikt auflösen: Server-Version laden und lokal übernehmen (lokale
     * Änderung verwerfen). Für v1 die einfache „Server gewinnt"-Variante.
     */
    suspend fun resolveWithServerVersion(pageId: Long, bookId: Long): Result<PageEntity> = runCatching {
        val dto = net.content(baseUrl()).page(pageId)
        db.pageDao().applyServerVersion(pageId, dto.html, dto.updated_at, dto.name)
        // Konflikt-/Pending-Writes der Seite verwerfen.
        db.pendingWriteDao().latestForPage(pageId)?.let { db.pendingWriteDao().delete(it.localId) }
        db.pageDao().byId(pageId)!!
    }

    // ── Sync (Delegation) ────────────────────────────────────────────────────

    suspend fun syncBook(bookId: Long): Result<Unit> = sync.pullBook(bookId, ::baseUrl)

    suspend fun flushPending(): Result<Int> = sync.flushPending(::flushOne)

    fun observePending() = db.pendingWriteDao().observePending()

    suspend fun devicePing(bookId: Long, pageId: Long?) = runCatching {
        net.content(baseUrl()).devicePing(
            bookId, DevicePingRequest(device_id = settings.deviceId(), page_id = pageId)
        )
    }

    // ── Helfer ────────────────────────────────────────────────────────────────

    private fun <T> parseError(body: ResponseBody?, serializer: kotlinx.serialization.KSerializer<T>): T? =
        body?.string()?.takeIf { it.isNotBlank() }?.let {
            runCatching { net.jsonParser.decodeFromString(serializer, it) }.getOrNull()
        }

    private fun nowMillis(): Long = System.currentTimeMillis()
}

private fun BookDto.toEntity() = BookEntity(
    id = id, name = name, role = role, ownerEmail = owner_email, buchtyp = buchtyp,
)

// ── Tagebuch-Helfer (geteilt mit der Tree-/Kalender-UI) ──────────────────────

/** `buchtyp`-Wert für Tagebuch-Bücher (Server-Schema). */
const val BUCHTYP_TAGEBUCH = "tagebuch"

private val DIARY_DATE_RE = Regex("""\d{4}-\d{2}-\d{2}""")
private val DIARY_DATE_PREFIX_RE = Regex("""^(\d{4}-\d{2}-\d{2})\b""")

/** Extrahiert den ISO-Tag `YYYY-MM-DD` aus einem Seitennamen, sonst null. */
fun diaryDateOf(name: String?): String? =
    name?.let { DIARY_DATE_PREFIX_RE.find(it)?.groupValues?.get(1) }

/** Alle Kapitelknoten flach (verschachtelte `subchapters` mit eingeschlossen). */
private fun collectChapters(tree: TreeDto): List<ChapterNodeDto> {
    val out = mutableListOf<ChapterNodeDto>()
    fun rec(c: ChapterNodeDto) { out += c; c.subchapters.forEach(::rec) }
    tree.chapters.forEach(::rec)
    return out
}

/** Alle Seiten des Baums (Top-Level + in Kapiteln). */
private fun collectPages(tree: TreeDto): List<TreePageDto> =
    tree.topPages + collectChapters(tree).flatMap { it.pages }

private val MONTH_NAMES_DE = arrayOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember",
)
private val MONTH_NAMES_EN = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

// DE/EN-Monatsname → 1-12, diakritika-tolerant. Spiegel zu diary-calendar.js.
private val MONTH_TOKENS = mapOf(
    "januar" to 1, "jan" to 1, "jaenner" to 1, "january" to 1,
    "februar" to 2, "feb" to 2, "february" to 2,
    "maerz" to 3, "marz" to 3, "mar" to 3, "mrz" to 3, "march" to 3,
    "april" to 4, "apr" to 4,
    "mai" to 5, "may" to 5,
    "juni" to 6, "jun" to 6, "june" to 6,
    "juli" to 7, "jul" to 7, "july" to 7,
    "august" to 8, "aug" to 8,
    "september" to 9, "sep" to 9, "sept" to 9,
    "oktober" to 10, "okt" to 10, "oct" to 10, "october" to 10,
    "november" to 11, "nov" to 11,
    "dezember" to 12, "dez" to 12, "december" to 12,
)

private val MONTH_SPLIT_RE = Regex("""[\s.,;:_\-/]+""")

private fun parseMonthName(token: String?): Int? {
    if (token.isNullOrBlank()) return null
    val norm = java.text.Normalizer.normalize(token.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("""\p{Mn}+"""), "")
    return norm.split(MONTH_SPLIT_RE).filter { it.isNotEmpty() }
        .firstNotNullOfOrNull { MONTH_TOKENS[it] }
}
