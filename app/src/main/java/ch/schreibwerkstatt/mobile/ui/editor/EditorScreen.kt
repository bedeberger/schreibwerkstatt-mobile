package ch.schreibwerkstatt.mobile.ui.editor

import android.Manifest
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import ch.schreibwerkstatt.mobile.locator
import org.json.JSONObject

private const val APP_ORIGIN = "https://appassets.androidplatform.net"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    bookId: Long,
    pageId: Long,
    pageTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(context.locator, bookId, pageId))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // evaluateJavascript-Helfer (UI-Thread).
    val evalJs: (String) -> Unit = { js -> webViewRef.value?.post { webViewRef.value?.evaluateJavascript(js, null) } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleDictation { text -> insertText(evalJs, text) }
    }

    // Snackbar-Events.
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Vor dem Verlassen flushen (host.html: window.__sw.save()).
                        evalJs("window.__sw && window.__sw.save();")
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (state.sttEnabled) {
                FloatingActionButton(onClick = {
                    if (state.transcribing) return@FloatingActionButton
                    // Permission prüfen → toggeln.
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    when {
                        state.transcribing -> CircularProgressIndicator(strokeWidth = 2.dp)
                        state.recording -> Icon(Icons.Filled.Stop, contentDescription = "Stopp")
                        else -> Icon(Icons.Filled.Mic, contentDescription = "Diktieren")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val b = state.bundle) {
                is BundleState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is BundleState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Editor-Bundle: ${b.message}", color = MaterialTheme.colorScheme.error)
                }
                is BundleState.Ready -> {
                    val bridge = remember(b.dir) { vm.newBridge(evalJs) }
                    EditorWebView(
                        bundleDir = b.dir,
                        bridge = bridge,
                        onWebViewCreated = { webViewRef.value = it },
                    )
                }
            }
        }
    }

    // Konflikt-Dialog (409): Server-Version laden / lokale behalten.
    state.conflict?.let { c ->
        AlertDialog(
            onDismissRequest = vm::dismissConflict,
            title = { Text("Konflikt") },
            text = {
                Text(
                    "Diese Seite wurde von ${c.serverEditorName ?: "jemand anderem"} geändert " +
                        "(${c.serverUpdatedAt ?: "unbekannt"}). Server-Version laden und lokale Änderung verwerfen?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resolveConflictWithServer { html ->
                        // Saubereren Server-Stand still in den Editor spielen (kein Save).
                        val payload = JSONObject().put("id", pageId).put("html", html).toString()
                        evalJs("window.__sw && window.__sw.setPage(${jsString(payload)});")
                    }
                }) { Text("Server-Version laden") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissConflict) { Text("Lokale behalten") }
            },
        )
    }

    // Beim Verlassen: speichern + WebView abräumen.
    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { wv ->
                wv.evaluateJavascript("window.__sw && window.__sw.save();", null)
                wv.destroy()
            }
        }
    }
}

/** Fügt Diktat-Text am Cursor der aktiven Seite ein (host.html: window.__sw.insertText). */
private fun insertText(evalJs: (String) -> Unit, text: String) {
    evalJs("window.__sw && window.__sw.insertText(${jsString(text)});")
}

/** Sicheres JS-/JSON-String-Literal. */
private fun jsString(value: String): String = JSONObject.quote(value)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EditorWebView(
    bundleDir: java.io.File,
    bridge: Any,
    onWebViewCreated: (WebView) -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            // Asset-Loader: Bundle-Verzeichnis an Origin-Root mappen, damit relative
            // Imports (./js/…) und /icons.svg same-origin auflösen.
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/", WebViewAssetLoader.InternalStoragePathHandler(context, bundleDir))
                .build()

            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView, request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                }
                // Bridge injizieren (Name muss zu host.html passen: SWHost).
                addJavascriptInterface(bridge, "SWHost")
                onWebViewCreated(this)
                loadUrl("$APP_ORIGIN/host.html")
            }
        },
    )
}
