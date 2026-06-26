package ch.schreibwerkstatt.mobile.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.schreibwerkstatt.mobile.ServiceLocator
import ch.schreibwerkstatt.mobile.ui.books.BooksScreen
import ch.schreibwerkstatt.mobile.ui.components.UpdateDialog
import ch.schreibwerkstatt.mobile.ui.editor.EditorScreen
import ch.schreibwerkstatt.mobile.ui.pairing.PairingScreen
import ch.schreibwerkstatt.mobile.ui.settings.SettingsScreen
import ch.schreibwerkstatt.mobile.ui.tree.TreeScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private object Routes {
    const val PAIRING = "pairing"
    const val BOOKS = "books"
    const val TREE = "tree/{bookId}?title={title}"
    const val EDITOR = "editor/{bookId}/{pageId}?title={title}"
    const val SETTINGS = "settings"

    fun tree(bookId: Long, title: String) = "tree/$bookId?title=${Uri.encode(title)}"
    fun editor(bookId: Long, pageId: Long, title: String) =
        "editor/$bookId/$pageId?title=${Uri.encode(title)}"
}

@Composable
fun AppNav(locator: ServiceLocator) {
    val navController = rememberNavController()
    val isPaired by locator.tokenStore.isPaired.collectAsStateWithLifecycle()

    val start = if (isPaired) Routes.BOOKS else Routes.PAIRING

    val updateState by locator.updateManager.state.collectAsStateWithLifecycle()

    // Token-Verlust (401 / Abmelden) → zurück zum Pairing, Backstack leeren.
    LaunchedEffect(isPaired) {
        if (!isPaired) {
            navController.navigate(Routes.PAIRING) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            // Beim Start einmal still nach einem neuen Release prüfen.
            locator.updateManager.checkOnLaunch()
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.PAIRING) {
            PairingScreen(onPaired = {
                navController.navigate(Routes.BOOKS) {
                    popUpTo(Routes.PAIRING) { inclusive = true }
                }
            })
        }

        composable(Routes.BOOKS) {
            BooksScreen(
                onOpenBook = { book -> navController.navigate(Routes.tree(book.id, book.name)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            Routes.TREE,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            val title = entry.arguments?.getString("title").orEmpty()
            TreeScreen(
                bookId = bookId,
                bookTitle = title,
                onBack = { navController.popBackStack() },
                onOpenPage = { pageId, pageName ->
                    navController.navigate(Routes.editor(bookId, pageId, pageName))
                },
            )
        }

        composable(
            Routes.EDITOR,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType },
                navArgument("pageId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            val pageId = entry.arguments?.getLong("pageId") ?: return@composable
            val title = entry.arguments?.getString("title").orEmpty()
            EditorScreen(
                bookId = bookId,
                pageId = pageId,
                pageTitle = title.ifBlank { "Seite" },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = { /* isPaired-LaunchedEffect navigiert automatisch */ },
            )
        }
    }

    // Update-Dialog über allen Screens (Auto-Check beim Start + manueller Check in Settings).
    UpdateDialog(
        state = updateState,
        onDownload = {
            (updateState as? ch.schreibwerkstatt.mobile.update.UpdateState.Available)?.let {
                locator.updateManager.download(it.release)
            }
        },
        onRetryInstall = { locator.updateManager.retryInstall() },
        onOpenPermissionSettings = { locator.updateManager.openInstallPermissionSettings() },
        onDismiss = { locator.updateManager.dismiss() },
    )
}
