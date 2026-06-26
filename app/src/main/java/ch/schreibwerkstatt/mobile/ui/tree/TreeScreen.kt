package ch.schreibwerkstatt.mobile.ui.tree

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.schreibwerkstatt.mobile.locator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(
    bookId: Long,
    bookTitle: String,
    onBack: () -> Unit,
    onOpenPage: (pageId: Long, pageName: String) -> Unit,
) {
    val context = LocalContext.current
    val vm: TreeViewModel = viewModel(factory = TreeViewModel.factory(context.locator, bookId))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bookTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null && state.rows.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { Text("Fehler: ${state.error}", color = MaterialTheme.colorScheme.error) }

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
                        is TreeRow.Page -> ListItem(
                            headlineContent = { Text(row.name) },
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
