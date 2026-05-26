package nl.muorg.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination
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
    object Library : Screen("library?artistFilter={artistFilter}") {
        fun createRoute(artistFilter: String? = null) =
            if (artistFilter == null) "library"
            else "library?artistFilter=${java.net.URLEncoder.encode(artistFilter, "UTF-8")}"
    }
    object Player : Screen("player")
    object Playlists : Screen("playlists")
    object Settings : Screen("settings")
    object Queue : Screen("queue")
    object AlbumDetail : Screen("album/{albumName}") {
        fun createRoute(albumName: String) =
            "album/${java.net.URLEncoder.encode(albumName, "UTF-8")}"
    }
}

private val bottomNavItems = listOf(
    Triple(Screen.Library, Icons.Filled.Home, "Library"),
    Triple(Screen.Playlists, Icons.AutoMirrored.Filled.PlaylistPlay, "Playlists"),
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
        route != Screen.Connect.route &&
            route != Screen.Welcome.route &&
            route != Screen.Player.route &&
            route != Screen.Queue.route
    } ?: false

    val navViewModel: NavViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()

    val baseUrl by navViewModel.serverUrl.collectAsStateWithLifecycle()
    val imageLoader = navViewModel.imageLoader

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = EnterTransition.None,
                exit = fadeOut(tween(200)),
            ) {
                AnimatedBottomNav(
                    items = bottomNavItems,
                    currentDestination = currentDestination,
                    onNavigate = { screen ->
                        val route = if (screen is Screen.Library) Screen.Library.createRoute() else screen.route
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
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
                            navController.navigate(Screen.Library.createRoute()) {
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
                            navController.navigate(Screen.Library.createRoute()) {
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
                        onPlayerBarClick = { navController.navigate(Screen.Player.route) },
                        onOpenQueue = { navController.navigate(Screen.Queue.route) },
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
                        onOpenQueue = { navController.navigate(Screen.Queue.route) },
                    )
                }

                composable(
                    route = Screen.Player.route,
                    enterTransition = {
                        slideInVertically(
                            initialOffsetY = { (it * 0.92f).toInt() },
                            animationSpec = tween(480, easing = androidx.compose.animation.core.EaseOutCubic),
                        ) + fadeIn(animationSpec = tween(320, delayMillis = 60))
                    },
                    exitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(360, easing = androidx.compose.animation.core.EaseInCubic),
                        ) + fadeOut(animationSpec = tween(240))
                    },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(360, easing = androidx.compose.animation.core.EaseInCubic),
                        ) + fadeOut(animationSpec = tween(240))
                    },
                ) {
                    PlayerScreen(
                        playerViewModel = playerViewModel,
                        imageLoader = imageLoader,
                        baseUrl = baseUrl,
                        onBack = { navController.popBackStack() },
                        onOpenQueue = { navController.navigate(Screen.Queue.route) },
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
                    enterTransition = {
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                        ) + fadeIn(animationSpec = tween(220))
                    },
                    exitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                        ) + fadeOut(animationSpec = tween(180))
                    },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = {
                        slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(280, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                        ) + fadeOut(animationSpec = tween(180))
                    },
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
                        onPlaylistClick = { _ ->
                            navController.navigate(Screen.Library.createRoute()) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onPlayerBarClick = { navController.navigate(Screen.Player.route) },
                        onOpenQueue = { navController.navigate(Screen.Queue.route) },
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

@Composable
private fun AnimatedBottomNav(
    items: List<Triple<Screen, androidx.compose.ui.graphics.vector.ImageVector, String>>,
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit,
) {
    val selectedIndex = items.indexOfFirst { (screen, _, _) ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }.coerceAtLeast(0)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val itemWidth = maxWidth / items.size
            val pillOffsetX by animateDpAsState(
                targetValue = itemWidth * selectedIndex + (itemWidth - 56.dp) / 2,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
                label = "pillX",
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = pillOffsetX)
                    .size(56.dp, 40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        RoundedCornerShape(20.dp),
                    )
            )

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, (screen, icon, label) ->
                    val selected = index == selectedIndex
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.22f else 1f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                        label = "iconScale$index",
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "iconColor$index",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onNavigate(screen) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = iconColor,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                    }
                }
            }
        }
    }
}
