package ch.schreibwerkstatt.mobile.data.net

import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Baut Retrofit-Clients gegen die (zur Laufzeit variable) Server-Basis-URL.
 * Cached pro Basis-URL — bei URL-Wechsel (Settings) wird neu gebaut.
 *
 * Pairing (Cookie-Session) läuft NICHT hierüber, sondern im WebView via
 * injiziertem fetch (siehe PairingScreen).
 */
class NetworkClient(
    private val tokenStore: TokenStore,
    private val debug: Boolean,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedRetrofit: Retrofit? = null

    private fun okHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)   // STT-Cold-Start grosszügig
            .writeTimeout(45, TimeUnit.SECONDS)
        if (debug) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
        return builder.build()
    }

    @Synchronized
    private fun retrofitFor(baseUrl: String): Retrofit {
        val normalized = baseUrl.trimEnd('/') + "/"
        cachedRetrofit?.let { if (cachedBaseUrl == normalized) return it }
        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttp())
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
        cachedBaseUrl = normalized
        cachedRetrofit = retrofit
        return retrofit
    }

    val jsonParser: Json get() = json

    fun content(baseUrl: String): ContentApi = retrofitFor(baseUrl).create(ContentApi::class.java)
    fun stt(baseUrl: String): SttApi = retrofitFor(baseUrl).create(SttApi::class.java)
    fun config(baseUrl: String): ConfigApi = retrofitFor(baseUrl).create(ConfigApi::class.java)
}
