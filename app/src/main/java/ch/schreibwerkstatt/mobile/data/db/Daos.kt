package ch.schreibwerkstatt.mobile.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    @Query("DELETE FROM books WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<Long>)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: Long): BookEntity?
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE bookId = :bookId ORDER BY name COLLATE NOCASE")
    fun observeForBook(bookId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun byId(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observeById(id: Long): Flow<PageEntity?>

    @Upsert
    suspend fun upsertAll(pages: List<PageEntity>)

    @Upsert
    suspend fun upsert(page: PageEntity)

    @Query("UPDATE pages SET html = :html, dirty = :dirty WHERE id = :id")
    suspend fun updateHtml(id: Long, html: String, dirty: Boolean)

    @Query("UPDATE pages SET html = :html, updatedAt = :updatedAt, name = :name, dirty = 0 WHERE id = :id")
    suspend fun applyServerVersion(id: Long, html: String?, updatedAt: String?, name: String?)
}

@Dao
interface SyncCursorDao {
    @Query("SELECT * FROM sync_cursors WHERE bookId = :bookId")
    suspend fun forBook(bookId: Long): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(cursor: SyncCursorEntity)
}

@Dao
interface PendingWriteDao {
    @Insert
    suspend fun insert(write: PendingWriteEntity): Long

    @Query("SELECT * FROM pending_writes WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: String = PendingWriteEntity.STATUS_PENDING): List<PendingWriteEntity>

    @Query("SELECT * FROM pending_writes WHERE pageId = :pageId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForPage(pageId: Long): PendingWriteEntity?

    fun observePending(): Flow<List<PendingWriteEntity>> = observeByStatus(PendingWriteEntity.STATUS_PENDING)

    @Query("SELECT * FROM pending_writes WHERE status = :status ORDER BY createdAt ASC")
    fun observeByStatus(status: String): Flow<List<PendingWriteEntity>>

    @Query("UPDATE pending_writes SET status = :status, note = :note WHERE localId = :localId")
    suspend fun setStatus(localId: Long, status: String, note: String?)

    @Query("DELETE FROM pending_writes WHERE localId = :localId")
    suspend fun delete(localId: Long)

    /** Beim Queuen einer neuen Version ältere Pending-Writes derselben Seite zusammenfassen. */
    @Query("DELETE FROM pending_writes WHERE pageId = :pageId AND status = :status")
    suspend fun deletePendingForPage(pageId: Long, status: String = PendingWriteEntity.STATUS_PENDING)
}
