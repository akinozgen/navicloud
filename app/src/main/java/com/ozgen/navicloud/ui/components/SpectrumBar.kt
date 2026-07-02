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
    Canvas(modifier.fillMaxWidth().height(76.dp)) {
        val n = heights.size
        val gap = 2.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val filled = progress.coerceIn(0f, 1f) * n
        // Bar tabanı: üstte asıl spektrum, altında sönük yansıması (su hattı gibi)
        val baseline = size.height * 0.62f
        val mirrorSpace = size.height - baseline
        heights.forEachIndexed { i, h ->
            val x = i * (barW + gap)
            val passed = i < filled
            val bh = baseline * h
            drawRoundRect(
                color = if (passed) accent.copy(alpha = 0.55f) else Color(0x14FFFFFF),
                topLeft = Offset(x, baseline - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f),
            )
            // Yansıma: aynı desen, aşağı doğru, çok daha sönük
            val rh = mirrorSpace * h * 0.8f
            drawRoundRect(
                color = if (passed) accent.copy(alpha = 0.18f) else Color(0x08FFFFFF),
                topLeft = Offset(x, baseline + 2.dp.toPx()),
                size = Size(barW, rh),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
