package ch.schreibwerkstatt.mobile.data.net

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Verifiziert, dass [AuthInterceptor] auf jedem Request die Geräte-Label-Header
 * (`X-Client-Platform`/`X-Client-Device`) zusätzlich zu `X-Client-Version` setzt
 * und das Token nur bei vorhandenem Wert anhängt. Pure JVM — der Interceptor ist
 * über Lambdas von TokenStore/Keystore entkoppelt.
 */
class AuthInterceptorHeaderTest {

    @Test
    fun setsClientPlatformAndDeviceHeadersWithToken() {
        val captured = runInterceptor(AuthInterceptor(token = { "swd_secret" }, onUnauthorized = {}))

        assertEquals("android", captured.header("X-Client-Platform"))
        assertTrue("X-Client-Device sollte gesetzt sein", !captured.header("X-Client-Device").isNullOrEmpty())
        assertTrue("X-Client-Version sollte weiterhin gesetzt sein", !captured.header("X-Client-Version").isNullOrEmpty())
        assertEquals("Bearer swd_secret", captured.header("Authorization"))
    }

    @Test
    fun headersPresentEvenWithoutToken() {
        val captured = runInterceptor(AuthInterceptor(token = { null }, onUnauthorized = {}))

        assertEquals("android", captured.header("X-Client-Platform"))
        assertTrue(!captured.header("X-Client-Device").isNullOrEmpty())
        assertNull(captured.header("Authorization"))
    }

    @Test
    fun clearsTokenOn401() {
        var cleared = false
        runInterceptor(AuthInterceptor(token = { "swd_x" }, onUnauthorized = { cleared = true }), responseCode = 401)
        assertTrue("onUnauthorized sollte bei 401 gefeuert haben", cleared)
    }

    private fun runInterceptor(interceptor: AuthInterceptor, responseCode: Int = 200): Request {
        lateinit var captured: Request
        val chain = object : Interceptor.Chain {
            private val original = Request.Builder().url("https://example.com/content/books").build()
            override fun request(): Request = original
            override fun proceed(request: Request): Response {
                captured = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("msg")
                    .body("".toResponseBody(null))
                    .build()
            }
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
        }
        interceptor.intercept(chain)
        return captured
    }
}
