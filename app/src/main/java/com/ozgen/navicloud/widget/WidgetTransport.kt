package com.ozgen.navicloud.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.ozgen.navicloud.MainActivity
import com.ozgen.navicloud.playback.PlaybackService

/**
 * Widget transport PendingIntent'leri.
 *
 * - **Sıcak** (servis yaşıyor): media-button intent → PlaybackService. Media3
 *   MediaSession bunu prev/play-pause/next'e yönlendirir (Media3 bump gerekmez).
 * - **Soğuk / idle**: app'i player açık şekilde başlat (ACTION_OPEN_PLAYER).
 *   Servis ölüyken media-button gönderilse boş player başlar; onun yerine
 *   uygulama açılır, MediaController bağlanınca kalıcı kuyruk geri yüklenir.
 */
object WidgetTransport {

    private fun mediaButton(context: Context, keyCode: Int, reqCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(context, PlaybackService::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        return PendingIntent.getForegroundService(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun playPause(context: Context): PendingIntent =
        mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, WidgetContract.REQ_PLAY_PAUSE)

    fun next(context: Context): PendingIntent =
        mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT, WidgetContract.REQ_NEXT)

    fun previous(context: Context): PendingIntent =
        mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, WidgetContract.REQ_PREV)

    /** Uygulamayı player açık başlatır (bildirim tap'iyle aynı intent). */
    fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = PlaybackService.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            WidgetContract.REQ_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
