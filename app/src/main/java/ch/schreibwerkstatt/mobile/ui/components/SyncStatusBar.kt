package ch.schreibwerkstatt.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.schreibwerkstatt.mobile.R

/**
 * Schmaler Statusstreifen, der den Offline-Zustand bzw. noch nicht
 * synchronisierte Pending-Writes sichtbar macht. Blendet sich aus, wenn online
 * und nichts in der Queue hängt.
 *
 * Speist sich aus [ch.schreibwerkstatt.mobile.data.repo.SyncCoordinator]
 * (`online` / `pendingCount`).
 */
@Composable
fun SyncStatusBar(
    online: Boolean,
    pendingCount: Int,
    modifier: Modifier = Modifier,
) {
    val visible = !online || pendingCount > 0
    AnimatedVisibility(visible = visible) {
        val offline = !online
        val container = if (offline) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
        val content = if (offline) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }
        val text = when {
            offline && pendingCount > 0 ->
                pluralStringResource(R.plurals.sync_pending_offline, pendingCount, pendingCount)
            offline ->
                stringResource(R.string.sync_offline)
            else ->
                pluralStringResource(R.plurals.sync_pending_online, pendingCount, pendingCount)
        }
        Surface(
            color = container,
            contentColor = content,
            // Als Live-Region zusammengefasst: TalkBack liest beim Ein-/Umblenden
            // den Statustext vor. Das Icon bleibt dekorativ (contentDescription=null),
            // da der Text den Zustand bereits vollständig benennt.
            modifier = modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = if (offline) Icons.Filled.CloudOff else Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
