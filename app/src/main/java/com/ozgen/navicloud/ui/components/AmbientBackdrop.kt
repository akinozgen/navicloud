package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Content-derived ambient glow behind collection headers: the cover art,
 * blurred once (RenderEffect, minSdk 31), fading into the background.
 * Depth comes from layering, not fake glass.
 */
@Composable
fun AmbientBackdrop(
    coverArt: String?,
    modifier: Modifier = Modifier,
    height: Dp = 420.dp,
) {
    val resolver = LocalArtResolver.current
    val url = resolver.url(coverArt, 200) ?: return
    val key = resolver.cacheKey(coverArt, 200)
    Box(modifier.fillMaxWidth().height(height)) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(url)
                .diskCacheKey(key)
                .memoryCacheKey(key)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.30f }
                .blur(64.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to androidx.compose.material3.MaterialTheme.colorScheme.background,
                    )
                ),
        )
    }
}
