package com.ozgen.navicloud.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt olmayan sınıflara (AppWidgetProvider = BroadcastReceiver) singleton
 * bağımlılık köprüsü. Provider'lar [dagger.hilt.android.EntryPointAccessors]
 * ile uygulama grafiğinden [WidgetUpdater]'ı çeker.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetUpdater(): WidgetUpdater
}
