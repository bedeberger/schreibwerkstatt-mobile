package ch.schreibwerkstatt.mobile.data.repo

import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.db.PageEntity
import ch.schreibwerkstatt.mobile.data.db.PendingWriteEntity
import ch.schreibwerkstatt.mobile.data.db.SyncCursorEntity
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delta-Pull (`GET /content/books/:id/sync`) + Flush der Pending-Write-Queue.
 *
 * Pull-Schleife: solange `has_more`, mit `cursor {since, since_id}`
 * weiterpaginieren; Cursor in Room persistieren. Erstaufruf ohne Cursor =
 * Baseline (gesamtes Buch).
 */
class SyncEngine(
    private val db: AppDatabase,
    private val net: NetworkClient,
    private val settings: SettingsStore,
) {
    suspend fun pullBook(bookId: Long, baseUrl: suspend () -> String): Result<Unit> = runCatching {
        val api = net.content(baseUrl())
        var cursor = db.syncCursorDao().forBook(bookId)
        var since = cursor?.since
        var sinceId = cursor?.sinceId
        var nowIso: String? = null

        while (true) {
            val resp = api.sync(bookId, since, sinceId, limit = 200)
            nowIso = resp.now
            if (resp.pages.isNotEmpty()) {
                // HTML→Klartext-Strippen ist CPU-Arbeit → vom (oft Main-)Aufrufer-
                // Dispatcher wegziehen, damit der Pull die UI nicht ruckeln lässt.
                val entities = withContext(Dispatchers.Default) {
                    resp.pages.mapNotNull { p ->
                        // Lokal-dirty Seiten nicht mit Server-Stand überschreiben —
                        // der Pending-Write hat Vorrang bis zum Flush/Konflikt.
                        val local = db.pageDao().byId(p.page_id)
                        if (local?.dirty == true) return@mapNotNull null
                        PageEntity(
                            id = p.page_id,
                            bookId = bookId,
                            chapterId = p.chapter_id,
                            name = p.page_name,
                            html = p.html,
                            plain = HtmlText.toPlain(p.html),
                            updatedAt = p.updated_at,
                            dirty = false,
                        )
                    }
                }
                db.pageDao().upsertAll(entities)
            }
            // Cursor fortschreiben.
            resp.cursor?.let { c ->
                since = c.since ?: since
                sinceId = c.since_id ?: sinceId
            }
            if (!resp.has_more) break
        }

        db.syncCursorDao().put(
            SyncCursorEntity(
                bookId = bookId,
                since = since,
                sinceId = sinceId,
                lastSyncedAt = nowIso,
            )
        )
    }

    /**
     * Flush aller Pending-Writes (Status pending). Delegiert den eigentlichen
     * PUT an [putOne] (ContentRepository.flushOne), das 409/423 in Status
     * übersetzt. Liefert Anzahl erfolgreich bestätigter Writes.
     */
    suspend fun flushPending(
        putOne: suspend (localId: Long, pageId: Long, bookId: Long, html: String, deviceId: String, baseUpdatedAt: String?) -> SaveResult,
    ): Result<Int> = runCatching {
        val pending = db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_PENDING)
        var ok = 0
        for (w in pending) {
            when (putOne(w.localId, w.pageId, w.bookId, w.html, w.deviceId, w.baseUpdatedAt)) {
                is SaveResult.Saved -> ok++
                // Queued/Conflict/Locked: Status wurde in flushOne gesetzt; nicht abbrechen,
                // damit andere Seiten trotzdem durchlaufen.
                else -> Unit
            }
        }
        ok
    }
}
