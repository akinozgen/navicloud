package com.ozgen.navicloud.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ozgen.navicloud.data.db.ApiCacheDao
import com.ozgen.navicloud.data.db.DownloadDao
import com.ozgen.navicloud.data.db.NaviDb
import com.ozgen.navicloud.data.db.ServerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "navicloud_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.prefsDataStore

    // v1→v2: metadata cache tablosu. Destructive migration YASAK —
    // sunucu kayıtları ve indirme listesi bu DB'de yaşıyor.
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `api_cache` (" +
                    "`key` TEXT NOT NULL, `json` TEXT NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): NaviDb =
        Room.databaseBuilder(context, NaviDb::class.java, "navicloud.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideServerDao(db: NaviDb): ServerDao = db.serverDao()

    @Provides
    fun provideDownloadDao(db: NaviDb): DownloadDao = db.downloadDao()

    @Provides
    fun provideApiCacheDao(db: NaviDb): ApiCacheDao = db.apiCacheDao()

    // Shared çekirdeğin portları → Android implementasyonları
    @Provides
    @Singleton
    fun provideServerSource(impl: com.ozgen.navicloud.data.ServerRepository): com.ozgen.navicloud.data.ServerSource = impl

    @Provides
    @Singleton
    fun provideOfflineModeSource(impl: com.ozgen.navicloud.data.SettingsRepository): com.ozgen.navicloud.data.OfflineModeSource = impl

    @Provides
    @Singleton
    fun provideLyricsSettings(impl: com.ozgen.navicloud.data.SettingsRepository): com.ozgen.navicloud.data.LyricsSettings = impl

    @Provides
    @Singleton
    fun provideApiCacheStore(impl: com.ozgen.navicloud.data.RoomApiCacheStore): com.ozgen.navicloud.data.ApiCacheStore = impl

    @Provides
    @Singleton
    fun provideRecentSearches(impl: com.ozgen.navicloud.data.DataStoreRecentSearches): com.ozgen.navicloud.data.RecentSearchesStore = impl

    @Provides
    @Singleton
    fun provideDownloadsPort(impl: com.ozgen.navicloud.data.DownloadRepository): com.ozgen.navicloud.data.DownloadsPort = impl

    @Provides
    @Singleton
    fun provideQueueStateStore(impl: com.ozgen.navicloud.playback.DataStoreQueueStateStore): com.ozgen.navicloud.playback.QueueStateStore = impl

    @Provides
    @Singleton
    fun provideQueueSyncStateStore(impl: com.ozgen.navicloud.playback.DataStoreQueueSyncStateStore): com.ozgen.navicloud.playback.QueueSyncStateStore = impl

    @Provides
    @Singleton
    fun provideQueueSyncManager(
        music: com.ozgen.navicloud.data.MusicRepository,
        servers: com.ozgen.navicloud.data.ServerSource,
        offline: com.ozgen.navicloud.data.OfflineModeSource,
        store: com.ozgen.navicloud.playback.QueueSyncStateStore,
        json: Json,
        // LOKAL player (Media3) — kuyruk senkronu BU cihazın çaldığını yazar; uzak kumanda karışmaz
        player: com.ozgen.navicloud.playback.Media3PlayerController,
    ): com.ozgen.navicloud.playback.QueueSyncManager =
        com.ozgen.navicloud.playback.QueueSyncManager(
            music, servers, offline, store, json,
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
            ),
            player,
            // Android: MediaController yalnız ana thread'den çağrılabilir
            kotlinx.coroutines.Dispatchers.Main,
        )

    @Provides
    @Singleton
    fun provideAudioEffectsController(impl: com.ozgen.navicloud.data.AudioEffectsRepository): com.ozgen.navicloud.audio.AudioEffectsController = impl

    @Provides
    @Singleton
    fun provideQueueCore(
        music: com.ozgen.navicloud.data.MusicRepository,
        downloads: com.ozgen.navicloud.data.DownloadsPort,
        offline: com.ozgen.navicloud.data.OfflineModeSource,
        store: com.ozgen.navicloud.playback.QueueStateStore,
        json: Json,
    ): com.ozgen.navicloud.playback.QueueCore =
        com.ozgen.navicloud.playback.QueueCore(music, downloads, offline, store, json)

    // UI'ın gördüğü PlayerController = lokal↔uzak swap proxy'si (ActivePlayerController).
    // Lokal Media3 implementasyonuna ihtiyacı olanlar (QueueSync, RC server/manager) somut tipi enjekte eder.
    @Provides
    @Singleton
    fun provideActivePlayerController(
        local: com.ozgen.navicloud.playback.Media3PlayerController,
    ): com.ozgen.navicloud.remote.ActivePlayerController =
        com.ozgen.navicloud.remote.ActivePlayerController(
            local,
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
            ),
        )

    @Provides
    @Singleton
    fun providePlayerController(
        active: com.ozgen.navicloud.remote.ActivePlayerController,
    ): com.ozgen.navicloud.playback.PlayerController = active

    // Uzaktan kumanda orkestratörü (RC-2): keşif + hedef geçişi + akıllı aktarım + busy.
    @Provides
    @Singleton
    fun provideRemoteControlManager(
        @ApplicationContext context: Context,
        local: com.ozgen.navicloud.playback.Media3PlayerController,
        active: com.ozgen.navicloud.remote.ActivePlayerController,
        music: com.ozgen.navicloud.data.MusicRepository,
        servers: com.ozgen.navicloud.data.ServerSource,
        settings: com.ozgen.navicloud.data.SettingsRepository,
        pairing: com.ozgen.navicloud.remote.DataStorePairingStore,
        okHttp: OkHttpClient,
        json: Json,
    ): com.ozgen.navicloud.remote.RemoteControlManager {
        val deviceIdBlocking = kotlinx.coroutines.runBlocking { settings.rcDeviceId() }
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
        return com.ozgen.navicloud.remote.RemoteControlManager(
            local = local,
            active = active,
            client = com.ozgen.navicloud.remote.OkHttpRcClient(okHttp, json),
            discovery = com.ozgen.navicloud.remote.NsdPeerDiscovery(context, deviceIdBlocking),
            scope = scope,
            // Android: MediaController yalnız ana thread'den çağrılabilir (QueueSync kuralı)
            playerDispatcher = kotlinx.coroutines.Dispatchers.Main,
            selfInfo = {
                com.ozgen.navicloud.remote.RcDeviceInfo(
                    deviceId = settings.rcDeviceId(),
                    name = settings.rcDeviceName.first(),
                    platform = "android",
                )
            },
            serverIdFlow = servers.activeServer.map { s ->
                s?.baseUrl?.let { com.ozgen.navicloud.remote.rcServerId(it) }
            },
            artworkResolver = { song -> runCatching { music.coverArtUrl(song.coverArt) }.getOrNull() },
            selfName = settings.rcDeviceName.stateIn(scope, SharingStarted.Eagerly, "NaviCloud"),
            setSelfNameImpl = { settings.setRcDeviceName(it) },
            pairing = pairing,
            secret = { settings.rcSecret.first().ifBlank { null } },
        )
    }

    // Uzaktan kumanda alıcısı (RC-1): lokal player'ı LAN WS'inde expose eder.
    // PlaybackService onCreate/onDestroy'da start/stop edilir (servis ömrü = kontrol edilebilirlik).
    @Provides
    @Singleton
    fun provideRemoteControlServer(
        player: com.ozgen.navicloud.playback.Media3PlayerController,
        manager: com.ozgen.navicloud.remote.RemoteControlManager,
        settings: com.ozgen.navicloud.data.SettingsRepository,
        pairing: com.ozgen.navicloud.remote.DataStorePairingStore,
        json: Json,
    ): com.ozgen.navicloud.remote.RemoteControlServer =
        com.ozgen.navicloud.remote.RemoteControlServer(
            server = com.ozgen.navicloud.remote.KtorRcServer(json),
            localPlayer = player,
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
            ),
            // Android: MediaController yalnız ana thread'den çağrılabilir (QueueSync kuralı)
            playerDispatcher = kotlinx.coroutines.Dispatchers.Main,
            deviceInfo = {
                com.ozgen.navicloud.remote.RcDeviceInfo(
                    deviceId = settings.rcDeviceId(),
                    name = settings.rcDeviceName.first(),
                    platform = "android",
                )
            },
            volumeSink = null, // mobilde donanım sesi; uzaktan VOLUME no-op
            // Kumanda ederken kilitli (soru turu kararı)
            isBusy = manager::isBusy,
            pairing = pairing,
            secret = { settings.rcSecret.first().ifBlank { null } },
        )
}
