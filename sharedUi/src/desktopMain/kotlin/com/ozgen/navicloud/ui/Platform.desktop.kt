package com.ozgen.navicloud.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import kotlin.math.max

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Masaüstünde sistem geri tuşu yok; Esc bağlaması pencere düzeyinde gelecek
}

@Composable
actual fun rememberToaster(): (String) -> Unit = { msg -> println("TOAST: $msg") }

/**
 * Android Palette'in masaüstü karşılığı: kapak küçük boyda decode edilir,
 * örneklenen piksellerden baskın (koyulaştırılmış ortalama) ve accent
 * (en doygun/parlak küme) türetilir.
 */
actual suspend fun extractArtColors(artUrl: String, cacheKey: String?): Pair<Color, Color?>? =
    runCatching {
        val ctx = PlatformContext.INSTANCE
        val request = ImageRequest.Builder(ctx)
            .data(artUrl)
            .apply { cacheKey?.let { diskCacheKey(it) } }
            .size(Size(96, 96))
            .build()
        val image = SingletonImageLoader.get(ctx).execute(request).image ?: return null
        val bmp = (image as? BitmapImage)?.bitmap ?: return null

        var rSum = 0L; var gSum = 0L; var bSum = 0L; var n = 0
        var accColor: Color? = null
        var accScore = 0f
        val stepX = max(1, bmp.width / 24)
        val stepY = max(1, bmp.height / 24)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val argb = bmp.getColor(x, y)
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                rSum += r; gSum += g; bSum += b; n++
                // Doygunluk * parlaklık: canlı accent adayı
                val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
                val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
                val score = sat * (mx / 255f)
                if (sat > 0.25f && score > accScore) {
                    accScore = score
                    accColor = Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
                }
                x += stepX
            }
            y += stepY
        }
        if (n == 0) return null
        val avg = Color(
            0xFF000000.toInt() or
                (((rSum / n).toInt()) shl 16) or
                (((gSum / n).toInt()) shl 8) or
                (bSum / n).toInt()
        )
        // Android tarafıyla aynı kurallar: zemin koyu, accent görünür kalsın
        val dom = if (avg.luminance() > 0.25f) lerp(avg, Color.Black, 0.55f) else avg
        val acc = accColor?.let { c ->
            if (c.luminance() < 0.3f) lerp(c, Color.White, 0.35f) else c
        }
        dom to acc
    }.getOrNull()
