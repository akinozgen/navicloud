package com.ozgen.navicloud.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.ui.i18n.LocalStrings

/** Kullanılan açık kaynak bir bileşen. */
data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String,
    /** Ek açıklama (ör. gömülü/dinamik bağlı olma durumu). */
    val note: String? = null,
)

/** Her iki platformda ortak (KMP/paylaşılan UI yığını). */
val commonLicenses: List<LicenseEntry> = listOf(
    LicenseEntry("Jetpack Compose Multiplatform", "Apache-2.0", "https://github.com/JetBrains/compose-multiplatform"),
    LicenseEntry("Kotlin", "Apache-2.0", "https://github.com/JetBrains/kotlin"),
    LicenseEntry("kotlinx.coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    LicenseEntry("kotlinx.serialization", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
    LicenseEntry("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    LicenseEntry("OkHttp", "Apache-2.0", "https://github.com/square/okhttp"),
    LicenseEntry("AndroidX Lifecycle", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    LicenseEntry("Navigation Compose", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    LicenseEntry("Reorderable", "Apache-2.0", "https://github.com/Calvin-LL/Reorderable"),
)

/** Yalnız Android. */
val androidLicenses: List<LicenseEntry> = listOf(
    LicenseEntry("AndroidX Media3 (ExoPlayer)", "Apache-2.0", "https://github.com/androidx/media"),
    LicenseEntry("Dagger Hilt", "Apache-2.0", "https://github.com/google/dagger"),
    LicenseEntry("AndroidX Room", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
    LicenseEntry("AndroidX DataStore", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    LicenseEntry("Retrofit", "Apache-2.0", "https://github.com/square/retrofit"),
    LicenseEntry("AndroidX Palette", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/palette"),
    LicenseEntry("AndroidX Core / Activity", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
)

/** Yalnız masaüstü (Windows). */
val desktopLicenses: List<LicenseEntry> = listOf(
    // Paketlenen derleme (zhongfly/mpv-winbuild) GPL ffmpeg gömülü → GPL, LGPL değil.
    // NaviCloud da bu yüzden GPLv3. Kaynak: build betiği repo'da.
    LicenseEntry("libmpv (mpv)", "GPL-2.0 or later", "https://github.com/mpv-player/mpv"),
    LicenseEntry("FFmpeg", "GPL-2.0 or later", "https://ffmpeg.org"),
    LicenseEntry("Java Native Access (JNA)", "Apache-2.0 / LGPL-2.1", "https://github.com/java-native-access/jna"),
    LicenseEntry("windows-rs", "MIT / Apache-2.0", "https://github.com/microsoft/windows-rs"),
)

/** Bileşen adına göre yerelleştirilmiş açıklama (data listesi statik olduğu için burada çözülür). */
@Composable
private fun licenseNote(name: String): String? {
    val s = LocalStrings.current
    return when (name) {
        "libmpv (mpv)" -> s.licensesMpvNote
        "FFmpeg" -> s.licensesFfmpegNote
        "Java Native Access (JNA)" -> s.licensesJnaNote
        "windows-rs" -> s.licensesWindowsRsNote
        else -> null
    }
}

/**
 * Açık kaynak lisansları ekranı. Her platform kendi bileşen listesini geçer
 * (ortak + platforma özel). Satıra dokunmak projenin sayfasını tarayıcıda açar.
 */
@Composable
fun LicensesScreen(entries: List<LicenseEntry>, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val strings = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.commonBack)
            }
            Text(strings.licensesTitle, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            strings.licensesIntro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        entries.forEach { e ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { uriHandler.openUri(e.url) } }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        e.license,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val note = licenseNote(e.name)
                    if (note != null) {
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        e.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = strings.licensesOpen,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
