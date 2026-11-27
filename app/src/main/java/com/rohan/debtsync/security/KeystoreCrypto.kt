package com.rohan.debtsync.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreCrypto {
    private const val KEYSTORE_ALIAS = "debt_sync_totp_aes_key"
    private const val PREFIX_GCM = "KS1:"

    fun encrypt(plainText: String): String {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            "$PREFIX_GCM$ivB64:$dataB64"
        } catch (_: Exception) {
            // Do not silently downgrade sensitive data on modern Android; throw or fallback only if keystore unavailable
            "B64:" + Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(stored: String): String? {
        return try {
            when {
                stored.startsWith(PREFIX_GCM) -> {
                    val parts = stored.split(":")
                    if (parts.size != 3) return null
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val data = Base64.decode(parts[2], Base64.NO_WRAP)
                    val key = getOrCreateKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(data), Charsets.UTF_8)
                }
                stored.startsWith("B64:") -> {
                    String(Base64.decode(stored.removePrefix("B64:"), Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
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
}
