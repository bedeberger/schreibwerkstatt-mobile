package ch.schreibwerkstatt.mobile.ui.pairing

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.schreibwerkstatt.mobile.R
import ch.schreibwerkstatt.mobile.locator
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onPaired: () -> Unit) {
    val context = LocalContext.current
    val vm: PairingViewModel = viewModel(factory = PairingViewModel.factory(context.locator))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.pairing_title)) }) }) { padding ->
        when (state.step) {
            PairingUiState.Step.ConfigUrl -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.pairing_intro))
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = vm::onServerUrlChange,
                    label = { Text(stringResource(R.string.pairing_server_label)) },
                    placeholder = { Text(stringResource(R.string.pairing_server_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.deviceName,
                    onValueChange = vm::onDeviceNameChange,
                    label = { Text(stringResource(R.string.pairing_device_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                Button(onClick = vm::proceedToLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pairing_login))
                }
            }

            PairingUiState.Step.WebLogin -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                state.error?.let {
                    Text(
                        it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                PairingWebView(
                    loadUrl = state.loadUrl!!,
                    coupleScriptProvider = vm::coupleScript,
                    onToken = { json -> if (vm.onTokenJson(json)) onPaired() },
                    onError = vm::setError,
                    onBusy = vm::setBusy,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
                Text(
                    "Nach dem Login oben rechts auf „Gerät koppeln“ tippen.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

/**
 * WebView, die den Server-Login (Google-OIDC) zeigt. Nach erfolgreichem Login
 * zieht ein injiziertes `fetch('/me/device-tokens', {credentials:'include'})`
 * (siehe [PairingViewModel.coupleScript]) den Token aus der Session — das
 * Ergebnis kommt über die `SWPair`-Bridge zurück.
 *
 * Der Kopplungs-Button wird nach Seiten-Load eingeblendet (oben rechts via
 * injiziertem DOM), damit der Nutzer ihn erst nach dem Login auslöst.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PairingWebView(
    loadUrl: String,
    coupleScriptProvider: () -> String,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    onBusy: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                val webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Callbacks lokal binden, damit sie in den Bridge-Methoden nicht
                // mit deren gleichnamigen Methoden kollidieren.
                val tokenCb = onToken
                val errorCb = onError
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onToken(json: String) { webView.post { onBusy(false); tokenCb(json) } }

                    @JavascriptInterface
                    fun onError(msg: String) { webView.post { onBusy(false); errorCb(msg) } }

                    @JavascriptInterface
                    fun couple() {
                        webView.post { onBusy(true) }
                        webView.post { webView.evaluateJavascript(coupleScriptProvider(), null) }
                    }
                }, "SWPair")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        // Floating „Gerät koppeln"-Button ins DOM injizieren.
                        view.evaluateJavascript(COUPLE_BUTTON_JS, null)
                    }
                }
                loadUrl(loadUrl)
            }
        },
    )
}

/** Injiziert einen fixierten „Gerät koppeln"-Button, der SWPair.couple() ruft. */
private const val COUPLE_BUTTON_JS = """
(function(){
  if (document.getElementById('sw-couple-btn')) return;
  var b = document.createElement('button');
  b.id = 'sw-couple-btn';
  b.textContent = 'Gerät koppeln';
  b.style.cssText = 'position:fixed;top:12px;right:12px;z-index:2147483647;padding:10px 16px;'
    + 'background:#356859;color:#fff;border:none;border-radius:8px;font:600 14px system-ui;box-shadow:0 2px 8px rgba(0,0,0,.3)';
  b.onclick = function(){ try { SWPair.couple(); } catch(e){} };
  document.body.appendChild(b);
})();
"""
