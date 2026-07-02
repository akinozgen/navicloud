package com.ozgen.navicloud.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.data.ServerRepository
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface AppState {
    data object Loading : AppState
    data object NeedsLogin : AppState
    data class Ready(val server: Server) : AppState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    val player: PlayerController,
) : ViewModel() {

    val appState: StateFlow<AppState> =
        combine(serverRepository.servers, serverRepository.activeServer) { servers, active ->
            when {
                active != null -> AppState.Ready(active)
                servers.isEmpty() -> AppState.NeedsLogin
                else -> AppState.Loading
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState.Loading)

    val playerState: StateFlow<PlayerUiState> = player.state

    /** Cover-art URL resolver bound to a server's authenticated client. */
    fun artResolverFor(server: Server): (String?, Int?) -> String? {
        val client = serverRepository.clientFor(server)
        return { coverArt, size -> coverArt?.let { client.coverArtUrl(it, size) } }
    }
}
