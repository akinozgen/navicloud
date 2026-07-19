package com.ozgen.navicloud.data

import com.ozgen.navicloud.core.model.Song
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * For You raflarının saf harman mantığı — ağsız, platform bağımsız, test edilebilir.
 * IO/cache MusicRepository'de kalır; burada yalnız liste aritmetiği yaşar.
 */
object ForYouMixer {

    /** Takvim günü damgası (lokal saat, "2026-07-21"). Mix tazeliği TTL değil GÜN ile ölçülür. */
    fun todayStamp(): String = LocalDate.now().toString()

    /**
     * Seed havuzlarını round-robin dokuyarak tek mix üretir:
     * - id dedup (aynı şarkı iki seed'den gelebilir)
     * - sanatçı başına en fazla [perArtistCap] (çeşitlilik — tek sanatçı mix'i domine etmesin)
     * - [target]'a ulaşınca durur; havuzlar erken biterse eldekiyle döner
     */
    fun blend(pools: List<List<Song>>, perArtistCap: Int = 3, target: Int = 50): List<Song> {
        val out = ArrayList<Song>(target)
        val seenIds = HashSet<String>()
        val perArtist = HashMap<String, Int>()
        val iters = pools.filter { it.isNotEmpty() }.map { it.iterator() }
        if (iters.isEmpty()) return emptyList()
        var exhausted = 0
        val active = iters.toMutableList()
        while (out.size < target && active.isNotEmpty()) {
            val it = active[exhausted % active.size]
            if (!it.hasNext()) {
                active.remove(it)
                continue
            }
            val s = it.next()
            exhausted++
            if (!seenIds.add(s.id)) continue
            val artistKey = s.artistId ?: s.artist ?: s.id
            val count = perArtist.getOrDefault(artistKey, 0)
            if (count >= perArtistCap) continue
            perArtist[artistKey] = count + 1
            out.add(s)
        }
        return out
    }

    /**
     * Radyo kuyruğu: iki listeyi (topSongs ⊕ similar) sırayla dokur, id dedup, [limit]'te keser.
     * Top şarkılar önde başlar — radyo tanıdıkla açılsın, benzerlerle genişlesin.
     */
    fun interleave(first: List<Song>, second: List<Song>, limit: Int = 20): List<Song> {
        val out = ArrayList<Song>(limit)
        val seen = HashSet<String>()
        val a = first.iterator()
        val b = second.iterator()
        while (out.size < limit && (a.hasNext() || b.hasNext())) {
            if (a.hasNext()) a.next().let { if (seen.add(it.id)) out.add(it) }
            if (out.size >= limit) break
            if (b.hasNext()) b.next().let { if (seen.add(it.id)) out.add(it) }
        }
        return out
    }
}

/** Günlük mix'in cache gövdesi — day değişince yeniden üretilir (TTL yerine takvim). */
@Serializable
data class DailyMix(val day: String, val songs: List<Song>)

/** Materyalize durumunun cache gövdesi ("foryou:mixstate" anahtarı). */
@Serializable
data class MixState(val playlistId: String? = null, val attemptDay: String? = null)

/** Materyalize sonucu — log/doğrulama için (masaüstünde ekran yerine log okunur). */
enum class MixResult { WRITTEN, SKIPPED_TODAY, SKIPPED_OFFLINE, SKIPPED_EMPTY, FAILED }
