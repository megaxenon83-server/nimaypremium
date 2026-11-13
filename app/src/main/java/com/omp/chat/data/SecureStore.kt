package com.omp.chat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores secrets encrypted with a non-exportable Android Keystore key.
 *
 * The ciphertext (IV + payload) lives in SharedPreferences; the key never leaves
 * the keystore. A keystore reset simply invalidates the old value, which the app
 * treats as "not paired".
 *
 * [keySuffix] allows multiple independent entries (e.g. one per PC profile) in
 * the same SharedPreferences file. When blank, the default key is used (backward
 * compatible with the single-PC token storage).
 */
class SecureStore(context: Context, private val keySuffix: String = "") {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val entryKey get() = if (keySuffix.isBlank()) KEY_CIPHER else "${KEY_CIPHER}_$keySuffix"
    private val keyAlias get() = if (keySuffix.isBlank()) ALIAS else "${ALIAS}_$keySuffix"

    fun put(secret: String) {
        if (secret.isEmpty()) {
            clear()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(cipher.iv.size + encrypted.size)
            System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
            System.arraycopy(encrypted, 0, payload, cipher.iv.size, encrypted.size)
            prefs.edit().putString(entryKey, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
        } catch (error: Exception) {
            android.util.Log.e("OmpChat", "SecureStore.put() failed: ${error.javaClass.simpleName}: ${error.message}")
            clear()
        }
    }

    fun get(): String? {
        val encoded = prefs.getString(entryKey, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_SIZE) return null
            val iv = payload.copyOfRange(0, IV_SIZE)
            val body = payload.copyOfRange(IV_SIZE, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (error: Exception) {
            null
        }
    }

    fun clear() {
        prefs.edit().remove(entryKey).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "ompchat_secure"
        const val KEY_CIPHER = "token_cipher"
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "ompchat_bridge_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}