package ch.schreibwerkstatt.mobile.ui.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.schreibwerkstatt.mobile.R
import ch.schreibwerkstatt.mobile.locator
import ch.schreibwerkstatt.mobile.ui.components.SyncStatusBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(
    bookId: Long,
    bookTitle: String,
    onBack: () -> Unit,
    onOpenPage: (pageId: Long, pageName: String) -> Unit,
) {
    val context = LocalContext.current
    val coordinator = remember(context) { context.locator.syncCoordinator }
    val vm: TreeViewModel = viewModel(factory = TreeViewModel.factory(context.locator, bookId))
    val state by vm.state.collectAsStateWithLifecycle()
    val online by coordinator.online.collectAsStateWithLifecycle()
    val pendingCount by coordinator.pendingCount.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(bookTitle) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                SyncStatusBar(online = online, pendingCount = pendingCount)
            }
        },
    ) { padding ->
        when {
            state.loading && state.rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null && state.rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.tree_load_error, state.error ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                itemsIndexed(state.rows) { _, row ->
                    val indent = (row.depth * 16).dp
                    when (row) {
                        is TreeRow.Chapter -> ListItem(
                            headlineContent = {
                                Text(row.name, fontWeight = FontWeight.SemiBold)
                            },
                            modifier = Modifier.padding(start = indent),
                        )
                        is TreeRow.Page -> {
                            val updated = formatUpdatedAt(state.pageUpdatedAt[row.id])
                            ListItem(
                                headlineContent = { Text(row.name) },
                                supportingContent = {
                                    Text(
                                        updated?.let { stringResource(R.string.tree_page_updated, it) }
                                            ?: stringResource(R.string.tree_page_updated_unknown),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null)
                                },
                                modifier = Modifier
                                    .padding(start = indent)
                                    .clickable { onOpenPage(row.id, row.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Lokalisiertes Anzeigedatum aus dem ISO-8601-`updated_at` des Servers; null = unbekannt/unparsebar. */
private val updatedAtFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

private fun formatUpdatedAt(iso: String?): String? =
    iso?.let { runCatching { updatedAtFormatter.format(Instant.parse(it)) }.getOrNull() }
