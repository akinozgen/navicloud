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
 * Ambient spektrum: kenardan kenara, alt hizalı bir arka plan dokusu —
 * ayrı bir widget gibi değil, zeminin parçası gibi. Track başına SEED'Lİ
 * deterministik desen; oynatma konumuna göre geçilen kısım accent'le dolar.
 * Gerçek FFT değil (izin/CPU yok), salt Canvas draw.
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
        // Komşu barlar arasında yumuşak geçiş: ham gürültüyü hafifçe blend'le
        val raw = List(96) { 0.10f + rnd.nextFloat() * 0.90f }
        raw.mapIndexed { i, h ->
            val prev = raw.getOrElse(i - 1) { h }
            val next = raw.getOrElse(i + 1) { h }
            (prev + h * 2f + next) / 4f
        }
    }
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val n = heights.size
        val gap = 2.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val filled = progress.coerceIn(0f, 1f) * n
        heights.forEachIndexed { i, h ->
            val x = i * (barW + gap)
            val bh = size.height * h
            drawRoundRect(
                // Yarı saydam: zeminle kaynaşır, öğe gibi bağırmaz
                color = if (i < filled) accent.copy(alpha = 0.55f) else Color(0x14FFFFFF),
                topLeft = Offset(x, size.height - bh), // alt hizalı skyline
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
