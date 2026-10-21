package nl.muorg.android.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable as navComposable
import androidx.navigation.navArgument
import coil.ImageLoader
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.screen.album.AlbumDetailScreen
import nl.muorg.android.ui.screen.connect.ConnectScreen
import nl.muorg.android.ui.screen.home.HomeScreen
import nl.muorg.android.ui.screen.library.LibraryScreen
import nl.muorg.android.ui.screen.player.PlayerScreen
import nl.muorg.android.ui.screen.playlist.PlaylistAlbumsScreen
import nl.muorg.android.ui.screen.playlists.PlaylistsScreen
import nl.muorg.android.ui.screen.reports.ReportDetailScreen
import nl.muorg.android.ui.screen.reports.ReportsScreen
import nl.muorg.android.ui.screen.settings.SettingsScreen
import nl.muorg.android.ui.screen.welcome.WelcomeScreen

/**
 * One sheet-style transition for every route: the new page rises over the old.
 * No alpha on either spec — a translucent sheet shows the page beneath through
 * it and the two read as one mixed-up screen.
 */
private val sheetEnter: EnterTransition =
    slideInVertically(
        initialOffsetY = { it },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
    )

private val sheetPopExit: ExitTransition =
    slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(280, easing = FastOutLinearInEasing),
    )

/**
 * Every route draws on an opaque background. The app background lives once
 * behind the whole NavHost and most screens are transparent, so while two
 * routes are composed at once during a transition the old one showed through
 * the new one.
 */
private fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    navComposable(route = route, arguments = arguments) { entry ->
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content(entry)
        }
    }
}

/**
 * Every route in the app, and the transitions between them.
 *
 * Split out of [NavGraph], which was a 457-line composable holding the route
 * table, the bottom island, the cast HUD and the scroll-collapse plumbing at
 * once. What is left there is the chrome around this.
 */
@Composable
fun MuorgNavHost(
    navController: NavHostController,
    navViewModel: NavViewModel,
    playerViewModel: PlayerViewModel,
    baseUrl: String,
    imageLoader: ImageLoader,
    onOpenQueue: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
        // One sheet-style transition for every route: the new page rises over
        // the stationary old one; back slides it down again.
        enterTransition = { sheetEnter },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { sheetPopExit },
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { sheetPopExit },
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToLibrary = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToRemoteSetup = {
                    navController.navigate(Screen.Connect.route)
                },
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onOpenAlbum = { albumName ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                },
                onOpenMix = { mixId ->
                    navController.navigate(Screen.MixDetail.createRoute(mixId))
                },
            )
        }

        composable(
            route = Screen.MixDetail.route,
            arguments = listOf(navArgument("mixId") { type = NavType.IntType }),
        ) { backStackEntry ->
            nl.muorg.android.ui.screen.mix.MixDetailScreen(
                mixId = backStackEntry.arguments?.getInt("mixId") ?: -1,
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onViewArtist = { artistName ->
                    navController.navigate(Screen.Library.createRoute(artistFilter = artistName))
                },
                onViewAlbum = { albumName ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                },
            )
        }

        composable(Screen.Connect.route) {
            ConnectScreen(
                onConnected = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Library.route,
            arguments = listOf(navArgument("artistFilter") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStackEntry ->
            val artistFilter = backStackEntry.arguments?.getString("artistFilter")
            LibraryScreen(
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                artistFilter = artistFilter,
                onAlbumClick = { albumName ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                },
                onOpenQueue = onOpenQueue,
                onViewArtist = { artistName ->
                    navController.navigate(Screen.Library.createRoute(artistFilter = artistName))
                },
                scrollToActiveSignal = navViewModel.scrollToActiveSignal,
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(
                navArgument("albumName") { type = NavType.StringType },
                navArgument("playlistId") { type = NavType.IntType; defaultValue = -1 },
            )
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("albumName") ?: ""
            val albumName = java.net.URLDecoder.decode(encoded, "UTF-8")
            val filterPlaylistId = backStackEntry.arguments?.getInt("playlistId")?.takeIf { it != -1 }
            AlbumDetailScreen(
                albumName = albumName,
                filterPlaylistId = filterPlaylistId,
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onOpenQueue = onOpenQueue,
                onViewArtist = { artistName ->
                    navController.navigate(Screen.Library.createRoute(artistFilter = artistName))
                },
            )
        }

        composable(
            route = Screen.Player.route,
        ) {
            PlayerScreen(
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
                onOpenQueue = onOpenQueue,
                onViewArtist = { artistName ->
                    navController.popBackStack()
                    navController.navigate(Screen.Library.createRoute(artistFilter = artistName))
                },
                onViewAlbum = { albumName ->
                    navController.popBackStack()
                    navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                },
            )
        }

        composable(
            route = Screen.Queue.route,
        ) {
            nl.muorg.android.ui.screen.queue.QueueScreen(
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.PlaylistAlbums.createRoute(playlistId))
                },
                onOpenQueue = onOpenQueue,
            )
        }

        composable(
            route = Screen.PlaylistAlbums.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val currentPlaylistId = backStackEntry.arguments?.getInt("playlistId") ?: -1
            PlaylistAlbumsScreen(
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onAlbumClick = { albumName ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumName, currentPlaylistId.takeIf { it != -1 }))
                },
                onBack = { navController.popBackStack() },
                onOpenQueue = onOpenQueue,
                onViewArtist = { artistName ->
                    navController.navigate(Screen.Library.createRoute(artistFilter = artistName))
                },
            )
        }

        composable(
            route = Screen.Settings.route,
        ) {
            SettingsScreen(
                onLoggedOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenReports = { navController.navigate(Screen.Reports.route) },
            )
        }

        composable(Screen.Reports.route) {
            ReportsScreen(
                onBack = { navController.popBackStack() },
                onOpenReport = { kind ->
                    navController.navigate(Screen.ReportDetail.createRoute(kind))
                },
            )
        }

        composable(
            route = Screen.ReportDetail.route,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { backStackEntry ->
            ReportDetailScreen(
                kind = nl.muorg.android.util.LibraryReports.Kind
                    .fromRouteArg(backStackEntry.arguments?.getString("kind")),
                playerViewModel = playerViewModel,
                imageLoader = imageLoader,
                baseUrl = baseUrl,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
