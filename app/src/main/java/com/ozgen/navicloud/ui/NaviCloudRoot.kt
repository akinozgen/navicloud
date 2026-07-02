package com.ozgen.navicloud.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
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
import com.ozgen.navicloud.ui.components.MiniPlayer
import com.ozgen.navicloud.ui.components.SongMenuHost
import com.ozgen.navicloud.ui.screens.album.AlbumScreen
import com.ozgen.navicloud.ui.screens.artist.ArtistScreen
import com.ozgen.navicloud.ui.screens.home.HomeScreen
import com.ozgen.navicloud.ui.screens.library.LibraryScreen
import com.ozgen.navicloud.ui.screens.login.LoginScreen
import com.ozgen.navicloud.ui.screens.nowplaying.NowPlayingScreen
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
    var nowPlayingOpen by remember { mutableStateOf(false) }
    val artResolver = remember(server.id) { vm.artResolverFor(server) }

    CompositionLocalProvider(LocalArtResolver provides artResolver) {
        SongMenuHost(navController, onBeforeNavigate = { nowPlayingOpen = false }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    MiniPlayer(
                        state = playerState,
                        player = vm.player,
                        onExpand = { nowPlayingOpen = true },
                    )
                    val backStack by navController.currentBackStackEntryAsState()
                    val currentRoute = backStack?.destination?.route
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        windowInsets = NavigationBarDefaults.windowInsets,
                    ) {
                        // On a detail page restoreState would resurrect the very detail
                        // we're leaving ("button does nothing") — jump clean to tab root instead
                        val onTabRoot = tabs.any { it.route == currentRoute }
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
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
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

        AnimatedVisibility(
            visible = nowPlayingOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            NowPlayingScreen(
                onClose = { nowPlayingOpen = false },
                onOpenAlbum = { albumId ->
                    nowPlayingOpen = false
                    navController.navigate("album/$albumId")
                },
            )
        }
        }
    }
}
