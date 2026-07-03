package com.ozgen.navicloud.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Etkileşimli dalga formu seek bar: ortadan simetrik büyüyen, track başına
 * SEED'li deterministik barlar. Geçilen kısım accent'le dolar; dokunma/
 * sürükleme ile aranır. Çalarken barlar boyunca hafif akan bir sine
 * dalgasıyla nefes alır (duraklayınca yumuşakça durur). Mini oynatıcıya özgü.
 */
@Composable
fun WaveformSeekBar(
    seedKey: String,
    progress: Float,
    accent: Color,
    playing: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val heights = remember(seedKey) {
        val rnd = Random(seedKey.hashCode())
        val raw = List(64) { 0.20f + rnd.nextFloat() * 0.80f }
        // Komşu barları hafif blend'le — ham gürültü yerine akışkan dalga
        raw.mapIndexed { i, h ->
            val prev = raw.getOrElse(i - 1) { h }
            val next = raw.getOrElse(i + 1) { h }
            (prev + h * 2f + next) / 4f
        }
    }

    // Çalarken hafif canlanma; durunca 0'a yumuşak iner (hareket = durum)
    val liveness by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = tween(500),
        label = "waveLiveness",
    )
    val phase: Float = if (playing) {
        val t = rememberInfiniteTransition(label = "wavePhase")
        val p by t.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
            label = "phase",
        )
        p
    } else 0f

    Canvas(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val n = heights.size
        val gap = 2.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val filled = progress.coerceIn(0f, 1f) * n
        val midY = size.height / 2f
        heights.forEachIndexed { i, h ->
            val x = i * (barW + gap)
            // Barlar boyunca akan sine: her bara faz kayması → dalga hissi.
            // Genlik küçük (%14) — hafif nefes, sakin.
            val wobble = 1f + 0.14f * liveness * sin(phase + i * 0.45f)
            val bh = (size.height * h * wobble).coerceAtMost(size.height)
            drawRoundRect(
                color = if (i < filled) accent else Color(0x33FFFFFF),
                topLeft = Offset(x, midY - bh / 2f),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
