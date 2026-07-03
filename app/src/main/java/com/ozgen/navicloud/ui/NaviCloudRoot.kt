package com.ozgen.navicloud.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.ui.components.LocalArtResolver
import com.ozgen.navicloud.ui.components.SongMenuHost
import com.ozgen.navicloud.ui.player.PlayerSheet
import com.ozgen.navicloud.ui.screens.album.AlbumScreen
import com.ozgen.navicloud.ui.screens.artist.ArtistScreen
import com.ozgen.navicloud.ui.screens.home.HomeScreen
import com.ozgen.navicloud.ui.screens.library.LibraryScreen
import com.ozgen.navicloud.ui.screens.login.LoginScreen
import com.ozgen.navicloud.ui.screens.nowplaying.NowPlayingViewModel
import com.ozgen.navicloud.ui.screens.playlist.PlaylistScreen
import com.ozgen.navicloud.ui.screens.search.SearchScreen
import com.ozgen.navicloud.ui.screens.servers.ServersScreen

@Composable
fun NaviCloudRoot(vm: AppViewModel = hiltViewModel()) {
    val appState by vm.appState.collectAsStateWithLifecycle()
    when (val s = appState) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AppState.NeedsLogin -> LoginScreen()
        is AppState.Ready -> key(s.server.id) { MainShell(vm, s.server) }
    }
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabs = listOf(
    BottomTab("home", "Ana Sayfa", Icons.Outlined.Home, Icons.Rounded.Home),
    BottomTab("search", "Ara", Icons.Outlined.Search, Icons.Rounded.Search),
    BottomTab("library", "Kitaplık", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
)

@Composable
private fun MainShell(vm: AppViewModel, server: Server) {
    val navController: NavHostController = rememberNavController()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val artResolver = remember(server.id) { vm.artResolverFor(server) }
    var collapseTick by remember { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalArtResolver provides artResolver) {
        SongMenuHost(navController, onBeforeNavigate = { collapseTick++ }) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        val backStack by navController.currentBackStackEntryAsState()
                        val currentRoute = backStack?.destination?.route
                        // On a detail page restoreState would resurrect the very detail
                        // we're leaving ("button does nothing") — jump clean to tab root instead
                        val onTabRoot = tabs.any { it.route == currentRoute }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.background,
                            windowInsets = NavigationBarDefaults.windowInsets,
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = onTabRoot }
                                            launchSingleTop = true
                                            restoreState = onTabRoot
                                        }
                                        // restoreState bazen kaydedilmiş stack'in tepesindeki
                                        // detayı geri getiriyor — sekme butonu HER ZAMAN
                                        // sekme köküne indirsin
                                        if (navController.currentDestination?.route != tab.route) {
                                            navController.popBackStack(tab.route, inclusive = false)
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.icon,
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    val hasPlayer = playerState.currentTrack != null
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        // Default 700ms crossfade feels sluggish — quick, subtle slide instead
                        enterTransition = {
                            slideInHorizontally(tween(200)) { it / 6 } + fadeIn(tween(150))
                        },
                        exitTransition = { fadeOut(tween(90)) },
                        popEnterTransition = { fadeIn(tween(120)) },
                        popExitTransition = {
                            slideOutHorizontally(tween(180)) { it / 6 } + fadeOut(tween(140))
                        },
                        modifier = Modifier
                            .padding(padding)
                            .padding(bottom = if (hasPlayer) 64.dp else 0.dp),
                    ) {
                        composable("home") { HomeScreen(navController) }
                        composable("search") { SearchScreen(navController) }
                        composable("library") { LibraryScreen(navController) }
                        composable("album/{id}") { entry ->
                            AlbumScreen(navController, entry.arguments?.getString("id").orEmpty())
                        }
                        composable("artist/{id}") { entry ->
                            ArtistScreen(navController, entry.arguments?.getString("id").orEmpty())
                        }
                        composable("playlist/{id}") { entry ->
                            PlaylistScreen(navController, entry.arguments?.getString("id").orEmpty())
                        }
                        composable("servers") { ServersScreen(navController) }
                    }
                }

                // Persistent morphing player sheet (mini bar <-> full player)
                if (playerState.currentTrack != null) {
                    val npVm: NowPlayingViewModel = hiltViewModel()
                    PlayerSheet(vm = npVm, collapseTick = collapseTick)
                }
            }
        }
    }
}
