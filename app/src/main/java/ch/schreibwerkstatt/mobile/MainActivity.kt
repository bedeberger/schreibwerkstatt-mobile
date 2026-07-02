package ch.schreibwerkstatt.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.schreibwerkstatt.mobile.data.prefs.ThemeMode
import ch.schreibwerkstatt.mobile.ui.AppNav
import ch.schreibwerkstatt.mobile.ui.research.SharedResearch
import ch.schreibwerkstatt.mobile.ui.research.parseShareIntent
import ch.schreibwerkstatt.mobile.ui.theme.SchreibwerkstattTheme

class MainActivity : ComponentActivity() {
    // Über einen ACTION_SEND-Intent geteilter Inhalt (Recherche-Erfassung). Als
    // Compose-State gehalten, damit AppNav bei neuem Share (onNewIntent, singleTop)
    // reagieren kann. Wird vom Capture-Screen nach Speichern/Abbruch geleert.
    private var sharedResearch by mutableStateOf<SharedResearch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedResearch = parseShareIntent(intent)
        val serviceLocator = locator
        setContent {
            val themeMode by serviceLocator.settings.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SchreibwerkstattTheme(darkTheme = darkTheme) {
                AppNav(
                    locator = serviceLocator,
                    sharedResearch = sharedResearch,
                    onSharedConsumed = { sharedResearch = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseShareIntent(intent)?.let { sharedResearch = it }
    }
}
