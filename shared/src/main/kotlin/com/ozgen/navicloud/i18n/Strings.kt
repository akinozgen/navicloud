package com.ozgen.navicloud.i18n

import java.util.Locale

// NOT: `interface Strings` + `EnStrings` + `TrStrings` artık ELLE yazılmaz —
// src/main/i18n/{keys,en,tr}.json'dan `genI18n` Gradle task'ı GeneratedStrings.kt
// olarak üretir. Yeni metin = JSON düzenle. Burada yalnız dil seçimi mantığı kalır.

/**
 * Uygulama dili. SYSTEM → JVM varsayılan locale'ine göre TR/EN çözülür.
 * Ayarlardan elle TURKISH/ENGLISH seçilebilir.
 */
enum class AppLanguage { SYSTEM, TURKISH, ENGLISH }

/** SYSTEM'i somut dile indirger (Locale.getDefault().language == "tr" → TR, aksi EN). */
fun AppLanguage.resolved(): AppLanguage = when (this) {
    AppLanguage.SYSTEM -> if (Locale.getDefault().language == "tr") AppLanguage.TURKISH else AppLanguage.ENGLISH
    else -> this
}

fun stringsFor(language: AppLanguage): Strings = when (language.resolved()) {
    AppLanguage.TURKISH -> TrStrings
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
