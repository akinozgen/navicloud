package com.ozgen.navicloud.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ses / ekolayzer sözleşmesi — platformdan bağımsız. Preset band değerleri,
 * efekt aralıkları ve zincir sırası burada SABİT tanımlıdır; mobil
 * (android.media.audiofx) ve masaüstü (mpv af zinciri) AYNI değerleri
 * uygular ki iki platform ayrışmasın.
 */

/** 5 band grafik EQ'nun sabit merkez frekansları (Hz). İki platformda aynı. */
val EQ_BANDS_HZ = listOf(60, 230, 910, 3600, 14000)

/** EQ band kazanç aralığı (dB). */
const val EQ_GAIN_MIN_DB = -12
const val EQ_GAIN_MAX_DB = 12

/**
 * 5 sabit EQ preseti. [gainsDb] her band için dB kazanç; sıra [EQ_BANDS_HZ]
 * ile birebir hizalı, değerler ±12 dB içinde.
 */
enum class EqPreset(val label: String, val gainsDb: List<Int>) {
    //                       60   230  910  3.6k 14k
    FLAT("Düz",       listOf(  0,   0,   0,   0,   0)),
    ROCK("Rock",      listOf(  5,   3,  -1,   3,   5)),
    POP("Pop",        listOf( -1,   2,   4,   3,  -1)),
    JAZZ("Jazz",      listOf(  3,   2,  -1,   2,   4)),
    CLASSICAL("Klasik", listOf(4,   3,   0,   3,   5)),
}

/**
 * Ortam (reverb) presetleri — android.media.audiofx.PresetReverb.PRESET_*
 * karşılıkları; masaüstünde preset başına sabit mpv aecho parametresine map'lenir.
 */
enum class ReverbPreset(val label: String) {
    SMALL_ROOM("Küçük oda"),
    MEDIUM_ROOM("Orta oda"),
    LARGE_ROOM("Büyük oda"),
    MEDIUM_HALL("Salon"),
    LARGE_HALL("Büyük salon"),
    PLATE("Plaka"),
}

/**
 * Efekt zinciri — enum SIRASI zincir sırasıdır ve iki platformda AYNI olmak
 * ZORUNDA: EQ (ayrı, ilk) → BASS → VIRTUALIZER → REVERB → GAIN. Aynı ayar
 * farklı sırada farklı ses verir.
 */
enum class AudioEffect(val label: String) {
    BASS("Bas güçlendirme"),
    VIRTUALIZER("Genişlik"),
    REVERB("Ortam"),
    GAIN("Ses kazancı"),
}

/**
 * Tüm ses ayarlarının anlık durumu — DataStore'da kalıcı (SoT). Seviyeler
 * 0f..1f normalize; her platform kendi motorunun aralığına ölçekler.
 */
data class AudioEffectsState(
    /** Ana anahtar. Kapalıyken (master bypass) tüm efektler devre dışı. */
    val masterEnabled: Boolean = false,
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val bassEnabled: Boolean = false,
    val bassLevel: Float = 0.4f,
    val virtualizerEnabled: Boolean = false,
    val virtualizerLevel: Float = 0.4f,
    val reverbEnabled: Boolean = false,
    val reverbPreset: ReverbPreset = ReverbPreset.MEDIUM_ROOM,
    val gainEnabled: Boolean = false,
    val gainLevel: Float = 0.3f,
)

/**
 * Cihaz yetenekleri — bazı efektler (özellikle BassBoost/Virtualizer)
 * cihaza göre desteklenmeyebilir. Engine gerçek durumu doldurur, UI
 * desteklenmeyen kontrolü disable gösterir. Varsayılan hepsi true (iyimser).
 */
data class AudioEffectsCapabilities(
    val eq: Boolean = true,
    val bass: Boolean = true,
    val virtualizer: Boolean = true,
    val reverb: Boolean = true,
    val gain: Boolean = true,
)

/**
 * Ses efektleri denetimi — UI bu arayüzle konuşur. Android'de DataStore
 * destekli implementasyon (state kalıcı, PlaybackService içindeki engine
 * aynı DataStore'u dinleyip audiofx'e uygular). Masaüstü kendi pass'inde
 * mpv af zincirine bağlar; o zamana dek [NoOpAudioEffectsController].
 */
interface AudioEffectsController {
    val state: StateFlow<AudioEffectsState>
    val capabilities: StateFlow<AudioEffectsCapabilities>

    fun setMasterEnabled(enabled: Boolean)
    fun setEqEnabled(enabled: Boolean)
    fun setEqPreset(preset: EqPreset)
    fun setBass(enabled: Boolean, level: Float? = null)
    fun setVirtualizer(enabled: Boolean, level: Float? = null)
    fun setReverb(enabled: Boolean, preset: ReverbPreset? = null)
    fun setGain(enabled: Boolean, level: Float? = null)
}

/** Efekt desteği olmayan platform için no-op (masaüstü, contract pass'i öncesi). */
class NoOpAudioEffectsController : AudioEffectsController {
    override val state = MutableStateFlow(AudioEffectsState()).asStateFlow()
    override val capabilities = MutableStateFlow(AudioEffectsCapabilities()).asStateFlow()
    override fun setMasterEnabled(enabled: Boolean) {}
    override fun setEqEnabled(enabled: Boolean) {}
    override fun setEqPreset(preset: EqPreset) {}
    override fun setBass(enabled: Boolean, level: Float?) {}
    override fun setVirtualizer(enabled: Boolean, level: Float?) {}
    override fun setReverb(enabled: Boolean, preset: ReverbPreset?) {}
    override fun setGain(enabled: Boolean, level: Float?) {}
}
