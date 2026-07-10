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

/** libmpv Linux'ta LC_NUMERIC=C bekler; JVM'in devraldığı locale (ör. tr_TR) mpv_create'i reddeder. */
private interface LibC : Library {
    fun setlocale(category: Int, locale: String): String?
}

class MpvEngine {
    private val lib: LibMpv
    private val ctx: Pointer

    init {
        resolveDllDir()?.let { System.setProperty("jna.library.path", it) }
        // Linux: libmpv, JVM'in devraldığı locale non-C ise mpv_create'i reddediyor. Sadece LC_NUMERIC
        // (glibc = 1) sıfırla — kullanıcı locale'inin başka etkilerine dokunmuyoruz.
        if (System.getProperty("os.name").orEmpty().lowercase().contains("linux")) {
            runCatching { Native.load("c", LibC::class.java).setlocale(1, "C") }
        }
        // Windows: libmpv-2.dll / mpv-2.dll · Linux: libmpv.so.2 / libmpv.so.1 (soname → "mpv")
        lib = runCatching { Native.load("libmpv-2", LibMpv::class.java) }
            .recoverCatching { Native.load("mpv-2", LibMpv::class.java) }
            .recoverCatching { Native.load("mpv", LibMpv::class.java) }
            .getOrThrow()
        ctx = lib.mpv_create() ?: error("mpv_create başarısız")
        opt("vid", "no")
        opt("audio-display", "no")
        opt("terminal", "no")
        opt("gapless-audio", "yes")
        opt("cache", "yes")
        // Ses sunucusunda (PipeWire/PulseAudio) "mpv" değil uygulama adı görünsün
        opt("audio-client-name", "NaviCloud")
        // Akış adı şablonu (vars "<başlık> - mpv") — sadece parça başlığı kalsın
        opt("title", "\${media-title}")
        val r = lib.mpv_initialize(ctx)
        check(r == 0) { "mpv_initialize: ${lib.mpv_error_string(r)}" }
    }

    private fun opt(name: String, value: String) {
        lib.mpv_set_option_string(ctx, name, value)
    }

    /**
     * libmpv'yi bilinen yerlerde arar (çalışma dizini, libs/, desktop/libs/, jpackage resources).
     * Linux'ta gömülü kopya beklenmez — sistem paketi (mpv-libs) `libmpv.so.2` sağlar; JNA
     * `jna.library.path` boşsa doğrudan `/usr/lib64` gibi standart yollardan yükler.
     */
    private fun resolveDllDir(): String? {
        val cwd = File(System.getProperty("user.dir"))
        val packaged = System.getProperty("compose.application.resources.dir")?.let { File(it) }
        val candidates = listOf(packaged, cwd, File(cwd, "libs"), File(cwd, "desktop/libs"), cwd.parentFile?.let { File(it, "desktop/libs") })
        val names = listOf("libmpv-2.dll", "mpv-2.dll", "libmpv.so.2", "libmpv.so.1", "libmpv.so")
        return candidates.filterNotNull().firstOrNull { dir ->
            names.any { File(dir, it).exists() }
        }?.absolutePath
    }

    fun command(vararg args: String): Int = lib.mpv_command(ctx, StringArray(args))

    fun play(url: String) {
        command("loadfile", url)
        setPaused(false)
    }

    /** Ses sunucusundaki akış başlığı (vars: "<url> - mpv") — parça bilgisiyle ezilir. */
    fun setMediaTitle(title: String) {
        lib.mpv_set_property_string(ctx, "force-media-title", title)
    }

    fun setPaused(paused: Boolean) {
        lib.mpv_set_property_string(ctx, "pause", if (paused) "yes" else "no")
    }

    fun seekTo(seconds: Double) {
        command("seek", seconds.toString(), "absolute")
    }

    /**
     * Ses filtresi zincirini ayarlar (mpv 'af' özelliği, libavfilter köprüsü).
     * Boş string zinciri temizler. Canlı uygulanır ve loadfile'lar arası korunur.
     */
    fun setAudioFilters(af: String) {
        lib.mpv_set_property_string(ctx, "af", af)
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

    /** 0-100 arası ses seviyesi (mpv 'volume' özelliği). */
    var volume: Int
        get() = prop("volume")?.toDoubleOrNull()?.toInt() ?: 100
        set(value) {
            lib.mpv_set_property_string(ctx, "volume", value.coerceIn(0, 130).toString())
        }
    val isIdle: Boolean get() = prop("idle-active") == "yes"

    /** Codec rozeti benzeri bilgi: örn. "flac 44100Hz stereo". */
    val audioParams: String?
        get() = prop("audio-codec-name")?.let { codec ->
            val sr = prop("audio-params/samplerate")
            if (sr != null) "$codec • ${sr}Hz" else codec
        }

    fun release() = lib.mpv_terminate_destroy(ctx)
}
