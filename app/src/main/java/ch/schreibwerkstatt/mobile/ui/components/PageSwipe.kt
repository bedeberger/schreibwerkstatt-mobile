package ch.schreibwerkstatt.mobile.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Horizontale „Blätter"-Geste über beliebigem Inhalt – auch über einer WebView,
 * weil im [PointerEventPass.Initial] entschieden und konsumiert wird (so kommt
 * eine eindeutig horizontale Wischbewegung dem Kind zuvor, während vertikales
 * Scrollen und Taps unangetastet durchgereicht werden).
 *
 * Wisch nach links → [onNext], nach rechts → [onPrev]. Ist ein Callback `null`,
 * gibt es in diese Richtung keine Nachbarseite und die Geste bleibt inaktiv.
 */
fun Modifier.pageFlipGestures(
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
): Modifier = composed {
    val vc = LocalViewConfiguration.current
    val touchSlop = vc.touchSlop
    pointerInput(onPrev, onNext) {
        // Schwelle, ab der ein horizontaler Wisch als Blättern gilt.
        val flingThreshold = 96.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var horizontal = false
            var bailed = false
            var totalX = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                totalX = dx
                if (!horizontal && !bailed) {
                    when {
                        abs(dy) > touchSlop && abs(dy) >= abs(dx) -> bailed = true // vertikal → WebView scrollen lassen
                        abs(dx) > touchSlop && abs(dx) > abs(dy) -> horizontal = true
                    }
                }
                if (horizontal) change.consume()
                if (!change.pressed) break
            }
            if (horizontal && abs(totalX) > flingThreshold) {
                if (totalX < 0) onNext?.invoke() else onPrev?.invoke()
            }
        }
    }
}
