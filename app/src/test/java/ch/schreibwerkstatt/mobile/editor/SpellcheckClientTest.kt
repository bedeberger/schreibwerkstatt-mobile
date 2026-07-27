package ch.schreibwerkstatt.mobile.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ch.schreibwerkstatt.mobile.data.net.DictionaryAddRequest
import ch.schreibwerkstatt.mobile.data.net.LanguageToolApi
import ch.schreibwerkstatt.mobile.data.net.LtCheckRequest
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Rechtschreibprüfung über den Server-Proxy ([SpellcheckClient]).
 *
 * Sichert die Vertrags-Punkte ab, an denen die WebView-Integration hängt:
 * die Prüf-Antwort geht als roher JSON-Body durch (der Spellcheck-Controller
 * im Bundle konsumiert LanguageTool-`matches[]` unverändert), 404 wird zum
 * „Feature aus"-Marker statt zum Fehler, und der lokale Grössen-Guard hält
 * das 500k-Zeichen-Limit des Servers ein.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpellcheckClientTest {

    private lateinit var api: FakeLanguageToolApi
    private lateinit var client: SpellcheckClient

    @Before fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        api = FakeLanguageToolApi()
        val settings = SettingsStore(ctx).apply { setServerBaseUrl("http://test") }
        client = SpellcheckClient(FakeLtNetworkClient(ctx, api), settings)
    }

    @Test fun `successful check passes the raw json body through`() = runTest {
        val body = """{"matches":[{"offset":3,"length":4,"rule":{"id":"GERMAN_SPELLER_RULE"}}]}"""
        api.checkResponder = { Response.success(body.asJson()) }

        val result = client.check("Der Fehlar hier", null, bookId = 7, pageId = 42)

        assertEquals(body, result.getOrNull())
    }

    @Test fun `book and page travel with the request, blank language is dropped`() = runTest {
        api.checkResponder = { Response.success("""{"matches":[]}""".asJson()) }

        client.check("Text", language = "auto", bookId = 7, pageId = 42)

        assertEquals(7L, api.lastCheck?.bookId)
        assertEquals(42L, api.lastCheck?.pageId)
        // „auto" ist kein Sprach-Code — die Buch-Locale löst der Server auf.
        assertNull(api.lastCheck?.language)
    }

    @Test fun `404 maps to the disabled marker instead of an error`() = runTest {
        api.checkResponder = { Response.error(404, """{"error":"languagetool_disabled"}""".asJson()) }

        val result = client.check("Text", null, bookId = 1, pageId = 2)

        assertEquals(SpellcheckClient.DISABLED_JSON, result.getOrNull())
    }

    @Test fun `upstream errors fail so the editor can show the error badge`() = runTest {
        api.checkResponder = { Response.error(502, """{"error":"languagetool_upstream"}""".asJson()) }

        val result = client.check("Text", null, bookId = 1, pageId = 2)

        assertTrue(result.isFailure)
    }

    @Test fun `text over the server limit is never sent`() = runTest {
        api.checkResponder = { error("darf nicht aufgerufen werden") }

        val result = client.check("x".repeat(SpellcheckClient.MAX_TEXT_CHARS + 1), null, 1, 2)

        assertTrue(result.isFailure)
        assertNull(api.lastCheck)
    }

    @Test fun `text at the limit is still allowed`() {
        assertFalse(SpellcheckClient.textTooLarge(SpellcheckClient.MAX_TEXT_CHARS))
        assertTrue(SpellcheckClient.textTooLarge(SpellcheckClient.MAX_TEXT_CHARS + 1))
    }

    @Test fun `add word posts globally and normalizes the language`() = runTest {
        api.addWordResponder = { Response.success(Unit) }

        assertTrue(client.addWord("Wortschatz", lang = null).getOrDefault(false))

        assertEquals("Wortschatz", api.lastAddWord?.word)
        assertEquals(0L, api.lastAddWord?.bookId)   // 0 = alle Bücher (wie im Web-Popover)
        assertEquals("*", api.lastAddWord?.lang)
    }

    @Test fun `add word reports failure instead of throwing`() = runTest {
        api.addWordResponder = { Response.error(500, """{"error":"add_failed"}""".asJson()) }

        assertFalse(client.addWord("Wort", "de-DE").getOrDefault(false))
    }

    private fun String.asJson(): ResponseBody = toResponseBody("application/json".toMediaType())
}

private class FakeLanguageToolApi : LanguageToolApi {
    var checkResponder: () -> Response<ResponseBody> = { error("nicht gesetzt") }
    var addWordResponder: () -> Response<Unit> = { error("nicht gesetzt") }
    var lastCheck: LtCheckRequest? = null
    var lastAddWord: DictionaryAddRequest? = null

    override suspend fun check(body: LtCheckRequest): Response<ResponseBody> {
        lastCheck = body
        return checkResponder()
    }

    override suspend fun addWord(body: DictionaryAddRequest): Response<Unit> {
        lastAddWord = body
        return addWordResponder()
    }
}

private class FakeLtNetworkClient(context: Context, private val api: LanguageToolApi) :
    NetworkClient(TokenStore(context), debug = false) {
    override fun languagetool(baseUrl: String): LanguageToolApi = api
}
