package com.ozgen.navicloud.i18n

import com.ozgen.navicloud.audio.EqPreset
import com.ozgen.navicloud.audio.ReverbPreset
import com.ozgen.navicloud.core.model.HomeSectionType
import com.ozgen.navicloud.data.StreamQuality
import java.util.Locale

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

fun appLanguageOf(name: String?): AppLanguage =
    runCatching { AppLanguage.valueOf(name ?: "") }.getOrDefault(AppLanguage.SYSTEM)

/**
 * Compose dışı (toast, tepsi menüsü, pencere) kod için dil erişim noktası.
 * Compose tarafı LocalStrings kullanır. Uygulama açılışında/ayar değişince güncellenir.
 */
object I18n {
    @Volatile
    var language: AppLanguage = AppLanguage.SYSTEM
    val strings: Strings get() = stringsFor(language)
}

/**
 * Tüm kullanıcı-yüzü metinlerin tek kaynağı. Parametreli metinler fonksiyon,
 * sabitler val. TR ve EN implementasyonları aynı sırayı korur.
 */
interface Strings {
    // --- Ortak (birden çok yerde) ---
    val commonBack: String
    val commonDelete: String
    val commonSave: String
    val commonCancel: String
    val commonClose: String
    val commonClear: String
    val commonRetry: String
    val commonPlay: String
    val commonPause: String
    val commonShuffle: String
    val commonNext: String
    val commonPrevious: String
    val commonRepeat: String
    val commonRefresh: String
    val commonSettings: String
    val commonAdd: String
    val commonDownload: String
    val commonOptions: String
    val commonPlayNext: String
    val commonAddToQueue: String
    val commonGoToAlbum: String
    val commonFavorite: String
    val commonDownloadQueued: String
    val commonConnectionLost: String
    val commonPassword: String
    fun commonSongCount(count: Int): String

    // --- Ana sayfa ---
    val homeGreetingMorning: String
    val homeGreetingAfternoon: String
    val homeGreetingEvening: String
    val homeServers: String
    val homeSeeAll: String

    // --- Albüm ---
    val albumLoadError: String
    val albumType: String
    fun albumFooterSummary(count: Int, duration: String): String

    // --- Sanatçı ---
    val artistLoadError: String
    fun artistAlbumCount(count: Int): String
    val artistPopular: String
    val artistAlbums: String
    val artistSimilar: String
    val artistAbout: String
    val artistBioCollapse: String
    val artistBioExpand: String

    // --- Kitaplık ---
    val libraryTabPlaylists: String
    val libraryTabAlbums: String
    val libraryTabArtists: String
    val libraryTabSongs: String
    val libraryTabFavorites: String
    val libraryTabDownloads: String
    val libraryCtxShuffleMix: String
    val libraryCtxAllSongs: String
    val libraryTitle: String
    val librarySearchHint: String
    fun libraryPlaylistSubtitle(count: Int): String
    val librarySortRecent: String
    val librarySortAlpha: String
    val libraryView: String
    fun libraryAlbumSubtitle(artist: String): String
    fun libraryArtistSubtitle(count: Int): String
    val libraryDownloadsGrouped: String
    val libraryDownloadsFlat: String
    val libraryToggleGrouping: String
    val libraryUnknownAlbum: String
    val libraryDownloadsEmpty: String
    val libraryShuffleAll: String

    // --- Giriş ---
    val loginConnectionFailed: String
    val loginSubtitle: String
    val loginServerName: String
    val loginServerUrl: String
    val loginServerUrlHint: String
    val loginUsername: String
    val loginPassword: String
    val loginConnect: String

    // --- Çalma listesi ---
    val playlistLoadError: String
    fun playlistHeaderSubtitle(count: Int): String

    // --- Arama ---
    val searchAll: String
    val searchSongs: String
    val searchAlbums: String
    val searchArtists: String
    val searchHint: String
    val searchRecent: String
    val searchRemoveRecent: String

    // --- Lisanslar ---
    val licensesLgplOrLater: String
    val licensesMpvNote: String
    val licensesFfmpegNote: String
    val licensesJnaNote: String
    val licensesWindowsRsNote: String
    val licensesTitle: String
    val licensesIntro: String
    val licensesOpen: String

    // --- Oynatıcı ---
    val playerNowPlaying: String
    val playerSelectDevice: String
    val playerMiniPlayer: String
    fun playerSleepMinutesShort(minutes: Int): String
    val playerSleepEndOfTrack: String
    val playerSleepEndOfQueue: String
    val playerSleepTimer: String
    val playerTrackInfo: String
    val playerLyrics: String
    val playerMute: String
    val playerUnmute: String
    fun playerUpNext(title: String): String
    val playerQueue: String
    val playerStop: String
    val playerPlayingFrom: String
    val playerAutoplay: String
    val playerAutoplayDesc: String
    val playerDragReorder: String
    val playerSoundEqualizer: String
    val playerSleepTimerOn: String
    val playerSleepRemaining: String
    val playerSleepEndOfTrackTitle: String
    val playerSleepWillStop: String
    val playerSleepEndOfQueueTitle: String
    val playerSleepCancel: String
    fun playerSleepMinutes(m: Int): String
    val playerLyricsLoading: String
    val playerLyricsEmpty: String
    val playerOfflineNoDownloads: String

    // --- Cihaz seçici (uzaktan kumanda) ---
    val devicePickerThisDevice: String
    val devicePickerEditName: String
    val devicePickerBusy: String
    val devicePickerPlayingHere: String
    val devicePickerConnecting: String
    val devicePickerAddedManually: String
    val devicePickerForgetDevice: String
    val devicePickerNoDevices: String
    val devicePickerConnectFailed: String
    val devicePickerAddByIp: String
    val devicePickerDeviceName: String
    val devicePickerAddByIpHint: String
    val devicePickerSelected: String

    // --- Şarkı öğesi / menüsü ---
    val songItemMenu: String
    val songMenuAddToPlaylist: String
    val songMenuGoToArtist: String
    val songMenuRemoveFromQueue: String
    val songMenuRemoveDownload: String
    val songMenuRemoveFavorite: String
    val songMenuAddFavorite: String
    val songMenuInfo: String
    val songMenuToastPlayNext: String
    val songMenuToastAddedToQueue: String
    val songMenuPlaylistsLoading: String
    fun songMenuToastAddedToPlaylist(name: String): String
    val songMenuToastAddFailed: String

    // --- Parça bilgisi ---
    val trackInfoFormat: String
    val trackInfoBitRate: String
    val trackInfoSampleRate: String
    val trackInfoBitDepth: String
    val trackInfoChannels: String
    val trackInfoDuration: String
    val trackInfoSize: String
    val trackInfoStream: String
    val trackInfoStreamOriginal: String
    val trackInfoChannelMono: String
    val trackInfoChannelStereo: String
    fun trackInfoChannelOther(n: Int): String

    // --- Koleksiyon başlığı ---
    val collectionShufflePlay: String
    val collectionRemoveDownloads: String
    val collectionDownloaded: String

    // --- Ses efektleri ---
    val audioFxBassBoost: String
    val audioFxWidth: String
    val audioFxAmbience: String
    val audioFxNotSupported: String
    val audioFxLoudness: String
    val audioFxSheetTitle: String
    val audioFxThisDeviceOnly: String
    val audioFxEqualizer: String

    // --- Kök: sekmeler + uzaktan kumanda bantları/dialogları ---
    val rootTabHome: String
    val rootTabSearch: String
    val rootTabLibrary: String
    val rootRemoteDeviceFallback: String
    fun rootControllingRemote(peerName: String): String
    val rootSwitchToThisDevice: String
    val rootControlledRemotely: String
    val rootTakeControl: String
    val rootPairingCodeTitle: String
    val rootEnterCodeOnConnecting: String
    val rootPassword: String
    val rootPairing: String
    fun rootEnterPasswordFor(peerName: String): String
    fun rootEnterCodeFrom(peerName: String): String
    val rootConnect: String
    val rootPair: String
    val rootResumeTitle: String
    val rootResume: String

    // --- Ayarlar (Android + masaüstü ortak) ---
    val settingsTitle: String
    val settingsServersSection: String
    val settingsAddServer: String
    val settingsPlaybackSection: String
    val settingsStreamQuality: String
    val settingsOfflineMode: String
    val settingsOfflineModeDesc: String
    val settingsPrefetch: String
    val settingsPrefetchDesc: String
    val settingsPrefetchWifiOnly: String
    val settingsPrefetchWifiOnlyDesc: String
    val settingsInternetLyrics: String
    val settingsInternetLyricsDesc: String
    val settingsLibrarySection: String
    val settingsScanLibrary: String
    fun settingsScanScanning(items: Long): String
    fun settingsScanDone(items: Long): String
    val settingsScanIdleDesc: String
    val settingsScanFull: String
    val settingsCacheSection: String
    val settingsStreamCache: String
    val settingsImageCache: String
    fun settingsImageCacheDesc(size: String): String
    val settingsDownloadsSection: String
    val settingsDownloadWifiOnly: String
    val settingsDownloadWifiOnlyDesc: String
    val settingsDownloadDeleteAll: String
    val settingsRemoteControlSection: String
    val settingsRemoteControlDesc: String
    val settingsAboutSection: String
    val settingsLicensesDesc: String
    val settingsStreamQualityDialogNote: String
    val settingsCacheLimitDialogTitle: String
    val settingsCacheLimitDialogNote: String
    val settingsClearDownloadsDialogTitle: String
    fun settingsClearDownloadsBody(count: Int, size: String): String
    val settingsAppSection: String
    val settingsLanguage: String

    // --- Dil adları (her dilde kendi adıyla) ---
    val languageSystem: String
    val languageTurkish: String
    val languageEnglish: String

    // --- İndirme durumu (kitaplık + ayarlar + masaüstü) ---
    fun downloadInProgress(title: String): String
    fun downloadWaitingForWifi(title: String): String
    fun downloadQueuedSuffix(count: Int): String

    // --- Masaüstü ayarlar (ek) ---
    val dsettingsAudioEngine: String
    val dsettingsAudioBackendDefault: String
    val dsettingsCloseToTrayTitle: String
    val dsettingsCloseToTraySubtitle: String
    val dsettingsLicensesSubtitle: String

    // --- Masaüstü tepsi menüsü ---
    val trayShow: String
    val trayExit: String

    // --- Masaüstü mini oynatıcı ---
    val miniAlwaysOnTop: String
    val miniVinylView: String
    val miniExpand: String
    val miniStandardView: String

    // --- Cihaz varsayılan adı ---
    val deviceDefaultDesktopName: String

    // --- Paylaşılan enum etiketleri (UI'da görünür, gösterimde çözülür) ---
    fun streamQualityLabel(quality: StreamQuality): String
    fun eqPresetLabel(preset: EqPreset): String
    fun reverbPresetLabel(preset: ReverbPreset): String
    fun homeSectionTitle(type: HomeSectionType): String
    val offlineContentUnavailable: String
}

/** Türkçe. */
object TrStrings : Strings {
    override val commonBack = "Geri"
    override val commonDelete = "Sil"
    override val commonSave = "Kaydet"
    override val commonCancel = "Vazgeç"
    override val commonClose = "Kapat"
    override val commonClear = "Temizle"
    override val commonRetry = "Tekrar dene"
    override val commonPlay = "Çal"
    override val commonPause = "Duraklat"
    override val commonShuffle = "Karıştır"
    override val commonNext = "Sonraki"
    override val commonPrevious = "Önceki"
    override val commonRepeat = "Tekrar"
    override val commonRefresh = "Yenile"
    override val commonSettings = "Ayarlar"
    override val commonAdd = "Ekle"
    override val commonDownload = "İndir"
    override val commonOptions = "Seçenekler"
    override val commonPlayNext = "Sıradakine ekle"
    override val commonAddToQueue = "Kuyruğa ekle"
    override val commonGoToAlbum = "Albüme git"
    override val commonFavorite = "Favori"
    override val commonDownloadQueued = "İndirme kuyruğa alındı"
    override val commonConnectionLost = "Bağlantı koptu"
    override val commonPassword = "Parola"
    override fun commonSongCount(count: Int) = "$count şarkı"

    override val homeGreetingMorning = "Günaydın"
    override val homeGreetingAfternoon = "İyi günler"
    override val homeGreetingEvening = "İyi akşamlar"
    override val homeServers = "Sunucular"
    override val homeSeeAll = "Tümünü gör"

    override val albumLoadError = "Albüm yüklenemedi"
    override val albumType = "Albüm"
    override fun albumFooterSummary(count: Int, duration: String) = "$count şarkı • $duration"

    override val artistLoadError = "Sanatçı yüklenemedi"
    override fun artistAlbumCount(count: Int) = "$count albüm"
    override val artistPopular = "Popüler"
    override val artistAlbums = "Albümler"
    override val artistSimilar = "Benzer sanatçılar"
    override val artistAbout = "Hakkında"
    override val artistBioCollapse = "Daha az göster"
    override val artistBioExpand = "Devamını oku"

    override val libraryTabPlaylists = "Çalma Listeleri"
    override val libraryTabAlbums = "Albümler"
    override val libraryTabArtists = "Sanatçılar"
    override val libraryTabSongs = "Şarkılar"
    override val libraryTabFavorites = "Favoriler"
    override val libraryTabDownloads = "İndirilenler"
    override val libraryCtxShuffleMix = "Karışık çalma"
    override val libraryCtxAllSongs = "Tüm şarkılar"
    override val libraryTitle = "Kitaplık"
    override val librarySearchHint = "Bu listede ara"
    override fun libraryPlaylistSubtitle(count: Int) = "Çalma listesi • $count şarkı"
    override val librarySortRecent = "Son eklenen"
    override val librarySortAlpha = "Ada göre"
    override val libraryView = "Görünüm"
    override fun libraryAlbumSubtitle(artist: String) = "Albüm • $artist"
    override fun libraryArtistSubtitle(count: Int) = "Sanatçı • $count albüm"
    override val libraryDownloadsGrouped = "Albüme göre gruplu"
    override val libraryDownloadsFlat = "Düz liste"
    override val libraryToggleGrouping = "Gruplamayı değiştir"
    override val libraryUnknownAlbum = "Bilinmeyen Albüm"
    override val libraryDownloadsEmpty = "Henüz indirilen şarkı yok. Albüm veya çalma listesi sayfasındaki indirme düğmesini kullan."
    override val libraryShuffleAll = "Tümünü karıştır"

    override val loginConnectionFailed = "Bağlantı başarısız"
    override val loginSubtitle = "Navidrome sunucuna bağlan"
    override val loginServerName = "Sunucu adı (isteğe bağlı)"
    override val loginServerUrl = "Sunucu adresi"
    override val loginServerUrlHint = "http://sunucu-adresi:port"
    override val loginUsername = "Kullanıcı adı"
    override val loginPassword = "Şifre"
    override val loginConnect = "Bağlan"

    override val playlistLoadError = "Çalma listesi yüklenemedi"
    override fun playlistHeaderSubtitle(count: Int) = "Çalma listesi • $count şarkı"

    override val searchAll = "Tümü"
    override val searchSongs = "Şarkılar"
    override val searchAlbums = "Albümler"
    override val searchArtists = "Sanatçılar"
    override val searchHint = "Şarkı, albüm veya sanatçı ara"
    override val searchRecent = "Son aramalar"
    override val searchRemoveRecent = "Kaldır"

    override val licensesLgplOrLater = "LGPL-2.1 veya sonrası"
    override val licensesMpvNote = "Ses motoru. libmpv-2.dll olarak gömülü ve dinamik bağlı; DLL değiştirilebilir."
    override val licensesFfmpegNote = "libmpv içinde kod çözme/çözümleme için kullanılır."
    override val licensesJnaNote = "libmpv'ye JVM'den erişim."
    override val licensesWindowsRsNote = "Medya tuşları / kontrol merkezi (SMTC) yardımcısında kullanılır."
    override val licensesTitle = "Açık kaynak lisansları"
    override val licensesIntro = "NaviCloud aşağıdaki açık kaynak yazılımlarla mümkün oldu. Teşekkürler."
    override val licensesOpen = "Aç"

    override val playerNowPlaying = "Şu an çalıyor"
    override val playerSelectDevice = "Cihaz seç"
    override val playerMiniPlayer = "Mini oynatıcı"
    override fun playerSleepMinutesShort(minutes: Int) = "$minutes dk"
    override val playerSleepEndOfTrack = "Parça sonu"
    override val playerSleepEndOfQueue = "Kuyruk sonu"
    override val playerSleepTimer = "Uyku zamanlayıcı"
    override val playerTrackInfo = "Parça bilgisi"
    override val playerLyrics = "Şarkı sözleri"
    override val playerMute = "Sessize al"
    override val playerUnmute = "Sesi aç"
    override fun playerUpNext(title: String) = "Sıradaki: $title"
    override val playerQueue = "Kuyruk"
    override val playerStop = "Durdur"
    override val playerPlayingFrom = "Şuradan çalınıyor:"
    override val playerAutoplay = "Otomatik oynatma"
    override val playerAutoplayDesc = "Kuyruk bitince benzer içerikle devam eder"
    override val playerDragReorder = "Sürükle"
    override val playerSoundEqualizer = "Ses / Ekolayzer"
    override val playerSleepTimerOn = "Uyku zamanlayıcı • açık"
    override val playerSleepRemaining = "kaldı"
    override val playerSleepEndOfTrackTitle = "Parça bitince"
    override val playerSleepWillStop = "duracak"
    override val playerSleepEndOfQueueTitle = "Kuyruk bitince"
    override val playerSleepCancel = "İptal et"
    override fun playerSleepMinutes(m: Int) = "$m dakika"
    override val playerLyricsLoading = "Sözler yükleniyor…"
    override val playerLyricsEmpty = "Bu şarkı için söz bulunamadı"
    override val playerOfflineNoDownloads = "Offline mod: bu içerikte indirilmiş şarkı yok"

    override val devicePickerThisDevice = "Bu cihaz"
    override val devicePickerEditName = "Adı düzenle"
    override val devicePickerBusy = "Meşgul"
    override val devicePickerPlayingHere = "Buradan çalıyor"
    override val devicePickerConnecting = "Bağlanıyor…"
    override val devicePickerAddedManually = "Elle eklendi"
    override val devicePickerForgetDevice = "Cihazı unut"
    override val devicePickerNoDevices = "Ağda başka cihaz yok."
    override val devicePickerConnectFailed = "Bağlanılamadı."
    override val devicePickerAddByIp = "IP ile ekle"
    override val devicePickerDeviceName = "Cihaz adı"
    override val devicePickerAddByIpHint = "Cihaz listede yoksa IP adresini yaz."
    override val devicePickerSelected = "Seçili"

    override val songItemMenu = "Şarkı menüsü"
    override val songMenuAddToPlaylist = "Çalma listesine ekle"
    override val songMenuGoToArtist = "Sanatçıya git"
    override val songMenuRemoveFromQueue = "Kuyruktan kaldır"
    override val songMenuRemoveDownload = "İndirileni kaldır"
    override val songMenuRemoveFavorite = "Favorilerden çıkar"
    override val songMenuAddFavorite = "Favorilere ekle"
    override val songMenuInfo = "Bilgi"
    override val songMenuToastPlayNext = "Sıradakine eklendi"
    override val songMenuToastAddedToQueue = "Kuyruğa eklendi"
    override val songMenuPlaylistsLoading = "Çalma listeleri yükleniyor…"
    override fun songMenuToastAddedToPlaylist(name: String) = "\"$name\" listesine eklendi"
    override val songMenuToastAddFailed = "Eklenemedi"

    override val trackInfoFormat = "Format"
    override val trackInfoBitRate = "Bit hızı"
    override val trackInfoSampleRate = "Örnekleme"
    override val trackInfoBitDepth = "Bit derinliği"
    override val trackInfoChannels = "Kanal"
    override val trackInfoDuration = "Süre"
    override val trackInfoSize = "Boyut"
    override val trackInfoStream = "Akış"
    override val trackInfoStreamOriginal = "Orijinal • transcode yok"
    override val trackInfoChannelMono = "1 • Mono"
    override val trackInfoChannelStereo = "2 • Stereo"
    override fun trackInfoChannelOther(n: Int) = "$n kanal"

    override val collectionShufflePlay = "Karıştırarak çal"
    override val collectionRemoveDownloads = "İndirilenleri kaldır"
    override val collectionDownloaded = "İndirildi"

    override val audioFxBassBoost = "Bas güçlendirme"
    override val audioFxWidth = "Genişlik"
    override val audioFxAmbience = "Ortam"
    override val audioFxNotSupported = "Cihaz desteklemiyor"
    override val audioFxLoudness = "Ses kazancı"
    override val audioFxSheetTitle = "Ses efektleri"
    override val audioFxThisDeviceOnly = "Yalnız bu cihazda çalar"
    override val audioFxEqualizer = "Ekolayzer"

    override val rootTabHome = "Ana Sayfa"
    override val rootTabSearch = "Ara"
    override val rootTabLibrary = "Kitaplık"
    override val rootRemoteDeviceFallback = "Uzak cihaz"
    override fun rootControllingRemote(peerName: String) = "$peerName kumanda ediliyor"
    override val rootSwitchToThisDevice = "Bu cihaza dön"
    override val rootControlledRemotely = "Bu cihaz uzaktan kumanda ediliyor"
    override val rootTakeControl = "Kumandayı al"
    override val rootPairingCodeTitle = "Eşleştirme kodu"
    override val rootEnterCodeOnConnecting = "Bağlanan cihaza bu kodu gir:"
    override val rootPassword = "Parola"
    override val rootPairing = "Eşleştirme"
    override fun rootEnterPasswordFor(peerName: String) = "$peerName için parolayı gir:"
    override fun rootEnterCodeFrom(peerName: String) = "$peerName ekranındaki kodu gir:"
    override val rootConnect = "Bağlan"
    override val rootPair = "Eşleştir"
    override val rootResumeTitle = "Kaldığın yerden devam et"
    override val rootResume = "Devam"

    override val settingsTitle = "Ayarlar"
    override val settingsServersSection = "Sunucular"
    override val settingsAddServer = "Sunucu ekle"
    override val settingsPlaybackSection = "Çalma"
    override val settingsStreamQuality = "Akış kalitesi"
    override val settingsOfflineMode = "Offline mod"
    override val settingsOfflineModeDesc = "Yalnızca indirilenlerden çalar, ağı kullanmaz"
    override val settingsPrefetch = "Sıradakini önceden yükle"
    override val settingsPrefetchDesc = "Şarkı geçişleri takılmadan başlar"
    override val settingsPrefetchWifiOnly = "Ön yüklemeyi Wi-Fi ile sınırla"
    override val settingsPrefetchWifiOnlyDesc = "Hücresel veride önceden yükleme yapılmaz"
    override val settingsInternetLyrics = "İnternet sözleri"
    override val settingsInternetLyricsDesc = "Sunucuda söz yoksa LRCLIB'ten getir (senkron destekli)"
    override val settingsLibrarySection = "Kütüphane"
    override val settingsScanLibrary = "Kütüphaneyi tara"
    override fun settingsScanScanning(items: Long) = "Taranıyor… $items öğe"
    override fun settingsScanDone(items: Long) = "Tamamlandı • $items öğe"
    override val settingsScanIdleDesc = "Sunucuda yeni dosyaları bulur"
    override val settingsScanFull = "Tam tarama"
    override val settingsCacheSection = "Önbellek"
    override val settingsStreamCache = "Akış önbelleği"
    override val settingsImageCache = "Görsel önbelleği"
    override fun settingsImageCacheDesc(size: String) = "$size • kapak görselleri"
    override val settingsDownloadsSection = "İndirmeler"
    override val settingsDownloadWifiOnly = "Sadece Wi-Fi'de indir"
    override val settingsDownloadWifiOnlyDesc = "Hücresel ağda indirmeler Wi-Fi'yi bekler"
    override val settingsDownloadDeleteAll = "Tümünü sil"
    override val settingsRemoteControlSection = "Uzaktan kumanda"
    override val settingsRemoteControlDesc = "Tüm cihazlarına aynı parolayı gir; birbirine tek dokunuşla bağlanır. Boş bırakırsan bağlanırken kod sorulur."
    override val settingsAboutSection = "Hakkında"
    override val settingsLicensesDesc = "Kullanılan kütüphaneler ve lisansları"
    override val settingsStreamQualityDialogNote = "Orijinal dışındaki seçenekler veri kullanımını azaltır."
    override val settingsCacheLimitDialogTitle = "Akış önbelleği sınırı"
    override val settingsCacheLimitDialogNote = "Dinlediklerin geçici olarak saklanır; yer gerektiğinde en eskiler silinir. İndirilenler bundan etkilenmez."
    override val settingsClearDownloadsDialogTitle = "Tüm indirilenler silinsin mi?"
    override fun settingsClearDownloadsBody(count: Int, size: String) = "$count şarkı ($size) cihazdan kaldırılacak."
    override val settingsAppSection = "Uygulama"
    override val settingsLanguage = "Dil"

    override val languageSystem = "Sistem varsayılanı"
    override val languageTurkish = "Türkçe"
    override val languageEnglish = "English"

    override fun downloadInProgress(title: String) = "İndiriliyor: $title"
    override fun downloadWaitingForWifi(title: String) = "Wi-Fi bekleniyor: $title"
    override fun downloadQueuedSuffix(count: Int) = " (+$count sırada)"

    override val dsettingsAudioEngine = "Ses motoru"
    override val dsettingsAudioBackendDefault = "Varsayılan"
    override val dsettingsCloseToTrayTitle = "Kapatınca tepsiye küçült"
    override val dsettingsCloseToTraySubtitle = "Pencereyi kapatınca uygulama tepside çalmaya devam eder"
    override val dsettingsLicensesSubtitle = "Kullanılan kütüphaneler ve lisansları (libmpv dâhil)"

    override val trayShow = "Göster"
    override val trayExit = "Çıkış"

    override val miniAlwaysOnTop = "Her zaman üstte"
    override val miniVinylView = "Plak görünümü"
    override val miniExpand = "Büyüt"
    override val miniStandardView = "Standart görünüm"

    override val deviceDefaultDesktopName = "NaviCloud Masaüstü"

    override fun streamQualityLabel(quality: StreamQuality) = when (quality) {
        StreamQuality.RAW -> "Orijinal kalite"
        StreamQuality.HIGH -> "Yüksek • 320 kbps"
        StreamQuality.MEDIUM -> "Normal • 192 kbps"
        StreamQuality.LOW -> "Düşük • 128 kbps"
    }

    override fun eqPresetLabel(preset: EqPreset) = when (preset) {
        EqPreset.FLAT -> "Düz"
        EqPreset.ROCK -> "Rock"
        EqPreset.POP -> "Pop"
        EqPreset.JAZZ -> "Jazz"
        EqPreset.CLASSICAL -> "Klasik"
    }

    override fun reverbPresetLabel(preset: ReverbPreset) = when (preset) {
        ReverbPreset.SMALL_ROOM -> "Küçük oda"
        ReverbPreset.MEDIUM_ROOM -> "Orta oda"
        ReverbPreset.LARGE_ROOM -> "Büyük oda"
        ReverbPreset.MEDIUM_HALL -> "Salon"
        ReverbPreset.LARGE_HALL -> "Büyük salon"
        ReverbPreset.PLATE -> "Plaka"
    }

    override fun homeSectionTitle(type: HomeSectionType) = when (type) {
        HomeSectionType.RECENT -> "Son çalınanlar"
        HomeSectionType.NEWEST -> "Yeni eklenenler"
        HomeSectionType.FREQUENT -> "Sık çalınanlar"
        HomeSectionType.RANDOM -> "Senin için karışık"
        HomeSectionType.STARRED -> "Favori albümler"
    }

    override val offlineContentUnavailable = "Offline moddasın — bu içerik daha önce yüklenmemiş"
}

/** İngilizce. */
object EnStrings : Strings {
    override val commonBack = "Back"
    override val commonDelete = "Delete"
    override val commonSave = "Save"
    override val commonCancel = "Cancel"
    override val commonClose = "Close"
    override val commonClear = "Clear"
    override val commonRetry = "Try again"
    override val commonPlay = "Play"
    override val commonPause = "Pause"
    override val commonShuffle = "Shuffle"
    override val commonNext = "Next"
    override val commonPrevious = "Previous"
    override val commonRepeat = "Repeat"
    override val commonRefresh = "Refresh"
    override val commonSettings = "Settings"
    override val commonAdd = "Add"
    override val commonDownload = "Download"
    override val commonOptions = "Options"
    override val commonPlayNext = "Play next"
    override val commonAddToQueue = "Add to queue"
    override val commonGoToAlbum = "Go to album"
    override val commonFavorite = "Favorite"
    override val commonDownloadQueued = "Download queued"
    override val commonConnectionLost = "Connection lost"
    override val commonPassword = "Password"
    override fun commonSongCount(count: Int) = "$count songs"

    override val homeGreetingMorning = "Good morning"
    override val homeGreetingAfternoon = "Good afternoon"
    override val homeGreetingEvening = "Good evening"
    override val homeServers = "Servers"
    override val homeSeeAll = "See all"

    override val albumLoadError = "Couldn't load album"
    override val albumType = "Album"
    override fun albumFooterSummary(count: Int, duration: String) = "$count songs • $duration"

    override val artistLoadError = "Couldn't load artist"
    override fun artistAlbumCount(count: Int) = "$count albums"
    override val artistPopular = "Popular"
    override val artistAlbums = "Albums"
    override val artistSimilar = "Similar artists"
    override val artistAbout = "About"
    override val artistBioCollapse = "Show less"
    override val artistBioExpand = "Read more"

    override val libraryTabPlaylists = "Playlists"
    override val libraryTabAlbums = "Albums"
    override val libraryTabArtists = "Artists"
    override val libraryTabSongs = "Songs"
    override val libraryTabFavorites = "Favorites"
    override val libraryTabDownloads = "Downloads"
    override val libraryCtxShuffleMix = "Shuffle mix"
    override val libraryCtxAllSongs = "All songs"
    override val libraryTitle = "Library"
    override val librarySearchHint = "Search this list"
    override fun libraryPlaylistSubtitle(count: Int) = "Playlist • $count songs"
    override val librarySortRecent = "Recently added"
    override val librarySortAlpha = "By name"
    override val libraryView = "View"
    override fun libraryAlbumSubtitle(artist: String) = "Album • $artist"
    override fun libraryArtistSubtitle(count: Int) = "Artist • $count albums"
    override val libraryDownloadsGrouped = "Grouped by album"
    override val libraryDownloadsFlat = "Flat list"
    override val libraryToggleGrouping = "Toggle grouping"
    override val libraryUnknownAlbum = "Unknown Album"
    override val libraryDownloadsEmpty = "No downloaded songs yet. Use the download button on an album or playlist page."
    override val libraryShuffleAll = "Shuffle all"

    override val loginConnectionFailed = "Connection failed"
    override val loginSubtitle = "Connect to your Navidrome server"
    override val loginServerName = "Server name (optional)"
    override val loginServerUrl = "Server address"
    override val loginServerUrlHint = "http://server-address:port"
    override val loginUsername = "Username"
    override val loginPassword = "Password"
    override val loginConnect = "Connect"

    override val playlistLoadError = "Couldn't load playlist"
    override fun playlistHeaderSubtitle(count: Int) = "Playlist • $count songs"

    override val searchAll = "All"
    override val searchSongs = "Songs"
    override val searchAlbums = "Albums"
    override val searchArtists = "Artists"
    override val searchHint = "Search songs, albums, or artists"
    override val searchRecent = "Recent searches"
    override val searchRemoveRecent = "Remove"

    override val licensesLgplOrLater = "LGPL-2.1 or later"
    override val licensesMpvNote = "Audio engine. Bundled as libmpv-2.dll and dynamically linked; the DLL can be replaced."
    override val licensesFfmpegNote = "Used inside libmpv for decoding/demuxing."
    override val licensesJnaNote = "Access to libmpv from the JVM."
    override val licensesWindowsRsNote = "Used in the media-keys / control-center (SMTC) helper."
    override val licensesTitle = "Open-source licenses"
    override val licensesIntro = "NaviCloud is made possible by the open-source software below. Thank you."
    override val licensesOpen = "Open"

    override val playerNowPlaying = "Now playing"
    override val playerSelectDevice = "Select device"
    override val playerMiniPlayer = "Mini player"
    override fun playerSleepMinutesShort(minutes: Int) = "$minutes min"
    override val playerSleepEndOfTrack = "End of track"
    override val playerSleepEndOfQueue = "End of queue"
    override val playerSleepTimer = "Sleep timer"
    override val playerTrackInfo = "Track info"
    override val playerLyrics = "Lyrics"
    override val playerMute = "Mute"
    override val playerUnmute = "Unmute"
    override fun playerUpNext(title: String) = "Up next: $title"
    override val playerQueue = "Queue"
    override val playerStop = "Stop"
    override val playerPlayingFrom = "Playing from:"
    override val playerAutoplay = "Autoplay"
    override val playerAutoplayDesc = "Continues with similar tracks when the queue ends"
    override val playerDragReorder = "Drag"
    override val playerSoundEqualizer = "Sound / Equalizer"
    override val playerSleepTimerOn = "Sleep timer • on"
    override val playerSleepRemaining = "remaining"
    override val playerSleepEndOfTrackTitle = "When the track ends"
    override val playerSleepWillStop = "will stop"
    override val playerSleepEndOfQueueTitle = "When the queue ends"
    override val playerSleepCancel = "Cancel"
    override fun playerSleepMinutes(m: Int) = "$m minutes"
    override val playerLyricsLoading = "Loading lyrics…"
    override val playerLyricsEmpty = "No lyrics found for this song"
    override val playerOfflineNoDownloads = "Offline mode: no downloaded songs in this content"

    override val devicePickerThisDevice = "This device"
    override val devicePickerEditName = "Edit name"
    override val devicePickerBusy = "Busy"
    override val devicePickerPlayingHere = "Playing here"
    override val devicePickerConnecting = "Connecting…"
    override val devicePickerAddedManually = "Added manually"
    override val devicePickerForgetDevice = "Forget device"
    override val devicePickerNoDevices = "No other devices on the network."
    override val devicePickerConnectFailed = "Couldn't connect."
    override val devicePickerAddByIp = "Add by IP"
    override val devicePickerDeviceName = "Device name"
    override val devicePickerAddByIpHint = "If the device isn't listed, enter its IP address."
    override val devicePickerSelected = "Selected"

    override val songItemMenu = "Song menu"
    override val songMenuAddToPlaylist = "Add to playlist"
    override val songMenuGoToArtist = "Go to artist"
    override val songMenuRemoveFromQueue = "Remove from queue"
    override val songMenuRemoveDownload = "Remove download"
    override val songMenuRemoveFavorite = "Remove from favorites"
    override val songMenuAddFavorite = "Add to favorites"
    override val songMenuInfo = "Info"
    override val songMenuToastPlayNext = "Added to play next"
    override val songMenuToastAddedToQueue = "Added to queue"
    override val songMenuPlaylistsLoading = "Loading playlists…"
    override fun songMenuToastAddedToPlaylist(name: String) = "Added to \"$name\""
    override val songMenuToastAddFailed = "Couldn't add"

    override val trackInfoFormat = "Format"
    override val trackInfoBitRate = "Bit rate"
    override val trackInfoSampleRate = "Sample rate"
    override val trackInfoBitDepth = "Bit depth"
    override val trackInfoChannels = "Channels"
    override val trackInfoDuration = "Duration"
    override val trackInfoSize = "Size"
    override val trackInfoStream = "Stream"
    override val trackInfoStreamOriginal = "Original • no transcode"
    override val trackInfoChannelMono = "1 • Mono"
    override val trackInfoChannelStereo = "2 • Stereo"
    override fun trackInfoChannelOther(n: Int) = "$n channels"

    override val collectionShufflePlay = "Shuffle play"
    override val collectionRemoveDownloads = "Remove downloads"
    override val collectionDownloaded = "Downloaded"

    override val audioFxBassBoost = "Bass boost"
    override val audioFxWidth = "Stereo width"
    override val audioFxAmbience = "Ambience"
    override val audioFxNotSupported = "Not supported on this device"
    override val audioFxLoudness = "Loudness"
    override val audioFxSheetTitle = "Sound effects"
    override val audioFxThisDeviceOnly = "Plays on this device only"
    override val audioFxEqualizer = "Equalizer"

    override val rootTabHome = "Home"
    override val rootTabSearch = "Search"
    override val rootTabLibrary = "Library"
    override val rootRemoteDeviceFallback = "Remote device"
    override fun rootControllingRemote(peerName: String) = "Controlling $peerName"
    override val rootSwitchToThisDevice = "Switch to this device"
    override val rootControlledRemotely = "This device is being controlled remotely"
    override val rootTakeControl = "Take control"
    override val rootPairingCodeTitle = "Pairing code"
    override val rootEnterCodeOnConnecting = "Enter this code on the connecting device:"
    override val rootPassword = "Password"
    override val rootPairing = "Pairing"
    override fun rootEnterPasswordFor(peerName: String) = "Enter the password for $peerName:"
    override fun rootEnterCodeFrom(peerName: String) = "Enter the code shown on $peerName:"
    override val rootConnect = "Connect"
    override val rootPair = "Pair"
    override val rootResumeTitle = "Resume where you left off"
    override val rootResume = "Resume"

    override val settingsTitle = "Settings"
    override val settingsServersSection = "Servers"
    override val settingsAddServer = "Add server"
    override val settingsPlaybackSection = "Playback"
    override val settingsStreamQuality = "Streaming quality"
    override val settingsOfflineMode = "Offline mode"
    override val settingsOfflineModeDesc = "Plays only downloads, uses no network"
    override val settingsPrefetch = "Preload next track"
    override val settingsPrefetchDesc = "Track transitions start without stutter"
    override val settingsPrefetchWifiOnly = "Limit preloading to Wi-Fi"
    override val settingsPrefetchWifiOnlyDesc = "No preloading on cellular data"
    override val settingsInternetLyrics = "Internet lyrics"
    override val settingsInternetLyricsDesc = "Fetch from LRCLIB when the server has no lyrics (synced supported)"
    override val settingsLibrarySection = "Library"
    override val settingsScanLibrary = "Scan library"
    override fun settingsScanScanning(items: Long) = "Scanning… $items items"
    override fun settingsScanDone(items: Long) = "Done • $items items"
    override val settingsScanIdleDesc = "Finds new files on the server"
    override val settingsScanFull = "Full scan"
    override val settingsCacheSection = "Cache"
    override val settingsStreamCache = "Streaming cache"
    override val settingsImageCache = "Image cache"
    override fun settingsImageCacheDesc(size: String) = "$size • cover art"
    override val settingsDownloadsSection = "Downloads"
    override val settingsDownloadWifiOnly = "Download on Wi-Fi only"
    override val settingsDownloadWifiOnlyDesc = "On cellular, downloads wait for Wi-Fi"
    override val settingsDownloadDeleteAll = "Delete all"
    override val settingsRemoteControlSection = "Remote control"
    override val settingsRemoteControlDesc = "Enter the same password on all your devices; they connect to each other with one tap. Leave it blank and you'll be asked for a code when connecting."
    override val settingsAboutSection = "About"
    override val settingsLicensesDesc = "Libraries used and their licenses"
    override val settingsStreamQualityDialogNote = "Options other than Original reduce data usage."
    override val settingsCacheLimitDialogTitle = "Streaming cache limit"
    override val settingsCacheLimitDialogNote = "What you listen to is stored temporarily; the oldest is removed when space is needed. Downloads are not affected."
    override val settingsClearDownloadsDialogTitle = "Delete all downloads?"
    override fun settingsClearDownloadsBody(count: Int, size: String) = "$count songs ($size) will be removed from this device."
    override val settingsAppSection = "Application"
    override val settingsLanguage = "Language"

    override val languageSystem = "System default"
    override val languageTurkish = "Türkçe"
    override val languageEnglish = "English"

    override fun downloadInProgress(title: String) = "Downloading: $title"
    override fun downloadWaitingForWifi(title: String) = "Waiting for Wi-Fi: $title"
    override fun downloadQueuedSuffix(count: Int) = " (+$count queued)"

    override val dsettingsAudioEngine = "Audio engine"
    override val dsettingsAudioBackendDefault = "Default"
    override val dsettingsCloseToTrayTitle = "Minimize to tray on close"
    override val dsettingsCloseToTraySubtitle = "Closing the window keeps the app playing in the tray"
    override val dsettingsLicensesSubtitle = "Libraries used and their licenses (including libmpv)"

    override val trayShow = "Show"
    override val trayExit = "Exit"

    override val miniAlwaysOnTop = "Always on top"
    override val miniVinylView = "Vinyl view"
    override val miniExpand = "Expand"
    override val miniStandardView = "Standard view"

    override val deviceDefaultDesktopName = "NaviCloud Desktop"

    override fun streamQualityLabel(quality: StreamQuality) = when (quality) {
        StreamQuality.RAW -> "Original quality"
        StreamQuality.HIGH -> "High • 320 kbps"
        StreamQuality.MEDIUM -> "Normal • 192 kbps"
        StreamQuality.LOW -> "Low • 128 kbps"
    }

    override fun eqPresetLabel(preset: EqPreset) = when (preset) {
        EqPreset.FLAT -> "Flat"
        EqPreset.ROCK -> "Rock"
        EqPreset.POP -> "Pop"
        EqPreset.JAZZ -> "Jazz"
        EqPreset.CLASSICAL -> "Classical"
    }

    override fun reverbPresetLabel(preset: ReverbPreset) = when (preset) {
        ReverbPreset.SMALL_ROOM -> "Small room"
        ReverbPreset.MEDIUM_ROOM -> "Medium room"
        ReverbPreset.LARGE_ROOM -> "Large room"
        ReverbPreset.MEDIUM_HALL -> "Hall"
        ReverbPreset.LARGE_HALL -> "Large hall"
        ReverbPreset.PLATE -> "Plate"
    }

    override fun homeSectionTitle(type: HomeSectionType) = when (type) {
        HomeSectionType.RECENT -> "Recently played"
        HomeSectionType.NEWEST -> "Recently added"
        HomeSectionType.FREQUENT -> "Most played"
        HomeSectionType.RANDOM -> "Made for you"
        HomeSectionType.STARRED -> "Favorite albums"
    }

    override val offlineContentUnavailable = "You're offline — this content hasn't been downloaded"
}
