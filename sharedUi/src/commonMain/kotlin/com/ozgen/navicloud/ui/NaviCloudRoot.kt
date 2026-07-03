package com.ozgen.navicloud.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun NaviCloudRoot(
    platformSettings: @Composable (NavHostController) -> Unit,
    vm: AppViewModel = containerViewModel { AppViewModel(it.servers, it.player) },
) {
    val appState by vm.appState.collectAsStateWithLifecycle()
    when (val s = appState) {
        AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        AppState.NeedsLogin -> LoginScreen()
        is AppState.Ready -> key(s.server.id) { MainShell(vm, s.server, platformSettings) }
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
private fun MainShell(vm: AppViewModel, server: Server, platformSettings: @Composable (NavHostController) -> Unit) {
    val navController: NavHostController = rememberNavController()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    val artResolver = remember(server.id) { vm.artResolverFor(server) }
    var collapseTick by remember { mutableIntStateOf(0) }

    // Sekme butonu her zaman sekme köküne indirir; detay sayfadayken
    // restoreState kaydedilmiş detayı geri getirip "buton çalışmıyor"
    // hissi veriyordu
    fun navigateTab(route: String, onTabRoot: Boolean) {
        // Rail'li düzende sekmeler açık player'ın yanında da görünür —
        // navigasyon açık player/kuyruğu kapatsın (telefonda no-op)
        collapseTick++
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = onTabRoot }
            launchSingleTop = true
            restoreState = onTabRoot
        }
        if (navController.currentDestination?.route != route) {
            navController.popBackStack(route, inclusive = false)
        }
    }

    val appNavHost: @Composable (Modifier) -> Unit = { navModifier ->
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
            modifier = navModifier,
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
            composable("servers") { platformSettings(navController) }
        }
    }

    CompositionLocalProvider(LocalArtResolver provides artResolver) {
        SongMenuHost(navController, onBeforeNavigate = { collapseTick++ }) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Tablet / yatay telefon / (ileride) masaüstü: alt sekmeler yerine
                // sol rail — aynı arayüz, geniş ekrana uyarlanmış kabuk
                val wide = maxWidth >= 600.dp
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                val onTabRoot = tabs.any { it.route == currentRoute }
                val hasPlayer = playerState.currentTrack != null

                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.background,
                        ) {
                            Spacer(Modifier.weight(1f))
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTab(tab.route, onTabRoot) },
                                    icon = {
                                        Icon(
                                            if (selected) tab.selectedIcon else tab.icon,
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = Color.Transparent,
                                    ),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                                appNavHost(
                                    Modifier
                                        .padding(padding)
                                        .padding(bottom = if (hasPlayer) 64.dp else 0.dp),
                                )
                            }
                            if (hasPlayer) {
                                val npVm: NowPlayingViewModel = containerViewModel { NowPlayingViewModel(it.music, it.player) }
                                // Rail'in sağındaki içerik alanına gömülü; bottom nav
                                // olmadığı için mini bar en alta oturur
                                PlayerSheet(vm = npVm, collapseTick = collapseTick, bottomBarHeight = 0.dp)
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    windowInsets = NavigationBarDefaults.windowInsets,
                                ) {
                                    tabs.forEach { tab ->
                                        val selected = currentRoute == tab.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateTab(tab.route, onTabRoot) },
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
                            appNavHost(
                                Modifier
                                    .padding(padding)
                                    .padding(bottom = if (hasPlayer) 64.dp else 0.dp),
                            )
                        }

                        // Persistent morphing player sheet (mini bar <-> full player)
                        if (hasPlayer) {
                            val npVm: NowPlayingViewModel = containerViewModel { NowPlayingViewModel(it.music, it.player) }
                            PlayerSheet(vm = npVm, collapseTick = collapseTick)
                        }
                    }
                }
            }
        }
    }
}
