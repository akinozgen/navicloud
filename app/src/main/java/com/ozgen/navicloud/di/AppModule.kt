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
    fun provideApiCacheStore(impl: com.ozgen.navicloud.data.RoomApiCacheStore): com.ozgen.navicloud.data.ApiCacheStore = impl

    // Platform-bağımsız PlayerController arayüzü → Android'de Media3 implementasyonu.
    // Masaüstü (KMP) portunda burası vlcj tabanlı implementasyona bağlanacak.
    @Provides
    @Singleton
    fun providePlayerController(
        impl: com.ozgen.navicloud.playback.Media3PlayerController,
    ): com.ozgen.navicloud.playback.PlayerController = impl
}
