package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozgen.navicloud.audio.AudioEffectsController
import com.ozgen.navicloud.audio.EQ_BANDS_HZ
import com.ozgen.navicloud.audio.EQ_GAIN_MAX_DB
import com.ozgen.navicloud.audio.EqPreset
import kotlin.math.abs

/**
 * "Ses / Ekolayzer" bottom sheet'i. Master anahtar tüm efektleri kapatır
 * (bypass); EQ 5 sabit presetle çalışır. Efekt bölümleri (bas/genişlik/ortam/
 * kazanç) aynı sheet'te devam eder. Cihazın desteklemediği efekt disable görünür.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsSheet(fx: AudioEffectsController) {
    val state by fx.state.collectAsStateWithLifecycle()
    val caps by fx.capabilities.collectAsStateWithLifecycle()
    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = navBarPad + 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Ses efektleri",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Yalnız bu cihazda çalar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.masterEnabled, onCheckedChange = { fx.setMasterEnabled(it) })
        }

        Spacer(Modifier.height(12.dp))

        // Master kapalıyken içerik soluk; kontroller ayrıca enabled=false
        Column(Modifier.graphicsLayer { alpha = if (state.masterEnabled) 1f else 0.45f }) {
            val eqEnabled = state.masterEnabled && caps.eq

            EffectSectionHeader(
                title = "Ekolayzer",
                checked = state.eqEnabled,
                enabled = eqEnabled,
                onCheckedChange = { fx.setEqEnabled(it) },
            )
            EqBandGraph(preset = state.eqPreset, accent = accent)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPreset.entries.forEach { p ->
                    FilterChip(
                        selected = state.eqPreset == p,
                        enabled = eqEnabled,
                        onClick = { fx.setEqPreset(p) },
                        label = { Text(p.label) },
                    )
                }
            }

            // Efekt bölümleri (bas/genişlik/ortam/kazanç) T4'te bu sheet'e eklenir
            AudioEffectSections(fx = fx, state = state, caps = caps, accent = accent)
        }
    }
}

/** Bölüm başlığı: sol başlık, sağda açma/kapama anahtarı. */
@Composable
internal fun EffectSectionHeader(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked && enabled, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/** 5 band EQ görselleştirmesi: orta hattan yukarı (boost) / aşağı (kesme) barlar. */
@Composable
private fun EqBandGraph(preset: EqPreset, accent: Color) {
    val gridColor = Color(0x22FFFFFF)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(88.dp)) {
            val n = EQ_BANDS_HZ.size
            val slot = size.width / n
            val midY = size.height / 2f
            val barW = slot * 0.34f
            val maxH = size.height / 2f - 6f
            // orta hat
            drawLine(gridColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)
            for (i in 0 until n) {
                val g = preset.gainsDb[i].toFloat()
                val cx = slot * i + slot / 2f
                val h = (g / EQ_GAIN_MAX_DB.toFloat()) * maxH
                val top = if (g >= 0f) midY - h else midY
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(cx - barW / 2f, top),
                    size = Size(barW, abs(h).coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            EQ_BANDS_HZ.forEach { hz ->
                Text(
                    freqLabel(hz),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun freqLabel(hz: Int): String = when {
    hz < 1000 -> "$hz"
    hz % 1000 == 0 -> "${hz / 1000}k"
    else -> "%.1fk".format(hz / 1000f)
}
