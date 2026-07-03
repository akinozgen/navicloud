package com.ozgen.navicloud.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NowPlayingUiState(
    val starred: Boolean = false,
    val lyrics: Lyrics? = null,
    val lyricsLoading: Boolean = false,
)

/** Backs the persistent PlayerSheet (star/lyrics state + player access). */
class NowPlayingViewModel(
    private val repo: MusicRepository,
    val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(NowPlayingUiState())
    val state: StateFlow<NowPlayingUiState> = _state

    private var lyricsForSongId: String? = null

    fun onSongChanged(mediaId: String?, starredExtra: Boolean) {
        _state.value = _state.value.copy(starred = starredExtra)
        if (mediaId != lyricsForSongId) {
            _state.value = _state.value.copy(lyrics = null)
            lyricsForSongId = null
        }
    }

    fun toggleStar(songId: String) {
        val newValue = !_state.value.starred
        _state.value = _state.value.copy(starred = newValue)
        viewModelScope.launch {
            runCatching { repo.setStarred(newValue, songId = songId) }
                .onFailure { _state.value = _state.value.copy(starred = !newValue) }
        }
    }

    fun loadLyrics(songId: String) {
        if (lyricsForSongId == songId) return
        lyricsForSongId = songId
        viewModelScope.launch {
            _state.value = _state.value.copy(lyricsLoading = true)
            val lyrics = runCatching { repo.lyrics(songId) }.getOrNull()
            _state.value = _state.value.copy(lyrics = lyrics, lyricsLoading = false)
        }
    }
}
