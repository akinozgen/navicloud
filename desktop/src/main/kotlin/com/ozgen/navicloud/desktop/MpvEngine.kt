package com.ozgen.navicloud.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.StringArray
import java.io.File

/**
 * libmpv C API'sinin ihtiyacımız kadarı (stabil ABI, libmpv-2.dll).
 * Salt ses backend'i: video çıkışı hiç açılmaz (vid=no) — Compose'a
 * pencere gömme derdi yok.
 */
@Suppress("FunctionName")
interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_command(ctx: Pointer, args: StringArray): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_error_string(error: Int): String
}

class MpvEngine {
    private val lib: LibMpv
    private val ctx: Pointer

    init {
        resolveDllDir()?.let { System.setProperty("jna.library.path", it) }
        lib = runCatching { Native.load("libmpv-2", LibMpv::class.java) }
            .recoverCatching { Native.load("mpv-2", LibMpv::class.java) }
            .getOrThrow()
        ctx = lib.mpv_create() ?: error("mpv_create başarısız")
        opt("vid", "no")
        opt("audio-display", "no")
        opt("terminal", "no")
        opt("gapless-audio", "yes")
        opt("cache", "yes")
        val r = lib.mpv_initialize(ctx)
        check(r == 0) { "mpv_initialize: ${lib.mpv_error_string(r)}" }
    }

    private fun opt(name: String, value: String) {
        lib.mpv_set_option_string(ctx, name, value)
    }

    /** libmpv-2.dll'i bilinen yerlerde arar (çalışma dizini, libs/, desktop/libs/). */
    private fun resolveDllDir(): String? {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(cwd, File(cwd, "libs"), File(cwd, "desktop/libs"), cwd.parentFile?.let { File(it, "desktop/libs") })
        return candidates.filterNotNull().firstOrNull { dir ->
            File(dir, "libmpv-2.dll").exists() || File(dir, "mpv-2.dll").exists()
        }?.absolutePath
    }

    fun command(vararg args: String): Int = lib.mpv_command(ctx, StringArray(args))

    fun play(url: String) {
        command("loadfile", url)
        setPaused(false)
    }

    fun setPaused(paused: Boolean) {
        lib.mpv_set_property_string(ctx, "pause", if (paused) "yes" else "no")
    }

    fun seekTo(seconds: Double) {
        command("seek", seconds.toString(), "absolute")
    }

    private fun prop(name: String): String? {
        val p = lib.mpv_get_property_string(ctx, name) ?: return null
        val s = p.getString(0)
        lib.mpv_free(p)
        return s
    }

    val positionSec: Double get() = prop("time-pos")?.toDoubleOrNull() ?: 0.0
    val durationSec: Double get() = prop("duration")?.toDoubleOrNull() ?: 0.0
    val isPaused: Boolean get() = prop("pause") == "yes"
    val isIdle: Boolean get() = prop("idle-active") == "yes"

    /** Codec rozeti benzeri bilgi: örn. "flac 44100Hz stereo". */
    val audioParams: String?
        get() = prop("audio-codec-name")?.let { codec ->
            val sr = prop("audio-params/samplerate")
            if (sr != null) "$codec • ${sr}Hz" else codec
        }

    fun release() = lib.mpv_terminate_destroy(ctx)
}
