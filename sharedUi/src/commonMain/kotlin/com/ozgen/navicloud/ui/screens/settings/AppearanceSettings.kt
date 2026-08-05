package com.ozgen.navicloud.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.data.AccentColor
import com.ozgen.navicloud.data.AppearancePreferences
import com.ozgen.navicloud.i18n.Strings
import com.ozgen.navicloud.ui.i18n.LocalStrings

/** Android ve masaüstü Ayarlar ekranlarının birebir kullandığı görünüm kontrolleri. */
@Composable
fun AppearanceSettingsControls(
    appearance: AppearancePreferences,
    onAccentChange: (AccentColor) -> Unit,
    onPreferPitchBlackChange: (Boolean) -> Unit,
    onAlbumArtGlowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(strings.settingsAccentColor, style = MaterialTheme.typography.bodyLarge)
        Text(
            strings.settingsAccentColorDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AccentColor.entries.forEach { accent ->
                AccentSwatch(
                    accent = accent,
                    label = accent.label(strings),
                    selected = appearance.accentColor == accent,
                    onClick = { onAccentChange(accent) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AppearanceCheckboxRow(
            title = strings.settingsPreferBlack,
            subtitle = strings.settingsPreferBlackDesc,
            checked = appearance.preferPitchBlack,
            onCheckedChange = onPreferPitchBlackChange,
        )
        AppearanceCheckboxRow(
            title = strings.settingsAlbumArtGlow,
            subtitle = strings.settingsAlbumArtGlowDesc,
            checked = appearance.albumArtGlow,
            onCheckedChange = onAlbumArtGlowChange,
        )
    }
}

@Composable
private fun AccentSwatch(
    accent: AccentColor,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "accentPress",
    )
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "accentSelected",
    )
    val color = accent.argb?.let { Color(it) }
    val checkColor = when {
        color == null -> Color.White
        color.luminance() >= 0.42f -> Color(0xFF101014)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(alpha = selectedProgress * 0.82f), CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .graphicsLayer { alpha = if (pressed) 0.88f else 1f },
        ) {
            if (color != null) {
                drawCircle(color)
            } else {
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF7C6CFF),
                            Color(0xFFFF8FB1),
                            Color(0xFFFFB38A),
                            Color(0xFFFFD166),
                            Color(0xFF72D6B3),
                            Color(0xFF78B7FF),
                            Color(0xFF7C6CFF),
                        )
                    )
                )
            }
        }
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = checkColor,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    alpha = selectedProgress
                    scaleX = 0.25f + (0.75f * selectedProgress)
                    scaleY = 0.25f + (0.75f * selectedProgress)
                },
        )
    }
}

@Composable
private fun AppearanceCheckboxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

private fun AccentColor.label(strings: Strings): String = when (this) {
    AccentColor.AUTO -> strings.settingsAccentAuto
    AccentColor.VIOLET -> strings.settingsAccentViolet
    AccentColor.ROSE -> strings.settingsAccentRose
    AccentColor.PEACH -> strings.settingsAccentPeach
    AccentColor.AMBER -> strings.settingsAccentAmber
    AccentColor.MINT -> strings.settingsAccentMint
    AccentColor.SKY -> strings.settingsAccentSky
}
