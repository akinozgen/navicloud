package com.ozgen.navicloud.data

import com.ozgen.navicloud.core.model.Album
import com.ozgen.navicloud.core.model.Artist
import com.ozgen.navicloud.core.model.HomeSection
import com.ozgen.navicloud.core.model.HomeSectionType
import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.core.model.LyricsLine
import com.ozgen.navicloud.core.model.Playlist
import com.ozgen.navicloud.core.model.SearchResult
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.core.network.dto.AlbumDto
import com.ozgen.navicloud.core.network.dto.ArtistDto
import com.ozgen.navicloud.core.network.dto.PlaylistDto
import com.ozgen.navicloud.core.network.dto.SongDto
import com.ozgen.navicloud.core.network.SubsonicException
import com.ozgen.navicloud.core.network.unwrap
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private fun AlbumDto.toModel() = Album(
    id = id,
    name = name ?: album ?: title ?: "",
    artist = artist ?: "",
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    duration = duration,
    year = year,
    genre = genre,
    starred = starred != null,
)

private fun SongDto.toModel() = Song(
    id = id,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    duration = duration,
    track = track,
    discNumber = discNumber,
    year = year,
    bitRate = bitRate,
    suffix = suffix,
    contentType = contentType,
    size = size,
    starred = starred != null,
    samplingRate = samplingRate,
    channelCount = channelCount,
    bitDepth = bitDepth,
)

private fun ArtistDto.toModel() = Artist(
    id = id,
    name = name,
    coverArt = coverArt,
    artistImageUrl = artistImageUrl,
    albumCount = albumCount,
    starred = starred != null,
)

private fun PlaylistDto.toModel(currentUser: String?) = Playlist(
    id = id,
    name = name,
    comment = comment,
    songCount = songCount,
    duration = duration,
    coverArt = coverArt,
    owner = owner,
    isPublic = isPublic,
    readonly = readonly,
    changed = changed,
    // Senaryo A: readonly birincil sinyal; yoksa owner fallback; o da yoksa
    // varsayılan true (Senaryo B: ilk mutasyondaki error 50 oturum katmanında yakalanır)
    editable = when {
        readonly != null -> !readonly
        owner != null && currentUser != null -> owner.equals(currentUser, ignoreCase = true)
        else -> true
    },
)

@Serializable
data class AlbumDetail(val album: Album, val songs: List<Song>)
@Serializable
data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val biography: String?,
    val similar: List<Artist>,
    /** Real artist photo URL if the server has one; placeholders filtered out. */
    val imageUrl: String? = null,
)

// Last.fm's well-known star placeholder — Navidrome serves it when no agent is configured
private const val LASTFM_PLACEHOLDER_HASH = "2a96cbd8b46e442fc41c2b86b821562f"

/** Materyalize günlük mix'in playlist adı — MARKA SABİTİ, i18n DEĞİL (dil değişince ikinci liste doğmasın). */
const val NAVICLOUD_MIX_NAME = "NaviCloud Mix"

private fun String?.realImageUrlOrNull(): String? =
    this?.takeIf { it.isNotBlank() && !it.contains(LASTFM_PLACEHOLDER_HASH) }
@Serializable
data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

/** Sunucu error 50: liste bu kullanıcı için yazılamaz (smart liste / sahibi değil). */
class PlaylistReadOnlyException(val playlistId: String) : Exception("Playlist salt okunur: $playlistId")

/** Yerel görünüm sunucudan sapmış (index doğrulaması tutmadı) — force-refresh gerekir. */
class PlaylistStaleException(val playlistId: String) : Exception("Playlist bayat: $playlistId")

/** Sunucudan çekilen uzak kuyruk durumu (cihazlar arası devam için). */
data class RemotePlayQueue(
    val songs: List<Song>,
    val currentId: String?,
    val positionMs: Long,
    val changed: String?,
    val changedBy: String?,
)

// TTL'ler: entity ne kadar oynaksa o kadar kısa
private const val TTL_HOME = 30L * 60 * 1000
private const val TTL_LISTS = 6L * 60 * 60 * 1000
private const val TTL_STABLE = 24L * 60 * 60 * 1000
private const val TTL_SEARCH = 15L * 60 * 1000
private const val TTL_LYRICS = 7L * 24 * 60 * 60 * 1000

@Singleton
class MusicRepository @Inject constructor(
    private val servers: ServerSource,
    private val cache: ApiCacheStore,
    private val json: Json,
    private val offline: OfflineModeSource,
    okHttp: okhttp3.OkHttpClient,
    private val lyricsSettings: LyricsSettings,
) {
    private val lrcLib = com.ozgen.navicloud.core.network.LrcLibClient(okHttp, json)

    private suspend fun api() = servers.activeClient().api

    /** Oturum içi: error 50 yemiş playlist id'leri — UI kalıcı olarak salt-okunura düşer. */
    private val playlistReadOnlyIds = MutableStateFlow<Set<String>>(emptySet())

    // Reaktivite sinyalleri: cache invalidation VM'lerin bellek-içi listelerini
    // uyandırmaz — mutasyon sonrası sürüm artar, açık ekranlar dinleyip tazelenir.
    private val _playlistsVersion = MutableStateFlow(0)
    val playlistsVersion: StateFlow<Int> = _playlistsVersion

    private val _starredVersion = MutableStateFlow(0)
    val starredVersion: StateFlow<Int> = _starredVersion

    private suspend fun serverPrefix(): String =
        "${servers.activeServer.first()?.id ?: 0}:"

    /**
     * Offline-first okuma: TTL içinde cache'ten (ağa hiç çıkmadan), bayatsa
     * ağdan çekip cache'i tazeler; ağ hatasında bayat cache'e düşer. Offline
     * modda her zaman cache — yoksa açık hata. force=true (pull-to-refresh)
     * TTL'i atlar ama hata durumunda yine cache'e düşer.
     */
    private suspend inline fun <reified T> cached(
        key: String,
        ttlMs: Long,
        force: Boolean = false,
        fetch: () -> T,
    ): T {
        val fullKey = serverPrefix() + key
        val offline = offline.offlineMode.first()
        val entry = cache.get(fullKey)
        val decoded: T? = entry?.let { e ->
            runCatching { json.decodeFromString<T>(e.json) }.getOrNull()
        }
        val fresh = entry != null && System.currentTimeMillis() - entry.updatedAt < ttlMs
        if (decoded != null && (offline || (fresh && !force))) return decoded
        if (offline) throw IllegalStateException(com.ozgen.navicloud.i18n.I18n.strings.offlineContentUnavailable)
        return try {
            val value = fetch()
            cache.put(CachedEntry(fullKey, json.encodeToString(value), System.currentTimeMillis()))
            value
        } catch (e: Exception) {
            decoded ?: throw e
        }
    }

    /** Mutasyon sonrası ilgili cache anahtarlarını düşürür (prefix eşleşmesi). */
    private suspend fun invalidate(vararg keyPrefixes: String) {
        val prefix = serverPrefix()
        keyPrefixes.forEach { cache.deleteByPrefix(prefix + it) }
    }

    suspend fun homeSections(force: Boolean = false): List<HomeSection> =
        cached("home", TTL_HOME, force) {
            coroutineScope {
                HomeSectionType.entries.map { type ->
                    async {
                        runCatching {
                            val albums = api().getAlbumList2(type.subsonicType, size = 20)
                                .unwrap().albumList2?.album.orEmpty().map { it.toModel() }
                            HomeSection(type, albums)
                        }.getOrElse { HomeSection(type, emptyList()) }
                    }
                }.map { it.await() }.filter { it.albums.isNotEmpty() }
            }
        }

    suspend fun albumList(type: HomeSectionType, size: Int = 50, offset: Int = 0, force: Boolean = false): List<Album> =
        cached("albums:${type.name}:$size:$offset", TTL_LISTS, force) {
            api().getAlbumList2(type.subsonicType, size, offset)
                .unwrap().albumList2?.album.orEmpty().map { it.toModel() }
        }

    suspend fun albumsAlphabetical(size: Int = 100, offset: Int = 0, force: Boolean = false): List<Album> =
        cached("albums:alpha:$size:$offset", TTL_STABLE, force) {
            api().getAlbumList2("alphabeticalByName", size, offset)
                .unwrap().albumList2?.album.orEmpty().map { it.toModel() }
        }

    suspend fun album(id: String, force: Boolean = false): AlbumDetail =
        cached("album:$id", TTL_STABLE, force) {
            val dto = api().getAlbum(id).unwrap().album
                ?: throw IllegalStateException("Albüm bulunamadı")
            AlbumDetail(dto.toModel(), dto.song.map { it.toModel() })
        }

    suspend fun artists(force: Boolean = false): List<Artist> =
        cached("artists", TTL_STABLE, force) {
            api().getArtists().unwrap().artists?.index.orEmpty()
                .flatMap { it.artist }.map { it.toModel() }
        }

    suspend fun artist(id: String, force: Boolean = false): ArtistDetail =
        cached("artist:$id", TTL_STABLE, force) {
            coroutineScope {
                val artistDeferred = async { api().getArtist(id).unwrap().artist }
                val infoDeferred = async {
                    runCatching { api().getArtistInfo2(id).unwrap().artistInfo2 }.getOrNull()
                }
                val dto = artistDeferred.await() ?: throw IllegalStateException("Sanatçı bulunamadı")
                val info = infoDeferred.await()
                ArtistDetail(
                    artist = dto.toModel(),
                    albums = dto.album.map { it.toModel() },
                    biography = info?.biography?.takeIf { it.isNotBlank() },
                    similar = info?.similarArtist.orEmpty().map { it.toModel() },
                    imageUrl = info?.largeImageUrl.realImageUrlOrNull()
                        ?: dto.artistImageUrl.realImageUrlOrNull(),
                )
            }
        }

    suspend fun topSongs(artistName: String, force: Boolean = false): List<Song> =
        runCatching {
            cached("topsongs:$artistName", TTL_STABLE, force) {
                api().getTopSongs(artistName).unwrap().topSongs?.song.orEmpty().map { it.toModel() }
            }
        }.getOrElse { emptyList() }

    private suspend fun currentUser(): String? = servers.activeServer.first()?.username

    /** Oturum içi error-50 hafızası cache SONRASI uygulanır (cache'te bayat editable kalmasın). */
    private fun Playlist.withSessionReadOnly(): Playlist =
        if (editable && id in playlistReadOnlyIds.value) copy(editable = false) else this

    suspend fun playlists(force: Boolean = false): List<Playlist> {
        val user = currentUser()
        return cached("playlists", TTL_LISTS, force) {
            api().getPlaylists().unwrap().playlists?.playlist.orEmpty().map { it.toModel(user) }
        }.map { it.withSessionReadOnly() }
    }

    suspend fun playlist(id: String, force: Boolean = false): PlaylistDetail {
        val user = currentUser()
        return cached("playlist:$id", TTL_LISTS, force) {
            val dto = api().getPlaylist(id).unwrap().playlist
                ?: throw IllegalStateException("Çalma listesi bulunamadı")
            PlaylistDetail(dto.toModel(user), dto.entry.map { it.toModel() })
        }.let { it.copy(playlist = it.playlist.withSessionReadOnly()) }
    }

    suspend fun search(query: String): SearchResult =
        cached("search:$query", TTL_SEARCH) {
            val r = api().search3(query).unwrap().searchResult3
            SearchResult(
                artists = r?.artist.orEmpty().map { it.toModel() }.distinctBy { it.id },
                albums = r?.album.orEmpty().map { it.toModel() }.distinctBy { it.id },
                songs = r?.song.orEmpty().map { it.toModel() }.distinctBy { it.id },
            )
        }

    suspend fun starred(force: Boolean = false): SearchResult =
        cached("starred", TTL_SEARCH, force) {
            val r = api().getStarred2().unwrap().starred2
            SearchResult(
                artists = r?.artist.orEmpty().map { it.toModel() }.distinctBy { it.id },
                albums = r?.album.orEmpty().map { it.toModel() }.distinctBy { it.id },
                songs = r?.song.orEmpty().map { it.toModel() }.distinctBy { it.id },
            )
        }

    /** Full library song list, paged. Navidrome returns everything for an empty query. */
    suspend fun allSongs(offset: Int, size: Int = 200, force: Boolean = false): List<Song> =
        cached("allsongs:$offset:$size", TTL_STABLE, force) {
            api().search3("", artistCount = 0, albumCount = 0, songCount = size, songOffset = offset)
                .unwrap().searchResult3?.song.orEmpty().map { it.toModel() }
        }

    suspend fun randomSongs(size: Int = 50): List<Song> =
        api().getRandomSongs(size).unwrap().randomSongs?.song.orEmpty().map { it.toModel() }

    suspend fun setStarred(starred: Boolean, songId: String? = null, albumId: String? = null, artistId: String? = null) {
        if (starred) api().star(songId, albumId, artistId).unwrap()
        else api().unstar(songId, albumId, artistId).unwrap()
        // Favori değişikliği görünen listeleri etkiler — ilgili cache'ler düşer
        invalidate("starred", "home", "foryou:rediscover")
        albumId?.let { invalidate("album:$it") }
        artistId?.let { invalidate("artist:$it") }
        _starredVersion.update { it + 1 }
    }

    suspend fun scrobble(songId: String, submission: Boolean) {
        runCatching { api().scrobble(songId, submission).unwrap() }
    }

    // --- Kuyruk senkronu (cihazlar arası) ---
    // Tüm çağrılar sessiz fail: ağ/uç hatası çökmeye ya da görünür hataya yol açmaz.

    suspend fun savePlayQueue(ids: List<String>, current: String?, positionMs: Long) {
        runCatching { api().savePlayQueue(ids, current, positionMs).unwrap() }
    }

    /** Sunucudaki kuyruk yoksa/boşsa/hata varsa null. */
    suspend fun getPlayQueue(): RemotePlayQueue? {
        val r = runCatching { api().getPlayQueue().unwrap() }.getOrNull() ?: return null
        val pq = r.playQueue ?: return null
        if (pq.entry.isEmpty()) return null
        return RemotePlayQueue(
            songs = pq.entry.map { it.toModel() },
            currentId = pq.current,
            positionMs = pq.position ?: 0L,
            changed = pq.changed,
            changedBy = pq.changedBy,
        )
    }

    // --- Playlist mutasyonları ---
    // Ortak sarmalayıcı: error 50 (yetki/salt-okunur) → oturum içi hafıza + tipli hata;
    // başarıda ilgili cache anahtarları düşer.

    private suspend fun <T> mutatePlaylist(id: String, block: suspend () -> T): T {
        val result = try {
            block()
        } catch (e: SubsonicException) {
            if (e.code == 50) {
                playlistReadOnlyIds.update { it + id }
                throw PlaylistReadOnlyException(id)
            }
            throw e
        }
        invalidate("playlist:$id", "playlists")
        _playlistsVersion.update { it + 1 }
        return result
    }

    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) {
        if (songIds.isEmpty()) return
        mutatePlaylist(playlistId) {
            api().updatePlaylist(playlistId, songIdsToAdd = songIds).unwrap()
        }
    }

    suspend fun renamePlaylist(id: String, name: String) {
        mutatePlaylist(id) { api().updatePlaylist(id, name = name.trim()).unwrap() }
    }

    /**
     * Index tabanlı çıkarma (duplicate güvenli). Sunucudaki GÜNCEL sıraya karşı korunmak
     * için önce cache'siz taze getPlaylist ile [expectedSongId] doğrulanır; tutmuyorsa
     * [PlaylistStaleException] — çağıran force-refresh yapmalı.
     */
    suspend fun removeFromPlaylist(id: String, index: Int, expectedSongId: String) {
        mutatePlaylist(id) {
            val fresh = api().getPlaylist(id).unwrap().playlist
                ?: throw PlaylistStaleException(id)
            if (fresh.entry.getOrNull(index)?.id != expectedSongId) throw PlaylistStaleException(id)
            api().updatePlaylist(id, songIndexesToRemove = listOf(index)).unwrap()
        }
    }

    /** Reorder = tam değiştirme. BOŞ liste bazı Navidrome sürümlerinde listeyi siler — guard ZORUNLU. */
    suspend fun reorderPlaylist(id: String, songIds: List<String>) {
        require(songIds.isNotEmpty()) { "Boş reorder listesi — sunucuda wipe riski" }
        mutatePlaylist(id) { api().replacePlaylist(id, songIds).unwrap() }
    }

    suspend fun deletePlaylist(id: String) {
        mutatePlaylist(id) { api().deletePlaylist(id).unwrap() }
    }

    /** Yeni çalma listesi oluşturur (opsiyonel başlangıç şarkılarıyla); oluşan listeyi döner. */
    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()): Playlist? {
        val r = api().createPlaylist(name, songIds.ifEmpty { null }).unwrap()
        invalidate("playlists")
        _playlistsVersion.update { it + 1 }
        return r.playlist?.toModel(currentUser())
    }

    /**
     * Playlist için "Öneriler" (YTM tarzı): seed sanatçılardan benzer şarkı toplar.
     * Katman 1 = getSimilarSongs2 (Last.fm agent gerektirir); seed başına boş dönerse
     * Katman 2 = search3 ile aynı sanatçının listede olmayan parçaları. Tümü sessiz
     * fail — hiçbir aday yoksa boş liste döner, UI bölümü hiç göstermez.
     */
    suspend fun playlistSuggestions(
        seedArtists: List<Pair<String, String?>>, // (artistId, artistName)
        excludeIds: Set<String>,
        limit: Int = 10,
    ): List<Song> {
        val candidates = LinkedHashMap<String, Song>()
        for ((artistId, artistName) in seedArtists) {
            val similar = similarSongs(artistId, 15)
            val pool = similar.ifEmpty {
                // Fallback: sanatçının kütüphanedeki diğer parçaları
                artistName?.let { name ->
                    runCatching {
                        api().search3(name, artistCount = 0, albumCount = 0, songCount = 20)
                            .unwrap().searchResult3?.song.orEmpty()
                            .map { it.toModel() }
                            .filter { it.artistId == artistId }
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }
            pool.forEach { s -> if (s.id !in excludeIds) candidates.putIfAbsent(s.id, s) }
        }
        return candidates.values.shuffled().take(limit)
    }

    // --- For You ana sayfa rafları (docs/home/PLAN.md) ---
    // Kompozisyon burada, saf harman ForYouMixer'da. Hepsi sessiz fail → boş raf gizlenir.

    /** Seed sanatçılar: frequent %60 + starred %40, ≤8, dedup. (artistId, ad) çiftleri. */
    private suspend fun forYouSeeds(): List<Pair<String, String?>> {
        val frequent = runCatching { albumList(HomeSectionType.FREQUENT, size = 30) }
            .getOrDefault(emptyList())
        val starredAll = runCatching { starred() }.getOrNull()
        val freqArtists = frequent.mapNotNull { a -> a.artistId?.let { it to a.artist } }
            .distinctBy { it.first }
        val starArtists = buildList {
            starredAll?.artists?.forEach { add(it.id to it.name) }
            starredAll?.songs?.forEach { s -> s.artistId?.let { add(it to s.artist) } }
        }.distinctBy { it.first }
        val seeds = LinkedHashMap<String, String?>()
        freqArtists.take(5).forEach { seeds.putIfAbsent(it.first, it.second) }
        starArtists.shuffled().forEach { if (seeds.size < 8) seeds.putIfAbsent(it.first, it.second) }
        freqArtists.drop(5).forEach { if (seeds.size < 8) seeds.putIfAbsent(it.first, it.second) }
        return seeds.map { it.key to it.value }
    }

    /**
     * Günün Mix'i (50). Tazelik TTL değil TAKVİM GÜNÜ: cache'teki DailyMix.day bugünden
     * farklıysa zorla yeniden üretilir — materyalize kadansıyla aynı karar, tek yerden.
     */
    suspend fun naviCloudMix(force: Boolean = false): List<Song> {
        val build: suspend () -> DailyMix = {
            val seeds = forYouSeeds()
            val pools = coroutineScope {
                seeds.map { (id, _) -> async { similarSongs(id, 25) } }.map { it.await() }
            }
            DailyMix(ForYouMixer.todayStamp(), ForYouMixer.blend(pools, perArtistCap = 3, target = 50))
        }
        val cachedMix = runCatching { cached("foryou:mix", TTL_STABLE * 2, force) { build() } }
            .getOrElse { return emptyList() }
        if (cachedMix.day == ForYouMixer.todayStamp()) return cachedMix.songs
        // Gün değişti: zorla tazele; ağ yoksa cached() bayata düşer → eski mix (sessiz)
        return runCatching { cached("foryou:mix", TTL_STABLE * 2, force = true) { build() }.songs }
            .getOrDefault(cachedMix.songs)
    }

    /** "Sevdiklerine benzer": seed'lerin benzer sanatçıları (Navidrome zaten kütüphaneyle kesişik döner). */
    suspend fun similarArtistShelf(force: Boolean = false): List<Artist> =
        runCatching {
            cached("foryou:similar", TTL_STABLE, force) {
                val seeds = forYouSeeds()
                val seedIds = seeds.map { it.first }.toSet()
                coroutineScope {
                    seeds.map { (id, _) ->
                        async {
                            runCatching {
                                api().getArtistInfo2(id).unwrap().artistInfo2?.similarArtist.orEmpty()
                                    .map { it.toModel() }
                            }.getOrDefault(emptyList())
                        }
                    }.flatMap { it.await() }
                }.distinctBy { it.id }.filter { it.id !in seedIds }.take(12)
            }
        }.getOrDefault(emptyList())

    /** Radyo kartları: en sık dinlenen sanatçılar (Last.fm gerektirmez; görsel = baskın albüm kapağı). */
    suspend fun radioArtists(force: Boolean = false): List<Artist> =
        runCatching {
            cached("foryou:radio", TTL_STABLE, force) {
                albumList(HomeSectionType.FREQUENT, size = 30)
                    .mapNotNull { a -> a.artistId?.let { Triple(it, a.artist, a.coverArt) } }
                    .distinctBy { it.first }
                    .take(8)
                    .map { (id, name, cover) ->
                        Artist(id = id, name = name, coverArt = cover, artistImageUrl = null, albumCount = 0, starred = false)
                    }
            }
        }.getOrDefault(emptyList())

    /** "Yeniden keşfet": favori albümlerden son çalınanlarda OLMAYANLAR. */
    suspend fun rediscoverAlbums(force: Boolean = false): List<Album> =
        runCatching {
            cached("foryou:rediscover", TTL_LISTS, force) {
                val recentIds = albumList(HomeSectionType.RECENT, size = 20).map { it.id }.toSet()
                starred().albums.filter { it.id !in recentIds }.take(20)
            }
        }.getOrDefault(emptyList())

    /** Sanatçı radyosu kuyruğu: topSongs önde, benzerlerle dokunmuş ~20 parça. Cache'siz (radyo taze olsun). */
    suspend fun artistRadio(artistId: String, artistName: String): List<Song> {
        val top = runCatching { topSongs(artistName) }.getOrDefault(emptyList())
        val similar = similarSongs(artistId, 25)
        return ForYouMixer.interleave(top.take(8), similar, limit = 20)
    }

    /**
     * Günlük materyalize: "NaviCloud Mix" playlist'ini bul-ya-da-oluştur, günde ≤1 tazele.
     * İki katman guard: lokal gün damgası (fast-path, ağa çıkmaz) + sunucu `changed`
     * tarihi (otoriter — ikinci cihaz aynı gün SKIP). Boş mix'te ASLA yazmaz (wipe koruması).
     */
    suspend fun materializeNaviCloudMix(): MixResult {
        return runCatching {
            if (offline.offlineMode.first()) return MixResult.SKIPPED_OFFLINE
            val today = ForYouMixer.todayStamp()
            val stateKey = serverPrefix() + "foryou:mixstate"
            val prev = cache.get(stateKey)
                ?.let { runCatching { json.decodeFromString<MixState>(it.json) }.getOrNull() }
            if (prev?.attemptDay == today) return MixResult.SKIPPED_TODAY

            val user = currentUser()
            val lists = api().getPlaylists().unwrap().playlists?.playlist.orEmpty()
            val existing = lists.firstOrNull {
                it.name == NAVICLOUD_MIX_NAME &&
                    (it.owner == null || user == null || it.owner.equals(user, ignoreCase = true))
            }
            // Sunucu otoriter: bugün başka cihaz yazdıysa dokunma (changed ISO — gün önekiyle karşılaştır)
            if (existing?.changed?.take(10) == today) {
                saveMixState(stateKey, MixState(existing.id, today))
                return MixResult.SKIPPED_TODAY
            }

            val mix = naviCloudMix()
            if (mix.isEmpty()) return MixResult.SKIPPED_EMPTY

            val id = existing?.id
                ?: api().createPlaylist(NAVICLOUD_MIX_NAME, null).unwrap().playlist?.id
                ?: api().getPlaylists().unwrap().playlists?.playlist.orEmpty()
                    .firstOrNull { it.name == NAVICLOUD_MIX_NAME }?.id
                ?: return MixResult.FAILED
            api().replacePlaylist(id, mix.map { it.id }).unwrap()
            // Günlük ezilme davranışını kullanıcıya playlist üzerinde de anlat
            runCatching {
                api().updatePlaylist(id, comment = com.ozgen.navicloud.i18n.I18n.strings.homeMixPlaylistComment)
            }
            invalidate("playlists", "playlist:$id")
            _playlistsVersion.update { it + 1 }
            saveMixState(stateKey, MixState(id, today))
            MixResult.WRITTEN
        }.getOrElse { MixResult.FAILED }
    }

    /** Mix hero rozetinin navigasyonu için: materyalize edilmiş playlist'in id'si (yoksa null). */
    suspend fun naviCloudMixPlaylistId(): String? =
        cache.get(serverPrefix() + "foryou:mixstate")
            ?.let { runCatching { json.decodeFromString<MixState>(it.json) }.getOrNull() }
            ?.playlistId

    private suspend fun saveMixState(key: String, state: MixState) {
        runCatching {
            cache.put(CachedEntry(key, json.encodeToString(MixState.serializer(), state), System.currentTimeMillis()))
        }
    }

    suspend fun similarSongs(artistId: String, count: Int = 25): List<Song> =
        runCatching {
            api().getSimilarSongs2(artistId, count).unwrap().similarSongs2?.song.orEmpty().map { it.toModel() }
        }.getOrElse { emptyList() }

    /**
     * Söz: önce sunucu (getLyricsBySongId = gömülü + Navidrome .lrc sidecar), yoksa internet
     * fallback (LRCLIB, ayar açıksa + online + sanatçı/başlık varsa). Yalnız POZİTİF sonuç
     * cache'lenir → ayar sonradan açılınca yeniden denenir. Sessiz fail (hata → null).
     */
    suspend fun lyrics(
        songId: String,
        artist: String? = null,
        title: String? = null,
        album: String? = null,
        durationSec: Int? = null,
    ): Lyrics? {
        val key = serverPrefix() + "lyrics:$songId"
        // Pozitif cache
        cache.get(key)?.let { entry ->
            if (System.currentTimeMillis() - entry.updatedAt < TTL_LYRICS) {
                return runCatching { json.decodeFromString(Lyrics.serializer(), entry.json) }.getOrNull()
            }
        }
        // 1) Sunucu
        val server = runCatching {
            api().getLyricsBySongId(songId).unwrap().lyricsList?.structuredLyrics
        }.getOrNull().orEmpty()
        val best = server.firstOrNull { it.synced } ?: server.firstOrNull()
        var result = best
            ?.let { Lyrics(synced = it.synced, lines = it.line.map { l -> LyricsLine(l.start, l.value) }) }
            ?.takeIf { it.lines.isNotEmpty() }
        // 2) İnternet fallback (LRCLIB)
        if (result == null &&
            !offline.offlineMode.first() &&
            lyricsSettings.internetLyricsEnabled.first() &&
            !artist.isNullOrBlank() && !title.isNullOrBlank()
        ) {
            result = runCatching { lrcLib.fetch(artist, title, album, durationSec) }.getOrNull()
        }
        if (result != null) {
            runCatching {
                cache.put(CachedEntry(key, json.encodeToString(Lyrics.serializer(), result), System.currentTimeMillis()))
            }
        }
        return result
    }

    suspend fun coverArtUrl(coverArtId: String?, size: Int? = null): String? =
        coverArtId?.let { servers.activeClient().coverArtUrl(it, size) }

    suspend fun streamUrl(songId: String, maxKbps: Int? = null, format: String? = null): String =
        servers.activeClient().streamUrl(songId, maxKbps, format)

    suspend fun startScan(fullScan: Boolean): Pair<Boolean, Long> {
        val s = api().startScan(fullScan).unwrap().scanStatus
        return (s?.scanning ?: true) to (s?.count ?: 0)
    }

    suspend fun scanStatus(): Pair<Boolean, Long> {
        val s = api().getScanStatus().unwrap().scanStatus
        return (s?.scanning ?: false) to (s?.count ?: 0)
    }
}
