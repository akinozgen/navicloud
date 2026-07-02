package com.ozgen.navicloud.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.PlayerUiState
import kotlinx.coroutines.delay

private enum class MiniDragValue { Rest, Expand }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    player: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.currentItem ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // Finger-tracking swipe-up: commits past 50% / fling, springs back otherwise
    val dragState = remember {
        AnchoredDraggableState(
            initialValue = MiniDragValue.Rest,
            anchors = DraggableAnchors {
                MiniDragValue.Rest at 0f
                MiniDragValue.Expand at with(density) { -110.dp.toPx() }
            },
            positionalThreshold = { distance -> distance * 0.5f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    LaunchedEffect(dragState) {
        snapshotFlow { dragState.settledValue }.collect { value ->
            if (value == MiniDragValue.Expand) {
                onExpand()
                dragState.snapTo(MiniDragValue.Rest)
            }
        }
    }

    LaunchedEffect(state.isPlaying, item.mediaId) {
        while (true) {
            val dur = player.durationMs
            progress = if (dur > 0) player.positionMs.toFloat() / dur else 0f
            delay(500)
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .offset { IntOffset(0, dragState.requireOffset().roundToInt().coerceAtMost(0)) }
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onExpand)
            .anchoredDraggable(dragState, Orientation.Vertical),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.mediaMetadata.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    item.mediaMetadata.title?.toString() ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.mediaMetadata.artist?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { player.togglePlayPause() }) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Duraklat" else "Çal",
                )
            }
            IconButton(onClick = { player.skipNext() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Sonraki")
            }
        }
        // Thin progress line at the bottom edge
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
