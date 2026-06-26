package ch.schreibwerkstatt.mobile.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schutz vor dem 5-MB-Segment-Limit des STT-Proxys (siehe Mutterprojekt
 * routes/stt.js). Reiner JVM-Test der Grössen-Guard- und Fehler-Mapping-Logik
 * aus [DictationController] — ohne MediaRecorder/Netzwerk.
 */
class DictationSizeLimitTest {

    @Test fun `limit is exactly five mebibytes`() {
        assertEquals(5 * 1024 * 1024, DictationController.MAX_SEGMENT_BYTES)
    }

    @Test fun `segment at the limit is allowed`() {
        assertFalse(DictationController.segmentTooLarge(DictationController.MAX_SEGMENT_BYTES))
    }

    @Test fun `one byte over the limit is rejected`() {
        assertTrue(DictationController.segmentTooLarge(DictationController.MAX_SEGMENT_BYTES + 1))
    }

    @Test fun `small and empty segments are allowed`() {
        assertFalse(DictationController.segmentTooLarge(0))
        assertFalse(DictationController.segmentTooLarge(64_000)) // ~1 s bei 64 kbit/s
    }

    @Test fun `oversized segment maps to the too-large message via server code`() {
        // Der lokale Guard wirft "stt_audio_too_large"; der Server quittiert mit 413.
        val expected = "Audiosegment zu gross (max 5 MB)."
        assertEquals(expected, DictationController.sttErrorMessage(413, "stt_audio_too_large"))
        assertEquals(expected, DictationController.sttErrorMessage(413, null))
    }

    @Test fun `other error codes keep their own messages`() {
        assertEquals("Diktat ist auf dem Server deaktiviert.", DictationController.sttErrorMessage(404, "stt_disabled"))
        assertEquals("Audioformat nicht unterstützt.", DictationController.sttErrorMessage(415, "stt_unsupported_audio"))
        assertEquals("STT-Dienst nicht erreichbar.", DictationController.sttErrorMessage(502, "stt_upstream"))
    }

    @Test fun `unknown error falls back to the http code`() {
        assertEquals("Transkription fehlgeschlagen (HTTP 500).", DictationController.sttErrorMessage(500, null))
    }
}
