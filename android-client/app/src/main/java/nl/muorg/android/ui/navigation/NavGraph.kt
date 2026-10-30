package nl.muorg.android.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import nl.muorg.android.data.preferences.AppPreferences
import nl.muorg.android.ui.UiMessage
import nl.muorg.android.ui.UiMessenger
import nl.muorg.android.ui.component.BottomIsland
import nl.muorg.android.ui.component.IslandTab
import nl.muorg.android.ui.component.LibraryChromeHost
import nl.muorg.android.ui.component.LocalBottomInset
import nl.muorg.android.ui.component.LocalIslandAccent
import nl.muorg.android.ui.component.LocalLibraryChromeHost
import nl.muorg.android.ui.component.TrackActionsSheet
import nl.muorg.android.ui.component.rememberDominantColor
import nl.muorg.android.ui.player.PlayerViewModel
import nl.muorg.android.ui.screen.library.LibraryScreen
import nl.muorg.android.ui.theme.MuorgTheme
import nl.muorg.android.ui.theme.artworkAccent

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Home : Screen("home")
    object Search : Screen("search")
    object MixDetail : Screen("mix/{mixId}") {
        fun createRoute(mixId: Int) = "mix/$mixId"
    }
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
    object Reports : Screen("reports")
    object ReportDetail : Screen("reports/{kind}") {
        fun createRoute(kind: nl.muorg.android.util.LibraryReports.Kind) = "reports/${kind.name}"
    }
}

/**
 * The web's `nav-tabs.ts`, verbatim — home, search, library, playlists,
 * settings, with the filled Mage variant standing in for the active state
 * (the Mage set has no filled search glyph, so Search keeps the same one).
 * Queue is deliberately absent: it is reached from the mini player, not as a
 * tab.
 */
private val islandTabs = listOf(
    Screen.Home to IslandTab("home-2", "home-2-fill", "Home"),
    Screen.Search to IslandTab("search", "search", "Search"),
    Screen.Library to IslandTab("compact-disk", "compact-disk-fill", "Library"),
    Screen.Playlists to IslandTab("library-music", "library-music-fill", "Playlists"),
    Screen.Settings to IslandTab("settings", "settings-fill", "Settings"),
)

/** Which tab owns a route; detail screens keep their parent tab lit. */
private fun tabIndexForRoute(route: String?): Int = when {
    route == null -> 0
    route.startsWith("album") -> 2
    route.startsWith("library") -> 2
    route.startsWith("playlist") -> 3
    route == Screen.Settings.route -> 4
    route == Screen.Home.route -> 0
    route == Screen.Search.route -> 1
    route.startsWith("mix/") -> 0
    else -> -1
}

@HiltViewModel
class NavViewModel @Inject constructor(
    private val preferences: AppPreferences,
    val imageLoader: ImageLoader,
    private val uiMessenger: UiMessenger,
) : ViewModel() {
    val messages: SharedFlow<UiMessage> = uiMessenger.messages
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

    // Re-tapping the Search tab focuses its input and opens the keyboard.
    private val _searchFocusSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchFocusSignal: SharedFlow<Unit> = _searchFocusSignal.asSharedFlow()

    /**
     * Emitted slightly after the tab tap: the island's navigate() pops and
     * restores the search entry (saveState/restoreState), so the screen's
     * collector is recreated — an immediate emission would be consumed by the
     * outgoing instance and never reach the new one.
     */
    fun requestSearchFocus() {
        viewModelScope.launch {
            delay(350)
            _searchFocusSignal.tryEmit(Unit)
        }
    }
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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        navViewModel.messages.collect { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                withDismissAction = false,
                duration = if (msg.actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) msg.onAction?.invoke()
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

    // Scrolling into a page collapses the island down to just the mini player
    // and hands the freed height back to the content; scrolling back up
    // restores it. LocalBottomInset is measured from the island, so every
    // scroller follows without knowing anything about this.
    //
    // Keyed off what the child actually CONSUMED, not off the raw gesture: a
    // page whose content fits on screen consumes nothing, so it never hides
    // its own navigation out from under the user.
    var barsCollapsed by remember { mutableStateOf(false) }
    val collapseOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // USER INPUT ONLY. A tab restoring its saved scroll offset
                // dispatches a programmatic scroll the moment it mounts, which
                // collapsed the bar the instant you arrived — the search row
                // appeared and then folded away on its own.
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (consumed.y < -6f) barsCollapsed = true
                else if (consumed.y > 6f) barsCollapsed = false
                return Offset.Zero
            }
        }
    }
    // Arriving somewhere new always shows the full island.
    LaunchedEffect(currentDestination?.route) { barsCollapsed = false }

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
    // INSIDE the theme: artworkAccent falls back to `colorScheme.primary` and
    // reads `surface` to decide which way to push lightness. Called outside,
    // both resolve against Material's default purple scheme.
    val islandAccent = artworkAccent(islandBloom)
    val chromeHost = remember { LibraryChromeHost() }
    val density = LocalDensity.current
    var islandHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(
        LocalLibraryChromeHost provides chromeHost,
        LocalIslandAccent provides islandAccent,
        LocalBottomInset provides islandHeight,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.fillMaxSize().nestedScroll(collapseOnScroll)) {
            MuorgNavHost(
                navController = navController,
                navViewModel = navViewModel,
                playerViewModel = playerViewModel,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onOpenQueue = onOpenQueue,
            )

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
                        if (screen is Screen.Search && alreadyThere) {
                            navViewModel.requestSearchFocus()
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
                    queueActive = currentDestination?.route == Screen.Queue.route,
                    showTabs = selectedTab >= 0 && !barsCollapsed,
                    // Driven by the destination, not by LibraryScreen's disposal:
                    // the outgoing route stays composed for the whole 320ms exit
                    // transition, so keying off disposal leaves the search bar
                    // sitting in the island long after the view has gone.
                    chrome = if (selectedTab == 2 && currentDestination?.route?.startsWith("library") == true) {
                        chromeHost.content
                    } else {
                        null
                    },
                    chromeExpanded = !barsCollapsed,
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
                        onRenameFile = { newPath ->
                            miniPlayerSheet = false
                            playerViewModel.renameTrackFile(track, newPath)
                        },
                    )
                }
            }

            CastVolumeHud(
                volume = castVolume,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = islandHeight + 12.dp),
            )
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
