package com.ozgen.navicloud.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors

/**
 * Geniş "Bar" widget'ı (4×2) — masaüstü MiniPlayer.kt karşılığı.
 *
 * Çizim mantığı [WidgetRenderer]'da; provider yalnız güncellemeyi tetikler.
 * updatePeriodMillis=0 → sistem periyodik güncellemez; [WidgetUpdater] canlı
 * player olaylarında sürer, burada eklendi/boyut değişti gibi durumlarda çekilir.
 */
class BarWidgetProvider : AppWidgetProvider() {

    private fun updater(context: Context): WidgetUpdater =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        ).widgetUpdater()

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        updater(context).requestRender()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        id: Int,
        newOptions: Bundle?,
    ) {
        // Yeniden boyutlandırma → zemin bitmap'i yeni hücre px'ine göre yeniden pişirilmeli
        updater(context).requestRender()
    }
}
