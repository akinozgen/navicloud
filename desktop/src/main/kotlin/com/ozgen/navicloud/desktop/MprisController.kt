package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

/**
 * MPRIS (org.mpris.MediaPlayer2) entegrasyonu — SMTC'nin Linux muadili.
 *
 * KDE/GNOME medya denetçisi, kilit ekranı ve donanım medya tuşları bu D-Bus
 * servisi üzerinden çalışır. [PlayerController]'a bağlanır: parça metadata'sı
 * + oynatma durumu PropertiesChanged ile yayınlanır, Play/Pause/Next/Prev
 * komutları player'a iletilir. Linux değilse ya da D-Bus yoksa sessizce
 * devre dışı kalır ([start] false döner).
 */

private const val MPRIS_PATH = "/org/mpris/MediaPlayer2"
private const val ROOT_IFACE = "org.mpris.MediaPlayer2"
private const val PLAYER_IFACE = "org.mpris.MediaPlayer2.Player"

@Suppress("FunctionName")
@DBusInterfaceName(ROOT_IFACE)
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@Suppress("FunctionName")
@DBusInterfaceName(PLAYER_IFACE)
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)

    class Seeked(path: String, position: Long) : DBusSignal(path, position)
}

class MprisController(
    private val player: PlayerController,
    private val onRaise: () -> Unit,
    private val onQuit: () -> Unit,
    private val getVolume: () -> Float,
    private val setVolume: (Float) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connection: DBusConnection? = null

    private fun playbackStatus(): String {
        val s = player.state.value
        return when {
            s.isPlaying -> "Playing"
            s.currentTrack != null -> "Paused"
            else -> "Stopped"
        }
    }

    private fun loopStatus(): String = when (player.state.value.repeat) {
        RepeatMode.OFF -> "None"
        RepeatMode.ALL -> "Playlist"
        RepeatMode.ONE -> "Track"
    }

    /** mpris:trackid geçerli bir D-Bus object path olmalı — uid'i [A-Za-z0-9_] setine indirger. */
    private fun trackId(uid: String) =
        DBusPath("/com/ozgen/navicloud/track/" + uid.map { if (it.isLetterOrDigit()) it else '_' }.joinToString(""))

    private fun metadata(): Map<String, Variant<*>> {
        val track = player.state.value.currentTrack ?: return emptyMap()
        val m = mutableMapOf<String, Variant<*>>(
            "mpris:trackid" to Variant(trackId(track.uid), "o"),
            "mpris:length" to Variant(track.song.duration * 1_000_000L),
            "xesam:title" to Variant(track.song.title),
        )
        track.song.album?.let { m["xesam:album"] = Variant(it) }
        track.song.artist?.let { m["xesam:artist"] = Variant(listOf(it), "as") }
        track.artworkUrl?.let { m["mpris:artUrl"] = Variant(it) }
        return m
    }

    private fun playerProps(): Map<String, Variant<*>> {
        val s = player.state.value
        val hasTrack = s.currentTrack != null
        return mapOf(
            "PlaybackStatus" to Variant(playbackStatus()),
            "LoopStatus" to Variant(loopStatus()),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(s.shuffle),
            "Metadata" to Variant(metadata(), "a{sv}"),
            "Volume" to Variant(getVolume().toDouble()),
            "Position" to Variant(player.positionMs * 1000),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(hasTrack),
            "CanGoPrevious" to Variant(hasTrack),
            "CanPlay" to Variant(hasTrack),
            "CanPause" to Variant(hasTrack),
            "CanSeek" to Variant(hasTrack),
            "CanControl" to Variant(true),
        )
    }

    private fun rootProps(): Map<String, Variant<*>> = mapOf(
        "CanQuit" to Variant(true),
        "CanRaise" to Variant(true),
        "HasTrackList" to Variant(false),
        "Identity" to Variant("NaviCloud"),
        "DesktopEntry" to Variant("navicloud-NaviCloud"),
        "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
        "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
    )

    private val mprisObject = object : MprisRoot, MprisPlayer, Properties {
        override fun getObjectPath() = MPRIS_PATH

        override fun Raise() = onRaise()
        override fun Quit() = onQuit()

        override fun Next() = player.skipNext()
        override fun Previous() = player.skipPrevious()
        override fun Pause() { if (player.state.value.isPlaying) player.togglePlayPause() }
        override fun Play() { if (!player.state.value.isPlaying) player.togglePlayPause() }
        override fun PlayPause() = player.togglePlayPause()
        override fun Stop() = player.stop()

        override fun Seek(offset: Long) {
            val target = (player.positionMs + offset / 1000).coerceAtLeast(0)
            player.seekTo(target)
            emitSeeked(target)
        }

        override fun SetPosition(trackId: DBusPath, position: Long) {
            player.seekTo(position / 1000)
            emitSeeked(position / 1000)
        }

        override fun OpenUri(uri: String) = Unit

        override fun GetAll(iface: String): Map<String, Variant<*>> = when (iface) {
            PLAYER_IFACE -> playerProps()
            else -> rootProps()
        }

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(iface: String, prop: String): A = GetAll(iface)[prop]?.value as A

        override fun <A> Set(iface: String, prop: String, value: A) {
            when (prop) {
                "Volume" -> (value as? Double)?.let { setVolume(it.toFloat().coerceIn(0f, 1f)) }
                "Shuffle" -> if (value as? Boolean != player.state.value.shuffle) player.toggleShuffle()
                "LoopStatus" -> {
                    // RepeatMode döngüseldir; istenen değere gelene dek çevir (en çok 2 adım)
                    repeat(2) { if (loopStatus() != value) player.cycleRepeat() }
                }
            }
        }
    }

    private fun emitSeeked(positionMs: Long) {
        runCatching { connection?.sendMessage(MprisPlayer.Seeked(MPRIS_PATH, positionMs * 1000)) }
    }

    private fun emitChanged(props: Map<String, Variant<*>>) {
        val conn = connection ?: return
        runCatching {
            conn.sendMessage(Properties.PropertiesChanged(MPRIS_PATH, PLAYER_IFACE, props, emptyList()))
        }
    }

    /** true = servis yayında. false = Linux değil / D-Bus yok (sessiz devre dışı). */
    fun start(): Boolean {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return false
        val ok = runCatching {
            val conn = DBusConnectionBuilder.forSessionBus().build()
            try {
                conn.exportObject(MPRIS_PATH, mprisObject)
                conn.requestBusName("org.mpris.MediaPlayer2.navicloud")
                connection = conn
            } catch (e: Exception) {
                conn.disconnect()
                throw e
            }
        }.isSuccess
        if (!ok) return false

        // Parça/durum değişimi -> PropertiesChanged (KDE/GNOME denetçisi bunu dinler)
        scope.launch {
            var lastId: String? = null
            var lastPlaying: Boolean? = null
            var lastShuffle: Boolean? = null
            var lastRepeat: RepeatMode? = null
            player.state.collect { s ->
                val changed = mutableMapOf<String, Variant<*>>()
                val trackChanged = s.currentTrack?.uid != lastId
                if (trackChanged) {
                    lastId = s.currentTrack?.uid
                    changed["Metadata"] = Variant(metadata(), "a{sv}")
                    val hasTrack = s.currentTrack != null
                    changed["CanGoNext"] = Variant(hasTrack)
                    changed["CanGoPrevious"] = Variant(hasTrack)
                    changed["CanPlay"] = Variant(hasTrack)
                    changed["CanPause"] = Variant(hasTrack)
                    changed["CanSeek"] = Variant(hasTrack)
                }
                if (s.isPlaying != lastPlaying || trackChanged) {
                    lastPlaying = s.isPlaying
                    changed["PlaybackStatus"] = Variant(playbackStatus())
                }
                if (s.shuffle != lastShuffle) {
                    lastShuffle = s.shuffle
                    changed["Shuffle"] = Variant(s.shuffle)
                }
                if (s.repeat != lastRepeat) {
                    lastRepeat = s.repeat
                    changed["LoopStatus"] = Variant(loopStatus())
                }
                if (changed.isNotEmpty()) emitChanged(changed)
            }
        }
        return true
    }

    fun dispose() {
        runCatching { connection?.disconnect() }
        connection = null
    }
}
