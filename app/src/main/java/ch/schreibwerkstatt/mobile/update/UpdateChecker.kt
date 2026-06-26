package ch.schreibwerkstatt.mobile.update

import ch.schreibwerkstatt.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Beschreibt das neueste GitHub-Release (Distributionskanal für die APK).
 */
data class ReleaseInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)

sealed interface UpdateCheckResult {
    /** Installierte Version ist aktuell (oder neuer als das Release). */
    object UpToDate : UpdateCheckResult
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

/**
 * Fragt die GitHub-Releases-API nach der neuesten App-Version und vergleicht sie
 * mit der installierten ([BuildConfig.VERSION_NAME]).
 *
 * Bewusst ein **eigener**, schlanker OkHttp-Client ohne [AuthInterceptor]: An
 * GitHub darf NIE das `swd_…`-Device-Token gehen — das gehört nur an die
 * Mutterprojekt-Server-API. GitHub ist hier reiner APK-Distributionskanal.
 */
open class UpdateChecker(
    private val owner: String = BuildConfig.UPDATE_GITHUB_OWNER,
    private val repo: String = BuildConfig.UPDATE_GITHUB_REPO,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    open suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext UpdateCheckResult.Failed("HTTP ${resp.code}")
                val body = resp.body?.string()
                    ?: return@withContext UpdateCheckResult.Failed("Leere Antwort")
                val release = json.decodeFromString<GhRelease>(body)
                val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext UpdateCheckResult.Failed("Kein APK im Release")
                val latest = stripV(release.tagName)
                if (compareSemver(latest, currentVersionName) > 0) {
                    UpdateCheckResult.Available(
                        ReleaseInfo(
                            versionName = latest,
                            notes = release.body?.trim().orEmpty(),
                            apkUrl = asset.downloadUrl,
                            apkSizeBytes = asset.size,
                        )
                    )
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    companion object {
        private fun stripV(tag: String): String = tag.trim().removePrefix("v").removePrefix("V")

        /**
         * Vergleicht zwei SemVer-`major.minor.patch`-Strings numerisch. Fehlende
         * Stellen zählen als 0; Suffixe (z.B. `-rc1`) werden ignoriert. >0 = a neuer.
         */
        fun compareSemver(a: String, b: String): Int {
            val pa = parse(a)
            val pb = parse(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x.compareTo(y)
            }
            return 0
        }

        private fun parse(v: String): List<Int> =
            v.substringBefore('-').substringBefore('+').split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    }
}
