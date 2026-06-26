package ch.schreibwerkstatt.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// === Marken-Palette (aus dem Logo / Mutterprojekt-Tokens) ===
// primary = Buchrücken-Blau (#1d4b73, AA mit Weiss), primary-light = Buchkörper
// (#2d6a9f), accent = Feder-Gold (#c4a55a), Paper-BG (#faf7f2), Navy (#1a1f3a).
private val BrandBlue = Color(0xFF1D4B73)
private val BrandBlueLight = Color(0xFF2D6A9F)
private val BrandGold = Color(0xFFC4A55A)
private val SparkBlue = Color(0xFF7EB8F0)
private val SparkPurple = Color(0xFFA78BFA)
private val PaperBg = Color(0xFFFAF7F2)
private val Navy = Color(0xFF1A1F3A)
private val InkText = Color(0xFF1F1C18)
private val ErrRed = Color(0xFFA32D2D)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F0),
    onPrimaryContainer = Color(0xFF0E2638),
    secondary = BrandGold,
    onSecondary = Color(0xFF2A2110),
    secondaryContainer = Color(0xFFF1E6C8),
    onSecondaryContainer = Color(0xFF4A3B14),
    tertiary = SparkPurple,
    onTertiary = Color.White,
    background = PaperBg,
    onBackground = InkText,
    surface = Color.White,
    onSurface = InkText,
    surfaceVariant = Color(0xFFEDE7DE),
    onSurfaceVariant = Color(0xFF6B6258),
    outline = Color(0xFFB8AEA2),
    error = ErrRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SparkBlue,
    onPrimary = Color(0xFF0E2638),
    primaryContainer = BrandBlue,
    onPrimaryContainer = Color(0xFFD6E4F0),
    secondary = BrandGold,
    onSecondary = Color(0xFF2A2110),
    secondaryContainer = Color(0xFF4A3B14),
    onSecondaryContainer = Color(0xFFF1E6C8),
    tertiary = SparkPurple,
    onTertiary = Color(0xFF1E1233),
    background = Navy,
    onBackground = Color(0xFFEDEAF2),
    surface = Color(0xFF222845),
    onSurface = Color(0xFFEDEAF2),
    surfaceVariant = Color(0xFF333A57),
    onSurfaceVariant = Color(0xFFC3BFCE),
    outline = Color(0xFF6E7393),
    error = Color(0xFFE24B4A),
    onError = Color(0xFF2A0A0A),
)

@Composable
fun SchreibwerkstattTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic Color (Material You) bewusst standardmässig AUS, damit die Marken-
    // farben konsistent bleiben — sonst überschreibt das System-Theme sie ab API 31.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
