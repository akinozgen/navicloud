package com.ozgen.navicloud.ui.player

import com.ozgen.navicloud.ui.PlatformBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.compose.LocalPlatformContext
import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.playback.PlayerController
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.ozgen.navicloud.audio.SleepTimerPreset
import com.ozgen.navicloud.ui.components.SongContextMenuItems
import com.ozgen.navicloud.ui.components.SongItem
import com.ozgen.navicloud.ui.components.WavySeekBar
import com.ozgen.navicloud.ui.components.formatDuration
import com.ozgen.navicloud.ui.i18n.LocalStrings
import com.ozgen.navicloud.ui.screens.nowplaying.NowPlayingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class SheetValue { Collapsed, Expanded }
private enum class QueuePanelValue { Hidden, Shown }

// Track başına 1 kez palette: (dominant, accent). LRU, recomposition'da hesap yok.
private val paletteCache = java.util.Collections.synchronizedMap(
    object : LinkedHashMap<String, Pair<Color, Color?>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Pair<Color, Color?>>,
        ): Boolean = size > 24
    },
)

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
    /** Altındaki gezinme çubuğunun yüksekliği; rail'li geniş düzende 0. */
    bottomBarHeight: androidx.compose.ui.unit.Dp = 80.dp,
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val uiState by vm.state.collectAsStateWithLifecycle()
    val item = playerState.currentTrack ?: return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var dominantTarget by remember { mutableStateOf(Color(0xFF17171E)) }
    var accentTarget by remember { mutableStateOf<Color?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showAudioFx by remember { mutableStateOf(false) }

    val artResolver = com.ozgen.navicloud.ui.components.LocalArtResolver.current
    LaunchedEffect(item.song.id) {
        vm.onSongChanged(
            item.song.id,
            item.song.starred,
        )
        val artUri = item.artworkUrl
        if (artUri == null) {
            dominantTarget = Color(0xFF17171E)
            accentTarget = null
            return@LaunchedEffect
        }
        val coverId = item.song.coverArt
        paletteCache[coverId ?: artUri]?.let { (dom, acc) ->
            dominantTarget = dom
            accentTarget = acc
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            // Palet çıkarımı platforma özgü (Android: Palette API); masaüstü
            // null döner ve varsayılan koyu tema renkleri kullanılır
            val colors = runCatching {
                com.ozgen.navicloud.ui.extractArtColors(artUri, artResolver.cacheKey(coverId))
            }.getOrNull() ?: return@withContext
            paletteCache[coverId ?: artUri] = colors
            dominantTarget = colors.first
            accentTarget = colors.second
        }
    }
    // İçerikten türeyen renkler; parça değişiminde yumuşak geçiş
    val dominantColor by animateColorAsState(dominantTarget, tween(600), label = "dominant")
    val accentColor by animateColorAsState(
        accentTarget ?: MaterialTheme.colorScheme.primary,
        tween(600),
        label = "accent",
    )

    BoxWithConstraints(modifier.fillMaxSize()) {
        val sheetHpx = constraints.maxHeight.toFloat()
        val screenWdp = maxWidth
        val screenHdp = this@BoxWithConstraints.maxHeight
        val miniHpx = with(density) { 64.dp.toPx() }
        // Collapsed: mini bar sits right above the bottom navigation bar
        // (geniş düzende bottomBarHeight=0 → nav inset'inin hemen üstü)
        val bottomBarPx = with(density) { (bottomBarHeight + navBarPad).toPx() }
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

        PlatformBackHandler(enabled = expanded || queueShown) {
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
            // Depth: blurred artwork as ambient backdrop (single blur layer,
            // RenderEffect — minSdk 31), the dominant gradient tames it on top
            val artKey = artResolver.cacheKey(item.song.coverArt)
            if (progress > 0.1f) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(item.artworkUrl)
                        .diskCacheKey(artKey).memoryCacheKey(artKey)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.35f * progress }
                        .blur(48.dp),
                )
            }
            // Gradient grows in as the sheet expands
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(dominantColor.copy(alpha = 0.9f), MaterialTheme.colorScheme.background),
                            endY = 1800f,
                        )
                    ),
            )

            // ---- Morph geometry ----
            // mini slot <-> full slot (margined, rounded — YT mindset: dominant but framed)
            // and full slot <-> queue dock slot while the queue rises
            // Dik ekranda kapak üstte, kontroller altta (telefon/dik tablet,
            // genişse kapak sınırlanıp ortalanır). Kısa-geniş ekranda (yatay)
            // dikey istif sığmaz: kapak solda, kontroller sağda
            // Kisa ekran (yatay telefon/tablet) YA DA genis pencere (masaustu):
            // kapak solda, kontroller sagda
            val sideBySide = screenHdp < 640.dp || screenWdp >= 900.dp
            // 144 = üst başlık 56 + alttaki kuyruk ipucu 72 + nefes payı
            val fullArtW =
                if (sideBySide) minOf(screenHdp - statusPad - 144.dp, 400.dp)
                else minOf(screenWdp - 32.dp, screenHdp * 0.42f, 420.dp)
            val artFullX = if (sideBySide) 32.dp else (screenWdp - fullArtW) / 2
            val artFullY =
                if (sideBySide) {
                    statusPad + 56.dp + ((screenHdp - statusPad - 144.dp - fullArtW) / 2)
                        .coerceAtLeast(0.dp)
                } else {
                    statusPad + 56.dp
                }
            val artSize = lerpDp(lerpDp(48.dp, fullArtW, progress), 44.dp, queueProgress)
            val artX = lerpDp(lerpDp(8.dp, artFullX, progress), 16.dp, queueProgress)
            val artY = lerpDp(lerpDp(8.dp, artFullY, progress), statusPad + 14.dp, queueProgress)

            // Mini row (fades out quickly)
            if (progress < 0.4f) {
                MiniRow(
                    vm = vm,
                    isPlaying = playerState.isPlaying,
                    title = item.song.title,
                    artist = item.song.artist ?: "",
                    alpha = (1f - progress * 2.5f).coerceIn(0f, 1f),
                    onClick = { scope.launch { sheetDrag.animateTo(SheetValue.Expanded) } },
                )
                // Thin progress line pinned to the mini bar's bottom edge
                MiniProgressLine(
                    player = vm.player,
                    isPlaying = playerState.isPlaying,
                    mediaId = item.song.id,
                    accent = accentColor,
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
                    accent = accentColor,
                    statusPad = statusPad,
                    artSpace = fullArtW,
                    sideBySide = sideBySide,
                    // fades in while expanding, fades out again as the queue rises
                    contentAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f) *
                        (1f - queueProgress).coerceIn(0f, 1f),
                    onCollapse = { scope.launch { sheetDrag.animateTo(SheetValue.Collapsed) } },
                    onOpenLyrics = {
                        vm.loadLyrics(item.song)
                        showLyrics = true
                    },
                    onOpenSleepTimer = { showSleepTimer = true },
                    onOpenAudioFx = { showAudioFx = true },
                )
            }

            // The one artwork, morphing between the two slots. Fully expanded it
            // becomes a pager: horizontal swipe = previous/next track.
            val artModifier = Modifier
                .offset { with(density) { IntOffset(artX.roundToPx(), artY.roundToPx()) } }
                .size(artSize)
                .zIndex(3f) // stays visible above the rising queue panel (dock slot)
                // Depth: soft shadow grows with expansion, fades as art docks
                .shadow(
                    elevation = lerpDp(0.dp, 18.dp, progress * (1f - queueProgress)),
                    shape = RoundedCornerShape(lerpDp(6.dp, 12.dp, progress)),
                    clip = false,
                )
                .clip(RoundedCornerShape(lerpDp(6.dp, 12.dp, progress)))
            if (expanded && !queueShown) {
                ArtPager(player = vm.player, modifier = artModifier)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(item.artworkUrl)
                        .diskCacheKey(artKey).memoryCacheKey(artKey)
                        .build(),
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
                    accent = accentColor,
                    bottomPad = navBarPad + statusPad + 8.dp,
                    // Yan yana düzende "Sıradaki" ipucu soldaki kapağın
                    // altına girmesin — sağ panele hizala
                    hintStartPad = if (sideBySide) fullArtW + 64.dp else 0.dp,
                    onShow = { scope.launch { queueDrag.animateTo(QueuePanelValue.Shown) } },
                    onHide = { scope.launch { queueDrag.animateTo(QueuePanelValue.Hidden) } },
                )
            }
        }
    }

    if (showLyrics) {
        // Tam açık başlat: kısmi-açık durumda ModalBottomSheet iç scroll'u yutuyordu
        // (özellikle synced olmayan/otomatik kaymayan sözlerde sadece ilk satırlar görünüyordu).
        val lyricsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showLyrics = false }, sheetState = lyricsSheetState) {
            LyricsSheet(
                lyrics = uiState.lyrics,
                loading = uiState.lyricsLoading,
                positionMsProvider = { vm.player.positionMs },
                onSeekLine = { ms -> vm.player.seekTo(ms) },
            )
        }
    }

    if (showSleepTimer) {
        ModalBottomSheet(onDismissRequest = { showSleepTimer = false }) {
            SleepTimerSheet(player = vm.player, onClose = { showSleepTimer = false })
        }
    }

    if (showAudioFx) {
        val fx = com.ozgen.navicloud.ui.LocalAppContainer.current.audioEffects
        // Tam açık başlat: telefonda "partially expanded" durumu, içerik değişince
        // (efekt açıp kapatınca) sheet'i yeniden konumlandırıp iç scroll'u başa
        // sarıyordu. skipPartiallyExpanded bu zıplamayı önler.
        ModalBottomSheet(
            onDismissRequest = { showAudioFx = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            com.ozgen.navicloud.ui.components.AudioEffectsSheet(fx = fx)
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

    val resolver = com.ozgen.navicloud.ui.components.LocalArtResolver.current
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        val qItem = state.queue.getOrNull(page)
        val key = resolver.cacheKey(qItem?.song?.coverArt)
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(qItem?.artworkUrl)
                .diskCacheKey(key).memoryCacheKey(key)
                .build(),
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
    accent: Color,
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
                .background(accent),
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
    val strings = LocalStrings.current
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
                contentDescription = if (isPlaying) strings.commonPause else strings.commonPlay,
                tint = Color.White,
            )
        }
        IconButton(onClick = { vm.player.skipNext() }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = strings.commonNext, tint = Color.White)
        }
    }
}

@Composable
private fun FullPlayerContent(
    vm: NowPlayingViewModel,
    starred: Boolean,
    accent: Color,
    statusPad: androidx.compose.ui.unit.Dp,
    artSpace: androidx.compose.ui.unit.Dp,
    contentAlpha: Float,
    onCollapse: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenAudioFx: () -> Unit,
    /** Yatay/kısa ekran: kapak solda, kontroller sağda. */
    sideBySide: Boolean = false,
) {
    val strings = LocalStrings.current
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val item = playerState.currentTrack
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    // seekTo async: hedef oturana kadar poll'u bastır, yoksa eski pozisyona
    // "pop-back" görünür. (hedefMs, setAtMs)
    var pendingSeek by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    LaunchedEffect(playerState.isPlaying, item?.song?.id) {
        while (true) {
            val reported = vm.player.positionMs
            val pending = pendingSeek
            val settled = pending == null ||
                kotlin.math.abs(reported - pending.first) < 1200 ||
                System.currentTimeMillis() - pending.second > 1500
            if (settled) {
                pendingSeek = null
                if (!dragging) positionMs = reported
            }
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
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = strings.commonClose, tint = Color.White)
            }
            Text(
                item?.song?.album ?: strings.playerNowPlaying,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Uzaktan kumanda — cihaz seçici + durum göstergesi (banner yerine ikon+badge).
            // İki ayrı durum, iki ayrı ikon: BEN başkasını sürüyorum (CastConnected/primary)
            // vs BENİ başkası sürüyor (SettingsRemote/tertiary). Badge = "canlı" vurgusu.
            // Basınca cihaz menüsü açılır — "kumandayı al"/disconnect orada.
            val rc = com.ozgen.navicloud.ui.LocalAppContainer.current.remoteControl
            if (rc != null) {
                val rcTarget by rc.target.collectAsStateWithLifecycle()
                val controlledBy by rc.controlledBy.collectAsStateWithLifecycle()
                var showDevicePicker by remember { mutableStateOf(false) }
                val isRemote = rcTarget is com.ozgen.navicloud.remote.ControlTarget.Remote
                val isControlled = controlledBy > 0
                val rcIcon = when {
                    isRemote -> Icons.Rounded.CastConnected
                    isControlled -> Icons.Rounded.SettingsRemote
                    else -> Icons.Rounded.Cast
                }
                val rcTint = when {
                    isRemote -> MaterialTheme.colorScheme.primary
                    isControlled -> MaterialTheme.colorScheme.tertiary
                    else -> Color.White
                }
                IconButton(onClick = { showDevicePicker = true }) {
                    androidx.compose.material3.BadgedBox(
                        badge = {
                            if (isRemote || isControlled) {
                                androidx.compose.material3.Badge(containerColor = rcTint)
                            }
                        },
                    ) {
                        Icon(rcIcon, contentDescription = strings.playerSelectDevice, tint = rcTint)
                    }
                }
                if (showDevicePicker) {
                    com.ozgen.navicloud.ui.components.DevicePickerSheet(onDismiss = { showDevicePicker = false })
                }
            }
            // Mini oynatıcı — yalnız masaüstünde (toggle sağlandıysa)
            val miniToggle = com.ozgen.navicloud.ui.LocalMiniPlayerToggle.current
            if (miniToggle != null) {
                IconButton(onClick = miniToggle) {
                    Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = strings.playerMiniPlayer, tint = Color.White)
                }
            }
            // Aktif uyku zamanlayıcı göstergesi: süreli → geri sayım (mm:ss),
            // parça/kuyruk bound → durum etiketi. Tıklayınca zamanlayıcı sheet'i.
            val sleepState by vm.player.sleepTimer.collectAsStateWithLifecycle()
            if (sleepState.active) {
                val sleepLabel = when (val p = sleepState.preset) {
                    is SleepTimerPreset.Duration ->
                        sleepState.remainingMs?.let { formatDuration((it / 1000).toInt()) } ?: strings.playerSleepMinutesShort(p.minutes)
                    SleepTimerPreset.EndOfTrack -> strings.playerSleepEndOfTrack
                    SleepTimerPreset.EndOfQueue -> strings.playerSleepEndOfQueue
                    null -> ""
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpenSleepTimer() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = strings.playerSleepTimer,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(sleepLabel, style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            // Player overflow: player-level actions (sleep timer) + song actions
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = strings.commonOptions, tint = Color.White)
                }
                item?.let {
                    PlayerOverflowMenu(
                        song = it.song,
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        sleepActive = sleepState.active,
                        onOpenSleepTimer = onOpenSleepTimer,
                        onOpenAudioFx = onOpenAudioFx,
                    )
                }
            }
        }

        // Ortak kontrol bloğu: dik düzende kapağın altına, yatayda sağ panele
        val controls: @Composable ColumnScope.() -> Unit = {
            // Title (bold) + chevron → album; artist muted below. Small icons right.
            val menuActions = com.ozgen.navicloud.ui.components.LocalSongMenu.current
            val albumId = item?.song?.albumId
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            enabled = albumId != null && menuActions != null,
                        ) { albumId?.let { menuActions?.goToAlbum?.invoke(it) } },
                    ) {
                        Text(
                            item?.song?.title ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (albumId != null) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = strings.commonGoToAlbum,
                                tint = Color(0x99FFFFFF),
                            )
                        }
                    }
                    Text(
                        item?.song?.artist ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Self-host gururu: kaynak dosyanın gerçek kalitesi.
                    // Streaming'ler gizler, biz gösteririz.
                    val suffix = item?.song?.suffix?.uppercase()
                    val kbps = item?.song?.bitRate
                    val hz = item?.song?.samplingRate
                    val quality by vm.player.streamQuality.collectAsStateWithLifecycle()
                    val badge = buildString {
                        suffix?.let { append(it) }
                        kbps?.let { if (isNotEmpty()) append(" • "); append("$it kbps") }
                        hz?.let { if (isNotEmpty()) append(" • "); append("%.1f kHz".format(it / 1000f)) }
                        if (quality.kbps != null && isNotEmpty()) append("  →  MP3 ${quality.kbps}")
                    }
                    if (badge.isNotEmpty()) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0x80FFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
                IconButton(onClick = { item?.song?.let { menuActions?.showInfo?.invoke(it) } }) {
                    Icon(Icons.Rounded.Info, contentDescription = strings.playerTrackInfo, tint = Color(0xB3FFFFFF))
                }
                IconButton(onClick = onOpenLyrics) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = strings.playerLyrics, tint = Color(0xB3FFFFFF))
                }
                // One-shot pop on favourite toggle — state change, not a loop
                val starScale = remember { Animatable(1f) }
                LaunchedEffect(starred) {
                    starScale.snapTo(0.7f)
                    starScale.animateTo(1f, spring(dampingRatio = 0.45f))
                }
                IconButton(onClick = { item?.song?.id?.let { vm.toggleStar(it) } }) {
                    Icon(
                        if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = strings.commonFavorite,
                        tint = if (starred) accent else Color(0xB3FFFFFF),
                        modifier = Modifier.graphicsLayer {
                            scaleX = starScale.value
                            scaleY = starScale.value
                        },
                    )
                }
            }

            val sliderValue = if (dragging) dragValue
            else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            WavySeekBar(
                value = sliderValue.coerceIn(0f, 1f),
                playing = playerState.isPlaying,
                accent = accent,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    val target = (dragValue * durationMs).toLong()
                    pendingSeek = target to System.currentTimeMillis()
                    positionMs = target // optimistic: bar bırakıldığı yerde kalır
                    vm.player.seekTo(target)
                    dragging = false
                },
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
                        contentDescription = strings.commonShuffle,
                        tint = if (playerState.shuffle) accent else Color(0xB3FFFFFF),
                        modifier = Modifier
                            .background(
                                if (playerState.shuffle) accent.copy(alpha = 0.16f) else Color.Transparent,
                                androidx.compose.foundation.shape.CircleShape,
                            )
                            .padding(6.dp),
                    )
                }
                IconButton(onClick = { vm.player.skipPrevious() }) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = strings.commonPrevious,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                // One-shot spring pop on play/pause state change
                val playScale = remember { Animatable(1f) }
                LaunchedEffect(playerState.isPlaying) {
                    playScale.snapTo(0.88f)
                    playScale.animateTo(1f, spring(dampingRatio = 0.5f))
                }
                FilledIconButton(
                    onClick = { vm.player.togglePlayPause() },
                    modifier = Modifier
                        .size(68.dp)
                        .graphicsLayer {
                            scaleX = playScale.value
                            scaleY = playScale.value
                        },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0F0F14),
                    ),
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playerState.isPlaying) strings.commonPause else strings.commonPlay,
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = { vm.player.skipNext() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = strings.commonNext,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    val repeatActive = playerState.repeat != com.ozgen.navicloud.playback.RepeatMode.OFF
                    Icon(
                        if (playerState.repeat == com.ozgen.navicloud.playback.RepeatMode.ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        contentDescription = strings.commonRepeat,
                        tint = if (repeatActive) accent else Color(0xB3FFFFFF),
                        modifier = Modifier
                            .background(
                                if (repeatActive) accent.copy(alpha = 0.16f) else Color.Transparent,
                                androidx.compose.foundation.shape.CircleShape,
                            )
                            .padding(6.dp),
                    )
                }
            }

            // Ses seviyesi — yalnız masaüstünde (mobilde donanım tuşları var).
            // Müzik çalar kalıbı: sağa yaslı, hep görünür kompakt slider;
            // ikona tıklamak sessize alır / geri açar (Spotify düzeni).
            val volumeCtl = com.ozgen.navicloud.ui.LocalVolumeController.current
            if (volumeCtl != null) {
                var vol by remember { mutableFloatStateOf(volumeCtl.volume) }
                var lastAudible by remember { mutableFloatStateOf(if (vol > 0.01f) vol else 0.7f) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = {
                            if (vol > 0.01f) {
                                lastAudible = vol
                                vol = 0f
                            } else {
                                vol = lastAudible
                            }
                            volumeCtl.volume = vol
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            when {
                                vol <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                                vol < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = if (vol <= 0.01f) strings.playerUnmute else strings.playerMute,
                            tint = Color(0x99FFFFFF),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    com.ozgen.navicloud.ui.components.ThinSlider(
                        value = vol,
                        onValueChange = {
                            vol = it
                            if (it > 0.01f) lastAudible = it
                            volumeCtl.volume = it
                        },
                        modifier = Modifier.width(120.dp),
                    )
                }
            }

        }

        if (sideBySide) {
            // Yatay: kapak solda (sheet'in morph görseli oraya iner),
            // kontroller sağda dikey ortalı
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.width(artSpace + 64.dp))
                Box(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 560.dp)
                            .padding(horizontal = 24.dp),
                    ) { controls() }
                }
            }
        } else {
            // Artwork lands here (drawn by the sheet as the morphing image)
            Spacer(Modifier.height(artSpace))
            // Geniş dik ekranda kontroller kapağın altında toplu kalsın
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp),
            ) { controls() }

            // Kenardan kenara ambient spektrum: ayrı öğe değil, zeminin dokusu —
            // progress'e senkron, track başına deterministik desen
            Spacer(Modifier.height(16.dp))
            com.ozgen.navicloud.ui.components.SpectrumBar(
                seedKey = item?.song?.id ?: "",
                progress = run {
                    val sv = if (dragging) dragValue
                    else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    sv.coerceIn(0f, 1f)
                },
                accent = accent,
            )
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
    accent: Color,
    bottomPad: androidx.compose.ui.unit.Dp,
    hintStartPad: androidx.compose.ui.unit.Dp = 0.dp,
    onShow: () -> Unit,
    onHide: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val state by player.state.collectAsStateWithLifecycle()
    val endless by player.endless.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Working copy the reorder library mutates INSTANTLY; Media3 catches up
    // asynchronously. Feeding the async state straight back caused runaway
    // moves (stale indices) and flicker.
    val localQueue = remember { androidx.compose.runtime.mutableStateListOf<com.ozgen.navicloud.playback.QueueTrack>() }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in localQueue.indices && to.index in localQueue.indices) {
            // Local list moves by index (visual order); Media3 mirror resolves
            // the item by UID so a lagging sync can't move the wrong song
            val uid = localQueue[from.index].uid
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

    // Swipe-down dismiss from ANYWHERE in the list: when the list is at its
    // very top, downward drags feed the panel's AnchoredDraggable; otherwise
    // they stay normal scrolls. Fling settles by threshold/velocity.
    val shownPx = remember(queueDrag) { queueDrag.anchors.positionOf(QueuePanelValue.Shown) }
    val nestedDismiss = remember(listState, queueDrag) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                val panelLowered = queueDrag.requireOffset() > shownPx + 0.5f
                return if ((available.y > 0f && atTop) || (available.y < 0f && panelLowered)) {
                    Offset(0f, queueDrag.dispatchRawDelta(available.y))
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (queueDrag.requireOffset() > shownPx + 0.5f) {
                    queueDrag.settle(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }
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
                    .clickable { if (queueProgress < 0.5f) onShow() else onHide() },
            ) {
                // Hidden state: drag pill + up-next hint
                val nextItem = state.queue.getOrNull(state.currentIndex + 1)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - queueProgress * 2f).coerceIn(0f, 1f) }
                        .padding(top = 8.dp, start = hintStartPad),
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
                        if (nextItem != null) strings.playerUpNext(nextItem.song.title) else strings.playerQueue,
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
                            currentItem?.song?.title ?: strings.playerQueue,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            currentItem?.song?.artist ?: strings.commonSongCount(state.queue.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0x99FFFFFF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { player.stop() }) {
                        Icon(Icons.Rounded.Stop, contentDescription = strings.playerStop, tint = Color(0x80FFFFFF))
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
                            contentDescription = if (state.isPlaying) strings.commonPause else strings.commonPlay,
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
                                strings.playerPlayingFrom,
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
                                strings.playerAutoplay,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                            )
                            Text(
                                strings.playerAutoplayDesc,
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
                    .graphicsLayer { alpha = queueProgress }
                    .nestedScroll(nestedDismiss),
                userScrollEnabled = queueProgress > 0.3f,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = bottomPad + 8.dp,
                ),
            ) {
                items(
                    localQueue.size,
                    key = { localQueue[it].uid },
                    contentType = { "song" },
                ) { i ->
                    val queueItem = localQueue[i]
                    val uid = queueItem.uid
                    ReorderableItem(
                        reorderableState,
                        key = uid,
                        modifier = Modifier.animateItem(),
                    ) { isDragging ->
                        val dragScope = this
                        // StartToEnd (sol→sağ) = sıradakine kopya ekle, EndToStart
                        // (sağ→sol) = kuyruktan kaldır. Frozen closure yalnızca
                        // değişmez UID'yi yakalar.
                        // confirmValueChange TEK sürüklemede birden çok kez tetiklenebilir
                        // (false dönüşü + sürükleme devamı yeni settle denemesi üretir) —
                        // kopya eklemeyi zaman eşiğiyle teke indir.
                        val lastPlayNextAt = remember(uid) { androidx.compose.runtime.mutableStateOf(0L) }
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance * 0.4f },
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        // Kopya, çalanın hemen arkasına; satır yerinde kalır
                                        val now = System.currentTimeMillis()
                                        if (now - lastPlayNextAt.value > 800) {
                                            lastPlayNextAt.value = now
                                            player.playNext(listOf(queueItem.song))
                                        }
                                        false
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        localQueue.removeAll { it.uid == uid }
                                        player.removeQueueItemByUid(uid)
                                        true
                                    }
                                    else -> false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                // Yöne göre renk + ikon
                                val direction = dismissState.dismissDirection
                                val bg: Color
                                val icon: androidx.compose.ui.graphics.vector.ImageVector
                                val tint: Color
                                val align: Alignment
                                when (direction) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                        icon = Icons.Rounded.PlaylistPlay
                                        tint = MaterialTheme.colorScheme.primary
                                        align = Alignment.CenterStart
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        bg = MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                                        icon = Icons.Rounded.RemoveCircleOutline
                                        tint = MaterialTheme.colorScheme.error
                                        align = Alignment.CenterEnd
                                    }
                                    else -> {
                                        bg = Color.Transparent
                                        icon = Icons.Rounded.PlaylistPlay
                                        tint = Color.Transparent
                                        align = Alignment.Center
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(bg)
                                        .padding(horizontal = 24.dp),
                                ) {
                                    // Icon grows with swipe progress — the threshold is felt
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier
                                            .align(align)
                                            .graphicsLayer {
                                                val s = 0.7f + 0.4f * dismissState.progress.coerceIn(0f, 1f)
                                                scaleX = s
                                                scaleY = s
                                            },
                                    )
                                }
                            },
                        ) {
                            SongItem(
                                song = queueItem.song,
                                onClick = { player.seekToUid(uid) },
                                highlighted = i == state.currentIndex,
                                inQueue = true,
                                queueUid = uid,
                                playingBars = if (i == state.currentIndex) state.isPlaying else null,
                                barsTint = accent,
                                modifier = Modifier
                                    .height(64.dp)
                                    .background(
                                        // Playing row separates with the content-derived accent
                                        if (i == state.currentIndex) {
                                            accent.copy(alpha = 0.14f)
                                                .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                    )
                                    .graphicsLayer { alpha = if (isDragging) 0.85f else 1f },
                                trailingContent = {
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = strings.playerDragReorder,
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

/** Player overflow: player seviyesi eylemler (uyku zamanlayıcı) + şarkı eylemleri. */
@Composable
private fun PlayerOverflowMenu(
    song: com.ozgen.navicloud.core.model.Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    sleepActive: Boolean,
    onOpenSleepTimer: () -> Unit,
    onOpenAudioFx: () -> Unit,
) {
    val strings = LocalStrings.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(strings.playerSoundEqualizer) },
            leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) },
            onClick = { onDismiss(); onOpenAudioFx() },
        )
        DropdownMenuItem(
            text = { Text(if (sleepActive) strings.playerSleepTimerOn else strings.playerSleepTimer) },
            leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
            onClick = { onDismiss(); onOpenSleepTimer() },
        )
        HorizontalDivider()
        SongContextMenuItems(song = song, onDismiss = onDismiss)
    }
}

@Composable
private fun SleepTimerSheet(player: PlayerController, onClose: () -> Unit) {
    val strings = LocalStrings.current
    val state by player.sleepTimer.collectAsStateWithLifecycle()
    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(Modifier.fillMaxWidth().padding(bottom = navBarPad + 16.dp)) {
        Text(
            strings.playerSleepTimer,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.active) {
            val (title, sub) = when (val p = state.preset) {
                is SleepTimerPreset.Duration ->
                    (state.remainingMs?.let { formatDuration((it / 1000).toInt()) } ?: strings.playerSleepMinutesShort(p.minutes)) to strings.playerSleepRemaining
                SleepTimerPreset.EndOfTrack -> strings.playerSleepEndOfTrackTitle to strings.playerSleepWillStop
                SleepTimerPreset.EndOfQueue -> strings.playerSleepEndOfQueueTitle to strings.playerSleepWillStop
                null -> "" to ""
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { player.cancelSleepTimer() }) { Text(strings.playerSleepCancel) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }
        SleepTimerPreset.DURATIONS.forEach { m ->
            SleepTimerRow(
                icon = Icons.Rounded.Timer,
                label = strings.playerSleepMinutes(m),
                selected = (state.preset as? SleepTimerPreset.Duration)?.minutes == m,
                onClick = { player.startSleepTimer(SleepTimerPreset.Duration(m)); onClose() },
            )
        }
        SleepTimerRow(
            icon = Icons.Rounded.MusicNote,
            label = strings.playerSleepEndOfTrackTitle,
            selected = state.preset == SleepTimerPreset.EndOfTrack,
            onClick = { player.startSleepTimer(SleepTimerPreset.EndOfTrack); onClose() },
        )
        SleepTimerRow(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = strings.playerSleepEndOfQueueTitle,
            selected = state.preset == SleepTimerPreset.EndOfQueue,
            onClick = { player.startSleepTimer(SleepTimerPreset.EndOfQueue); onClose() },
        )
    }
}

@Composable
private fun SleepTimerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LyricsSheet(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMsProvider: () -> Long,
    onSeekLine: (Long) -> Unit,
) {
    val strings = LocalStrings.current
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = positionMsProvider()
            delay(250)
        }
    }

    when {
        loading -> Box(
            Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center,
        ) { Text(strings.playerLyricsLoading) }
        lyrics == null || lyrics.lines.isEmpty() -> Box(
            Modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center,
        ) { Text(strings.playerLyricsEmpty, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> {
            val synced = lyrics.synced
            val activeIndex = if (synced) {
                lyrics.lines.indexOfLast { (it.startMs ?: 0) <= positionMs }.coerceAtLeast(0)
            } else -1
            val listState = rememberLazyListState()

            BoxWithConstraints(Modifier.fillMaxWidth().height(520.dp)) {
                // Aktif satırı dikey ORTAYA getir: gerçek layout ölçüsüyle (satır yükseklikleri
                // değişken olduğundan sabit offset yanlış oluyordu). Görünürse yumuşak kaydır,
                // değilse önce yaklaş sonra ortala.
                LaunchedEffect(activeIndex) {
                    if (activeIndex < 0) return@LaunchedEffect
                    suspend fun centerOn(index: Int, smooth: Boolean) {
                        val info = listState.layoutInfo
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
                        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        // Aktif satır tam ortada değil, ortadan ~2 satır YUKARIDA dursun →
                        // altında gelecek satırlar görünür (Apple/Spotify tarzı).
                        val bias = item.size * 2f
                        val delta = (item.offset + item.size / 2f) - (viewportCenter - bias)
                        if (smooth) listState.animateScrollBy(delta) else listState.scrollBy(delta)
                    }
                    if (listState.layoutInfo.visibleItemsInfo.none { it.index == activeIndex }) {
                        listState.scrollToItem(activeIndex)  // görünür değil → önce getir
                        centerOn(activeIndex, smooth = false)
                    } else {
                        centerOn(activeIndex, smooth = true)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 24.dp,
                        vertical = if (synced) maxHeight / 2 else 24.dp,
                    ),
                ) {
                    items(lyrics.lines.size) { i ->
                        val line = lyrics.lines[i]
                        val active = i == activeIndex
                        val dist = kotlin.math.abs(i - activeIndex)
                        // Aktiften uzaklaştıkça sol (üst/alt simetrik faded)
                        val targetAlpha = when {
                            !synced -> 1f
                            active -> 1f
                            dist == 1 -> 0.5f
                            dist == 2 -> 0.34f
                            dist == 3 -> 0.24f
                            else -> 0.16f
                        }
                        val alpha by animateFloatAsState(targetAlpha, tween(320), label = "lyricAlpha")
                        val scale by animateFloatAsState(if (active) 1f else 0.92f, tween(320), label = "lyricScale")
                        val seekable = synced && line.startMs != null
                        Text(
                            line.text.ifBlank { "♪" },
                            style = if (active) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.titleLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (seekable) Modifier.clickable { onSeekLine(line.startMs!!) }
                                    else Modifier
                                )
                                .graphicsLayer {
                                    this.alpha = alpha
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
