package com.ozgen.navicloud.desktop

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/*
 * Linux sistem tepsisi: D-Bus StatusNotifierItem + com.canonical.dbusmenu.
 *
 * AWT SystemTray, KDE/GNOME'un modern tepsi protokolüne (StatusNotifierItem)
 * değil XEmbed'e dayandığı için sağ tık menüsü ve çift tık çalışmıyor.
 * Burada SNI'yi doğrudan konuşuyoruz — GTK/libappindicator bağımlılığı yok.
 * Watcher yoksa (ör. eklentisiz GNOME) [LinuxTray.start] false döner ve
 * çağıran taraf AWT tabanlı Compose Tray'e düşer.
 */

private const val ITEM_PATH = "/StatusNotifierItem"
private const val MENU_PATH = "/MenuBar"
private const val ITEM_IFACE = "org.kde.StatusNotifierItem"
private const val MENU_IFACE = "com.canonical.dbusmenu"

// Sabit menü yapısı — kimlikler GetLayout/Event arasında paylaşılır
private const val ID_ROOT = 0
private const val ID_SHOW = 1
private const val ID_MINI = 2
private const val ID_SEP1 = 3
private const val ID_PLAY = 4
private const val ID_PREV = 5
private const val ID_NEXT = 6
private const val ID_SEP2 = 7
private const val ID_QUIT = 8

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    @Suppress("FunctionName")
    fun RegisterStatusNotifierItem(service: String)
}

/** SNI ikon pixmap'i: (genişlik, yükseklik, ARGB32 network byte order). */
class SniPixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

/** SNI ToolTip: (ikon adı, pixmap'ler, başlık, metin). */
class SniToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val pixmaps: List<SniPixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val text: String,
) : Struct()

@Suppress("FunctionName")
@DBusInterfaceName(ITEM_IFACE)
interface StatusNotifierItem : DBusInterface {
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun ContextMenu(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    class NewToolTip(path: String) : DBusSignal(path)
    class NewIcon(path: String) : DBusSignal(path)
    class NewTitle(path: String) : DBusSignal(path)
}

/** dbusmenu düğümü: (id, özellikler, çocuklar — her biri Variant içinde aynı struct). */
class MenuNode(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct()

/**
 * GetLayout dönüşü: (revizyon, kök düğüm). dbus-java Tuple alt sınıflarının imzasını
 * metodun dönüş tipindeki generic argümanlardan çıkarır — sınıf parametrize olmak zorunda.
 */
class LayoutReply<A, B>(
    @field:Position(0) @JvmField val revision: A,
    @field:Position(1) @JvmField val layout: B,
) : Tuple()

/** GetGroupProperties girdisi başına: (id, özellikler). */
class MenuItemProps(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

@Suppress("FunctionName")
@DBusInterfaceName(MENU_IFACE)
interface DBusMenu : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): LayoutReply<UInt32, MenuNode>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProps>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun AboutToShow(id: Int): Boolean

    class LayoutUpdated(path: String, revision: UInt32, parent: Int) : DBusSignal(path, revision, parent)
}

class LinuxTray(
    private val onShow: () -> Unit,
    private val onMini: () -> Unit,
    private val onPlayPause: () -> Unit,
    private val onPrev: () -> Unit,
    private val onNext: () -> Unit,
    private val onQuit: () -> Unit,
) {
    private var connection: DBusConnection? = null

    // Menü/tooltip durumu — update() ile değişir, GetLayout/GetAll bunları okur
    @Volatile private var tooltipText = ""
    @Volatile private var playLabel = "Çal"
    @Volatile private var hasTrack = false
    @Volatile private var revision = 1L

    /** Panel ikonları: kaynak PNG'den birkaç boyut (panel 22-24px kullanır, KDE ölçekler). */
    private val iconPixmaps: List<SniPixmap> by lazy {
        val src = javaClass.getResourceAsStream("/navicloud-tray.png")?.use(ImageIO::read)
            ?: return@lazy emptyList()
        listOf(24, 48, 128).map { size ->
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            img.createGraphics().apply {
                drawImage(src.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null)
                dispose()
            }
            val buf = ByteBuffer.allocate(size * size * 4)
            for (y in 0 until size) for (x in 0 until size) buf.putInt(img.getRGB(x, y))
            SniPixmap(size, size, buf.array())
        }
    }

    private val itemObject = object : StatusNotifierItem, Properties {
        override fun getObjectPath() = ITEM_PATH

        override fun Activate(x: Int, y: Int) = onShow()
        override fun SecondaryActivate(x: Int, y: Int) = onPlayPause()
        override fun ContextMenu(x: Int, y: Int) = Unit // menüyü Menu property'si üzerinden panel açar
        override fun Scroll(delta: Int, orientation: String) = Unit

        override fun GetAll(iface: String): Map<String, Variant<*>> = mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant("navicloud"),
            "Title" to Variant("NaviCloud"),
            "Status" to Variant("Active"),
            "WindowId" to Variant(0),
            "IconName" to Variant(""),
            "IconPixmap" to Variant(iconPixmaps, "a(iiay)"),
            "OverlayIconName" to Variant(""),
            "OverlayIconPixmap" to Variant(emptyList<SniPixmap>(), "a(iiay)"),
            "AttentionIconName" to Variant(""),
            "AttentionIconPixmap" to Variant(emptyList<SniPixmap>(), "a(iiay)"),
            "ToolTip" to Variant(SniToolTip("", iconPixmaps, "NaviCloud", tooltipText), "(sa(iiay)ss)"),
            "ItemIsMenu" to Variant(false),
            "Menu" to Variant(DBusPath(MENU_PATH), "o"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(iface: String, prop: String): A = GetAll(iface)[prop]?.value as A
        override fun <A> Set(iface: String, prop: String, value: A) = Unit
    }

    private fun item(id: Int): Map<String, Variant<*>> {
        fun entry(label: String, enabled: Boolean = true): Map<String, Variant<*>> =
            mapOf("label" to Variant(label), "enabled" to Variant(enabled))
        val sep = mapOf<String, Variant<*>>("type" to Variant("separator"))
        return when (id) {
            ID_ROOT -> mapOf("children-display" to Variant("submenu"))
            ID_SHOW -> entry("Göster")
            ID_MINI -> entry("Mini oynatıcı")
            ID_SEP1, ID_SEP2 -> sep
            ID_PLAY -> entry(playLabel, hasTrack)
            ID_PREV -> entry("Önceki", hasTrack)
            ID_NEXT -> entry("Sonraki", hasTrack)
            ID_QUIT -> entry("Çıkış")
            else -> emptyMap()
        }
    }

    private val childIds = listOf(ID_SHOW, ID_MINI, ID_SEP1, ID_PLAY, ID_PREV, ID_NEXT, ID_SEP2, ID_QUIT)

    private val menuObject = object : DBusMenu, Properties {
        override fun getObjectPath() = MENU_PATH

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): LayoutReply<UInt32, MenuNode> {
            val children =
                if (parentId == ID_ROOT && recursionDepth != 0) {
                    childIds.map { Variant(MenuNode(it, item(it), emptyList()), "(ia{sv}av)") }
                } else {
                    emptyList()
                }
            return LayoutReply(UInt32(revision), MenuNode(parentId, item(parentId), children))
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProps> =
            ids.map { MenuItemProps(it, item(it)) }

        override fun GetProperty(id: Int, name: String): Variant<*> = item(id)[name] ?: Variant("")

        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId != "clicked") return
            when (id) {
                ID_SHOW -> onShow()
                ID_MINI -> onMini()
                ID_PLAY -> onPlayPause()
                ID_PREV -> onPrev()
                ID_NEXT -> onNext()
                ID_QUIT -> onQuit()
            }
        }

        override fun AboutToShow(id: Int) = false

        override fun GetAll(iface: String): Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(iface: String, prop: String): A = GetAll(iface)[prop]?.value as A
        override fun <A> Set(iface: String, prop: String, value: A) = Unit
    }

    /** true = SNI watcher bulundu ve kayıt başarılı. false = AWT tepsisine düş. */
    fun start(): Boolean {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return false
        return runCatching {
            val conn = DBusConnectionBuilder.forSessionBus().build()
            try {
                conn.exportObject(ITEM_PATH, itemObject)
                conn.exportObject(MENU_PATH, menuObject)
                val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
                conn.requestBusName(busName)
                conn.getRemoteObject(
                    "org.kde.StatusNotifierWatcher",
                    "/StatusNotifierWatcher",
                    StatusNotifierWatcher::class.java,
                ).RegisterStatusNotifierItem(busName)
                connection = conn
            } catch (e: Exception) {
                conn.disconnect()
                throw e
            }
        }.onFailure {
            println("LinuxTray: SNI kaydı başarısız (AWT tepsisine düşülüyor): $it")
        }.isSuccess
    }

    /** Parça/durum değişiminde tooltip + menü etiketlerini tazeler. */
    fun update(tooltip: String, playLabel: String, hasTrack: Boolean) {
        this.tooltipText = tooltip
        this.playLabel = playLabel
        this.hasTrack = hasTrack
        revision++
        val conn = connection ?: return
        runCatching {
            conn.sendMessage(StatusNotifierItem.NewToolTip(ITEM_PATH))
            conn.sendMessage(DBusMenu.LayoutUpdated(MENU_PATH, UInt32(revision), ID_ROOT))
        }
    }

    fun dispose() {
        runCatching { connection?.disconnect() }
        connection = null
    }
}
