package nl.muorg.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.screen.album.AlbumDetailScreen
import nl.muorg.android.ui.screen.connect.ConnectScreen
import nl.muorg.android.ui.screen.library.LibraryScreen
import nl.muorg.android.ui.screen.welcome.WelcomeScreen
import nl.muorg.android.ui.screen.player.PlayerScreen
import nl.muorg.android.ui.screen.playlists.PlaylistsScreen
import nl.muorg.android.ui.screen.settings.SettingsScreen
import javax.inject.Inject

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Connect : Screen("connect")
    object Library : Screen("library")
    object Player : Screen("player")
    object Playlists : Screen("playlists")
    object Settings : Screen("settings")
    object AlbumDetail : Screen("album/{albumName}") {
        fun createRoute(albumName: String) =
            "album/${java.net.URLEncoder.encode(albumName, "UTF-8")}"
    }
}

private val bottomNavItems = listOf(
    Triple(Screen.Library, Icons.Filled.LibraryMusic, "Library"),
    Triple(Screen.Playlists, Icons.AutoMirrored.Filled.QueueMusic, "Playlists"),
    Triple(Screen.Settings, Icons.Filled.Settings, "Settings"),
)

@HiltViewModel
class NavViewModel @Inject constructor(
    private val preferences: AppPreferences,
    val imageLoader: ImageLoader,
) : ViewModel() {
    val serverUrl: StateFlow<String> = preferences.serverUrl.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route?.let { route ->
        route != Screen.Connect.route && route != Screen.Welcome.route
    } ?: false

    val navViewModel: NavViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val baseUrl by navViewModel.serverUrl.collectAsStateWithLifecycle()
    val imageLoader = navViewModel.imageLoader

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { (screen, icon, label) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Welcome.route,
            ) {
                composable(Screen.Welcome.route) {
                    WelcomeScreen(
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Welcome.route) { inclusive = true }
                            }
                        },
                        onNavigateToRemoteSetup = {
                            navController.navigate(Screen.Connect.route)
                        },
                    )
                }

                composable(Screen.Connect.route) {
                    ConnectScreen(
                        onConnected = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        playerViewModel = playerViewModel,
                        imageLoader = imageLoader,
                        baseUrl = baseUrl,
                        onAlbumClick = { albumName ->
                            navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                        },
                        onPlayerBarClick = { navController.navigate(Screen.Player.route) },
                        showPlayerBar = showBottomBar,
                    )
                }

                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encoded = backStackEntry.arguments?.getString("albumName") ?: ""
                    val albumName = java.net.URLDecoder.decode(encoded, "UTF-8")
                    AlbumDetailScreen(
                        albumName = albumName,
                        playerViewModel = playerViewModel,
                        imageLoader = imageLoader,
                        baseUrl = baseUrl,
                        onBack = { navController.popBackStack() },
                        onPlayerBarClick = { navController.navigate(Screen.Player.route) },
                    )
                }

                composable(Screen.Player.route) {
                    PlayerScreen(
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
                        onPlaylistClick = { _ ->
                            navController.navigate(Screen.Library.route) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onPlayerBarClick = { navController.navigate(Screen.Player.route) },
                        showPlayerBar = showBottomBar,
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onLoggedOut = {
                            navController.navigate(Screen.Welcome.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
