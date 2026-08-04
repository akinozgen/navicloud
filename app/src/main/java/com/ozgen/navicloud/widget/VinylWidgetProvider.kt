package com.ozgen.navicloud.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors

/**
 * Kare "Vinyl" widget'ı (2×2) — masaüstü MiniVinylWindow.kt karşılığı.
 * STATİK disk (dönmez); çizim [WidgetRenderer]'da, tetikleme [WidgetUpdater]'da.
 */
class VinylWidgetProvider : AppWidgetProvider() {

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
        updater(context).requestRender()
    }
}
