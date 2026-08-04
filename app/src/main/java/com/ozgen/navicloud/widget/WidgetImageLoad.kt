package com.ozgen.navicloud.widget

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Widget için kapak yükleyici — RemoteViews YAZILIM bitmap ister
 * (`allowHardware(false)`). Uygulamanın tek Coil instance'ını (250MB disk
 * cache) kullanır; anahtar coverArt id'sidir (URL'deki auth salt'ı her açılışta
 * değişir → URL asla cache anahtarı olamaz).
 */
suspend fun loadWidgetCover(
    context: Context,
    url: String,
    cacheKey: String?,
    sizePx: Int,
): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(url)
        .apply {
            cacheKey?.let {
                diskCacheKey(it)
                memoryCacheKey(it)
            }
        }
        .allowHardware(false)
        .size(sizePx)
        .build()
    val image = runCatching { context.imageLoader.execute(request).image }.getOrNull() ?: return null
    return runCatching { image.toBitmap() }.getOrNull()
}
