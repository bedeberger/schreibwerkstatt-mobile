package ch.schreibwerkstatt.mobile.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Anker der Schreiblinie ist reine Geometrie: er legt die Linie auf die Mitte
 * des SICHTBAREN Bildschirms, ausgedrückt als Anteil der WebView-Höhe. Sitzt die
 * Formel falsch, klebt die Schreiblinie an der Tastatur (zu gross) oder springt an
 * den oberen Rand (zu klein) — am Gerät nur mühsam zu beurteilen, hier exakt.
 */
class TypewriterAnchorRatioTest {

    @Test fun `without chrome above or below the anchor is the middle`() {
        assertEquals(0.5f, typewriterAnchorRatio(topGap = 0, height = 800, bottomGap = 0), 0.0001f)
    }

    @Test fun `chrome above lifts the anchor so the line sits on the screen middle`() {
        // Statusleiste + TopAppBar = 88, WebView 527 (Tastatur darunter, kein
        // sichtbarer Rest). Bildschirmmitte = (88 + 527) / 2 = 307.5 ab
        // Bildschirmoberkante → 219.5 ab WebView-Oberkante.
        val ratio = typewriterAnchorRatio(topGap = 88, height = 527, bottomGap = 0)
        assertEquals(219.5f / 527f, ratio, 0.0001f)
        assertTrue("Anker muss über der WebView-Mitte liegen", ratio < 0.5f)
    }

    @Test fun `visible area below the web view pushes the anchor back down`() {
        // Tastatur zu: unter der WebView liegt noch die Navigationsleiste, die zum
        // sichtbaren Bildschirm gehört. Symmetrische Chrome → wieder die Mitte.
        assertEquals(
            0.5f,
            typewriterAnchorRatio(topGap = 60, height = 800, bottomGap = 60),
            0.0001f,
        )
    }

    @Test fun `extreme geometry stays inside the clamp`() {
        // Sehr flache WebView neben hoher Chrome: ohne Klemmung landete die Linie
        // ausserhalb bzw. am Rand der Schreibfläche.
        assertEquals(0.25f, typewriterAnchorRatio(topGap = 600, height = 120, bottomGap = 0), 0.0001f)
        assertEquals(0.6f, typewriterAnchorRatio(topGap = 0, height = 120, bottomGap = 600), 0.0001f)
    }

    @Test fun `degenerate measurements fall back to the middle`() {
        assertEquals(0.5f, typewriterAnchorRatio(topGap = 0, height = 0, bottomGap = 0), 0.0001f)
    }
}
