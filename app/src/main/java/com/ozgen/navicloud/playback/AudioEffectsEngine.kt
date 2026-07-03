package com.ozgen.navicloud.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.ozgen.navicloud.audio.AudioEffectsCapabilities
import com.ozgen.navicloud.audio.AudioEffectsState
import com.ozgen.navicloud.audio.EQ_BANDS_HZ
import com.ozgen.navicloud.audio.EqPreset
import com.ozgen.navicloud.audio.ReverbPreset
import com.ozgen.navicloud.data.AudioEffectsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * android.media.audiofx efektlerini ExoPlayer'ın audio session'ına bağlar ve
 * [AudioEffectsRepository] durumunu uygular. Efektler tek yerden yönetilir,
 * oturum ömrü boyunca yaşar, [release] ile kapanır.
 *
 * Zincir sırası (EQ→bass→virtualizer→reverb→gain) oluşturma sırası + artan
 * öncelikle ifade edilir; Android'de nihai sıra framework yönetiminde
 * (best-effort), desktop mpv af zincirinde birebirdir. Hiçbir efekt sıfırdan
 * DSP değildir; hepsi hazır platform API'si.
 */
@Suppress("DEPRECATION") // Virtualizer yeni API'lerde deprecated ama işlevsel; spec bunu istiyor
class AudioEffectsEngine(
    private val audioSessionId: Int,
    private val repository: AudioEffectsRepository,
    private val scope: CoroutineScope,
) {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var loudness: LoudnessEnhancer? = null

    fun start() {
        repository.updateCapabilities(createEffects())
        // DataStore = tek doğru kaynak; durum değiştikçe audiofx'e uygula
        scope.launch { repository.state.collect { apply(it) } }
    }

    /** Efektleri oluştur (öncelik = zincir sırası) ve cihaz yeteneklerini çöz. */
    private fun createEffects(): AudioEffectsCapabilities {
        var eqOk = false; var bassOk = false; var virtOk = false; var reverbOk = false; var gainOk = false
        runCatching { equalizer = Equalizer(100, audioSessionId); eqOk = true }
            .onFailure { Log.w(TAG, "Equalizer desteklenmiyor: ${it.message}") }
        runCatching {
            val bb = BassBoost(200, audioSessionId); bassBoost = bb
            bassOk = bb.strengthSupported
        }.onFailure { Log.w(TAG, "BassBoost desteklenmiyor: ${it.message}") }
        runCatching {
            val v = Virtualizer(300, audioSessionId); virtualizer = v
            virtOk = v.strengthSupported
        }.onFailure { Log.w(TAG, "Virtualizer desteklenmiyor: ${it.message}") }
        runCatching { reverb = PresetReverb(400, audioSessionId); reverbOk = true }
            .onFailure { Log.w(TAG, "PresetReverb desteklenmiyor: ${it.message}") }
        runCatching { loudness = LoudnessEnhancer(audioSessionId); gainOk = true }
            .onFailure { Log.w(TAG, "LoudnessEnhancer desteklenmiyor: ${it.message}") }
        return AudioEffectsCapabilities(
            eq = eqOk, bass = bassOk, virtualizer = virtOk, reverb = reverbOk, gain = gainOk,
        )
    }

    /** Master kapalıysa hepsi devre dışı; açıksa her efekt kendi anahtarına göre. */
    private fun apply(s: AudioEffectsState) {
        val master = s.masterEnabled

        equalizer?.let { eq ->
            runCatching {
                val active = master && s.eqEnabled
                if (active) applyEqPreset(eq, s.eqPreset)
                eq.setEnabled(active)
            }
        }
        bassBoost?.let { bb ->
            runCatching {
                val active = master && s.bassEnabled && bb.strengthSupported
                if (active) bb.setStrength((s.bassLevel * 1000).toInt().coerceIn(0, 1000).toShort())
                bb.setEnabled(active)
            }
        }
        virtualizer?.let { v ->
            runCatching {
                val active = master && s.virtualizerEnabled && v.strengthSupported
                if (active) v.setStrength((s.virtualizerLevel * 1000).toInt().coerceIn(0, 1000).toShort())
                v.setEnabled(active)
            }
        }
        reverb?.let { r ->
            runCatching {
                val active = master && s.reverbEnabled
                if (active) r.setPreset(s.reverbPreset.toPresetValue())
                r.setEnabled(active)
            }
        }
        loudness?.let { le ->
            runCatching {
                val active = master && s.gainEnabled
                if (active) le.setTargetGain((s.gainLevel * 1200).toInt().coerceIn(0, 1200))
                le.setEnabled(active)
            }
        }
    }

    /**
     * Sözleşmedeki 5 sabit band kazancını cihazın bandlarına uygular: her cihaz
     * bandı için en yakın sözleşme frekansının kazancı alınır (cihazlar farklı
     * band sayısı/frekansı bildirebilir).
     */
    private fun applyEqPreset(eq: Equalizer, preset: EqPreset) {
        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange // [min, max] millibel
        val min = range[0].toInt(); val max = range[1].toInt()
        for (b in 0 until bandCount) {
            val centerHz = eq.getCenterFreq(b.toShort()) / 1000 // milliHz → Hz
            var nearest = 0; var best = Int.MAX_VALUE
            for (t in EQ_BANDS_HZ.indices) {
                val d = abs(EQ_BANDS_HZ[t] - centerHz)
                if (d < best) { best = d; nearest = t }
            }
            val mB = (preset.gainsDb[nearest] * 100).coerceIn(min, max)
            eq.setBandLevel(b.toShort(), mB.toShort())
        }
    }

    private fun ReverbPreset.toPresetValue(): Short = when (this) {
        ReverbPreset.SMALL_ROOM -> PresetReverb.PRESET_SMALLROOM
        ReverbPreset.MEDIUM_ROOM -> PresetReverb.PRESET_MEDIUMROOM
        ReverbPreset.LARGE_ROOM -> PresetReverb.PRESET_LARGEROOM
        ReverbPreset.MEDIUM_HALL -> PresetReverb.PRESET_MEDIUMHALL
        ReverbPreset.LARGE_HALL -> PresetReverb.PRESET_LARGEHALL
        ReverbPreset.PLATE -> PresetReverb.PRESET_PLATE
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { reverb?.release() }
        runCatching { loudness?.release() }
        equalizer = null; bassBoost = null; virtualizer = null; reverb = null; loudness = null
    }

    private companion object { const val TAG = "AudioEffectsEngine" }
}
