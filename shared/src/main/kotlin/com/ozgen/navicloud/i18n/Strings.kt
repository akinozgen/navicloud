package com.ozgen.navicloud.i18n

import java.util.Locale

// NOT: `interface Strings` + `EnStrings` + `TrStrings` artık ELLE yazılmaz —
// src/main/i18n/{keys,en,tr}.json'dan `genI18n` Gradle task'ı GeneratedStrings.kt
// olarak üretir. Yeni metin = JSON düzenle. Burada yalnız dil seçimi mantığı kalır.

/**
 * Uygulama dili. SYSTEM → JVM varsayılan locale'ine göre TR/DE/EN çözülür.
 * Ayarlardan elle TURKISH/ENGLISH/GERMAN seçilebilir.
 */
enum class AppLanguage { SYSTEM, TURKISH, ENGLISH, GERMAN }

/** SYSTEM'i somut dile indirger (locale dili tr→TR, de→GERMAN, aksi EN). */
fun AppLanguage.resolved(): AppLanguage = when (this) {
    AppLanguage.SYSTEM -> when (Locale.getDefault().language) {
        "tr" -> AppLanguage.TURKISH
        "de" -> AppLanguage.GERMAN
        else -> AppLanguage.ENGLISH
    }
    else -> this
}

fun stringsFor(language: AppLanguage): Strings = when (language.resolved()) {
    AppLanguage.TURKISH -> TrStrings
    AppLanguage.GERMAN -> DeStrings
    else -> EnStrings
}

// Kayıtlı tercih yoksa varsayılan İngilizce (SYSTEM değil) — uluslararası varsayılan.
fun appLanguageOf(name: String?): AppLanguage =
    runCatching { AppLanguage.valueOf(name ?: "") }.getOrDefault(AppLanguage.ENGLISH)

/**
 * Compose dışı (toast, tepsi menüsü, pencere) kod için dil erişim noktası.
 * Compose tarafı LocalStrings kullanır. Uygulama açılışında/ayar değişince güncellenir.
 */
object I18n {
    @Volatile
    var language: AppLanguage = AppLanguage.ENGLISH
    val strings: Strings get() = stringsFor(language)
}
