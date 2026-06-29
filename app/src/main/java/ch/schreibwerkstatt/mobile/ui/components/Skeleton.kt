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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wischender Verlaufs-Pinsel für Skeleton-Platzhalter. Eine endlose Animation
 * verschiebt einen hellen „Glanz" horizontal über die Fläche, damit Ladezustände
 * lebendig wirken (statt eines harten Spinners). Farben aus dem aktiven Theme,
 * damit der Effekt in Hell und Dunkel passt.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "translate",
    )
    val base = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f),
    )
}

/** Einzelner schimmernder Block (z.B. eine Textzeile) mit abgerundeten Ecken. */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Spacer(
        modifier
            .height(height)
            .clip(shape)
            .background(shimmerBrush()),
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
    LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = contentPadding) {
        items(rows) { SkeletonListItem(showSupporting = showSupporting) }
    }
}

/** Mehrere schimmernde Absatz-Zeilen (z.B. Editor-Inhalt beim Laden). */
@Composable
fun SkeletonParagraphs(modifier: Modifier = Modifier, lines: Int = 6) {
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
