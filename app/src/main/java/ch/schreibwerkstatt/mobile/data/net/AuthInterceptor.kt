package ch.schreibwerkstatt.mobile.data.net

import ch.schreibwerkstatt.mobile.BuildConfig
import ch.schreibwerkstatt.mobile.data.prefs.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Setzt auf jedem Request den `Authorization: Bearer swd_…`-Header (sofern ein
 * Token vorliegt) plus `X-Client-Version`. Bei 401 wird das Token verworfen —
 * die UI fällt dann (über [TokenStore.isPaired]) zurück aufs Pairing.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("X-Client-Version", BuildConfig.CLIENT_VERSION)

        tokenStore.token()?.let { builder.header("Authorization", "Bearer $it") }

        val response = chain.proceed(builder.build())
        if (response.code == 401) {
            // Token ungültig/widerrufen → lokal verwerfen. isPaired=false triggert
            // die Navigation zurück zum Pairing.
            tokenStore.clear()
        }
        return response
    }
}
