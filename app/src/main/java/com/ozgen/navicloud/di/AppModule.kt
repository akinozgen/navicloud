package com.ozgen.navicloud.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext context: Context): NaviDb =
        Room.databaseBuilder(context, NaviDb::class.java, "navicloud.db").build()

    @Provides
    fun provideServerDao(db: NaviDb): ServerDao = db.serverDao()

    @Provides
    fun provideDownloadDao(db: NaviDb): DownloadDao = db.downloadDao()
}
