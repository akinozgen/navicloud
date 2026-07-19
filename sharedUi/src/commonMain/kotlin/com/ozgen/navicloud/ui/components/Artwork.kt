package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.compose.LocalPlatformContext

/**
 * Resolves a Subsonic coverArt id (+ optional pixel size) to an authenticated URL
 * ve Coil için SABİT cache anahtarı üretir. URL'deki auth salt'ı her uygulama
 * açılışında değiştiği için URL disk cache anahtarı olarak kullanılamaz.
 */
class ArtResolver(
    private val serverId: Long,
    private val urlFor: (String, Int?) -> String?,
) {
    fun url(coverArt: String?, sizePx: Int?): String? = coverArt?.let { urlFor(it, sizePx) }
    fun cacheKey(coverArt: String?, sizePx: Int? = null): String? =
        coverArt?.let { "art:$serverId:$it:${sizePx ?: "full"}" }

    /**
     * Sanatçı foto'su (Last.fm/Navidrome proxy) için SABİT cache anahtarı. URL değil artistId'ye
     * bağlanır — proxy URL'i auth salt'ı yüzünden her açılışta değişir, URL'yle cache tutmazdı.
     */
    fun artistImageCacheKey(artistId: String): String = "artistimg:$serverId:$artistId"
}

val LocalArtResolver = staticCompositionLocalOf { ArtResolver(0L) { _, _ -> null } }

@Composable
fun Artwork(
    coverArt: String?,
    modifier: Modifier = Modifier,
    sizePx: Int? = 300,
    cornerRadius: Dp = 8.dp,
) {
    val resolver = LocalArtResolver.current
    val url = resolver.url(coverArt, sizePx)
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            val key = resolver.cacheKey(coverArt, sizePx)
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(url)
                    .diskCacheKey(key)
                    .memoryCacheKey(key)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
