package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.audio.AudioEffectsCapabilities
import com.ozgen.navicloud.audio.AudioEffectsController
import com.ozgen.navicloud.audio.AudioEffectsState
import com.ozgen.navicloud.audio.ReverbPreset
import com.ozgen.navicloud.ui.i18n.LocalStrings

/**
 * "Ses / Ekolayzer" sheet'inin efekt bölümleri: bas → genişlik → ortam → kazanç.
 * Zincir sırası sözleşmeyle aynı. Her efekt: açma/kapama + tek slider (ortam:
 * preset). Cihaz desteklemiyorsa kontrol disable + not. Master kapalıysa hepsi
 * pasif (üstteki alpha + enabled).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioEffectSections(
    fx: AudioEffectsController,
    state: AudioEffectsState,
    caps: AudioEffectsCapabilities,
    accent: Color,
) {
    val master = state.masterEnabled
    val strings = LocalStrings.current

    HorizontalDivider(Modifier.padding(vertical = 10.dp))

    EffectSliderSection(
        title = strings.audioFxBassBoost,
        supported = caps.bass,
        master = master,
        on = state.bassEnabled,
        level = state.bassLevel,
        accent = accent,
        onToggle = { fx.setBass(it, null) },
        onLevel = { fx.setBass(true, it) },
    )

    EffectSliderSection(
        title = strings.audioFxWidth,
        supported = caps.virtualizer,
        master = master,
        on = state.virtualizerEnabled,
        level = state.virtualizerLevel,
        accent = accent,
        onToggle = { fx.setVirtualizer(it, null) },
        onLevel = { fx.setVirtualizer(true, it) },
    )

    // Ortam (reverb): slider yerine preset seçici
    val reverbEnabled = master && caps.reverb
    EffectSectionHeader(
        title = strings.audioFxAmbience,
        checked = state.reverbEnabled,
        enabled = reverbEnabled,
        onCheckedChange = { fx.setReverb(it, null) },
        subtitle = if (!caps.reverb) strings.audioFxNotSupported else null,
    )
    if (state.reverbEnabled && reverbEnabled) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReverbPreset.entries.forEach { p ->
                FilterChip(
                    selected = state.reverbPreset == p,
                    onClick = { fx.setReverb(true, p) },
                    label = { Text(strings.reverbPresetLabel(p)) },
                )
            }
        }
    }

    EffectSliderSection(
        title = strings.audioFxLoudness,
        supported = caps.gain,
        master = master,
        on = state.gainEnabled,
        level = state.gainLevel,
        accent = accent,
        onToggle = { fx.setGain(it, null) },
        onLevel = { fx.setGain(true, it) },
    )
}

@Composable
private fun EffectSliderSection(
    title: String,
    supported: Boolean,
    master: Boolean,
    on: Boolean,
    level: Float,
    accent: Color,
    onToggle: (Boolean) -> Unit,
    onLevel: (Float) -> Unit,
) {
    val enabled = master && supported
    val strings = LocalStrings.current
    EffectSectionHeader(
        title = title,
        checked = on,
        enabled = enabled,
        onCheckedChange = onToggle,
        subtitle = if (!supported) strings.audioFxNotSupported else null,
    )
    if (on && enabled) {
        ThinSlider(
            value = level,
            onValueChange = onLevel,
            activeColor = accent,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
        )
    }
}
