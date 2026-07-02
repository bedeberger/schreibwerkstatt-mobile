package ch.schreibwerkstatt.mobile.ui.research

import android.content.Intent
import android.util.Patterns

/**
 * Aus einem `ACTION_SEND`-Intent extrahierte Recherche-Vorbelegung. Rein
 * datentragend; die Felder speisen die editierbaren Eingabefelder des
 * Capture-Screens (der Nutzer kann alles noch anpassen).
 */
data class SharedResearch(
    val url: String,
    val title: String,
    val note: String,
)

/**
 * Zerlegt einen geteilten `text/plain`-Intent in URL / Titel / Notiz.
 *
 * Heuristik (Android liefert je nach Quell-App Unterschiedliches):
 * - Erste http(s)-URL im `EXTRA_TEXT` → [SharedResearch.url].
 * - `EXTRA_SUBJECT`/`EXTRA_TITLE` (falls vorhanden) → Titel; sonst der Text ohne
 *   die URL (viele Browser teilen „Seitentitel\nURL").
 * - Bleibt nach Titel-Extraktion noch Text übrig, wandert er in die Notiz.
 *
 * Liefert null, wenn der Intent kein verwertbarer Text-Share ist.
 */
fun parseShareIntent(intent: Intent?): SharedResearch? {
    if (intent?.action != Intent.ACTION_SEND) return null
    val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
    val subject = (intent.getStringExtra(Intent.EXTRA_SUBJECT)
        ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    if (text.isEmpty() && subject == null) return null

    val url = Patterns.WEB_URL.matcher(text).let { m ->
        if (m.find()) text.substring(m.start(), m.end()) else ""
    }
    val leftover = if (url.isNotEmpty()) text.replace(url, "").trim() else text

    val title: String
    val note: String
    when {
        subject != null -> {
            title = subject
            // Leftover nur als Notiz übernehmen, wenn er nicht bloss den Titel wiederholt.
            note = leftover.takeIf { it.isNotEmpty() && !it.equals(subject, ignoreCase = true) }.orEmpty()
        }
        leftover.isNotEmpty() -> {
            // Erste Zeile als Titel, Rest als Notiz.
            val lines = leftover.lines().map { it.trim() }.filter { it.isNotEmpty() }
            title = lines.firstOrNull().orEmpty()
            note = lines.drop(1).joinToString("\n")
        }
        else -> {
            title = ""
            note = ""
        }
    }
    return SharedResearch(url = url, title = title, note = note)
}
