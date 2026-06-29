package ch.schreibwerkstatt.mobile

import android.app.Application
import android.content.Context
import ch.schreibwerkstatt.mobile.audio.DictationController
import ch.schreibwerkstatt.mobile.bundle.BundleManager
import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.net.ClientInfo
import ch.schreibwerkstatt.mobile.data.net.ConnectivityObserver
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository
import ch.schreibwerkstatt.mobile.data.repo.SyncCoordinator
import ch.schreibwerkstatt.mobile.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Schlanker manueller DI-Container. Hält die App-weiten Singletons; ViewModels
 * ziehen sich ihre Abhängigkeiten über [ServiceLocator] (siehe ViewModel-
 * Factories). Kein Hilt — der Graph ist klein und überschaubar.
 */
class ServiceLocator(context: Context) {
    private val appContext = context.applicationContext

    /** App-Scope für Hintergrundarbeit, die einzelne ViewModels überdauert. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val tokenStore: TokenStore by lazy { TokenStore(appContext) }
    val network: NetworkClient by lazy { NetworkClient(tokenStore, debug = BuildConfig.DEBUG) }
    val db: AppDatabase by lazy { AppDatabase.get(appContext) }
    val repository: ContentRepository by lazy { ContentRepository(db, network, settings) }
    val bundleManager: BundleManager by lazy { BundleManager(appContext, tokenStore) }
    val connectivity: ConnectivityObserver by lazy { ConnectivityObserver(appContext) }
    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(appContext, repository, connectivity, tokenStore, settings, applicationScope)
    }
    val updateManager: UpdateManager by lazy { UpdateManager(appContext, applicationScope) }

    fun dictationController(): DictationController =
        DictationController(appContext, network, settings)
}

class App : Application() {
    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        // Gerätename einmal pro Prozess ermitteln (für die X-Client-Device-Header).
        ClientInfo.init(this)
        locator = ServiceLocator(this)
        // Auto-Flush der Pending-Write-Queue bei (wiederhergestellter) Verbindung.
        locator.syncCoordinator.start()
    }
}

/** Bequemer Zugriff aus Composables/Activities. */
val Context.locator: ServiceLocator
    get() = (applicationContext as App).locator
