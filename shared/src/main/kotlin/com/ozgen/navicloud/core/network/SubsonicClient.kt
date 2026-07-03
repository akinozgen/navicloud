package com.ozgen.navicloud.core.network

import com.ozgen.navicloud.core.model.Server
import com.ozgen.navicloud.core.network.dto.SubsonicResponse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.MessageDigest
import java.security.SecureRandom

class SubsonicException(val code: Int, message: String) : Exception(message)

private const val API_VERSION = "1.16.1"
private const val CLIENT_NAME = "NaviCloud"

/** Per-server Subsonic client: authenticated Retrofit API + URL builders. */
class SubsonicClient(
    val server: Server,
    baseOkHttp: OkHttpClient,
    json: Json,
) {
    private val salt: String = buildString {
        val rnd = SecureRandom()
        repeat(12) { append("abcdefghijklmnopqrstuvwxyz0123456789"[rnd.nextInt(36)]) }
    }
    private val token: String = md5Hex(server.password + salt)

    private val restUrl: HttpUrl = (server.baseUrl.trimEnd('/') + "/rest/").toHttpUrl()

    private val authInterceptor = Interceptor { chain ->
        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", server.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()
        chain.proceed(chain.request().newBuilder().url(url).build())
    }

    val okHttp: OkHttpClient = baseOkHttp.newBuilder()
        .addInterceptor(authInterceptor)
        .build()

    val api: SubsonicApi = Retrofit.Builder()
        .baseUrl(restUrl)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SubsonicApi::class.java)

    private fun authedUrl(endpoint: String): HttpUrl.Builder =
        restUrl.newBuilder().addPathSegment(endpoint)
            .addQueryParameter("u", server.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)

    fun streamUrl(songId: String, maxBitRateKbps: Int? = null, format: String? = null): String =
        authedUrl("stream").addQueryParameter("id", songId).apply {
            maxBitRateKbps?.let { addQueryParameter("maxBitRate", it.toString()) }
            format?.let { addQueryParameter("format", it) }
        }.build().toString()

    fun coverArtUrl(coverArtId: String, size: Int? = null): String =
        authedUrl("getCoverArt").addQueryParameter("id", coverArtId).apply {
            size?.let { addQueryParameter("size", it.toString()) }
        }.build().toString()

    fun downloadUrl(songId: String): String =
        authedUrl("download").addQueryParameter("id", songId).build().toString()

    companion object {
        fun md5Hex(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/** Unwraps the subsonic-response envelope, throwing on API-level failure. */
fun com.ozgen.navicloud.core.network.dto.SubsonicEnvelope.unwrap(): SubsonicResponse {
    val r = response
    if (r.status != "ok") {
        val err = r.error
        throw SubsonicException(err?.code ?: -1, err?.message ?: "Bilinmeyen sunucu hatası")
    }
    return r
}
