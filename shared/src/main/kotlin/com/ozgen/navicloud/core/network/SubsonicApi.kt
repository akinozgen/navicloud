package com.ozgen.navicloud.core.network

import com.ozgen.navicloud.core.network.dto.SubsonicEnvelope
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SubsonicApi {

    @GET("ping")
    suspend fun ping(): SubsonicEnvelope

    @GET("getAlbumList2")
    suspend fun getAlbumList2(
        @Query("type") type: String,
        @Query("size") size: Int = 20,
        @Query("offset") offset: Int = 0,
    ): SubsonicEnvelope

    @GET("getAlbum")
    suspend fun getAlbum(@Query("id") id: String): SubsonicEnvelope

    @GET("getArtists")
    suspend fun getArtists(): SubsonicEnvelope

    @GET("getArtist")
    suspend fun getArtist(@Query("id") id: String): SubsonicEnvelope

    @GET("getArtistInfo2")
    suspend fun getArtistInfo2(
        @Query("id") id: String,
        @Query("count") count: Int = 8,
    ): SubsonicEnvelope

    @GET("getTopSongs")
    suspend fun getTopSongs(
        @Query("artist") artistName: String,
        @Query("count") count: Int = 10,
    ): SubsonicEnvelope

    @GET("getPlaylists")
    suspend fun getPlaylists(): SubsonicEnvelope

    @GET("getPlaylist")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("search3")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 8,
        @Query("albumCount") albumCount: Int = 12,
        @Query("songCount") songCount: Int = 25,
        @Query("songOffset") songOffset: Int = 0,
    ): SubsonicEnvelope

    @GET("getStarred2")
    suspend fun getStarred2(): SubsonicEnvelope

    @GET("getRandomSongs")
    suspend fun getRandomSongs(@Query("size") size: Int = 50): SubsonicEnvelope

    @GET("star")
    suspend fun star(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicEnvelope

    @GET("unstar")
    suspend fun unstar(
        @Query("id") id: String? = null,
        @Query("albumId") albumId: String? = null,
        @Query("artistId") artistId: String? = null,
    ): SubsonicEnvelope

    @GET("scrobble")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("submission") submission: Boolean,
    ): SubsonicEnvelope

    @GET("getLyricsBySongId")
    suspend fun getLyricsBySongId(@Query("id") id: String): SubsonicEnvelope

    @GET("updatePlaylist")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("comment") comment: String? = null,
        @Query("public") public: Boolean? = null,
        @Query("songIdToAdd") songIdsToAdd: List<String>? = null,
        // 0-tabanlı; sunucu çağrı anındaki snapshot sırasına göre yorumlar
        @Query("songIndexToRemove") songIndexesToRemove: List<Int>? = null,
    ): SubsonicEnvelope

    @GET("createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songIds: List<String>? = null,
    ): SubsonicEnvelope

    // Reorder = tam değiştirme: Subsonic'te move yok; playlistId + TAM sıralı songId listesi
    // listeyi komple değiştirir. BOŞ liste bazı sürümlerde wipe — guard repo'da. POST form:
    // uzun listeler URL limitine sığmaz (Navidrome formPost uzantısı; savePlayQueue emsali).
    @FormUrlEncoded
    @POST("createPlaylist")
    suspend fun replacePlaylist(
        @Field("playlistId") playlistId: String,
        @Field("songId") songIds: List<String>,
    ): SubsonicEnvelope

    @GET("deletePlaylist")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicEnvelope

    @GET("startScan")
    suspend fun startScan(@Query("fullScan") fullScan: Boolean = false): SubsonicEnvelope

    @GET("getScanStatus")
    suspend fun getScanStatus(): SubsonicEnvelope

    @GET("getSimilarSongs2")
    suspend fun getSimilarSongs2(
        @Query("id") artistId: String,
        @Query("count") count: Int = 25,
    ): SubsonicEnvelope

    // Kuyruk senkronu — savePlayQueue POST form (büyük kuyruk = uzun id listesi, URL limitini
    // aşmasın; auth interceptor query'ye eklendiği için gövde sadece kuyruk verisi taşır).
    @FormUrlEncoded
    @POST("savePlayQueue")
    suspend fun savePlayQueue(
        @Field("id") ids: List<String>,
        @Field("current") current: String?,
        @Field("position") position: Long?,
    ): SubsonicEnvelope

    @GET("getPlayQueue")
    suspend fun getPlayQueue(): SubsonicEnvelope
}
