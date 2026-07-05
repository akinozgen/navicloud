package com.ozgen.navicloud.remote

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * İki yönlü, JSON-çerçeveli soket soyutlaması. [incoming] karşıdan gelen [RcMessage]'ları taşır;
 * [send] gönderir. Client (OkHttp) ve server (Ktor) aynı arayüzü sağlar → üst katman (RemotePlayerController,
 * RemoteControlServer) transporttan bağımsız. Tüm çözümleme sessiz fail (bozuk çerçeve düşürülür).
 */
interface RcConnection {
    val incoming: Flow<RcMessage>
    suspend fun send(msg: RcMessage)
    fun close()
}

/** Controller tarafı — bir peer'a bağlanır. */
interface RcClient {
    suspend fun connect(host: String, port: Int): RcConnection
}

/** Alıcı tarafı — WS sunucusu; her bağlantı için [onConnection] çağrılır. */
interface RcServer {
    fun start(port: Int, host: String = "0.0.0.0", onConnection: (RcConnection) -> Unit)
    fun stop()
    /** Gerçekten bağlanılan port (port=0 verilirse OS seçer → mDNS bunu yayınlar, RC-2). */
    val boundPort: Int
}

private fun Json.encodeMsg(msg: RcMessage): String = encodeToString(RcMessage.serializer(), msg)
private fun Json.decodeMsgOrNull(text: String): RcMessage? =
    runCatching { decodeFromString(RcMessage.serializer(), text) }.getOrNull()

/** OkHttp WebSocket istemcisi — ek bağımlılık yok (okhttp zaten shared'te). */
class OkHttpRcClient(
    private val client: OkHttpClient,
    private val json: Json,
) : RcClient {
    override suspend fun connect(host: String, port: Int): RcConnection {
        val channel = Channel<RcMessage>(Channel.UNLIMITED)
        val opened = CompletableDeferred<Unit>()
        val request = Request.Builder().url("ws://$host:$port/rc").build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                json.decodeMsgOrNull(text)?.let { channel.trySend(it) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.completeExceptionally(t)
                channel.close()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                channel.close()
            }
        })
        opened.await() // onFailure → exception; çağıran runCatching sarar
        return object : RcConnection {
            override val incoming = channel.receiveAsFlow()
            override suspend fun send(msg: RcMessage) {
                ws.send(json.encodeMsg(msg))
            }
            override fun close() {
                runCatching { ws.close(1000, null) }
                channel.close()
            }
        }
    }
}

/** Ktor CIO WebSocket sunucusu. `/rc` endpoint'i; her oturum bir [RcConnection]. Android + desktop JVM'de çalışır. */
class KtorRcServer(
    private val json: Json,
) : RcServer {
    private var server: EmbeddedServer<*, *>? = null

    @Volatile
    private var _boundPort: Int = 0
    override val boundPort: Int get() = _boundPort

    override fun start(port: Int, host: String, onConnection: (RcConnection) -> Unit) {
        stop() // idempotent: varsa eski sunucuyu kapat (Android servis yeniden yaratma → çifte bind/sızıntı önle)
        val s = embeddedServer(CIO, port = port, host = host) {
            install(WebSockets)
            routing {
                webSocket("/rc") {
                    val session = this
                    val channel = Channel<RcMessage>(Channel.UNLIMITED)
                    val conn = object : RcConnection {
                        override val incoming = channel.receiveAsFlow()
                        override suspend fun send(msg: RcMessage) {
                            session.send(Frame.Text(json.encodeMsg(msg)))
                        }
                        override fun close() {
                            channel.close()
                            session.launch { runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "")) } }
                        }
                    }
                    onConnection(conn)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) json.decodeMsgOrNull(frame.readText())?.let { channel.trySend(it) }
                        }
                    } finally {
                        channel.close()
                    }
                }
            }
        }
        s.start(wait = false)
        server = s
        // resolvedConnectors bind bitene dek bekler; bind HATASI fırlar → çağıran
        // runCatching ile yakalayıp port=0 (OS seçer) fallback'ine düşebilir.
        _boundPort = try {
            runBlocking { s.engine.resolvedConnectors().first().port }
        } catch (t: Throwable) {
            runCatching { s.stop(0, 0) }
            server = null
            throw t
        }
    }

    override fun stop() {
        runCatching { server?.stop(300, 800) }
        server = null
    }
}
