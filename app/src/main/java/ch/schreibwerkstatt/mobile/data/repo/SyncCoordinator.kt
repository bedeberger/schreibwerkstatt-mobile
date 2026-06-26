package ch.schreibwerkstatt.mobile.data.repo

import ch.schreibwerkstatt.mobile.data.net.ConnectivityObserver
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-weiter Sync-Koordinator (lebt im Application-Scope, überdauert einzelne
 * ViewModels). Aufgaben:
 *
 *  1. **Auto-Flush:** Sobald die Verbindung (wieder) da ist und ein Token vorliegt,
 *     wird die Pending-Write-Queue best-effort geflusht ([ContentRepository.flushPending]).
 *  2. **Status für die UI:** [online] und [pendingCount] als heisse StateFlows,
 *     die der Offline-/Queue-Banner direkt beobachtet.
 *
 * Der eigentliche Flush (inkl. 409/423-Übersetzung) steckt unverändert im
 * Repository/SyncEngine — hier wird er nur ausgelöst.
 */
class SyncCoordinator(
    private val repository: ContentRepository,
    connectivity: ConnectivityObserver,
    private val tokenStore: TokenStore,
    private val scope: CoroutineScope,
) {
    val online: StateFlow<Boolean> =
        connectivity.online.stateIn(scope, SharingStarted.Eagerly, true)

    val pendingCount: StateFlow<Int> =
        repository.observePending()
            .map { it.size }
            .stateIn(scope, SharingStarted.Eagerly, 0)

    /** Beim App-Start aufrufen (App.onCreate). Idempotent — nur einmal nötig. */
    fun start() {
        scope.launch {
            // online ist ein StateFlow → emittiert nur bei echtem Wechsel.
            // Der Startwert (true) löst einen Flush ggf. liegen gebliebener
            // Pending-Writes aus früheren Sessions aus (harmlos, wenn leer).
            online.collect { isOnline -> if (isOnline) flushNow() }
        }
    }

    /** Pending-Queue manuell anstossen (z.B. Pull-to-Refresh). Best effort. */
    fun flushNow() {
        if (tokenStore.token() == null) return
        scope.launch { repository.flushPending() }
    }
}
