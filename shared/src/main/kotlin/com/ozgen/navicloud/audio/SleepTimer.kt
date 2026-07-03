package com.ozgen.navicloud.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uyku zamanlayıcı sözleşmesi — platformdan bağımsız. Mobil ve masaüstü AYNI
 * preset listesini kullanır; zamanlayıcı saf app mantığıdır (ses motoruna
 * dokunmaz), tetiklenince yalnızca çalmayı duraklatır.
 */
sealed interface SleepTimerPreset {
    /** Belirli süre sonunda duraklat. */
    data class Duration(val minutes: Int) : SleepTimerPreset
    /** Çalan parça bitince duraklat. */
    data object EndOfTrack : SleepTimerPreset
    /** Kuyruk bitince duraklat. */
    data object EndOfQueue : SleepTimerPreset

    companion object {
        /** İki platformda ortak süre presetleri (dakika). */
        val DURATIONS = listOf(10, 20, 30, 60, 90)
    }
}

data class SleepTimerState(
    val active: Boolean = false,
    val preset: SleepTimerPreset? = null,
    /** Süre presetinde kalan süre (ms); bound presetlerde null. */
    val remainingMs: Long? = null,
)

/** [PlayerController] default'u için boş, paylaşılan durum akışı. */
internal val EMPTY_SLEEP_TIMER: StateFlow<SleepTimerState> =
    MutableStateFlow(SleepTimerState()).asStateFlow()
