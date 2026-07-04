package com.ozgen.navicloud.playback

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.OfflineModeSource
import com.ozgen.navicloud.data.ServerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/** Kullanıcıya sunulan "başka cihazda kaldın" teklifi. */
data class ResumeOffer(
    val songs: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
    val changedBy: String?,
    val changed: String?,
    val signature: String,
)

/**
 * Cihazlar arası kuyruk senkronu. Yalnızca [PlayerController] public arayüzünü, repository'yi
 * ve platform portlarını kullanır — controller iç koduna dokunmaz.
 *
 * Push: track/kuyruk değişimi + pause + periyodik + zorunlu flush (arka plan/kapanış).
 * Pull/çakışma: açılışta imza tabanlı karar → farklıysa [resumeOffer] doldurulur (prompt).
 * Tüm ağ çağrıları sessiz fail — hata çökmeye/görünür hataya yol açmaz.
 */
class QueueSyncManager(
    private val repo: MusicRepository,
    private val servers: ServerSource,
    private val offline: OfflineModeSource,
    private val store: QueueSyncStateStore,
    private val json: Json,
    private val scope: CoroutineScope,
    private val player: PlayerController,
    // Player'a dokunan çağrıların (positionMs/durationMs/seekTo) koşacağı dispatcher.
    // Android: Dispatchers.Main ZORUNLU (MediaController yalnız ana thread). Masaüstü: mpv
    // erişimi thread-güvenli olduğundan Dispatchers.Default yeterli.
    private val playerDispatcher: CoroutineContext,
) {
    private val _resumeOffer = MutableStateFlow<ResumeOffer?>(null)
    val resumeOffer: StateFlow<ResumeOffer?> = _resumeOffer.asStateFlow()

    private var syncState = QueueSyncState()
    private val mutex = Mutex()
    private var lastPushMs = 0L
    private var started = false

    // Push, ilk resume-kontrolü bitene kadar KAPALI. Aksi halde açılışta cihazın kendi yerel
    // kuyruğu, checkForResume sunucuyu okumadan push'lanıp diğer cihazın kuyruğunu ezer
    // (yarış). Kontrol bitince (ya da teklif kararı verilince) açılır.
    @Volatile
    private var pushEnabled = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            syncState = loadState()

            // 1) Oynatıcı durumunu izle: track değişimi (throttle'lı) + pause (zorunlu flush).
            launch {
                var prevTrackId: String? = null
                var prevPlaying = false
                player.state.collect { st ->
                    val trackId = st.currentTrack?.song?.id
                    if (trackId != null && trackId != prevTrackId) maybePush(force = false)
                    if (prevPlaying && !st.isPlaying && trackId != null) maybePush(force = true)
                    prevTrackId = trackId
                    prevPlaying = st.isPlaying
                }
            }

            // 2) Periyodik push — pozisyon/kuyruk düzenlemelerini yansıtır.
            launch {
                while (isActive) {
                    delay(PERIODIC_MS)
                    if (player.state.value.isPlaying) maybePush(force = false)
                }
            }

            // 3) Aktif sunucu değişince: push'u kapat → sunucuyu oku (teklif) → aç.
            servers.activeServer.map { it?.id }.distinctUntilChanged().collect {
                lastPushMs = 0L
                runResumeCheck()
            }
        }
    }

    /** checkForResume'u push'u kapatıp/açarak sarar → pull, push'tan önce olur. */
    private suspend fun runResumeCheck() {
        pushEnabled = false
        try {
            checkForResume()
        } finally {
            pushEnabled = true
        }
    }

    // --- Push ---

    private suspend fun maybePush(force: Boolean) = mutex.withLock {
        runCatching {
            // İlk resume-kontrolü bitmeden VEYA teklif ekrandayken push YOK — diğer cihazın
            // kuyruğunu ezmeyelim (yarış / teklif edilen durumu clobber etme).
            if (!pushEnabled || _resumeOffer.value != null) return@withLock
            if (offline.offlineMode.first()) return@withLock
            val st = player.state.value
            val currentId = st.currentTrack?.song?.id ?: return@withLock
            if (st.queue.isEmpty()) return@withLock
            val now = System.currentTimeMillis()
            if (!force && now - lastPushMs < MIN_PUSH_MS) return@withLock

            // Tüm kuyruğu gönder (öndeki parçalar dâhil). Yalnız çok büyük kuyrukta
            // current çevresinde bir pencere al (bir miktar geriye + ileriye).
            val allIds = st.queue.map { it.song.id }
            val idx = st.currentIndex.coerceIn(0, (st.queue.size - 1).coerceAtLeast(0))
            val ids = if (allIds.size <= MAX_SYNC_TRACKS) {
                allIds
            } else {
                val start = (idx - LOOKBACK).coerceAtLeast(0)
                allIds.drop(start).take(MAX_SYNC_TRACKS)
            }
            if (ids.isEmpty()) return@withLock

            // positionMs ana-thread-güvenli okunmalı (Android MediaController)
            val pos = withContext(playerDispatcher) { player.positionMs }
            repo.savePlayQueue(ids, currentId, pos)
            lastPushMs = now
            val sig = queueSignature(currentId, ids)
            saveState(syncState.copy(serverId = activeServerId(), lastSyncedSignature = sig))
        }
    }

    /** Arka plan/pause — throttle bypass, tek push. */
    suspend fun flush() = maybePush(force = true)

    /** Kapanış (masaüstü) — bloklar ama timeout'lu; asla asılmaz. */
    fun flushBlocking(timeoutMs: Long) {
        runCatching { runBlocking { withTimeoutOrNull(timeoutMs) { flush() } } }
    }

    // --- Pull / çakışma ---

    /** Sunucudaki kuyruk yerelden farklıysa devam teklifi üretir. Hata = sessiz no-op. */
    suspend fun checkForResume() {
        runCatching {
            if (offline.offlineMode.first()) return
            val remote = repo.getPlayQueue() ?: return
            val ids = remote.songs.map { it.id }
            val remoteSig = queueSignature(remote.currentId, ids)
            val lastSynced = currentLastSynced()
            if (!shouldOfferResume(remoteSig, lastSynced)) return
            val idx = ids.indexOf(remote.currentId).let { if (it < 0) 0 else it }
            _resumeOffer.value = ResumeOffer(
                songs = remote.songs,
                currentIndex = idx,
                positionMs = remote.positionMs,
                changedBy = remote.changedBy,
                changed = remote.changed,
                signature = remoteSig,
            )
        }
    }

    fun applyResume(offer: ResumeOffer) {
        _resumeOffer.value = null
        // Başlangıç konumu doğrudan play()'e verilir (Media3 setMediaItems startPosition /
        // mpv yükleme sonrası seek) → prepare/play sonrası sıfırlanmaz, yarış yok.
        // player'a dokunur → playerDispatcher'da (Android: Main zorunlu).
        scope.launch(playerDispatcher) {
            player.play(offer.songs, startIndex = offer.currentIndex, startPositionMs = offer.positionMs)
        }
        scope.launch {
            mutex.withLock {
                lastPushMs = System.currentTimeMillis()
                saveState(syncState.copy(serverId = activeServerId(), lastSyncedSignature = offer.signature))
            }
        }
    }

    /** Ateşle-unut devam yoklaması (kendi scope'unda) — push'u kontrol süresince kapatır. */
    fun requestResumeCheck() {
        scope.launch { runResumeCheck() }
    }

    fun dismissResume() {
        val o = _resumeOffer.value ?: return
        _resumeOffer.value = null
        scope.launch {
            mutex.withLock {
                saveState(syncState.copy(serverId = activeServerId(), lastSyncedSignature = o.signature))
            }
        }
    }

    // --- Yardımcılar ---

    private suspend fun activeServerId(): String? =
        servers.activeServer.first()?.id?.toString()

    /** lastSynced yalnız aktif sunucuya aitse geçerli; başka sunucuysa null (temiz başla). */
    private suspend fun currentLastSynced(): String? =
        if (syncState.serverId == activeServerId()) syncState.lastSyncedSignature else null

    private suspend fun loadState(): QueueSyncState =
        store.load()?.let {
            runCatching { json.decodeFromString(QueueSyncState.serializer(), it) }.getOrNull()
        } ?: QueueSyncState()

    private suspend fun saveState(s: QueueSyncState) {
        syncState = s
        runCatching { store.save(json.encodeToString(QueueSyncState.serializer(), s)) }
    }

    companion object {
        private const val MIN_PUSH_MS = 10_000L
        private const val PERIODIC_MS = 30_000L
        private const val MAX_SYNC_TRACKS = 500
        private const val LOOKBACK = 100          // büyük kuyrukta current'tan geriye korunan parça

        /** İmza yalnız current + kuyruk kimliklerinden — pozisyon dâhil değil (nag önler). */
        fun queueSignature(currentId: String?, ids: List<String>): String =
            (currentId.orEmpty() + "#" + ids.joinToString(",")).hashCode().toString()

        fun shouldOfferResume(remoteSig: String?, lastSynced: String?): Boolean =
            remoteSig != null && remoteSig != lastSynced
    }
}
