package com.ozgen.navicloud

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NaviCloudApp : Application(), ImageLoaderFactory {

    // Tek Coil instance: memory + disk cache. Subsonic kapak URL'leri no-cache
    // header'ıyla gelebilir, o yüzden respectCacheHeaders kapalı; anahtarlar
    // isteklerde coverArt id ile sabitlenir (URL'deki auth salt'ı her açılışta
    // değiştiği için URL anahtar OLAMAZ).
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
