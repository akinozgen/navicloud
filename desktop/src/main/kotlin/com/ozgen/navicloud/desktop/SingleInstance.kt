package com.ozgen.navicloud.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Tek örnek (single-instance) kilidi. İlk açılan örnek 127.0.0.1'de sabit bir portu tutar ve "öne getir"
 * sinyallerini dinler. İkinci kez açılırsa [acquire] false döner → [signalExisting] ile mevcut örneği öne
 * getirtip çıkılır. Yalnızca loopback (dış ağa açık değil).
 */
object SingleInstance {
    // NaviCloud'a özgü, çakışması olası olmayan loopback IPC portu (RC portundan ayrı).
    private const val PORT = 47324

    private var serverSocket: ServerSocket? = null
    private val _focusRequests = MutableStateFlow(0)
    /** Her yeni "öne getir" sinyalinde artar — UI bunu izleyip pencereyi öne getirir. */
    val focusRequests: StateFlow<Int> = _focusRequests.asStateFlow()

    /** true = bu örnek birincil (portu aldı). false = zaten çalışan bir örnek var. */
    fun acquire(): Boolean = try {
        serverSocket = ServerSocket(PORT, 0, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true, name = "navicloud-single-instance") {
            val ss = serverSocket ?: return@thread
            while (!ss.isClosed) {
                runCatching {
                    ss.accept().close() // herhangi bir bağlantı = öne getir sinyali
                    _focusRequests.value = _focusRequests.value + 1
                }
            }
        }
        true
    } catch (e: Exception) {
        false // port dolu → başka örnek çalışıyor
    }

    /** Çalışan örneğe "öne gel" sinyali gönderir (kısa TCP bağlantısı). */
    fun signalExisting() {
        runCatching { Socket("127.0.0.1", PORT).use { it.getOutputStream().write(1) } }
    }
}
