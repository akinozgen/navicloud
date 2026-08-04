package com.ozgen.navicloud.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.ozgen.navicloud.R
import com.ozgen.navicloud.data.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot → bitmap → RemoteViews → [AppWidgetManager.updateAppWidget].
 *
 * Kapak URL'i + bitmap + accent bir kez çözülür, iki widget tipine de dağıtılır.
 * Her widget kendi hücre boyutunda (options'tan) piksel-birebir zemin bitmap'i
 * alır (fitXY → gerilme yok, yuvarlak köşe bozulmaz). IO thread'inde çağrılır.
 */
@Singleton
class WidgetRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val music: MusicRepository,
) {
    private val density = context.resources.displayMetrics.density

    private val bgRadiusPx: Float = runCatching {
        context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
    }.getOrDefault(16f * density)

    suspend fun render(snapshot: WidgetSnapshot) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val barIds = mgr.getAppWidgetIds(ComponentName(context, BarWidgetProvider::class.java))
        val vinylIds = mgr.getAppWidgetIds(ComponentName(context, VinylWidgetProvider::class.java))
        if (barIds.isEmpty() && vinylIds.isEmpty()) return

        // Kapak bitmap'i + accent bir kez (256px yeter; blur/disk buradan türer)
        var cover: Bitmap? = null
        if (snapshot.coverArtId != null) {
            val url = runCatching { music.coverArtUrl(snapshot.coverArtId, 256) }.getOrNull()
            if (url != null) cover = loadWidgetCover(context, url, snapshot.coverArtId, 256)
        }
        val accent = cover?.let { WidgetBitmaps.accentFrom(it) } ?: WidgetContract.DEFAULT_ACCENT

        for (id in barIds) runCatching { renderBar(mgr, id, snapshot, cover, accent) }
        for (id in vinylIds) runCatching { renderVinyl(mgr, id, snapshot, cover, accent) }
    }

    // ---- Bar (geniş) ----

    private fun renderBar(mgr: AppWidgetManager, id: Int, s: WidgetSnapshot, cover: Bitmap?, accent: Int) {
        val (wPx, hPx) = cellPx(mgr, id, defW = 250, defH = 110)
        val rv = RemoteViews(context.packageName, R.layout.widget_bar)

        rv.setImageViewBitmap(
            R.id.widget_bar_bg,
            WidgetBitmaps.barBackground(cover, wPx, hPx, accent, bgRadiusPx),
        )
        applyOffline(rv, R.id.widget_bar_offline, s.isOffline)

        if (s.isIdle) {
            rv.setViewVisibility(R.id.widget_bar_content, View.GONE)
            rv.setViewVisibility(R.id.widget_bar_idle, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.widget_bar_root, WidgetTransport.openApp(context))
            mgr.updateAppWidget(id, rv)
            return
        }

        rv.setViewVisibility(R.id.widget_bar_idle, View.GONE)
        rv.setViewVisibility(R.id.widget_bar_content, View.VISIBLE)
        rv.setTextViewText(R.id.widget_bar_title, s.title ?: "")
        rv.setTextViewText(R.id.widget_bar_artist, s.artist ?: "")
        cover?.let {
            rv.setImageViewBitmap(
                R.id.widget_bar_cover,
                WidgetBitmaps.roundedCover(it, (56 * density).toInt(), 12f * density),
            )
        }
        rv.setImageViewResource(
            R.id.widget_bar_playpause,
            if (s.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )

        // İnce non-interaktif progress (accent tint), yalnız süre bilindiğinde
        if (s.durationMs > 0) {
            rv.setViewVisibility(R.id.widget_bar_progress, View.VISIBLE)
            val frac = (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f)
            rv.setProgressBar(R.id.widget_bar_progress, 1000, (frac * 1000).toInt(), false)
            runCatching {
                rv.setColorStateList(
                    R.id.widget_bar_progress,
                    "setProgressTintList",
                    ColorStateList.valueOf(accent),
                )
            }
        } else {
            rv.setViewVisibility(R.id.widget_bar_progress, View.INVISIBLE)
        }

        bindTransport(
            rv, s,
            playPauseId = R.id.widget_bar_playpause,
            nextId = R.id.widget_bar_next,
            prevId = R.id.widget_bar_prev,
            rootId = R.id.widget_bar_root,
        )
        mgr.updateAppWidget(id, rv)
    }

    // ---- Vinyl (kare) ----

    private fun renderVinyl(mgr: AppWidgetManager, id: Int, s: WidgetSnapshot, cover: Bitmap?, accent: Int) {
        val (wPx, hPx) = cellPx(mgr, id, defW = 110, defH = 110)
        val side = minOf(wPx, hPx).coerceAtLeast(1)
        val rv = RemoteViews(context.packageName, R.layout.widget_vinyl)

        rv.setImageViewBitmap(
            R.id.widget_vinyl_bg,
            WidgetBitmaps.vinylBackground(cover, side, bgRadiusPx),
        )
        applyOffline(rv, R.id.widget_vinyl_offline, s.isOffline)

        if (s.isIdle) {
            rv.setViewVisibility(R.id.widget_vinyl_disc, View.GONE)
            rv.setViewVisibility(R.id.widget_vinyl_controls, View.GONE)
            rv.setViewVisibility(R.id.widget_vinyl_idle, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.widget_vinyl_root, WidgetTransport.openApp(context))
            mgr.updateAppWidget(id, rv)
            return
        }

        rv.setViewVisibility(R.id.widget_vinyl_idle, View.GONE)
        rv.setViewVisibility(R.id.widget_vinyl_disc, View.VISIBLE)
        rv.setViewVisibility(R.id.widget_vinyl_controls, View.VISIBLE)
        // STATİK disk (kapak + oluk + accent label + delik) — dönmez, olay başına baked
        rv.setImageViewBitmap(
            R.id.widget_vinyl_disc,
            WidgetBitmaps.vinylDisc(cover, (side * 0.82f).toInt(), accent),
        )
        rv.setImageViewResource(
            R.id.widget_vinyl_playpause,
            if (s.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        bindTransport(
            rv, s,
            playPauseId = R.id.widget_vinyl_playpause,
            nextId = R.id.widget_vinyl_next,
            prevId = R.id.widget_vinyl_prev,
            rootId = R.id.widget_vinyl_root,
        )
        mgr.updateAppWidget(id, rv)
    }

    // ---- Ortak ----

    /**
     * Sıcak → gerçek media-button transport. Soğuk → tüm butonlar app açar
     * (servis ölüyken media-button boş player başlatır). Kapak/gövde her
     * durumda player'ı açar (root click).
     */
    private fun bindTransport(
        rv: RemoteViews,
        s: WidgetSnapshot,
        playPauseId: Int,
        nextId: Int,
        prevId: Int,
        rootId: Int,
    ) {
        rv.setOnClickPendingIntent(rootId, WidgetTransport.openApp(context))
        if (s.isCold) {
            val open = WidgetTransport.openApp(context)
            rv.setOnClickPendingIntent(prevId, open)
            rv.setOnClickPendingIntent(playPauseId, open)
            rv.setOnClickPendingIntent(nextId, open)
        } else {
            rv.setOnClickPendingIntent(prevId, WidgetTransport.previous(context))
            rv.setOnClickPendingIntent(playPauseId, WidgetTransport.playPause(context))
            rv.setOnClickPendingIntent(nextId, WidgetTransport.next(context))
        }
    }

    private fun applyOffline(rv: RemoteViews, offlineId: Int, offline: Boolean) {
        rv.setViewVisibility(offlineId, if (offline) View.VISIBLE else View.GONE)
    }

    /** Hücre boyutunu (dp) options'tan px'e çevirir; okunamazsa varsayılan. */
    private fun cellPx(mgr: AppWidgetManager, id: Int, defW: Int, defH: Int): Pair<Int, Int> {
        val opts: Bundle? = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val wDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 } ?: defW
        val hDp = opts?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 } ?: defH
        val wPx = (wDp * density).toInt().coerceIn(1, WidgetContract.MAX_BG_W)
        val hPx = (hDp * density).toInt().coerceIn(1, WidgetContract.MAX_BG_H)
        return wPx to hPx
    }
}
