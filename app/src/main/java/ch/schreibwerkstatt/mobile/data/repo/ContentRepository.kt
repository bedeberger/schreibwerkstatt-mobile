package ch.schreibwerkstatt.mobile.data.repo

import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.db.BookEntity
import ch.schreibwerkstatt.mobile.data.db.PageEntity
import ch.schreibwerkstatt.mobile.data.db.PendingWriteEntity
import ch.schreibwerkstatt.mobile.data.net.DevicePingRequest
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.net.dto.ApiErrorDto
import ch.schreibwerkstatt.mobile.data.net.dto.BookDto
import ch.schreibwerkstatt.mobile.data.net.dto.PageConflictDto
import ch.schreibwerkstatt.mobile.data.net.dto.PageLockedDto
import ch.schreibwerkstatt.mobile.data.net.dto.SavePageRequest
import ch.schreibwerkstatt.mobile.data.net.dto.TreeDto
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
            )
        )
        // 2) Online-Versuch.
        return flushOne(localId, pageId, bookId, html, deviceId)
    }

    /** Einzelnen Pending-Write gegen den Server schicken. */
    suspend fun flushOne(
        localId: Long,
        pageId: Long,
        bookId: Long,
        html: String,
        deviceId: String,
    ): SaveResult {
        val resp: Response<ch.schreibwerkstatt.mobile.data.net.dto.PageDto> = try {
            net.content(baseUrl()).savePage(pageId, SavePageRequest(html = html, device_id = deviceId))
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
