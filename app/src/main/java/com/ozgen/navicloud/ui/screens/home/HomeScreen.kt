package com.ozgen.navicloud.ui.screens.home

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
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.HomeSection
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.ui.components.OverlayAlbumCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: MusicRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val initial = _state.value.sections.isEmpty()
            _state.value = _state.value.copy(loading = initial, refreshing = !initial, error = null)
            runCatching { repo.homeSections() }
                .onSuccess { _state.value = HomeUiState(loading = false, refreshing = false, sections = it) }
                .onFailure { _state.value = HomeUiState(loading = false, refreshing = false, error = it.message) }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Günaydın"
    in 12..17 -> "İyi günler"
    else -> "İyi akşamlar"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) { Text("Tekrar dene") }
            }
        }
        else -> PullToRefreshBox(
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
                        greeting(),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { navController.navigate("servers") }) {
                        Icon(
                            Icons.Rounded.Dns,
                            contentDescription = "Sunucular",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // First section: 2-column quick-access grid (info density up top)
            val firstSection = state.sections.firstOrNull()
            if (firstSection != null) {
                item(key = "quick-grid", contentType = "grid") {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        firstSection.albums.take(6).chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { album ->
                                    OverlayAlbumCard(
                                        album,
                                        onClick = { navController.navigate("album/${album.id}") },
                                        modifier = Modifier.weight(1f).aspectRatio(1f),
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
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
                    Text(
                        section.type.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
