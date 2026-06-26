package ch.schreibwerkstatt.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ch.schreibwerkstatt.mobile.ui.AppNav
import ch.schreibwerkstatt.mobile.ui.theme.SchreibwerkstattTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val serviceLocator = locator
        setContent {
            SchreibwerkstattTheme {
                AppNav(serviceLocator)
            }
        }
    }
}
