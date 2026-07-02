package ch.schreibwerkstatt.mobile.data.repo

import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.db.PageEntity
import ch.schreibwerkstatt.mobile.data.db.PendingWriteEntity
import ch.schreibwerkstatt.mobile.data.db.SyncCursorEntity
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import androidx.room.withTransaction
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

        var guard = 0
        while (true) {
            val resp = api.sync(bookId, since, sinceId, limit = 200)
            nowIso = resp.now
            if (resp.pages.isNotEmpty()) {
                // HTML→Klartext-Strippen ist CPU-Arbeit → vom (oft Main-)Aufrufer-
                // Dispatcher wegziehen, damit der Pull die UI nicht ruckeln lässt.
                // (Ausserhalb der DB-Transaktion, damit diese kurz bleibt.)
                val candidates = withContext(Dispatchers.Default) {
                    resp.pages.map { p ->
                        p.page_id to PageEntity(
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
                // Dirty-Recheck UND Upsert atomar in einer Transaktion: schliesst das
                // TOCTOU-Fenster, in dem ein zwischen Prüfung und Upsert eintreffender
                // lokaler Save die Seite dirty macht und sonst überschrieben würde.
                // Lokal-dirty Seiten behalten so garantiert ihren Pending-Write-Vorrang.
                db.withTransaction {
                    val fresh = candidates
                        .filter { (id, _) -> db.pageDao().byId(id)?.dirty != true }
                        .map { it.second }
                    if (fresh.isNotEmpty()) db.pageDao().upsertAll(fresh)
                }
            }
            // Cursor fortschreiben.
            val prevSince = since
            val prevSinceId = sinceId
            resp.cursor?.let { c ->
                since = c.since ?: since
                sinceId = c.since_id ?: sinceId
            }
            if (!resp.has_more) break
            // Schutz gegen Endlosschleife: has_more=true, aber der Cursor bewegt sich nicht
            // (Server liefert keinen/denselben Cursor) → sonst identische Anfrage in Endlosschleife.
            if (since == prevSince && sinceId == prevSinceId) break
            if (++guard >= MAX_PULL_PAGES) break
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

    private companion object {
        /** Harte Obergrenze an Pull-Seiten pro Buch — Backstop gegen einen fehlerhaften Server. */
        const val MAX_PULL_PAGES = 10_000
    }
}
