package ch.schreibwerkstatt.mobile.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reiner JVM-Test (kein Android) für die URL-Normalisierung. Diese Regel ist der
 * Vertrag, auf den sich Retrofit-baseUrl, das Pairing (verifyToken) und der
 * Bundle-Download verlassen.
 */
class SettingsStoreNormalizeTest {

    @Test fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", SettingsStore.normalizeBaseUrl("  https://example.com  "))
    }

    @Test fun `strips a single trailing slash`() {
        assertEquals("https://example.com", SettingsStore.normalizeBaseUrl("https://example.com/"))
    }

    @Test fun `strips multiple trailing slashes`() {
        assertEquals("https://example.com", SettingsStore.normalizeBaseUrl("https://example.com///"))
    }

    @Test fun `keeps a path but drops its trailing slash`() {
        assertEquals("https://example.com/api", SettingsStore.normalizeBaseUrl("https://example.com/api/"))
    }

    @Test fun `leaves an already-normalized url untouched`() {
        assertEquals("http://192.168.1.10:3000", SettingsStore.normalizeBaseUrl("http://192.168.1.10:3000"))
    }
}
