package com.ozgen.navicloud.ui.screens.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Album
import com.ozgen.navicloud.core.model.HomeSectionType
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.ui.components.OverlayAlbumCard
import com.ozgen.navicloud.ui.containerViewModel
import com.ozgen.navicloud.ui.i18n.LocalStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val PAGE = 60

data class SectionUiState(
    val albums: List<Album> = emptyList(),
    val loading: Boolean = true,
    val endReached: Boolean = false,
    val loadingMore: Boolean = false,
)

class SectionViewModel(
    private val repo: MusicRepository,
    private val type: HomeSectionType,
) : ViewModel() {
    private val _state = MutableStateFlow(SectionUiState())
    val state: StateFlow<SectionUiState> = _state

    init {
        viewModelScope.launch {
            runCatching { repo.albumList(type, size = PAGE, offset = 0) }
                .onSuccess {
                    _state.value = SectionUiState(
                        albums = it.distinctBy { a -> a.id },
                        loading = false,
                        endReached = it.size < PAGE,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loading = false, endReached = true) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { repo.albumList(type, size = PAGE, offset = _state.value.albums.size) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        albums = (_state.value.albums + page).distinctBy { it.id },
                        endReached = page.size < PAGE,
                        loadingMore = false,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loadingMore = false, endReached = true) }
        }
    }
}

/** Ana sayfa rafının tam sayfa hali: kategori albümleri adaptive grid'de. */
@Composable
fun SectionScreen(
    navController: NavController,
    typeName: String,
    vm: SectionViewModel = containerViewModel(key = "section-$typeName") {
        SectionViewModel(it.music, HomeSectionType.valueOf(typeName))
    },
) {
    val type = remember(typeName) { HomeSectionType.valueOf(typeName) }
    val strings = LocalStrings.current
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Sayfa sonuna yaklaşınca sıradaki sayfa (7k albüm topluca yüklenmez)
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.commonBack)
            }
            Text(strings.homeSectionTitle(type), style = MaterialTheme.typography.headlineSmall)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.albums.size, key = { state.albums[it].id }, contentType = { "album" }) { i ->
                val album = state.albums[i]
                OverlayAlbumCard(
                    album,
                    onClick = { navController.navigate("album/${album.id}") },
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }
    }
}
