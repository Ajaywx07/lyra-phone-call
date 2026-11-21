package com.example.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware/OS-backed Android Keystore AES-256-GCM helper for encrypting sensitive user
 * configuration such as runtime AI API keys at rest inside DataStore.
 * Never logs key material or plaintext secrets.
 */
object SecretCryptoHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lyra_ai_secret_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PREFIX_ENC_V1 = "ENC_V1:"

    @Volatile
    private var fallbackKey: SecretKey? = null

    fun encrypt(plainText: String): String {
        val trimmed = plainText.trim()
        if (trimmed.isEmpty()) return ""

        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            PREFIX_ENC_V1 + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // If Keystore is temporarily unavailable, avoid crashing settings persistence
            ""
        }
    }

    fun decrypt(storedValue: String?): String {
        if (storedValue.isNullOrBlank()) return ""
        if (!storedValue.startsWith(PREFIX_ENC_V1)) {
            // Backward compatibility for legacy unencrypted preference values
            return storedValue.trim()
        }

        return try {
            val encoded = storedValue.removePrefix(PREFIX_ENC_V1)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (_: Exception) {
            // Fallback for local JVM unit test runners (Robolectric) where AndroidKeyStore is absent
            return fallbackKey ?: synchronized(this) {
                fallbackKey ?: KeyGenerator.getInstance("AES").apply {
                    init(256)
                }.generateKey().also { fallbackKey = it }
            }
        }
    }
}
