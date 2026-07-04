package ch.schreibwerkstatt.mobile.editor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Idle-Mathematik der [WritingTimeTracker] (Pendant zum Web-/Mac-Heartbeat):
 * anrechenbare Zeit ist gedeckelt durch die 120-s-Leerlauf-Schwelle, die Lücke
 * wird nie (auch nicht rückwirkend) verbucht, und pro Ping wird auf 3600 s geclamped.
 * Reiner JVM-Test mit injizierter, monoton steuerbarer Uhr — kein Robolectric.
 */
class WritingTimeTrackerTest {

    private var clock = 0L
    private val sent = mutableListOf<Int>()

    private fun tracker() = WritingTimeTracker(nowMs = { clock }, send = { sent += it })

    @Test fun `zaehlt vergangene Zeit bei laufender Aktivitaet`() = runTest {
        val t = tracker()
        // Innerhalb der Idle-Schwelle regelmässig tippen → volle Zeit zählt.
        clock = 15_000; t.notifyActivity(); t.tick()
        clock = 30_000; t.notifyActivity(); t.tick()
        assertEquals(listOf(15, 15), sent)
    }

    @Test fun `deckelt Leerlauf jenseits der Schwelle`() = runTest {
        val t = tracker()
        // Kein Tippen; nach 200 s ticken → nur die ersten 120 s (idleMs) sind anrechenbar.
        clock = 200_000
        t.tick()
        assertEquals(listOf(120), sent)
    }

    @Test fun `rechnet die Leerlaufluecke beim Aufwachen nicht rueckwirkend an`() = runTest {
        val t = tracker()
        // 120 s werden verbucht (bis zur Schwelle), dann lange Pause.
        clock = 300_000
        t.tick()
        // Nach 300 s Leerlauf wieder tippen → Zähl-Marke springt auf jetzt.
        clock = 300_000; t.notifyActivity()
        // 10 s später ticken → nur diese 10 s zählen, nicht die Pause davor.
        clock = 310_000; t.tick()
        assertEquals(listOf(120, 10), sent)
    }

    @Test fun `resume beginnt ein frisches Segment ohne die Luecke davor`() = runTest {
        val t = tracker()
        // Editor war lange offen/im Hintergrund; resume() beim Wiedereintritt.
        clock = 500_000; t.resume()
        clock = 512_000; t.notifyActivity(); t.tick()
        assertEquals(listOf(12), sent)
    }

    @Test fun `tick meldet nichts bei stehender Uhr`() = runTest {
        val t = tracker()
        t.tick() // countedUpTo == now, delta 0
        assertEquals(emptyList<Int>(), sent)
    }

    @Test fun `clamped auf den Sekundendeckel pro Ping`() = runTest {
        // Durchgehende Aktivität (Lücken < Idle-Schwelle) hält die Uhr über eine
        // Stunde am Laufen, ohne dass getickt wird → das Delta würde 4000 s ergeben,
        // wird aber defensiv auf 3600 gedeckelt (analog zum Server-Clamp).
        val t = tracker()
        var i = 100_000L
        while (i <= 4_000_000L) { clock = i; t.notifyActivity(); i += 100_000L }
        t.tick()
        assertEquals(listOf(3600), sent)
    }
}
