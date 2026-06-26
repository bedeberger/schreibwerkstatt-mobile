package ch.schreibwerkstatt.mobile.data.net

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Prozess-weit konstante Client-Metadaten für die `X-Client-*`-Header. Einmalig
 * in [ch.schreibwerkstatt.mobile.App.onCreate] über [init] befüllt, danach vom
 * [AuthInterceptor] auf jedem Request gelesen.
 *
 * Der Server baut daraus das Geräte-Label im Revision-Viewer
 * (`<Device> · <Platform>`), sodass ein geteiltes Device-Token trotzdem das
 * richtige Gerät anzeigt.
 */
object ClientInfo {
    /** Plattform-Code für `X-Client-Platform` (kleingeschrieben; der Server mappt ihn auf die Anzeige „Android"). */
    const val PLATFORM = "android"

    /**
     * Nutzererkennbarer Gerätename für `X-Client-Device`. Bevorzugt der in den
     * Android-Settings vergebene Name ([Settings.Global.DEVICE_NAME]), sonst das
     * Hardware-Modell ([Build.MODEL]). Pro Prozess konstant.
     */
    @Volatile
    var deviceName: String = Build.MODEL ?: PLATFORM
        private set

    fun init(context: Context) {
        deviceName = resolveDeviceName(context)
    }

    private fun resolveDeviceName(context: Context): String {
        val userName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.let(::sanitize)
        val model = Build.MODEL?.let(::sanitize)
        return userName ?: model ?: PLATFORM
    }

    /**
     * HTTP-Header-Werte müssen ASCII-sauber sein — OkHttp wirft sonst auf jedem
     * Request. Nicht-druckbare/Nicht-ASCII-Zeichen entfernen und auf das
     * Server-Limit (100 Zeichen) kürzen.
     */
    internal fun sanitize(raw: String): String? {
        val cleaned = raw.trim()
            .filter { it.code in 0x20..0x7e }
            .take(100)
            .trim()
        return cleaned.takeIf { it.isNotEmpty() }
    }
}
