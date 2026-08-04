package com.ozgen.navicloud.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.palette.graphics.Palette

/**
 * Widget bitmap araç kutusu — saf [Canvas]/[Paint], sıfır bağımlılık.
 *
 * Widget'ta canlı blur YOK: kapağı küçültüp saf-Kotlin box-blur uygular, koyu
 * scrim + accent bindirip ana ekran hücresine ölçekli tek bitmap üretir. Plak
 * (vinyl) STATİKtir — dönme yok; oluk halkaları + accent label + orta delik
 * bitmap'e baked-in. Tüm boyutlar cross-process ~6MB sınırına göre kırpılır.
 */
object WidgetBitmaps {

    // ---- Kapaktan accent (androidx.palette; zaten :app bağımlılığı) ----

    /** Kapağın hissedilen rengine sadık accent (koyu zeminde görünür minimum parlaklık). */
    fun accentFrom(cover: Bitmap): Int {
        val palette = runCatching { Palette.from(cover).generate() }.getOrNull()
            ?: return WidgetContract.DEFAULT_ACCENT
        val raw = palette.getVibrantColor(
            palette.getLightVibrantColor(palette.getDominantColor(0)),
        )
        if (raw == 0) return WidgetContract.DEFAULT_ACCENT
        // Koyu zeminde okunur olsun diye tonu bozmadan parlaklık tabanı
        return if (luminance(raw) < 0.30f) lerpColor(raw, Color.WHITE, 0.35f) else raw
    }

    // ---- Box blur (saf Kotlin; küçük görüntüde ucuz) ----

    private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        var pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        repeat(passes) {
            pix = blurPass(pix, w, h, radius, horizontal = true)
            pix = blurPass(pix, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    private fun blurPass(src: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean): IntArray {
        val out = IntArray(w * h)
        if (horizontal) {
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var cnt = 0
                    for (dx in -r..r) {
                        val c = src[row + (x + dx).coerceIn(0, w - 1)]
                        a += (c ushr 24) and 0xff; rr += (c ushr 16) and 0xff
                        g += (c ushr 8) and 0xff; b += c and 0xff; cnt++
                    }
                    out[row + x] = pack(a / cnt, rr / cnt, g / cnt, b / cnt)
                }
            }
        } else {
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var cnt = 0
                    for (dy in -r..r) {
                        val c = src[(y + dy).coerceIn(0, h - 1) * w + x]
                        a += (c ushr 24) and 0xff; rr += (c ushr 16) and 0xff
                        g += (c ushr 8) and 0xff; b += c and 0xff; cnt++
                    }
                    out[y * w + x] = pack(a / cnt, rr / cnt, g / cnt, b / cnt)
                }
            }
        }
        return out
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    // ---- Zemin: ön-pişmiş blur + scrim + accent, yuvarlak köşeli ----

    /** Bar zemini: geniş, sol-accent hafif parıltı, dikey karartma. */
    fun barBackground(cover: Bitmap?, wPx: Int, hPx: Int, accent: Int, radiusPx: Float): Bitmap {
        val w = wPx.coerceIn(1, WidgetContract.MAX_BG_W)
        val h = hPx.coerceIn(1, WidgetContract.MAX_BG_H)
        val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        drawBlurredCover(c, cover, w, h)
        // Dikey scrim (okunabilirlik)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradientPaint(
            0f, 0f, 0f, h.toFloat(), 0x33000000, 0xB3000000.toInt(),
        ))
        // Soldan accent parıltısı (kimlik dokunuşu, düşük alfa)
        c.drawRect(0f, 0f, w * 0.6f, h.toFloat(), gradientPaint(
            0f, 0f, w * 0.6f, 0f, (accent and 0xFFFFFF) or 0x3D000000, 0x00000000,
        ))
        return roundCorners(full, radiusPx)
    }

    /** Vinyl zemini: kare, güçlü karartma (plak öne çıksın). */
    fun vinylBackground(cover: Bitmap?, sizePx: Int, radiusPx: Float): Bitmap {
        val s = sizePx.coerceIn(1, WidgetContract.MAX_BG_W)
        val full = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(full)
        drawBlurredCover(c, cover, s, s)
        c.drawColor(0x66000000)
        return roundCorners(full, radiusPx)
    }

    private fun drawBlurredCover(c: Canvas, cover: Bitmap?, w: Int, h: Int) {
        if (cover == null) {
            c.drawColor(0xFF14141B.toInt())
            return
        }
        // ~44px'e küçült → box-blur → hücreye center-crop ölçekle (yumuşak zemin)
        val small = Bitmap.createScaledBitmap(cover, 44, 44, true)
        val blurred = boxBlur(small, 3, 2)
        val scale = maxOf(w / blurred.width.toFloat(), h / blurred.height.toFloat())
        val dw = blurred.width * scale
        val dh = blurred.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        c.drawBitmap(blurred, null, RectF(left, top, left + dw, top + dh), filterPaint())
    }

    // ---- Bar sol kapak (yuvarlak köşeli minik bitmap) ----

    fun roundedCover(cover: Bitmap, sizePx: Int, radiusPx: Float): Bitmap {
        val square = centerSquare(cover, sizePx)
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radiusPx, radiusPx, p)
        return out
    }

    // ---- Vinyl STATİK disk (kapak daire + oluk + accent label + delik) ----

    fun vinylDisc(cover: Bitmap?, sizePx: Int, accent: Int): Bitmap {
        val s = sizePx.coerceIn(1, WidgetContract.MAX_DISC)
        val out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cx = s / 2f
        val cy = s / 2f
        val r = s / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Plak tabanı (siyah)
        p.color = 0xFF0A0A0D.toInt()
        c.drawCircle(cx, cy, r, p)

        // Kapak daireye kırpılı (picture-disc, tüm diski kaplar)
        if (cover != null) {
            val square = centerSquare(cover, s)
            p.shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
            p.shader = null
        } else {
            p.color = 0xFF17171C.toInt()
            c.drawCircle(cx, cy, r * 0.95f, p)
        }

        // Oluk dokusu (ince, düşük alfa) — MiniVinylWindow ile hizalı oranlar
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = maxOf(1f, s * 0.004f)
        ring.color = 0x12000000
        for (i in 0 until 7) {
            c.drawCircle(cx, cy, r * (0.42f + i * 0.075f), ring)
        }

        // Accent label çemberi
        val label = Paint(Paint.ANTI_ALIAS_FLAG)
        label.style = Paint.Style.STROKE
        label.strokeWidth = maxOf(2f, s * 0.012f)
        label.color = (accent and 0xFFFFFF) or 0x8C000000.toInt()
        c.drawCircle(cx, cy, r * 0.30f, label)

        // Orta delik + ince parlak halka
        val hole = Paint(Paint.ANTI_ALIAS_FLAG)
        hole.color = 0xFF08080B.toInt()
        c.drawCircle(cx, cy, r * 0.05f, hole)
        val holeRing = Paint(Paint.ANTI_ALIAS_FLAG)
        holeRing.style = Paint.Style.STROKE
        holeRing.strokeWidth = maxOf(1f, s * 0.004f)
        holeRing.color = 0x4DFFFFFF
        c.drawCircle(cx, cy, r * 0.05f, holeRing)

        return out
    }

    // ---- Yardımcılar ----

    private fun roundCorners(src: Bitmap, radiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radiusPx, radiusPx, p)
        return out
    }

    /** Kaynağı merkezden kare kırpıp hedef boyuta ölçekler. */
    private fun centerSquare(src: Bitmap, sizePx: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        return if (cropped.width == sizePx && cropped.height == sizePx) cropped
        else Bitmap.createScaledBitmap(cropped, sizePx, sizePx, true)
    }

    private fun filterPaint() = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun gradientPaint(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int): Paint {
        val p = Paint()
        p.shader = LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        return p
    }

    private fun luminance(c: Int): Float {
        val r = Color.red(c) / 255f
        val g = Color.green(c) / 255f
        val b = Color.blue(c) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a)
        val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b)
        return Color.rgb(
            (ar + (br - ar) * t).toInt(),
            (ag + (bg - ag) * t).toInt(),
            (ab + (bb - ab) * t).toInt(),
        )
    }
}
