package ch.schreibwerkstatt.mobile.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.net.VerifyResult
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val DEFAULT_SERVER_URL = "https://schreibwerkstatt.app"
private const val TOKEN_PREFIX = "swd_"

data class PairingUiState(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val token: String = "",
    val deviceName: String = "",
    val error: String? = null,
    val busy: Boolean = false,
)

/**
 * Manuelles Pairing wie beim Mac-Client: der Nutzer gibt die Server-Adresse ein
 * und fügt ein am Server (Einstellungen → Geräte) vorab erzeugtes Device-Token
 * (`swd_…`) ein. Das Token wird gegen `GET …/config` verifiziert und erst bei
 * Erfolg verschlüsselt im [TokenStore] abgelegt.
 */
class PairingViewModel(
    private val settings: SettingsStore,
    private val tokenStore: TokenStore,
    private val network: NetworkClient,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState(deviceName = android.os.Build.MODEL ?: "Android"))
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.serverBaseUrlOnce()?.let { url ->
                _state.value = _state.value.copy(serverUrl = url)
            }
        }
    }

    fun onServerUrlChange(v: String) { _state.value = _state.value.copy(serverUrl = v, error = null) }
    fun onTokenChange(v: String) { _state.value = _state.value.copy(token = v, error = null) }
    fun onDeviceNameChange(v: String) { _state.value = _state.value.copy(deviceName = v) }

    /**
     * URL + Token prüfen, Token gegen den Server verifizieren und bei Erfolg
     * speichern. [onPaired] wird nur bei erfolgreichem Pairing aufgerufen.
     */
    fun couple(onPaired: () -> Unit) {
        val rawUrl = _state.value.serverUrl.trim()
        val token = _state.value.token.trim()

        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            _state.value = _state.value.copy(error = "URL muss mit http:// oder https:// beginnen")
            return
        }
        if (!token.startsWith(TOKEN_PREFIX)) {
            _state.value = _state.value.copy(error = "Token muss mit $TOKEN_PREFIX beginnen")
            return
        }

        val normalized = SettingsStore.normalizeBaseUrl(rawUrl)
        _state.value = _state.value.copy(serverUrl = normalized, busy = true, error = null)

        viewModelScope.launch {
            settings.setServerBaseUrl(normalized)
            when (val r = network.verifyToken(normalized, token)) {
                is VerifyResult.Ok -> {
                    val label = _state.value.deviceName.ifBlank { "Android" }
                    tokenStore.save(token, label, null)
                    _state.value = _state.value.copy(busy = false)
                    onPaired()
                }
                is VerifyResult.Unauthorized ->
                    _state.value = _state.value.copy(busy = false, error = "Token ungültig oder widerrufen.")
                is VerifyResult.Failed ->
                    _state.value = _state.value.copy(busy = false, error = "Server nicht erreichbar: ${r.message}")
            }
        }
    }

    companion object {
        fun factory(locator: ServiceLocator): ViewModelProvider.Factory = viewModelFactory {
            initializer { PairingViewModel(locator.settings, locator.tokenStore, locator.network) }
        }
    }
}
