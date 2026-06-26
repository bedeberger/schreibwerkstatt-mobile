package ch.schreibwerkstatt.mobile.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ClientInfo.sanitize] muss Header-sichere Werte liefern: HTTP-Header-Werte
 * sind ASCII-only (OkHttp wirft sonst), und der Server begrenzt `X-Client-Device`
 * auf 100 Zeichen.
 */
class ClientInfoSanitizeTest {

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("Davids Handy", ClientInfo.sanitize("  Davids Handy  "))
    }

    @Test
    fun stripsNonAscii() {
        // Umlaute werden entfernt, der Rest bleibt erhalten:
        assertEquals("Mllers Pixel", ClientInfo.sanitize("Müllers Pixel"))
    }

    @Test
    fun capsAtHundredChars() {
        val long = "x".repeat(250)
        val out = ClientInfo.sanitize(long)!!
        assertTrue(out.length <= 100)
    }

    @Test
    fun emptyAfterSanitizeIsNull() {
        assertNull(ClientInfo.sanitize("   "))
        assertNull(ClientInfo.sanitize("üöä")) // alles nicht-ASCII → leer
    }

    @Test
    fun platformIsLowercaseAndroid() {
        assertEquals("android", ClientInfo.PLATFORM)
    }
}
