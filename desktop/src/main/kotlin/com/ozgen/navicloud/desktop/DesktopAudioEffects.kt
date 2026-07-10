package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.audio.AudioEffectsCapabilities
import com.ozgen.navicloud.audio.AudioEffectsController
import com.ozgen.navicloud.audio.AudioEffectsState
import com.ozgen.navicloud.audio.EQ_BANDS_HZ
import com.ozgen.navicloud.audio.EqPreset
import com.ozgen.navicloud.audio.ReverbPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * [AudioEffectsController]'ın masaüstü (libmpv) implementasyonu. Durum
 * `~/.navicloud/audio_effects.json`'da kalıcı (SoT), her değişimde mpv `af`
 * (libavfilter) zincirine uygulanır. Paylaşılan [com.ozgen.navicloud.audio]
 * sözleşmesindeki AYNI band/preset/zincir-sırası değerlerini kullanır ki
 * mobil (android.media.audiofx) ile ayrışmasın.
 */
class DesktopAudioEffects(
    private val engine: MpvEngine,
    private val json: Json,
) : AudioEffectsController {

    private val file = File(System.getProperty("user.home"), ".navicloud/audio_effects.json")

    private val _state = MutableStateFlow(load())
    override val state: StateFlow<AudioEffectsState> = _state.asStateFlow()

    // libavfilter tüm efektleri destekler → hepsi yetenekli.
    override val capabilities: StateFlow<AudioEffectsCapabilities> =
        MutableStateFlow(AudioEffectsCapabilities()).asStateFlow()

    init {
        apply() // açılışta kalıcı durumu uygula
    }

    private fun update(transform: (AudioEffectsState) -> AudioEffectsState) {
        _state.value = transform(_state.value)
        persist()
        apply()
    }

    override fun setMasterEnabled(enabled: Boolean) = update { it.copy(masterEnabled = enabled) }
    override fun setEqEnabled(enabled: Boolean) = update { it.copy(eqEnabled = enabled) }
    override fun setEqPreset(preset: EqPreset) = update { it.copy(eqPreset = preset) }
    override fun setBass(enabled: Boolean, level: Float?) =
        update { it.copy(bassEnabled = enabled, bassLevel = level ?: it.bassLevel) }
    override fun setVirtualizer(enabled: Boolean, level: Float?) =
        update { it.copy(virtualizerEnabled = enabled, virtualizerLevel = level ?: it.virtualizerLevel) }
    override fun setReverb(enabled: Boolean, preset: ReverbPreset?) =
        update { it.copy(reverbEnabled = enabled, reverbPreset = preset ?: it.reverbPreset) }
    override fun setGain(enabled: Boolean, level: Float?) =
        update { it.copy(gainEnabled = enabled, gainLevel = level ?: it.gainLevel) }

    private fun apply() {
        runCatching { engine.setAudioFilters(buildAfChain(_state.value)) }
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(Dto.serializer(), _state.value.toDto()))
        }
    }

    private fun load(): AudioEffectsState =
        runCatching {
            if (!file.exists()) return@runCatching AudioEffectsState()
            json.decodeFromString(Dto.serializer(), file.readText()).toState()
        }.getOrElse { AudioEffectsState() }

    // --- Kalıcı DTO (enum'lar isim olarak saklanır) ---
    @Serializable
    private data class Dto(
        val masterEnabled: Boolean = false,
        val eqEnabled: Boolean = false,
        val eqPreset: String = EqPreset.FLAT.name,
        val bassEnabled: Boolean = false,
        val bassLevel: Float = 0.4f,
        val virtualizerEnabled: Boolean = false,
        val virtualizerLevel: Float = 0.4f,
        val reverbEnabled: Boolean = false,
        val reverbPreset: String = ReverbPreset.MEDIUM_ROOM.name,
        val gainEnabled: Boolean = false,
        val gainLevel: Float = 0.3f,
    )

    private fun AudioEffectsState.toDto() = Dto(
        masterEnabled, eqEnabled, eqPreset.name, bassEnabled, bassLevel,
        virtualizerEnabled, virtualizerLevel, reverbEnabled, reverbPreset.name,
        gainEnabled, gainLevel,
    )

    private fun Dto.toState() = AudioEffectsState(
        masterEnabled = masterEnabled,
        eqEnabled = eqEnabled,
        eqPreset = runCatching { EqPreset.valueOf(eqPreset) }.getOrDefault(EqPreset.FLAT),
        bassEnabled = bassEnabled,
        bassLevel = bassLevel,
        virtualizerEnabled = virtualizerEnabled,
        virtualizerLevel = virtualizerLevel,
        reverbEnabled = reverbEnabled,
        reverbPreset = runCatching { ReverbPreset.valueOf(reverbPreset) }.getOrDefault(ReverbPreset.MEDIUM_ROOM),
        gainEnabled = gainEnabled,
        gainLevel = gainLevel,
    )

    companion object {
        /** Locale-bağımsız ondalık (tr_TR virgülü mpv/libavfilter'ı bozar). */
        private fun f(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v)

        /** ReverbPreset → aecho "in_gain:out_gain:delays(ms):decays". */
        private fun aecho(p: ReverbPreset): String = when (p) {
            ReverbPreset.SMALL_ROOM -> "0.8:0.7:40:0.25"
            ReverbPreset.MEDIUM_ROOM -> "0.8:0.7:60:0.3"
            ReverbPreset.LARGE_ROOM -> "0.8:0.7:90:0.35"
            ReverbPreset.MEDIUM_HALL -> "0.8:0.8:120:0.4"
            ReverbPreset.LARGE_HALL -> "0.8:0.8:180:0.5"
            ReverbPreset.PLATE -> "0.8:0.75:50:0.4"
        }

        /**
         * Paylaşılan sözleşmedeki değerleri mpv af (libavfilter) zincirine çevirir.
         * Zincir sırası AudioEffect enum'ıyla aynı: EQ → BASS → VIRTUALIZER → REVERB → GAIN.
         * Saf/test edilebilir.
         */
        fun buildAfChain(s: AudioEffectsState): String {
            if (!s.masterEnabled) return ""
            val parts = mutableListOf<String>()
            // EQ (ilk): band başına libavfilter equalizer, yalnız sıfırdan farklı kazançlar
            if (s.eqEnabled) {
                EQ_BANDS_HZ.forEachIndexed { i, hz ->
                    val g = s.eqPreset.gainsDb.getOrElse(i) { 0 }
                    if (g != 0) parts += "lavfi=[equalizer=f=$hz:width_type=o:width=2:gain=$g]"
                }
            }
            // BASS: shelf, 0..15 dB
            if (s.bassEnabled) {
                val g = (s.bassLevel.coerceIn(0f, 1f) * 15).toInt()
                if (g > 0) parts += "lavfi=[bass=gain=$g]"
            }
            // VIRTUALIZER (genişlik): extrastereo m 1.0..2.5
            if (s.virtualizerEnabled) {
                val m = 1.0 + s.virtualizerLevel.coerceIn(0f, 1f) * 1.5
                parts += "lavfi=[extrastereo=m=${f(m, 2)}]"
            }
            // REVERB (ortam): preset başına sabit aecho
            if (s.reverbEnabled) {
                parts += "lavfi=[aecho=${aecho(s.reverbPreset)}]"
            }
            // GAIN (ses kazancı): 0..12 dB
            if (s.gainEnabled) {
                val db = s.gainLevel.coerceIn(0f, 1f) * 12
                if (db > 0) parts += "lavfi=[volume=volume=${f(db.toDouble(), 1)}dB]"
            }
            return parts.joinToString(",")
        }
    }
}
