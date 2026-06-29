package ch.schreibwerkstatt.mobile.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.schreibwerkstatt.mobile.locator
import java.util.concurrent.TimeUnit

/**
 * Periodischer Background-Pull (WorkManager): frischt stündlich alle Bücher auf,
 * damit Inhalte aktuell bleiben, ohne dass man Bücher manuell öffnet. Reine
 * Client-Arbeit über [ContentRepository.syncAllBooks] — keine eigene
 * Geschäftslogik, kein direkter Server-Zugriff (siehe Harte Regeln).
 *
 * Doppelt abgesichert: läuft nur, wenn ein Token vorliegt UND der Nutzer den
 * Background-Sync nicht abgeschaltet hat. WorkManager garantiert das Intervall
 * nicht minutengenau — Pulls werden opportunistisch gebündelt.
 */
class PeriodicSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val locator = applicationContext.locator
        // Nicht gepairt oder vom Nutzer abgeschaltet → still nichts tun (kein Retry).
        if (locator.tokenStore.token() == null) return Result.success()
        if (!locator.settings.backgroundSyncOnce()) return Result.success()

        return locator.repository.syncAllBooks().fold(
            onSuccess = { Result.success() },
            // Transienter Fehler (offline mitten im Lauf, Serverfehler) → später erneut.
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val UNIQUE_NAME = "periodic-sync"
        private const val INTERVAL_HOURS = 1L

        /** Stündlichen Pull registrieren (idempotent — bestehende Planung bleibt). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Geplanten Pull abbestellen (wenn der Nutzer Background-Sync deaktiviert). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
