package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kapak grid'lerinin HEDEF genişliği (tek kaynak). Home hızlı-grid, Library ve Section
 * grid'leri bunu kullanır → tüm ekranlarda kapaklar tutarlı boyutta. Varsayılan 176dp
 * (mobil/masaüstü ortak). Masaüstünde Ayarlar → Görünüm "ızgara yoğunluğu" bunu değiştirir
 * (B1); değer masaüstü Window'da sağlanır, mobilde default kalır → mobil davranış değişmez.
 */
val LocalCoverTarget = staticCompositionLocalOf { 176.dp }

/**
 * Manuel (Row-chunk) grid için kolon sayısı = genişlik / hedef, [min,max] arası.
 * Telefonda (~360–420dp, hedef 176) 2 döner — eski `coerceIn(2,4)` ile birebir.
 */
fun coverColumns(maxWidth: Dp, target: Dp, min: Int = 2, max: Int = 6): Int =
    (maxWidth / target).toInt().coerceIn(min, max)

/**
 * `GridCells.Adaptive` gibi hedef-boyuta göre kolon seçer ama MAKS kolon cap'li:
 * 4K'da "20 minik kolon" yerine hedef boyutta, sınırlı kolon. Adaptive semantiği (floor)
 * korunur → mobilde `Adaptive(160)` ile ~aynı kolon sayısı (parite).
 */
class CappedGridCells(
    private val target: Dp,
    private val min: Int = 2,
    private val max: Int = 8,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val targetPx = target.roundToPx().coerceAtLeast(1)
        val cols = ((availableSize + spacing) / (targetPx + spacing)).coerceIn(min, max)
        val totalSpacing = spacing * (cols - 1)
        val cell = (availableSize - totalSpacing) / cols
        val extra = (availableSize - totalSpacing) % cols
        return List(cols) { cell + if (it < extra) 1 else 0 }
    }

    override fun hashCode(): Int = (target.hashCode() * 31 + min) * 31 + max
    override fun equals(other: Any?): Boolean =
        other is CappedGridCells && other.target == target && other.min == min && other.max == max
}
