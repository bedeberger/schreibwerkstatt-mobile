package ch.schreibwerkstatt.mobile.data.repo

import androidx.core.text.HtmlCompat

/**
 * Wandelt den serverseitigen Seiten-HTML in durchsuchbaren Klartext um — für den
 * FTS-Index ([ch.schreibwerkstatt.mobile.data.db.PageEntity.plain]) und damit auch
 * für saubere Treffer-Snippets ohne Markup. CPU-only, ohne Context.
 */
object HtmlText {
    private val WHITESPACE = Regex("\\s+")

    fun toPlain(html: String?): String? {
        if (html == null) return null
        if (html.isBlank()) return ""
        val text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return text.replace(WHITESPACE, " ").trim()
    }
}
