package ch.schreibwerkstatt.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ch.schreibwerkstatt.mobile.R

// Marken-Fonts (self-hosted, auf Latin/Latin-Ext subgesettet) — spiegeln das
// Mutterprojekt: Inter = UI/Sans, Source Serif 4 = Lese-/Überschriftentext.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val SourceSerif = FontFamily(
    Font(R.font.source_serif_regular, FontWeight.Normal),
    Font(R.font.source_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.source_serif_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif_bold, FontWeight.Bold),
)

// Material3-Default-Typografie als Basis, Familien marken-konform überschrieben:
// Display/Headline/Title in Serif (Buch-/Leseanmutung), Body/Label in Inter.
private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SourceSerif),
    displayMedium = base.displayMedium.copy(fontFamily = SourceSerif),
    displaySmall = base.displaySmall.copy(fontFamily = SourceSerif),
    headlineLarge = base.headlineLarge.copy(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = SourceSerif, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)

// Explizite Lese-Style-Variante für lange Fliesstexte (Page-/Editor-Vorschau),
// 16sp analog --font-size-reading des Mutterprojekts.
val ReadingTextStyle: TextStyle = TextStyle(
    fontFamily = SourceSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 26.sp,
)
