package com.ozgen.navicloud.ui

import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun rememberToaster(): (String) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
}

private var appContext: android.content.Context? = null

/** NaviCloudApp onCreate'te çağırır — palet çıkarımı context'e ihtiyaç duyar. */
fun initArtColorExtractor(context: android.content.Context) {
    appContext = context.applicationContext
}

actual suspend fun extractArtColors(artUrl: String, cacheKey: String?): Pair<Color, Color?>? {
    val context = appContext ?: return null
    val request = ImageRequest.Builder(context)
        .data(artUrl)
        .apply { cacheKey?.let { diskCacheKey(it) } }
        .allowHardware(false)
        .size(128)
        .build()
    val image = context.imageLoader.execute(request).image ?: return null
    val bitmap = runCatching { image.toBitmap() }.getOrNull() ?: return null
    val palette = Palette.from(bitmap).generate()
    val domRaw = Color(
        palette.getDarkVibrantColor(
            palette.getDarkMutedColor(palette.getMutedColor(0xFF17171E.toInt()))
        )
    )
    val dom = if (domRaw.luminance() > 0.25f) lerp(domRaw, Color.Black, 0.55f) else domRaw
    // Kapağın hissedilen rengine sadık kal: vibrant → lightVibrant → dominant
    val accRaw = palette.getVibrantColor(
        palette.getLightVibrantColor(palette.getDominantColor(0))
    )
    val acc = if (accRaw != 0) {
        var c = Color(accRaw)
        // Koyu zeminde görünürlük için minimum parlaklık, tonu bozmadan
        if (c.luminance() < 0.3f) c = lerp(c, Color.White, 0.35f)
        c
    } else null
    return dom to acc
}
