package com.ozgen.navicloud.remote

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Uzaktan kumanda eşleştirme kriptosu (RC-5). HMAC-SHA256 challenge/response — LAN self-host için yeterli
 * (TLS yok, kararlaştırıldı). PIN düz gönderilmez: her handshake'te taze nonce + `HMAC(sır, nonce)`.
 */
object RcCrypto {
    private val rng = SecureRandom()

    /** [bytes] rastgele bayt → hex. nonce (16) ve kalıcı pairKey (32) için. */
    fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        rng.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Alıcıda gösterilen 6 haneli eşleştirme kodu. */
    fun randomPin(): String = (rng.nextInt(900_000) + 100_000).toString()

    fun hmac(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Zamanlama-sabit karşılaştırma (token doğrulama). */
    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
}
