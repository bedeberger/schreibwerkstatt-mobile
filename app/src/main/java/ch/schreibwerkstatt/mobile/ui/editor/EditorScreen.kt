package ch.schreibwerkstatt.mobile.ui.editor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import ch.schreibwerkstatt.mobile.R
import ch.schreibwerkstatt.mobile.editor.EditorBridge
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
    val darkTheme = isSystemInDarkTheme()
    val vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(context.locator, bookId, pageId))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // evaluateJavascript-Helfer (UI-Thread).
    val evalJs: (String) -> Unit = { js -> webViewRef.value?.post { webViewRef.value?.evaluateJavascript(js, null) } }

    val micDeniedMsg = stringResource(R.string.editor_mic_denied)
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleDictation { text -> insertText(evalJs, text) }
        else vm.notify(micDeniedMsg)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (state.sttEnabled) {
                FloatingActionButton(onClick = {
                    if (state.transcribing) return@FloatingActionButton
                    // Bereits erteilt → direkt toggeln (start/stop). Sonst Berechtigung
                    // anfragen; der Launcher-Callback startet bei Erfolg, sonst Hinweis.
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.toggleDictation { text -> insertText(evalJs, text) }
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    when {
                        state.transcribing -> CircularProgressIndicator(strokeWidth = 2.dp)
                        state.recording -> Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.editor_stop))
                        else -> Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.editor_dictate))
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
                    Text(stringResource(R.string.editor_bundle_error, b.message), color = MaterialTheme.colorScheme.error)
                }
                is BundleState.Ready -> {
                    val bridge = remember(b.dir, darkTheme) { vm.newBridge(evalJs, darkTheme) }
                    EditorWebView(
                        bundleDir = b.dir,
                        bridge = bridge,
                        darkTheme = darkTheme,
                        onWebViewCreated = { webViewRef.value = it },
                    )
                }
            }

            // Native Diktat-Statusleiste: pulsende Pegel-Anzeige + Label, animiert
            // ein-/ausgeblendet. Antippen beendet die Aufnahme (wie der FAB).
            DictationStatusBar(
                recording = state.recording,
                transcribing = state.transcribing,
                level = state.level,
                onStop = { vm.toggleDictation { text -> insertText(evalJs, text) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            )
        }
    }

    // Konflikt-Dialog (409): Server-Version laden / lokale behalten.
    state.conflict?.let { c ->
        val editorName = c.serverEditorName ?: stringResource(R.string.editor_conflict_someone)
        val updatedAt = c.serverUpdatedAt ?: stringResource(R.string.editor_conflict_unknown_time)
        AlertDialog(
            onDismissRequest = vm::dismissConflict,
            title = { Text(stringResource(R.string.editor_conflict_title)) },
            text = {
                Text(stringResource(R.string.editor_conflict_message, editorName, updatedAt))
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resolveConflictWithServer { html ->
                        // Saubereren Server-Stand still in den Editor spielen (kein Save).
                        val payload = JSONObject().put("id", pageId).put("html", html).toString()
                        evalJs("window.__sw && window.__sw.setPage(${jsString(payload)});")
                    }
                }) { Text(stringResource(R.string.editor_conflict_load_server)) }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissConflict) { Text(stringResource(R.string.editor_conflict_keep_local)) }
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

/**
 * Bodennahe Statusleiste während des Diktats. Beim Aufnehmen zeigt sie eine
 * lebendige Pegel-Anzeige (reagiert auf [level]) und „Höre zu …"; während der
 * Transkription einen Spinner. Tippen beendet die Aufnahme. Gleitet sanft ein
 * und aus, sobald sich der Zustand ändert.
 */
@Composable
private fun DictationStatusBar(
    recording: Boolean,
    transcribing: Boolean,
    level: Float,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = recording || transcribing,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = if (recording) Modifier.clickable(onClick = onStop) else Modifier,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (transcribing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(R.string.editor_transcribing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LevelEqualizer(
                        level = level,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Column {
                        Text(
                            stringResource(R.string.editor_listening),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.editor_listening_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fünf-Balken-Equalizer, der auf den Mikrofon-Pegel reagiert. Eine endlose
 * Wellen-Phase moduliert die Balken zeitversetzt, damit die Anzeige auch bei
 * konstantem Pegel „atmet"; ein Pegel-Boden hält sie bei Stille lebendig.
 */
@Composable
private fun LevelEqualizer(level: Float, color: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val bars = 5
    val maxHeight = 22.dp
    val barWidth = 4.dp
    val amplitude = max(level, 0.10f) // Boden, damit es bei Stille leicht pulst.
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(maxHeight),
    ) {
        repeat(bars) { i ->
            val wave = (sin(phase + i * 0.9f) * 0.5f + 0.5f) // 0..1
            val fraction = (0.18f + 0.82f * amplitude * wave).coerceIn(0.12f, 1f)
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * fraction)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EditorWebView(
    bundleDir: java.io.File,
    // Konkreter Typ (nicht Any): nur so erkennt Lint die @JavascriptInterface-
    // Methoden der Bridge (sonst JavascriptInterface-Error beim addJavascriptInterface).
    bridge: EditorBridge,
    darkTheme: Boolean,
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
                // WebView-Hintergrund passend zum Theme, damit beim Laden kein
                // weisser Blitz vor dem CSS-Inject aufscheint (Navy = Dark-BG).
                setBackgroundColor(if (darkTheme) 0xFF1A1F3A.toInt() else 0xFFFAF7F2.toInt())
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
