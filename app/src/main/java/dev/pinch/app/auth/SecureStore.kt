package dev.pinch.app.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key/value store for credentials.
 *
 * Values are encrypted with AES-256-GCM under a key that lives in the Android Keystore
 * (hardware-backed where the phone supports it) and can never be exported. What lands on
 * disk is only IV + ciphertext; the app's data is also excluded from backups and device
 * transfers (see backup rules), so tokens never leave the phone except to Anthropic.
 */
class SecureStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_LEN))
            String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
        } catch (_: Exception) {
            // Key gone (app data restored elsewhere, keystore reset) or data tampered with:
            // the value is useless, so drop it and let the user sign in again.
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).commit()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val out = cipher.iv + ct
        prefs.edit().putString(key, Base64.encodeToString(out, Base64.NO_WRAP)).commit()
    }

    fun putLong(key: String, value: Long) = put(key, value.toString())
    fun getLong(key: String): Long = get(key)?.toLongOrNull() ?: 0L

    fun clear() {
        prefs.edit().clear().commit()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        // Prefer StrongBox, the phone's separate security chip, where it has one; otherwise
        // the regular hardware-backed keystore.
        return try {
            gen.init(spec(strongBox = true))
            gen.generateKey()
        } catch (_: Exception) {
            gen.init(spec(strongBox = false))
            gen.generateKey()
        }
    }

    private companion object {
        const val PREFS = "secure_store"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "clawd_usage_credentials"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
