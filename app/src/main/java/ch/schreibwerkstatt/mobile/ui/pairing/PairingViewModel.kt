package ch.schreibwerkstatt.mobile.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.data.net.dto.CreateDeviceTokenResponse
import ch.schreibwerkstatt.mobile.data.prefs.SettingsStore
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PairingUiState(
    val serverUrl: String = "",
    val deviceName: String = "",
    val step: Step = Step.ConfigUrl,
    val loadUrl: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
) {
    enum class Step { ConfigUrl, WebLogin }
}

class PairingViewModel(
    private val settings: SettingsStore,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

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
    fun onDeviceNameChange(v: String) { _state.value = _state.value.copy(deviceName = v) }

    /** URL prüfen, persistieren und in den WebLogin-Schritt wechseln. */
    fun proceedToLogin() {
        val raw = _state.value.serverUrl.trim()
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            _state.value = _state.value.copy(error = "URL muss mit http:// oder https:// beginnen")
            return
        }
        val normalized = SettingsStore.normalizeBaseUrl(raw)
        viewModelScope.launch { settings.setServerBaseUrl(normalized) }
        _state.value = _state.value.copy(
            serverUrl = normalized,
            loadUrl = normalized + "/",
            step = PairingUiState.Step.WebLogin,
            error = null,
        )
    }

    fun backToConfig() {
        _state.value = _state.value.copy(step = PairingUiState.Step.ConfigUrl, loadUrl = null, error = null)
    }

    /** JS-Snippet, das in der eingeloggten Session den Device-Token zieht. */
    fun coupleScript(): String {
        val name = _state.value.deviceName.ifBlank { "Android" }.replace("\"", "\\\"")
        return """
            (function(){
              fetch('/me/device-tokens', {
                method:'POST', credentials:'include',
                headers:{'Content-Type':'application/json'},
                body: JSON.stringify({ device_name: "$name", platform: "android" })
              })
              .then(function(r){ if(!r.ok){ return r.text().then(function(t){ throw new Error('HTTP '+r.status+' '+t); }); } return r.json(); })
              .then(function(j){ SWPair.onToken(JSON.stringify(j)); })
              .catch(function(e){ SWPair.onError(String(e && e.message || e)); });
            })();
        """.trimIndent()
    }

    fun setBusy(b: Boolean) { _state.value = _state.value.copy(busy = b) }
    fun setError(msg: String) { _state.value = _state.value.copy(error = msg, busy = false) }

    /** Token-Antwort verarbeiten und sicher ablegen. Liefert true bei Erfolg. */
    fun onTokenJson(raw: String): Boolean {
        return try {
            val resp = json.decodeFromString(CreateDeviceTokenResponse.serializer(), raw)
            val plain = resp.token.plain_token
            if (plain.isNullOrBlank()) {
                setError("Antwort ohne plain_token")
                false
            } else {
                tokenStore.save(plain, resp.token.device_name ?: _state.value.deviceName, resp.token.id)
                true
            }
        } catch (e: Exception) {
            setError("Token konnte nicht gelesen werden: ${e.message}")
            false
        }
    }

    companion object {
        fun factory(locator: ServiceLocator): ViewModelProvider.Factory = viewModelFactory {
            initializer { PairingViewModel(locator.settings, locator.tokenStore) }
        }
    }
}
