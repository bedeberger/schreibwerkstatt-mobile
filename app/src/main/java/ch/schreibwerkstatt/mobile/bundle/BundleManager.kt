package ch.schreibwerkstatt.mobile.bundle

import android.content.Context
import ch.schreibwerkstatt.mobile.data.net.AuthInterceptor
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Lädt das Focus-Editor-OTA-Bundle (`GET /content/editor-bundle.zip`) und
 * entpackt es ins App-internal-Storage. Nutzt `If-None-Match`/`ETag`: bei 304
 * wird nichts neu entpackt.
 *
 * Layout nach dem Entpacken (alles unter [bundleDir], an Origin-Root gemappt):
 *   js/editor/focus/standalone.js, css/…, icons.svg, bundle-manifest.json
 *   host.html  ← aus den App-Assets hineinkopiert (Einstiegsseite der WebView)
 *
 * Der [WebViewAssetLoader] (siehe EditorScreen) serviert [bundleDir] unter
 * https://appassets.androidplatform.net/ — so greift Same-Origin und die
 * relativen Imports (`./js/…`) sowie `/icons.svg` des Bundles funktionieren.
 */
class BundleManager(
    private val context: Context,
    tokenStore: TokenStore,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val bundleDir: File = File(context.filesDir, "editor-bundle")
    private val etagFile: File = File(context.filesDir, "editor-bundle.etag")

    /** Liegt ein entpacktes, lauffähiges Bundle vor? */
    fun isReady(): Boolean = File(bundleDir, "host.html").exists() &&
        File(bundleDir, "js/editor/focus/standalone.js").exists()

    /**
     * Synchronisiert das Bundle gegen den Server. Liefert true, wenn danach ein
     * lauffähiges Bundle bereitliegt (auch bei 304/Offline mit vorhandenem Cache).
     */
    suspend fun ensureBundle(baseUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/content/editor-bundle.zip"
        val storedEtag = etagFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        try {
            val reqBuilder = Request.Builder().url(url)
            if (storedEtag.isNotEmpty() && isReady()) {
                reqBuilder.header("If-None-Match", storedEtag)
            }
            http.newCall(reqBuilder.build()).execute().use { resp ->
                when {
                    resp.code == 304 -> Result.success(isReady())
                    resp.isSuccessful -> {
                        val body = resp.body ?: return@use Result.failure(
                            IllegalStateException("editor-bundle: leerer Body")
                        )
                        extractZip(body.byteStream())
                        copyHostPage()
                        resp.header("ETag")?.let { etagFile.writeText(it) }
                        Result.success(isReady())
                    }
                    else -> {
                        // Offline/Fehler: vorhandenes Bundle weiterverwenden.
                        if (isReady()) Result.success(true)
                        else Result.failure(IllegalStateException("editor-bundle HTTP ${resp.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            if (isReady()) Result.success(true) else Result.failure(e)
        }
    }

    private fun extractZip(input: java.io.InputStream) {
        if (bundleDir.exists()) bundleDir.deleteRecursively()
        bundleDir.mkdirs()
        val canonicalRoot = bundleDir.canonicalPath
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val target = File(bundleDir, entry.name)
                // Zip-Slip-Schutz.
                if (!target.canonicalPath.startsWith(canonicalRoot + File.separator) &&
                    target.canonicalPath != canonicalRoot
                ) {
                    throw SecurityException("Ungültiger Zip-Eintrag: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Host-Seite aus den App-Assets ins Bundle-Root kopieren. */
    private fun copyHostPage() {
        context.assets.open("editor-host/host.html").use { input ->
            File(bundleDir, "host.html").outputStream().use { input.copyTo(it) }
        }
    }
}
