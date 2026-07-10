package com.ozgen.navicloud.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozgen.navicloud.ui.components.NaviRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozgen.navicloud.ui.containerViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.HomeSection
import com.ozgen.navicloud.i18n.Strings
import com.ozgen.navicloud.ui.i18n.LocalStrings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.ui.components.OverlayAlbumCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val repo: MusicRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init { load(force = false) }

    /** Pull-to-refresh: TTL'i atla, sunucudan tazele. İlk açılış cache'ten. */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        viewModelScope.launch {
            val initial = _state.value.sections.isEmpty()
            _state.value = _state.value.copy(loading = initial, refreshing = !initial, error = null)
            runCatching { repo.homeSections(force) }
                .onSuccess { _state.value = HomeUiState(loading = false, refreshing = false, sections = it) }
                .onFailure { _state.value = HomeUiState(loading = false, refreshing = false, error = it.message) }
        }
    }
}

private fun greeting(s: Strings): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> s.homeGreetingMorning
    in 12..17 -> s.homeGreetingAfternoon
    else -> s.homeGreetingEvening
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: HomeViewModel = containerViewModel { HomeViewModel(it.music) }) {
    val state by vm.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) { Text(strings.commonRetry) }
            }
        }
        else -> NaviRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        greeting(strings),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { navController.navigate("servers") }) {
                        Icon(
                            Icons.Rounded.Dns,
                            contentDescription = strings.homeServers,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // First section: quick-access grid (info density up top). Kolon
            // sayısı gerçek içerik genişliğinden türer — telefonda 2, geniş
            // ekranda (tablet/rail'li düzen) karolar devleşmesin diye 3-4
            val firstSection = state.sections.firstOrNull()
            if (firstSection != null) {
                item(key = "quick-grid", contentType = "grid") {
                    androidx.compose.foundation.layout.BoxWithConstraints(
                        Modifier.padding(horizontal = 16.dp),
                    ) {
                        val cols = (maxWidth / 220.dp).toInt().coerceIn(2, 4)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            firstSection.albums.take(cols * 3).chunked(cols).forEach { rowAlbums ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rowAlbums.forEach { album ->
                                        OverlayAlbumCard(
                                            album,
                                            onClick = { navController.navigate("album/${album.id}") },
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                        )
                                    }
                                    repeat(cols - rowAlbums.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
            // Remaining sections: horizontal shelves with overlay cards
            val shelves = state.sections.drop(1)
            items(
                shelves.size,
                key = { shelves[it].type.name },
                contentType = { "section" },
            ) { i ->
                val section = shelves[i]
                Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                    // Başlık tıklanınca kategorinin tam sayfa grid'i açılır —
                    // masaüstünde yatay raf gezilemiyordu, artık gerek de yok
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("section/${section.type.name}") }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            strings.homeSectionTitle(section.type),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = strings.homeSeeAll,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(section.albums.size, key = { section.albums[it].id }) { j ->
                            val album = section.albums[j]
                            OverlayAlbumCard(
                                album,
                                onClick = { navController.navigate("album/${album.id}") },
                                modifier = Modifier.width(160.dp).aspectRatio(1f),
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
