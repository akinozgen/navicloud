package com.ozgen.navicloud.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.ozgen.navicloud.i18n.I18n
import com.ozgen.navicloud.i18n.stringsFor
import com.ozgen.navicloud.ui.i18n.LocalStrings
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
    val language by LocalAppContainer.current.language
        .collectAsStateWithLifecycle(remember { I18n.language })
    LaunchedEffect(language) { I18n.language = language }
    CompositionLocalProvider(LocalStrings provides stringsFor(language)) {
        val appState by vm.appState.collectAsStateWithLifecycle()
        when (val st = appState) {
            AppState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            AppState.NeedsLogin -> LoginScreen()
            is AppState.Ready -> key(st.server.id) { MainShell(vm, st.server, platformSettings) }
        }
    }
}

private data class BottomTab(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val tabs = listOf(
    BottomTab("home", Icons.Outlined.Home, Icons.Rounded.Home),
    BottomTab("search", Icons.Outlined.Search, Icons.Rounded.Search),
    BottomTab("library", Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
)

@Composable
private fun tabLabel(route: String): String {
    val s = LocalStrings.current
    return when (route) {
        "home" -> s.rootTabHome
        "search" -> s.rootTabSearch
        else -> s.rootTabLibrary
    }
}

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
            composable("section/{type}") { entry ->
                com.ozgen.navicloud.ui.screens.section.SectionScreen(
                    navController,
                    entry.arguments?.getString("type").orEmpty(),
                )
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
                    // Sidebar içerikle aynı zeminde otursun — arkadaki çıplak
                    // pencere siyahı 'ayrı koyu tema' gibi görünüyordu
                    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // Standart sidebar: ikon solda, metin saginda — dikey
                        // rail'in mobil havasi genis ekrana yakismiyordu
                        Column(
                            Modifier
                                .width(if (this@BoxWithConstraints.maxWidth < 840.dp) 176.dp else 220.dp)
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                "NaviCloud",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 16.dp),
                            )
                            tabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                                            else Color.Transparent
                                        )
                                        .clickable { navigateTab(tab.route, onTabRoot) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tabLabel(tab.route),
                                        tint = if (selected) MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        tabLabel(tab.route),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
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
                                                    contentDescription = tabLabel(tab.route),
                                                )
                                            },
                                            label = { Text(tabLabel(tab.route), style = MaterialTheme.typography.labelMedium) },
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

                // "Kaldığın yerden devam" bandı (üstte). Uzaktan kumanda DURUMU artık banner
                // değil — player cast ikonu + badge'inde; kick/disconnect cihaz menüsünde.
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .fillMaxWidth(),
                ) {
                    ResumeSyncBanner()
                }
                // Uzaktan kumanda dialog/toast'ları (banner DEĞİL): alıcı PIN gösterimi,
                // controller PIN/parola girişi, bağlantı-koptu bildirimi.
                RemoteControlDialogs()
            }
        }
    }
}

/**
 * Uzaktan kumanda dialog/toast'ları (banner DEĞİL — kumanda durumu artık player cast ikonu +
 * badge'inde; kick/disconnect cihaz menüsünde). Burada yalnız: alıcı PIN gösterimi, controller
 * PIN/parola giriş dialoğu, bağlantı-koptu (FAILED) toast'ı.
 */
@Composable
private fun RemoteControlDialogs() {
    val rc = LocalAppContainer.current.remoteControl ?: return
    val s = LocalStrings.current
    val connState by rc.connState.collectAsStateWithLifecycle()

    val toast = rememberToaster()
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(connState) {
        if (connState == com.ozgen.navicloud.remote.ConnState.CONNECTED) wasConnected = true
        if (connState == com.ozgen.navicloud.remote.ConnState.FAILED && wasConnected) {
            wasConnected = false
            toast(s.commonConnectionLost)
        }
    }

    // RC-5 alıcı: biri eşleşmek istiyorsa PIN'i göster
    val incomingPin by rc.incomingPairPin.collectAsStateWithLifecycle()
    incomingPin?.let { pin ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(s.rootPairingCodeTitle) },
            text = {
                Column {
                    Text(s.rootEnterCodeOnConnecting)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pin,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {},
        )
    }

    // RC-5/RC-7 controller: uzak cihaz PIN ya da sabit parola istiyorsa giriş dialog'u
    val pinPrompt by rc.pinPrompt.collectAsStateWithLifecycle()
    pinPrompt?.let { prompt ->
        var entered by remember(prompt) { mutableStateOf("") }
        val ok = if (prompt.secret) entered.isNotBlank() else entered.length == 6
        AlertDialog(
            onDismissRequest = { prompt.cancel() },
            title = { Text(if (prompt.secret) s.rootPassword else s.rootPairing) },
            text = {
                Column {
                    Text(
                        if (prompt.secret) {
                            s.rootEnterPasswordFor(prompt.peerName)
                        } else {
                            s.rootEnterCodeFrom(prompt.peerName)
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = entered,
                        onValueChange = {
                            entered = if (prompt.secret) it else it.filter(Char::isDigit).take(6)
                        },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { prompt.submit(entered) }, enabled = ok) {
                    Text(if (prompt.secret) s.rootConnect else s.rootPair)
                }
            },
            dismissButton = { TextButton(onClick = { prompt.cancel() }) { Text(s.commonCancel) } },
        )
    }
}

@Composable
private fun ResumeSyncBanner() {
    val sync = LocalAppContainer.current.queueSync ?: return
    // Uzak hedefteyken gizle: "Devam" LOKAL cihazda çalar — kumanda ederken kafa karıştırır (RC-3 kararı)
    val rcTarget = LocalAppContainer.current.remoteControl?.target?.collectAsStateWithLifecycle()?.value
    if (rcTarget is com.ozgen.navicloud.remote.ControlTarget.Remote) return
    val offer by sync.resumeOffer.collectAsStateWithLifecycle()
    val o = offer ?: return
    val s = LocalStrings.current
    val track = o.songs.getOrNull(o.currentIndex)
    Card(
        modifier = Modifier
            .padding(12.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.rootResumeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        // changedBy her iki platformda da "NaviCloud" — cihaz ayırt etmez;
                        // yalnız farklı/anlamlıysa göster (ör. ileride "NaviCloud Desktop").
                        o.changedBy?.takeIf { it.isNotBlank() && it != "NaviCloud" }
                            ?.let { append(it); append(" · ") }
                        track?.title?.let { append(it); append(" · ") }
                        append(formatMmSs(o.positionMs))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = { sync.dismissResume() }) { Text(s.commonClose) }
            Button(onClick = { sync.applyResume(o) }) { Text(s.rootResume) }
        }
    }
}

private fun formatMmSs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
