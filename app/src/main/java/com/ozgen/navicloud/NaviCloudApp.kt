package com.ozgen.navicloud

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.ozgen.navicloud.ui.initArtColorExtractor
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NaviCloudApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        initArtColorExtractor(this)
    }

    // Tek Coil instance: memory + disk cache. Anahtarlar isteklerde coverArt
    // id ile sabitlenir (URL'deki auth salt'ı her açılışta değiştiği için URL
    // anahtar OLAMAZ). Coil 3'te respectCacheHeaders varsayılan kapalı.
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()
}
