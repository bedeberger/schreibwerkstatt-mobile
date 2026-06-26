package ch.schreibwerkstatt.mobile

import android.app.Application
import android.content.Context
import ch.schreibwerkstatt.mobile.audio.DictationController
import ch.schreibwerkstatt.mobile.bundle.BundleManager
import ch.schreibwerkstatt.mobile.data.db.AppDatabase
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import ch.schreibwerkstatt.mobile.data.repo.ContentRepository

/**
 * Schlanker manueller DI-Container. Hält die App-weiten Singletons; ViewModels
 * ziehen sich ihre Abhängigkeiten über [ServiceLocator] (siehe ViewModel-
 * Factories). Kein Hilt — der Graph ist klein und überschaubar.
 */
class ServiceLocator(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val tokenStore: TokenStore by lazy { TokenStore(appContext) }
    val network: NetworkClient by lazy { NetworkClient(tokenStore, debug = BuildConfig.DEBUG) }
    val db: AppDatabase by lazy { AppDatabase.get(appContext) }
    val repository: ContentRepository by lazy { ContentRepository(db, network, settings) }
    val bundleManager: BundleManager by lazy { BundleManager(appContext, tokenStore) }

    fun dictationController(): DictationController =
        DictationController(appContext, network, settings)
}

class App : Application() {
    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        locator = ServiceLocator(this)
    }
}

/** Bequemer Zugriff aus Composables/Activities. */
val Context.locator: ServiceLocator
    get() = (applicationContext as App).locator
