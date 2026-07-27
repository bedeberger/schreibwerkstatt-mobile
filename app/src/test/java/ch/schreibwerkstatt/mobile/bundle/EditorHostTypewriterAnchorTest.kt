package ch.schreibwerkstatt.mobile.bundle

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verdrahtungs-Guard für die Schreiblinie („Typewriter-Anker") im WebView.
 *
 * Der Anker ist der einzige Hebel, mit dem die App die Schreiblinie des
 * OTA-Bundles positioniert: die Engine liest pro Recenter `host.typewriterAnchor`
 * (Anteil der Sichthöhe) und die Puffer der Schreibfläche kommen aus
 * `--sw-pad-top`/`--sw-pad-bottom`. Beides muss zusammen gesetzt werden — sonst
 * liegt die Linie woanders als der Puffer sie erreichbar macht, und die erste bzw.
 * letzte Zeile kommt nie auf die Linie. Reisst die Kette (Bridge-Methode
 * umbenannt, `installAnchor` im Boot vergessen, CSS-Override entfernt), fällt das
 * sonst erst am Gerät auf.
 */
class EditorHostTypewriterAnchorTest {

    @Test fun `native side can set the anchor and it reaches the engine host`() {
        val host = asset("host.html")
        assertTrue(
            "host.html muss die Bridge-Methode setTypewriterAnchor anbieten",
            Regex("""setTypewriterAnchor\s*\(""").containsMatchIn(host),
        )
        assertTrue(
            "der Anker muss als Getter am Engine-Host hängen (jeder Recenter liest neu)",
            host.contains("Object.defineProperty(host, 'typewriterAnchor'"),
        )
        assertTrue(
            "installAnchor muss nach dem Mount aufgerufen werden (boot)",
            Regex("""__sw\.installAnchor\(""").containsMatchIn(host),
        )
    }

    @Test fun `anchor drives the scroll buffers of the writing surface`() {
        val host = asset("host.html")
        for (prop in listOf("--sw-pad-top", "--sw-pad-bottom")) {
            assertTrue(
                "host.html muss $prop aus dem Anker setzen",
                host.contains("'$prop'"),
            )
        }
        val css = asset("editor-host.css")
        assertTrue(
            "editor-host.css muss die Puffer aus --sw-pad-top/--sw-pad-bottom beziehen — " +
                "mit !important, weil das Bundle-CSS danach injiziert wird",
            Regex("""padding-top:\s*var\(--sw-pad-top[^)]*\)\s*!important""").containsMatchIn(css)
                && Regex("""padding-bottom:\s*var\(--sw-pad-bottom[^)]*\)\s*!important""")
                    .containsMatchIn(css),
        )
    }

    /** Liest ein Host-Asset relativ zum Modul-Verzeichnis (CWD der Unit-Tests). */
    private fun asset(name: String): String {
        val rel = "src/main/assets/editor-host/$name"
        val f = listOf(File(rel), File("app/$rel")).firstOrNull { it.exists() }
            ?: error("Asset nicht gefunden: $rel (cwd=${File(".").absolutePath})")
        return f.readText()
    }
}
