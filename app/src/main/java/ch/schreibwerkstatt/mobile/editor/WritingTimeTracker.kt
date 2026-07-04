package ch.schreibwerkstatt.mobile.editor

import android.os.SystemClock

/**
 * Idle-gedeckelte Schreibzeit-Uhr (Pendant zum Web-Heartbeat `writing-time.js` und
 * zum Mac-Client `WritingTimeTracker.swift`). Hält in-memory nur zwei monotone
 * Marken und rechnet daraus pro [tick] die seit dem letzten Ping *anrechenbare*
 * Zeit aus. „Anrechenbar" heisst: gedeckelt durch eine Leerlauf-Schwelle
 * ([idleMs]) — liegt das letzte Tippen länger als die Schwelle zurück, pausiert
 * die Uhr, und die Leerlauf-Lücke wird **nicht** (auch nicht rückwirkend) verbucht.
 *
 * Zeitbasis ist bewusst [SystemClock.elapsedRealtime] (monoton, kein Uhrsprung bei
 * NTP-/Zeitzonen-Änderung), injizierbar über [nowMs] für Tests.
 *
 * Der Tracker speichert selbst nichts und ist reines Delta-Melden: [send] bekommt
 * nur positive Sekunden-Deltas (Server addiert pro User/Buch/Tag auf). Threading:
 * [notifyActivity] kommt vom Binder-Thread der [EditorBridge], [tick]/[resume] vom
 * ViewModel-Coroutine-Thread — die Zustands-Mutation ist deshalb `@Synchronized`.
 */
class WritingTimeTracker(
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    /** Positive Delta-Sekunden melden (best effort; Fehler/Offline werden geschluckt). */
    private val send: suspend (seconds: Int) -> Unit,
) {
    /** Zuletzt registrierte Aktivität (Tippen). */
    private var lastActivity = nowMs()

    /** Bis hierher wurde Zeit bereits gemeldet — die Uhr läuft ab dieser Marke weiter. */
    private var countedUpTo = lastActivity

    /**
     * Aktivitäts-Signal (contenteditable `input`, debounced in host.html). Kommt der
     * Nutzer aus dem Leerlauf zurück (letzte Aktivität länger als [idleMs] her), wird
     * die Zähl-Marke auf jetzt gesetzt — die Pause wird so **nicht rückwirkend**
     * angerechnet, sobald wieder getippt wird.
     */
    @Synchronized
    fun notifyActivity() {
        val now = nowMs()
        if (now - lastActivity > idleMs) countedUpTo = now
        lastActivity = now
    }

    /**
     * Frisches Segment beginnen (Editor betreten / App zurück im Vordergrund). Beide
     * Marken auf jetzt — die (Hintergrund-/Abwesenheits-)Lücke davor zählt nie mit.
     */
    @Synchronized
    fun resume() {
        val now = nowMs()
        lastActivity = now
        countedUpTo = now
    }

    /** Berechnet das anrechenbare Delta seit dem letzten Ping und rückt die Marke vor. */
    @Synchronized
    private fun consumeSeconds(): Int {
        val now = nowMs()
        // Nur bis idleMs nach der letzten Aktivität zählen: nach einer Sprech-/Tipp-
        // pause jenseits der Schwelle steht die Uhr, bis wieder getippt wird.
        val windowEnd = minOf(now, lastActivity + idleMs)
        val deltaMs = windowEnd - countedUpTo
        if (deltaMs <= 0) return 0 // idle: nichts anrechnen
        countedUpTo = windowEnd
        return (deltaMs / 1000L).toInt().coerceAtMost(MAX_DELTA_S)
    }

    /** ~alle 15 s: anrechenbare Sekunden ermitteln und (falls > 0) melden. */
    suspend fun tick() {
        val seconds = consumeSeconds()
        if (seconds > 0) send(seconds)
    }

    private companion object {
        /** Leerlauf-Schwelle: liegt das letzte Tippen weiter zurück, pausiert die Uhr. */
        const val idleMs = 120_000L

        /** Defensiver Deckel der Delta-Sekunden pro Ping (analog zum Server-Clamp). */
        const val MAX_DELTA_S = 3600
    }
}
