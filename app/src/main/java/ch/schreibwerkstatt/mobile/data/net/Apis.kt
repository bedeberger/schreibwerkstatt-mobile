package ch.schreibwerkstatt.mobile.data.net

import ch.schreibwerkstatt.mobile.data.net.dto.BookDto
import ch.schreibwerkstatt.mobile.data.net.dto.ConfigDto
import ch.schreibwerkstatt.mobile.data.net.dto.CreateDeviceTokenRequest
import ch.schreibwerkstatt.mobile.data.net.dto.CreateDeviceTokenResponse
import ch.schreibwerkstatt.mobile.data.net.dto.PageDto
import ch.schreibwerkstatt.mobile.data.net.dto.SavePageRequest
import ch.schreibwerkstatt.mobile.data.net.dto.SyncResponse
import ch.schreibwerkstatt.mobile.data.net.dto.TranscribeResponse
import ch.schreibwerkstatt.mobile.data.net.dto.TreeDto
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Geräte-Token-Ausstellung. Läuft im Pairing-WebView gegen die interaktive
 * Google-OIDC-Session (Cookie-Auth), NICHT über den Bearer-Interceptor.
 */
interface AuthApi {
    @POST("me/device-tokens")
    suspend fun createDeviceToken(
        @Body body: CreateDeviceTokenRequest,
    ): Response<CreateDeviceTokenResponse>
}

interface ConfigApi {
    @GET("config")
    suspend fun config(): ConfigDto
}

interface ContentApi {
    @GET("content/books")
    suspend fun books(): List<BookDto>

    @GET("content/books/{bookId}/tree")
    suspend fun tree(@Path("bookId") bookId: Long): TreeDto

    /** Delta-Pull. Erstaufruf ohne Cursor = Baseline. */
    @GET("content/books/{bookId}/sync")
    suspend fun sync(
        @Path("bookId") bookId: Long,
        @Query("since") since: String?,
        @Query("since_id") sinceId: Long?,
        @Query("limit") limit: Int = 200,
    ): SyncResponse

    @GET("content/pages/{pageId}")
    suspend fun page(@Path("pageId") pageId: Long): PageDto

    /** 200 → PageDto · 409 PAGE_CONFLICT · 423 PAGE_LOCKED (Body via errorBody). */
    @PUT("content/pages/{pageId}")
    suspend fun savePage(
        @Path("pageId") pageId: Long,
        @Body body: SavePageRequest,
    ): Response<PageDto>

    @POST("content/books/{bookId}/device-ping")
    suspend fun devicePing(
        @Path("bookId") bookId: Long,
        @Body body: DevicePingRequest,
    ): Response<Unit>
}

@kotlinx.serialization.Serializable
data class DevicePingRequest(
    val device_id: String,
    val page_id: Long? = null,
)

interface SttApi {
    /**
     * Rohe Audio-Bytes (kein Multipart). Content-Type trägt den Audio-Mime
     * (z.B. `audio/mp4`); [audio] muss mit derselben MediaType gebaut sein.
     */
    @POST("stt/transcribe")
    suspend fun transcribe(
        @Query("bookId") bookId: Long,
        @Query("pageId") pageId: Long,
        @Body audio: RequestBody,
    ): Response<TranscribeResponse>
}
