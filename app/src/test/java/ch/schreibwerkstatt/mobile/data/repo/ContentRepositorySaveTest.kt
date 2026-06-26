package ch.schreibwerkstatt.mobile.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.db.PageEntity
import ch.schreibwerkstatt.mobile.data.db.PendingWriteEntity
import ch.schreibwerkstatt.mobile.data.net.dto.PageDto
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import kotlinx.coroutines.test.runTest
import retrofit2.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Editor-Save-Pfad des [ContentRepository]: lokal persistieren → Pending-Write
 * queuen → Online-Flush. Prüft die vier [SaveResult]-Ausgänge und die daraus
 * folgenden Queue-Status (pending/conflict/locked/failed) sowie das Dirty-Flag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ContentRepositorySaveTest {

    private lateinit var db: AppDatabase
    private lateinit var api: FakeContentApi
    private lateinit var repo: ContentRepository

    private val pageId = 10L
    private val bookId = 1L

    @Before fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        api = FakeContentApi()
        val settings = SettingsStore(ctx).apply { setServerBaseUrl("http://test") }
        repo = ContentRepository(db, FakeNetworkClient(ctx, api), settings)
        // Ausgangs-Seite mit bekanntem Server-Stand (Basis für expected_updated_at).
        db.pageDao().upsert(
            PageEntity(id = pageId, bookId = bookId, name = "S", html = "<p>old</p>", updatedAt = "t0", dirty = false)
        )
    }

    @After fun tearDown() = db.close()

    @Test fun `online save returns Saved, clears dirty and drains the queue`() = runTest {
        api.savePageResponder = {
            Response.success(PageDto(id = pageId, name = "S", html = "<p>server</p>", updated_at = "t1"))
        }

        val res = repo.savePage(pageId, bookId, "<p>local</p>")

        assertTrue(res is SaveResult.Saved)
        db.pageDao().byId(pageId)!!.let {
            assertFalse(it.dirty)
            assertEquals("<p>server</p>", it.html)
            assertEquals("t1", it.updatedAt)
        }
        assertTrue(db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_PENDING).isEmpty())
        // expected_updated_at trug den Stand VOR der lokalen Mutation.
        assertEquals("t0", api.lastSaveRequest?.expected_updated_at)
    }

    @Test fun `409 returns Conflict and parks the write as conflict`() = runTest {
        api.savePageResponder = {
            FakeContentApi.error(
                409,
                """{"error_code":"PAGE_CONFLICT","server_updated_at":"t2","server_editor_name":"Lektor"}""",
            )
        }

        val res = repo.savePage(pageId, bookId, "<p>local</p>")

        assertTrue(res is SaveResult.Conflict)
        assertEquals("Lektor", (res as SaveResult.Conflict).serverEditorName)
        assertTrue(db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_PENDING).isEmpty())
        db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_CONFLICT).single().let {
            assertEquals("Lektor", it.note)
        }
        // Lokale Änderung bleibt dirty, bis der Nutzer den Konflikt auflöst.
        assertTrue(db.pageDao().byId(pageId)!!.dirty)
    }

    @Test fun `423 returns Locked and parks the write as locked`() = runTest {
        api.savePageResponder = {
            FakeContentApi.error(
                423,
                """{"error_code":"PAGE_LOCKED","locked_by_email":"lektor@x.ch","expires_at":"t3"}""",
            )
        }

        val res = repo.savePage(pageId, bookId, "<p>local</p>")

        assertTrue(res is SaveResult.Locked)
        assertEquals("lektor@x.ch", (res as SaveResult.Locked).lockedByEmail)
        db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_LOCKED).single().let {
            assertEquals("lektor@x.ch", it.note)
        }
    }

    @Test fun `network failure returns Queued and keeps a pending write`() = runTest {
        api.savePageResponder = { throw java.io.IOException("offline") }

        val res = repo.savePage(pageId, bookId, "<p>local</p>")

        assertTrue(res is SaveResult.Queued)
        db.pageDao().byId(pageId)!!.let {
            assertTrue(it.dirty)
            assertEquals("<p>local</p>", it.html) // lokal erhalten
        }
        assertEquals(1, db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_PENDING).size)
    }

    @Test fun `server error 500 returns Queued and marks the write failed`() = runTest {
        api.savePageResponder = { FakeContentApi.error(500, """{"error_code":"INTERNAL"}""") }

        val res = repo.savePage(pageId, bookId, "<p>local</p>")

        assertTrue(res is SaveResult.Queued)
        db.pendingWriteDao().byStatus(PendingWriteEntity.STATUS_FAILED).single().let {
            assertEquals("INTERNAL", it.note)
        }
    }
}
