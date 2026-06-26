package ch.schreibwerkstatt.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.data.net.NetworkClient
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val deviceName: String = "",
    val sttStatus: String = "…",
)

class SettingsViewModel(
    private val settings: SettingsStore,
    private val tokenStore: TokenStore,
    private val network: NetworkClient,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(deviceName = tokenStore.deviceName() ?: "—"))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val base = settings.serverBaseUrlOnce().orEmpty()
            _state.value = _state.value.copy(serverUrl = base)
            if (base.isNotBlank()) {
                val status = runCatching { network.config(base).stt?.enabled == true }
                    .map { if (it) "aktiviert" else "deaktiviert" }
                    .getOrElse { "unbekannt" }
                _state.value = _state.value.copy(sttStatus = status)
            }
        }
    }

    /** Lokales Abmelden: nur den Token verwerfen (kein Server-Revoke). */
    fun signOut() = tokenStore.clear()

    companion object {
        fun factory(locator: ServiceLocator): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(locator.settings, locator.tokenStore, locator.network) }
        }
    }
}
