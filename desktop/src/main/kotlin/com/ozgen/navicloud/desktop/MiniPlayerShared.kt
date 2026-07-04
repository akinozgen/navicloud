package com.ozgen.navicloud.desktop

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import kotlin.math.roundToInt

/** Mini oynatıcının iki görünümü. Konum/persist ikisinde ortaktır. */
enum class MiniVariant { STANDARD, VINYL }

val MINI_STANDARD_SIZE = DpSize(420.dp, 190.dp)
val MINI_VINYL_SIZE = DpSize(220.dp, 220.dp)

/**
 * Mini pencere durumunun TEK kaynağı — konum + varyant. Ses motorundan
 * (mpv) tamamen bağımsız, saf pencere/UI mantığı. Konum AWT ekran px'inde
 * tutulur (MouseInfo/GraphicsConfiguration ile aynı uzay → DPI'dan bağımsız
 * doğru), her değişimde DesktopPrefs'e (ayarlarla aynı store) yazılır.
 */
class MiniWindowModel {
    var variant by mutableStateOf(DesktopPrefs.miniVariant)
        private set

    private var pos: Point? = DesktopPrefs.miniPosition

    fun position(): Point? = pos

    fun onMoved(x: Int, y: Int) {
        val p = Point(x, y)
        pos = p
        DesktopPrefs.miniPosition = p
    }

    fun switchVariant(v: MiniVariant) {
        if (v == variant) return
        variant = v
        DesktopPrefs.miniVariant = v
    }
}

/**
 * Ekran geometrisi yardımcıları — çok monitör + taskbar hariç work area +
 * kenara snap. Hepsi AWT ekran px uzayında çalışır.
 */
object MiniGeometry {
    private const val SNAP_THRESHOLD = 20   // px — bu eşik içindeyse kenara yapış
    private const val MARGIN = 24           // varsayılan sağ-alt konum boşluğu

    private fun screens(): List<GraphicsConfiguration> =
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration }

    private fun primary(): GraphicsConfiguration =
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration

    /** Ekranın taskbar/dock hariç kullanılabilir alanı. */
    fun workArea(gc: GraphicsConfiguration): Rectangle {
        val b = gc.bounds
        val ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        return Rectangle(
            b.x + ins.left, b.y + ins.top,
            b.width - ins.left - ins.right, b.height - ins.top - ins.bottom,
        )
    }

    /** Bir dikdörtgenin EN ÇOK örtüştüğü ekran; hiçbiriyle kesişmiyorsa merkeze en yakın ekran. */
    fun screenForRect(r: Rectangle): GraphicsConfiguration {
        val list = screens()
        val best = list.maxByOrNull { gc ->
            val i = gc.bounds.intersection(r)
            if (i.isEmpty) 0L else i.width.toLong() * i.height
        }
        if (best != null && !best.bounds.intersection(r).isEmpty) return best
        val c = Point(r.centerX.toInt(), r.centerY.toInt())
        return list.minByOrNull { gc ->
            val gcc = Point(gc.bounds.centerX.toInt(), gc.bounds.centerY.toInt())
            val dx = (gcc.x - c.x).toLong(); val dy = (gcc.y - c.y).toLong()
            dx * dx + dy * dy
        } ?: primary()
    }

    /** Pencereyi tümüyle work area içinde kalacak şekilde kıstır. */
    fun clampToWorkArea(x: Int, y: Int, w: Int, h: Int, wa: Rectangle): Point {
        val nx = if (w >= wa.width) wa.x else x.coerceIn(wa.x, wa.x + wa.width - w)
        val ny = if (h >= wa.height) wa.y else y.coerceIn(wa.y, wa.y + wa.height - h)
        return Point(nx, ny)
    }

    fun defaultBottomEnd(w: Int, h: Int, gc: GraphicsConfiguration): Point {
        val wa = workArea(gc)
        return Point(wa.x + wa.width - w - MARGIN, wa.y + wa.height - h - MARGIN)
    }

    /**
     * Açılışta (LaunchedEffect) yetkili yerleştirme: kaydedilmiş konumu doğrula
     * — görünür alanla kesişmiyorsa (monitör çıkarılmış/çözünürlük değişmiş)
     * varsayılana dön, aksi halde work area'ya clamp et. Sonuç setLocation + persist.
     */
    suspend fun place(win: ComposeWindow, model: MiniWindowModel) {
        var tries = 0
        while (win.width == 0 && tries < 40) { delay(16); tries++ }
        val w = win.width.coerceAtLeast(1)
        val h = win.height.coerceAtLeast(1)
        val saved = model.position()
        val target = if (saved != null) {
            val rect = Rectangle(saved.x, saved.y, w, h)
            val gc = screenForRect(rect)
            if (gc.bounds.intersection(rect).isEmpty) defaultBottomEnd(w, h, gc)
            else clampToWorkArea(saved.x, saved.y, w, h, workArea(gc))
        } else {
            defaultBottomEnd(w, h, primary())
        }
        win.setLocation(target.x, target.y)
        model.onMoved(target.x, target.y)
    }

    /**
     * Sürükleme bitince: pencerenin bulunduğu ekranın work area kenarına eşik
     * içindeyse hizala; ayrıca görünür alana clamp et. Tek seferlik spring
     * animasyonu ile taşınır, sonra persist edilir.
     */
    suspend fun snapAndPersist(win: ComposeWindow, model: MiniWindowModel) {
        val w = win.width
        val h = win.height
        val rect = Rectangle(win.x, win.y, w, h)
        val wa = workArea(screenForRect(rect))
        var nx = win.x
        var ny = win.y
        if (win.x - wa.x <= SNAP_THRESHOLD) nx = wa.x
        else if ((wa.x + wa.width) - (win.x + w) <= SNAP_THRESHOLD) nx = wa.x + wa.width - w
        if (win.y - wa.y <= SNAP_THRESHOLD) ny = wa.y
        else if ((wa.y + wa.height) - (win.y + h) <= SNAP_THRESHOLD) ny = wa.y + wa.height - h
        val target = clampToWorkArea(nx, ny, w, h, wa)
        if (target.x != win.x || target.y != win.y) {
            val sx = win.x
            val sy = win.y
            animate(
                initialValue = 0f, targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
            ) { t, _ ->
                win.setLocation(
                    (sx + (target.x - sx) * t).roundToInt(),
                    (sy + (target.y - sy) * t).roundToInt(),
                )
            }
        }
        model.onMoved(win.x, win.y)
    }
}

/**
 * Açılışta yakın-doğru (flash'sız) başlangıç konumu. Kaydedilmiş konum varsa
 * o ekranın ölçeğiyle Dp'ye çevrilir; monitör çıkarılmışsa/geçersizse
 * [MiniGeometry.place] açılışta yine yetkili düzeltmeyi yapar.
 */
fun initialMiniPosition(model: MiniWindowModel, sizeDp: DpSize): WindowPosition {
    val saved = model.position() ?: return WindowPosition(Alignment.BottomEnd)
    val gc = MiniGeometry.screenForRect(Rectangle(saved.x, saved.y, 1, 1))
    val scale = gc.defaultTransform.scaleX.takeIf { it > 0.0 } ?: 1.0
    return WindowPosition.Absolute((saved.x / scale).dp, (saved.y / scale).dp)
}

/**
 * Ortak sürükle-taşı davranışı (standart üst şeridi + plak varyantının zemini).
 * Mutlak fare deltası kullanır (bileşen-göreli delta pencere kayınca titrer);
 * sürükleme bitince [MiniGeometry.snapAndPersist] çağrılır.
 */
fun Modifier.miniWindowDrag(win: ComposeWindow, model: MiniWindowModel, scope: CoroutineScope): Modifier =
    this.pointerInput(win) {
        var last: Point? = null
        var job: Job? = null
        detectDragGestures(
            onDragStart = {
                job?.cancel()
                last = java.awt.MouseInfo.getPointerInfo()?.location
            },
            onDragEnd = {
                last = null
                job = scope.launch { MiniGeometry.snapAndPersist(win, model) }
            },
            onDragCancel = {
                last = null
                job = scope.launch { MiniGeometry.snapAndPersist(win, model) }
            },
        ) { change, _ ->
            change.consume()
            val cur = java.awt.MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
            last?.let { win.setLocation(win.x + (cur.x - it.x), win.y + (cur.y - it.y)) }
            last = cur
        }
    }
