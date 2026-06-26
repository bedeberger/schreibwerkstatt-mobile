package ch.schreibwerkstatt.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.schreibwerkstatt.mobile.R
import ch.schreibwerkstatt.mobile.locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context.locator))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.pairing_server_label)) },
                supportingContent = { Text(state.serverUrl.ifBlank { "—" }) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.pairing_device_label)) },
                supportingContent = { Text(state.deviceName) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_stt_status)) },
                supportingContent = { Text(state.sttStatus) },
            )
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { vm.signOut(); onSignedOut() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_signout))
                }
                Text(
                    "Abmelden entfernt nur das lokale Token von diesem Gerät. " +
                        "Den Token serverseitig zu widerrufen ist über die Web-Oberfläche möglich.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
