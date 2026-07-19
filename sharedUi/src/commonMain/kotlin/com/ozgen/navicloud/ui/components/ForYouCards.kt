package com.ozgen.navicloud.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.core.model.Artist
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.ui.i18n.LocalStrings

/**
 * For You raf bileşenleri (docs/home/PLAN.md): Mix hero — geniş ekranda ince tek-satır
 * boydan-boya STRIP, telefonda 2x yüksek kolaj HERO — radyo kartı ve ghost iskeletler.
 * Mix her iki biçimde de tam genişlik kaplar (eski widthIn(600) "yarım blok" derdi çözüldü).
 */

/** İlk 4 kapaktan 2x2 kolaj (yoksa tek kapak). Hem hero hem strip küçük görsel bunu kullanır. */
@Composable
private fun MixCollage(covers: List<String?>, modifier: Modifier = Modifier) {
    if (covers.size < 4) {
        Artwork(covers.firstOrNull(), sizePx = 800, cornerRadius = 0.dp, modifier = modifier.fillMaxSize())
    } else {
        Column(modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Artwork(covers[0], sizePx = 400, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
                Artwork(covers[1], sizePx = 400, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
            }
            Row(Modifier.weight(1f)) {
                Artwork(covers[2], sizePx = 400, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
                Artwork(covers[3], sizePx = 400, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

/**
 * Geniş ekran (masaüstü/tablet) Mix biçimi: ince, tek-satır, boydan-boya kart.
 * Sol kolaj küçük görsel, ortada başlık + alt bilgi, sağda büyük Play. Kart tıklanınca
 * (onOpen) kalıcı playlist'e gider, Play doğrudan çalar.
 */
@Composable
fun MixHeroStrip(
    songs: List<Song>,
    onPlay: () -> Unit,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val covers = songs.mapNotNull { it.coverArt }.distinct().take(4)
    Box(
        modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    1f to MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MixCollage(covers, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(
                    strings.homeMixTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    strings.homeMixSubtitle(songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    strings.homeMixBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = strings.commonPlay, modifier = Modifier.size(30.dp))
            }
        }
    }
}

/** Telefon Mix biçimi: 2x2 kolaj + scrim + büyük Play + rozet (tek baskın eleman, tam genişlik). */
@Composable
fun MixHeroCard(
    songs: List<Song>,
    onPlay: () -> Unit,
    onBadgeClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val covers = songs.mapNotNull { it.coverArt }.distinct().take(4)
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        MixCollage(covers, Modifier.fillMaxSize())
        // Dikey scrim: metinler her kolajda okunsun
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.homeMixTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        strings.homeMixSubtitle(songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = strings.commonPlay, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                strings.homeMixBadge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .then(if (onBadgeClick != null) Modifier.clickable(onClick = onBadgeClick) else Modifier),
            )
        }
    }
}

/** Sanatçı radyosu kartı: kapak ya da harf monogramı + RADYO chip'i + morph'lu play/spinner. */
@Composable
fun RadioCard(
    artist: Artist,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !busy, onClick = onClick),
    ) {
        if (artist.coverArt != null) {
            Artwork(artist.coverArt, sizePx = 400, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
        } else {
            // Monogram: foto bağımlılığı yok (Spotify agent bilinçli olarak kullanılmıyor)
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    artist.name.firstOrNull()?.uppercase() ?: "♪",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.35f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
        )
        // Albüm kartıyla karışmasın: sol üstte ayrıştırıcı kimlik
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.Sensors,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
            Text(strings.homeRadioChip, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 48.dp, bottom = 10.dp),
        )
        // Play → spinner morph'u: Last.fm 1-3 sn sürer, ani değişim "çiğ" olurdu
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = busy, label = "radioBusy") { isBusy ->
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = strings.commonPlay,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Raf ghost'u: nabız atan yer tutucu (yükleniyorken raf boş/çirkin görünmesin). */
@Composable
fun GhostCard(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val pulse = rememberInfiniteTransition(label = "ghostCard")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ghostCardAlpha",
    )
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)),
    )
}
