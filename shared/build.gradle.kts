import groovy.json.JsonSlurper
import java.io.File

// Platform-bağımsız çekirdek: model + Subsonic istemcisi + repository'ler.
// Saf Kotlin/JVM — hem Android app hem Compose Desktop buna bağlanır.
// Android API'si ve Compose bu modüle GİREMEZ.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    api(libs.retrofit.kotlinx.serialization)
    api(libs.okhttp)
    // @Inject/@Singleton — Hilt (Android) ve Koin/manuel (desktop) ikisi de okuyabilir
    api(libs.javax.inject)
    // Uzaktan kumanda WS sunucusu (KtorRcServer bu modülde; platformlar sadece shared tipini kullanır).
    // OkHttp WS istemcisi zaten yukarıdaki okhttp'de. implementation → ktor api'si dışa sızmaz.
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
}

// ---------------------------------------------------------------------------
// i18n codegen: src/main/i18n/{keys,en,tr}.json  →  GeneratedStrings.kt
// (interface Strings + object EnStrings + object TrStrings). Metin ekleme =
// JSON düzenleme; kod call-site'ları değişmez. Yanlış anahtar KULLANIMI derleme
// hatası (tipli interface). Bir dilde eksik anahtar → "⟦missing:key⟧" + build
// uyarısı; -Pi18nStrict ile build-fail. Placeholder/arity uyuşmazlığı → build-fail.
// ---------------------------------------------------------------------------
val i18nSrcDir = layout.projectDirectory.dir("src/main/i18n")
val i18nGenDir = layout.buildDirectory.dir("generated/i18n/kotlin")

val genI18n by tasks.registering {
    val keysF = i18nSrcDir.file("keys.json").asFile
    val enF = i18nSrcDir.file("en.json").asFile
    val trF = i18nSrcDir.file("tr.json").asFile
    val deF = i18nSrcDir.file("de.json").asFile
    val outDir = i18nGenDir.get().asFile
    val strict = providers.gradleProperty("i18nStrict").isPresent
    val log = logger
    inputs.files(keysF, enF, trF, deF)
    outputs.dir(outDir)
    doLast { generateI18n(keysF, enF, trF, deF, outDir, strict, log) }
}

kotlin.sourceSets["main"].kotlin.srcDir(i18nGenDir)
tasks.named("compileKotlin") { dependsOn(genI18n) }

// Kotlin string-literal kaçışı (interpolasyonsuz düz metin).
fun i18nEsc(s: String): String = buildString {
    for (c in s) when (c) {
        '\\' -> append("\\\\"); '"' -> append("\\\""); '$' -> append("\\$")
        '\n' -> append("\\n"); '\t' -> append("\\t"); '\r' -> append("\\r")
        else -> append(c)
    }
}

// "{x} y" template → Kotlin literal "${x} y"; {param}'lar declared params ⊆ değilse hata.
fun i18nTemplate(t: String, params: Set<String>, key: String, errors: MutableList<String>): String {
    val sb = StringBuilder("\"")
    var i = 0
    while (i < t.length) {
        val c = t[i]
        if (c == '{') {
            val j = t.indexOf('}', i)
            if (j > i) {
                val name = t.substring(i + 1, j)
                if (name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' }) {
                    if (name !in params) errors.add("$key: '{$name}' bildirilen parametrede yok $params")
                    sb.append("\${").append(name).append("}")
                    i = j + 1; continue
                }
            }
        }
        when (c) {
            '\\' -> sb.append("\\\\"); '"' -> sb.append("\\\""); '$' -> sb.append("\\$")
            '\n' -> sb.append("\\n"); '\t' -> sb.append("\\t"); '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
        i++
    }
    return sb.append("\"").toString()
}

@Suppress("UNCHECKED_CAST")
fun generateI18n(keysF: File, enF: File, trF: File, deF: File, outDir: File, strict: Boolean, log: org.gradle.api.logging.Logger) {
    val slurper = JsonSlurper()
    val keys = slurper.parse(keysF) as Map<String, Map<String, Any?>>
    val langs = linkedMapOf(
        "EnStrings" to (slurper.parse(enF) as Map<String, Any?>),
        "TrStrings" to (slurper.parse(trF) as Map<String, Any?>),
        "DeStrings" to (slurper.parse(deF) as Map<String, Any?>),
    )
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    fun missing(msg: String) { if (strict) errors.add(msg) else warnings.add(msg) }

    val sb = StringBuilder()
    sb.appendLine("// ÜRETİLDİ — elle DÜZENLEME. Metin için src/main/i18n/*.json'u düzenle (genI18n üretir).")
    sb.appendLine("package com.ozgen.navicloud.i18n")
    sb.appendLine()
    keys.values.mapNotNull { it["enum"] as? String }.toSortedSet().forEach { sb.appendLine("import $it") }
    sb.appendLine()

    sb.appendLine("interface Strings {")
    for ((k, meta) in keys) {
        when {
            meta.containsKey("enum") -> {
                val simple = (meta["enum"] as String).substringAfterLast('.')
                sb.appendLine("    fun $k(${meta["param"]}: $simple): String")
            }
            meta.containsKey("params") -> {
                val sig = (meta["params"] as List<String>).joinToString(", ") {
                    val (n, t) = it.split(":"); "$n: $t"
                }
                sb.appendLine("    fun $k($sig): String")
            }
            else -> sb.appendLine("    val $k: String")
        }
    }
    sb.appendLine("}")
    sb.appendLine()

    for ((obj, vals) in langs) {
        sb.appendLine("object $obj : Strings {")
        for ((k, meta) in keys) {
            if (!vals.containsKey(k)) missing("$obj: '$k' eksik")
            when {
                meta.containsKey("enum") -> {
                    val simple = (meta["enum"] as String).substringAfterLast('.')
                    val pn = meta["param"] as String
                    val caseVals = (vals[k] as? Map<String, String>) ?: emptyMap()
                    sb.appendLine("    override fun $k($pn: $simple): String = when ($pn) {")
                    for (case in meta["cases"] as List<String>) {
                        val v = caseVals[case] ?: run { missing("$obj: '$k.$case' eksik"); "⟦missing:$k.$case⟧" }
                        sb.appendLine("        $simple.$case -> \"${i18nEsc(v)}\"")
                    }
                    sb.appendLine("    }")
                }
                meta.containsKey("params") -> {
                    val ps = meta["params"] as List<String>
                    val sig = ps.joinToString(", ") { val (n, t) = it.split(":"); "$n: $t" }
                    val names = ps.map { it.split(":")[0] }.toSet()
                    val tmpl = (vals[k] as? String) ?: "⟦missing:$k⟧"
                    sb.appendLine("    override fun $k($sig): String = ${i18nTemplate(tmpl, names, k, errors)}")
                }
                else -> {
                    val v = (vals[k] as? String) ?: "⟦missing:$k⟧"
                    sb.appendLine("    override val $k: String = \"${i18nEsc(v)}\"")
                }
            }
        }
        sb.appendLine("}")
        sb.appendLine()
    }

    warnings.forEach { log.warn("i18n UYARI: $it") }
    if (errors.isNotEmpty()) throw org.gradle.api.GradleException("i18n hataları:\n" + errors.joinToString("\n"))

    val pkgDir = File(outDir, "com/ozgen/navicloud/i18n").apply { mkdirs() }
    File(pkgDir, "GeneratedStrings.kt").writeText(sb.toString(), Charsets.UTF_8)
    log.lifecycle("i18n: ${keys.size} anahtar × ${langs.size} dil üretildi" +
        if (warnings.isNotEmpty()) " (${warnings.size} uyarı)" else "")
}
