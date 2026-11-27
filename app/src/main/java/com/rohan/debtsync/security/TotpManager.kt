package com.rohan.debtsync.security

import android.graphics.Bitmap
import android.graphics.Color
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object TotpManager {
    private const val KEYSTORE_ALIAS = "debt_sync_totp_aes_key"
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
        issuer: String = "Debt Sync"
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

    fun encryptSecret(plain: String): String {
        return try {
            val key = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "KS1:$ivB64:$dataB64"
        } catch (_: Exception) {
            // Fallback encoding if keystore is unavailable on device
            "B64:" + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decryptSecret(stored: String): String? {
        return try {
            if (stored.startsWith("KS1:")) {
                val parts = stored.split(":")
                if (parts.size != 3) return null
                val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                val data = Base64.decode(parts[2], Base64.NO_WRAP)
                val key = getOrCreateKeystoreKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                String(cipher.doFinal(data), Charsets.UTF_8)
            } else if (stored.startsWith("B64:")) {
                String(Base64.decode(stored.removePrefix("B64:"), Base64.NO_WRAP), Charsets.UTF_8)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

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
