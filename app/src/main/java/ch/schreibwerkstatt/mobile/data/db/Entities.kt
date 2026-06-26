package ch.schreibwerkstatt.mobile.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val role: String? = null,
    val ownerEmail: String? = null,
    val buchtyp: String? = null,
)

@Entity(
    tableName = "pages",
    indices = [Index("bookId"), Index("chapterId")],
)
data class PageEntity(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val chapterId: Long? = null,
    val name: String? = null,
    val html: String? = null,
    /** Server-Stand (ISO-8601 Z). Für Konflikt-Vergleich. */
    val updatedAt: String? = null,
    /** true = lokale, noch nicht bestätigte Änderung (Pending-Write existiert). */
    val dirty: Boolean = false,
)

/** Delta-Sync-Cursor pro Buch. Erstaufruf ohne Cursor = Baseline. */
@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val bookId: Long,
    val since: String? = null,
    val sinceId: Long? = null,
    val lastSyncedAt: String? = null,
)

/**
 * Offline-Schreib-Queue. Bei Verbindung seriell geflusht (PUT /content/pages/:id);
 * 409/423 setzen [status] entsprechend.
 */
@Entity(
    tableName = "pending_writes",
    indices = [Index("pageId")],
)
data class PendingWriteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val pageId: Long,
    val bookId: Long,
    val html: String,
    val deviceId: String,
    val createdAt: Long,
    /**
     * Server-`updated_at`-Snapshot, auf dem dieser Edit basierte (aus der Page-Row
     * beim Queuen). Geht als `expected_updated_at` an den Server, damit ein
     * verzögerter Offline-Flush einen zwischenzeitlichen Fremd-Save als 409 erkennt
     * statt blind zu überschreiben. null = keine bekannte Basis → kein Guard.
     */
    val baseUpdatedAt: String? = null,
    val status: String = STATUS_PENDING,
    /** Letzte Fehlermeldung / Konfliktinfo (z.B. server_editor_name). */
    val note: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_CONFLICT = "conflict"   // 409
        const val STATUS_LOCKED = "locked"       // 423
        const val STATUS_FAILED = "failed"
    }
}
