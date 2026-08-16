package nl.muorg.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import nl.muorg.android.data.preferences.AppPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import nl.muorg.android.ui.component.BottomIsland
import nl.muorg.android.ui.component.rememberDominantColor
import nl.muorg.android.ui.component.IslandTab
import nl.muorg.android.ui.component.LibraryChromeHost
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.LocalLibraryChromeHost
import nl.muorg.android.ui.component.TrackActionsSheet
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.screen.home.HomeScreen
import nl.muorg.android.ui.theme.MuorgTheme
import nl.muorg.android.ui.screen.album.AlbumDetailScreen
import nl.muorg.android.ui.screen.connect.ConnectScreen
import nl.muorg.android.ui.screen.library.LibraryScreen
import nl.muorg.android.ui.screen.welcome.WelcomeScreen
import nl.muorg.android.ui.screen.player.PlayerScreen
import nl.muorg.android.ui.screen.playlist.PlaylistAlbumsScreen
import nl.muorg.android.ui.screen.playlists.PlaylistsScreen
import nl.muorg.android.ui.screen.settings.SettingsScreen
import javax.inject.Inject

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Home : Screen("home")
    object Connect : Screen("connect")
    object Library : Screen("library?artistFilter={artistFilter}") {
        fun createRoute(artistFilter: String? = null) =
            if (artistFilter == null) "library"
            else "library?artistFilter=${java.net.URLEncoder.encode(artistFilter, "UTF-8").replace("+", "%20")}"
    }
    object Player : Screen("player")
    object Playlists : Screen("playlists")
    object Settings : Screen("settings")
    object Queue : Screen("queue")
    object AlbumDetail : Screen("album/{albumName}?playlistId={playlistId}") {
        fun createRoute(albumName: String, playlistId: Int? = null) =
            "album/${java.net.URLEncoder.encode(albumName, "UTF-8")}" +
            (if (playlistId != null) "?playlistId=$playlistId" else "")
    }
    object PlaylistAlbums : Screen("playlist/{playlistId}/albums") {
        fun createRoute(playlistId: Int) = "playlist/$playlistId/albums"
    }
}

/**
 * The web's `nav-tabs.ts`, verbatim — home, library, playlists, settings, with
 * the filled Mage variant standing in for the active state. Queue is
 * deliberately absent: it is reached from the mini player, not as a tab.
 */
private val islandTabs = listOf(
    Screen.Home to IslandTab("home-2", "home-2-fill", "Home"),
    Screen.Library to IslandTab("compact-disk", "compact-disk-fill", "Library"),
    Screen.Playlists to IslandTab("dashboard", "dashboard-fill", "Playlists"),
    Screen.Settings to IslandTab("settings", "settings-fill", "Settings"),
)

/** Which tab owns a route; detail screens keep their parent tab lit. */
private fun tabIndexForRoute(route: String?): Int = when {
    route == null -> 0
    route.startsWith("album") -> 1
    route.startsWith("library") -> 1
    route.startsWith("playlist") -> 2
    route == Screen.Settings.route -> 3
    route == Screen.Home.route -> 0
    else -> -1
}

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

    val useTrueBlack: StateFlow<Boolean> = preferences.useTrueBlack.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val playerBarTapOpensPlayer: StateFlow<Boolean> = preferences.playerBarTapOpensPlayer.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

    val materialYou: StateFlow<Boolean> = preferences.materialYou.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val themeMode: StateFlow<String> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "dark",
    )

    private val _scrollToActiveSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToActiveSignal: SharedFlow<Unit> = _scrollToActiveSignal.asSharedFlow()

    fun requestScrollToActive() { _scrollToActiveSignal.tryEmit(Unit) }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route?.let { route ->
        route != Screen.Connect.route &&
            route != Screen.Welcome.route &&
            route != Screen.Player.route
    } ?: false

    val navViewModel: NavViewModel = hiltViewModel()
    val playerViewModel: PlayerViewModel = hiltViewModel()

    var toastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        playerViewModel.toastEvent.collect { message ->
            toastMessage = message
            delay(2000)
            toastMessage = null
        }
    }

    val baseUrl by navViewModel.serverUrl.collectAsStateWithLifecycle()
    val useTrueBlack by navViewModel.useTrueBlack.collectAsStateWithLifecycle()
    val materialYou by navViewModel.materialYou.collectAsStateWithLifecycle()
    val themeMode by navViewModel.themeMode.collectAsStateWithLifecycle()
    val playerBarTapOpensPlayer by navViewModel.playerBarTapOpensPlayer.collectAsStateWithLifecycle()
    val imageLoader = navViewModel.imageLoader
    val castVolume by playerViewModel.castVolume.collectAsStateWithLifecycle()

    // The queue glyph is a TOGGLE, not a push: tapping it while the queue is
    // already open goes back, and it can never stack two queue screens.
    val onOpenQueue: () -> Unit = {
        if (currentDestination?.route == Screen.Queue.route) {
            navController.popBackStack()
        } else {
            navController.navigate(Screen.Queue.route) { launchSingleTop = true }
        }
    }

    val onPlayerBarClick: () -> Unit =
        if (playerBarTapOpensPlayer) ({ navController.navigate(Screen.Player.route) })
        else ({ playerViewModel.playPause() })

    val playerState by playerViewModel.playerState.collectAsStateWithLifecycle()
    val playlists by playerViewModel.playlists.collectAsStateWithLifecycle()
    var miniPlayerSheet by remember { mutableStateOf(false) }

    // The island's colour wash comes from the playing artwork — the same thing
    // the web's saturated backdrop blur ends up showing.
    val bloomTrack = playerState.currentTrack
    val bloomModel: Any? = when {
        bloomTrack?.localCoverPath != null -> java.io.File(bloomTrack.localCoverPath!!)
        bloomTrack?.hasCover == true -> "$baseUrl/api/tracks/${bloomTrack.id}/cover"
        else -> null
    }
    val islandBloom = rememberDominantColor(
        url = bloomModel,
        imageLoader = imageLoader,
        fallback = Color.Transparent,
    ).color

    MuorgTheme(themeMode = themeMode, useTrueBlack = useTrueBlack, useMaterialYou = materialYou) {
    val chromeHost = remember { LibraryChromeHost() }
    val density = LocalDensity.current
    var islandHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalLibraryChromeHost provides chromeHost,
        LocalBottomInset provides islandHeight,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Welcome.route,
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

            if (showBottomBar) {
                val selectedTab = tabIndexForRoute(currentDestination?.route)
                BottomIsland(
                    bloom = islandBloom,
                    tabs = islandTabs.map { it.second },
                    selectedIndex = selectedTab.coerceAtLeast(0),
                    onSelectTab = { index ->
                        val screen = islandTabs[index].first
                        val alreadyThere = selectedTab == index
                        if (screen is Screen.Library && alreadyThere) {
                            navViewModel.requestScrollToActive()
                        }
                        val route = if (screen is Screen.Library) Screen.Library.createRoute() else screen.route
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    playerState = playerState,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onMiniPlayerClick = onPlayerBarClick,
                    onPlayPause = { playerViewModel.playPause() },
                    onNext = { playerViewModel.skipNext() },
                    onOpenQueue = onOpenQueue,
                    onTrackMenu = { miniPlayerSheet = true },
                    showTabs = selectedTab >= 0,
                    // Driven by the destination, not by LibraryScreen's disposal:
                    // the outgoing route stays composed for the whole 320ms exit
                    // transition, so keying off disposal leaves the search bar
                    // sitting in the island long after the view has gone.
                    chrome = if (selectedTab == 1 && currentDestination?.route?.startsWith("library") == true) {
                        chromeHost.content
                    } else {
                        null
                    },
                    chromeExpanded = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned {
                            islandHeight = with(density) { it.size.height.toDp() }
                        },
                )
            }

            playerState.currentTrack?.let { track ->
                if (miniPlayerSheet) {
                    TrackActionsSheet(
                        track = track,
                        playerViewModel = playerViewModel,
                        playlists = playlists,
                        isFavorite = track.id.toString() in playerState.favorites,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onDismiss = { miniPlayerSheet = false },
                        onAddToPlaylist = { id -> playerViewModel.addTrackToPlaylist(track, id); miniPlayerSheet = false },
                        onRemoveFromPlaylist = { id -> playerViewModel.removeTrackFromPlaylist(track, id); miniPlayerSheet = false },
                        onViewArtist = {
                            miniPlayerSheet = false
                            navController.navigate(Screen.Library.createRoute(artistFilter = track.displayArtist))
                        },
                        onViewAlbum = {
                            miniPlayerSheet = false
                            navController.navigate(Screen.AlbumDetail.createRoute(track.displayAlbum))
                        },
                        onSaveMetadata = { title, artist, album, albumArtist, genre, year ->
                            miniPlayerSheet = false
                            playerViewModel.saveMetadata(track, title, artist, album, albumArtist, genre, year)
                        },
                    )
                }
            }

            CastVolumeHud(
                volume = castVolume,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            AnimatedVisibility(
                visible = toastMessage != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = islandHeight + 12.dp),
                enter = fadeIn(tween(150)) + slideInVertically { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically { it / 2 },
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shadowElevation = 4.dp,
                ) {
                    Text(
                        text = toastMessage ?: "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }
    }
    } // MuorgTheme
}


@Composable
private fun CastVolumeHud(
    volume: Float?,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(volume) {
        if (volume != null) {
            visible = true
            delay(2200)
            visible = false
        } else {
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
        exit = fadeOut(tween(280)) + slideOutVertically { -it / 2 },
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val vol = volume ?: 0f
                val icon = when {
                    vol == 0f -> Icons.Filled.VolumeMute
                    vol < 0.5f -> Icons.Filled.VolumeDown
                    else -> Icons.Filled.VolumeUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { vol },
                    modifier = Modifier.widthIn(min = 100.dp, max = 160.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${(vol * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}
