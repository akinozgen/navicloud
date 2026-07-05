package com.ozgen.navicloud.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import com.ozgen.navicloud.audio.AudioEffectsController
import com.ozgen.navicloud.audio.NoOpAudioEffectsController
import com.ozgen.navicloud.data.DownloadsPort
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.OfflineModeSource
import com.ozgen.navicloud.data.RecentSearchesStore
import com.ozgen.navicloud.data.ServerSource
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.QueueSyncManager

/**
 * Paylaşılan UI'ın bağımlılık kabı — Hilt yerine hafif kompozisyon yerel'i.
 * Android'de Hilt grafiğinden, masaüstünde elle kurulur.
 */
class AppContainer(
    val music: MusicRepository,
    val player: PlayerController,
    val servers: ServerSource,
    val downloads: DownloadsPort,
    val offline: OfflineModeSource,
    val recents: RecentSearchesStore,
    /** Ses/EQ efektleri. Desteklemeyen platform default NoOp bırakır. */
    val audioEffects: AudioEffectsController = NoOpAudioEffectsController(),
    /** Cihazlar arası kuyruk senkronu. Sağlanmayan platform null bırakır → sessiz devre dışı. */
    val queueSync: QueueSyncManager? = null,
    /** Uzaktan kumanda (cihaz seçici). null = özellik gizli. [player] bu durumda ActivePlayerController olmalı. */
    val remoteControl: com.ozgen.navicloud.remote.RemoteControlManager? = null,
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer sağlanmadı")
}

/** hiltViewModel yerine: container'dan kurulan, navigasyon girdisine scope'lu VM. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val c = LocalAppContainer.current
    return viewModel(key = key) { create(c) }
}

/**
 * Ses seviyesi denetimi: mobilde donanım tuşları var, GEREKSİZ (null);
 * masaüstünde Main mpv'ye bağlı implementasyon sağlar ve player'da
 * ses ikonu görünür.
 */
interface VolumeController {
    /** 0f..1f */
    var volume: Float
}

val LocalVolumeController = staticCompositionLocalOf<VolumeController?> { null }

/**
 * Mini oynatıcıyı açan eylem — yalnızca masaüstünde sağlanır (null ise
 * tam oynatıcıda mini oynatıcı ikonu gösterilmez).
 */
val LocalMiniPlayerToggle = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Dokunmatik platformda true — pull-to-refresh jestinin ön koşulu. */
expect val supportsPullToRefresh: Boolean

/** Android'de sistem geri tuşu; masaüstünde no-op. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** Kısa bildirim: Android'de Toast, masaüstünde şimdilik konsol. */
@Composable
expect fun rememberToaster(): (String) -> Unit

/**
 * Kapaktan baskın + accent renk çıkarımı (Android: Palette).
 * null → çağıran varsayılan renklerde kalır.
 */
expect suspend fun extractArtColors(artUrl: String, cacheKey: String?): Pair<Color, Color?>?
