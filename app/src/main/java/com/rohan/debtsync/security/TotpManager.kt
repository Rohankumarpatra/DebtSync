package com.rohan.debtsync.security

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpManager {
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val RECOVERY_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val STEP_SECONDS = 30L
    private const val DIGITS = 6

    fun generateSecretBase32(numBytes: Int = 20): String {
        val bytes = ByteArray(numBytes)
        SecureRandom().nextBytes(bytes)
        return encodeBase32(bytes)
    }

    fun formatSecretForDisplay(secret: String): String {
        return secret.uppercase().replace(" ", "").chunked(4).joinToString(" ")
    }

    fun buildOtpAuthUri(
        secretBase32: String,
        accountName: String = "Personal",
        issuer: String = "EmiGo"
    ): String {
        val encIssuer = URLEncoder.encode(issuer, "UTF-8").replace("+", "%20")
        val encAccount = URLEncoder.encode(accountName, "UTF-8").replace("+", "%20")
        val cleanSecret = secretBase32.replace(" ", "").uppercase()
        return "otpauth://totp/$encIssuer:$encAccount?secret=$cleanSecret&issuer=$encIssuer&algorithm=SHA1&digits=$DIGITS&period=$STEP_SECONDS"
    }

    fun secondsRemainingInStep(): Int {
        val nowSec = System.currentTimeMillis() / 1000L
        return (STEP_SECONDS - (nowSec % STEP_SECONDS)).toInt()
    }

    fun verifyTotp(secretBase32: String, code: String, windowSteps: Long = 1L): Boolean {
        val cleanCode = code.trim()
        if (cleanCode.length != DIGITS || !cleanCode.all { it.isDigit() }) return false
        val keyBytes = decodeBase32(secretBase32)
        if (keyBytes.isEmpty()) return false

        val currentStep = (System.currentTimeMillis() / 1000L) / STEP_SECONDS
        for (offset in -windowSteps..windowSteps) {
            val candidate = generateTotpForStep(keyBytes, currentStep + offset)
            if (constantTimeEquals(candidate, cleanCode)) return true
        }
        return false
    }

    private fun generateTotpForStep(keyBytes: ByteArray, step: Long): String {
        val data = ByteBuffer.allocate(8).putLong(step).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 1_000_000
        return otp.toString().padStart(DIGITS, '0')
    }

    fun generateRecoveryCode(): String {
        val rng = SecureRandom()
        val raw = (1..8).map { RECOVERY_CHARS[rng.nextInt(RECOVERY_CHARS.length)] }.joinToString("")
        return "${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    fun createQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val offset = y * w
                for (x in 0 until w) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    fun encryptSecret(plain: String): String = KeystoreCrypto.encrypt(plain)

    fun decryptSecret(stored: String): String? = KeystoreCrypto.decrypt(stored)

    private fun encodeBase32(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer shr (bitsLeft - 5)) and 0x1F
                out.append(BASE32_CHARS[idx])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(BASE32_CHARS[idx])
        }
        return out.toString()
    }

    private fun decodeBase32(input: String): ByteArray {
        val clean = input.uppercase().filter { it in BASE32_CHARS }
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            val val5 = BASE32_CHARS.indexOf(ch)
            if (val5 < 0) continue
            buffer = (buffer shl 5) or val5
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}
