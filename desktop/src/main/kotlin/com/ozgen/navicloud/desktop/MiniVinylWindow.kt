package com.ozgen.navicloud.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.extractArtColors
import com.ozgen.navicloud.ui.theme.NaviCloudTheme

// Plağın dönme hızı: tam tur / 9 sn (~40°/sn) — sakin, "yavaş dönen plak" hissi.
private const val DEGREES_PER_SEC = 40f

/**
 * Mini oynatıcının 2. varyantı: dönen plak. 1:1 kare, küçük.
 * - Zemin: kapak blur (art-tint dili) — kendi layer'ında statik, her frame yeniden blur'lanmaz.
 * - Kapak daireye kırpılır (picture-disc); çalarken YAVAŞ döner, pause'da durur (açı korunur).
 * - Delik + label halkası: statik vektör overlay, dönmez (radyal simetrik → görünmez fark, ucuz).
 * - Hover'da ortada tek kontrol (play/pause) belirir; hover bitince kaybolur.
 * - Sağ üstte küçük ikon → standart mini oynatıcıya döner. Konum/persist Task 1 ile ortak.
 */
@Composable
fun MiniVinylWindow(player: PlayerController, model: MiniWindowModel, onExpand: () -> Unit) {
    val state = rememberWindowState(
        size = MINI_VINYL_SIZE,
        position = initialMiniPosition(model, MINI_VINYL_SIZE),
    )
    var alwaysOnTop by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Window(
        onCloseRequest = onExpand,
        state = state,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = alwaysOnTop,
        title = "NaviCloud Plak",
    ) {
        val win = window
        // Konum sürükleme bitiminde persist edilir; yıkımda win.x/y stale → onDispose'da okunmaz.
        LaunchedEffect(win) { MiniGeometry.place(win, model) }

        NaviCloudTheme {
            val ps by player.state.collectAsState()
            val track = ps.currentTrack

            // Kapaktan türeyen accent — label halkasına renk kimliği verir
            var accentTarget by remember { mutableStateOf<Color?>(null) }
            LaunchedEffect(track?.song?.coverArt) {
                val url = track?.artworkUrl
                accentTarget = if (url == null) null
                else runCatching { extractArtColors(url, track.song.coverArt) }.getOrNull()?.second
            }
            val accent by animateColorAsState(
                accentTarget ?: MaterialTheme.colorScheme.primary, tween(500), label = "vinylAccent",
            )

            // Tek uzun animasyon: frame saatiyle açıyı artır. Pause'da / pencere
            // gizliyken (minimize) coroutine biter → dönüş tamamen durur, açı korunur.
            var angle by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(ps.isPlaying, state.isMinimized) {
                if (ps.isPlaying && !state.isMinimized) {
                    var last = 0L
                    while (true) {
                        withFrameNanos { now ->
                            if (last != 0L) {
                                val dt = (now - last) / 1_000_000_000f
                                angle = (angle + dt * DEGREES_PER_SEC) % 360f
                            }
                            last = now
                        }
                    }
                }
            }

            // Hover → ortada play/pause
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val controlAlpha by animateFloatAsState(if (hovered) 1f else 0f, tween(160), label = "vinylControls")

            Surface(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                color = Color(0xFF0E0E12),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .hoverable(interaction)
                        .miniWindowDrag(win, model, scope),
                    contentAlignment = Alignment.Center,
                ) {
                    // 1) Blur'lu kapak zemini — statik, kendi layer'ında (art değişince yenilenir)
                    track?.artworkUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.5f,
                            modifier = Modifier.fillMaxSize().blur(34.dp),
                        )
                    }
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)))

                    // 2) Plak + overlay ortak kare alan
                    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                        // Dönen kapak (picture-disc). rotationZ graphicsLayer'da → recomposition yok
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = angle }
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (track?.artworkUrl != null) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(Modifier.fillMaxSize().background(Color(0xFF17171C)))
                            }
                        }

                        // Statik vektör overlay: oluk çizgileri + label halkası + orta delik
                        Canvas(Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2f
                            val c = center
                            // Vinil oluk dokusu (ince, düşük alfa)
                            for (i in 0 until 7) {
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.07f),
                                    radius = r * (0.42f + i * 0.075f),
                                    center = c,
                                    style = Stroke(width = 1f),
                                )
                            }
                            // Label çevresi (accent kimliği)
                            drawCircle(
                                color = accent.copy(alpha = 0.55f),
                                radius = r * 0.30f,
                                center = c,
                                style = Stroke(width = 2f),
                            )
                            // Orta delik + ince parlak halka
                            drawCircle(color = Color(0xFF08080B), radius = r * 0.045f, center = c)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.30f),
                                radius = r * 0.045f, center = c, style = Stroke(width = 1f),
                            )
                        }
                    }

                    // 3) Hover kontrolü: tek play/pause (alfa 0 iken hiç çizilmez → sürükleme serbest)
                    if (controlAlpha > 0.01f) {
                        FilledIconButton(
                            onClick = { player.togglePlayPause() },
                            modifier = Modifier.size(56.dp).graphicsLayer { alpha = controlAlpha },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xE6FFFFFF),
                                contentColor = Color(0xFF0F0F14),
                            ),
                        ) {
                            Icon(
                                if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (ps.isPlaying) "Duraklat" else "Çal",
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    // 4) Sağ üst: standart mini oynatıcıya dön
                    IconButton(
                        onClick = { model.switchVariant(MiniVariant.STANDARD) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CropLandscape,
                            contentDescription = "Standart görünüm",
                            tint = Color(0xCCFFFFFF),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
