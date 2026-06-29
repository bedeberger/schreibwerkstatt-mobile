package ch.schreibwerkstatt.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.schreibwerkstatt.mobile.data.prefs.ThemeMode
import ch.schreibwerkstatt.mobile.ui.AppNav
import ch.schreibwerkstatt.mobile.ui.theme.SchreibwerkstattTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
                AppNav(serviceLocator)
            }
        }
    }
}
