package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Dekoratif spektrum bar: track başına SEED'Lİ deterministik yükseklikler
 * (aynı şarkı hep aynı görünür), oynatma konumuna göre geçilen kısım accent
 * ile dolar. Gerçek FFT değil — RECORD_AUDIO izni ve CPU maliyeti olmadan
 * progress'i yansıtır. Salt Canvas draw; relayout yok.
 */
@Composable
fun SpectrumBar(
    seedKey: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val heights = remember(seedKey) {
        val rnd = Random(seedKey.hashCode())
        List(52) { 0.18f + rnd.nextFloat() * 0.82f }
    }
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val n = heights.size
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val filled = progress.coerceIn(0f, 1f) * n
        heights.forEachIndexed { i, h ->
            val x = i * (barW + gap)
            val bh = size.height * h
            drawRoundRect(
                color = if (i < filled) accent else Color(0x30FFFFFF),
                topLeft = Offset(x, (size.height - bh) / 2f),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
