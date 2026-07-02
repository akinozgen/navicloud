package com.ozgen.navicloud.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.ozgen.navicloud.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AKIŞ cache'i: çalınan/prefetch edilen sesin geçici LRU deposu.
 * KALICI indirmelerden (filesDir/music, DownloadRepository) tamamen AYRI —
 * buradaki eviction indirmelere asla dokunamaz.
 */
@Singleton
class StreamCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseOkHttp: OkHttpClient,
    settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val evictor = AdjustableLruEvictor(DEFAULT_MAX_BYTES)

    val cache: SimpleCache = SimpleCache(
        File(context.cacheDir, "stream"),
        evictor,
        StandaloneDatabaseProvider(context),
    )

    init {
        // Ayarlardaki boyut sınırı canlı uygulanır; küçültme bir sonraki
        // yazmada eritilir
        scope.launch {
            settings.streamCacheMaxMb.collect { mb ->
                evictor.maxBytes = mb * 1024L * 1024L
            }
        }
    }

    /**
     * Anahtar auth'suz ve sunucu+şarkı+kalite bazlı: URL'deki salt/token her
     * açılışta değiştiği için URL'nin kendisi anahtar OLAMAZ.
     */
    val cacheKeyFactory = CacheKeyFactory { dataSpec ->
        val uri = dataSpec.uri
        val id = uri.getQueryParameter("id")
        if (id != null && uri.pathSegments.lastOrNull() == "stream") {
            "stream:${uri.host}:${uri.port}:$id:" +
                "${uri.getQueryParameter("maxBitRate") ?: "raw"}:${uri.getQueryParameter("format") ?: ""}"
        } else {
            dataSpec.key ?: uri.toString()
        }
    }

    /** Cache'ten okuyup upstream'e OkHttp ile çıkan factory (yalnız http/https). */
    fun cacheDataSourceFactory(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(baseOkHttp))
            .setCacheKeyFactory(cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Player'ın kullanacağı factory: http/https cache üzerinden akar,
     * file:// (kalıcı indirmeler) cache'e HİÇ girmeden doğrudan okunur —
     * yoksa indirmeler bir de akış cache'ine kopyalanıp LRU'yu şişirirdi.
     */
    fun playerDataSourceFactory(): DataSource.Factory {
        val cacheFactory = cacheDataSourceFactory()
        val localFactory = DefaultDataSource.Factory(context)
        return DataSource.Factory {
            RoutingDataSource(cacheFactory.createDataSource(), localFactory.createDataSource())
        }
    }

    suspend fun cacheSpaceBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    suspend fun clear() = withContext(Dispatchers.IO) {
        // Aktif okunan span kilitlidir; kalanı silinir, çalan parça
        // FLAG_IGNORE_CACHE_ON_ERROR sayesinde upstream'den sürer
        cache.keys.toList().forEach { key -> runCatching { cache.removeResource(key) } }
    }

    companion object {
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024
    }
}

/**
 * LeastRecentlyUsedCacheEvictor'ın maxBytes'ı çalışırken değiştirilebilen
 * birebir karşılığı (Media3'teki sınıfın sınırı final).
 */
class AdjustableLruEvictor(@Volatile var maxBytes: Long) : CacheEvictor {
    private val leastRecentlyUsed = TreeSet<CacheSpan> { a, b ->
        val t = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
        if (t != 0) t else a.compareTo(b)
    }
    private var currentSize = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evictCache(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSize += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        while (currentSize + requiredSpace > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}

/** open() anında şemaya göre yönlendirir: http(s) → cache'li yol, diğerleri → yerel. */
private class RoutingDataSource(
    private val network: DataSource,
    private val local: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        network.addTransferListener(transferListener)
        local.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val delegate = when (dataSpec.uri.scheme) {
            "http", "https" -> network
            else -> local
        }
        active = delegate
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        active?.close()
        active = null
    }
}
