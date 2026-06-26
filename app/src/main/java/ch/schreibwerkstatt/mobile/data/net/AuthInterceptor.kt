package ch.schreibwerkstatt.mobile.data.net

import android.os.Build
import ch.schreibwerkstatt.mobile.BuildConfig
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Setzt auf jedem Request den `Authorization: Bearer swd_…`-Header (sofern ein
 * Token vorliegt) plus `X-Client-Version` und einen aussagekräftigen
 * `User-Agent`. Bei 401 wird das Token verworfen — die UI fällt dann (über
 * [TokenStore.isPaired]) zurück aufs Pairing.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-Client-Version", BuildConfig.CLIENT_VERSION)
            .header("User-Agent", USER_AGENT)

        tokenStore.token()?.let { builder.header("Authorization", "Bearer $it") }

        val response = chain.proceed(builder.build())
        if (response.code == 401) {
            // Token ungültig/widerrufen → lokal verwerfen. isPaired=false triggert
            // die Navigation zurück zum Pairing.
            tokenStore.clear()
        }
        return response
    }

    companion object {
        /**
         * Menschenlesbares Geräte-Label für die Admin-Geräteliste des Servers
         * (`req.get('user-agent')` → `upsertDevice`). Ersetzt den OkHttp-Default
         * (`okhttp/x.y.z`), der dort sonst nichtssagend erschiene.
         */
        val USER_AGENT: String =
            "Schreibwerkstatt-Android/${BuildConfig.VERSION_NAME} " +
                "(${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE})"
    }
}
