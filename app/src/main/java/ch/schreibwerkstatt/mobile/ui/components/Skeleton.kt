package ch.schreibwerkstatt.mobile.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Endlose 0→1-Phase, die den „Glanz" über die Skeleton-Flächen wandern lässt.
 * `RepeatMode.Restart` über eine volle Sweep-Breite, die das Band auf beiden
 * Seiten komplett verlässt — so gibt es keinen sichtbaren Sprung am Zyklusende.
 */
@Composable
private fun rememberShimmerPhase(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    return phase
}

// Geteilte Phase für alle Blöcke innerhalb einer Skeleton-Liste/-Absatzgruppe,
// damit die Zeilen synchron schimmern statt phasenverschoben zu flimmern.
private val LocalShimmerPhase = compositionLocalOf<Float?> { null }

/** Stellt allen [ShimmerBlock]s im [content] eine einzige, geteilte Schimmer-Phase bereit. */
@Composable
private fun ProvideShimmer(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShimmerPhase provides rememberShimmerPhase(), content = content)
}

/** Geteilte Phase, falls vorhanden – sonst eine eigene (für einzeln stehende Blöcke). */
@Composable
private fun shimmerPhase(): Float = LocalShimmerPhase.current ?: rememberShimmerPhase()

/**
 * Einzelner schimmernder Block (z.B. eine Textzeile) mit abgerundeten Ecken.
 * Das Glanz-Band wird relativ zur **tatsächlichen Blockbreite** geführt, damit
 * der Sweep auf schmalen wie breiten Flächen gleichmässig wirkt (kein Flackern).
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val phase = shimmerPhase()
    var widthPx by remember { mutableFloatStateOf(0f) }
    val base = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    // Bandbreite wächst mit dem Block; der Sweep startet links ausserhalb und
    // endet rechts ausserhalb, sodass der Restart unsichtbar bleibt.
    val band = widthPx * 0.7f + 200f
    val x = phase * (widthPx + band) - band
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + band, 0f),
    )
    Spacer(
        modifier
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .clip(shape)
            .background(brush),
    )
}

/** Skeleton-Zeile in der Form eines [androidx.compose.material3.ListItem] (Icon + zwei Textzeilen). */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier, showSupporting: Boolean = true) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShimmerBlock(Modifier.size(24.dp), height = 24.dp, shape = CircleShape)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            ShimmerBlock(Modifier.fillMaxWidth(0.6f), height = 16.dp)
            if (showSupporting) ShimmerBlock(Modifier.fillMaxWidth(0.35f), height = 12.dp)
        }
    }
}

/**
 * Liste schimmernder Platzhalter-Zeilen für den ersten Ladezustand einer Liste.
 * Ersetzt den `CircularProgressIndicator`, damit die Wartezeit als Vorschau auf
 * die kommende Liste erscheint.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    showSupporting: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    ProvideShimmer {
        LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = contentPadding) {
            items(rows) { SkeletonListItem(showSupporting = showSupporting) }
        }
    }
}

/** Mehrere schimmernde Absatz-Zeilen (z.B. Editor-Inhalt beim Laden). */
@Composable
fun SkeletonParagraphs(modifier: Modifier = Modifier, lines: Int = 6) {
    ProvideShimmer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(lines) { i ->
                // Letzte Zeile eines „Absatzes" kürzer, damit es wie Fliesstext wirkt.
                val fraction = if (i % 3 == 2) 0.5f else 1f
                ShimmerBlock(Modifier.fillMaxWidth(fraction), height = 14.dp)
            }
        }
    }
}
