package com.ozgen.navicloud.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ozgen.navicloud.audio.AudioEffectsCapabilities
import com.ozgen.navicloud.audio.AudioEffectsController
import com.ozgen.navicloud.audio.AudioEffectsState
import com.ozgen.navicloud.audio.EqPreset
import com.ozgen.navicloud.audio.ReverbPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_MASTER = booleanPreferencesKey("fx_master")
private val KEY_EQ_ON = booleanPreferencesKey("fx_eq_on")
private val KEY_EQ_PRESET = stringPreferencesKey("fx_eq_preset")
private val KEY_BASS_ON = booleanPreferencesKey("fx_bass_on")
private val KEY_BASS_LVL = floatPreferencesKey("fx_bass_lvl")
private val KEY_VIRT_ON = booleanPreferencesKey("fx_virt_on")
private val KEY_VIRT_LVL = floatPreferencesKey("fx_virt_lvl")
private val KEY_REVERB_ON = booleanPreferencesKey("fx_reverb_on")
private val KEY_REVERB_PRESET = stringPreferencesKey("fx_reverb_preset")
private val KEY_GAIN_ON = booleanPreferencesKey("fx_gain_on")
private val KEY_GAIN_LVL = floatPreferencesKey("fx_gain_lvl")

/**
 * Ses efektleri ayarlarının tek doğru kaynağı (DataStore). UI buradan okur/yazar;
 * [com.ozgen.navicloud.playback.AudioEffectsEngine] aynı akışı dinleyip audiofx'e
 * uygular. Yetenekler (capabilities) motordan geri yazılır — cihazın gerçekten
 * desteklediği efektleri UI'a bildirir.
 */
@Singleton
class AudioEffectsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AudioEffectsController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val defaults = AudioEffectsState()

    override val state: StateFlow<AudioEffectsState> = dataStore.data
        .map { p ->
            AudioEffectsState(
                masterEnabled = p[KEY_MASTER] ?: defaults.masterEnabled,
                eqEnabled = p[KEY_EQ_ON] ?: defaults.eqEnabled,
                eqPreset = p[KEY_EQ_PRESET]?.let { runCatching { EqPreset.valueOf(it) }.getOrNull() } ?: defaults.eqPreset,
                bassEnabled = p[KEY_BASS_ON] ?: defaults.bassEnabled,
                bassLevel = p[KEY_BASS_LVL] ?: defaults.bassLevel,
                virtualizerEnabled = p[KEY_VIRT_ON] ?: defaults.virtualizerEnabled,
                virtualizerLevel = p[KEY_VIRT_LVL] ?: defaults.virtualizerLevel,
                reverbEnabled = p[KEY_REVERB_ON] ?: defaults.reverbEnabled,
                reverbPreset = p[KEY_REVERB_PRESET]?.let { runCatching { ReverbPreset.valueOf(it) }.getOrNull() } ?: defaults.reverbPreset,
                gainEnabled = p[KEY_GAIN_ON] ?: defaults.gainEnabled,
                gainLevel = p[KEY_GAIN_LVL] ?: defaults.gainLevel,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, defaults)

    private val _capabilities = MutableStateFlow(AudioEffectsCapabilities())
    override val capabilities: StateFlow<AudioEffectsCapabilities> = _capabilities

    /** Motor tarafından cihaz yetenekleri çözülünce çağrılır. */
    fun updateCapabilities(caps: AudioEffectsCapabilities) { _capabilities.value = caps }

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit(block) }
    }

    override fun setMasterEnabled(enabled: Boolean) = edit { it[KEY_MASTER] = enabled }
    override fun setEqEnabled(enabled: Boolean) = edit { it[KEY_EQ_ON] = enabled }
    override fun setEqPreset(preset: EqPreset) = edit {
        it[KEY_EQ_PRESET] = preset.name
        it[KEY_EQ_ON] = true
    }

    override fun setBass(enabled: Boolean, level: Float?) = edit {
        it[KEY_BASS_ON] = enabled
        if (level != null) it[KEY_BASS_LVL] = level.coerceIn(0f, 1f)
    }

    override fun setVirtualizer(enabled: Boolean, level: Float?) = edit {
        it[KEY_VIRT_ON] = enabled
        if (level != null) it[KEY_VIRT_LVL] = level.coerceIn(0f, 1f)
    }

    override fun setReverb(enabled: Boolean, preset: ReverbPreset?) = edit {
        it[KEY_REVERB_ON] = enabled
        if (preset != null) it[KEY_REVERB_PRESET] = preset.name
    }

    override fun setGain(enabled: Boolean, level: Float?) = edit {
        it[KEY_GAIN_ON] = enabled
        if (level != null) it[KEY_GAIN_LVL] = level.coerceIn(0f, 1f)
    }
}
