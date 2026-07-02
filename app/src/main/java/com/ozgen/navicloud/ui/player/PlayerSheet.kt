package com.ozgen.navicloud.ui.player

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.playback.MediaKeys
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.queueUid
import com.ozgen.navicloud.playback.toSong
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.ozgen.navicloud.ui.components.SongContextMenu
import com.ozgen.navicloud.ui.components.SongItem
import com.ozgen.navicloud.ui.components.formatDuration
import com.ozgen.navicloud.ui.screens.nowplaying.NowPlayingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class SheetValue { Collapsed, Expanded }
private enum class QueuePanelValue { Hidden, Shown }

/**
 * Single persistent player surface: the mini bar and the full player are the
 * same sheet whose artwork, content and background morph continuously with
 * the drag offset — no hard state jump.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(
    vm: NowPlayingViewModel,
    collapseTick: Int,
    modifier: Modifier = Modifier,
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val uiState by vm.state.collectAsStateWithLifecycle()
    val item = playerState.currentItem ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var dominantColor by remember { mutableStateOf(Color(0xFF17171E)) }
    var showLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(item.mediaId) {
        vm.onSongChanged(
            item.mediaId,
            item.mediaMetadata.extras?.getBoolean(MediaKeys.STARRED, false) ?: false,
        )
        val artUri = item.mediaMetadata.artworkUri
        if (artUri != null) {
            withContext(Dispatchers.IO) {
                val result = ImageLoader(context).execute(
                    ImageRequest.Builder(context).data(artUri).allowHardware(false).size(128).build()
                )
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val rgb = palette.getDarkVibrantColor(
                        palette.getDarkMutedColor(palette.getMutedColor(0xFF17171E.toInt()))
                    )
                    val base = Color(rgb)
                    dominantColor = if (base.luminance() > 0.25f) lerp(base, Color.Black, 0.55f) else base
                }
            }
        } else {
            dominantColor = Color(0xFF17171E)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sheetHpx = constraints.maxHeight.toFloat()
        val screenWdp = maxWidth
        val miniHpx = with(density) { 64.dp.toPx() }
        // Collapsed: mini bar sits right above the bottom navigation bar
        val bottomBarPx = with(density) { (80.dp + navBarPad).toPx() }
        val collapsedOffset = (sheetHpx - miniHpx - bottomBarPx).coerceAtLeast(1f)

        val sheetDrag = remember(collapsedOffset) {
            AnchoredDraggableState(
                initialValue = SheetValue.Collapsed,
                anchors = DraggableAnchors {
                    SheetValue.Expanded at 0f
                    SheetValue.Collapsed at collapsedOffset
                },
                positionalThreshold = { it * 0.4f },
                velocityThreshold = { with(density) { 125.dp.toPx() } },
                snapAnimationSpec = spring(),
                decayAnimationSpec = exponentialDecay(),
            )
        }
        val sheetOffset = sheetDrag.requireOffset().coerceIn(0f, collapsedOffset)
        // 0 = mini bar, 1 = full player
        val progress = 1f - (sheetOffset / collapsedOffset)
        val expanded = progress > 0.98f

        // Queue panel: slides up over the player (YTMusic up-next panel).
        // Shown anchor stays clear of the status bar so the header drag
        // doesn't fight the notification shade.
        val queueShownPx = with(density) { (statusPad + 8.dp).toPx() }
        val upNextHpx = with(density) { (44.dp + navBarPad).toPx() }
        val queueHidden = (sheetHpx - upNextHpx - with(density) { 28.dp.toPx() })
            .coerceAtLeast(queueShownPx + 1f)
        val queueDrag = remember(queueHidden, queueShownPx) {
            AnchoredDraggableState(
                initialValue = QueuePanelValue.Hidden,
                anchors = DraggableAnchors {
                    QueuePanelValue.Shown at queueShownPx
                    QueuePanelValue.Hidden at queueHidden
                },
                positionalThreshold = { it * 0.4f },
                velocityThreshold = { with(density) { 125.dp.toPx() } },
                snapAnimationSpec = spring(),
                decayAnimationSpec = exponentialDecay(),
            )
        }
        val queueOffset = queueDrag.requireOffset().coerceIn(queueShownPx, queueHidden)
        val queueProgress = 1f - ((queueOffset - queueShownPx) / (queueHidden - queueShownPx))
        val queueShown = queueProgress > 0.98f

        // External collapse requests (e.g. menu navigation)
        LaunchedEffect(collapseTick) {
            if (collapseTick > 0) {
                queueDrag.animateTo(QueuePanelValue.Hidden)
                sheetDrag.animateTo(SheetValue.Collapsed)
            }
        }

        // Media notification tap → open with the player expanded
        val expandReq by vm.player.expandRequests.collectAsStateWithLifecycle()
        LaunchedEffect(expandReq) {
            if (expandReq > 0) sheetDrag.animateTo(SheetValue.Expanded)
        }

        BackHandler(enabled = expanded || queueShown) {
            scope.launch {
                if (queueShown) queueDrag.animateTo(QueuePanelValue.Hidden)
                else sheetDrag.animateTo(SheetValue.Collapsed)
            }
        }

        // ---- Sheet surface (height grows with the drag so the nav bar stays visible when collapsed) ----
        val sheetHeightDp = with(density) { lerpDp(64.dp, sheetHpx.toDp(), progress) }
        Box(
            Modifier
                .offset { IntOffset(0, sheetOffset.roundToInt()) }
                .fillMaxWidth()
                .height(sheetHeightDp)
                .padding(horizontal = lerpDp(8.dp, 0.dp, progress))
                .clip(RoundedCornerShape(lerpDp(10.dp, 0.dp, progress)))
                .background(
                    lerp(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.background,
                        progress,
                    )
                )
                .anchoredDraggable(sheetDrag, Orientation.Vertical)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = expanded,
                ) { /* consume stray taps behind the full player */ },
        ) {
            // Gradient grows in as the sheet expands
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(dominantColor, MaterialTheme.colorScheme.background),
                            endY = 1800f,
                        )
                    ),
            )

            // ---- Morph geometry ----
            // mini slot <-> full slot (margined, rounded — YT mindset: dominant but framed)
            // and full slot <-> queue dock slot while the queue rises
            val fullArtW = screenWdp - 32.dp
            val artSize = lerpDp(lerpDp(48.dp, fullArtW, progress), 44.dp, queueProgress)
            val artX = lerpDp(lerpDp(8.dp, 16.dp, progress), 16.dp, queueProgress)
            val artY = lerpDp(lerpDp(8.dp, statusPad + 56.dp, progress), statusPad + 14.dp, queueProgress)

            // Mini row (fades out quickly)
            if (progress < 0.4f) {
                MiniRow(
                    vm = vm,
                    isPlaying = playerState.isPlaying,
                    title = item.mediaMetadata.title?.toString() ?: "",
                    artist = item.mediaMetadata.artist?.toString() ?: "",
                    alpha = (1f - progress * 2.5f).coerceIn(0f, 1f),
                    onClick = { scope.launch { sheetDrag.animateTo(SheetValue.Expanded) } },
                )
                // Thin progress line pinned to the mini bar's bottom edge
                MiniProgressLine(
                    player = vm.player,
                    isPlaying = playerState.isPlaying,
                    mediaId = item.mediaId,
                    alpha = (1f - progress * 2.5f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = 62.dp),
                )
            }

            // Full player content (fades in)
            if (progress > 0.05f) {
                FullPlayerContent(
                    vm = vm,
                    starred = uiState.starred,
                    statusPad = statusPad,
                    artSpace = screenWdp - 32.dp,
                    // fades in while expanding, fades out again as the queue rises
                    contentAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) *
                        (1f - queueProgress).coerceIn(0f, 1f),
                    onCollapse = { scope.launch { sheetDrag.animateTo(SheetValue.Collapsed) } },
                    onOpenLyrics = {
                        vm.loadLyrics(item.mediaId)
                        showLyrics = true
                    },
                )
            }

            // The one artwork, morphing between the two slots. Fully expanded it
            // becomes a pager: horizontal swipe = previous/next track.
            val artModifier = Modifier
                .offset { with(density) { IntOffset(artX.roundToPx(), artY.roundToPx()) } }
                .size(artSize)
                .zIndex(3f) // stays visible above the rising queue panel (dock slot)
                .clip(RoundedCornerShape(lerpDp(6.dp, 12.dp, progress)))
            if (expanded && !queueShown) {
                ArtPager(player = vm.player, modifier = artModifier)
            } else {
                AsyncImage(
                    model = item.mediaMetadata.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = artModifier,
                )
            }

            // ---- Queue panel (only relevant once expanded) ----
            if (progress > 0.9f) {
                // Dim the player behind as the queue slides up — the sliver above
                // the panel reads as a scrim, not a chopped player
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(1.5f)
                        .graphicsLayer { alpha = (queueProgress * 0.75f).coerceIn(0f, 0.75f) }
                        .background(Color.Black),
                )
                val ctxLabel by vm.player.contextLabel.collectAsStateWithLifecycle()
                QueuePanel(
                    player = vm.player,
                    queueDrag = queueDrag,
                    queueOffset = queueOffset,
                    queueProgress = queueProgress,
                    contextLabel = ctxLabel,
                    bottomPad = navBarPad + statusPad + 8.dp,
                    onShow = { scope.launch { queueDrag.animateTo(QueuePanelValue.Shown) } },
                )
            }
        }
    }

    if (showLyrics) {
        ModalBottomSheet(onDismissRequest = { showLyrics = false }) {
            LyricsSheet(
                lyrics = uiState.lyrics,
                loading = uiState.lyricsLoading,
                positionMsProvider = { vm.player.positionMs },
            )
        }
    }
}

/** Expanded-state artwork pager: horizontal swipe skips to previous/next track. */
@Composable
private fun ArtPager(player: PlayerController, modifier: Modifier = Modifier) {
    val state by player.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex.coerceAtLeast(0),
    ) { state.queue.size }

    // Player -> pager (track changed elsewhere)
    LaunchedEffect(state.currentIndex, state.queue.size) {
        if (!pagerState.isScrollInProgress &&
            state.currentIndex in 0 until state.queue.size &&
            pagerState.currentPage != state.currentIndex
        ) {
            pagerState.animateScrollToPage(state.currentIndex)
        }
    }
    // Pager -> player (user swiped the art)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val current = player.state.value
            if (page != current.currentIndex && page in current.queue.indices) {
                player.seekToQueueItem(page)
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        AsyncImage(
            model = state.queue.getOrNull(page)?.mediaMetadata?.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun MiniProgressLine(
    player: PlayerController,
    isPlaying: Boolean,
    mediaId: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, mediaId) {
        while (true) {
            val dur = player.durationMs
            progress = if (dur > 0) player.positionMs.toFloat() / dur else 0f
            delay(500)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0x33FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun MiniRow(
    vm: NowPlayingViewModel,
    isPlaying: Boolean,
    title: String,
    artist: String,
    alpha: Float,
    onClick: () -> Unit,
) {
    // The sheet lives outside any Surface, so LocalContentColor defaults to
    // black — every color here must be explicit.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer { this.alpha = alpha }
            .clickable(onClick = onClick)
            .padding(start = 64.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xB3FFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { vm.player.togglePlayPause() }) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Duraklat" else "Çal",
                tint = Color.White,
            )
        }
        IconButton(onClick = { vm.player.skipNext() }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Sonraki", tint = Color.White)
        }
    }
}

@Composable
private fun FullPlayerContent(
    vm: NowPlayingViewModel,
    starred: Boolean,
    statusPad: androidx.compose.ui.unit.Dp,
    artSpace: androidx.compose.ui.unit.Dp,
    contentAlpha: Float,
    onCollapse: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val item = playerState.currentItem
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playerState.isPlaying, item?.mediaId) {
        while (true) {
            positionMs = vm.player.positionMs
            durationMs = vm.player.durationMs
            delay(500)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .padding(top = statusPad),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Kapat", tint = Color.White)
            }
            Text(
                item?.mediaMetadata?.albumTitle?.toString() ?: "Şu an çalıyor",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Current song's context menu, top right
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Seçenekler", tint = Color.White)
                }
                item?.let {
                    SongContextMenu(
                        song = it.toSong(),
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                    )
                }
            }
        }

        // Artwork lands here (drawn by the sheet as the morphing image)
        Spacer(Modifier.height(artSpace))

        Column(Modifier.padding(horizontal = 24.dp).padding(top = 16.dp)) {
            // Title (bold) + chevron → album; artist muted below. Small icons right.
            val menuActions = com.ozgen.navicloud.ui.components.LocalSongMenu.current
            val albumId = item?.mediaMetadata?.extras?.getString(MediaKeys.ALBUM_ID)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            enabled = albumId != null && menuActions != null,
                        ) { albumId?.let { menuActions?.goToAlbum?.invoke(it) } },
                    ) {
                        Text(
                            item?.mediaMetadata?.title?.toString() ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (albumId != null) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = "Albüme git",
                                tint = Color(0x99FFFFFF),
                            )
                        }
                    }
                    Text(
                        item?.mediaMetadata?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenLyrics) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = "Şarkı sözleri", tint = Color(0xB3FFFFFF))
                }
                IconButton(onClick = { item?.mediaId?.let { vm.toggleStar(it) } }) {
                    Icon(
                        if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (starred) MaterialTheme.colorScheme.primary else Color(0xB3FFFFFF),
                    )
                }
            }

            val sliderValue = if (dragging) dragValue
            else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    vm.player.seekTo((dragValue * durationMs).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x4DFFFFFF),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration((if (dragging) (dragValue * durationMs / 1000).toInt() else (positionMs / 1000).toInt())),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99FFFFFF),
                )
                Text(
                    formatDuration((durationMs / 1000).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99FFFFFF),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { vm.player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Karıştır",
                        tint = if (playerState.shuffle) MaterialTheme.colorScheme.primary else Color(0xB3FFFFFF),
                    )
                }
                IconButton(onClick = { vm.player.skipPrevious() }) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Önceki",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                FilledIconButton(
                    onClick = { vm.player.togglePlayPause() },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0F0F14),
                    ),
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Duraklat" else "Çal",
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = { vm.player.skipNext() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Sonraki",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    Icon(
                        if (playerState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        contentDescription = "Tekrar",
                        tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else Color(0xB3FFFFFF),
                    )
                }
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePanel(
    player: PlayerController,
    queueDrag: AnchoredDraggableState<QueuePanelValue>,
    queueOffset: Float,
    queueProgress: Float,
    contextLabel: String?,
    bottomPad: androidx.compose.ui.unit.Dp,
    onShow: () -> Unit,
) {
    val state by player.state.collectAsStateWithLifecycle()
    val endless by player.endless.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Working copy the reorder library mutates INSTANTLY; Media3 catches up
    // asynchronously. Feeding the async state straight back caused runaway
    // moves (stale indices) and flicker.
    val localQueue = remember { androidx.compose.runtime.mutableStateListOf<androidx.media3.common.MediaItem>() }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in localQueue.indices && to.index in localQueue.indices) {
            // Local list moves by index (visual order); Media3 mirror resolves
            // the item by UID so a lagging sync can't move the wrong song
            val uid = localQueue[from.index].queueUid()
            localQueue.add(to.index, localQueue.removeAt(from.index))
            player.moveQueueItemUidTo(uid, to.index)
        }
    }
    LaunchedEffect(state.queue, reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            localQueue.clear()
            localQueue.addAll(state.queue)
        }
    }

    Box(
        Modifier
            .offset { IntOffset(0, queueOffset.roundToInt()) }
            .fillMaxSize()
            .zIndex(2f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            // Transparent while resting as the up-next hint; solid as it slides up
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = (queueProgress * 3f).coerceIn(0f, 1f),
                )
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Drag zone: both header states stay composed and crossfade —
            // the composition swap at 50% caused a visible hitch mid-drag
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .anchoredDraggable(queueDrag, Orientation.Vertical)
                    .clickable(enabled = queueProgress < 0.5f, onClick = onShow),
            ) {
                // Hidden state: drag pill + up-next hint
                val nextItem = state.queue.getOrNull(state.currentIndex + 1)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - queueProgress * 2f).coerceIn(0f, 1f) }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x4DFFFFFF)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (nextItem != null) "Sıradaki: ${nextItem.mediaMetadata.title}" else "Kuyruk",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0x99FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                // Shown state: docked mini player — the morphing artwork lands
                // in the 56dp leading slot (drawn by the sheet, zIndex above)
                val currentItem = state.queue.getOrNull(state.currentIndex)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = ((queueProgress - 0.5f) * 2f).coerceIn(0f, 1f) }
                        .padding(start = 72.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            currentItem?.mediaMetadata?.title?.toString() ?: "Kuyruk",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            currentItem?.mediaMetadata?.artist?.toString() ?: "${state.queue.size} şarkı",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0x99FFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { player.stop() }) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Durdur", tint = Color(0x80FFFFFF))
                    }
                    FilledIconButton(
                        onClick = { player.togglePlayPause() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F0F14),
                        ),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.isPlaying) "Duraklat" else "Çal",
                        )
                    }
                }
            }

            // Context + autoplay rows (only meaningful once the panel is up)
            if (queueProgress > 0.3f) {
                Column(Modifier.graphicsLayer { alpha = ((queueProgress - 0.3f) / 0.7f).coerceIn(0f, 1f) }) {
                    if (contextLabel != null) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(
                                "Şuradan çalınıyor:",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0x99FFFFFF),
                            )
                            Text(
                                contextLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Otomatik oynatma",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                            )
                            Text(
                                "Kuyruk bitince benzer içerikle devam eder",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0x99FFFFFF),
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = endless,
                            onCheckedChange = { player.toggleEndless() },
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                // Stays composed (no first-drag jank) but invisible & inert while hidden
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = queueProgress },
                userScrollEnabled = queueProgress > 0.3f,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = bottomPad + 8.dp,
                ),
            ) {
                items(
                    localQueue.size,
                    key = { localQueue[it].queueUid() },
                    contentType = { "song" },
                ) { i ->
                    val queueItem = localQueue[i]
                    val uid = queueItem.queueUid()
                    ReorderableItem(reorderableState, key = uid) { isDragging ->
                        val dragScope = this
                        // Swipe left→right removes; right→left bumps to play-next.
                        // rememberSwipeToDismissBoxState freezes this lambda at first
                        // composition — so it must only capture the row's immutable UID,
                        // never its index (stale indices removed the wrong songs).
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        localQueue.removeAll { it.queueUid() == uid }
                                        player.removeQueueItemByUid(uid)
                                        true
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        // Adds a copy right after the current track;
                                        // the swiped row stays where it is
                                        player.playNext(listOf(queueItem.toSong()))
                                        false
                                    }
                                    else -> false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Row(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.RemoveCircleOutline,
                                        contentDescription = "Kuyruktan kaldır",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        Icons.Rounded.PlaylistPlay,
                                        contentDescription = "Sıradakine taşı",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        ) {
                            SongItem(
                                song = queueItem.toSong(),
                                onClick = { player.seekToUid(uid) },
                                highlighted = i == state.currentIndex,
                                inQueue = true,
                                queueIndex = i,
                                modifier = Modifier
                                    .height(64.dp)
                                    .background(
                                        // Playing row separates with a primary tint
                                        if (i == state.currentIndex) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    )
                                    .graphicsLayer { alpha = if (isDragging) 0.85f else 1f },
                                trailingContent = {
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = "Sürükle",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        // Long-press to reorder: an always-on handle swallows
                                        // horizontal swipes too, killing swipe-to-play-next
                                        modifier = with(dragScope) { Modifier.longPressDraggableHandle() },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsSheet(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMsProvider: () -> Long,
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = positionMsProvider()
            delay(300)
        }
    }

    when {
        loading -> Box(
            Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Sözler yükleniyor…") }
        lyrics == null || lyrics.lines.isEmpty() -> Box(
            Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Bu şarkı için söz bulunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> {
            val listState = rememberLazyListState()
            val activeIndex = if (lyrics.synced) {
                lyrics.lines.indexOfLast { (it.startMs ?: 0) <= positionMs }.coerceAtLeast(0)
            } else -1

            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) listState.animateScrollToItem(maxOf(activeIndex - 3, 0))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(500.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            ) {
                items(lyrics.lines.size) { i ->
                    val line = lyrics.lines[i]
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                            i == activeIndex -> MaterialTheme.colorScheme.primary
                            i < activeIndex -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}
